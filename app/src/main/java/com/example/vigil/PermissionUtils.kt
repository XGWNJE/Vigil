package com.example.vigil

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager // 导入 PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat // 导入 ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat // 导入 ContextCompat

object PermissionUtils {

    const val REQUEST_CODE_NOTIFICATION_LISTENER = 1001
    const val REQUEST_CODE_DND_ACCESS = 1002
    const val REQUEST_CODE_OVERLAY_PERMISSION = 1003
    const val REQUEST_CODE_POST_NOTIFICATIONS = 1004 // 新增

    fun isNotificationListenerEnabled(context: Context): Boolean {
        // ... (代码不变)
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    fun requestNotificationListenerPermission(activity: Activity) {
        // ... (代码不变)
        Log.d("PermissionUtils", "正在引导用户前往设置页面授予通知读取权限")
        AlertDialog.Builder(activity)
            .setTitle(R.string.notification_permission_dialog_title)
            .setMessage(R.string.notification_permission_dialog_message)
            .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                if (activity is MainActivity) {
                    activity.appSettingsLauncher.launch(intent)
                } else {
                    activity.startActivityForResult(intent, REQUEST_CODE_NOTIFICATION_LISTENER)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    fun isDndAccessGranted(context: Context): Boolean {
        // ... (代码不变)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.isNotificationPolicyAccessGranted
        }
        return true
    }

    fun requestDndAccessPermission(activity: Activity) {
        // ... (代码不变)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d("PermissionUtils", "正在引导用户前往设置页面授予勿扰模式读取权限")
            AlertDialog.Builder(activity)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.dnd_permission_dialog_message)
                .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    if (activity is MainActivity) {
                        activity.appSettingsLauncher.launch(intent)
                    } else {
                        activity.startActivityForResult(intent, REQUEST_CODE_DND_ACCESS)
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        // ... (代码不变)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(activity: Activity) {
        // ... (代码不变)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                Log.d("PermissionUtils", "正在引导用户前往设置页面授予悬浮窗权限")
                AlertDialog.Builder(activity)
                    .setTitle(R.string.overlay_permission_dialog_title)
                    .setMessage(R.string.overlay_permission_dialog_message)
                    .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}")
                        )
                        if (activity is MainActivity) {
                            activity.appSettingsLauncher.launch(intent)
                        } else {
                            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
                        }
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
            }
        }
    }

    /**
     * 新增：检查应用是否具有发送通知的权限 (Android 13+)。
     * @param context 上下文。
     * @return 如果已授予权限或系统版本低于 Tiramisu，则返回 true；否则返回 false。
     */
    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // API 33 以下，此权限默认授予
        }
    }

    /**
     * 新增：请求用户授予发送通知的权限 (Android 13+)。
     * @param activity 发起请求的 Activity。
     */
    fun requestPostNotificationsPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 应该显示一个解释为什么需要此权限的对话框
                AlertDialog.Builder(activity)
                    .setTitle(R.string.post_notifications_permission_dialog_title)
                    .setMessage(R.string.post_notifications_permission_dialog_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_CODE_POST_NOTIFICATIONS
                        )
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
            }
        }
    }
}
