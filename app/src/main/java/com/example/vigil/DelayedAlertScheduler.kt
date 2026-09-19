// src/main/java/com/example/vigil/DelayedAlertScheduler.kt
package com.example.vigil

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.Calendar
import java.util.TimeZone

/**
 * 延时报警的到期时刻计算与闹钟调度。
 *
 * 命中关键词后若策略要求延时，报警不立即入队，而是先持久化一条 ScheduledAlert（铁律 3），
 * 再由这里用 AlarmManager 排一个到期闹钟；到期后 [DelayedAlertReceiver] 拉起服务把该项转成真正的报警。
 *
 * 三层触发保证不漏报：
 * 1. AlarmManager：进程被杀、设备休眠也由系统唤醒（有精确闹钟权限时精确到秒，否则非精确兜底）；
 * 2. 服务内 Handler 定时器：进程存活时对非精确兜底做精度补偿；
 * 3. 心跳每 30s 顺带扫描到期项：前两层都被 ROM 拦截时仍能在进程存活期内补触发。
 * 三层都以「已移除的待触发项」为幂等判据，重复触发是安全的。
 */
object DelayedAlertScheduler {

    private const val TAG = "VigilDelayScheduler"

    const val ACTION_DELAYED_ALERT_DUE = "com.example.vigil.ACTION_DELAYED_ALERT_DUE"
    const val EXTRA_SCHEDULED_ALERT_ID = "com.example.vigil.EXTRA_SCHEDULED_ALERT_ID"

    /**
     * 计算本次命中的响铃时刻。
     * @return null 表示不延时（立即报警）；否则为墙钟到期毫秒。
     */
    fun resolveTriggerAt(policy: DelayPolicy, now: Long = System.currentTimeMillis()): Long? {
        return when (policy.mode) {
            DelayMode.IMMEDIATE -> null
            DelayMode.FIXED ->
                now + SharedPreferencesHelper.normalizeFixedDelayMs(policy.fixedDelayMs)
            DelayMode.SCHEDULED -> nextTimeOfDayTrigger(policy.timesOfDay, now)
        }
    }

    /**
     * 命中后最近的每日定点时刻。
     * 取严格晚于 [now] 的最近时间点；当天时间点已全部过去则取次日第一个。
     * @return null 表示没有任何合法时间点（调用方按立即报警处理）
     */
    fun nextTimeOfDayTrigger(timesOfDay: List<Int>, now: Long): Long? {
        val times = SharedPreferencesHelper.normalizeTimesOfDay(timesOfDay)
        if (times.isEmpty()) return null
        for (minutes in times) {
            val candidate = calendarAt(now, minutes)
            if (candidate > now) return candidate
        }
        // 当天都过去了：取次日第一个时间点
        val first = times.first()
        return calendarAt(now, first, dayOffset = 1)
    }

    private fun calendarAt(now: Long, minutesOfDay: Int, dayOffset: Int = 0): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        calendar.set(Calendar.MINUTE, minutesOfDay % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (dayOffset != 0) calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
        return calendar.timeInMillis
    }

    /** 是否允许精确闹钟：Android 12+ 需系统「闹钟和提醒」授权，未授权时用非精确闹钟兜底。 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return try {
            alarmManager.canScheduleExactAlarms()
        } catch (e: Exception) {
            Log.w(TAG, "查询精确闹钟授权失败，按未授权处理", e)
            false
        }
    }

    /** 为一条待触发延时报警排闹钟（同一 id 重复调用＝覆盖，幂等）。 */
    fun schedule(context: Context, id: String, triggerAtMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, id)
        val exact = canScheduleExactAlarms(context)
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
            Log.i(TAG, "延时报警闹钟已排定: id=$id, triggerAt=$triggerAtMs, exact=$exact")
            VigilLogger.i(context, "VigilDelayScheduler", "闹钟已排定: id=$id, exact=$exact, 距现在 ${(triggerAtMs - System.currentTimeMillis()) / 1000}s")
        } catch (e: SecurityException) {
            // 精确闹钟权限刚被撤销：回落非精确，宁可晚几秒也不丢报警
            Log.w(TAG, "精确闹钟被拒（权限缺失），回落非精确闹钟", e)
            VigilLogger.w(context, "VigilDelayScheduler", "精确闹钟被拒，回落非精确闹钟: ${e.javaClass.simpleName}")
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "排定延时报警闹钟失败", e2)
                VigilLogger.e(context, "VigilDelayScheduler", "排定延时报警闹钟失败: ${e2.javaClass.simpleName}", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "排定延时报警闹钟失败", e)
            VigilLogger.e(context, "VigilDelayScheduler", "排定延时报警闹钟失败: ${e.javaClass.simpleName}", e)
        }
    }

    /** 取消某条待触发项的闹钟（已触发或用户取消时调用）。 */
    fun cancel(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            alarmManager.cancel(buildPendingIntent(context, id))
            Log.i(TAG, "延时报警闹钟已取消: id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "取消延时报警闹钟失败", e)
        }
    }

    /** 打开系统「闹钟和提醒」授权页（Android 12+）；返回是否成功调起。 */
    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val withPackage = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(withPackage)
            true
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e2: Exception) {
                Log.e(TAG, "无法打开精确闹钟授权页", e2)
                false
            }
        }
    }

    private fun buildPendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, DelayedAlertReceiver::class.java).apply {
            action = ACTION_DELAYED_ALERT_DUE
            putExtra(EXTRA_SCHEDULED_ALERT_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            // PendingIntent 身份由 requestCode + Intent 过滤决定，把 id 映射成稳定 requestCode；
            // 带上 extras 只为方便接收方定位，不参与身份比较
            id.hashCode() and 0x7fffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
