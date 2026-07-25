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

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        Log.d(TAG, "正在引导用户将应用加入电池优化白名单")
        AlertDialog.Builder(activity)
            .setTitle(R.string.battery_optimization_dialog_title)
            .setMessage(R.string.battery_optimization_dialog_message)
            .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${activity.packageName}")
                    )
                    if (activity is MainActivity) {
                        activity.appSettingsLauncher.launch(intent)
                    } else {
                        activity.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "无法直接请求忽略电池优化，回退到电池优化设置列表页", e)
                    try {
                        activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        Log.e(TAG, "无法打开电池优化设置页面", e2)
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
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

    /**
     * 打开"自启动管理"设置页（国产 ROM 通用）。
     * 国产 ROM（小米/华为/OPPO/vivo/荣耀等）默认禁止应用自启动，进程被系统清理后
     * 服务无法及时重建。各厂商设置页 intent 不统一，按序尝试已知的厂商页面，
     * 全部失败则回退到本应用详情页，由用户手动查找"自启动"选项。
     */
    fun openAutoStartSettings(activity: Activity) {
        val attempts = listOf(
            // 小米 MIUI / HyperOS
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            // 华为 EMUI / 鸿蒙
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            // OPPO ColorOS
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // vivo OriginOS / Funtouch
            Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"),
            // 荣耀
            Intent().setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        for (intent in attempts) {
            try {
                activity.startActivity(intent)
                Log.i(TAG, "已打开自启动管理页面: $intent")
                return
            } catch (e: Exception) {
                Log.d(TAG, "自启动页面不可用，尝试下一个: $intent")
            }
        }
        Log.w(TAG, "未找到可用的自启动管理页面，回退到应用详情页")
        openAppDetailsSettings(activity)
    }

    /**
     * 打开"后台运行/省电策略"引导。各厂商无稳定直达 intent，统一跳应用详情页，
     * 由对话框文案引导用户将省电策略/后台耗电管理设为"无限制/允许后台运行"。
     */
    fun openBackgroundRunSettings(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.background_run_dialog_title)
            .setMessage(R.string.background_run_dialog_message)
            .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                openAppDetailsSettings(activity)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun openAppDetailsSettings(activity: Activity) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开应用详情页", e)
        }
    }

}

