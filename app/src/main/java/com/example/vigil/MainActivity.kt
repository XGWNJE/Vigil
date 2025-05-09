package com.example.vigil

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.vigil.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var selectedRingtoneUri: Uri? = null

    companion object {
        private const val TAG = "MainActivityVigil"
    }

    // ActivityResultLauncher 用于处理从系统设置页面返回的结果
    // 声明为 internal 以便 PermissionUtils 可以访问（如果 PermissionUtils 在同一模块）
    // 或者通过其他方式传递 launcher 的引用或让 PermissionUtils 返回 Intent 由 Activity 启动
    internal lateinit var appSettingsLauncher: ActivityResultLauncher<Intent>


    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    selectedRingtoneUri = uri
                    updateSelectedRingtoneUI()
                    // 中文日志：用户选择了新的铃声
                    Log.i(TAG, "用户选择了新的铃声: $selectedRingtoneUri")
                } else {
                    selectedRingtoneUri = null
                    updateSelectedRingtoneUI()
                    Log.i(TAG, "用户清除了铃声选择或选择了'无'。")
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        sharedPreferencesHelper = SharedPreferencesHelper(this)

        // 初始化 appSettingsLauncher
        appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 当从设置页面返回时，重新检查权限并更新UI
            Log.d(TAG, "从系统设置页面返回 (appSettingsLauncher)，重新检查权限。")
            checkAndRequestPermissions() // 确保权限状态被刷新
            updateUI()
        }


        setupUIListeners()
        loadSettings()

        // 检查是否因为缺少悬浮窗权限而从服务跳转过来
        if (intent.getStringExtra("reason") == "missing_overlay_permission") {
            Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
            // 可以考虑直接触发悬浮窗权限请求
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !PermissionUtils.canDrawOverlays(this)) {
                PermissionUtils.requestOverlayPermission(this)
            }
        }

        Log.d(TAG, "MainActivity onCreate 完成。")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // 处理从服务通知点击跳转过来的情况 (例如，当应用已在后台运行时)
        setIntent(intent) // 更新 Activity 的 Intent
        if (intent?.getStringExtra("reason") == "missing_overlay_permission") {
            Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !PermissionUtils.canDrawOverlays(this)) {
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

        // 悬浮窗权限按钮的监听器在 checkAndRequestPermissions 中动态设置
        // 因为按钮的 ID 是通过 findViewById 查找的 (或者直接用 binding 如果 ID 在 XML 中固定)
        // binding.buttonGrantOverlayAccess.setOnClickListener { ... }

        // 新增：为发送通知权限按钮设置监听器
        binding.buttonGrantPostNotifications.setOnClickListener {
            PermissionUtils.requestPostNotificationsPermission(this)
        }

        binding.buttonSelectRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION) // 或者 TYPE_ALARM
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.ringtone_selection_title))
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri)
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
        }

        binding.switchEnableService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 启用服务前，检查所有必要权限
                if (!PermissionUtils.isNotificationListenerEnabled(this)) {
                    binding.switchEnableService.isChecked = false // 拨回开关
                    Toast.makeText(this, R.string.permission_notification_access_missing, Toast.LENGTH_LONG).show()
                    PermissionUtils.requestNotificationListenerPermission(this)
                    return@setOnCheckedChangeListener
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !PermissionUtils.canDrawOverlays(this)) {
                    binding.switchEnableService.isChecked = false // 拨回开关
                    Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
                    PermissionUtils.requestOverlayPermission(this)
                    return@setOnCheckedChangeListener
                }
                // 新增：检查发送通知权限 (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionUtils.canPostNotifications(this)) {
                    binding.switchEnableService.isChecked = false // 拨回开关
                    Toast.makeText(this, R.string.post_notifications_permission_missing, Toast.LENGTH_LONG).show()
                    PermissionUtils.requestPostNotificationsPermission(this)
                    return@setOnCheckedChangeListener
                }
                sharedPreferencesHelper.saveServiceEnabledState(true)
                startVigilService()

            } else {
                sharedPreferencesHelper.saveServiceEnabledState(false)
                stopVigilService()
            }
            updateServiceStatus() // 确保在状态改变后立即更新UI
            updateEnvironmentWarnings() // 同时更新环境警告
        }
        Log.d(TAG, "UI 监听器已设置。")
    }

    private fun checkAndRequestPermissions() {
        val notificationAccessGranted = PermissionUtils.isNotificationListenerEnabled(this)
        binding.buttonGrantNotificationAccess.visibility = if (notificationAccessGranted) View.GONE else View.VISIBLE

        var dndAccessGranted = true // 对于低于M的版本，DND权限不适用或默认拥有
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dndAccessGranted = PermissionUtils.isDndAccessGranted(this)
            binding.buttonGrantDndAccess.visibility = if (dndAccessGranted) View.GONE else View.VISIBLE
        } else {
            binding.buttonGrantDndAccess.visibility = View.GONE
        }

        var overlayAccessGranted = true // 对于低于M的版本，悬浮窗权限通常在manifest声明即可
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayAccessGranted = PermissionUtils.canDrawOverlays(this)
            // XML 中 buttonGrantOverlayAccess 的 ID 是 buttonGrantOverlayAccess
            binding.buttonGrantOverlayAccess.visibility = if (overlayAccessGranted) View.GONE else View.VISIBLE
            if (binding.buttonGrantOverlayAccess.visibility == View.VISIBLE) {
                binding.buttonGrantOverlayAccess.setOnClickListener { PermissionUtils.requestOverlayPermission(this) }
            }
        } else {
            binding.buttonGrantOverlayAccess.visibility = View.GONE
        }

        // 新增：检查发送通知权限 (Android 13+)
        var postNotificationsAccessGranted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsAccessGranted = PermissionUtils.canPostNotifications(this)
            binding.buttonGrantPostNotifications.visibility = if (postNotificationsAccessGranted) View.GONE else View.VISIBLE
            // 监听器已在 setupUIListeners 中设置，这里只控制可见性
            // 如果希望在这里动态设置监听器:
            // if (binding.buttonGrantPostNotifications.visibility == View.VISIBLE) {
            //    binding.buttonGrantPostNotifications.setOnClickListener { PermissionUtils.requestPostNotificationsPermission(this) }
            // }
        } else {
            binding.buttonGrantPostNotifications.visibility = View.GONE
        }

        // 如果所有权限都已授予，隐藏整个权限卡片
        if (notificationAccessGranted &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || dndAccessGranted) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || overlayAccessGranted)) {
            binding.cardPermissions.visibility = View.GONE
        } else {
            binding.cardPermissions.visibility = View.VISIBLE
        }

        // 核心功能依赖通知读取和悬浮窗权限
        val allCoreFunctionalityPermissionsGranted = notificationAccessGranted && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || overlayAccessGranted)

        binding.editTextKeywords.isEnabled = allCoreFunctionalityPermissionsGranted
        binding.textFieldKeywordsLayout.isEnabled = allCoreFunctionalityPermissionsGranted
        binding.buttonSelectRingtone.isEnabled = allCoreFunctionalityPermissionsGranted
        binding.buttonSaveSettings.isEnabled = allCoreFunctionalityPermissionsGranted
        binding.switchEnableService.isEnabled = allCoreFunctionalityPermissionsGranted
        // 更新卡片的可交互状态
        setCardInteractive(binding.cardConfiguration, allCoreFunctionalityPermissionsGranted)
        setCardInteractive(binding.cardServiceControl, allCoreFunctionalityPermissionsGranted)


        if (!allCoreFunctionalityPermissionsGranted) {
            if (binding.switchEnableService.isChecked) { // 如果开关是开的但核心权限不足
                binding.switchEnableService.isChecked = false // 关闭开关
                sharedPreferencesHelper.saveServiceEnabledState(false) // 同步状态
                // stopVigilService() // 服务停止逻辑由开关的 listener 处理
            }
        }
        Log.i(TAG, "权限检查完成。通知: $notificationAccessGranted, DND: $dndAccessGranted, 悬浮窗: $overlayAccessGranted (SDK ${Build.VERSION.SDK_INT})")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")
        when (requestCode) {
            PermissionUtils.REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 用户授予了发送通知权限
                    Log.i(TAG, "发送通知权限已授予。")
                    Toast.makeText(this, "发送通知权限已获取", Toast.LENGTH_SHORT).show()
                } else {
                    // 用户拒绝了权限
                    Log.w(TAG, "发送通知权限被拒绝。")
                    Toast.makeText(this, "未授予发送通知权限，备选通知可能无法显示。", Toast.LENGTH_LONG).show()
                }
                // 刷新UI和权限状态
                checkAndRequestPermissions()
                updateUI()
            }
            // 可以添加其他权限请求码的处理 (如果未来有的话)
        }
    }

    private fun loadSettings() {
        val keywords = sharedPreferencesHelper.getKeywords()
        binding.editTextKeywords.setText(keywords.joinToString(","))

        selectedRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        updateSelectedRingtoneUI()

        val serviceCanBeEnabled = PermissionUtils.isNotificationListenerEnabled(this) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionUtils.canDrawOverlays(this)) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || PermissionUtils.canPostNotifications(this)) // 新增检查
        val serviceEnabledByUser = sharedPreferencesHelper.getServiceEnabledState()

        binding.switchEnableService.isChecked = serviceEnabledByUser && serviceCanBeEnabled

        Log.d(TAG, "设置已加载到 UI。用户设置启用: $serviceEnabledByUser, 核心权限满足: $serviceCanBeEnabled, 开关状态: ${binding.switchEnableService.isChecked}")
    }


    private fun saveSettings() {
        val keywordsText = binding.editTextKeywords.text.toString()
        val keywords = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        sharedPreferencesHelper.saveKeywords(keywords)

        sharedPreferencesHelper.saveRingtoneUri(selectedRingtoneUri)
        Log.i(TAG, "设置已保存。关键词: $keywords, 铃声: $selectedRingtoneUri")

        // 如果服务当前是启用状态（通过开关和权限判断），则通知服务更新设置
        if (binding.switchEnableService.isChecked) { // 开关状态已包含了权限检查
            val intent = Intent(this, MyNotificationListenerService::class.java)
            intent.action = MyNotificationListenerService.ACTION_UPDATE_SETTINGS
            try {
                startService(intent)
                Log.d(TAG, "已发送 ACTION_UPDATE_SETTINGS 到服务。")
            } catch (e: Exception) {
                Log.e(TAG, "启动服务以更新设置时出错: ", e)
            }
        }
    }


    private fun updateSelectedRingtoneUI() {
        if (selectedRingtoneUri != null) {
            try {
                val ringtone = RingtoneManager.getRingtone(this, selectedRingtoneUri)
                val name = ringtone?.getTitle(this) ?: getString(R.string.default_ringtone_name)
                binding.textViewSelectedRingtone.text = getString(R.string.selected_ringtone_label, name)
            } catch (e: Exception) {
                Log.e(TAG, "获取铃声标题时出错: $selectedRingtoneUri", e)
                binding.textViewSelectedRingtone.text = getString(R.string.selected_ringtone_label, "未知铃声")
            }
        } else {
            binding.textViewSelectedRingtone.text = getString(R.string.no_ringtone_selected)
        }
    }

    private fun updateUI() {
        updateServiceStatus()
        updateEnvironmentWarnings()

        val hasNotificationAccess = PermissionUtils.isNotificationListenerEnabled(this)
        val hasOverlayAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionUtils.canDrawOverlays(this)
        val hasPostNotificationsAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || PermissionUtils.canPostNotifications(this) // 新增检查
        val allCorePermissions = hasNotificationAccess && hasOverlayAccess && hasPostNotificationsAccess // 更新逻辑

        setCardInteractive(binding.cardConfiguration, allCorePermissions)
        setCardInteractive(binding.cardServiceControl, allCorePermissions)

        if (!allCorePermissions && binding.switchEnableService.isChecked) {
            binding.switchEnableService.isChecked = false
            sharedPreferencesHelper.saveServiceEnabledState(false)
        }
        Log.d(TAG, "UI 已更新。通知权限: $hasNotificationAccess, 悬浮窗权限: $hasOverlayAccess, 发送通知权限: $hasPostNotificationsAccess")
    }


    // 辅助函数，用于启用/禁用卡片及其所有可交互子视图，并调整透明度
    private fun setCardInteractive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else 0.5f // 视觉上提示不可用
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                // 对于 MaterialSwitch 和 Button 等，直接设置 isEnabled 即可
                // 对于 TextInputLayout，也需要设置 isEnabled
                val child = view.getChildAt(i)
                child.isEnabled = enabled
                // 如果需要更细致的控制（例如，某些子视图即使在卡片禁用时也保持可用），则需要更复杂的逻辑
            }
        }
    }


    private fun updateServiceStatus() {
        val isEnabledByUser = sharedPreferencesHelper.getServiceEnabledState()
        val hasNotificationPermission = PermissionUtils.isNotificationListenerEnabled(this)
        val hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionUtils.canDrawOverlays(this)
        val hasPostNotificationsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || PermissionUtils.canPostNotifications(this) // 新增检查


        if (isEnabledByUser && hasNotificationPermission && hasOverlayPermission && hasPostNotificationsPermission) { // 更新条件
            binding.textViewServiceStatus.text = getString(R.string.service_running)
            binding.textViewServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.positive_text_color))
            setNotificationListenerServiceComponentEnabled(true)
        } else {
            binding.textViewServiceStatus.text = getString(R.string.service_stopped)
            binding.textViewServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_text_color))
            val reasons = mutableListOf<String>()
            if (!hasNotificationPermission) reasons.add("通知读取权限")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission) reasons.add("悬浮窗权限")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationsPermission) reasons.add("发送通知权限") // 新增原因

            if (isEnabledByUser && reasons.isNotEmpty()){
                binding.textViewServiceStatus.append(" (缺少: ${reasons.joinToString(", ")})")
            }
        }
        Log.d(TAG, "服务状态已更新: ${binding.textViewServiceStatus.text}")
    }




    private fun updateEnvironmentWarnings() {
        val warnings = EnvironmentChecker.getEnvironmentWarnings(this)
        binding.textViewEnvironmentWarnings.text = warnings
        if (warnings == getString(R.string.all_clear)) {
            binding.textViewEnvironmentWarnings.setTextColor(ContextCompat.getColor(this, R.color.neutral_text_color))
        } else {
            binding.textViewEnvironmentWarnings.setTextColor(ContextCompat.getColor(this, R.color.warning_text_color))
        }
        Log.d(TAG, "环境警告已更新: $warnings")
    }

    private fun startVigilService() {
        // 权限检查已在开关监听器中完成
        setNotificationListenerServiceComponentEnabled(true)
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent) // 对于 Android O 及以上，推荐使用 startForegroundService
            } else {
                startService(serviceIntent)
            }
            Log.i(TAG, "Vigil 通知服务已尝试启动。")
        } catch (e: Exception) {
            Log.e(TAG, "启动 Vigil 服务时出错: ", e)
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
            // 发生错误时，确保开关状态回滚
            binding.switchEnableService.isChecked = false
            sharedPreferencesHelper.saveServiceEnabledState(false)
            // updateServiceStatus() 会在 catch 块外部被调用，所以这里不需要重复
        }
        updateServiceStatus() // 确保UI立即反映状态
    }

    private fun stopVigilService() {
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            stopService(serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试停止。")
        } catch (e: Exception) {
            Log.e(TAG, "停止 Vigil 服务时出错: ", e)
        }
        // MyNotificationListenerService 的 onDestroy 中会处理铃声停止和唤醒锁释放
        updateServiceStatus() // 确保UI立即反映状态
    }

    private fun setNotificationListenerServiceComponentEnabled(enabled: Boolean) {
        val componentName = ComponentName(this, MyNotificationListenerService::class.java)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            // 当用户明确禁用服务或权限不足时，可以考虑将组件设为默认或禁用
            // 但通常情况下，stopService 已经足够。
            // 如果服务意外停止，系统可能会尝试重启已启用的组件。
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        try {
            if (packageManager.getComponentEnabledSetting(componentName) != newState) {
                packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
                Log.i(TAG, "MyNotificationListenerService 组件状态设置为: ${if (enabled) "启用" else "默认"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置服务组件状态时出错: ", e)
        }
    }
}
