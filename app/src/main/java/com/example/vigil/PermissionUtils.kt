package com.example.vigil

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    private const val TAG = "PermissionUtils"
    const val REQUEST_CODE_NOTIFICATION_LISTENER = 1001
    const val REQUEST_CODE_POST_NOTIFICATIONS = 1004


    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    /**
     * 打开通知使用权设置页。
     * 引导说明由 Compose 弹窗（PermissionGuideDialog）承载，确认后调用本函数直接跳转。
     */
    fun openNotificationListenerSettings(activity: Activity) {
        Log.d(TAG, "正在引导用户前往设置页面授予通知读取权限")
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (activity is MainActivity) {
            activity.appSettingsLauncher.launch(intent)
        } else {
            activity.startActivityForResult(intent, REQUEST_CODE_NOTIFICATION_LISTENER)
        }
    }

    fun isDndAccessGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.isNotificationPolicyAccessGranted
        }
        return true
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求将应用加入电池优化白名单。
     * 引导说明由 Compose 弹窗（PermissionGuideDialog）承载，确认后调用本函数；
     * 无法直接请求时回退到电池优化设置列表页。
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        Log.d(TAG, "正在引导用户将应用加入电池优化白名单")
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

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
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
     * 打开本应用系统详情页。"后台运行"引导（Compose 弹窗）确认后进入，
     * 由用户按弹窗文案将省电策略/后台耗电管理设为"无限制/允许后台运行"。
     */
    fun openAppDetailsSettings(activity: Activity) {
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
