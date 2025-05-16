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
// import android.content.pm.PackageManager // 如果未使用，可以移除
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
// import androidx.core.app.ActivityCompat // 如果未使用，可以移除
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat as CoreNotificationManagerCompat // 保持别名
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.vigil.ui.monitoring.MonitoringViewModel // 导入 ViewModel 以使用其常量

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
        // private const val FULL_SCREEN_NOTIFICATION_ID = 718 // 不再需要，因为 AlertDialogActivity 被移除

        const val ACTION_HEARTBEAT = "com.example.vigil.ACTION_HEARTBEAT"
        private const val HEARTBEAT_INTERVAL_MS = 30 * 1000L

        // 新增: 用于从 UI 确认提醒的 Action
        const val ACTION_ALERT_CONFIRMED_FROM_UI = "com.example.vigil.ACTION_ALERT_CONFIRMED_FROM_UI"
    }

    // 修改: 监听来自 UI (ViewModel) 的确认广播
    private val alertConfirmedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_ALERT_CONFIRMED_FROM_UI) { // 修改 Action
                Log.i(TAG, "收到来自 UI 的确认广播，停止铃声和释放锁。")
                stopRingtoneAndLock()
                // 不再需要取消 FULL_SCREEN_NOTIFICATION_ID
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
            IntentFilter(ACTION_ALERT_CONFIRMED_FROM_UI) // 修改 Action
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
        // 确保服务在前台运行
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "服务销毁中...")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alertConfirmedReceiver)
        stopHeartbeat()
        stopRingtoneAndLock() // 确保在销毁时停止
        stopForeground(Service.STOP_FOREGROUND_REMOVE) // 使用兼容的参数
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
                return
            }
        }

        // 修改: 调整自身通知的判断逻辑，移除 FULL_SCREEN_NOTIFICATION_ID
        if (sbn.packageName == packageName && (sbn.id == FOREGROUND_NOTIFICATION_ID /* || sbn.tag == AlertDialogActivity.TAG */)) {
            // AlertDialogActivity.TAG 不再需要
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
            val finalMatchedKeyword = matchedKeyword // 确保在 lambda 中是 final
            handler.post {
                // 检查悬浮窗权限（如果决定使用系统级提醒窗口）或发送通知权限
                // 对于 Compose Dialog，主要依赖应用自身在前台或有能力显示 Dialog
                // 但播放声音和唤醒锁仍然重要

                acquireWakeLock()
                playRingtoneLooping()

                // --- 修改: 发送广播给 ViewModel 以显示对话框 ---
                val alertIntent = Intent(MonitoringViewModel.ACTION_SHOW_KEYWORD_ALERT).apply {
                    putExtra(MonitoringViewModel.EXTRA_KEYWORD_FOR_ALERT, finalMatchedKeyword)
                    // setPackage(packageName) // 可选，如果只希望应用内接收
                }
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(alertIntent)
                Log.i(TAG, "已发送广播以显示关键词提醒对话框 (关键词: $finalMatchedKeyword)。")

                // 如果需要，仍然可以发送一个标准的通知作为备选或补充
                // sendFallbackNotification(finalMatchedKeyword, "请查看应用内提醒")
            }
        }
    }

    private fun stopRingtoneAndLock() {
        stopRingtone()
        releaseWakeLock()
    }

    // sendFallbackNotification 方法可以保留，以备不时之需，但当前主要依赖 Compose 对话框
    private fun sendFallbackNotification(
        keyword: String,
        additionalInfo: String? = null
    ) {
        // ... (此方法代码保持不变)
        if (!PermissionUtils.canPostNotifications(this)) {
            Log.w(TAG, "备选通知：无 POST_NOTIFICATIONS 权限，无法发送。")
            return
        }

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val notificationTitle = "Vigil 备选提醒"
        var baseMessage = "检测到关键词 '$keyword'."
        if (additionalInfo != null) {
            baseMessage += "\n($additionalInfo)"
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intentAction = PendingIntent.getActivity(this, System.currentTimeMillis().toInt() % 10001, mainActivityIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(notificationTitle)
            .setContentText(baseMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(baseMessage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intentAction)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

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
    }

    private fun playRingtoneLooping() {
        stopRingtone() // 确保先停止任何正在播放的铃声
        val ringtoneUriToPlay = currentRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (ringtoneUriToPlay == null) {
            Log.e(TAG, "无法获取铃声 URI！")
            releaseWakeLock() // 如果无法播放铃声，也应该释放锁
            return
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUriToPlay)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) // 确保使用正确的音频用途
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true
                prepareAsync() // 使用异步准备
                setOnPreparedListener { mp ->
                    Log.i(TAG, "MediaPlayer 已准备好，开始播放。")
                    try {
                        if (!mp.isPlaying) { // 再次检查，避免重复启动
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
                    true // 表示错误已处理
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
                it.reset() // 重置状态
                it.release() // 释放资源
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
        // 先释放可能存在的旧锁
        releaseWakeLock()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, // 使用 PARTIAL_WAKE_LOCK 保持 CPU 运行，屏幕可以关闭
            WAKELOCK_TAG
        ).apply {
            try {
                acquire(WAKELOCK_TIMEOUT_MS) // 设置超时
                if (isHeld) {
                    Log.i(TAG, "唤醒锁已获取 (超时: ${WAKELOCK_TIMEOUT_MS / 1000}秒)。")
                } else {
                    Log.w(TAG, "调用 acquire 后，锁仍未持有？")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取唤醒锁时出错", e)
                wakeLock = null // 获取失败则置空
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
            val importance = NotificationManager.IMPORTANCE_HIGH // 高优先级用于提醒
            val channel = NotificationChannel(ALERT_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = android.graphics.Color.RED
                // 对于需要声音的提醒渠道，可以设置声音，但我们通过 MediaPlayer 控制
                // setSound(null, null) // 如果不希望此渠道的通知发出默认声音
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

        return NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID) // 使用相同的渠道ID
            .setContentTitle("${getString(R.string.app_name)} ${getString(R.string.service_running)}")
            .setContentText(getString(R.string.foreground_service_description)) // 新增一个字符串资源
            .setSmallIcon(R.drawable.ic_notification_icon) // 确保这个图标存在
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 使其持续
            .setSilent(true)  // 前台服务通知通常是静默的，不打扰用户
            .setPriority(NotificationCompat.PRIORITY_MIN) // 较低优先级，不那么显眼
            .build()
    }

    private fun sendHeartbeat() {
        val intent = Intent(ACTION_HEARTBEAT)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        // Log.d(TAG, "发送心跳广播。") // 心跳日志可以按需开启
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
