// src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt
package com.example.vigil.ui.monitoring

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vigil.PermissionUtils
import com.example.vigil.SharedPreferencesHelper
import com.example.vigil.VigilEventBus
import kotlinx.coroutines.launch

/**
 * 服务状态的单一枚举，所有 UI 元素从这一个来源派生状态，避免多个独立状态变量不同步。
 */
enum class ServiceState {
    DISABLED,           // 用户关闭了服务开关
    INITIALIZING,       // 开关已开，等待服务连接信号（<5s）
    RUNNING,            // 运行正常，心跳最近有收到
    RUNNING_LIMITED,    // 运行中，但悬浮窗/通知权限缺失，报警可能受影响
    HEARTBEAT_TIMEOUT,  // 心跳超时，Service 可能被系统杀死
    NO_PERMISSION,      // 通知监听权限未授予（系统设置里没有开）
    ERROR               // 未知错误状态
}

class MonitoringViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPreferencesHelper = SharedPreferencesHelper(context)

    // ---- 核心状态：单一枚举 ----
    private val _serviceState = mutableStateOf(ServiceState.DISABLED)
    val serviceState: State<ServiceState> = _serviceState

    // ---- 开关状态：用户主观意图（是否想开启），与 serviceState 分离 ----
    private val _serviceEnabled = mutableStateOf(false)
    val serviceEnabled: State<Boolean> = _serviceEnabled

    // ---- 调试信息：面向开发者的诊断文字 ----
    private val _debugInfo = mutableStateOf("")
    val debugInfo: State<String> = _debugInfo

    // ---- 报警对话框 ----
    private val _showKeywordAlertDialog = mutableStateOf(false)
    val showKeywordAlertDialog: State<Boolean> = _showKeywordAlertDialog

    private val _matchedKeywordForDialog = mutableStateOf<String?>(null)
    val matchedKeywordForDialog: State<String?> = _matchedKeywordForDialog

    // ---- 内部状态跟踪 ----
    private var lastHeartbeatTime: Long = 0L        // SystemClock.elapsedRealtime() 单位
    private var hasReceivedAnySignal = false         // 是否曾收到过心跳或 connected 信号
    private var isInitializingWindow = false         // 是否处于启动初始化窗口期

    private val mainHandler = Handler(Looper.getMainLooper())

    // 初始化超时 Runnable：5s 后若仍无信号则退出初始化状态
    private val initTimeoutRunnable = Runnable {
        Log.d(TAG, "init timeout fired, hasReceivedAnySignal=$hasReceivedAnySignal")
        isInitializingWindow = false
        recomputeState()
    }

    // 心跳检查 Runnable：每 15s 检查一次心跳是否超时
    private val heartbeatCheckRunnable = object : Runnable {
        override fun run() {
            recomputeState()
            mainHandler.postDelayed(this, HEARTBEAT_CHECK_INTERVAL_MS)
        }
    }

    companion object {
        private const val TAG = "MonitoringViewModel"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val HEARTBEAT_TOLERANCE_MS = 15_000L   // 比心跳间隔多一些容错
        private const val HEARTBEAT_TIMEOUT_MS = HEARTBEAT_INTERVAL_MS + HEARTBEAT_TOLERANCE_MS
        private const val HEARTBEAT_CHECK_INTERVAL_MS = 15_000L
        private const val INIT_WINDOW_MS = 8_000L            // 启动后 8s 内不判定超时
    }

    init {
        Log.d(TAG, "MonitoringViewModel created.")
        _serviceEnabled.value = sharedPreferencesHelper.getServiceEnabledState()

        // 收集心跳
        viewModelScope.launch {
            VigilEventBus.heartbeat.collect {
                Log.d(TAG, "heartbeat received")
                lastHeartbeatTime = SystemClock.elapsedRealtime()
                hasReceivedAnySignal = true
                isInitializingWindow = false
                mainHandler.removeCallbacks(initTimeoutRunnable)
                recomputeState()
            }
        }

        // 收集连接状态
        viewModelScope.launch {
            VigilEventBus.serviceStatus.collect { isConnected ->
                Log.i(TAG, "serviceStatus received: isConnected=$isConnected")
                if (isConnected) {
                    hasReceivedAnySignal = true
                    isInitializingWindow = false
                    mainHandler.removeCallbacks(initTimeoutRunnable)
                    // connected 时刷新心跳时间，避免误判超时
                    lastHeartbeatTime = SystemClock.elapsedRealtime()
                }
                recomputeState()
            }
        }

        // 收集报警事件
        viewModelScope.launch {
            VigilEventBus.keywordAlert.collect { event ->
                Log.i(TAG, "AlertEvent received: keyword=${event.keyword}")
                _matchedKeywordForDialog.value = event.keyword
                _showKeywordAlertDialog.value = true
            }
        }

        // 启动心跳检查定时器
        mainHandler.post(heartbeatCheckRunnable)

        // 如果服务已开启，进入初始化窗口期
        if (_serviceEnabled.value) {
            enterInitWindow()
        }

        recomputeState()
    }

    override fun onCleared() {
        super.onCleared()
        mainHandler.removeCallbacks(initTimeoutRunnable)
        mainHandler.removeCallbacks(heartbeatCheckRunnable)
        Log.d(TAG, "MonitoringViewModel cleared.")
    }

    // ---- 状态计算核心：单一入口 ----
    private fun recomputeState() {
        val enabled = _serviceEnabled.value
        val hasNotifPermission = PermissionUtils.isNotificationListenerEnabled(context)
        val timeSinceHeartbeat = SystemClock.elapsedRealtime() - lastHeartbeatTime
        val heartbeatOk = hasReceivedAnySignal && timeSinceHeartbeat < HEARTBEAT_TIMEOUT_MS

        val newState = when {
            !enabled -> ServiceState.DISABLED
            !hasNotifPermission -> ServiceState.NO_PERMISSION
            isInitializingWindow -> ServiceState.INITIALIZING
            heartbeatOk -> {
                // 检查辅助权限（悬浮窗、通知发送）
                val missingOverlay = !PermissionUtils.canDrawOverlays(context)
                val missingPostNotif = !PermissionUtils.canPostNotifications(context)
                if (missingOverlay || missingPostNotif) ServiceState.RUNNING_LIMITED
                else ServiceState.RUNNING
            }
            !hasReceivedAnySignal -> ServiceState.INITIALIZING   // 从未收到信号，还在等
            else -> ServiceState.HEARTBEAT_TIMEOUT
        }

        // 构建调试信息
        val debugParts = mutableListOf<String>()
        debugParts.add("switch=${if (enabled) "ON" else "OFF"}")
        debugParts.add("notifPerm=${hasNotifPermission}")
        if (enabled) {
            debugParts.add("initWindow=$isInitializingWindow")
            if (hasReceivedAnySignal) {
                debugParts.add("lastBeat=${timeSinceHeartbeat / 1000}s ago")
            } else {
                debugParts.add("noSignalYet")
            }
        }

        _serviceState.value = newState
        _debugInfo.value = debugParts.joinToString(" | ")
        Log.d(TAG, "recomputeState → $newState [${_debugInfo.value}]")
    }

    private fun enterInitWindow() {
        isInitializingWindow = true
        hasReceivedAnySignal = false
        lastHeartbeatTime = 0L
        mainHandler.removeCallbacks(initTimeoutRunnable)
        mainHandler.postDelayed(initTimeoutRunnable, INIT_WINDOW_MS)
        Log.d(TAG, "entered init window (${INIT_WINDOW_MS}ms)")
    }

    // ---- 公开操作 ----

    fun onServiceEnabledChange(
        enabled: Boolean,
        isLicensed: Boolean,
        startServiceCallback: (Boolean) -> Unit,
        stopServiceCallback: () -> Unit
    ) {
        if (enabled && !isLicensed) {
            Log.w(TAG, "Not licensed, cannot enable service.")
            _serviceEnabled.value = false
            return
        }
        _serviceEnabled.value = enabled
        sharedPreferencesHelper.saveServiceEnabledState(enabled)

        if (enabled) {
            enterInitWindow()
            startServiceCallback(PermissionUtils.isNotificationListenerEnabled(context))
            notifyServiceToUpdateSettingsCallback?.invoke()
        } else {
            isInitializingWindow = false
            mainHandler.removeCallbacks(initTimeoutRunnable)
            notifyServiceToUpdateSettingsCallback?.invoke()
            stopServiceCallback()
        }
        recomputeState()
    }

    fun onRestartServiceClick(restartServiceCallback: () -> Unit) {
        Log.i(TAG, "User clicked restart service.")
        enterInitWindow()
        recomputeState()
        restartServiceCallback()
    }

    fun triggerShowKeywordAlert(keyword: String) {
        Log.i(TAG, "triggerShowKeywordAlert: $keyword")
        viewModelScope.launch {
            _matchedKeywordForDialog.value = keyword
            _showKeywordAlertDialog.value = true
        }
    }

    fun onKeywordAlertDialogConfirm() {
        Log.d(TAG, "Alert dialog confirmed.")
        viewModelScope.launch { VigilEventBus.alertConfirmed.emit(Unit) }
        onKeywordAlertDialogDismiss()
    }

    fun onKeywordAlertDialogDismiss() {
        _showKeywordAlertDialog.value = false
        _matchedKeywordForDialog.value = null
    }

    // ---- 回调（由 Activity/Screen 注入）----
    var startServiceCallback: ((Boolean) -> Unit)? = null
    var stopServiceCallback: (() -> Unit)? = null
    var restartServiceCallback: (() -> Unit)? = null
    var notifyServiceToUpdateSettingsCallback: (() -> Unit)? = null
}
