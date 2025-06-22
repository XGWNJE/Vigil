// src/main/java/com/example/vigil/MyNotificationListenerService.kt
package com.example.vigil

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat as CoreNotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.vigil.ui.monitoring.MonitoringViewModel

class MyNotificationListenerService : NotificationListenerService() {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var currentRingtoneUri: Uri? = null
    private var keywords: List<String> = emptyList()

    private var filterAppsEnabled: Boolean = false
    private var filteredAppPackages: Set<String> = emptySet()

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    companion object {
        private const val TAG = "VigilListenerService"
        const val ACTION_UPDATE_SETTINGS = "com.example.vigil.UPDATE_SETTINGS"
        private const val WAKELOCK_TAG = "Vigil::KeywordAlertWakeLock"
        private const val FOREGROUND_NOTIFICATION_ID = 717
        private const val ALERT_NOTIFICATION_CHANNEL_ID = "vigil_alert_channel"
        private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 1000L

        const val ACTION_HEARTBEAT = "com.example.vigil.ACTION_HEARTBEAT"
        private const val HEARTBEAT_INTERVAL_MS = 30 * 1000L
        const val ACTION_ALERT_CONFIRMED_FROM_UI = "com.example.vigil.ACTION_ALERT_CONFIRMED_FROM_UI"

        // 新增：用于从服务启动 MainActivity 并请求显示对话框的 Action 和 Extra
        const val ACTION_SHOW_ALERT_FROM_SERVICE = "com.example.vigil.ACTION_SHOW_ALERT_FROM_SERVICE"
        const val EXTRA_ALERT_KEYWORD_FROM_SERVICE = "com.example.vigil.EXTRA_ALERT_KEYWORD_FROM_SERVICE"
    }

