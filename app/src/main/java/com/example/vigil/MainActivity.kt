package com.example.vigil

import android.app.Activity
import android.content.ComponentName
// import android.content.Context // 已在 SharedPreferencesHelper 中使用
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
// import android.provider.Settings // 已在 PermissionUtils 中使用
import android.util.Log
import android.view.View // 导入 View
import android.widget.Toast
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

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    selectedRingtoneUri = uri
                    updateSelectedRingtoneUI()
                    Log.i(TAG, "用户选择了新的铃声: $selectedRingtoneUri")
                } else {
                    selectedRingtoneUri = null
                    updateSelectedRingtoneUI()
                    Log.i(TAG, "用户清除了铃声选择或选择了'无'。")
                }
            }
        }

    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "从系统设置页面返回，重新检查权限。ActivityResult: ${result.resultCode}")
            checkAndRequestPermissions()
            updateUI()
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置 Toolbar
        setSupportActionBar(binding.toolbar)

        sharedPreferencesHelper = SharedPreferencesHelper(this)

        setupUIListeners()
        loadSettings()
        Log.d(TAG, "MainActivity onCreate 完成。")
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

        binding.buttonSelectRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
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
            sharedPreferencesHelper.saveServiceEnabledState(isChecked)
            if (isChecked) {
                if (PermissionUtils.isNotificationListenerEnabled(this)) {
                    startVigilService()
                } else {
                    binding.switchEnableService.isChecked = false
                    sharedPreferencesHelper.saveServiceEnabledState(false)
                    Toast.makeText(this, R.string.permission_notification_access_missing, Toast.LENGTH_LONG).show()
                    PermissionUtils.requestNotificationListenerPermission(this)
                }
            } else {
                stopVigilService()
            }
            updateServiceStatus()
            updateEnvironmentWarnings()
        }
        Log.d(TAG, "UI 监听器已设置。")
    }

    private fun checkAndRequestPermissions() {
        val notificationAccessGranted = PermissionUtils.isNotificationListenerEnabled(this)
        binding.buttonGrantNotificationAccess.visibility = if (notificationAccessGranted) View.GONE else View.VISIBLE

        var dndAccessGranted = true // 默认对于低于M的版本是true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dndAccessGranted = PermissionUtils.isDndAccessGranted(this)
            binding.buttonGrantDndAccess.visibility = if (dndAccessGranted) View.GONE else View.VISIBLE
        } else {
            binding.buttonGrantDndAccess.visibility = View.GONE
        }

        // 如果所有权限都已授予，可以考虑隐藏整个权限卡片
        if (notificationAccessGranted && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || dndAccessGranted)) {
            binding.cardPermissions.visibility = View.GONE
        } else {
            binding.cardPermissions.visibility = View.VISIBLE
        }


        binding.editTextKeywords.isEnabled = notificationAccessGranted
        binding.textFieldKeywordsLayout.isEnabled = notificationAccessGranted // 启用/禁用 TextInputLayout
        binding.buttonSelectRingtone.isEnabled = notificationAccessGranted
        binding.buttonSaveSettings.isEnabled = notificationAccessGranted
        binding.switchEnableService.isEnabled = notificationAccessGranted
        // 根据权限启用/禁用整个配置卡片和控制卡片
        binding.cardConfiguration.isEnabled = notificationAccessGranted
        binding.cardServiceControl.isEnabled = notificationAccessGranted


        if (!notificationAccessGranted) {
            if (binding.switchEnableService.isChecked) {
                binding.switchEnableService.isChecked = false
                sharedPreferencesHelper.saveServiceEnabledState(false)
            }
        }
        Log.i(TAG, "权限检查完成。通知权限: $notificationAccessGranted, DND权限: $dndAccessGranted (SDK ${Build.VERSION.SDK_INT})")
    }


    private fun loadSettings() {
        val keywords = sharedPreferencesHelper.getKeywords()
        binding.editTextKeywords.setText(keywords.joinToString(","))

        selectedRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        updateSelectedRingtoneUI()

        val serviceShouldBeEnabled = sharedPreferencesHelper.getServiceEnabledState() && PermissionUtils.isNotificationListenerEnabled(this)
        binding.switchEnableService.isChecked = serviceShouldBeEnabled

        Log.d(TAG, "设置已加载到 UI。服务开关计算状态: $serviceShouldBeEnabled")
    }

    private fun saveSettings() {
        val keywordsText = binding.editTextKeywords.text.toString()
        val keywords = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        sharedPreferencesHelper.saveKeywords(keywords)

        sharedPreferencesHelper.saveRingtoneUri(selectedRingtoneUri)
        Log.i(TAG, "设置已保存。关键词: $keywords, 铃声: $selectedRingtoneUri")

        if (sharedPreferencesHelper.getServiceEnabledState() && PermissionUtils.isNotificationListenerEnabled(this)) {
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
        // 根据权限启用/禁用卡片
        binding.cardConfiguration.alpha = if (hasNotificationAccess) 1.0f else 0.5f
        binding.cardServiceControl.alpha = if (hasNotificationAccess) 1.0f else 0.5f
        // 递归禁用卡片内的子视图交互
        setCardEnabled(binding.cardConfiguration, hasNotificationAccess)
        setCardEnabled(binding.cardServiceControl, hasNotificationAccess)


        if (!hasNotificationAccess && binding.switchEnableService.isChecked) {
            binding.switchEnableService.isChecked = false
            sharedPreferencesHelper.saveServiceEnabledState(false)
        }
        Log.d(TAG, "UI 已更新。通知权限: $hasNotificationAccess")
    }

    // 辅助函数，用于启用/禁用卡片及其所有可交互子视图
    private fun setCardEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                setCardEnabled(view.getChildAt(i), enabled)
            }
        }
    }


    private fun updateServiceStatus() {
        val isEnabledByUser = sharedPreferencesHelper.getServiceEnabledState()
        val hasPermission = PermissionUtils.isNotificationListenerEnabled(this)

        if (isEnabledByUser && hasPermission) {
            binding.textViewServiceStatus.text = getString(R.string.service_running)
            binding.textViewServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.positive_text_color)) // 使用语义化颜色
            setNotificationListenerServiceComponentEnabled(true)
        } else {
            binding.textViewServiceStatus.text = getString(R.string.service_stopped)
            binding.textViewServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_text_color)) // 使用语义化颜色
            if (!hasPermission && isEnabledByUser) {
                binding.textViewServiceStatus.append(" (${getString(R.string.permission_notification_access_missing)})")
            }
        }
        Log.d(TAG, "服务状态已更新: ${binding.textViewServiceStatus.text}")
    }


    private fun updateEnvironmentWarnings() {
        val warnings = EnvironmentChecker.getEnvironmentWarnings(this)
        binding.textViewEnvironmentWarnings.text = warnings
        if (warnings == getString(R.string.all_clear)) {
            binding.textViewEnvironmentWarnings.setTextColor(ContextCompat.getColor(this, R.color.neutral_text_color)) // 中性色
        } else {
            binding.textViewEnvironmentWarnings.setTextColor(ContextCompat.getColor(this, R.color.warning_text_color)) // 警告色
        }
        Log.d(TAG, "环境警告已更新: $warnings")
    }

    private fun startVigilService() {
        if (!PermissionUtils.isNotificationListenerEnabled(this)) {
            Toast.makeText(this, R.string.permission_notification_access_missing, Toast.LENGTH_LONG).show()
            binding.switchEnableService.isChecked = false
            sharedPreferencesHelper.saveServiceEnabledState(false)
            return
        }
        setNotificationListenerServiceComponentEnabled(true)
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            startService(serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试启动。")
        } catch (e: Exception) {
            Log.e(TAG, "启动 Vigil 服务时出错: ", e)
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
        updateServiceStatus()
    }

    private fun stopVigilService() {
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            stopService(serviceIntent)
            Log.i(TAG, "Vigil 通知服务已尝试停止。")
        } catch (e: Exception) {
            Log.e(TAG, "停止 Vigil 服务时出错: ", e)
        }
        updateServiceStatus()
    }

    private fun setNotificationListenerServiceComponentEnabled(enabled: Boolean) {
        val componentName = ComponentName(this, MyNotificationListenerService::class.java)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        try {
            if (packageManager.getComponentEnabledSetting(componentName) != newState) {
                packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
                Log.i(TAG, "MyNotificationListenerService 组件状态设置为: ${if(enabled) "启用" else "默认/禁用"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置服务组件状态时出错: ", e)
        }
    }
}
