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
// import android.widget.TextView // TextView 已通过 binding.tvAppTitleGithub 访问，无需单独导入
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
import com.example.vigil.LicenseManager // 确保添加了此导入语句
import java.text.SimpleDateFormat
import java.util.* // 导入 Date 和 Locale 用于日期格式化

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var selectedRingtoneUri: Uri? = null

    // LicenseManager 实例
    private lateinit var licenseManager: LicenseManager

    companion object {
        private const val TAG = "VigilMainActivity"

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

        // 设置标题点击跳转到 GitHub
        setupGithubLinkForTitle()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { view, windowInsets -> // 确保 ID 是 main_container
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left,
                right = insets.right,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        sharedPreferencesHelper = SharedPreferencesHelper(this)
        licenseManager = LicenseManager() // 初始化 LicenseManager

        appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "从系统设置页面返回。")
            // onResume will handle UI updates.
        }

        setupUIListeners()
        loadSettings()
        handleIntentExtras(intent)
        Log.d(TAG, "MainActivity onCreate 完成。")
    }

    private fun setupGithubLinkForTitle() {
        binding.tvAppTitleGithub.setOnClickListener {
            val githubUrl = getString(R.string.github_url)
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "无法打开 GitHub 链接: $githubUrl", e)
                Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
            }
        }
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
        // 在 onResume 中加载并显示保存的授权状态，并更新功能可用性
        loadLicenseStatusAndUpdateUI()
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
            // 检查授权状态，如果未授权且尝试开启服务，则阻止
            val isLicensed = sharedPreferencesHelper.isAuthenticated()
            if (isChecked && !isLicensed) {
                Log.w(TAG, "尝试开启服务但未授权。")
                Toast.makeText(this, "请先激活授权码以启用服务", Toast.LENGTH_SHORT).show()
                binding.switchEnableService.isChecked = false // 阻止开关开启
                // 不保存状态，因为用户意图开启但被阻止
            } else {
                // 授权有效或用户尝试关闭服务，保存状态
                sharedPreferencesHelper.saveServiceEnabledState(isChecked)
                if (isChecked) {
                    Log.i(TAG, "服务开关已打开，尝试启动服务（如果权限允许）。")
                    startVigilService()
                } else {
                    Log.i(TAG, "服务开关已关闭，停止服务。")
                    stopVigilService()
                }
            }
            updateServiceStatusUI() // 无论如何都更新服务状态UI
            Log.d(TAG, "服务开关状态改变为: $isChecked。")
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
            // 检查授权状态，如果未授权，则阻止
            val isLicensed = sharedPreferencesHelper.isAuthenticated()
            if (isChecked && !isLicensed) {
                Log.w(TAG, "尝试启用应用过滤但未授权。")
                Toast.makeText(this, "请先激活授权码以启用应用过滤", Toast.LENGTH_SHORT).show()
                binding.switchFilterApps.isChecked = false // 阻止开关开启
                // 不保存状态
            } else {
                // 授权有效或用户尝试关闭，保存状态
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
        }

        // 修改: 激活授权按钮点击监听器，调用 verifyLicense 方法并保存状态
        binding.buttonActivateLicense.setOnClickListener {
            val licenseKey = binding.editTextLicenseKey.text.toString().trim()
            if (licenseKey.isEmpty()) {
                Toast.makeText(this, "请输入授权码", Toast.LENGTH_SHORT).show()
                updateLicenseStatusUI(null) // 输入为空时显示未授权状态（通过 null）
                sharedPreferencesHelper.saveLicenseStatus(null) // 清除授权状态
                updateFeatureAvailabilityUI(false) // 禁用高级功能 UI
                return@setOnClickListener
            }

            // 调用 LicenseManager 进行验证
            val licensePayload = licenseManager.verifyLicense(licenseKey, packageName)

            if (licensePayload != null) {
                // 授权码有效
                Log.i(TAG, "授权码验证成功: $licensePayload")
                sharedPreferencesHelper.saveLicenseStatus(licensePayload) // 保存授权状态
                updateLicenseStatusUI(licensePayload) // 更新 UI 显示授权信息
                Toast.makeText(this, R.string.license_status_valid_premium, Toast.LENGTH_SHORT).show()
                updateFeatureAvailabilityUI(true) // 启用高级功能 UI
                // 如果服务开关已打开但因无授权而未运行，尝试启动服务
                if (binding.switchEnableService.isChecked && !PermissionUtils.isNotificationListenerEnabled(this)) {
                    startVigilService()
                }

            } else {
                // 授权码无效（格式错误、签名不匹配、appId 不匹配或已过期）
                Log.w(TAG, "授权码验证失败。")
                sharedPreferencesHelper.saveLicenseStatus(null) // 清除授权状态
                // TODO: 根据 LicenseManager 内部的日志判断具体失败原因，更新 UI 提示
                updateLicenseStatusUI(getString(R.string.license_status_invalid)) // 显示无效状态
                Toast.makeText(this, R.string.license_status_invalid, Toast.LENGTH_SHORT).show()
                updateFeatureAvailabilityUI(false) // 禁用高级功能 UI
                // 如果服务正在运行（尽管不应该），考虑停止它（此场景较少见，因为无权限通常无法启动）
                if (PermissionUtils.isNotificationListenerEnabled(this)) {
                    // stopVigilService() // 暂不自动停止，避免用户误解
                }
            }
            updateServiceStatusUI() // 更新服务状态 UI (可能因为授权状态改变)
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

        // 核心功能权限状态不受授权码影响，但高级功能UI的启用受授权码影响
        val allCoreFunctionalityPermissionsGranted = notificationAccessGranted &&
                overlayAccessGranted &&
                postNotificationsAccessGranted

        // setCardInteractive(binding.cardConfiguration, allCoreFunctionalityPermissionsGranted) // 由 updateFeatureAvailabilityUI 控制
        // setCardInteractive(binding.cardServiceControl, allCoreFunctionalityPermissionsGranted) // 由 updateFeatureAvailabilityUI 控制
        // setCardInteractive(binding.cardAppFilter, allCoreFunctionalityPermissionsGranted) // 由 updateFeatureAvailabilityUI 控制
        // binding.switchEnableService.isEnabled = allCoreFunctionalityPermissionsGranted // 由 updateFeatureAvailabilityUI 控制
        // binding.switchFilterApps.isEnabled = allCoreFunctionalityPermissionsGranted // 由 updateFeatureAvailabilityUI 控制
        // 授权卡片始终启用
        setCardInteractive(binding.cardLicenseKey, true)


        if (!allCoreFunctionalityPermissionsGranted) {
            if (binding.switchEnableService.isChecked) {
                Log.w(TAG, "核心权限不足，但服务开关为开。将关闭开关并保存状态。")
                binding.switchEnableService.isChecked = false
                sharedPreferencesHelper.saveServiceEnabledState(false) // 保存关闭状态
            }
            // if (binding.switchFilterApps.isChecked) { // isEnabled 会处理显示，无需强制关闭
            //     binding.switchFilterApps.isChecked = false
            // }
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
        var permissionsChanged = false
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

        if (permissionsChanged) {
            // onResume will be called after this, which calls updateUI().
            // So, explicit updateUI() call might be redundant here but ensures immediate refresh.
            // updateUI()
            Log.d(TAG, "权限结果返回并且相关权限已更改，UI 将在 onResume 中刷新。")
        } else {
            Log.d(TAG, "权限结果返回，但未检测到影响环境警告的直接权限更改。")
        }
    }

    private fun loadSettings() {
        binding.editTextKeywords.setText(sharedPreferencesHelper.getKeywords().joinToString(","))
        selectedRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        updateSelectedRingtoneUI()

        val serviceEnabledByUserIntent = sharedPreferencesHelper.getServiceEnabledState()
        if(binding.switchEnableService.isChecked != serviceEnabledByUserIntent) { // 避免不必要的监听器触发
            binding.switchEnableService.isChecked = serviceEnabledByUserIntent
        }

        val filterAppsEnabled = sharedPreferencesHelper.getFilterAppsEnabledState()
        if(binding.switchFilterApps.isChecked != filterAppsEnabled) { // 避免不必要的监听器触发
            binding.switchFilterApps.isChecked = filterAppsEnabled
        }
        // updateFilterAppsSummary(filterAppsEnabled) // updateUI 会调用它

        // 授权状态在 onResume 中加载和更新 UI
        Log.d(TAG, "设置已加载到 UI (不包括授权状态)。用户意图启用服务: $serviceEnabledByUserIntent, 应用过滤启用: $filterAppsEnabled")
    }

    // 新增: 加载授权状态并更新 UI 及功能可用性
    private fun loadLicenseStatusAndUpdateUI() {
        val licensePayload = sharedPreferencesHelper.getLicenseStatus()
        updateLicenseStatusUI(licensePayload) // 更新授权状态 UI
        updateFeatureAvailabilityUI(licensePayload != null) // 根据授权是否存在更新功能可用性
        Log.d(TAG, "加载授权状态并更新UI及功能可用性完成。授权有效: ${licensePayload != null}")
    }


    private fun saveSettings() {
        val keywordsText = binding.editTextKeywords.text.toString()
        val keywords = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        sharedPreferencesHelper.saveKeywords(keywords)
        sharedPreferencesHelper.saveRingtoneUri(selectedRingtoneUri)

        // 应用过滤状态由其 Switch 监听器直接保存
        // val filterAppsEnabled = binding.switchFilterApps.isChecked
        // sharedPreferencesHelper.saveFilterAppsEnabledState(filterAppsEnabled)
        // if (filterAppsEnabled) {
        //     sharedPreferencesHelper.saveFilteredAppPackages(PREDEFINED_COMMUNICATION_APPS)
        // } else {
        //     sharedPreferencesHelper.saveFilteredAppPackages(emptySet())
        // }

        Log.i(TAG, "设置已保存 (关键词和铃声)。关键词: ${keywords.size}个, 铃声: $selectedRingtoneUri")
        notifyServiceToUpdateSettings()
    }

    private fun notifyServiceToUpdateSettings() {
        // 只有在服务开关打开且通知读取权限已授予的情况下才通知服务
        if (binding.switchEnableService.isChecked && PermissionUtils.isNotificationListenerEnabled(this)) {
            val intent = Intent(this, MyNotificationListenerService::class.java).apply {
                action = MyNotificationListenerService.ACTION_UPDATE_SETTINGS
            }
            try {
                ContextCompat.startForegroundService(this, intent)
                Log.d(TAG, "已发送 ACTION_UPDATE_SETTINGS 到服务。")
            } catch (e: Exception) {
                Log.e(TAG, "启动服务以更新设置时出错: ", e)
            }
        } else {
            Log.d(TAG, "不满足通知服务更新设置的条件 (开关状态或权限)。")
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
        // checkAndRequestPermissions() // 确保在 onResume 中调用
        updateServiceStatusUI()
        updateEnvironmentWarnings()
        updateFilterAppsSummary(binding.switchFilterApps.isChecked)

        // updateFeatureAvailabilityUI() // 由 loadLicenseStatusAndUpdateUI 调用
    }

    private fun setCardInteractive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else 0.5f
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                // MaterialSwitch 的 enabled 状态由 updateFeatureAvailabilityUI 单独控制
                if (child !is com.google.android.material.materialswitch.MaterialSwitch) {
                    child.isEnabled = enabled
                }
            }
        }
    }

    // 新增: 根据授权状态更新高级功能 UI 的可用性
    private fun updateFeatureAvailabilityUI(isLicensed: Boolean) {
        Log.d(TAG, "更新功能可用性 UI，授权状态: $isLicensed")

        // 配置卡片
        setCardInteractive(binding.cardConfiguration, isLicensed)
        binding.editTextKeywords.isEnabled = isLicensed
        binding.buttonSelectRingtone.isEnabled = isLicensed
        binding.buttonSaveSettings.isEnabled = isLicensed

        // 应用过滤卡片
        setCardInteractive(binding.cardAppFilter, isLicensed)
        binding.switchFilterApps.isEnabled = isLicensed
        // 注意：switchFilterApps 的 CheckedChangeListener 已经处理了未授权时的阻止逻辑

        // 服务控制卡片
        setCardInteractive(binding.cardServiceControl, isLicensed)
        binding.switchEnableService.isEnabled = isLicensed
        // 注意：switchEnableService 的 CheckedChangeListener 已经处理了未授权时的阻止逻辑
        // 重启按钮的可用性取决于服务状态，不受授权直接控制

        // 授权卡片始终启用
        setCardInteractive(binding.cardLicenseKey, true)

        // 如果未授权，且服务开关是开的，强制关闭并保存状态
        if (!isLicensed && binding.switchEnableService.isChecked) {
            binding.switchEnableService.isChecked = false
            sharedPreferencesHelper.saveServiceEnabledState(false)
            updateServiceStatusUI() // 更新服务状态 UI
            Log.w(TAG, "检测到未授权，已强制关闭服务开关。")
        }
        // 如果未授权，且应用过滤开关是开的，强制关闭并保存状态
        if (!isLicensed && binding.switchFilterApps.isChecked) {
            binding.switchFilterApps.isChecked = false
            sharedPreferencesHelper.saveFilterAppsEnabledState(false)
            updateFilterAppsSummary(false) // 更新应用过滤总结文本
            Log.w(TAG, "检测到未授权，已强制关闭应用过滤开关。")
        }
    }


    // 修改: 更新授权状态 UI 的方法，接受 LicensePayload 对象或 null
    private fun updateLicenseStatusUI(licensePayload: LicensePayload?) {
        val statusText: String
        val textColor: Int // 用于存储最终解析的颜色整数值
        val isDark = isDarkThemeActive()

        if (licensePayload != null) {
            // 授权有效
            statusText = if (licensePayload.expiresAt != null) {
                val expiryDate = Date(licensePayload.expiresAt * 1000) // 秒级时间戳转毫秒
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                getString(R.string.license_status_valid_premium_expiry_format, dateFormat.format(expiryDate))
            } else {
                getString(R.string.license_status_valid_premium)
            }
            textColor = if (isDark) R.color.status_positive_green_dark else R.color.status_positive_green_light

        } else {
            // 授权无效、过期或未授权
            // 尝试从 SharedPreferencesHelper 获取更具体的无效原因（如果需要）
            val savedLicense = sharedPreferencesHelper.getLicenseStatus() // 再次尝试获取，可能已过期
            statusText = if (sharedPreferencesHelper.prefs.getBoolean(SharedPreferencesHelper.KEY_IS_LICENSED, false) && savedLicense == null) {
                // 如果之前标记为已授权，但现在 getLicenseStatus 返回 null (可能是因为过期)
                getString(R.string.license_status_expired) // 显示过期
            } else {
                // 未授权或验证失败
                getString(R.string.license_status_unlicensed) // 默认显示未授权
            }

            textColor = if (isDark) R.color.status_negative_red_dark else R.color.status_negative_red_light // 无效和过期都显示红色

        }

        binding.textViewLicenseStatus.text = statusText
        binding.textViewLicenseStatus.setTextColor(ContextCompat.getColor(this, textColor))
    }

    // 保留此方法用于显示解析错误等临时字符串状态
    private fun updateLicenseStatusUI(statusString: String) {
        binding.textViewLicenseStatus.text = statusString
        val isDark = isDarkThemeActive()
        val textColor = when (statusString) {
            getString(R.string.license_parsing_error) -> if (isDark) R.color.status_negative_red_dark else R.color.status_negative_red_light
            getString(R.string.license_status_invalid) -> if (isDark) R.color.status_negative_red_dark else R.color.status_negative_red_light
            getString(R.string.license_status_unlicensed) -> if (isDark) R.color.status_neutral_grey_dark else R.color.status_neutral_grey_light
            else -> if (isDark) R.color.md_theme_onSurface else R.color.md_theme_onSurface // 其他情况使用默认文本颜色
        }
        binding.textViewLicenseStatus.setTextColor(ContextCompat.getColor(this, textColor))
    }


    private fun isDarkThemeActive(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateServiceStatusUI() {
        val serviceEnabledByUser = binding.switchEnableService.isChecked
        val notificationAccessActuallyGranted = PermissionUtils.isNotificationListenerEnabled(this)
        val isLicensed = sharedPreferencesHelper.isAuthenticated() // 检查授权状态

        var statusText: String
        val statusColorRes: Int
        var showRestartButton = false

        val isDark = isDarkThemeActive()

        if (serviceEnabledByUser && isLicensed) { // 服务启用且已授权
            if (notificationAccessActuallyGranted) {
                statusText = getString(R.string.service_running)
                statusColorRes = if (isDark) R.color.status_positive_green_dark else R.color.status_positive_green_light

                val missingAlertPermissions = mutableListOf<String>()
                if (!PermissionUtils.canDrawOverlays(this)) missingAlertPermissions.add(getString(R.string.permission_overlay_short)) // 使用短名称
                if (!PermissionUtils.canPostNotifications(this)) {
                    missingAlertPermissions.add(getString(R.string.permission_post_notification_short)) // 使用短名称
                }
                if (missingAlertPermissions.isNotEmpty()) {
                    statusText += " (" + getString(R.string.service_alert_limited_permissions, missingAlertPermissions.joinToString(getString(R.string.joiner_comma))) + ")"
                }
            } else {
                statusText = getString(R.string.service_status_abnormal_no_permission) // 权限不足
                statusColorRes = if (isDark) R.color.status_negative_red_dark else R.color.status_negative_red_light
                showRestartButton = true
                Log.w(TAG, "服务开关已启用且已授权，但系统层面通知监听器未启用。")
            }
        } else if (serviceEnabledByUser && !isLicensed) { // 服务启用但未授权
            statusText = getString(R.string.service_status_abnormal) // 服务异常 (未连接) - 可以考虑更具体的文本
            statusColorRes = if (isDark) R.color.status_negative_red_dark else R.color.status_negative_red_light
            Log.w(TAG, "服务开关已启用但未授权，服务不会真正运行。")
        }
        else { // 服务未启用
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

        val textColor: Int // 用于存储最终解析的颜色整数值
        if (warnings == getString(R.string.all_clear)) {
            textColor = try {
                ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant)
            } catch (e: Exception) {
                Log.w(TAG, "R.color.md_theme_onSurfaceVariant not found, using fallback for all_clear.")
                val fallbackColorResId = if (isDarkThemeActive()) android.R.color.darker_gray else android.R.color.black
                ContextCompat.getColor(this, fallbackColorResId)
            }
        } else {
            val warningColorResId = if (isDarkThemeActive()) R.color.status_negative_red_dark else R.color.status_negative_red_light
            textColor = ContextCompat.getColor(this, warningColorResId)
        }
        binding.textViewEnvironmentWarnings.setTextColor(textColor)
    }


    private fun updateFilterAppsSummary(isFilterEnabled: Boolean) {
        binding.textViewFilterAppsSummary.text = if (isFilterEnabled) {
            getString(R.string.filter_apps_switch_summary_on)
        } else {
            getString(R.string.filter_apps_switch_summary_off)
        }
    }

    private fun startVigilService() {
        val isLicensed = sharedPreferencesHelper.isAuthenticated()
        if (!PermissionUtils.isNotificationListenerEnabled(this)) {
            Log.w(TAG, "尝试启动服务，但通知读取权限未授予。")
            updateUI() // 确保UI反映此状态
            return
        }
        if (!isLicensed) {
            Log.w(TAG, "尝试启动服务，但未授权。")
            updateUI() // 确保UI反映此状态
            return
        }


        setNotificationListenerServiceComponentEnabled(true)
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试启动。")
        } catch (e: Exception) {
            Log.e(TAG, "启动 Vigil 服务时出错: ", e)
            Toast.makeText(this, getString(R.string.error_starting_service, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            // 如果启动失败，强制关闭开关并保存状态
            if (binding.switchEnableService.isChecked) {
                binding.switchEnableService.isChecked = false
                sharedPreferencesHelper.saveServiceEnabledState(false)
            }
        }
        updateUI() // 服务启动尝试后更新UI
    }

    private fun stopVigilService() {
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            stopService(serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试停止。")
        } catch (e: Exception) {
            Log.e(TAG, "停止 Vigil 服务时出错: ", e)
        }
        updateUI() // 服务停止尝试后更新UI
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

// 扩展函数，方便将 ByteArray 转换为十六进制字符串用于调试
fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