    private val alertConfirmedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_ALERT_CONFIRMED_FROM_UI) {
                Log.i(TAG, "收到来自 UI 的确认广播，停止铃声和释放锁。")
                stopRingtoneAndLock()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "服务创建中...")
        sharedPreferencesHelper = SharedPreferencesHelper(applicationContext)
        loadSettings()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            alertConfirmedReceiver,
            IntentFilter(ACTION_ALERT_CONFIRMED_FROM_UI)
        )
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        Log.i(TAG, "服务已创建并启动为前台服务。")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "通知监听器已连接。")
        loadSettings()
        startHeartbeat()
        sendServiceStatusUpdate(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "通知监听器已断开连接！")
        stopHeartbeat()
        sendServiceStatusUpdate(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到命令: ${intent?.action}")
        if (intent?.action == ACTION_UPDATE_SETTINGS) {
            Log.i(TAG, "收到 ACTION_UPDATE_SETTINGS，重新加载设置。")
            loadSettings()
        } else if (intent?.action == null) {
            Log.i(TAG, "服务由系统或 START_STICKY 重启，开始发送心跳。")
            startHeartbeat()
        }
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "服务销毁中...")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alertConfirmedReceiver)
        stopHeartbeat()
        stopRingtoneAndLock()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        Log.w(TAG, "服务已销毁，资源已释放。")
        sendServiceStatusUpdate(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) {
            Log.d(TAG, "服务未被用户启用，忽略通知。")
            return
        }

        val isLicensed = sharedPreferencesHelper.isAuthenticated()
        if (!isLicensed) {
            Log.w(TAG, "未授权，忽略通知处理。")
            return
        }

        if (sbn == null) { Log.w(TAG, "StatusBarNotification 为空，忽略。"); return }

        if (filterAppsEnabled && filteredAppPackages.isNotEmpty()) {
            if (sbn.packageName !in filteredAppPackages) {
                Log.d(TAG, "通知来自 ${sbn.packageName}，不在过滤列表中，忽略。当前过滤应用列表: $filteredAppPackages")
                return
            }
            Log.d(TAG, "通知来自 ${sbn.packageName}，在过滤列表中，继续处理。")
        }

        if (sbn.packageName == packageName && (sbn.id == FOREGROUND_NOTIFICATION_ID)) {
            return
        }

        val notification = sbn.notification ?: run {
            Log.w(TAG, "Notification 对象为空，忽略 (ID: ${sbn.id}, Pkg: ${sbn.packageName})。")
            return
        }

        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val notificationContent = "$title $text $bigText".lowercase()

        val currentKeywords = keywords
        if (currentKeywords.isEmpty()) {
            return
        }

        var matchedKeyword: String? = null
        for (keyword in currentKeywords) {
            if (keyword.isNotBlank() && notificationContent.contains(keyword.lowercase())) {
                Log.i(TAG, "关键词匹配成功! Keyword: '$keyword', App: ${sbn.packageName}")
                matchedKeyword = keyword
                break
            }
        }

        if (matchedKeyword != null) {
            val finalMatchedKeyword = matchedKeyword
            handler.post {
                acquireWakeLock()
                playRingtoneLooping()

                // 1. 发送 LocalBroadcast 给 ViewModel (如果应用在前台，ViewModel 可以直接响应)
                val localAlertIntent = Intent(MonitoringViewModel.ACTION_SHOW_KEYWORD_ALERT).apply {
                    putExtra(MonitoringViewModel.EXTRA_KEYWORD_FOR_ALERT, finalMatchedKeyword)
                }
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(localAlertIntent)
                Log.i(TAG, "已发送 LocalBroadcast (ACTION_SHOW_KEYWORD_ALERT) 以显示关键词提醒对话框 (关键词: $finalMatchedKeyword)。")

                // 2. 尝试启动 MainActivity 并将其带到前台，传递显示提醒的指令
                // 这有助于在应用不在前台时也能触发对话框
                val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    action = ACTION_SHOW_ALERT_FROM_SERVICE // 自定义 Action
                    putExtra(EXTRA_ALERT_KEYWORD_FROM_SERVICE, finalMatchedKeyword)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                try {
                    startActivity(activityIntent)
                    Log.i(TAG, "已尝试启动 MainActivity 以显示提醒 (关键词: $finalMatchedKeyword)。")
                } catch (e: Exception) {
                    Log.e(TAG, "启动 MainActivity 时发生错误: ", e)
                    // 如果启动 Activity 失败，可以考虑发送一个高优先级通知作为备选
                    sendFallbackNotification(finalMatchedKeyword, "请打开应用查看详情")
                }
            }
        }
    }

    private fun stopRingtoneAndLock() {
        stopRingtone()
        releaseWakeLock()
    }

    private fun sendFallbackNotification(keyword: String, additionalInfo: String? = null) {
        if (!PermissionUtils.canPostNotifications(this)) {
            Log.w(TAG, "备选通知：无 POST_NOTIFICATIONS 权限，无法发送。")
            return
        }

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SHOW_ALERT_FROM_SERVICE // 点击通知也使用这个 Action
            putExtra(EXTRA_ALERT_KEYWORD_FROM_SERVICE, keyword)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intentAction = PendingIntent.getActivity(this, System.currentTimeMillis().toInt() % 10001, mainActivityIntent, pendingIntentFlags)

        val notificationTitle = getString(R.string.app_name) // 使用应用名称作为标题
        val baseMessage = getString(R.string.alert_dialog_message_keyword_format, keyword) // "检测到关键词: '%1$s'"

        val builder = NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(notificationTitle)
            .setContentText(baseMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(baseMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 提高优先级
            .setAutoCancel(true)
            .setContentIntent(intentAction)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(intentAction, true) // 尝试使用全屏 Intent (需要 USE_FULL_SCREEN_INTENT 权限)

        try {
            CoreNotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
            Log.i(TAG, "已发送备选通知 (关键词: $keyword)。")
        } catch (e: SecurityException) {
            Log.e(TAG, "发送备选通知时捕获到 SecurityException", e)
        } catch (e: Exception) {
            Log.e(TAG, "发送备选通知时发生其他错误", e)
        }
    }

    private fun loadSettings() {
        keywords = sharedPreferencesHelper.getKeywords()
        currentRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        filterAppsEnabled = sharedPreferencesHelper.getFilterAppsEnabledState()
        filteredAppPackages = sharedPreferencesHelper.getFilteredAppPackages()
        Log.i(TAG, "服务设置已加载/更新: ${keywords.size}个关键词, 铃声 URI: '$currentRingtoneUri', 应用过滤启用: $filterAppsEnabled, 过滤列表大小: ${filteredAppPackages.size}")
        // 添加详细日志以便调试
        if (filterAppsEnabled && filteredAppPackages.isNotEmpty()) {
            Log.d(TAG, "应用过滤已启用，包含的应用包名: ${filteredAppPackages.joinToString()}")
        } else if (filterAppsEnabled) {
            Log.w(TAG, "应用过滤已启用，但过滤列表为空，将监听所有应用")
        } else {
            Log.d(TAG, "应用过滤未启用，将监听所有应用")
        }
    }

    private fun playRingtoneLooping() {
        stopRingtone()
        val ringtoneUriToPlay = currentRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (ringtoneUriToPlay == null) {
            Log.e(TAG, "无法获取铃声 URI！")
            releaseWakeLock()
            return
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUriToPlay)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true
                prepareAsync()
                setOnPreparedListener { mp ->
                    Log.i(TAG, "MediaPlayer 已准备好，开始播放。")
                    try {
                        if (!mp.isPlaying) {
                            mp.start()
                        }
                    } catch (startEx: IllegalStateException) {
                        Log.e(TAG, "MediaPlayer 调用 start() 时出错", startEx)
                        stopRingtoneAndLock()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer 播放错误: what=$what, extra=$extra, URI: $ringtoneUriToPlay")
                    stopRingtoneAndLock()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置 MediaPlayer 数据源或准备时出错", e)
            stopRingtoneAndLock()
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "停止或释放 MediaPlayer 时出错", e)
            } finally {
                mediaPlayer = null
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            Log.d(TAG, "唤醒锁已持有，无需重复获取。")
            return
        }
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            try {
                acquire(WAKELOCK_TIMEOUT_MS)
                if (isHeld) {
                    Log.i(TAG, "唤醒锁已获取 (超时: ${WAKELOCK_TIMEOUT_MS / 1000}秒)。")
                } else {
                    Log.w(TAG, "调用 acquire 后，锁仍未持有？")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取唤醒锁时出错", e)
                wakeLock = null
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    Log.i(TAG, "唤醒锁已释放。")
                } catch (e: Exception) {
                    Log.e(TAG, "释放唤醒锁时出错", e)
                }
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALERT_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG,"通知渠道 '$ALERT_NOTIFICATION_CHANNEL_ID' 已创建/更新。")
        }
    }

    private fun createForegroundServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("${getString(R.string.app_name)} ${getString(R.string.service_running)}")
            .setContentText(getString(R.string.foreground_service_description))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun sendHeartbeat() {
        val intent = Intent(ACTION_HEARTBEAT)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun startHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, 1000)
        Log.d(TAG, "已安排服务心跳发送 (每隔 ${HEARTBEAT_INTERVAL_MS / 1000} 秒)。")
    }

    private fun stopHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        Log.d(TAG, "已停止服务心跳发送。")
    }

    private fun sendServiceStatusUpdate(isConnected: Boolean) {
        val intent = Intent(MainActivity.ACTION_SERVICE_STATUS_UPDATE).apply {
            putExtra(MainActivity.EXTRA_SERVICE_CONNECTED, isConnected)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "发送服务连接状态更新广播: $isConnected")
    }
}
