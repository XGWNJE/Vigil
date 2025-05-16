package com.example.vigil

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager // 新增：用于检查OP_SYSTEM_ALERT_WINDOW等操作权限
import android.app.NotificationManager
import android.content.ComponentName // 新增：用于构造MIUI Intent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast // 新增：用于显示无法跳转的提示
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader // 新增
import java.io.IOException // 新增
import java.io.InputStreamReader // 新增

object PermissionUtils {

    private const val TAG = "PermissionUtils"
    const val REQUEST_CODE_NOTIFICATION_LISTENER = 1001
    const val REQUEST_CODE_DND_ACCESS = 1002
    const val REQUEST_CODE_OVERLAY_PERMISSION = 1003
    const val REQUEST_CODE_POST_NOTIFICATIONS = 1004
    // MIUI 相关常量
    private const val MIUI_ROM_VERSION_PROPERTY = "ro.miui.ui.version.name"
    private const val MIUI_V6 = "V6"
    private const val MIUI_V7 = "V7"
    private const val MIUI_V8 = "V8"
    private const val MIUI_V9 = "V9"
    private const val MIUI_V10 = "V10"
    private const val MIUI_V11 = "V11"
    private const val MIUI_V12 = "V12"
    private const val MIUI_V13 = "V13"
    private const val MIUI_V14 = "V14"


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

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                Log.d(TAG, "正在引导用户前往设置页面授予悬浮窗权限")
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

    // --- MIUI 后台弹窗权限相关 ---

    /**
     * 检查当前系统是否为 MIUI。
     */
    fun isMiui(): Boolean {
        return !getSystemProperty(MIUI_ROM_VERSION_PROPERTY).isNullOrBlank()
    }

    private fun getSystemProperty(propName: String): String? {
        val line: String
        var input: BufferedReader? = null
        try {
            val p = Runtime.getRuntime().exec("getprop $propName")
            input = BufferedReader(InputStreamReader(p.inputStream), 1024)
            line = input.readLine()
            input.close()
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to read sysprop $propName", ex)
            return null
        } finally {
            if (input != null) {
                try {
                    input.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Exception while closing InputStream", e)
                }
            }
        }
        return line
    }

    /**
     * 尝试检查 MIUI 后台弹出界面权限。
     * 注意：这只是一个启发式的方法，可能不完全准确，因为 MIUI 没有标准的 API 来检查这个特定权限。
     * 我们这里使用 AppOpsManager 检查 OP_SYSTEM_ALERT_WINDOW (悬浮窗权限) 和 OP_BACKGROUND_START_ACTIVITY
     * (后台启动Activity权限，如果能获取到的话) 作为一种间接的判断。
     * 对于MIUI，"后台弹出界面" 更像是一个组合权限或特定开关。
     *
     * @return 0 (MODE_ALLOWED), 1 (MODE_IGNORED), 2 (MODE_ERRORED), 默认返回 true (认为已授予或不适用) 如果无法准确判断。
     * 更可靠的做法是让用户自行判断。
     */
    fun checkMiuiBackgroundPopupPermissionStatus(context: Context): Int {
        if (!isMiui()) return AppOpsManager.MODE_ALLOWED // 非MIUI则认为允许

        // 在MIUI上，"后台弹出界面"权限通常与悬浮窗权限和自启动管理相关联
        // 但没有直接的API可以查询这个特定的“后台弹出界面”开关状态
        // 我们可以检查 SYSTEM_ALERT_WINDOW，它是一个强相关的权限
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) {
                AppOpsManager.MODE_ALLOWED
            } else {
                // 如果悬浮窗权限没有，那么后台弹出基本不可能
                AppOpsManager.MODE_IGNORED
            }
        } else {
            AppOpsManager.MODE_ALLOWED // 旧版本认为允许
        }
        // 更准确的检测需要针对性的MIUI内部API，这里我们简化处理
        // 返回一个值让UI知道需要用户去设置，而不是直接判断是否开启
    }


    /**
     * 尝试跳转到 MIUI 的后台弹出界面权限设置页面。
     * 由于 MIUI 版本和设置路径的多样性，会尝试多种常见的 Intent。
     */
    fun requestMiuiBackgroundPopupPermission(activity: Activity) {
        if (!isMiui()) return

        val intents = mutableListOf<Intent>()
        val packageName = activity.packageName

        // 通用权限设置页
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })

        // MIUI 特定权限管理页面 (尝试不同版本)
        // 小米后台弹出界面权限 (MIUI 10/11/12/13/14 常见路径)
        intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            putExtra("extra_pkgname", packageName)
        })
        intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
            putExtra("extra_pkgname", packageName)
        })
        // 另一个可能的路径 (旧版本或特定设备)
        intents.add(Intent("miui.intent.action.OP_AUTO_START").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra("extra_pkgname", packageName)
        })
        // 尝试跳转到应用信息页，用户可以从中找到权限设置
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })


        var success = false
        for (intent in intents) {
            try {
                Log.d(TAG, "尝试跳转到MIUI权限设置: ${intent.component?.className ?: intent.action}")
                if (activity is MainActivity) {
                    activity.appSettingsLauncher.launch(intent) // 使用 ActivityResultLauncher
                } else {
                    activity.startActivity(intent) // 兼容非 MainActivity 的情况
                }
                success = true
                break // 如果成功跳转，则不再尝试其他 Intent
            } catch (e: Exception) {
                Log.e(TAG, "跳转到MIUI权限设置失败 (Intent: ${intent.action}): ${e.message}")
            }
        }

        if (!success) {
            Log.w(TAG, "所有尝试跳转到MIUI权限设置页面的Intent都失败了。")
            Toast.makeText(activity, R.string.error_opening_miui_settings, Toast.LENGTH_LONG).show()
            // 作为最后的备选，可以只打开应用详情页
            try {
                val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (activity is MainActivity) {
                    activity.appSettingsLauncher.launch(detailsIntent)
                } else {
                    activity.startActivity(detailsIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "最终尝试跳转到应用详情页也失败了: ${e.message}")
            }
        }
    }
}
