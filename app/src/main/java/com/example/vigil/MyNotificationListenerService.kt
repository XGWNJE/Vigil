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
    private var isListenerConnected = false

    companion object {
        private const val TAG = "NotificationListener"
        const val ACTION_UPDATE_SETTINGS = "com.example.vigil.UPDATE_SETTINGS"
        private const val WAKELOCK_TAG = "Vigil::KeywordAlertWakeLock"
        private const val FOREGROUND_NOTIFICATION_ID = 717
        private const val ALERT_NOTIFICATION_CHANNEL_ID = "vigil_alert_channel"
        private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 1000L
        private const val FULL_SCREEN_NOTIFICATION_ID = 718 // 用于全屏提醒的通知ID
    }

    // --- 广播接收器 ---
    private val alertConfirmedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlertDialogActivity.ACTION_ALERT_CONFIRMED) {
                Log.i(TAG, "[Alert Confirmed] 收到确认广播，停止铃声和释放锁。")
                stopRingtone()
                releaseWakeLock()
                // 用户确认后，取消全屏通知 (如果它还存在)
                CoreNotificationManagerCompat.from(applicationContext).cancel(FULL_SCREEN_NOTIFICATION_ID)
            }
        }
    }

    // --- 服务生命周期 ---
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "[onCreate] 服务创建中...")
        sharedPreferencesHelper = SharedPreferencesHelper(applicationContext)
        loadSettings()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            alertConfirmedReceiver,
            IntentFilter(AlertDialogActivity.ACTION_ALERT_CONFIRMED)
        )
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        Log.i(TAG, "[onCreate] 服务已创建并启动为前台服务。")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        Log.i(TAG, "[onListenerConnected] 通知监听器已连接。 isListenerConnected = true")
        loadSettings()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isListenerConnected = false
        Log.w(TAG, "[onListenerDisconnected] 通知监听器已断开连接！ isListenerConnected = false")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[onStartCommand] 收到命令: ${intent?.action}, flags: $flags, startId: $startId")
        if (intent?.action == ACTION_UPDATE_SETTINGS) {
            Log.i(TAG, "[onStartCommand] 收到 ACTION_UPDATE_SETTINGS，重新加载设置。")
            loadSettings()
        }
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        Log.d(TAG, "[onStartCommand] 确保服务为前台服务。")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "[onDestroy] 服务销毁中...")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alertConfirmedReceiver)
        stopRingtone()
        releaseWakeLock()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        Log.w(TAG, "[onDestroy] 服务已销毁，资源已释放，前台服务已停止。")
    }

    // --- 核心通知处理 ---
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val postTime = System.currentTimeMillis()
        Log.d(TAG, "[onNotificationPosted] 开始处理通知 (ID: ${sbn?.id}, Pkg: ${sbn?.packageName}) at $postTime")

        if (!isListenerConnected) {
            Log.e(TAG, "[onNotificationPosted] 错误：监听器未连接，无法处理通知！")
            return
        }
        if (sbn == null) { Log.w(TAG, "[onNotificationPosted] StatusBarNotification 为空，忽略。"); return }
        if (!SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) { Log.d(TAG, "[onNotificationPosted] 服务已被用户禁用，忽略通知 (ID: ${sbn.id})。"); return }
        if (sbn.packageName == packageName && (sbn.tag == AlertDialogActivity.TAG || sbn.id == FOREGROUND_NOTIFICATION_ID || sbn.id == FULL_SCREEN_NOTIFICATION_ID)) { Log.d(TAG, "[onNotificationPosted] 忽略应用自身通知 (ID: ${sbn.id}, Tag: ${sbn.tag})。"); return }

        val notification = sbn.notification
        if (notification == null) { Log.w(TAG, "[onNotificationPosted] Notification 对象为空，忽略 (ID: ${sbn.id}, Pkg: ${sbn.packageName})。"); return }

        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val notificationContent = "$title $text $bigText".lowercase()

        Log.d(TAG, "[onNotificationPosted] 提取内容: Title='${title}', Text='${text}', BigText='${bigText}'")

        val currentKeywords = keywords
        if (currentKeywords.isEmpty()) { Log.d(TAG, "[onNotificationPosted] 当前关键词列表为空，不进行匹配。"); return }
        Log.d(TAG, "[onNotificationPosted] 当前关键词列表: $currentKeywords")

        var matchedKeyword: String? = null
        for (keyword in currentKeywords) {
            if (keyword.isNotBlank() && notificationContent.contains(keyword.lowercase())) {
                Log.i(TAG, "[onNotificationPosted] *** 关键词匹配成功! *** Keyword: '$keyword', Notification ID: ${sbn.id}")
                matchedKeyword = keyword
                break
            }
        }

        if (matchedKeyword != null) {
            Log.i(TAG, "[onNotificationPosted] 匹配成功，准备在主线程处理...")
            handler.post {
                val handlerPostTime = System.currentTimeMillis()
                Log.d(TAG, "[Handler.post] 开始执行 - 距离 onNotificationPosted: ${handlerPostTime - postTime}ms")

                // **修改：优先检查发送通知权限，因为 FullScreen Intent 依赖它**
                val canPostNotifications = PermissionUtils.canPostNotifications(applicationContext)
                Log.d(TAG, "[Handler.post] 权限检查结果: canPostNotifications=$canPostNotifications")

                if (canPostNotifications) {
                    // **情况 A：有发送通知权限 -> 使用 FullScreen Intent**
                    Log.i(TAG, "[Handler.post] 拥有发送通知权限，准备使用 FullScreen Intent。")
                    acquireWakeLock()
                    playRingtoneLooping() // 只有能发送通知时才播放循环铃声

                    // 创建启动 AlertDialogActivity 的 Intent
                    val alertIntent = Intent(applicationContext, AlertDialogActivity::class.java).apply {
                        // 清除旧实例，确保每次都是新的弹窗请求
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        putExtra(AlertDialogActivity.EXTRA_MATCHED_KEYWORD, matchedKeyword) // 传递关键词
                    }
                    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    // 使用不同的 requestCode (例如基于时间) 以确保 PendingIntent 是唯一的，如果需要的话
                    val fullScreenPendingIntent = PendingIntent.getActivity(applicationContext, (System.currentTimeMillis() % 10000).toInt(), alertIntent, pendingIntentFlags)

                    // 构建高优先级通知并设置 FullScreen Intent
                    val notificationBuilder = NotificationCompat.Builder(applicationContext, ALERT_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_icon) // 必须设置小图标
                        .setContentTitle(getString(R.string.alert_dialog_title)) // 使用统一标题
                        .setContentText(getString(R.string.alert_dialog_message_keyword_format, matchedKeyword)) // 简化内容
                        .setPriority(NotificationCompat.PRIORITY_HIGH) // 高优先级
                        .setCategory(NotificationCompat.CATEGORY_ALARM) // 或 CATEGORY_CALL，表明是紧急提醒
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 在锁屏显示
                        // **设置全屏 Intent**
                        .setFullScreenIntent(fullScreenPendingIntent, true) // true 表示高优先级
                        // 可选：设置普通点击行为，例如也打开 MainActivity
                        // .setContentIntent(PendingIntent.getActivity(...))
                        // 可选：添加 Action Button，例如“稍后提醒” (需要额外逻辑)
                        // .addAction(...)
                        .setAutoCancel(true) // 用户点击通知本身时取消 (如果设置了 ContentIntent)

                    try {
                        Log.i(TAG, "[Handler.post] 尝试发送 FullScreen Intent 通知 (ID: $FULL_SCREEN_NOTIFICATION_ID)...")
                        CoreNotificationManagerCompat.from(applicationContext).notify(FULL_SCREEN_NOTIFICATION_ID, notificationBuilder.build())
                        Log.i(TAG, "[Handler.post] FullScreen Intent 通知已发送。")
                    } catch (e: SecurityException) {
                        // 特别捕捉 SecurityException，可能与 USE_FULL_SCREEN_INTENT 权限有关
                        Log.e(TAG, "[Handler.post] 发送 FullScreen Intent 通知失败! (SecurityException)", e)
                        stopRingtone() // 发送失败，停止铃声
                        releaseWakeLock()
                        // 可以尝试发送一个普通通知告知用户权限问题
                        sendFallbackNotification(title, text, matchedKeyword, false, "无法显示全屏提醒，请检查应用权限。")
                    } catch (e: Exception) {
                        Log.e(TAG, "[Handler.post] 发送 FullScreen Intent 通知失败! (其他异常)", e)
                        stopRingtone() // 发送失败，停止铃声
                        releaseWakeLock()
                        sendFallbackNotification(title, text, matchedKeyword, false, "显示提醒时发生错误。")
                    }

                } else {
                    // **情况 B：没有发送通知权限 -> 无法进行任何提醒**
                    Log.e(TAG, "[Handler.post] 无发送通知权限。无法执行任何提醒操作（包括全屏和备选通知）。")
                    // 不获取锁，不播放铃声
                }
                val handlerEndTime = System.currentTimeMillis()
                Log.d(TAG, "[Handler.post] 执行完毕 - 耗时: ${handlerEndTime - handlerPostTime}ms")
            }
        } else {
            Log.d(TAG, "[onNotificationPosted] 未匹配到关键词。")
        }
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "[onNotificationPosted] 处理结束 - 总耗时: ${endTime - postTime}ms")
    }


    // --- 辅助方法 ---
    private fun sendFallbackNotification(
        title: String,
        text: String,
        keyword: String,
        showOverlayPermissionPrompt: Boolean = false, // 保留以备将来使用或调试
        additionalInfo: String? = null
    ) {
        // 权限检查已在调用前完成，或在此函数开始时再次检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "[sendFallbackNotification] 没有 POST_NOTIFICATIONS 权限，无法发送。")
                return
            }
        }

        val intentAction: PendingIntent?
        val notificationMessage: String
        val notificationTitle: String

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        notificationTitle = "Vigil 提醒" // 通用标题
        var baseMessage = "检测到关键词 '$keyword'。"
        if (additionalInfo != null) {
            baseMessage += "\n($additionalInfo)"
        } else if (showOverlayPermissionPrompt) {
            // 虽然主流程变了，但如果因为其他原因调用且需要提示，可以保留
            baseMessage += "\n请检查悬浮窗权限以确保弹窗提醒正常。"
            mainActivityIntent.putExtra("reason", "missing_overlay_permission")
        } else {
            baseMessage += "\n标题: $title\n内容: $text"
        }
        notificationMessage = baseMessage

        // 使用不同的 request code 避免冲突
        val requestCode = if(showOverlayPermissionPrompt) 2 else 3
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        intentAction = PendingIntent.getActivity(this, requestCode, mainActivityIntent, pendingIntentFlags)


        val builder = NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(notificationTitle)
            .setContentText(notificationMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 普通通知用默认优先级
            .setAutoCancel(true)
            .setContentIntent(intentAction)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // 使用不同的通知ID，避免覆盖全屏通知或前台通知
        CoreNotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        Log.d(TAG, "[sendFallbackNotification] 已发送备选通知。")
    }


    private fun loadSettings() {
        Log.d(TAG, "[loadSettings] 正在加载设置...")
        val oldKeywords = keywords
        val oldUri = currentRingtoneUri
        keywords = sharedPreferencesHelper.getKeywords()
        currentRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        if (oldKeywords != keywords || oldUri != currentRingtoneUri) {
            Log.i(TAG, "[loadSettings] 设置已更新: Keywords=${keywords.size}个, Ringtone URI='$currentRingtoneUri'")
        } else {
            Log.d(TAG, "[loadSettings] 设置未改变。")
        }
    }

    private fun playRingtoneLooping() {
        Log.d(TAG, "[playRingtoneLooping] 请求播放循环铃声...")
        stopRingtone()

        val ringtoneUriToPlay = currentRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (ringtoneUriToPlay == null) {
            Log.e(TAG, "[playRingtoneLooping] 无法获取铃声 URI！")
            releaseWakeLock()
            return
        }
        Log.d(TAG, "[playRingtoneLooping] 准备播放 URI: $ringtoneUriToPlay")

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUriToPlay)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true
                Log.d(TAG, "[playRingtoneLooping] MediaPlayer 设置完成，准备异步加载...")
                prepareAsync()
                setOnPreparedListener {
                    Log.i(TAG, "[MediaPlayer.onPrepared] MediaPlayer 已准备好，开始播放！")
                    try {
                        start()
                    } catch (startEx: IllegalStateException) {
                        Log.e(TAG, "[MediaPlayer.onPrepared] 调用 start() 时出错", startEx)
                        stopRingtone()
                        releaseWakeLock()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "[MediaPlayer.onError] 播放错误: what=$what, extra=$extra, URI: $ringtoneUriToPlay")
                    stopRingtone()
                    releaseWakeLock()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[playRingtoneLooping] 设置 MediaPlayer 数据源时出错", e)
            stopRingtone()
            releaseWakeLock()
        }
    }

    private fun stopRingtone() {
        if (mediaPlayer != null) {
            Log.d(TAG, "[stopRingtone] 尝试停止和释放 MediaPlayer...")
            try {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.stop()
                    Log.i(TAG, "[stopRingtone] MediaPlayer 已停止。")
                }
                mediaPlayer!!.release()
                Log.i(TAG, "[stopRingtone] MediaPlayer 已释放。")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "[stopRingtone] 停止或释放 MediaPlayer 时出错", e)
            } finally {
                mediaPlayer = null
            }
        } else {
            Log.d(TAG, "[stopRingtone] MediaPlayer 为空，无需停止。")
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        Log.d(TAG, "[acquireWakeLock] 尝试获取唤醒锁...")
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            try {
                acquire(WAKELOCK_TIMEOUT_MS)
                if (isHeld) {
                    Log.i(TAG, "[acquireWakeLock] 唤醒锁已获取 (超时: ${WAKELOCK_TIMEOUT_MS / 1000}秒)。")
                } else {
                    Log.w(TAG, "[acquireWakeLock] 调用 acquire 后，锁仍未持有？")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[acquireWakeLock] 获取唤醒锁时出错", e)
                wakeLock = null
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    Log.i(TAG, "[releaseWakeLock] 唤醒锁已释放。")
                } catch (e: Exception) {
                    Log.e(TAG, "[releaseWakeLock] 释放唤醒锁时出错", e)
                }
            } else {
                Log.d(TAG, "[releaseWakeLock] 唤醒锁存在但未持有，无需释放。")
            }
        }
        if (wakeLock != null) {
            Log.d(TAG, "[releaseWakeLock] 将 wakeLock 引用置空。")
            wakeLock = null
        }
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
        Log.d(TAG,"[createNotificationChannel] 通知渠道已创建: $ALERT_NOTIFICATION_CHANNEL_ID")
    }

    private fun createForegroundServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name) + " 正在运行")
            .setContentText("正在后台监听关键词...")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
