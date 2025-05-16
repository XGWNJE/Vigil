// src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt
package com.example.vigil.ui.monitoring

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
import com.example.vigil.MainActivity
import com.example.vigil.MyNotificationListenerService // 确保导入 MyNotificationListenerService
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.SharedPreferencesHelper
import kotlinx.coroutines.launch

class MonitoringViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPreferencesHelper = SharedPreferencesHelper(context)

    // UI 状态
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

    private val _showKeywordAlertDialog = mutableStateOf(false)
    val showKeywordAlertDialog: State<Boolean> = _showKeywordAlertDialog

    private val _matchedKeywordForDialog = mutableStateOf<String?>(null)
    val matchedKeywordForDialog: State<String?> = _matchedKeywordForDialog

    // 服务心跳监控
    private var lastHeartbeatTime: Long = 0
    private val heartbeatCheckHandler = Handler(Looper.getMainLooper())
    private val heartbeatCheckRunnable = object : Runnable {
        override fun run() {
            this@MonitoringViewModel.checkServiceHeartbeatStatus()
            heartbeatCheckHandler.postDelayed(this, HEARTBEAT_CHECK_INTERVAL_MS)
        }
    }

    // 应用内服务状态和关键词提醒的广播接收器
    private val serviceAndAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MyNotificationListenerService.ACTION_HEARTBEAT -> {
                    Log.d(TAG, "ViewModel received service heartbeat.")
                    lastHeartbeatTime = System.currentTimeMillis()
                    updateServiceStatusUI()
                }
                MainActivity.ACTION_SERVICE_STATUS_UPDATE -> {
                    val isConnected = intent.getBooleanExtra(MainActivity.EXTRA_SERVICE_CONNECTED, false)
                    Log.i(TAG, "ViewModel received service connection status update: $isConnected")
                    updateServiceStatusUI()
                }
                ACTION_SHOW_KEYWORD_ALERT -> { // 来自 LocalBroadcastManager
                    val matchedKeyword = intent.getStringExtra(EXTRA_KEYWORD_FOR_ALERT)
                    Log.i(TAG, "ViewModel received ACTION_SHOW_KEYWORD_ALERT (LocalBroadcast) for keyword: $matchedKeyword")
                    if (matchedKeyword != null) {
                        // 确保在主线程更新 UI 状态
                        viewModelScope.launch {
                            _matchedKeywordForDialog.value = matchedKeyword
                            _showKeywordAlertDialog.value = true
                            Log.d(TAG, "Dialog state updated: show=true, keyword=$matchedKeyword")
                        }
                    }
                }
            }
        }
    }

    private val environmentChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "EnvironmentChangeReceiver received action: ${intent?.action}")
            when (intent?.action) {
                AudioManager.RINGER_MODE_CHANGED_ACTION,
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    Log.i(TAG, "环境变化 (${intent.action}), 更新环境警告信息。")
                    updateEnvironmentWarnings()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MonitoringViewModel"
        private const val HEARTBEAT_INTERVAL_MS = 30 * 1000L
        private const val HEARTBEAT_TOLERANCE_MS = 10 * 1000L
        private const val HEARTBEAT_CHECK_INTERVAL_MS = 15 * 1000L
        private const val HEARTBEAT_TIMEOUT_MS = HEARTBEAT_INTERVAL_MS + HEARTBEAT_TOLERANCE_MS

        // 与 MyNotificationListenerService 中的 ACTION_SHOW_KEYWORD_ALERT 保持一致
        const val ACTION_SHOW_KEYWORD_ALERT = "com.example.vigil.ACTION_SHOW_KEYWORD_ALERT"
        const val EXTRA_KEYWORD_FOR_ALERT = "com.example.vigil.EXTRA_KEYWORD_FOR_ALERT"
    }

    init {
        Log.d(TAG, "MonitoringViewModel created.")
        loadSettings()
        updateEnvironmentWarnings()

        LocalBroadcastManager.getInstance(context).registerReceiver(
            serviceAndAlertReceiver, IntentFilter().apply {
                addAction(MyNotificationListenerService.ACTION_HEARTBEAT)
                addAction(MainActivity.ACTION_SERVICE_STATUS_UPDATE)
                addAction(ACTION_SHOW_KEYWORD_ALERT) // 监听来自服务的本地广播
            }
        )
        Log.d(TAG, "serviceAndAlertReceiver registered for ACTION_SHOW_KEYWORD_ALERT.")


        val environmentIntentFilter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
        }
        context.registerReceiver(environmentChangeReceiver, environmentIntentFilter)
        Log.d(TAG, "EnvironmentChangeReceiver registered.")

        startHeartbeatCheck()
        updateServiceStatusUI()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MonitoringViewModel cleared.")
        LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceAndAlertReceiver)
        context.unregisterReceiver(environmentChangeReceiver)
        Log.d(TAG, "EnvironmentChangeReceiver unregistered.")
        stopHeartbeatCheck()
    }

    /**
     * 新增：此方法由 MainActivity 在接收到特定 Intent (从服务启动) 时调用，
     * 以确保即使 LocalBroadcast 未被及时处理，对话框也能显示。
     */
    fun triggerShowKeywordAlert(keyword: String) {
        Log.i(TAG, "triggerShowKeywordAlert called by MainActivity for keyword: $keyword")
        viewModelScope.launch {
            _matchedKeywordForDialog.value = keyword
            _showKeywordAlertDialog.value = true
            Log.d(TAG, "Dialog state updated via trigger: show=true, keyword=$keyword")
        }
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
                "未知铃声"
            }
        } else {
            context.getString(R.string.no_ringtone_selected)
        }
    }

    fun onServiceEnabledChange(enabled: Boolean, isLicensed: Boolean, startServiceCallback: (Boolean) -> Unit, stopServiceCallback: () -> Unit) {
        if (enabled && !isLicensed) {
            Log.w(TAG, "Attempted to enable service but not licensed.")
            _serviceEnabled.value = false
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
        updateServiceStatusUI()
    }

    internal fun checkServiceHeartbeatStatus() {
        Log.d(TAG, "Executing ViewModel heartbeat status check.")
        val currentTime = System.currentTimeMillis()
        val serviceEnabledByUser = _serviceEnabled.value
        val notificationAccessActuallyGranted = PermissionUtils.isNotificationListenerEnabled(context)
        val isLicensed = sharedPreferencesHelper.isAuthenticated()

        val shouldServiceBeRunning = serviceEnabledByUser && notificationAccessActuallyGranted && isLicensed
        val hasRecentHeartbeat = if (shouldServiceBeRunning) {
            (currentTime - lastHeartbeatTime) < HEARTBEAT_TIMEOUT_MS
        } else {
            true
        }
        updateServiceStatusUI(hasRecentHeartbeat)
    }

    private fun updateServiceStatusUI(hasRecentHeartbeat: Boolean? = null) {
        val serviceEnabledByUser = _serviceEnabled.value
        val notificationAccessActuallyGranted = PermissionUtils.isNotificationListenerEnabled(context)
        val isLicensed = sharedPreferencesHelper.isAuthenticated()

        var statusTextResult: String
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
            statusTextResult = context.getString(R.string.service_status_abnormal)
            showRestartBtnResult = false
            Log.w(TAG, "Service switch enabled but not licensed, service will not truly run.")
        }
        else {
            statusTextResult = context.getString(R.string.service_stopped)
            showRestartBtnResult = false
        }

        _serviceStatusText.value = statusTextResult
        _showRestartButton.value = showRestartBtnResult
        Log.d(TAG, "ViewModel service status updated: ${_serviceStatusText.value}, ShowRestartButton: ${_showRestartButton.value} (Heartbeat recent: $currentHeartbeatStatus)")
    }

    fun onRestartServiceClick(restartServiceCallback: () -> Unit) {
        Log.i(TAG, "User clicked restart service.")
        _serviceStatusText.value = context.getString(R.string.service_status_recovering)
        _showRestartButton.value = false
        restartServiceCallback()
    }

    fun updateEnvironmentWarnings() {
        viewModelScope.launch {
            _environmentWarnings.value = EnvironmentChecker.getEnvironmentWarnings(context)
            Log.d(TAG, "Environment warnings updated via updateEnvironmentWarnings(): ${_environmentWarnings.value}")
        }
    }

    private fun startHeartbeatCheck() {
        Log.d(TAG, "Starting ViewModel heartbeat check.")
        heartbeatCheckHandler.removeCallbacks(heartbeatCheckRunnable)
        heartbeatCheckHandler.post(heartbeatCheckRunnable)
    }

    private fun stopHeartbeatCheck() {
        Log.d(TAG, "Stopping ViewModel heartbeat check.")
        heartbeatCheckHandler.removeCallbacks(heartbeatCheckRunnable)
    }

    fun onKeywordAlertDialogDismiss() {
        _showKeywordAlertDialog.value = false
        _matchedKeywordForDialog.value = null
        Log.d(TAG, "Keyword alert dialog dismissed.")
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
