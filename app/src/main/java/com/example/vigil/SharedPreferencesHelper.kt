// src/main/java/com/example/vigil/SharedPreferencesHelper.kt
package com.example.vigil

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** 报警结束方式：用户手动确认 / 循环次数用完自动结束 */
enum class AlertEndType { MANUAL, AUTO }

/** 一条报警历史记录 */
data class AlertRecord(
    val keyword: String,
    val sourceApp: String?,
    val timestamp: Long,
    val endType: AlertEndType
)

/** 未确认报警的持久化状态（进程被杀后恢复响铃用，铁律 3） */
data class PendingAlert(
    val keyword: String,
    val timestamp: Long,
    val ringtoneUri: String?,
    val loopLimit: Int,      // 1..10，达到次数后自动结束
    val playedLoops: Int,    // 已播放次数（进程重建后的续播依据）
    val sourceApp: String?
)

data class AlertQueueItem(
    val id: String,
    val keyword: String,
    val sourcePackage: String?,
    val sourceApp: String?,
    val firstTriggeredAt: Long,
    val lastTriggeredAt: Long,
    val ringtoneUri: String?,
    val loopLimit: Int,
    val playedLoops: Int,
    val occurrenceCount: Int
)

enum class AlertEnqueueStatus {
    STARTED, QUEUED, AGGREGATED, COOLDOWN_IGNORED, QUEUE_FULL, PERSIST_FAILED
}

data class AlertEnqueueResult(
    val status: AlertEnqueueStatus,
    val active: AlertQueueItem?,
    val affected: AlertQueueItem?,
    val queueSize: Int
)

data class AlertQueueTransition(
    val success: Boolean,
    val finished: AlertQueueItem?,
    val next: AlertQueueItem?,
    val remaining: Int
)

/**
 * SharedPreferencesHelper 用于管理应用的持久化存储。
 *
 * @property context 上下文环境，用于访问 SharedPreferences。
 */
class SharedPreferencesHelper(context: Context) {

    private val alertQueueLock = Any()

    // 将 prefs 的访问修饰符改为 internal
    internal val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "vigil_prefs"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        // 应用过滤相关键
        private const val KEY_FILTER_APPS_ENABLED = "filter_apps_enabled"
        private const val KEY_FILTERED_APP_PACKAGES = "filtered_app_packages"

        // 新增: 首次使用和捐赠提示相关键
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_HAS_SHOWN_DONATE_DIALOG = "has_shown_donate_dialog"

        // 未确认报警（进程被杀后用于恢复响铃与弹窗）
        private const val KEY_PENDING_ALERT_KEYWORD = "pending_alert_keyword"
        private const val KEY_PENDING_ALERT_TIMESTAMP = "pending_alert_timestamp"
        private const val KEY_PENDING_ALERT_RINGTONE_URI = "pending_alert_ringtone_uri"
        private const val KEY_PENDING_ALERT_LOOP_LIMIT = "pending_alert_loop_limit"
        private const val KEY_PENDING_ALERT_PLAYED_LOOPS = "pending_alert_played_loops"
        private const val KEY_PENDING_ALERT_SOURCE_APP = "pending_alert_source_app"
        private const val KEY_ALERT_QUEUE = "alert_queue"
        private const val ALERT_QUEUE_VERSION = 1
        private const val KEY_KEYWORD_REPEAT_INTERVAL_MS = "keyword_repeat_interval_ms"
        private const val KEY_LAST_KEYWORD_TRIGGERS = "last_keyword_triggers"
        const val MAX_ALERT_QUEUE_SIZE = 20
        const val DEFAULT_KEYWORD_REPEAT_INTERVAL_MS = 60_000L
        private const val MAX_KEYWORD_REPEAT_INTERVAL_MS = 24 * 60 * 60 * 1000L

        // 关键词级铃声与循环次数映射（JSON：{keyword: 值}）
        private const val KEY_KEYWORD_RINGTONES = "keyword_ringtones"
        private const val KEY_KEYWORD_LOOP_COUNTS = "keyword_loop_counts"
        // 全局默认循环次数：1..10；旧版 0（无限）迁移为 10
        private const val KEY_DEFAULT_LOOP_COUNT = "default_loop_count"

        const val MIN_LOOP_COUNT = 1
        const val MAX_LOOP_COUNT = 10
        const val DEFAULT_LOOP_COUNT = MAX_LOOP_COUNT

        internal fun normalizeLoopCount(count: Int): Int = when (count) {
            0 -> DEFAULT_LOOP_COUNT
            else -> count.coerceIn(MIN_LOOP_COUNT, MAX_LOOP_COUNT)
        }

