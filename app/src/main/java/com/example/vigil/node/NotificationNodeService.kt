package com.example.vigil.node

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.vigil.MainActivity
import com.example.vigil.MyNotificationListenerService
import com.example.vigil.SharedPreferencesHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

data class NodeState(val status: String = "已停止", val connected: Boolean = false, val scope: String = "由控制台分配",
                     val received: Int = 0, val lastResponse: Long = 0, val timeZone: String? = null)

/** 独立前台连接；收件入持久化队列，声音仍由现有报警引擎执行。 */
class NotificationNodeService : Service() {
    companion object {
        const val NOTIFICATION_ID = 718
        private val mutableState = MutableStateFlow(NodeState())
        val state = mutableState.asStateFlow()
        fun start(context: android.content.Context) = ContextCompat.startForegroundService(context, Intent(context, NotificationNodeService::class.java))
    }
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    private lateinit var settings: NotificationNodeSettings
    private lateinit var alarms: SharedPreferencesHelper
    private var socket: WebSocket? = null
    private var generation = 0
    private var authenticated = false
    private var stopped = false
    private var terminal = false
    private var nextAttempt = 0L
    private var retryMs = 1_000L
    private var connectedAt = 0L
    private var lastResponse = 0L
    private var outageId: String? = null
    private var outageAccepted = false
    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            val now = SystemClock.elapsedRealtime()
            if (!terminal && now - lastResponse >= 45_000) {
                if (outageId == null) outageId = "service-${UUID.randomUUID()}"
                if (!outageAccepted) {
                    val wall = System.currentTimeMillis()
                    outageAccepted = alarms.acceptRemoteAlert(outageId!!, "服务中断", "统一服务", "连续 45 秒未收到服务响应", wall, wall + 30_000)
                    if (outageAccepted) wakePlayback()
                }
                if (socket != null) reconnect("服务响应超时")
            }
            if (socket != null && !authenticated && now - connectedAt >= 12_000) reconnect("认证超时")
            if (!terminal && socket == null && now >= nextAttempt) connect()
            if (authenticated) socket?.send(JSONObject().put("type", "heartbeat-notifier").put("deviceId", settings.read().deviceId).toString())
            handler.postDelayed(this, 3_000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        settings = NotificationNodeSettings(this)
        mutableState.value = NodeState(timeZone = settings.timeZone)
        alarms = SharedPreferencesHelper(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("notification-node", "通知节点连接", NotificationManager.IMPORTANCE_LOW))
        val launch = PendingIntent.getActivity(this, 718, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, "notification-node")
            .setSmallIcon(com.example.vigil.R.drawable.ic_notification_icon).setContentTitle("Vigil 通知节点")
            .setContentText("后台接收统一服务报警").setContentIntent(launch).setOngoing(true).build())
        lastResponse = SystemClock.elapsedRealtime()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settings.enabled || !NotificationNodeSettings.valid(settings.read())) { stopSelf(); return START_NOT_STICKY }
        if (socket == null && !terminal) connect()
        handler.removeCallbacks(tick)
        handler.post(tick)
        if (alarms.getActiveAlert() != null) wakePlayback()
        return START_STICKY
    }
    private fun connect() {
        val value = settings.read()
        val own = ++generation
        authenticated = false
        connectedAt = SystemClock.elapsedRealtime()
        mutableState.value = mutableState.value.copy(status = "连接中", connected = false)
        socket = client.newWebSocket(Request.Builder().url(value.endpoint).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { handler.post {
                if (own != generation || stopped) { ws.cancel(); return@post }
                ws.send(JSONObject().put("type", "auth").put("deviceId", value.deviceId).put("deviceName", value.name)
                    .put("role", "notifier").put("nodeType", "notification").put("platform", "android")
                    .put("channel", value.channel).put("apiKey", value.apiKey).toString())
            } }
            override fun onMessage(ws: WebSocket, text: String) { handler.post {
                if (own != generation || stopped || text.length > 1_000_000) return@post
                runCatching { handleMessage(ws, JSONObject(text)) }.onFailure {
                    mutableState.value = mutableState.value.copy(status = "收到无效消息")
                }
            } }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { handler.post { if (own == generation && !stopped) reconnect("连接断开，等待重试") } }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { handler.post { if (own == generation && !stopped) reconnect("连接关闭，等待重试") } }
        })
    }
    private fun handleMessage(ws: WebSocket, message: JSONObject) {
        val type = message.optString("type")
        if (type == "auth-result") {
            if (!message.optBoolean("success")) { terminal = true; reconnect("身份或通道不匹配，请修改连接配置"); return }
            authenticated = true
            retryMs = 1_000
            message.optJSONObject("notificationScope")?.let { updateScope(it) }
            message.optJSONObject("timeStandard")?.let { updateTimeStandard(it) }
        }
        if (type == "kicked") { terminal = true; reconnect("此身份已在另一台设备连接"); return }
        if (!authenticated) return
        lastResponse = SystemClock.elapsedRealtime()
        outageId = null
        outageAccepted = false
        mutableState.value = mutableState.value.copy(status = "已连接", connected = true, lastResponse = lastResponse)
        if (type == "notification-scope") message.optJSONObject("scope")?.let { updateScope(it) }
        if (type == "time-standard") updateTimeStandard(message)
        if (type == "alert") {
            val id = message.getString("alertId")
            val kinds = mapOf("visual-detection" to "视觉检测", "sensor-detection" to "传感器检测", "connection-lost" to "连接中断", "detection-interrupted" to "检测中断")
            val label = kinds[message.getString("eventKind")] ?: return
            val timestamp = Instant.parse(message.getString("timestamp")).toEpochMilli()
            val expires = Instant.parse(message.getString("expiresAt")).toEpochMilli()
            val source = message.optString("deviceName", message.getString("deviceId")) + message.optString("sourceName").let { if (it.isBlank()) "" else " · $it" }
            if (alarms.acceptRemoteAlert(id, label, source, message.optString("summary"), timestamp, expires)) {
                ws.send(JSONObject().put("type", "notification-receipt").put("alertId", id).toString())
                wakePlayback()
                mutableState.value = mutableState.value.copy(received = mutableState.value.received + 1)
            }
        }
    }
    private fun updateScope(scope: JSONObject) {
        val count = scope.optJSONArray("targets")?.length() ?: 0
        mutableState.value = mutableState.value.copy(scope = if (scope.optString("mode") == "all") "全部检测节点" else "指定 $count 个节点或来源")
    }
    private fun updateTimeStandard(value: JSONObject) {
        val timeZone = value.getString("timeZone")
        check(settings.saveTimeZone(timeZone)) { "无法保存告警时间标准" }
        mutableState.value = mutableState.value.copy(timeZone = timeZone)
    }
    private fun wakePlayback() {
        runCatching { ContextCompat.startForegroundService(this, Intent(this, MyNotificationListenerService::class.java)) }
            .onFailure { mutableState.value = mutableState.value.copy(status = "报警已保存，请打开 Vigil 恢复播放") }
    }
    private fun reconnect(reason: String) {
        ++generation
        socket?.cancel(); socket = null; authenticated = false
        nextAttempt = SystemClock.elapsedRealtime() + retryMs
        retryMs = (retryMs * 2).coerceAtMost(30_000)
        mutableState.value = mutableState.value.copy(status = reason, connected = false)
    }
    override fun onDestroy() {
        stopped = true
        ++generation
        handler.removeCallbacksAndMessages(null)
        socket?.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        mutableState.value = NodeState(timeZone = settings.timeZone)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
