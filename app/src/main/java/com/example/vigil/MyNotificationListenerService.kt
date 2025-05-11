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
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat as CoreNotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MyNotificationListenerService : NotificationListenerService() {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var currentRingtoneUri: Uri? = null
    private var keywords: List<String> = emptyList()

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "VigilListenerService"
        const val ACTION_UPDATE_SETTINGS = "com.example.vigil.UPDATE_SETTINGS"
        private const val WAKELOCK_TAG = "Vigil::KeywordAlertWakeLock"
        private const val FOREGROUND_NOTIFICATION_ID = 717
        private const val ALERT_NOTIFICATION_CHANNEL_ID = "vigil_alert_channel"
        private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 1000L // 2 minutes
        private const val FULL_SCREEN_NOTIFICATION_ID = 718
    }

    private val alertConfirmedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlertDialogActivity.ACTION_ALERT_CONFIRMED) {
                Log.i(TAG, "收到确认广播，停止铃声和释放锁。")
                stopRingtone()
                releaseWakeLock()
                CoreNotificationManagerCompat.from(applicationContext).cancel(FULL_SCREEN_NOTIFICATION_ID)
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
            IntentFilter(AlertDialogActivity.ACTION_ALERT_CONFIRMED)
        )
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        Log.i(TAG, "服务已创建并启动为前台服务。")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "通知监听器已连接。")
        loadSettings()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "通知监听器已断开连接！")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到命令: ${intent?.action}")
        if (intent?.action == ACTION_UPDATE_SETTINGS) {
            Log.i(TAG, "收到 ACTION_UPDATE_SETTINGS，重新加载设置。")
            loadSettings()
        }
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "服务销毁中...")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alertConfirmedReceiver)
        stopRingtone()
        releaseWakeLock()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        Log.w(TAG, "服务已销毁，资源已释放。")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) {
            return
        }
        if (sbn == null) { Log.w(TAG, "StatusBarNotification 为空，忽略。"); return }

        if (sbn.packageName == packageName && (sbn.tag == AlertDialogActivity.TAG || sbn.id == FOREGROUND_NOTIFICATION_ID || sbn.id == FULL_SCREEN_NOTIFICATION_ID)) {
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
            handler.post {
                val canPostNotificationsByPermissionUtil = PermissionUtils.canPostNotifications(applicationContext)

                if (canPostNotificationsByPermissionUtil) { // 优先使用 PermissionUtils 的检查结果
                    acquireWakeLock()
                    playRingtoneLooping()

                    val alertIntent = Intent(applicationContext, AlertDialogActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        putExtra(AlertDialogActivity.EXTRA_MATCHED_KEYWORD, matchedKeyword)
                    }
                    val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    val fullScreenPendingIntent = PendingIntent.getActivity(applicationContext, (System.currentTimeMillis() % 10000).toInt(), alertIntent, pendingIntentFlags)

                    val notificationBuilder = NotificationCompat.Builder(applicationContext, ALERT_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_icon)
                        .setContentTitle(getString(R.string.alert_dialog_title))
                        .setContentText(getString(R.string.alert_dialog_message_keyword_format, matchedKeyword))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setFullScreenIntent(fullScreenPendingIntent, true)
                        .setAutoCancel(true)

                    try {
                        // 即使 PermissionUtils.canPostNotifications 返回 true，
                        // Lint 仍然建议对 notify 调用进行 try-catch，以防万一。
                        CoreNotificationManagerCompat.from(applicationContext).notify(FULL_SCREEN_NOTIFICATION_ID, notificationBuilder.build())
                        Log.i(TAG, "FullScreen Intent 通知已发送 (关键词: $matchedKeyword)。")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "发送 FullScreen Intent 通知失败! (SecurityException)", e)
                        stopRingtoneAndLock()
                        // 在这里调用备选通知时，也需要注意 SecurityException
                        sendFallbackNotification(matchedKeyword, "无法显示全屏提醒，请检查应用权限。")
                    } catch (e: Exception) {
                        Log.e(TAG, "发送 FullScreen Intent 通知失败! (其他异常)", e)
                        stopRingtoneAndLock()
                        sendFallbackNotification(matchedKeyword, "显示提醒时发生错误。")
                    }
                } else {
                    Log.e(TAG, "无发送通知权限 (通过 PermissionUtils 检测)。无法执行任何提醒操作。")
                    // 如果 PermissionUtils 检测到没有权限，就不应该尝试发送任何通知。
                    // 如果仍希望在某些情况下尝试发送备选通知（例如，如果全屏Intent失败但普通通知可能成功），
                    // 则 sendFallbackNotification 内部的权限检查和 try-catch 仍然重要。
                }
            }
        }
    }

    private fun stopRingtoneAndLock() {
        stopRingtone()
        releaseWakeLock()
    }

    private fun sendFallbackNotification(
        keyword: String,
        additionalInfo: String? = null
    ) {
        // 再次检查权限，作为双重保险，尽管调用此方法的地方可能已经检查过
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
            CoreNotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build()) // 使用不同的通知ID
            Log.i(TAG, "已发送备选通知 (关键词: $keyword)。")
        } catch (e: SecurityException) {
            // 这是针对 Lint 警告的主要修复点
            Log.e(TAG, "发送备选通知时捕获到 SecurityException (POST_NOTIFICATIONS 权限可能在检查后被撤销)", e)
        } catch (e: Exception) {
            Log.e(TAG, "发送备选通知时发生其他错误", e)
        }
    }


    private fun loadSettings() {
        val oldKeywords = keywords
        val oldUri = currentRingtoneUri

        keywords = sharedPreferencesHelper.getKeywords()
        currentRingtoneUri = sharedPreferencesHelper.getRingtoneUri()

        if (oldKeywords != keywords || oldUri != currentRingtoneUri) {
            Log.i(TAG, "设置已更新: ${keywords.size}个关键词, 铃声 URI: '$currentRingtoneUri'")
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
                        mp.start()
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
                } catch (e: Exception) {
                    Log.e(TAG, "释放唤醒锁时出错", e)
                }
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
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

    private fun createForegroundServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("${getString(R.string.app_name)} 正在运行")
            .setContentText("正在后台监听关键词...")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
