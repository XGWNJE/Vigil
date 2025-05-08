package com.example.vigil

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * PermissionUtils 包含权限检查和请求相关的辅助函数。
 */
object PermissionUtils {

    const val REQUEST_CODE_NOTIFICATION_LISTENER = 1001
    const val REQUEST_CODE_DND_ACCESS = 1002

    /**
     * 检查应用是否具有通知读取权限。
     *
     * @param context 上下文。
     * @return 如果已授予权限，则返回 true；否则返回 false。
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    /**
     * 请求用户授予通知读取权限。
     * 这会打开系统的权限设置页面。
     *
     * @param activity 发起请求的 Activity。
     */
    fun requestNotificationListenerPermission(activity: Activity) {
        // 中文日志：请求通知读取权限
        android.util.Log.d("PermissionUtils", "正在引导用户前往设置页面授予通知读取权限")
        AlertDialog.Builder(activity)
            .setTitle(R.string.notification_permission_dialog_title)
            .setMessage(R.string.notification_permission_dialog_message)
            .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                activity.startActivityForResult(intent, REQUEST_CODE_NOTIFICATION_LISTENER)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * 检查应用是否具有读取勿扰 (DND) 模式状态的权限。
     * 仅在 Android M (API 23) 及以上版本需要此权限。
     *
     * @param context 上下文。
     * @return 如果已授予权限或系统版本低于 M，则返回 true；否则返回 false。
     */
    fun isDndAccessGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.isNotificationPolicyAccessGranted
        }
        return true // API 23 以下不需要特殊权限
    }

    /**
     * 请求用户授予读取勿扰 (DND) 模式状态的权限。
     * 这会打开系统的权限设置页面。
     * 仅在 Android M (API 23) 及以上版本有效。
     *
     * @param activity 发起请求的 Activity。
     */
    fun requestDndAccessPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 中文日志：请求勿扰模式读取权限
            android.util.Log.d("PermissionUtils", "正在引导用户前往设置页面授予勿扰模式读取权限")
            AlertDialog.Builder(activity)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.dnd_permission_dialog_message)
                .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    activity.startActivityForResult(intent, REQUEST_CODE_DND_ACCESS)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }
}
