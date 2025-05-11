// src/main/java/com/example/vigil/MainActivity.kt
package com.example.vigil

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.view.isGone
import com.example.vigil.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var selectedRingtoneUri: Uri? = null

    companion object {
        private const val TAG = "VigilMainActivity"

        // 预设的通讯应用包名列表
        val PREDEFINED_COMMUNICATION_APPS = setOf(
            "com.tencent.mm",           // 微信
            "com.tencent.mobileqq",     // QQ
            "com.whatsapp",             // WhatsApp
            "com.facebook.orca",        // Messenger (Facebook)
            "org.telegram.messenger",   // Telegram
            "com.google.android.apps.messaging", // Google Messages
            "com.viber.voip",           // Viber
            "com.skype.raider",         // Skype
            "jp.naver.line.android",    // Line
            "com.snapchat.android",     // Snapchat
            "com.discord"               // Discord
            // 可以根据需要添加更多国内常用的通讯应用
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

        setSupportActionBar(binding.toolbar)
        sharedPreferencesHelper = SharedPreferencesHelper(this)

        appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "从系统设置页面返回，重新检查权限和更新UI。")
            // onResume 会处理 UI 更新和权限检查
        }

        setupUIListeners() // 先设置监听器
        loadSettings()     // 再加载设置以正确初始化UI
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
            saveSettings() // 保存所有设置，包括新的过滤开关状态
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
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
        }

        binding.buttonRestartService.setOnClickListener {
            Log.i(TAG, "用户点击尝试重启服务...")
            binding.buttonRestartService.isEnabled = false
            binding.textViewServiceStatus.text = getString(R.string.service_status_recovering)
            binding.textViewServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.neutral_text_color))

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

        // 新增: 应用过滤开关的监听器
        binding.switchFilterApps.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferencesHelper.saveFilterAppsEnabledState(isChecked)
            if (isChecked) {
                // 当启用过滤时，保存预设的通讯应用列表
                sharedPreferencesHelper.saveFilteredAppPackages(PREDEFINED_COMMUNICATION_APPS)
                binding.textViewFilterAppsSummary.text = getString(R.string.filter_apps_switch_summary_on)
                Log.i(TAG, "应用过滤已启用，监听预设通讯应用。")
            } else {
                // 当禁用过滤时，可以清空列表或服务会忽略此列表
                sharedPreferencesHelper.saveFilteredAppPackages(emptySet()) // 保存空集合表示不特别指定
                binding.textViewFilterAppsSummary.text = getString(R.string.filter_apps_switch_summary_off)
                Log.i(TAG, "应用过滤已禁用，监听所有应用。")
            }
            // 通知服务更新设置
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
        setCardInteractive(binding.cardAppFilter, allCoreFunctionalityPermissionsGranted) // 新增：应用过滤卡片也受核心权限影响
        binding.switchEnableService.isEnabled = allCoreFunctionalityPermissionsGranted
        binding.switchFilterApps.isEnabled = allCoreFunctionalityPermissionsGranted // 新增：应用过滤开关也受核心权限影响


        if (!allCoreFunctionalityPermissionsGranted) {
            if (binding.switchEnableService.isChecked) {
                Log.w(TAG, "核心权限不足，但服务开关为开。将关闭开关并保存状态。")
                binding.switchEnableService.isChecked = false
            }
            // 如果核心权限不足，也应该禁用应用过滤开关并更新其状态
            if (binding.switchFilterApps.isChecked) {
                binding.switchFilterApps.isChecked = false
                // sharedPreferencesHelper.saveFilterAppsEnabledState(false) // 开关监听器会处理
                // updateFilterAppsSummary(false) // loadSettings 或 updateUI 会处理
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
        if (requestCode == PermissionUtils.REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "发送通知权限已授予。")
                Toast.makeText(this, "发送通知权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "发送通知权限被拒绝。")
                Toast.makeText(this, "未授予发送通知权限，部分提醒功能可能受限。", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSettings() {
        binding.editTextKeywords.setText(sharedPreferencesHelper.getKeywords().joinToString(","))
        selectedRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        updateSelectedRingtoneUI()

        val serviceEnabledByUserIntent = sharedPreferencesHelper.getServiceEnabledState()
        binding.switchEnableService.isChecked = serviceEnabledByUserIntent

        // 新增: 加载应用过滤设置
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

        // 保存应用过滤开关的状态 (虽然其 listener 已经保存，但统一保存确保一致性)
        val filterAppsEnabled = binding.switchFilterApps.isChecked
        sharedPreferencesHelper.saveFilterAppsEnabledState(filterAppsEnabled)
        if (filterAppsEnabled) {
            sharedPreferencesHelper.saveFilteredAppPackages(PREDEFINED_COMMUNICATION_APPS)
        } else {
            sharedPreferencesHelper.saveFilteredAppPackages(emptySet())
        }

        Log.i(TAG, "设置已保存。关键词: ${keywords.size}个, 铃声: $selectedRingtoneUri, 应用过滤: $filterAppsEnabled")
        notifyServiceToUpdateSettings()
    }

    private fun notifyServiceToUpdateSettings() {
        // 仅当服务开关开启且核心权限满足时才通知服务更新
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
        updateServiceStatusUI()
        updateEnvironmentWarnings()
        // 新增: 更新应用过滤相关的UI状态
        updateFilterAppsSummary(binding.switchFilterApps.isChecked)
        // 确保应用过滤卡片及其内部控件的可交互性也由核心权限控制
        val corePermissionsGranted = PermissionUtils.isNotificationListenerEnabled(this) &&
                PermissionUtils.canDrawOverlays(this) &&
                PermissionUtils.canPostNotifications(this)
        setCardInteractive(binding.cardAppFilter, corePermissionsGranted)
        binding.switchFilterApps.isEnabled = corePermissionsGranted


        Log.d(TAG, "UI 已更新。")
    }

    private fun setCardInteractive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else 0.5f
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                // 特殊处理：MaterialSwitch 本身的 isEnabled 应该由其逻辑控制，而不是简单地被父卡片覆盖
                if (view.getChildAt(i) !is com.google.android.material.materialswitch.MaterialSwitch) {
                    view.getChildAt(i).isEnabled = enabled
                }
            }
        }
    }

    private fun updateServiceStatusUI() {
        val serviceEnabledByUser = binding.switchEnableService.isChecked
        val notificationAccessActuallyGranted = PermissionUtils.isNotificationListenerEnabled(this)

        var statusText: String
        var statusColor: Int
        var showRestartButton = false

        if (serviceEnabledByUser) {
            if (notificationAccessActuallyGranted) {
                statusText = getString(R.string.service_running)
                statusColor = ContextCompat.getColor(this, R.color.positive_text_color)

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
                statusColor = ContextCompat.getColor(this, R.color.warning_text_color)
                showRestartButton = true
                Log.w(TAG, "服务开关已启用，但系统层面通知监听器未启用。")
            }
        } else {
            statusText = getString(R.string.service_stopped)
            statusColor = ContextCompat.getColor(this, R.color.neutral_text_color)
        }

        binding.textViewServiceStatus.text = statusText
        binding.textViewServiceStatus.setTextColor(statusColor)
        binding.buttonRestartService.isGone = !showRestartButton

        Log.d(TAG, "UI服务状态更新: $statusText, 重启按钮: $showRestartButton")
    }


    private fun updateEnvironmentWarnings() {
        val warnings = EnvironmentChecker.getEnvironmentWarnings(this)
        binding.textViewEnvironmentWarnings.text = warnings
        binding.textViewEnvironmentWarnings.setTextColor(
            ContextCompat.getColor(this, if (warnings == getString(R.string.all_clear)) R.color.neutral_text_color else R.color.warning_text_color)
        )
    }

    // 新增: 更新应用过滤开关下方的摘要文本
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
    }

    private fun stopVigilService() {
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            stopService(serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试停止。")
        } catch (e: Exception) {
            Log.e(TAG, "停止 Vigil 服务时出错: ", e)
        }
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