        // 报警历史记录（JSON array，新→旧，上限 MAX_ALERT_HISTORY 条）
        private const val KEY_ALERT_HISTORY = "alert_history"
        private const val MAX_ALERT_HISTORY = 100

        // 铃声库元数据（JSON：{fileName: displayName}），文件本体在 filesDir/ringtones/
        private const val KEY_RINGTONE_LIBRARY = "ringtone_library"

        // 通知监听服务与系统的真实绑定状态（心跳只证明进程活着，此标志才代表系统正在投递通知）
        private const val KEY_LISTENER_CONNECTED = "listener_connected"
        private const val KEY_LISTENER_RECOVERY_FAILED = "listener_recovery_failed"

        // 上滑手势入口提示（设置 Sheet 被打开过一次即标记，不再显示主屏提示文案）
        private const val KEY_HAS_SHOWN_SWIPE_HINT = "has_shown_swipe_hint"

        // 锁定任务卡片引导行被用户「不再提示」关闭
        private const val KEY_LOCK_TASK_TIP_DISMISSED = "lock_task_tip_dismissed"

        // 自动更新：用户对某版本点了「稍后」后，冷启动不再重复提示该版本（手动检查仍可触发）
        private const val KEY_LAST_DISMISSED_UPDATE = "last_dismissed_update_version"

        // 调试专用（仅 debug 构建生效）：覆盖 GitHub API 基址，用于本地模拟更新返回，生产为空
        private const val KEY_DEBUG_UPDATE_API_BASE = "debug_update_api_base"

