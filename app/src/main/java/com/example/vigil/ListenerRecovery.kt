// src/main/java/com/example/vigil/ListenerRecovery.kt
package com.example.vigil

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 通知监听绑定的自愈工具，供 Service 看门狗和 MainActivity 共用。
 *
 * 背景：部分系统（尤其是国产 ROM 的激进省电策略）会在进程被杀重建后
 * 不再重新绑定 NotificationListenerService，但 enabled_notification_listeners
 * 设置仍在，导致"权限显示已授予、服务进程活着、却收不到任何通知"。
 * 撤销再授予权限会强制系统重绑，这里的两个方法用代码实现等效操作。
 */
object ListenerRecovery {

    private const val TAG = "ListenerRecovery"

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
     * 强刷绑定：disable → 延迟 → enable 自身组件，强制系统解绑后重绑。
     * App enable/disable 自己的组件无需额外权限（AGENTS.md 中的 SecurityException
     * 警告只针对 shell `pm enable`，不影响应用自身调用）。
     * 为挂起函数，需在协程中调用。
     */
    suspend fun toggleComponentRebind(context: Context) {
        setComponentEnabled(context, false)
        delay(2000) // 给系统足够时间完成解绑
        setComponentEnabled(context, true)
        Log.i(TAG, "组件 toggle 重绑完成。")
    }

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
