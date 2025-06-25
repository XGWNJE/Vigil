// src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt
package com.example.vigil.ui.monitoring

import android.app.Application
import android.app.NotificationManager
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

    private val _showKeywordAlertDialog = mutableStateOf(false)
    val showKeywordAlertDialog: State<Boolean> = _showKeywordAlertDialog

    private val _matchedKeywordForDialog = mutableStateOf<String?>(null)
    val matchedKeywordForDialog: State<String?> = _matchedKeywordForDialog
    
    // 添加初始化状态标志，用于控制启动阶段显示
    private var isInitializing = true
    private var hasReceivedHeartbeat = false // 记录是否收到过心跳
    private var lastConnectionState = false // 记录最后一次连接状态
    private val initTimeoutHandler = Handler(Looper.getMainLooper())
    private val initTimeoutRunnable = Runnable {
        Log.d(TAG, "初始化超时，检查服务状态")
        // 不直接设置isInitializing=false，而是检查连接状态
        // 如果从未收到过心跳或连接状态更新，则保持初始化状态
        if (!hasReceivedHeartbeat && !lastConnectionState) {
            Log.d(TAG, "初始化超时，但未收到心跳且连接状态为false，尝试再等待一段时间")
            // 延长初始化时间，再等待一段时间
            initTimeoutHandler.postDelayed(this.extendedInitTimeoutRunnable, EXTENDED_INIT_TIMEOUT_MS)
        } else {
            Log.d(TAG, "初始化超时，已收到心跳或连接状态为true，结束初始化状态")
            isInitializing = false
            updateServiceStatusUI()
        }
    }
    
    // 扩展的初始化超时处理
    private val extendedInitTimeoutRunnable = Runnable {
        Log.d(TAG, "扩展初始化时间结束，无论如何结束初始化状态")
        isInitializing = false
        updateServiceStatusUI()
    }

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
                    hasReceivedHeartbeat = true // 标记已收到心跳
                    isInitializing = false // 收到心跳后初始化完成
                    updateServiceStatusUI()
                }
                MainActivity.ACTION_SERVICE_STATUS_UPDATE -> {
                    val isConnected = intent.getBooleanExtra(MainActivity.EXTRA_SERVICE_CONNECTED, false)
                    Log.i(TAG, "ViewModel received service connection status update: $isConnected")
                    lastConnectionState = isConnected // 记录连接状态
                    if (isConnected) {
                        isInitializing = false // 连接成功后初始化完成
                    }
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

    companion object {
        private const val TAG = "MonitoringViewModel"
        private const val HEARTBEAT_INTERVAL_MS = 30 * 1000L
        private const val HEARTBEAT_TOLERANCE_MS = 10 * 1000L
        private const val HEARTBEAT_CHECK_INTERVAL_MS = 15 * 1000L
        private const val HEARTBEAT_TIMEOUT_MS = HEARTBEAT_INTERVAL_MS + HEARTBEAT_TOLERANCE_MS
        private const val INITIALIZATION_TIMEOUT_MS = 5 * 1000L // 初始化超时时间：5秒
        private const val EXTENDED_INIT_TIMEOUT_MS = 8 * 1000L // 扩展初始化超时时间：8秒

        // 与 MyNotificationListenerService 中的 ACTION_SHOW_KEYWORD_ALERT 保持一致
        const val ACTION_SHOW_KEYWORD_ALERT = "com.example.vigil.ACTION_SHOW_KEYWORD_ALERT"
        const val EXTRA_KEYWORD_FOR_ALERT = "com.example.vigil.EXTRA_KEYWORD_FOR_ALERT"
    }

    init {
        Log.d(TAG, "MonitoringViewModel created.")
        loadSettings()

        LocalBroadcastManager.getInstance(context).registerReceiver(
            serviceAndAlertReceiver, IntentFilter().apply {
                addAction(MyNotificationListenerService.ACTION_HEARTBEAT)
                addAction(MainActivity.ACTION_SERVICE_STATUS_UPDATE)
                addAction(ACTION_SHOW_KEYWORD_ALERT) // 监听来自服务的本地广播
            }
        )
        Log.d(TAG, "serviceAndAlertReceiver registered for ACTION_SHOW_KEYWORD_ALERT.")

        startHeartbeatCheck()
        
        // 开始初始化计时
        isInitializing = true
        hasReceivedHeartbeat = false
        lastConnectionState = false
        initTimeoutHandler.postDelayed(initTimeoutRunnable, INITIALIZATION_TIMEOUT_MS)
        
        updateServiceStatusUI()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MonitoringViewModel cleared.")
        LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceAndAlertReceiver)
        Log.d(TAG, "ViewModel receivers unregistered.")
        stopHeartbeatCheck()
        initTimeoutHandler.removeCallbacks(initTimeoutRunnable)
        initTimeoutHandler.removeCallbacks(extendedInitTimeoutRunnable)
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
            val oldValue = _serviceEnabled.value
            _serviceEnabled.value = enabled
            
            // 先保存服务启用状态
            sharedPreferencesHelper.saveServiceEnabledState(enabled)
            
            // 然后执行相应操作
            if (enabled) {
                Log.i(TAG, "Service switch enabled, attempting to start service.")
                // 如果是启用，则重置初始化状态
                isInitializing = true
                hasReceivedHeartbeat = false
                lastConnectionState = false
                initTimeoutHandler.removeCallbacks(initTimeoutRunnable)
                initTimeoutHandler.removeCallbacks(extendedInitTimeoutRunnable)
                initTimeoutHandler.postDelayed(initTimeoutRunnable, INITIALIZATION_TIMEOUT_MS)
                
                // 先启动服务
                startServiceCallback(PermissionUtils.isNotificationListenerEnabled(context))
                
                // 无论状态是否改变，都强制通知服务更新
                Log.i(TAG, "Force notifying service to update settings after enabling")
                notifyServiceToUpdateSettingsCallback?.invoke()
            } else {
                Log.i(TAG, "Service switch disabled, stopping service.")
                // 关闭时不是初始化状态
                isInitializing = false
                initTimeoutHandler.removeCallbacks(initTimeoutRunnable)
                initTimeoutHandler.removeCallbacks(extendedInitTimeoutRunnable)
                
                // 先通知服务更新（可能导致通知状态改变）
                Log.i(TAG, "Force notifying service to update settings before stopping")
                notifyServiceToUpdateSettingsCallback?.invoke()
                
                // 然后停止服务
                stopServiceCallback()
            }
        }
        updateServiceStatusUI()
        
        // 额外的日志，帮助排查问题
        Log.d(TAG, "Service status after change: enabled=${_serviceEnabled.value}, status=${_serviceStatusText.value}")
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

        // 如果处于初始化阶段且服务已启用，直接显示初始化状态
        if (isInitializing && serviceEnabledByUser) {
            statusTextResult = context.getString(R.string.service_initializing)
            showRestartBtnResult = false
            Log.d(TAG, "服务正在初始化中，等待连接...")
        } 
        // 如果服务已启用，但从未收到过心跳且连接状态为false，且不在初始化阶段，则可能是服务启动中
        else if (serviceEnabledByUser && isLicensed && !hasReceivedHeartbeat && !lastConnectionState && !isInitializing) {
            // 这种情况可能是服务正在启动但尚未连接，显示为初始化状态而非异常
            statusTextResult = context.getString(R.string.service_initializing)
            showRestartBtnResult = false
            Log.d(TAG, "服务可能正在启动中，尚未收到心跳或连接状态更新")
        }
        else if (serviceEnabledByUser && isLicensed) {
            if (notificationAccessActuallyGranted) {
                if (currentHeartbeatStatus) {
                    statusTextResult = context.getString(R.string.service_running)
                    val missingAlertPermissions = mutableListOf<String>()
                    if (!PermissionUtils.canDrawOverlays(context)) missingAlertPermissions.add(context.getString(R.string.permission_overlay_short))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionUtils.canPostNotifications(context)) {
                        missingAlertPermissions.add(context.getString(R.string.permission_post_notification_short))
                    }
                    if (missingAlertPermissions.isNotEmpty()) {
                        // 简化权限提示，使其更简洁
                        val permissionText = missingAlertPermissions.joinToString(context.getString(R.string.joiner_comma))
                        // 如果权限文本过长，则只显示权限数量
                        if (permissionText.length > 10) {
                            statusTextResult += " (" + context.getString(R.string.service_alert_limited_permissions, missingAlertPermissions.size.toString() + "项") + ")"
                        } else {
                            statusTextResult += " (" + context.getString(R.string.service_alert_limited_permissions, permissionText) + ")"
                        }
                    }
                    showRestartBtnResult = false
                } else {
                    // 如果曾经收到过心跳，但现在超时，则确实是异常状态
                    if (hasReceivedHeartbeat) {
                        statusTextResult = context.getString(R.string.service_status_abnormal)
                        showRestartBtnResult = true
                        Log.w(TAG, "Service switch enabled, permission granted, but heartbeat timed out. Restart might be needed.")
                    } else {
                        // 如果从未收到过心跳，可能是服务启动中或系统限制，显示为初始化状态
                        statusTextResult = context.getString(R.string.service_initializing)
                        showRestartBtnResult = false
                        Log.d(TAG, "服务可能正在启动中，尚未收到任何心跳")
                    }
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
        Log.d(TAG, "ViewModel service status updated: ${_serviceStatusText.value}, ShowRestartButton: ${_showRestartButton.value} (Heartbeat recent: $currentHeartbeatStatus, Initializing: $isInitializing, HasReceivedHeartbeat: $hasReceivedHeartbeat, LastConnectionState: $lastConnectionState)")
    }

    fun onRestartServiceClick(restartServiceCallback: () -> Unit) {
        Log.i(TAG, "User clicked restart service.")
        _serviceStatusText.value = context.getString(R.string.service_status_recovering)
        _showRestartButton.value = false
        
        // 重置初始化状态并设置超时
        isInitializing = true
        hasReceivedHeartbeat = false
        lastConnectionState = false
        initTimeoutHandler.removeCallbacks(initTimeoutRunnable)
        initTimeoutHandler.removeCallbacks(extendedInitTimeoutRunnable)
        initTimeoutHandler.postDelayed(initTimeoutRunnable, INITIALIZATION_TIMEOUT_MS)
        
        restartServiceCallback()
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
