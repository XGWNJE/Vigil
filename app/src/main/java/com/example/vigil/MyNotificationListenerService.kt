package com.example.vigil

import android.app.Notification
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * MyNotificationListenerService 是一个后台服务，用于监听系统通知。
 * 当收到符合关键词的通知时，它会播放指定的铃声。
 */
class MyNotificationListenerService : NotificationListenerService() {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var currentRingtoneUri: Uri? = null
    private var keywords: List<String> = emptyList()

    companion object {
        private const val TAG = "NotificationListener"
        // 用于从 MainActivity 更新服务状态或触发重新加载配置
        const val ACTION_UPDATE_SETTINGS = "com.example.vigil.UPDATE_SETTINGS"
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferencesHelper = SharedPreferencesHelper(applicationContext)
        loadSettings()
        // 中文日志：通知监听服务已创建
        Log.i(TAG, "通知监听服务已创建 (MyNotificationListenerService created)")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 中文日志：通知监听器已连接
        Log.i(TAG, "通知监听器已连接 (Listener connected)")
        // 可以在这里再次确认服务是否应该运行，基于 SharedPreferencesHelper.isServiceEnabledByUser
        if (!SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) {
            // 如果用户已在 UI 中禁用了服务，则不应处理通知
            // 但 NotificationListenerService 的启停更多是由系统设置和 startService/stopService 控制
            // 这里主要是确保配置是最新的
            Log.i(TAG, "服务在 SharedPreferences 中标记为禁用，但监听器已连接。确保配置正确。")
        }
        loadSettings() // 确保连接时配置是最新的
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // 中文日志：通知监听器已断开连接
        Log.i(TAG, "通知监听器已断开连接 (Listener disconnected)")
    }

    /**
     * 当系统发布新的通知时调用。
     *
     * @param sbn 包含通知详情的 StatusBarNotification 对象。
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) {
            // 如果服务被用户禁用，则不处理通知
            if (sbn !=null && !SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) {
                Log.d(TAG, "服务已被用户禁用，忽略通知。")
            }
            return
        }

        // 重新加载设置以确保是最新的，因为服务可能长时间运行
        // 考虑性能，可以不在每个通知都加载，或者通过 Intent 更新
        // loadSettings() // 暂时注释掉，避免频繁IO，依赖 onStartCommand 或 ACTION_UPDATE_SETTINGS

        val packageName = sbn.packageName
        val notification = sbn.notification
        if (notification == null) {
            // 中文日志：通知对象为空
            Log.w(TAG, "接收到 StatusBarNotification，但其 Notification 对象为空。包名: $packageName")
            return
        }

        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // 中文日志：收到通知
        Log.d(TAG, "收到通知: 包名=$packageName, 标题='$title', 内容='$text', 大文本='$bigText'")

        val notificationContent = "$title $text $bigText".lowercase()

        if (keywords.isEmpty()) {
            Log.d(TAG, "关键词列表为空，不进行匹配。")
            return
        }

        var matchFound = false
        for (keyword in keywords) {
            if (keyword.isNotBlank() && notificationContent.contains(keyword.lowercase())) {
                // 中文日志：关键词匹配成功
                Log.i(TAG, "关键词 '$keyword' 匹配成功!")
                matchFound = true
                break
            }
        }

        if (matchFound) {
            playRingtone()
        }
    }

    /**
     * 当通知被移除时调用。
     *
     * @param sbn 被移除的通知。
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 当前应用场景下，通知移除的逻辑不是核心，但可以记录日志
        if (sbn != null) {
            val packageName = sbn.packageName
            val title = sbn.notification?.extras?.getString(Notification.EXTRA_TITLE) ?: "N/A"
            // 中文日志：通知已移除
            Log.d(TAG, "通知已移除: 包名=$packageName, 标题='$title'")
        }
    }

    /**
     * 当服务启动时调用，包括通过 startService()。
     * 用于接收 MainActivity 发来的更新指令。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 中文日志：服务 onStartCommand 被调用
        Log.d(TAG, "服务 onStartCommand: ${intent?.action}")
        if (intent?.action == ACTION_UPDATE_SETTINGS || intent == null /* 首次启动 */) {
            loadSettings()
            // 中文日志：设置已从 onStartCommand 更新
            Log.i(TAG, "设置已从 onStartCommand 或首次启动时更新/加载。")
        }
        // 根据文档，NotificationListenerService 通常由系统管理其生命周期，
        // 但 startService 仍可用于传递命令或确保其创建。
        // 返回 START_STICKY 尝试在服务被杀死后重启，但这对于 NLS 可能不是必需的，
        // 因为系统会尝试保持它的运行（如果用户授权了）。
        return START_STICKY
    }


    /**
     * 从 SharedPreferences 加载关键词和铃声设置。
     */
    private fun loadSettings() {
        keywords = sharedPreferencesHelper.getKeywords()
        currentRingtoneUri = sharedPreferencesHelper.getRingtoneUri()
        // 中文日志：设置已加载
        Log.i(TAG, "设置已加载: ${keywords.size} 个关键词, 铃声 URI: $currentRingtoneUri")
    }

    /**
     * 播放用户选择的铃声。
     * 如果未选择铃声，则播放系统默认通知音。
     */
    private fun playRingtone() {
        val ringtoneUriToPlay = currentRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (ringtoneUriToPlay == null) {
            // 中文日志：没有可播放的铃声
            Log.w(TAG, "无法播放铃声：铃声 URI 为空且无法获取默认通知音。")
            return
        }

        try {
            val ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUriToPlay)
            if (ringtone != null) {
                // 中文日志：正在播放铃声
                Log.i(TAG, "正在播放铃声: $ringtoneUriToPlay")
                ringtone.play()
            } else {
                // 中文日志：无法获取铃声对象
                Log.e(TAG, "无法获取铃声对象进行播放，URI: $ringtoneUriToPlay")
            }
        } catch (e: Exception) {
            // 中文日志：播放铃声时发生错误
            Log.e(TAG, "播放铃声时发生错误", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 中文日志：通知监听服务已销毁
        Log.i(TAG, "通知监听服务已销毁 (MyNotificationListenerService destroyed)")
    }
}
