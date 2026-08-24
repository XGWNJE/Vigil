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
 */
object ListenerRecovery {

    private const val TAG = "ListenerRecovery"

    // 两次完整重连序列的最小间隔：HyperOS 上序列本身会销毁重建服务，
    // 过密触发只会在一个循环里空转；90s 也保证单次序列有充分时间观察结果
    private const val FORCE_RECONNECT_THROTTLE_MS = 90_000L

    // 完整重连序列连续无效达到该次数后，标记恢复失败并转交 UI 引导用户重新授权。
    // 注意：forceReconnect 会 stopService 销毁当前服务实例，无法用服务内计数器
    // 跨实例累计，因此放在本对象（进程级，服务重建不清零），恢复成功后清零。
    private const val MAX_FORCE_ATTEMPTS = 2

    @Volatile private var lastForceReconnectAt = 0L
    @Volatile private var forceAttemptCount = 0

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
     * 完整重连序列（节流 ≥ FORCE_RECONNECT_THROTTLE_MS）：
     *   stopService → 组件 disable → 等待系统解绑 → 组件 enable → 重启服务 → requestRebind。
     *
     * 关键点：只 disable/enable 组件而不停服务时，服务实例仍活着（前台 started 状态），
     * 系统观察不到连接真正消亡；先 stopService 让连接真正断掉，系统才会在
     * 组件重新 enable、服务重建后建立新绑定。
     *
     * 注意：本方法不校验结果，是否恢复统一由 onListenerConnected 判定；
     * 连续多次失败由调用方（看门狗）计数并最终转交用户手动重新授权。
     */
    fun forceReconnect(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastForceReconnectAt < FORCE_RECONNECT_THROTTLE_MS) {
            Log.d(TAG, "forceReconnect 距上次不足 ${FORCE_RECONNECT_THROTTLE_MS / 1000}s，跳过")
            VigilLogger.d(context, TAG, "强制重连节流中，跳过本次触发")
            return
        }
        lastForceReconnectAt = now
        recoveryScope.launch {
            try {
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
                // 观察期：新服务若已重绑（onListenerConnected 持久化 listener_connected=true），
                // 序列视为有效；否则累计失败计数，达上限转交 UI 引导（标记失败前先给足观察时间，
                // 避免与 onListenerConnected 写回产生竞态误判）
                delay(4000)
                if (SharedPreferencesHelper(context).getListenerConnectedState()) {
                    Log.i(TAG, "强制重连序列观察: 绑定已恢复，序列有效。")
                    forceAttemptCount = 0
                } else {
                    forceAttemptCount++
                    Log.w(TAG, "强制重连序列观察: 仍未绑定（累计 ${forceAttemptCount} 次）。")
                    if (forceAttemptCount >= MAX_FORCE_ATTEMPTS) {
                        markRecoveryFailed(context)
                    }
                }
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
            }
        }
    }

    /**
     * 恢复失败标记（跳转 UI 逃生通道的依据）。
     * 看门狗在完整重连序列仍无效后调用一次，UI 据此展示"重新授权"引导，
     * 避免用户只能靠卸载重装/清数据。自动重连成功后由 markRecoverySuccess 清除。
     */
    fun markRecoveryFailed(context: Context) {
        if (SharedPreferencesHelper(context).saveListenerRecoveryFailed(true)) {
            VigilLogger.w(context, TAG, "监听自动重连多次失败，已引导用户重新授权")
        }
        recoveryScope.launch { VigilEventBus.listenerRecoveryFailed.emit(true) }
    }

    fun markRecoverySuccess(context: Context) {
        forceAttemptCount = 0
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
