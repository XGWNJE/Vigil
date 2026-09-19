// src/main/java/com/example/vigil/DelayedAlertReceiver.kt
package com.example.vigil

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 延时报警到期闹钟的接收方（AlarmManager → 拉起服务）。
 * 只做一件事：把「某条待触发项到期了」告诉服务，真正的判定/入队/响铃都在服务里，
 * 这样无论闹钟把进程冷启动还是服务本来就在跑，走的都是同一条幂等路径。
 */
class DelayedAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val id = intent.getStringExtra(DelayedAlertScheduler.EXTRA_SCHEDULED_ALERT_ID)
        Log.i(TAG, "延时报警闹钟到期触发 (id=$id)")
        VigilLogger.i(appContext, TAG, "延时报警闹钟到期 (id=$id)")
        val serviceIntent = Intent(appContext, MyNotificationListenerService::class.java).apply {
            action = DelayedAlertScheduler.ACTION_DELAYED_ALERT_DUE
            id?.let { putExtra(DelayedAlertScheduler.EXTRA_SCHEDULED_ALERT_ID, it) }
        }
        try {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限（未加电池白名单等）时会被拒：
            // 待触发项仍在持久化里，服务下次运行（心跳扫描）或用户打开应用时会补触发
            Log.e(TAG, "闹钟到期但无法拉起服务，等待服务下次运行补触发", e)
            VigilLogger.e(
                appContext, TAG,
                "闹钟到期但拉起服务失败(${e.javaClass.simpleName})，等待下次运行补触发"
            )
        }
    }

    companion object {
        private const val TAG = "VigilDelayReceiver"
    }
}
