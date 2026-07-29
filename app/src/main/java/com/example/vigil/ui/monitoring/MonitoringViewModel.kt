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
import com.example.vigil.VigilLogger
import kotlinx.coroutines.launch

/**
 * 服务状态的单一枚举，所有 UI 元素从这一个来源派生状态，避免多个独立状态变量不同步。
 */
enum class ServiceState {
    DISABLED,               // 用户关闭了服务开关
    INITIALIZING,           // 开关已开，等待服务连接信号（<5s）
    RUNNING,                // 运行正常，心跳最近有收到且系统绑定存活
    RUNNING_LIMITED,        // 运行中，但通知发送权限缺失，前台通知可能受影响
    LISTENER_DISCONNECTED,  // 进程活着（心跳正常）但系统监听绑定断开，自动重连中
    HEARTBEAT_TIMEOUT,      // 心跳超时，Service 可能被系统杀死
    NO_PERMISSION,          // 通知监听权限未授予（系统设置里没有开）
    ERROR                   // 未知错误状态
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
    // 系统监听绑定状态：初始值取持久化兜底（EventBus 无 replay，冷启动收不到历史事件）
    private var listenerConnected = sharedPreferencesHelper.getListenerConnectedState()

    private val mainHandler = Handler(Looper.getMainLooper())

    // 初始化超时 Runnable：5s 后若仍无信号则退出初始化状态
    private val initTimeoutRunnable = Runnable {
        Log.d(TAG, "init timeout fired, hasReceivedAnySignal=$hasReceivedAnySignal")
        VigilLogger.w(context, TAG, "初始化窗口超时仍无服务信号 (hasReceivedAnySignal=$hasReceivedAnySignal)")
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

        // 若存在未确认报警（报警期间进程被杀、或报警在后台触发导致弹窗未能显示），
        // 用户打开应用时直接弹出确认对话框，避免铃声一直循环却无法停止
        sharedPreferencesHelper.getPendingAlert()?.let { (keyword, _) ->
            Log.w(TAG, "检测到未确认报警，启动时直接弹出确认对话框: $keyword")
            _matchedKeywordForDialog.value = keyword
            _showKeywordAlertDialog.value = true
        }

        // 收集心跳（payload 为服务发射时刻的时间戳；replay=1，冷启动立即拿到最近一次心跳）
        viewModelScope.launch {
            VigilEventBus.heartbeat.collect { beatAt ->
                Log.d(TAG, "heartbeat received")
                // 用发射时刻而非接收时刻计时：replay 出来的旧心跳能算对年龄，服务真死仍会判超时
                lastHeartbeatTime = beatAt
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
                listenerConnected = isConnected
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

        // 如果服务已开启，进入初始化窗口期；
        // 但若 replay 心跳已在上方的收集中先到（服务与 UI 同进程，heartbeat replay=1），
        // 说明服务刚刚活过，直接保持 RUNNING，不再进窗口空等
        if (_serviceEnabled.value && !hasReceivedAnySignal) {
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
            // 心跳正常但系统监听绑定断开：进程活着却收不到通知，看门狗自动重连中
            heartbeatOk && !listenerConnected -> ServiceState.LISTENER_DISCONNECTED
            heartbeatOk -> ServiceState.RUNNING
            !hasReceivedAnySignal -> ServiceState.INITIALIZING   // 从未收到信号，还在等
            else -> ServiceState.HEARTBEAT_TIMEOUT
        }

        // 构建调试信息
        val debugParts = mutableListOf<String>()
        debugParts.add("switch=${if (enabled) "ON" else "OFF"}")
        debugParts.add("notifPerm=${hasNotifPermission}")
        if (enabled) {
            debugParts.add("initWindow=$isInitializingWindow")
            debugParts.add("listenerBound=$listenerConnected")
            if (hasReceivedAnySignal) {
                debugParts.add("lastBeat=${timeSinceHeartbeat / 1000}s ago")
            } else {
                debugParts.add("noSignalYet")
            }
        }

        _debugInfo.value = debugParts.joinToString(" | ")
        if (newState != _serviceState.value) {
            VigilLogger.i(context, TAG, "服务状态变化: ${_serviceState.value} → $newState [${_debugInfo.value}]")
        }
        _serviceState.value = newState
        Log.d(TAG, "recomputeState → $newState [${_debugInfo.value}]")
    }

    private fun enterInitWindow() {
        isInitializingWindow = true
        hasReceivedAnySignal = false
        lastHeartbeatTime = 0L
        mainHandler.removeCallbacks(initTimeoutRunnable)
        mainHandler.postDelayed(initTimeoutRunnable, INIT_WINDOW_MS)
        Log.d(TAG, "entered init window (${INIT_WINDOW_MS}ms)")
        VigilLogger.i(context, TAG, "进入初始化窗口，等待服务信号 (${INIT_WINDOW_MS}ms)")
    }

    // ---- 公开操作 ----

    fun onServiceEnabledChange(
        enabled: Boolean,
        startServiceCallback: (Boolean) -> Unit,
        stopServiceCallback: () -> Unit
    ) {
        if (enabled) {
            // Pre-check: verify notification listener permission before changing state
            val hasNotifPermission = PermissionUtils.isNotificationListenerEnabled(context)
            if (!hasNotifPermission) {
                Log.w(TAG, "Notification listener permission not granted, cannot enable service.")
                _serviceEnabled.value = false
                sharedPreferencesHelper.saveServiceEnabledState(false)
                // Permission toast will be shown by caller via startServiceCallback(false)
                startServiceCallback(false)
                recomputeState()
                return
            }
            // Permission OK — enter enabling flow
            _serviceEnabled.value = true
            sharedPreferencesHelper.saveServiceEnabledState(true)
            enterInitWindow()
            startServiceCallback(true)
            notifyServiceToUpdateSettingsCallback?.invoke()
        } else {
            _serviceEnabled.value = false
            sharedPreferencesHelper.saveServiceEnabledState(false)
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
