// src/main/java/com/example/vigil/ListenerRecovery.kt
package com.example.vigil

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通知监听绑定的自愈工具，供 Service 看门狗和 MainActivity 共用。
 *
 * 背景：部分系统（尤其是国产 ROM 的激进省电策略）会在进程被杀重建后
 * 不再重新绑定 NotificationListenerService，但 enabled_notification_listeners
 * 设置仍在，导致"权限显示已授予、服务进程活着、却收不到任何通知"。
 * 撤销再授予权限会强制系统重绑，这里的"完整重连序列"用代码实现等效操作。
 *
 * 2026-08 HyperOS 3.0.308 实机证据（issue #2 日志）：
 * 应用开关关闭再打开后，requestRebind() 被系统静默忽略、永不回调
 * onListenerConnected，唯一有效恢复是系统级撤销+重新授权（用户卸载重装/
 * 清数据重配即此效果）。因此：只动 requestRebind 不够，必须让系统真正
 * 观察到"连接消亡 → 重建"（stopService + 组件 disable/enable + 重启服务）。
 *
 * 时效要求（owner 2026-08）：权限失效必须"以最快速度判断出来"。一旦发现
 * 服务失效就尽快让用户重新授权，不做长时间重连（用户会以为卡死）。所以
 * 采用有界的"快速自愈"：requestRebind → 短观察 → 一次完整重连序列 →
 * 仍无效则立即标记恢复失败（约 10s 内）转交 UI，而不是多档位 + 长节流的慢速升级。
 */
object ListenerRecovery {

    private const val TAG = "ListenerRecovery"

    // 完整重连序列低频入口（forceReconnect）的最小间隔：序列本身会 stopService 销毁重建服务，
    // 过密触发只会在一个循环里空转；30s 足以观察单次序列结果，又不会让手动重试长时间空等。
    private const val FORCE_RECONNECT_THROTTLE_MS = 30_000L

    // 快速自愈：先做无损 requestRebind，给系统若干秒自愈机会；失败才升级完整重连序列
    private const val FAST_REBIND_OBSERVE_MS = 2500L
    // 完整重连序列结束后的观察期：等待 onListenerConnected 写回 listener_connected=true
    private const val FORCE_SEQUENCE_OBSERVE_MS = 4000L

    @Volatile private var lastForceReconnectAt = 0L
    // 快速自愈进行中标记（进程级）：看门狗与 onListenerDisconnected 可能同时触发，防抖只跑一轮
    @Volatile private var fastRecoveryInFlight = false

    /**
     * 独立于服务生命周期的执行域。
     * 完整重连序列会 stopService 销毁当前服务实例，若在服务的 serviceScope
     * 里执行，onDestroy 会 cancel 协程、序列中断在半路（组件停在 disabled 状态），
     * 这是此前"组件强刷"从未真正生效的隐患之一。
     */
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 官方 API（API 24+）：请求系统重新绑定通知监听服务。
     * @return true 表示调用成功（不代表绑定已恢复，恢复以 onListenerConnected 为准）。
     */
    fun requestRebind(context: Context): Boolean {
        return try {
            NotificationListenerService.requestRebind(
                ComponentName(context, MyNotificationListenerService::class.java)
            )
            Log.i(TAG, "requestRebind 调用成功，等待系统重绑。")
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind 调用失败", e)
            false
        }
    }

    /**
     * 快速自愈入口（看门狗 / onListenerDisconnected / 手动"立即重试"共用）。
     *
     * 目标：权限失效要"以最快速度判断出来"。整个流程有界（约 10s），不搞长时间重连：
     *   ① 先做无损 requestRebind（官方 API）——多数瞬时断连此时即可自愈；
     *   ② 短观察后仍断开 → 执行一次完整重连序列；
     *   ③ 序列也无效 → 立即标记恢复失败，让 UI 尽快显示"重新授权"逃生通道。
     *
     * 防抖：fastRecoveryInFlight 保证同一时刻只有一轮在跑（看门狗与 onListenerDisconnected
     * 可能同时触发），避免 stopService/startService 在系统侧空转。成功后由 onListenerConnected
     * 正常清除失败标记。
     */
    fun startFastRecovery(context: Context) {
        if (fastRecoveryInFlight) {
            Log.d(TAG, "快速自愈已进行中，跳过重复触发")
            return
        }
        fastRecoveryInFlight = true
        recoveryScope.launch {
            try {
                // ① 无损 requestRebind
                VigilLogger.w(context, TAG, "快速自愈: 先 requestRebind，观察 ${FAST_REBIND_OBSERVE_MS / 1000}s")
                requestRebind(context)
                if (waitForConnection(context, FAST_REBIND_OBSERVE_MS)) {
                    Log.i(TAG, "快速自愈: requestRebind 后绑定已恢复。")
                    markRecoverySuccess(context)
                    return@launch
                }
                // ② 仍断开，执行一次完整重连序列
                if (runForceReconnectSequence(context)) {
                    markRecoverySuccess(context)
                    return@launch
                }
                // ③ 序列也无效 —— 立即失败，转交 UI 引导用户重新授权
                markRecoveryFailed(context)
            } catch (e: Exception) {
                Log.e(TAG, "快速自愈流程异常", e)
                VigilLogger.e(context, TAG, "快速自愈流程异常", e)
            } finally {
                fastRecoveryInFlight = false
            }
        }
    }

