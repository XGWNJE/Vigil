// src/main/java/com/example/vigil/MainActivity.kt
package com.example.vigil

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isGone
import com.example.vigil.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var selectedRingtoneUri: Uri? = null

    companion object {
        private const val TAG = "VigilMainActivity"

        // Typo warnings for "mobileqq" and "naver" can be ignored as these are correct package names.
        val PREDEFINED_COMMUNICATION_APPS = setOf(
            "com.tencent.mm", "com.tencent.mobileqq", "com.whatsapp",
            "com.facebook.orca", "org.telegram.messenger", "com.google.android.apps.messaging",
            "com.viber.voip", "com.skype.raider", "jp.naver.line.android",
            "com.snapchat.android", "com.discord"
        )
    }

    internal lateinit var appSettingsLauncher: ActivityResultLauncher<Intent>

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                selectedRingtoneUri = uri
                updateSelectedRingtoneUI()
                Log.i(TAG, "用户${if (uri != null) "选择了新铃声: $uri" else "清除了铃声选择。"}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = insets.left, right = insets.right)
            WindowInsetsCompat.CONSUMED
        }

        sharedPreferencesHelper = SharedPreferencesHelper(this) // Redundant qualifier 'this' can be removed if preferred, but not an error.

        appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "从系统设置页面返回。")
            // onResume will handle UI updates.
        }

        setupUIListeners()
        loadSettings()
        handleIntentExtras(intent)
        Log.d(TAG, "MainActivity onCreate 完成。")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.getStringExtra("reason") == "missing_overlay_permission") {
            Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
            if (!PermissionUtils.canDrawOverlays(this)) {
                PermissionUtils.requestOverlayPermission(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume。")
        checkAndRequestPermissions()
        updateUI()
    }

    private fun setupUIListeners() {
        binding.buttonGrantNotificationAccess.setOnClickListener {
            PermissionUtils.requestNotificationListenerPermission(this)
        }
        binding.buttonGrantDndAccess.setOnClickListener {
            PermissionUtils.requestDndAccessPermission(this)
        }
        binding.buttonGrantOverlayAccess.setOnClickListener {
            PermissionUtils.requestOverlayPermission(this)
        }
        binding.buttonGrantPostNotifications.setOnClickListener {
            PermissionUtils.requestPostNotificationsPermission(this)
        }

        binding.buttonSelectRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.ringtone_selection_title))
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            }
            try {
                ringtonePickerLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "无法启动铃声选择器", e)
                Toast.makeText(this, "无法打开铃声选择器: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            updateEnvironmentWarnings()
            Log.d(TAG, "设置已保存，环境警告已刷新。")
        }

        binding.switchEnableService.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferencesHelper.saveServiceEnabledState(isChecked)
            if (isChecked) {
                Log.i(TAG, "服务开关已打开，尝试启动服务（如果权限允许）。")
                startVigilService()
            } else {
                Log.i(TAG, "服务开关已关闭，停止服务。")
                stopVigilService()
            }
            updateServiceStatusUI()
            updateEnvironmentWarnings()
            Log.d(TAG, "服务开关状态改变，环境警告已刷新。")
        }

        binding.buttonRestartService.setOnClickListener {
            Log.i(TAG, "用户点击尝试重启服务...")
            binding.buttonRestartService.isEnabled = false
            binding.textViewServiceStatus.text = getString(R.string.service_status_recovering)
            val neutralColorRes = if (isDarkThemeActive()) R.color.status_neutral_grey_dark else R.color.status_neutral_grey_light
            binding.textViewServiceStatus.setTextColor(ContextCompat.getColor(this, neutralColorRes))

            setNotificationListenerServiceComponentEnabled(false)

            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "重新启用服务组件...")
                setNotificationListenerServiceComponentEnabled(true)

                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "尝试启动服务并更新UI...")
                    if (binding.switchEnableService.isChecked && PermissionUtils.isNotificationListenerEnabled(this)) {
                        startVigilService()
                    }
                    updateUI()
                    binding.buttonRestartService.isEnabled = true
                }, 700)
            }, 300)
        }

        binding.switchFilterApps.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferencesHelper.saveFilterAppsEnabledState(isChecked)
            if (isChecked) {
                sharedPreferencesHelper.saveFilteredAppPackages(PREDEFINED_COMMUNICATION_APPS)
                binding.textViewFilterAppsSummary.text = getString(R.string.filter_apps_switch_summary_on)
                Log.i(TAG, "应用过滤已启用，监听预设通讯应用。")
            } else {
                sharedPreferencesHelper.saveFilteredAppPackages(emptySet())
                binding.textViewFilterAppsSummary.text = getString(R.string.filter_apps_switch_summary_off)
                Log.i(TAG, "应用过滤已禁用，监听所有应用。")
            }
            notifyServiceToUpdateSettings()
        }
        Log.d(TAG, "UI 监听器已设置。")
    }

    private fun checkAndRequestPermissions() {
        val notificationAccessGranted = PermissionUtils.isNotificationListenerEnabled(this)
        binding.buttonGrantNotificationAccess.isGone = notificationAccessGranted

        val dndAccessGranted = PermissionUtils.isDndAccessGranted(this)
        binding.buttonGrantDndAccess.isGone = dndAccessGranted

        val overlayAccessGranted = PermissionUtils.canDrawOverlays(this)
        binding.buttonGrantOverlayAccess.isGone = overlayAccessGranted

        val postNotificationsAccessGranted = PermissionUtils.canPostNotifications(this)
        binding.buttonGrantPostNotifications.isGone = postNotificationsAccessGranted

        val allPermissionButtonsGone = binding.buttonGrantNotificationAccess.isGone &&
                binding.buttonGrantDndAccess.isGone &&
                binding.buttonGrantOverlayAccess.isGone &&
                binding.buttonGrantPostNotifications.isGone

        binding.cardPermissions.isGone = allPermissionButtonsGone

        val allCoreFunctionalityPermissionsGranted = notificationAccessGranted &&
                overlayAccessGranted &&
                postNotificationsAccessGranted

        setCardInteractive(binding.cardConfiguration, allCoreFunctionalityPermissionsGranted)
        setCardInteractive(binding.cardServiceControl, allCoreFunctionalityPermissionsGranted)
        setCardInteractive(binding.cardAppFilter, allCoreFunctionalityPermissionsGranted)
        binding.switchEnableService.isEnabled = allCoreFunctionalityPermissionsGranted
        binding.switchFilterApps.isEnabled = allCoreFunctionalityPermissionsGranted


        if (!allCoreFunctionalityPermissionsGranted) {
            if (binding.switchEnableService.isChecked) {
                Log.w(TAG, "核心权限不足，但服务开关为开。将关闭开关并保存状态。")
                binding.switchEnableService.isChecked = false
            }
            if (binding.switchFilterApps.isChecked) {
                binding.switchFilterApps.isChecked = false
            }
        }
        Log.i(TAG, "权限检查完成。通知: $notificationAccessGranted, DND: $dndAccessGranted, 悬浮窗: $overlayAccessGranted, 发送通知: $postNotificationsAccessGranted")
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")
        var permissionsChanged = false // Flag to see if any relevant permission changed
        if (requestCode == PermissionUtils.REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "发送通知权限已授予。")
                Toast.makeText(this, "发送通知权限已获取", Toast.LENGTH_SHORT).show()
                permissionsChanged = true
            } else {
                Log.w(TAG, "发送通知权限被拒绝。")
                Toast.makeText(this, "未授予发送通知权限，部分提醒功能可能受限。", Toast.LENGTH_LONG).show()
            }
        }
        // Add similar checks for other permission request codes if they become relevant
        // e.g., if (requestCode == PermissionUtils.REQUEST_CODE_DND_ACCESS) { ... permissionsChanged = true }

        if (permissionsChanged) {
            // If a relevant permission changed, a full UI update (which includes environment warnings)
            // is good practice. onResume will handle this, but an explicit call ensures immediate refresh.
            updateUI()
            Log.d(TAG, "权限结果返回并且相关权限已更改，UI 已刷新。")
        } else {
            Log.d(TAG, "权限结果返回，但未检测到影响环境警告的直接权限更改（或将在onResume中刷新）。")
        }
    }

    private fun loadSettings() {
        binding.editTextKeywords.setText(sharedPreferencesHelper.getKeywords().joinToString(","))
        selectedRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        updateSelectedRingtoneUI()

        val serviceEnabledByUserIntent = sharedPreferencesHelper.getServiceEnabledState()
        binding.switchEnableService.isChecked = serviceEnabledByUserIntent

        val filterAppsEnabled = sharedPreferencesHelper.getFilterAppsEnabledState()
        binding.switchFilterApps.isChecked = filterAppsEnabled
        updateFilterAppsSummary(filterAppsEnabled)

        Log.d(TAG, "设置已加载到 UI。用户意图启用服务: $serviceEnabledByUserIntent, 应用过滤启用: $filterAppsEnabled")
    }

    private fun saveSettings() {
        val keywordsText = binding.editTextKeywords.text.toString()
        val keywords = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        sharedPreferencesHelper.saveKeywords(keywords)
        sharedPreferencesHelper.saveRingtoneUri(selectedRingtoneUri)

        val filterAppsEnabled = binding.switchFilterApps.isChecked
        sharedPreferencesHelper.saveFilterAppsEnabledState(filterAppsEnabled)
        if (filterAppsEnabled) {
            sharedPreferencesHelper.saveFilteredAppPackages(PREDEFINED_COMMUNICATION_APPS)
        } else {
            sharedPreferencesHelper.saveFilteredAppPackages(emptySet())
        }

        Log.i(TAG, "设置已保存。关键词: ${keywords.size}个, 铃声: $selectedRingtoneUri, 应用过滤: $filterAppsEnabled")
        notifyServiceToUpdateSettings()
        // updateEnvironmentWarnings() is already called in the onClickListener for saveSettings button
    }

    private fun notifyServiceToUpdateSettings() {
        if (binding.switchEnableService.isChecked && PermissionUtils.isNotificationListenerEnabled(this)) {
            val intent = Intent(this, MyNotificationListenerService::class.java).apply {
                action = MyNotificationListenerService.ACTION_UPDATE_SETTINGS
            }
            try {
                startService(intent)
                Log.d(TAG, "已发送 ACTION_UPDATE_SETTINGS 到服务。")
            } catch (e: Exception) {
                Log.e(TAG, "启动服务以更新设置时出错: ", e)
            }
        }
    }


    private fun updateSelectedRingtoneUI() {
        val ringtoneName = if (selectedRingtoneUri != null) {
            try {
                RingtoneManager.getRingtone(this, selectedRingtoneUri)?.getTitle(this) ?: getString(R.string.default_ringtone_name)
            } catch (e: Exception) {
                Log.e(TAG, "获取铃声标题时出错: $selectedRingtoneUri", e)
                "未知铃声"
            }
        } else {
            null
        }

        if (ringtoneName != null) {
            binding.textViewSelectedRingtone.text = getString(R.string.selected_ringtone_label, ringtoneName)
        } else {
            binding.textViewSelectedRingtone.text = getString(R.string.no_ringtone_selected)
        }
    }

    private fun updateUI() {
        Log.d(TAG, "updateUI 调用。")
        updateServiceStatusUI()
        updateEnvironmentWarnings()
        updateFilterAppsSummary(binding.switchFilterApps.isChecked)
        val corePermissionsGranted = PermissionUtils.isNotificationListenerEnabled(this) &&
                PermissionUtils.canDrawOverlays(this) &&
                PermissionUtils.canPostNotifications(this)
        setCardInteractive(binding.cardAppFilter, corePermissionsGranted)
        binding.switchFilterApps.isEnabled = corePermissionsGranted
    }

    private fun setCardInteractive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else 0.5f
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                if (view.getChildAt(i) !is com.google.android.material.materialswitch.MaterialSwitch) {
                    view.getChildAt(i).isEnabled = enabled
                }
            }
        }
    }

    private fun isDarkThemeActive(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateServiceStatusUI() {
        val serviceEnabledByUser = binding.switchEnableService.isChecked
        val notificationAccessActuallyGranted = PermissionUtils.isNotificationListenerEnabled(this)

        var statusText: String
        val statusColorRes: Int
        var showRestartButton = false

        val isDark = isDarkThemeActive()

        if (serviceEnabledByUser) {
            if (notificationAccessActuallyGranted) {
                statusText = getString(R.string.service_running)
                statusColorRes = if (isDark) R.color.status_positive_green_dark else R.color.status_positive_green_light

                val missingAlertPermissions = mutableListOf<String>()
                if (!PermissionUtils.canDrawOverlays(this)) missingAlertPermissions.add("悬浮窗")
                if (!PermissionUtils.canPostNotifications(this)) {
                    missingAlertPermissions.add("发送通知")
                }
                if (missingAlertPermissions.isNotEmpty()) {
                    statusText += " (" + getString(R.string.service_alert_limited_permissions, missingAlertPermissions.joinToString("、")) + ")"
                }
            } else {
                statusText = getString(R.string.service_status_abnormal)
                statusColorRes = if (isDark) R.color.status_negative_red_dark else R.color.status_negative_red_light
                showRestartButton = true
                Log.w(TAG, "服务开关已启用，但系统层面通知监听器未启用。")
            }
        } else {
            statusText = getString(R.string.service_stopped)
            statusColorRes = if (isDark) R.color.status_neutral_grey_dark else R.color.status_neutral_grey_light
        }

        binding.textViewServiceStatus.text = statusText
        binding.textViewServiceStatus.setTextColor(ContextCompat.getColor(this, statusColorRes))
        binding.buttonRestartService.isGone = !showRestartButton

        Log.d(TAG, "UI服务状态更新: $statusText, 重启按钮: $showRestartButton")
    }


    private fun updateEnvironmentWarnings() {
        val warnings = EnvironmentChecker.getEnvironmentWarnings(this)
        binding.textViewEnvironmentWarnings.text = warnings
        Log.d(TAG, "环境警告已更新: $warnings")

        val warningColorResId: Int = if (warnings == getString(R.string.all_clear)) {
            // For "all clear", use the theme's onSurfaceVariant color
            // This color (md_theme_onSurfaceVariant) should be defined for both light and dark themes
            R.color.md_theme_onSurfaceVariant
        } else {
            // For warnings, use the specific red status color based on the theme
            if (isDarkThemeActive()) R.color.status_negative_red_dark else R.color.status_negative_red_light
        }
        binding.textViewEnvironmentWarnings.setTextColor(ContextCompat.getColor(this, warningColorResId))
    }


    private fun updateFilterAppsSummary(isFilterEnabled: Boolean) {
        binding.textViewFilterAppsSummary.text = if (isFilterEnabled) {
            getString(R.string.filter_apps_switch_summary_on)
        } else {
            getString(R.string.filter_apps_switch_summary_off)
        }
    }

    private fun startVigilService() {
        if (!PermissionUtils.isNotificationListenerEnabled(this)) {
            Log.w(TAG, "尝试启动服务，但通知读取权限未授予。")
            updateUI()
            return
        }

        setNotificationListenerServiceComponentEnabled(true)
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            startForegroundService(serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试启动。")
        } catch (e: Exception) {
            Log.e(TAG, "启动 Vigil 服务时出错: ", e)
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
            if (binding.switchEnableService.isChecked) {
                binding.switchEnableService.isChecked = false
            }
        }
        updateServiceStatusUI()
        updateEnvironmentWarnings()
    }

    private fun stopVigilService() {
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            stopService(serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试停止。")
        } catch (e: Exception) {
            Log.e(TAG, "停止 Vigil 服务时出错: ", e)
        }
        updateServiceStatusUI()
        updateEnvironmentWarnings()
    }

    private fun setNotificationListenerServiceComponentEnabled(enabled: Boolean) {
        val componentName = ComponentName(this, MyNotificationListenerService::class.java)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            if (packageManager.getComponentEnabledSetting(componentName) != newState) {
                packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
                Log.i(TAG, "MyNotificationListenerService 组件状态设置为: ${if (enabled) "启用" else "禁用"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置服务组件状态时出错: ", e)
        }
    }
}
