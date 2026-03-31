package com.example.vigil

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    private const val TAG = "PermissionUtils"
    const val REQUEST_CODE_NOTIFICATION_LISTENER = 1001
    const val REQUEST_CODE_DND_ACCESS = 1002
    const val REQUEST_CODE_POST_NOTIFICATIONS = 1004


    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    fun requestNotificationListenerPermission(activity: Activity) {
        Log.d(TAG, "正在引导用户前往设置页面授予通知读取权限")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.isNotificationPolicyAccessGranted
        }
        return true
    }

    fun requestDndAccessPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "正在引导用户前往设置页面授予勿扰模式读取权限")
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

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestPostNotificationsPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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