    /**
     * 完整重连序列（节流 ≥ FORCE_RECONNECT_THROTTLE_MS）——供低频/手动入口（MainActivity.restartService）调用。
     * 执行序列后标记成功或失败，给调用方即时反馈。
     */
    fun forceReconnect(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (fastRecoveryInFlight || now - lastForceReconnectAt < FORCE_RECONNECT_THROTTLE_MS) {
            Log.d(TAG, "forceReconnect 节流或自愈进行中，跳过")
            VigilLogger.d(context, TAG, "强制重连节流中，跳过本次触发")
            return
        }
        lastForceReconnectAt = now
        recoveryScope.launch {
            if (runForceReconnectSequence(context)) markRecoverySuccess(context)
            else markRecoveryFailed(context)
        }
    }

    /**
     * 执行完整重连序列（无节流，供快速自愈与变频入口复用）：
     *   stopService → 组件 disable → 等待系统解绑 → 组件 enable → 重启服务 → requestRebind。
     *
     * 关键点：只 disable/enable 组件而不停服务时，服务实例仍活着（前台 started 状态），
     * 系统观察不到连接真正消亡；先 stopService 让连接真正断掉，系统才会在
     * 组件重新 enable、服务重建后建立新绑定。
     *
     * @return 序列观察结束后绑定是否已恢复（onListenerConnected 已写回 listener_connected=true）。
     */
    private suspend fun runForceReconnectSequence(context: Context): Boolean {
        return try {
            Log.i(TAG, "强制重连序列开始: stopService")
            VigilLogger.w(context, TAG, "强制重连序列开始（stopService → 组件 toggle → 重启 → requestRebind）")
            context.stopService(Intent(context, MyNotificationListenerService::class.java))
            setComponentEnabled(context, false)
            delay(1500) // 给系统足够时间完成解绑
            setComponentEnabled(context, true)
            delay(300)
            Log.i(TAG, "强制重连序列: 启动服务")
            ContextCompat.startForegroundService(
                context, Intent(context, MyNotificationListenerService::class.java)
            )
            delay(800)
            Log.i(TAG, "强制重连序列: requestRebind 兜底")
            requestRebind(context)
            // 观察期：等待 onListenerConnected 持久化 listener_connected=true
            waitForConnection(context, FORCE_SEQUENCE_OBSERVE_MS)
        } catch (e: Exception) {
            Log.e(TAG, "强制重连序列执行失败", e)
            VigilLogger.e(context, TAG, "强制重连序列执行失败", e)
            // 序列半路失败时确保组件处于 enabled，避免把自己锁死
            if (context.packageManager.getComponentEnabledSetting(
                    ComponentName(context, MyNotificationListenerService::class.java)
                ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                setComponentEnabled(context, true)
            }
            false
        }
    }

    /** 轮询监听绑定状态直到恢复或超时；恢复以持久化 listener_connected=true 为准。 */
    private suspend fun waitForConnection(context: Context, timeoutMs: Long): Boolean {
        val prefs = SharedPreferencesHelper(context)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (prefs.getListenerConnectedState()) return true
            delay(600)
        }
        return prefs.getListenerConnectedState()
    }

    /**
     * 恢复失败标记（跳转 UI 逃生通道的依据）。
     * 快速自愈在尝试 requestRebind + 完整重连序列后仍无效时调用一次，
     * UI 据此展示"重新授权"引导，避免用户只能靠卸载重装/清数据。
     * 自动重连成功后由 markRecoverySuccess 清除。
     */
    fun markRecoveryFailed(context: Context) {
        if (SharedPreferencesHelper(context).saveListenerRecoveryFailed(true)) {
            VigilLogger.w(context, TAG, "监听自动重连失败，已引导用户重新授权")
        }
        recoveryScope.launch { VigilEventBus.listenerRecoveryFailed.emit(true) }
    }

    fun markRecoverySuccess(context: Context) {
        fastRecoveryInFlight = false
        if (SharedPreferencesHelper(context).saveListenerRecoveryFailed(false)) {
            VigilLogger.i(context, TAG, "监听绑定已恢复，清除重连失败标记")
        }
        recoveryScope.launch { VigilEventBus.listenerRecoveryFailed.emit(false) }
    }

    /**
     * 组件 enable/disable（App 对自身组件操作无需额外权限；
     * AGENTS.md 中的 SecurityException 警告只针对 shell `pm enable`）。
     */
    fun setComponentEnabled(context: Context, enabled: Boolean) {
        val componentName = ComponentName(context, MyNotificationListenerService::class.java)
        val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            if (context.packageManager.getComponentEnabledSetting(componentName) != newState) {
                context.packageManager.setComponentEnabledSetting(
                    componentName, newState, PackageManager.DONT_KILL_APP
                )
                Log.i(TAG, "监听服务组件状态设置为: ${if (enabled) "enabled" else "disabled"}")
            } else {
                Log.d(TAG, "监听服务组件已处于目标状态: ${if (enabled) "enabled" else "disabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置监听服务组件状态出错: ", e)
        }
    }
}
