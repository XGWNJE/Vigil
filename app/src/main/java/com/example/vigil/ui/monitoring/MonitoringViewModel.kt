// src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt
package com.example.vigil.ui.monitoring

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.vigil.EnvironmentChecker
import com.example.vigil.MainActivity // 保持导入
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.SharedPreferencesHelper
import com.example.vigil.MyNotificationListenerService
import kotlinx.coroutines.launch
// import java.text.SimpleDateFormat // 如果未使用，可以移除
// import java.util.* // 如果未使用，可以移除
// import android.app.Activity // 如果未使用，可以移除

/**
 * ViewModel for the Monitoring screen.
 * Manages UI state and logic related to service control, keywords, ringtone, environment check,
 * and the keyword alert dialog.
 */
class MonitoringViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPreferencesHelper = SharedPreferencesHelper(context)

    // State for UI elements
    private val _keywords = mutableStateOf("")
    val keywords: State<String> = _keywords

    private val _selectedRingtoneUri = mutableStateOf<Uri?>(null)
    val selectedRingtoneUri: State<Uri?> = _selectedRingtoneUri

    private val _selectedRingtoneName = mutableStateOf(context.getString(R.string.no_ringtone_selected))
    val selectedRingtoneName: State<String> = _selectedRingtoneName

    private val _serviceEnabled = mutableStateOf(false)
    val serviceEnabled: State<Boolean> = _serviceEnabled

    private val _serviceStatusText = mutableStateOf(context.getString(R.string.service_stopped))
    val serviceStatusText: State<String> = _serviceStatusText

    private val _showRestartButton = mutableStateOf(false)
    val showRestartButton: State<Boolean> = _showRestartButton

    private val _environmentWarnings = mutableStateOf(context.getString(R.string.all_clear))
    val environmentWarnings: State<String> = _environmentWarnings

    // --- 关键词提醒对话框状态 ---
    private val _showKeywordAlertDialog = mutableStateOf(false)
    val showKeywordAlertDialog: State<Boolean> = _showKeywordAlertDialog

    private val _matchedKeywordForDialog = mutableStateOf<String?>(null)
    val matchedKeywordForDialog: State<String?> = _matchedKeywordForDialog
    // --- 结束 ---


    // Heartbeat and service status monitoring
    private var lastHeartbeatTime: Long = 0
    private val heartbeatCheckHandler = Handler(Looper.getMainLooper())
    private val heartbeatCheckRunnable = object : Runnable {
        override fun run() {
            // *** 调用 ViewModel 内部的方法 ***
            this@MonitoringViewModel.checkServiceHeartbeatStatus()
            heartbeatCheckHandler.postDelayed(this, HEARTBEAT_CHECK_INTERVAL_MS)
        }
    }

    // *** 将可见性改为 internal 或 public 进行测试，如果 private 确实是问题所在 ***
    // private fun checkServiceHeartbeatStatus() {
    internal fun checkServiceHeartbeatStatus() { // 改为 internal 尝试
        Log.d(TAG, "Executing ViewModel heartbeat status check.")
        val currentTime = System.currentTimeMillis()
        val serviceEnabledByUser = _serviceEnabled.value
        val notificationAccessActuallyGranted = PermissionUtils.isNotificationListenerEnabled(context)
        val isLicensed = sharedPreferencesHelper.isAuthenticated()

        val shouldServiceBeRunning = serviceEnabledByUser && notificationAccessActuallyGranted && isLicensed
        val hasRecentHeartbeat = if (shouldServiceBeRunning) {
            (currentTime - lastHeartbeatTime) < HEARTBEAT_TIMEOUT_MS
        } else {
            // 如果服务不应该运行，则认为心跳是“最近的”，状态将反映为停止或未授权
            true
        }
        updateServiceStatusUI(hasRecentHeartbeat)
    }


    private val serviceAndAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MyNotificationListenerService.ACTION_HEARTBEAT -> {
                    Log.d(TAG, "ViewModel received service heartbeat.")
                    lastHeartbeatTime = System.currentTimeMillis()
                    updateServiceStatusUI() // 根据新的心跳时间更新UI
                }
                MainActivity.ACTION_SERVICE_STATUS_UPDATE -> {
                    val isConnected = intent.getBooleanExtra(MainActivity.EXTRA_SERVICE_CONNECTED, false)
                    Log.i(TAG, "ViewModel received service connection status update: $isConnected")
                    updateServiceStatusUI()
                }
                ACTION_SHOW_KEYWORD_ALERT -> {
                    val matchedKeyword = intent.getStringExtra(EXTRA_KEYWORD_FOR_ALERT)
                    Log.i(TAG, "ViewModel received request to show alert for keyword: $matchedKeyword")
                    if (matchedKeyword != null) {
                        _matchedKeywordForDialog.value = matchedKeyword
                        _showKeywordAlertDialog.value = true
                    }
                }
            }
        }
    }


    companion object {
        private const val TAG = "MonitoringViewModel"
        private const val HEARTBEAT_INTERVAL_MS = 30 * 1000L // 服务发送心跳间隔 30秒
        private const val HEARTBEAT_TOLERANCE_MS = 10 * 1000L // 容忍延迟
        private const val HEARTBEAT_CHECK_INTERVAL_MS = 15 * 1000L // ViewModel 检查间隔 15秒
        private const val HEARTBEAT_TIMEOUT_MS = HEARTBEAT_INTERVAL_MS + HEARTBEAT_TOLERANCE_MS // 超时阈值

        const val ACTION_SHOW_KEYWORD_ALERT = "com.example.vigil.ACTION_SHOW_KEYWORD_ALERT"
        const val EXTRA_KEYWORD_FOR_ALERT = "com.example.vigil.EXTRA_KEYWORD_FOR_ALERT"
    }

    init {
        Log.d(TAG, "MonitoringViewModel created.")
        loadSettings()
        updateEnvironmentWarnings() // 初始环境检查
        LocalBroadcastManager.getInstance(context).registerReceiver(
            serviceAndAlertReceiver, IntentFilter().apply {
                addAction(MyNotificationListenerService.ACTION_HEARTBEAT)
                addAction(MainActivity.ACTION_SERVICE_STATUS_UPDATE)
                addAction(ACTION_SHOW_KEYWORD_ALERT)
            }
        )
        startHeartbeatCheck() // 开始监控心跳
        updateServiceStatusUI() // 初始UI状态更新
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MonitoringViewModel cleared.")
        LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceAndAlertReceiver)
        stopHeartbeatCheck()
    }

    private fun loadSettings() {
        _keywords.value = sharedPreferencesHelper.getKeywords().joinToString(",")
        _selectedRingtoneUri.value = sharedPreferencesHelper.getRingtoneUri()
        updateSelectedRingtoneName()
        _serviceEnabled.value = sharedPreferencesHelper.getServiceEnabledState()
        Log.d(TAG, "Settings loaded: Keywords=${_keywords.value.length} chars, RingtoneURI=${_selectedRingtoneUri.value}, ServiceEnabled=${_serviceEnabled.value}")
    }

    fun saveSettings() {
        viewModelScope.launch {
            val keywordsText = _keywords.value
            val keywordsList = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            sharedPreferencesHelper.saveKeywords(keywordsList)
            sharedPreferencesHelper.saveRingtoneUri(_selectedRingtoneUri.value)
            Log.i(TAG, "Settings saved: Keywords=${keywordsList.size}个, RingtoneURI=${_selectedRingtoneUri.value}")
            notifyServiceToUpdateSettingsCallback?.invoke()
        }
    }

    fun onKeywordsChange(newKeywords: String) {
        _keywords.value = newKeywords
    }

    fun onRingtoneUriSelected(uri: Uri?) {
        _selectedRingtoneUri.value = uri
        updateSelectedRingtoneName()
    }

    private fun updateSelectedRingtoneName() {
        _selectedRingtoneName.value = if (_selectedRingtoneUri.value != null) {
            try {
                RingtoneManager.getRingtone(context, _selectedRingtoneUri.value)?.getTitle(context) ?: context.getString(R.string.default_ringtone_name)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting ringtone title: ${_selectedRingtoneUri.value}", e)
                "未知铃声" // 可以考虑使用字符串资源
            }
        } else {
            context.getString(R.string.no_ringtone_selected)
        }
    }

    fun onServiceEnabledChange(enabled: Boolean, isLicensed: Boolean, startServiceCallback: (Boolean) -> Unit, stopServiceCallback: () -> Unit) {
        if (enabled && !isLicensed) {
            Log.w(TAG, "Attempted to enable service but not licensed.")
            _serviceEnabled.value = false // 确保UI状态与实际状态一致
        } else {
            _serviceEnabled.value = enabled
            sharedPreferencesHelper.saveServiceEnabledState(enabled)
            if (enabled) {
                Log.i(TAG, "Service switch enabled, attempting to start service.")
                startServiceCallback(PermissionUtils.isNotificationListenerEnabled(context))
            } else {
                Log.i(TAG, "Service switch disabled, stopping service.")
                stopServiceCallback()
            }
        }
        updateServiceStatusUI() // 立即更新状态文本
    }

    fun onRestartServiceClick(restartServiceCallback: () -> Unit) {
        Log.i(TAG, "User clicked restart service.")
        _serviceStatusText.value = context.getString(R.string.service_status_recovering)
        _showRestartButton.value = false // 尝试重启时隐藏按钮
        restartServiceCallback()
    }

    private fun updateServiceStatusUI(hasRecentHeartbeat: Boolean? = null) {
        val serviceEnabledByUser = _serviceEnabled.value
        val notificationAccessActuallyGranted = PermissionUtils.isNotificationListenerEnabled(context)
        val isLicensed = sharedPreferencesHelper.isAuthenticated()

        var statusTextResult: String // 使用不同的变量名以避免与 State 属性冲突
        var showRestartBtnResult = false

        val currentHeartbeatStatus = hasRecentHeartbeat ?: ((System.currentTimeMillis() - lastHeartbeatTime) < HEARTBEAT_TIMEOUT_MS)

        if (serviceEnabledByUser && isLicensed) {
            if (notificationAccessActuallyGranted) {
                if (currentHeartbeatStatus) {
                    statusTextResult = context.getString(R.string.service_running)
                    val missingAlertPermissions = mutableListOf<String>()
                    if (!PermissionUtils.canDrawOverlays(context)) missingAlertPermissions.add(context.getString(R.string.permission_overlay_short))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionUtils.canPostNotifications(context)) {
                        missingAlertPermissions.add(context.getString(R.string.permission_post_notification_short))
                    }
                    if (missingAlertPermissions.isNotEmpty()) {
                        statusTextResult += " (" + context.getString(R.string.service_alert_limited_permissions, missingAlertPermissions.joinToString(context.getString(R.string.joiner_comma))) + ")"
                    }
                    showRestartBtnResult = false
                } else {
                    statusTextResult = context.getString(R.string.service_status_abnormal)
                    showRestartBtnResult = true
                    Log.w(TAG, "Service switch enabled, permission granted, but heartbeat timed out. Restart might be needed.")
                }
            } else {
                statusTextResult = context.getString(R.string.service_status_abnormal_no_permission)
                showRestartBtnResult = true
                Log.w(TAG, "Service switch enabled and licensed, but notification listener is not enabled in system settings.")
            }
        } else if (serviceEnabledByUser && !isLicensed) {
            statusTextResult = context.getString(R.string.service_status_abnormal) // 或者更具体的“未授权”状态
            showRestartBtnResult = false
            Log.w(TAG, "Service switch enabled but not licensed, service will not truly run.")
        }
        else { // 服务未启用
            statusTextResult = context.getString(R.string.service_stopped)
            showRestartBtnResult = false
        }

        _serviceStatusText.value = statusTextResult
        _showRestartButton.value = showRestartBtnResult
        Log.d(TAG, "ViewModel service status updated: ${_serviceStatusText.value}, ShowRestartButton: ${_showRestartButton.value} (Heartbeat recent: $currentHeartbeatStatus)")
    }

    fun updateEnvironmentWarnings() {
        viewModelScope.launch {
            _environmentWarnings.value = EnvironmentChecker.getEnvironmentWarnings(context)
            Log.d(TAG, "Environment warnings updated: ${_environmentWarnings.value}")
        }
    }

    private fun startHeartbeatCheck() {
        Log.d(TAG, "Starting ViewModel heartbeat check.")
        heartbeatCheckHandler.removeCallbacks(heartbeatCheckRunnable) // 确保只有一个 runnable 在运行
        heartbeatCheckHandler.post(heartbeatCheckRunnable)
    }

    private fun stopHeartbeatCheck() {
        Log.d(TAG, "Stopping ViewModel heartbeat check.")
        heartbeatCheckHandler.removeCallbacks(heartbeatCheckRunnable)
    }

    fun onKeywordAlertDialogDismiss() {
        _showKeywordAlertDialog.value = false
        _matchedKeywordForDialog.value = null
    }

    fun onKeywordAlertDialogConfirm() {
        Log.d(TAG, "Keyword alert dialog confirmed by user.")
        val intent = Intent(MyNotificationListenerService.ACTION_ALERT_CONFIRMED_FROM_UI)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        onKeywordAlertDialogDismiss()
    }

    var startServiceCallback: ((Boolean) -> Unit)? = null
    var stopServiceCallback: (() -> Unit)? = null
    var restartServiceCallback: (() -> Unit)? = null
    var notifyServiceToUpdateSettingsCallback: (() -> Unit)? = null
}