        fun isServiceEnabledByUser(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, false)
        }
    }

    fun saveKeywords(keywords: List<String>) {
        prefs.edit().putStringSet(KEY_KEYWORDS, keywords.toSet()).apply()
        Log.i("SharedPreferencesHelper", "关键词已保存: ${keywords.size}个")
    }

    fun getKeywords(): List<String> {
        return prefs.getStringSet(KEY_KEYWORDS, emptySet())?.toList() ?: emptyList()
    }

    /**
     * 迁移旧版逗号分隔字符串格式到 StringSet 格式。
     * 首次升级后调用一次，不影响已是 StringSet 格式的数据。
     */
    fun migrateKeywordsIfNeeded() {
        if (prefs.contains(KEY_KEYWORDS)) {
            try {
                // 如果是旧的 String 格式，读取并转换
                val old = prefs.getString(KEY_KEYWORDS, "") ?: ""
                val migrated = old.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                prefs.edit().remove(KEY_KEYWORDS).putStringSet(KEY_KEYWORDS, migrated).apply()
                Log.i("SharedPreferencesHelper", "关键词格式迁移完成: ${migrated.size}个")
            } catch (e: ClassCastException) {
                // 已经是 StringSet 格式，无需迁移
                Log.d("SharedPreferencesHelper", "关键词已是 StringSet 格式，跳过迁移")
            }
        }
    }

    fun saveRingtoneUri(ringtoneUri: Uri?) {
        prefs.edit().putString(KEY_RINGTONE_URI, ringtoneUri?.toString()).apply()
        Log.i("SharedPreferencesHelper", "铃声 URI 已保存: $ringtoneUri")
    }

    /**
     * 保存默认铃声值：content:// 系统铃声 URI 或铃声库本地文件路径；null = 系统默认闹钟铃声。
     */
    fun saveRingtoneValue(value: String?) {
        prefs.edit().putString(KEY_RINGTONE_URI, value).apply()
        Log.i("SharedPreferencesHelper", "默认铃声已保存: $value")
    }

    /** 默认铃声值（URI 字符串或文件路径），未设置返回 null。 */
    fun getRingtoneValue(): String? {
        return prefs.getString(KEY_RINGTONE_URI, null)
    }

    fun getRingtoneUri(): Uri? {
        val uriString = prefs.getString(KEY_RINGTONE_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    fun saveServiceEnabledState(enabled: Boolean) {
        val oldValue = getServiceEnabledState()
        if (oldValue != enabled) {
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
            Log.i("SharedPreferencesHelper", "服务启用状态已改变: $oldValue -> $enabled")
        } else {
            Log.d("SharedPreferencesHelper", "服务启用状态未变化: $enabled")
        }
    }

    fun getServiceEnabledState(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    /**
     * 持久化通知监听服务与系统的绑定状态。
     * 心跳只能证明进程活着，此标志才代表系统正在向服务投递通知；
     * 供 UI 冷启动时作为状态兜底（EventBus 无 replay）。
     */
    fun saveListenerConnectedState(connected: Boolean) {
        if (getListenerConnectedState() != connected) {
            prefs.edit().putBoolean(KEY_LISTENER_CONNECTED, connected).apply()
            Log.i("SharedPreferencesHelper", "监听绑定状态已保存: $connected")
        }
    }

    fun getListenerConnectedState(): Boolean {
        return prefs.getBoolean(KEY_LISTENER_CONNECTED, false)
    }

    /**
     * 监听自动重连失败标记：看门狗完整重连序列（stopService + 组件强刷）也无效时置 true，
     * UI 据此展示"重新授权通知使用权"引导（系统级撤销+重新授权是唯一兜底手段）。
     * @return 值是否发生变化（发生变化时调用方决定记日志/发事件）
     */
    fun saveListenerRecoveryFailed(failed: Boolean): Boolean {
        if (getListenerRecoveryFailed() != failed) {
            prefs.edit().putBoolean(KEY_LISTENER_RECOVERY_FAILED, failed).apply()
            Log.i("SharedPreferencesHelper", "监听恢复失败标记已保存: $failed")
            return true
        }
        return false
    }

    fun getListenerRecoveryFailed(): Boolean {
        return prefs.getBoolean(KEY_LISTENER_RECOVERY_FAILED, false)
    }

    /**
     * 是否已展示过上滑手势入口提示。设置 Sheet 被打开过一次（任意入口）即视为已发现。
     */
    fun hasShownSwipeHint(): Boolean {
        return prefs.getBoolean(KEY_HAS_SHOWN_SWIPE_HINT, false)
    }

    fun markSwipeHintShown() {
        prefs.edit().putBoolean(KEY_HAS_SHOWN_SWIPE_HINT, true).apply()
        Log.i("SharedPreferencesHelper", "已标记上滑手势提示完成")
    }

    /**
     * 锁定任务卡片引导行是否已被用户「不再提示」关闭。
     */
    fun isLockTaskTipDismissed(): Boolean {
        return prefs.getBoolean(KEY_LOCK_TASK_TIP_DISMISSED, false)
    }

    fun markLockTaskTipDismissed() {
        prefs.edit().putBoolean(KEY_LOCK_TASK_TIP_DISMISSED, true).apply()
        Log.i("SharedPreferencesHelper", "锁定任务卡片引导已标记不再提示")
    }

    // --- 应用过滤相关方法 ---

    /**
     * 保存"仅监听指定应用"功能的启用状态。
     * @param enabled true 表示启用过滤，false 表示监听所有应用。
     */
    fun saveFilterAppsEnabledState(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_APPS_ENABLED, enabled).apply()
        Log.i("SharedPreferencesHelper", "应用过滤启用状态已保存: $enabled")
    }

    /**
     * 获取"仅监听指定应用"功能的启用状态。
     * @return true 如果用户启用了应用过滤，默认为 false。
     */
    fun getFilterAppsEnabledState(): Boolean {
        // 默认不启用过滤，即监听所有应用
        return prefs.getBoolean(KEY_FILTER_APPS_ENABLED, false)
    }

    /**
     * 保存用户选择的被监听的应用包名列表。
     * 如果为 null 或空集合，表示不进行过滤（或根据 filter_apps_enabled 的状态决定）。
     * @param appPackages 应用包名集合。
     */
    fun saveFilteredAppPackages(appPackages: Set<String>?) {
        // SharedPreferences 不直接支持 Set<String> 的 null 值，保存 emptySet() 表示没有选择特定应用
        prefs.edit().putStringSet(KEY_FILTERED_APP_PACKAGES, appPackages ?: emptySet()).apply()
        Log.i("SharedPreferencesHelper", "被过滤的应用包名列表已保存: ${appPackages?.size ?: 0}个")
    }

    /**
     * 获取用户选择的被监听的应用包名列表。
     * @return 返回应用包名集合。如果未保存，则返回空集合。
     */
    fun getFilteredAppPackages(): Set<String> {
        return prefs.getStringSet(KEY_FILTERED_APP_PACKAGES, emptySet()) ?: emptySet()
    }

    // --- 首次启动和捐赠提示相关方法 ---

    /**
     * 检查是否是首次启动应用
     * @return true 如果是首次启动，否则返回 false
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    /**
     * 标记应用已经首次启动
     */
    fun markFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
        Log.i("SharedPreferencesHelper", "已标记首次启动完成")
    }

    /**
     * 检查是否已经显示过捐赠提示对话框
     * @return true 如果已经显示过，否则返回 false
     */
    fun hasShownDonateDialog(): Boolean {
        return prefs.getBoolean(KEY_HAS_SHOWN_DONATE_DIALOG, false)
    }

    /**
     * 标记已经显示过捐赠提示对话框
     */
    fun markDonateDialogShown() {
        prefs.edit().putBoolean(KEY_HAS_SHOWN_DONATE_DIALOG, true).apply()
        Log.i("SharedPreferencesHelper", "已标记捐赠提示对话框已显示")
    }

    // --- 持久化报警 FIFO（队首即当前报警） ---

    fun getKeywordRepeatIntervalMs(): Long =
        prefs.getLong(KEY_KEYWORD_REPEAT_INTERVAL_MS, DEFAULT_KEYWORD_REPEAT_INTERVAL_MS)
            .coerceIn(0L, MAX_KEYWORD_REPEAT_INTERVAL_MS)

    fun saveKeywordRepeatIntervalMs(intervalMs: Long) {
        prefs.edit().putLong(
            KEY_KEYWORD_REPEAT_INTERVAL_MS,
            intervalMs.coerceIn(0L, MAX_KEYWORD_REPEAT_INTERVAL_MS)
        ).apply()
    }

    fun getKeywordCooldownSeconds(): Int = (getKeywordRepeatIntervalMs() / 1_000L).toInt()

    fun saveKeywordCooldownSeconds(seconds: Int) {
        saveKeywordRepeatIntervalMs(seconds.coerceAtLeast(0) * 1_000L)
    }

    fun enqueueOrAggregateAlert(
        keyword: String,
        sourcePackage: String?,
        sourceApp: String?,
        ringtoneUri: String?,
        loopLimit: Int,
        now: Long = System.currentTimeMillis(),
        cooldownMs: Long = getKeywordRepeatIntervalMs()
    ): AlertEnqueueResult = synchronized(alertQueueLock) {
        migratePendingAlertToQueueLocked()
        val queue = readAlertQueueLocked().toMutableList()
        val identity = alertIdentity(sourcePackage, keyword)
        val normalizedCooldown = cooldownMs.coerceIn(0L, MAX_KEYWORD_REPEAT_INTERVAL_MS)
        val existingIndex = if (normalizedCooldown == 0L) -1 else queue.indexOfLast {
            alertIdentity(it.sourcePackage, it.keyword) == identity &&
                now - it.lastTriggeredAt in 0..normalizedCooldown
        }
        if (existingIndex >= 0) {
            val aggregated = queue[existingIndex].copy(
                lastTriggeredAt = now,
                occurrenceCount = queue[existingIndex].occurrenceCount + 1
            )
            queue[existingIndex] = aggregated
            val recent = readLastTriggerMapLocked().apply { put(identity, now) }
            val committed = prefs.edit()
                .putString(KEY_ALERT_QUEUE, encodeAlertQueue(queue))
                .putString(KEY_LAST_KEYWORD_TRIGGERS, JSONObject(recent as Map<*, *>).toString())
                .commit()
            if (!committed) Log.e("SharedPreferencesHelper", "报警聚合持久化失败: $keyword")
            if (!committed) {
                return@synchronized AlertEnqueueResult(
                    AlertEnqueueStatus.PERSIST_FAILED,
                    readAlertQueueLocked().firstOrNull(), null, readAlertQueueLocked().size
                )
            }
            return@synchronized AlertEnqueueResult(
                AlertEnqueueStatus.AGGREGATED, queue.firstOrNull(), aggregated, queue.size
            )
        }
        val lastTrigger = readLastTriggerMapLocked()[identity]
        if (normalizedCooldown > 0L && lastTrigger != null && now - lastTrigger in 0..normalizedCooldown) {
            return@synchronized AlertEnqueueResult(
                AlertEnqueueStatus.COOLDOWN_IGNORED, queue.firstOrNull(), null, queue.size
            )
        }
        if (queue.size >= MAX_ALERT_QUEUE_SIZE) {
            return@synchronized AlertEnqueueResult(
                AlertEnqueueStatus.QUEUE_FULL, queue.firstOrNull(), null, queue.size
            )
        }
        val item = AlertQueueItem(
            id = UUID.randomUUID().toString(), keyword = keyword,
            sourcePackage = sourcePackage, sourceApp = sourceApp,
            firstTriggeredAt = now, lastTriggeredAt = now,
            ringtoneUri = ringtoneUri, loopLimit = normalizeLoopCount(loopLimit),
            playedLoops = 0, occurrenceCount = 1
        )
        queue += item
        val recent = readLastTriggerMapLocked().apply { put(identity, now) }
        val committed = prefs.edit()
            .putString(KEY_ALERT_QUEUE, encodeAlertQueue(queue))
            .putString(KEY_LAST_KEYWORD_TRIGGERS, JSONObject(recent as Map<*, *>).toString())
            .commit()
        if (!committed) Log.e("SharedPreferencesHelper", "报警入队持久化失败: $keyword")
        if (!committed) {
            val persisted = readAlertQueueLocked()
            return@synchronized AlertEnqueueResult(
                AlertEnqueueStatus.PERSIST_FAILED, persisted.firstOrNull(), null, persisted.size
            )
        }
        AlertEnqueueResult(
            if (queue.size == 1) AlertEnqueueStatus.STARTED else AlertEnqueueStatus.QUEUED,
            queue.firstOrNull(), item, queue.size
        )
    }

    fun getAlertQueue(): List<AlertQueueItem> = synchronized(alertQueueLock) {
        migratePendingAlertToQueueLocked()
        readAlertQueueLocked()
    }

    fun getActiveAlert(): AlertQueueItem? = getAlertQueue().firstOrNull()

    fun updateActiveAlertPlayedLoops(expectedId: String, playedLoops: Int): Boolean =
        synchronized(alertQueueLock) {
            val queue = readAlertQueueLocked().toMutableList()
            val active = queue.firstOrNull() ?: return@synchronized false
            if (active.id != expectedId) return@synchronized false
            queue[0] = active.copy(playedLoops = playedLoops.coerceAtLeast(0))
            prefs.edit().putString(KEY_ALERT_QUEUE, encodeAlertQueue(queue)).commit()
        }

    /** 原子写历史并移除队首；expectedId 防止迟到回调误结束下一条。 */
    fun finishActiveAlert(expectedId: String, endType: AlertEndType): AlertQueueTransition =
        synchronized(alertQueueLock) {
            val queue = readAlertQueueLocked().toMutableList()
            val active = queue.firstOrNull()
                ?: return@synchronized AlertQueueTransition(false, null, null, 0)
            if (active.id != expectedId) {
                return@synchronized AlertQueueTransition(false, null, active, queue.size)
            }
            queue.removeAt(0)
            val history = prependHistoryRecord(
                readAlertHistoryArray(),
                AlertRecord(active.keyword, active.sourceApp, active.firstTriggeredAt, endType)
            )
            val committed = prefs.edit()
                .putString(KEY_ALERT_QUEUE, encodeAlertQueue(queue))
                .putString(KEY_ALERT_HISTORY, history.toString())
                .commit()
            AlertQueueTransition(
                success = committed,
                finished = if (committed) active else null,
                next = if (committed) queue.firstOrNull() else active,
                remaining = if (committed) queue.size else queue.size + 1
            )
        }

    fun migratePendingAlertToQueueIfNeeded(): Boolean = synchronized(alertQueueLock) {
        migratePendingAlertToQueueLocked()
    }

    /**
     * 保存一条未确认的报警。触发报警时调用。
     * ringtoneUri/loopLimit/sourceApp 一并持久化：进程重建恢复时用同一铃声续播剩余次数（铁律 3）。
     */
    fun savePendingAlert(keyword: String, ringtoneUri: String?, loopLimit: Int, sourceApp: String?) {
        enqueueOrAggregateAlert(keyword, null, sourceApp, ringtoneUri, loopLimit, cooldownMs = 0L)
    }

    /**
     * 读取未确认报警；无则返回 null。
     */
    fun getPendingAlert(): PendingAlert? {
        val active = getActiveAlert() ?: return null
        return PendingAlert(
            keyword = active.keyword, timestamp = active.firstTriggeredAt,
            ringtoneUri = active.ringtoneUri, loopLimit = active.loopLimit,
            playedLoops = active.playedLoops, sourceApp = active.sourceApp
        )
    }

    /** 有限档位下每播完一次更新已播放次数，进程重建后续播剩余次数。 */
    fun updatePendingAlertPlayedLoops(playedLoops: Int) {
        getActiveAlert()?.let { updateActiveAlertPlayedLoops(it.id, playedLoops) }
    }

    /**
     * 清除未确认报警。用户确认或循环次数用完自动结束后调用。
     */
    fun clearPendingAlert() {
        synchronized(alertQueueLock) {
            val queue = readAlertQueueLocked().drop(1)
            prefs.edit().putString(KEY_ALERT_QUEUE, encodeAlertQueue(queue)).commit()
        }
        Log.i("SharedPreferencesHelper", "未确认报警已清除。")
    }

    private fun migratePendingAlertToQueueLocked(): Boolean {
        val legacyKeyword = prefs.getString(KEY_PENDING_ALERT_KEYWORD, null)
        val queueAlreadyExists = prefs.contains(KEY_ALERT_QUEUE)
        if (legacyKeyword == null && queueAlreadyExists) return false
        if (legacyKeyword == null) return false

        val editor = prefs.edit()
        if (!queueAlreadyExists) {
            val timestamp = prefs.getLong(KEY_PENDING_ALERT_TIMESTAMP, System.currentTimeMillis())
            val item = AlertQueueItem(
                id = UUID.randomUUID().toString(),
                keyword = legacyKeyword,
                sourcePackage = null,
                sourceApp = prefs.getString(KEY_PENDING_ALERT_SOURCE_APP, null),
                firstTriggeredAt = timestamp,
                lastTriggeredAt = timestamp,
                ringtoneUri = prefs.getString(KEY_PENDING_ALERT_RINGTONE_URI, null),
                loopLimit = normalizeLoopCount(
                    prefs.getInt(KEY_PENDING_ALERT_LOOP_LIMIT, DEFAULT_LOOP_COUNT)
                ),
                playedLoops = prefs.getInt(KEY_PENDING_ALERT_PLAYED_LOOPS, 0).coerceAtLeast(0),
                occurrenceCount = 1
            )
            editor.putString(KEY_ALERT_QUEUE, encodeAlertQueue(listOf(item)))
        }
        removeLegacyPendingKeys(editor)
        val committed = editor.commit()
        if (committed) {
            Log.i("SharedPreferencesHelper", "旧版未确认报警已迁移到 FIFO: $legacyKeyword")
        } else {
            Log.e("SharedPreferencesHelper", "旧版未确认报警迁移失败: $legacyKeyword")
        }
        return committed
    }

    private fun removeLegacyPendingKeys(editor: SharedPreferences.Editor) {
        editor.remove(KEY_PENDING_ALERT_KEYWORD)
            .remove(KEY_PENDING_ALERT_TIMESTAMP)
            .remove(KEY_PENDING_ALERT_RINGTONE_URI)
            .remove(KEY_PENDING_ALERT_LOOP_LIMIT)
            .remove(KEY_PENDING_ALERT_PLAYED_LOOPS)
            .remove(KEY_PENDING_ALERT_SOURCE_APP)
    }

    private fun readAlertQueueLocked(): List<AlertQueueItem> {
        return try {
            val root = JSONObject(prefs.getString(KEY_ALERT_QUEUE, null) ?: return emptyList())
            val items = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until minOf(items.length(), MAX_ALERT_QUEUE_SIZE)) {
                    val obj = items.getJSONObject(i)
                    val keyword = obj.getString("keyword")
                    add(
                        AlertQueueItem(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            keyword = keyword,
                            sourcePackage = obj.nullableString("sourcePackage"),
                            sourceApp = obj.nullableString("sourceApp"),
                            firstTriggeredAt = obj.optLong("firstTriggeredAt", 0L),
                            lastTriggeredAt = obj.optLong(
                                "lastTriggeredAt", obj.optLong("firstTriggeredAt", 0L)
                            ),
                            ringtoneUri = obj.nullableString("ringtoneUri"),
                            loopLimit = normalizeLoopCount(
                                obj.optInt("loopLimit", DEFAULT_LOOP_COUNT)
                            ),
                            playedLoops = obj.optInt("playedLoops", 0).coerceAtLeast(0),
                            occurrenceCount = obj.optInt("occurrenceCount", 1).coerceAtLeast(1)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SharedPreferencesHelper", "读取报警队列失败，按空队列处理", e)
            emptyList()
        }
    }

    private fun encodeAlertQueue(queue: List<AlertQueueItem>): String {
        val items = JSONArray()
        queue.take(MAX_ALERT_QUEUE_SIZE).forEach { item ->
            items.put(JSONObject().apply {
                put("id", item.id)
                put("keyword", item.keyword)
                put("sourcePackage", item.sourcePackage ?: JSONObject.NULL)
                put("sourceApp", item.sourceApp ?: JSONObject.NULL)
                put("firstTriggeredAt", item.firstTriggeredAt)
                put("lastTriggeredAt", item.lastTriggeredAt)
                put("ringtoneUri", item.ringtoneUri ?: JSONObject.NULL)
                put("loopLimit", normalizeLoopCount(item.loopLimit))
                put("playedLoops", item.playedLoops.coerceAtLeast(0))
                put("occurrenceCount", item.occurrenceCount.coerceAtLeast(1))
            })
        }
        return JSONObject().put("version", ALERT_QUEUE_VERSION).put("items", items).toString()
    }

    private fun readLastTriggerMapLocked(): MutableMap<String, Long> {
        return try {
            val obj = JSONObject(prefs.getString(KEY_LAST_KEYWORD_TRIGGERS, null) ?: "{}")
            obj.keys().asSequence().associateWithTo(mutableMapOf()) { obj.getLong(it) }
        } catch (e: Exception) {
            Log.e("SharedPreferencesHelper", "读取关键词最近触发时间失败", e)
            mutableMapOf()
        }
    }

    private fun alertIdentity(sourcePackage: String?, keyword: String): String =
        "${sourcePackage.orEmpty().lowercase(Locale.ROOT)}\u0000${keyword.trim().lowercase(Locale.ROOT)}"

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    // --- 关键词级铃声与循环次数 ---

    /** 保存关键词 → 铃声值映射（content:// URI 或铃声库文件路径）；value 为 null 表示移除映射（回落默认铃声）。 */
    fun saveKeywordRingtone(keyword: String, value: String?) {
        val map = getKeywordRingtoneMap().toMutableMap()
        if (value == null) map.remove(keyword) else map[keyword] = value
        prefs.edit().putString(KEY_KEYWORD_RINGTONES, JSONObject(map as Map<*, *>).toString()).apply()
        Log.i("SharedPreferencesHelper", "关键词铃声映射已保存: $keyword -> $value")
    }

    /** 关键词绑定的铃声值；未绑定返回 null（调用方回落默认铃声）。 */
    fun getKeywordRingtoneValue(keyword: String): String? {
        return getKeywordRingtoneMap()[keyword]
    }

    fun getKeywordRingtoneMap(): Map<String, String> {
        return readJsonStringMap(KEY_KEYWORD_RINGTONES)
    }

    /** 保存关键词 → 循环次数覆盖；count 为 null 表示移除覆盖（跟随全局默认）。 */
    fun saveKeywordLoopCount(keyword: String, count: Int?) {
        val obj = JSONObject(prefs.getString(KEY_KEYWORD_LOOP_COUNTS, null) ?: "{}")
        if (count == null) obj.remove(keyword) else obj.put(keyword, normalizeLoopCount(count))
        prefs.edit().putString(KEY_KEYWORD_LOOP_COUNTS, obj.toString()).apply()
        Log.i("SharedPreferencesHelper", "关键词循环次数覆盖已保存: $keyword -> $count")
    }

    /** 关键词的循环次数覆盖；未覆盖返回 null（调用方用全局默认）。 */
    fun getKeywordLoopCount(keyword: String): Int? {
        return getKeywordLoopCountMap()[keyword]
    }

    fun getKeywordLoopCountMap(): Map<String, Int> {
        val obj = JSONObject(prefs.getString(KEY_KEYWORD_LOOP_COUNTS, null) ?: "{}")
        var changed = false
        val result = obj.keys().asSequence().associateWith { keyword ->
            val stored = obj.getInt(keyword)
            normalizeLoopCount(stored).also { normalized ->
                if (stored != normalized) {
                    obj.put(keyword, normalized)
                    changed = true
                }
            }
        }
        if (changed) {
            prefs.edit().putString(KEY_KEYWORD_LOOP_COUNTS, obj.toString()).apply()
        }
        return result
    }

    /** 移除某关键词的全部个性化配置（删除关键词时调用）。 */
    fun clearKeywordConfig(keyword: String) {
        saveKeywordRingtone(keyword, null)
        saveKeywordLoopCount(keyword, null)
    }

    /** 全局默认循环次数：1..10；范围外旧值读取/保存时自动迁移。 */
    fun saveDefaultLoopCount(count: Int) {
        val normalized = normalizeLoopCount(count)
        prefs.edit().putInt(KEY_DEFAULT_LOOP_COUNT, normalized).apply()
        Log.i("SharedPreferencesHelper", "默认循环次数已保存: $normalized")
    }

    fun getDefaultLoopCount(): Int {
        val stored = prefs.getInt(KEY_DEFAULT_LOOP_COUNT, DEFAULT_LOOP_COUNT)
        val normalized = normalizeLoopCount(stored)
        if (stored != normalized) {
            prefs.edit().putInt(KEY_DEFAULT_LOOP_COUNT, normalized).apply()
        }
        return normalized
    }

    private fun readJsonStringMap(key: String): Map<String, String> {
        return try {
            val obj = JSONObject(prefs.getString(key, null) ?: "{}")
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            Log.e("SharedPreferencesHelper", "读取 JSON 映射失败: $key", e)
            emptyMap()
        }
    }

    // --- 报警历史记录 ---

    /** 追加一条报警记录（新记录置顶，超出上限裁掉最旧）。 */
    fun appendAlertRecord(record: AlertRecord) {
        val arr = readAlertHistoryArray()
        val newArr = prependHistoryRecord(arr, record)
        prefs.edit().putString(KEY_ALERT_HISTORY, newArr.toString()).apply()
        Log.i("SharedPreferencesHelper", "报警记录已写入: ${record.keyword} (${record.endType})")
    }

    private fun prependHistoryRecord(arr: JSONArray, record: AlertRecord): JSONArray {
        val obj = JSONObject().apply {
            put("keyword", record.keyword)
            put("sourceApp", record.sourceApp ?: JSONObject.NULL)
            put("timestamp", record.timestamp)
            put("endType", record.endType.name)
        }
        return JSONArray().put(obj).also { result ->
            for (i in 0 until minOf(arr.length(), MAX_ALERT_HISTORY - 1)) {
                result.put(arr.get(i))
            }
        }
    }

    /** 读取报警历史，新→旧。 */
    fun getAlertHistory(): List<AlertRecord> {
        val arr = readAlertHistoryArray()
        val list = mutableListOf<AlertRecord>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                list.add(
                    AlertRecord(
                        keyword = obj.getString("keyword"),
                        sourceApp = if (obj.isNull("sourceApp")) null else obj.getString("sourceApp"),
                        timestamp = obj.getLong("timestamp"),
                        endType = runCatching { AlertEndType.valueOf(obj.getString("endType")) }
                            .getOrDefault(AlertEndType.MANUAL)
                    )
                )
            } catch (e: Exception) {
                Log.e("SharedPreferencesHelper", "解析报警记录失败 (index=$i)", e)
            }
        }
        return list
    }

    fun clearAlertHistory() {
        prefs.edit().remove(KEY_ALERT_HISTORY).apply()
        Log.i("SharedPreferencesHelper", "报警记录已清空。")
    }

    private fun readAlertHistoryArray(): JSONArray {
        return try {
            JSONArray(prefs.getString(KEY_ALERT_HISTORY, null) ?: "[]")
        } catch (e: Exception) {
            Log.e("SharedPreferencesHelper", "读取报警记录失败，按空处理", e)
            JSONArray()
        }
    }

    // --- 铃声库元数据（fileName → displayName；文件本体由 RingtoneLibrary 管理） ---

    /** 铃声库条目映射：fileName → displayName。 */
    fun getRingtoneLibraryMap(): Map<String, String> {
        return readJsonStringMap(KEY_RINGTONE_LIBRARY)
    }

    fun putRingtoneLibraryEntry(fileName: String, displayName: String) {
        val map = getRingtoneLibraryMap().toMutableMap()
        map[fileName] = displayName
        prefs.edit().putString(KEY_RINGTONE_LIBRARY, JSONObject(map as Map<*, *>).toString()).apply()
        Log.i("SharedPreferencesHelper", "铃声库条目已保存: $fileName -> $displayName")
    }

    fun removeRingtoneLibraryEntry(fileName: String) {
        val map = getRingtoneLibraryMap().toMutableMap()
        if (map.remove(fileName) != null) {
            prefs.edit().putString(KEY_RINGTONE_LIBRARY, JSONObject(map as Map<*, *>).toString()).apply()
            Log.i("SharedPreferencesHelper", "铃声库条目已移除: $fileName")
        }
    }

    // --- 自动更新 ---

    /** 用户上次对某版本点了「稍后」的版本号；冷启动检测到同版本更新时不再重复提示。 */
    fun getLastDismissedUpdateVersion(): String? {
        return prefs.getString(KEY_LAST_DISMISSED_UPDATE, null)
    }

    fun setLastDismissedUpdateVersion(version: String) {
        prefs.edit().putString(KEY_LAST_DISMISSED_UPDATE, version).apply()
        Log.i("SharedPreferencesHelper", "已记录稍后提示的更新版本: $version")
    }

    /** debug 专用：覆盖更新检查的 API 基址（仅 debug 构建读取，生产恒为空）。 */
    fun getDebugUpdateApiBase(): String? {
        return prefs.getString(KEY_DEBUG_UPDATE_API_BASE, null)
    }

    fun setDebugUpdateApiBase(base: String?) {
        if (base.isNullOrBlank()) {
            prefs.edit().remove(KEY_DEBUG_UPDATE_API_BASE).apply()
        } else {
            prefs.edit().putString(KEY_DEBUG_UPDATE_API_BASE, base).apply()
        }
        Log.i("SharedPreferencesHelper", "调试更新 API 基址已设置: $base")
    }
}
