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
    // private var isListenerConnected = false // Replaced by direct system check in MainActivity for UI

    companion object {
        private const val TAG = "VigilListenerService" // 日志标签已修改
        const val ACTION_UPDATE_SETTINGS = "com.example.vigil.UPDATE_SETTINGS"
        private const val WAKELOCK_TAG = "Vigil::KeywordAlertWakeLock"
        private const val FOREGROUND_NOTIFICATION_ID = 717
        private const val ALERT_NOTIFICATION_CHANNEL_ID = "vigil_alert_channel"
        private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 1000L
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
        // 初始连接状态将在 onListenerConnected 中明确
        Log.i(TAG, "服务已创建并启动为前台服务。")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // isListenerConnected = true // 内部状态标记，可选
        Log.i(TAG, "通知监听器已连接。")
        // 可以在此处发送广播或更新共享首选项（如果其他组件需要精确的连接事件）
        // 但对于MainActivity的UI，它会直接查询系统状态
        loadSettings() // 确保设置是最新的
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // isListenerConnected = false // 内部状态标记，可选
        Log.w(TAG, "通知监听器已断开连接！这可能是系统行为或服务问题。")
        // 同上，MainActivity会通过系统查询来更新UI
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到命令: ${intent?.action}")
        if (intent?.action == ACTION_UPDATE_SETTINGS) {
            Log.i(TAG, "收到 ACTION_UPDATE_SETTINGS，重新加载设置。")
            loadSettings()
        }
        // 确保服务在前台运行
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "服务销毁中...")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alertConfirmedReceiver)
        stopRingtone()
        releaseWakeLock()
        stopForeground(Service.STOP_FOREGROUND_REMOVE) // 使用 STOP_FOREGROUND_REMOVE
        // isListenerConnected = false // 内部状态标记，可选
        Log.w(TAG, "服务已销毁，资源已释放。")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val postTime = System.currentTimeMillis()
        // Log.d(TAG, "开始处理通知 (ID: ${sbn?.id}, Pkg: ${sbn?.packageName}) at $postTime") // 减少不必要的日志

        // 检查服务是否应处理通知 (用户是否启用)
        if (!SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) {
            // Log.d(TAG, "服务已被用户禁用，忽略通知 (ID: ${sbn?.id})。") // 仅在需要时记录
            return
        }

        if (sbn == null) { Log.w(TAG, "StatusBarNotification 为空，忽略。"); return }

        // 忽略自身应用的通知，防止循环或干扰
        if (sbn.packageName == packageName && (sbn.tag == AlertDialogActivity.TAG || sbn.id == FOREGROUND_NOTIFICATION_ID || sbn.id == FULL_SCREEN_NOTIFICATION_ID)) {
            // Log.d(TAG, "忽略应用自身通知 (ID: ${sbn.id}, Tag: ${sbn.tag})。")
            return
        }

        val notification = sbn.notification
        if (notification == null) { Log.w(TAG, "Notification 对象为空，忽略 (ID: ${sbn.id}, Pkg: ${sbn.packageName})。"); return }

        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val notificationContent = "$title $text $bigText".lowercase()

        // Log.d(TAG, "提取内容: Title='${title}', Text='${text}', BigText='${bigText}'")

        val currentKeywords = keywords // 使用加载到内存的关键词
        if (currentKeywords.isEmpty()) {
            // Log.d(TAG, "当前关键词列表为空，不进行匹配。")
            return
        }
        // Log.d(TAG, "当前关键词列表: $currentKeywords")

        var matchedKeyword: String? = null
        for (keyword in currentKeywords) {
            if (keyword.isNotBlank() && notificationContent.contains(keyword.lowercase())) {
                Log.i(TAG, "关键词匹配成功! Keyword: '$keyword', App: ${sbn.packageName}")
                matchedKeyword = keyword
                break
            }
        }

        if (matchedKeyword != null) {
            // Log.i(TAG, "匹配成功，准备在主线程处理...") // 减少日志
            handler.post {
                // val handlerPostTime = System.currentTimeMillis()
                // Log.d(TAG, "Handler.post 开始执行 - 距离 onNotificationPosted: ${handlerPostTime - postTime}ms")

                val canPostNotifications = PermissionUtils.canPostNotifications(applicationContext)
                // Log.d(TAG, "权限检查结果: canPostNotifications=$canPostNotifications")

                if (canPostNotifications) {
                    // Log.i(TAG, "拥有发送通知权限，准备使用 FullScreen Intent。")
                    acquireWakeLock()
                    playRingtoneLooping()

                    val alertIntent = Intent(applicationContext, AlertDialogActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        putExtra(AlertDialogActivity.EXTRA_MATCHED_KEYWORD, matchedKeyword)
                    }
                    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val fullScreenPendingIntent = PendingIntent.getActivity(applicationContext, (System.currentTimeMillis() % 10000).toInt(), alertIntent, pendingIntentFlags)

                    val notificationBuilder = NotificationCompat.Builder(applicationContext, ALERT_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_icon)
                        .setContentTitle(getString(R.string.alert_dialog_title))
                        .setContentText(getString(R.string.alert_dialog_message_keyword_format, matchedKeyword))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setFullScreenIntent(fullScreenPendingIntent, true)
                        .setAutoCancel(true) // 点击通知本身时取消 (如果 AlertDialogActivity 没启动或被意外关闭)

                    try {
                        // Log.i(TAG, "尝试发送 FullScreen Intent 通知 (ID: $FULL_SCREEN_NOTIFICATION_ID)...")
                        CoreNotificationManagerCompat.from(applicationContext).notify(FULL_SCREEN_NOTIFICATION_ID, notificationBuilder.build())
                        Log.i(TAG, "FullScreen Intent 通知已发送 (关键词: $matchedKeyword)。")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "发送 FullScreen Intent 通知失败! (SecurityException)", e)
                        stopRingtoneAndLock()
                        sendFallbackNotification(title, text, matchedKeyword, "无法显示全屏提醒，请检查应用权限。")
                    } catch (e: Exception) {
                        Log.e(TAG, "发送 FullScreen Intent 通知失败! (其他异常)", e)
                        stopRingtoneAndLock()
                        sendFallbackNotification(title, text, matchedKeyword, "显示提醒时发生错误。")
                    }
                } else {
                    Log.e(TAG, "无发送通知权限。无法执行任何提醒操作。")
                    // 不获取锁，不播放铃声
                }
                // val handlerEndTime = System.currentTimeMillis()
                // Log.d(TAG, "Handler.post 执行完毕 - 耗时: ${handlerEndTime - handlerPostTime}ms")
            }
        }
        // else { Log.d(TAG, "未匹配到关键词。") }
        // val endTime = System.currentTimeMillis()
        // Log.d(TAG, "处理结束 - 总耗时: ${endTime - postTime}ms") // 减少不必要的日志
    }

    private fun stopRingtoneAndLock() {
        stopRingtone()
        releaseWakeLock()
    }

    private fun sendFallbackNotification(
        originalTitle: String,
        originalText: String,
        keyword: String,
        additionalInfo: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "无 POST_NOTIFICATIONS 权限，无法发送备选通知。")
                return
            }
        }

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val notificationTitle = "Vigil 备选提醒"
        var baseMessage = "检测到关键词 '$keyword'."
        if (additionalInfo != null) {
            baseMessage += "\n($additionalInfo)"
        }
        // baseMessage += "\n原通知: $originalTitle - $originalText" // 可选：包含原通知信息

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intentAction = PendingIntent.getActivity(this, System.currentTimeMillis().toInt() % 10001, mainActivityIntent, pendingIntentFlags) // Unique request code

        val builder = NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(notificationTitle)
            .setContentText(baseMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(baseMessage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intentAction)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        CoreNotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build()) // Unique notification ID
        Log.i(TAG, "已发送备选通知 (关键词: $keyword)。")
    }


    private fun loadSettings() {
        // Log.d(TAG, "正在加载设置...") // 减少日志
        val oldKeywords = keywords
        val oldUri = currentRingtoneUri

        keywords = sharedPreferencesHelper.getKeywords()
        currentRingtoneUri = sharedPreferencesHelper.getRingtoneUri()

        if (oldKeywords != keywords || oldUri != currentRingtoneUri) {
            Log.i(TAG, "设置已更新: ${keywords.size}个关键词, 铃声 URI: '$currentRingtoneUri'")
        }
    }

    private fun playRingtoneLooping() {
        // Log.d(TAG, "请求播放循环铃声...") // 减少日志
        stopRingtone() // 确保先停止任何正在播放的

        val ringtoneUriToPlay = currentRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (ringtoneUriToPlay == null) {
            Log.e(TAG, "无法获取铃声 URI！")
            releaseWakeLock() // 如果无法播放，也释放锁
            return
        }
        // Log.d(TAG, "准备播放 URI: $ringtoneUriToPlay")

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUriToPlay)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) // 确保使用正确的音频焦点和流类型
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true
                // Log.d(TAG, "MediaPlayer 设置完成，准备异步加载...")
                prepareAsync() // 异步准备，避免阻塞服务线程
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
                    true // 表示已处理错误
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置 MediaPlayer 数据源或准备时出错", e)
            stopRingtoneAndLock()
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.let {
            // Log.d(TAG, "尝试停止和释放 MediaPlayer...") // 减少日志
            try {
                if (it.isPlaying) {
                    it.stop()
                    // Log.i(TAG, "MediaPlayer 已停止。")
                }
                it.reset() // 重置状态
                it.release()
                // Log.i(TAG, "MediaPlayer 已释放。")
            } catch (e: Exception) { // IllegalStateException or other
                Log.e(TAG, "停止或释放 MediaPlayer 时出错", e)
            } finally {
                mediaPlayer = null
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        // Log.d(TAG, "尝试获取唤醒锁...") // 减少日志
        if (wakeLock?.isHeld == true) {
            // Log.d(TAG, "唤醒锁已持有，无需重复获取。")
            return
        }
        releaseWakeLock() // 确保之前的锁已释放
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, // PARTIAL_WAKE_LOCK 保持 CPU 运行，屏幕可关闭
            WAKELOCK_TAG
        ).apply {
            try {
                acquire(WAKELOCK_TIMEOUT_MS) // 设置超时，防止无限期持有
                if (isHeld) {
                    Log.i(TAG, "唤醒锁已获取 (超时: ${WAKELOCK_TIMEOUT_MS / 1000}秒)。")
                } else {
                    Log.w(TAG, "调用 acquire 后，锁仍未持有？") // 不太可能发生，但记录
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取唤醒锁时出错", e)
                wakeLock = null // 获取失败
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    // Log.i(TAG, "唤醒锁已释放。") // 减少日志
                } catch (e: Exception) {
                    Log.e(TAG, "释放唤醒锁时出错", e)
                }
            }
        }
        wakeLock = null // 清除引用
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
                // setSound(null, null) // 如果希望通知本身不发出声音，仅依赖 MediaPlayer
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG,"通知渠道 '$ALERT_NOTIFICATION_CHANNEL_ID' 已创建/更新。")
        }
    }

    private fun createForegroundServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID) // 使用相同的渠道ID
            .setContentTitle(getString(R.string.app_name) + " 正在运行")
            .setContentText("正在后台监听关键词...")
            .setSmallIcon(R.drawable.ic_notification_icon) // 确保图标存在
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 使其成为持续性通知
            .setSilent(true)  // 前台服务通知通常是静默的，避免打扰用户
            .setPriority(NotificationCompat.PRIORITY_MIN) // 最低优先级，减少在状态栏的干扰
            .build()
    }
}
