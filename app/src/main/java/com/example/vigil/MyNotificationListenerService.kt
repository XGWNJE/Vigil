// src/main/java/com/example/vigil/MyNotificationListenerService.kt
package com.example.vigil

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat as CoreNotificationManagerCompat
import com.example.vigil.ui.monitoring.MonitoringViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyNotificationListenerService : NotificationListenerService() {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var currentRingtoneValue: String? = null
    @Volatile private var keywords: List<String> = emptyList()

    // 关键词级铃声/循环次数映射与全局默认循环次数（0=直到确认）
    @Volatile private var keywordRingtoneMap: Map<String, String> = emptyMap()
    @Volatile private var keywordLoopCountMap: Map<String, Int> = emptyMap()
    @Volatile private var defaultLoopCount: Int = 0

    // 当前活动报警信息（写历史记录用；进程重建后为 null，恢复路径另行回填）
    private var activeAlertKeyword: String? = null
    private var activeAlertSourceApp: String? = null

    @Volatile private var filterAppsEnabled: Boolean = false
    @Volatile private var filteredAppPackages: Set<String> = emptySet()

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val alertedNotificationKeys = mutableSetOf<String>()

    // 系统绑定状态：只有它才代表 NotificationManagerService 正在向本服务投递通知。
    // 进程被杀后系统以 START_STICKY 重建服务时，onListenerConnected 可能永不被调用，
    // 此时进程活着、心跳照发，但通知永远不来 —— 看门狗靠此标志识别并自愈。
    @Volatile private var listenerActuallyConnected: Boolean = false
    private var rebindFailCount: Int = 0
    private var heartbeatCount: Int = 0

    private enum class PlayerState { IDLE, PREPARING, PLAYING, STOPPED }
    @Volatile private var playerState = PlayerState.IDLE

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            // 每 20 次心跳（10 分钟）落一条存活标记，便于事后判断进程/绑定在何时断掉
            heartbeatCount++
            if (heartbeatCount % 20 == 0) {
                VigilLogger.i(applicationContext, TAG,
                    "存活标记: 心跳#${heartbeatCount}, 绑定=$listenerActuallyConnected, 播放器=$playerState")
            }
            watchdogListenerBinding()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    /**
     * 绑定看门狗：进程活着但系统未绑定监听服务时自动自愈（心跳每 30s 触发一次）。
     * 升级策略（2026-08 issue #2：HyperOS 上 requestRebind 被系统静默忽略）：
     *   本实例前 2 次：requestRebind（低频尝试，官方 API）
     *   之后：完整重连序列（stopService → 组件 disable/enable → 重启 → requestRebind，
     *         90s 节流，等效系统级撤销+重授权；连续无效次数由 ListenerRecovery
     *         进程级累计，跨服务实例不清零）
     *   序列连续无效达上限：标记恢复失败（UI 显示"重新授权"引导），此后仅低频
     *   requestRebind 兜底，不无限升级，避免在系统侧卡死态里空转耗电
     */
    private fun watchdogListenerBinding() {
        if (listenerActuallyConnected) {
            if (rebindFailCount > 0 || sharedPreferencesHelper.getListenerRecoveryFailed()) {
                rebindFailCount = 0
                ListenerRecovery.markRecoverySuccess(applicationContext)
            }
            return
        }
        if (!SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) return
        if (!PermissionUtils.isNotificationListenerEnabled(applicationContext)) return

        rebindFailCount++
        if (rebindFailCount <= WATCHDOG_SOFT_REBIND_ATTEMPTS) {
            VigilLogger.w(applicationContext, TAG, "看门狗: 监听未绑定（第 $rebindFailCount 次），自动 requestRebind")
            Log.w(TAG, "检测到监听服务未绑定（第 $rebindFailCount 次），自动 requestRebind。")
            ListenerRecovery.requestRebind(applicationContext)
        } else if (sharedPreferencesHelper.getListenerRecoveryFailed()) {
            // 已标记失败：不再升级折腾，保持低频请求兜底，等用户重新授权或系统自行恢复
            VigilLogger.w(applicationContext, TAG, "看门狗: 自动重连已失败，保持低频 requestRebind 兜底")
            ListenerRecovery.requestRebind(applicationContext)
        } else {
            VigilLogger.w(applicationContext, TAG, "看门狗: requestRebind 未恢复（第 $rebindFailCount 次），升级为完整重连序列")
            Log.w(TAG, "requestRebind 未恢复（第 $rebindFailCount 次），升级为完整重连序列。")
            ListenerRecovery.forceReconnect(applicationContext)
        }
    }

    companion object {
        private const val TAG = "VigilListenerService"
        const val ACTION_UPDATE_SETTINGS = "com.example.vigil.UPDATE_SETTINGS"
        private const val WAKELOCK_TAG = "Vigil::KeywordAlertWakeLock"
        private const val FOREGROUND_NOTIFICATION_ID = 717
        private const val FOREGROUND_CHANNEL_ID = "vigil_foreground_channel"
        private const val WAKELOCK_TIMEOUT_MS = 5 * 60 * 1000L  // 5 分钟，应对激进电池优化设备
        private const val PENDING_ALERT_TTL_MS = 30 * 60 * 1000L  // 未确认报警恢复窗口：30 分钟

        const val ACTION_HEARTBEAT = "com.example.vigil.ACTION_HEARTBEAT"
        private const val HEARTBEAT_INTERVAL_MS = 30 * 1000L
        // 看门狗升级档位（见 watchdogListenerBinding 注释）：
        // 本实例前 2 次 requestRebind；之后升级完整重连序列（ListenerRecovery 内 90s 节流）
        private const val WATCHDOG_SOFT_REBIND_ATTEMPTS = 2
        const val ACTION_ALERT_CONFIRMED_FROM_UI = "com.example.vigil.ACTION_ALERT_CONFIRMED_FROM_UI"

        // 新增：用于从服务启动 MainActivity 并请求显示对话框的 Action 和 Extra
        const val ACTION_SHOW_ALERT_FROM_SERVICE = "com.example.vigil.ACTION_SHOW_ALERT_FROM_SERVICE"
        const val EXTRA_ALERT_KEYWORD_FROM_SERVICE = "com.example.vigil.EXTRA_ALERT_KEYWORD_FROM_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "服务创建中...")
        VigilLogger.i(applicationContext, TAG, "服务创建 (onCreate)")
        sharedPreferencesHelper = SharedPreferencesHelper(applicationContext)
        // 进程若是被系统强杀后重建，onDestroy 不会执行，持久化的绑定状态会残留 true；
        // 重建后绑定状态未知，先重置为 false，等 onListenerConnected 真正回调再置 true
        listenerActuallyConnected = false
        sharedPreferencesHelper.saveListenerConnectedState(false)
        // 同步广播给 UI：冷启动时持久化值可能是旧进程残留的 true，
        // 不发事件则 UI 只能在下次心跳后靠"绑定已断"才纠正，期间误显示"监听中"
        sendServiceStatusUpdate(false)
        loadSettings()
        // 监听 UI 确认报警事件（替代 LocalBroadcastManager alertConfirmedReceiver）
        serviceScope.launch {
            VigilEventBus.alertConfirmed.collect {
                Log.i(TAG, "收到来自 UI 的确认事件，停止铃声和释放锁。")
                VigilLogger.i(applicationContext, TAG, "用户确认报警，清除 pending 并停止响铃")
                recordAlertEnd(AlertEndType.MANUAL)
                sharedPreferencesHelper.clearPendingAlert()
                stopRingtoneAndLock()
                activeAlertKeyword = null
                activeAlertSourceApp = null
            }
        }
        createNotificationChannel()

        // 使用简单方式启动前台服务
        val notification = createForegroundServiceNotification()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        Log.i(TAG, "服务已创建并启动为前台服务。")

        // 进程被杀重建后，恢复此前未被用户确认的报警
        recoverPendingAlertIfNeeded()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "通知监听器已连接。")
        VigilLogger.i(applicationContext, TAG, "onListenerConnected: 系统监听绑定已建立")
        listenerActuallyConnected = true
        rebindFailCount = 0
        sharedPreferencesHelper.saveListenerConnectedState(true)
        ListenerRecovery.markRecoverySuccess(applicationContext)
        loadSettings()
        startHeartbeat()
        sendServiceStatusUpdate(true)

        // 确保更新通知以反映最新状态
        updateForegroundNotification()
        Log.d(TAG, "监听器连接后已更新前台通知")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "通知监听器已断开连接！")
        VigilLogger.w(applicationContext, TAG, "onListenerDisconnected: 系统监听绑定断开，心跳停止（看门狗随心跳暂停）")
        listenerActuallyConnected = false
        sharedPreferencesHelper.saveListenerConnectedState(false)
        stopHeartbeat()
        sendServiceStatusUpdate(false)

        // 确保更新通知以反映最新状态
        updateForegroundNotification()
        Log.d(TAG, "监听器断开连接后已更新前台通知")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到命令: ${intent?.action}")
        if (intent?.action == ACTION_UPDATE_SETTINGS) {
            Log.i(TAG, "收到 ACTION_UPDATE_SETTINGS，重新加载设置。")
            loadSettings() // loadSettings内部会调用updateForegroundNotification
        } else if (intent?.action == null) {
            Log.i(TAG, "服务由系统或 START_STICKY 重启，重新加载设置并发送心跳。")
            VigilLogger.i(applicationContext, TAG, "onStartCommand: 系统重建服务 (action=null, START_STICKY)")
            loadSettings()
            startHeartbeat()
        } else {
            // 其他任何命令也确保通知更新
            updateForegroundNotification()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "服务销毁中...")
        VigilLogger.w(applicationContext, TAG, "服务销毁 (onDestroy)")
        listenerActuallyConnected = false
        sharedPreferencesHelper.saveListenerConnectedState(false)
        serviceScope.cancel()
        stopHeartbeat()
        stopRingtoneAndLock()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        Log.w(TAG, "服务已销毁，资源已释放。")
        sendServiceStatusUpdate(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) { Log.w(TAG, "StatusBarNotification 为空，忽略。"); return }

        if (filterAppsEnabled && filteredAppPackages.isNotEmpty()) {
            if (sbn.packageName !in filteredAppPackages) {
                Log.d(TAG, "通知来自 ${sbn.packageName}，不在过滤列表中，忽略。当前过滤应用列表: $filteredAppPackages")
                VigilLogger.d(applicationContext, TAG, "过滤排除: pkg=${sbn.packageName} 不在监听列表 $filteredAppPackages")
                return
            }
            Log.d(TAG, "通知来自 ${sbn.packageName}，在过滤列表中，继续处理。")
            VigilLogger.d(applicationContext, TAG, "通知到达: pkg=${sbn.packageName} (在过滤列表中)")
        }

        if (sbn.packageName == packageName && (sbn.id == FOREGROUND_NOTIFICATION_ID)) {
            return
        }

        VigilLogger.d(applicationContext, TAG, "通知到达: pkg=${sbn.packageName}")

        // 检查服务是否被用户启用 - 只在这里检查，因为我们想保持前台服务运行，即使通知提醒功能被禁用
        if (!SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)) {
            Log.d(TAG, "服务未被用户启用，忽略通知。")
            VigilLogger.d(applicationContext, TAG, "忽略通知: pkg=${sbn.packageName}, 原因=服务开关关闭")
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
            Log.d(TAG, "关键词列表为空，忽略通知 (Pkg: ${sbn.packageName})。")
            VigilLogger.d(applicationContext, TAG, "忽略通知: pkg=${sbn.packageName}, 原因=关键词列表为空")
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

        if (matchedKeyword == null) {
            VigilLogger.d(applicationContext, TAG, "忽略通知: pkg=${sbn.packageName}, 原因=未命中关键词")
        }

        if (matchedKeyword != null) {
            val notifKey = sbn.key
            if (notifKey in alertedNotificationKeys) {
                Log.d(TAG, "通知 $notifKey 已报警过，跳过重复触发。")
                VigilLogger.d(applicationContext, TAG, "忽略通知: pkg=${sbn.packageName}, 原因=已报警过去重 (keyword=$matchedKeyword)")
                return
            }
            alertedNotificationKeys.add(notifKey)
            VigilLogger.i(applicationContext, TAG, "命中关键词 '$matchedKeyword' (pkg=${sbn.packageName})，触发报警")

            // 解析该关键词的铃声与循环次数（未配置则回落全局默认）
            val alertRingtoneValue = keywordRingtoneMap[matchedKeyword] ?: currentRingtoneValue
            val alertLoopLimit = keywordLoopCountMap[matchedKeyword] ?: defaultLoopCount

            // 获取来源应用名称（用于 Dialog 展示与历史记录）
            val sourceAppName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(sbn.packageName, 0)
                ).toString()
            } catch (e: Exception) { null }

            // 持久化未确认报警：进程被省电策略杀死后，服务重建时可恢复响铃（含铃声/剩余次数，铁律 3）
            sharedPreferencesHelper.savePendingAlert(matchedKeyword, alertRingtoneValue, alertLoopLimit, sourceAppName)
            activeAlertKeyword = matchedKeyword
            activeAlertSourceApp = sourceAppName

            val finalMatchedKeyword = matchedKeyword
            val snippet = "$title $text".trim().take(100).ifEmpty { null }

            handler.post {
                acquireWakeLock()
                playRingtoneLooping(alertRingtoneValue, alertLoopLimit, alreadyPlayed = 0)

                // 通过 VigilEventBus 通知 ViewModel 显示报警 Dialog（替代 LocalBroadcastManager）
                serviceScope.launch {
                    VigilEventBus.keywordAlert.emit(AlertEvent(finalMatchedKeyword, sourceAppName, snippet))
                }
                Log.i(TAG, "已发送 AlertEvent (关键词: $finalMatchedKeyword, 来源: $sourceAppName)。")

                // 启动 MainActivity 带到前台（应用不在前台时确保 Dialog 可见）
                val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    action = ACTION_SHOW_ALERT_FROM_SERVICE
                    putExtra(EXTRA_ALERT_KEYWORD_FROM_SERVICE, finalMatchedKeyword)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                try {
                    startActivity(activityIntent)
                    Log.i(TAG, "已尝试启动 MainActivity 以显示提醒 (关键词: $finalMatchedKeyword)。")
                } catch (e: Exception) {
                    Log.e(TAG, "启动 MainActivity 时发生错误: ", e)
                    sendFallbackNotification(finalMatchedKeyword, "请打开应用查看详情")
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let { alertedNotificationKeys.remove(it) }
    }

    private fun stopRingtoneAndLock() {
        VigilLogger.i(applicationContext, TAG, "停止报警铃声并释放唤醒锁")
        stopRingtone()
        releaseWakeLock()
    }

    /**
     * 进程被系统省电策略（如 Motorola Device Guard）杀死后，服务随系统重新绑定而重建。
     * 若存在未被用户确认的报警（未超过有效期），恢复响铃并重新弹出提醒，
     * 避免报警被静默吞掉。
     */
    private fun recoverPendingAlertIfNeeded() {
        val pending = sharedPreferencesHelper.getPendingAlert() ?: return
        if (System.currentTimeMillis() - pending.timestamp > PENDING_ALERT_TTL_MS) {
            Log.w(TAG, "未确认报警 (关键词: ${pending.keyword}) 已超过 ${PENDING_ALERT_TTL_MS / 60000} 分钟，按过期处理，清除。")
            sharedPreferencesHelper.clearPendingAlert()
            return
        }
        val keyword = pending.keyword
        // 有限档位剩余次数为 0（crash 前已到数但未来得及收尾）：直接走自动结束
        if (pending.loopLimit > 0 && pending.playedLoops >= pending.loopLimit) {
            Log.w(TAG, "未确认报警 (关键词: $keyword) 剩余次数为 0，按自动结束处理。")
            activeAlertKeyword = keyword
            activeAlertSourceApp = pending.sourceApp
            autoEndAlert(keyword)
            return
        }
        Log.w(TAG, "检测到未确认报警 (关键词: $keyword)，进程重建后恢复响铃与提醒。")
        VigilLogger.w(applicationContext, TAG, "恢复未确认报警 (keyword=$keyword, 已播=${pending.playedLoops}/${pending.loopLimit})：进程重建后重新响铃")
        activeAlertKeyword = keyword
        activeAlertSourceApp = pending.sourceApp
        handler.post {
            acquireWakeLock()
            playRingtoneLooping(pending.ringtoneUri, pending.loopLimit, pending.playedLoops)

            serviceScope.launch {
                VigilEventBus.keywordAlert.emit(AlertEvent(keyword, null, null))
            }

            val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = ACTION_SHOW_ALERT_FROM_SERVICE
                putExtra(EXTRA_ALERT_KEYWORD_FROM_SERVICE, keyword)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            try {
                startActivity(activityIntent)
            } catch (e: Exception) {
                Log.e(TAG, "恢复报警时启动 MainActivity 出错: ", e)
                sendFallbackNotification(keyword, "请打开应用查看详情")
            }
        }
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

        val builder = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(notificationTitle)
            .setContentText(baseMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 提高优先级
            .setAutoCancel(true)
            .setContentIntent(intentAction)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(intentAction, true) // 尝试使用全屏 Intent (需要 USE_FULL_SCREEN_INTENT 权限)

        try {
            // 使用随机ID以避免覆盖前台服务通知
            val alertNotificationId = System.currentTimeMillis().toInt() % 10000
            if (alertNotificationId == FOREGROUND_NOTIFICATION_ID) {
                // 避免与前台服务通知ID冲突
                CoreNotificationManagerCompat.from(this).notify(alertNotificationId + 1, builder.build())
            } else {
                CoreNotificationManagerCompat.from(this).notify(alertNotificationId, builder.build())
            }
            Log.i(TAG, "已发送备选通知 (关键词: $keyword)。")
        } catch (e: SecurityException) {
            Log.e(TAG, "发送备选通知时捕获到 SecurityException", e)
        } catch (e: Exception) {
            Log.e(TAG, "发送备选通知时发生其他错误", e)
        }
    }

    private fun loadSettings() {
        keywords = sharedPreferencesHelper.getKeywords()
        currentRingtoneValue = sharedPreferencesHelper.getRingtoneValue()
        keywordRingtoneMap = sharedPreferencesHelper.getKeywordRingtoneMap()
        keywordLoopCountMap = sharedPreferencesHelper.getKeywordLoopCountMap()
        defaultLoopCount = sharedPreferencesHelper.getDefaultLoopCount()
        filterAppsEnabled = sharedPreferencesHelper.getFilterAppsEnabledState()
        filteredAppPackages = sharedPreferencesHelper.getFilteredAppPackages()
        Log.i(TAG, "服务设置已加载/更新: ${keywords.size}个关键词, 铃声: '$currentRingtoneValue', 关键词铃声映射: ${keywordRingtoneMap.size}个, 默认循环次数: $defaultLoopCount, 应用过滤启用: $filterAppsEnabled, 过滤列表大小: ${filteredAppPackages.size}")
        VigilLogger.i(applicationContext, TAG, "设置已加载: ${keywords.size}个关键词, 过滤启用=$filterAppsEnabled (${filteredAppPackages.size}个应用)")
        // 添加详细日志以便调试
        if (filterAppsEnabled && filteredAppPackages.isNotEmpty()) {
            Log.d(TAG, "应用过滤已启用，包含的应用包名: ${filteredAppPackages.joinToString()}")
            VigilLogger.i(applicationContext, TAG, "过滤列表: ${filteredAppPackages.joinToString()}")
        } else if (filterAppsEnabled) {
            Log.w(TAG, "应用过滤已启用，但过滤列表为空，将监听所有应用")
        } else {
            Log.d(TAG, "应用过滤未启用，将监听所有应用")
        }
        
        // 设置更新后刷新前台服务通知
        updateForegroundNotification()
    }
    
    private fun updateForegroundNotification() {
        try {
            // 使用简单明确的方式更新通知
            val notification = createForegroundServiceNotification()
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            Log.d(TAG, "已更新前台服务通知")
        } catch (e: Exception) {
            Log.e(TAG, "更新前台服务通知时出错", e)
        }
    }

    /**
     * 播放报警铃声。loopLimit=0 表示直到用户确认（isLooping 无限循环）；
     * 有限档位下不用 isLooping，改用 OnCompletion 手动重启计数，到数自动结束。
     * preferredValue 为铃声值（content:// URI 或铃声库文件路径），null/文件缺失时回落默认铃声，再回落系统默认闹钟铃声。
     */
    private fun playRingtoneLooping(preferredValue: String?, loopLimit: Int = 0, alreadyPlayed: Int = 0) {
        // 防止并发触发：正在准备或播放中则忽略新请求
        if (playerState == PlayerState.PREPARING || playerState == PlayerState.PLAYING) {
            Log.d(TAG, "playRingtoneLooping: 已在播放中 ($playerState)，忽略重复请求。")
            return
        }
        stopRingtone()
        val dataSource = RingtoneLibrary.resolve(applicationContext, preferredValue)
            // preferred 与默认铃声同值时跳过重复解析，避免回落警告打两遍
            ?: (if (currentRingtoneValue == preferredValue) null
                else RingtoneLibrary.resolve(applicationContext, currentRingtoneValue))
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { RingtoneLibrary.DataSource.ContentUri(it) }
        if (dataSource == null) {
            Log.e(TAG, "无法获取铃声数据源！")
            releaseWakeLock()
            return
        }
        val dataSourceDesc = when (dataSource) {
            is RingtoneLibrary.DataSource.ContentUri -> dataSource.uri.toString()
            is RingtoneLibrary.DataSource.LocalFile -> dataSource.file.absolutePath
        }
        playerState = PlayerState.PREPARING
        var playedCount = alreadyPlayed
        try {
            mediaPlayer = MediaPlayer().apply {
                when (dataSource) {
                    is RingtoneLibrary.DataSource.ContentUri -> setDataSource(applicationContext, dataSource.uri)
                    is RingtoneLibrary.DataSource.LocalFile -> setDataSource(dataSource.file.absolutePath)
                }
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = (loopLimit == 0)
                prepareAsync()
                setOnPreparedListener { mp ->
                    if (playerState == PlayerState.PREPARING) {
                        playerState = PlayerState.PLAYING
                        Log.i(TAG, "MediaPlayer 已准备好，开始播放。")
                        VigilLogger.i(applicationContext, TAG, "报警铃声开始播放 (source=$dataSourceDesc, loopLimit=${if (loopLimit == 0) "无限" else loopLimit}, 已播=$alreadyPlayed)")
                        try {
                            mp.start()
                        } catch (startEx: IllegalStateException) {
                            Log.e(TAG, "MediaPlayer 调用 start() 时出错", startEx)
                            playerState = PlayerState.STOPPED
                            stopRingtoneAndLock()
                        }
                    } else {
                        // 在 prepare 期间已被取消，释放此孤立实例
                        Log.d(TAG, "onPrepared: 播放已取消 ($playerState)，释放孤立 MediaPlayer。")
                        mp.release()
                    }
                }
                if (loopLimit > 0) {
                    setOnCompletionListener { mp ->
                        if (playerState != PlayerState.PLAYING) {
                            Log.d(TAG, "onCompletion: 播放已停止 ($playerState)，忽略。")
                            return@setOnCompletionListener
                        }
                        playedCount++
                        sharedPreferencesHelper.updatePendingAlertPlayedLoops(playedCount)
                        // 续期唤醒锁：高档位（如 100 次 × 长铃声）总时长会超过单次 wakelock 超时
                        releaseWakeLock()
                        acquireWakeLock()
                        if (playedCount >= loopLimit) {
                            Log.i(TAG, "循环次数已用完 ($playedCount/$loopLimit)，自动结束报警。")
                            autoEndAlert(activeAlertKeyword)
                        } else {
                            Log.d(TAG, "循环续播 ($playedCount/$loopLimit)")
                            try {
                                mp.seekTo(0)
                                mp.start()
                            } catch (e: IllegalStateException) {
                                Log.e(TAG, "循环续播失败", e)
                                playerState = PlayerState.STOPPED
                                stopRingtoneAndLock()
                            }
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer 播放错误: what=$what, extra=$extra, source: $dataSourceDesc")
                    VigilLogger.e(applicationContext, TAG, "MediaPlayer 播放错误: what=$what, extra=$extra, source: $dataSourceDesc")
                    playerState = PlayerState.STOPPED
                    stopRingtoneAndLock()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置 MediaPlayer 数据源或准备时出错", e)
            playerState = PlayerState.STOPPED
            stopRingtoneAndLock()
        }
    }

    /** 循环次数用完自动结束：写记录、清 pending、停铃放锁、通知 UI 关弹窗。 */
    private fun autoEndAlert(keyword: String?) {
        VigilLogger.i(applicationContext, TAG, "报警自动结束 (keyword=$keyword)：循环次数用完")
        recordAlertEnd(AlertEndType.AUTO)
        sharedPreferencesHelper.clearPendingAlert()
        stopRingtoneAndLock()
        keyword?.let { kw ->
            serviceScope.launch { VigilEventBus.alertAutoEnded.emit(kw) }
        }
        activeAlertKeyword = null
        activeAlertSourceApp = null
    }

    /** 写一条报警历史记录；keyword/sourceApp 缺失时从持久化 pending 兜底。 */
    private fun recordAlertEnd(endType: AlertEndType) {
        val pending = sharedPreferencesHelper.getPendingAlert()
        val keyword = activeAlertKeyword ?: pending?.keyword ?: return
        val sourceApp = activeAlertSourceApp ?: pending?.sourceApp
        val timestamp = pending?.timestamp ?: System.currentTimeMillis()
        sharedPreferencesHelper.appendAlertRecord(AlertRecord(keyword, sourceApp, timestamp, endType))
        VigilLogger.i(applicationContext, TAG, "报警记录已写入: $keyword ($endType, 来源=${sourceApp ?: "未知"})")
    }

    private fun stopRingtone() {
        playerState = PlayerState.STOPPED
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
                playerState = PlayerState.IDLE
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
            // 只创建前台服务通知渠道
            val foregroundChannelName = "监控服务状态"
            val foregroundChannelDesc = "Vigil服务运行状态通知"
            val foregroundChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID, 
                foregroundChannelName, 
                NotificationManager.IMPORTANCE_LOW // 使用低重要性避免干扰用户
            ).apply {
                description = foregroundChannelDesc
                setShowBadge(false) // 不显示角标
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(foregroundChannel)
            
            Log.d(TAG,"前台服务通知渠道已创建/更新")
        }
    }

    private fun createForegroundServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        // 使用真实的系统绑定状态（activeNotifications 伪检测无法区分"无通知"与"未绑定"）
        val isListenerConnected = listenerActuallyConnected

        // 获取服务状态
        val serviceEnabled = SharedPreferencesHelper.isServiceEnabledByUser(applicationContext)
        
        // 简化通知标题
        val notificationTitle = if (isListenerConnected && serviceEnabled) {
            "Vigil监听中"
        } else if (isListenerConnected && !serviceEnabled) {
            "Vigil已就绪(已关闭提醒)"
        } else {
            "Vigil未连接"
        }
        
        // 简化关键词显示
        val keywordsText = if (keywords.isNotEmpty() && serviceEnabled) {
            if (keywords.size <= 2) {
                // 关键词少时显示全部
                "关键词:${keywords.joinToString(",")}"
            } else {
                // 关键词多时只显示数量
                "监听${keywords.size}个关键词"
            }
        } else if (!serviceEnabled) {
            "点击进入应用设置"
        } else {
            "请设置关键词"
        }
        
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(keywordsText)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // 设置为常驻通知
            .setSilent(true)   // 不发出声音
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendHeartbeat() {
        // 携带发射时刻时间戳：replay=1 下冷启动的收集方据此计算心跳真实年龄
        serviceScope.launch { VigilEventBus.heartbeat.emit(SystemClock.elapsedRealtime()) }
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
        serviceScope.launch { VigilEventBus.serviceStatus.emit(isConnected) }
        Log.d(TAG, "发送服务连接状态更新: $isConnected")
    }
}
