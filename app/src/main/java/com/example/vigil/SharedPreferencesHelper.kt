// src/main/java/com/example/vigil/SharedPreferencesHelper.kt
package com.example.vigil

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

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
    val loopLimit: Int,      // 0 = 直到用户确认（无限循环）
    val playedLoops: Int,    // 已播放次数（有限档位下续播依据）
    val sourceApp: String?
)

/**
 * SharedPreferencesHelper 用于管理应用的持久化存储。
 *
 * @property context 上下文环境，用于访问 SharedPreferences。
 */
class SharedPreferencesHelper(context: Context) {

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

        // 关键词级铃声与循环次数映射（JSON：{keyword: 值}）
        private const val KEY_KEYWORD_RINGTONES = "keyword_ringtones"
        private const val KEY_KEYWORD_LOOP_COUNTS = "keyword_loop_counts"
        // 全局默认循环次数：0 = 直到用户确认（无限）
        private const val KEY_DEFAULT_LOOP_COUNT = "default_loop_count"

        // 报警历史记录（JSON array，新→旧，上限 MAX_ALERT_HISTORY 条）
        private const val KEY_ALERT_HISTORY = "alert_history"
        private const val MAX_ALERT_HISTORY = 100

        // 铃声库元数据（JSON：{fileName: displayName}），文件本体在 filesDir/ringtones/
        private const val KEY_RINGTONE_LIBRARY = "ringtone_library"

        // 通知监听服务与系统的真实绑定状态（心跳只证明进程活着，此标志才代表系统正在投递通知）
        private const val KEY_LISTENER_CONNECTED = "listener_connected"

        // 上滑手势入口提示（设置 Sheet 被打开过一次即标记，不再显示主屏提示文案）
        private const val KEY_HAS_SHOWN_SWIPE_HINT = "has_shown_swipe_hint"

        // 锁定任务卡片引导行被用户「不再提示」关闭
        private const val KEY_LOCK_TASK_TIP_DISMISSED = "lock_task_tip_dismissed"

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

    // --- 未确认报警持久化（进程被系统省电策略杀死后恢复报警用） ---

    /**
     * 保存一条未确认的报警。触发报警时调用。
     * ringtoneUri/loopLimit/sourceApp 一并持久化：进程重建恢复时用同一铃声续播剩余次数（铁律 3）。
     */
    fun savePendingAlert(keyword: String, ringtoneUri: String?, loopLimit: Int, sourceApp: String?) {
        prefs.edit()
            .putString(KEY_PENDING_ALERT_KEYWORD, keyword)
            .putLong(KEY_PENDING_ALERT_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_PENDING_ALERT_RINGTONE_URI, ringtoneUri)
            .putInt(KEY_PENDING_ALERT_LOOP_LIMIT, loopLimit)
            .putInt(KEY_PENDING_ALERT_PLAYED_LOOPS, 0)
            .putString(KEY_PENDING_ALERT_SOURCE_APP, sourceApp)
            .apply()
        Log.i("SharedPreferencesHelper", "未确认报警已持久化: $keyword (loopLimit=$loopLimit)")
    }

    /**
     * 读取未确认报警；无则返回 null。
     */
    fun getPendingAlert(): PendingAlert? {
        val keyword = prefs.getString(KEY_PENDING_ALERT_KEYWORD, null) ?: return null
        return PendingAlert(
            keyword = keyword,
            timestamp = prefs.getLong(KEY_PENDING_ALERT_TIMESTAMP, 0L),
            ringtoneUri = prefs.getString(KEY_PENDING_ALERT_RINGTONE_URI, null),
            loopLimit = prefs.getInt(KEY_PENDING_ALERT_LOOP_LIMIT, 0),
            playedLoops = prefs.getInt(KEY_PENDING_ALERT_PLAYED_LOOPS, 0),
            sourceApp = prefs.getString(KEY_PENDING_ALERT_SOURCE_APP, null)
        )
    }

    /** 有限档位下每播完一次更新已播放次数，进程重建后续播剩余次数。 */
    fun updatePendingAlertPlayedLoops(playedLoops: Int) {
        prefs.edit().putInt(KEY_PENDING_ALERT_PLAYED_LOOPS, playedLoops).apply()
    }

    /**
     * 清除未确认报警。用户确认或循环次数用完自动结束后调用。
     */
    fun clearPendingAlert() {
        prefs.edit()
            .remove(KEY_PENDING_ALERT_KEYWORD)
            .remove(KEY_PENDING_ALERT_TIMESTAMP)
            .remove(KEY_PENDING_ALERT_RINGTONE_URI)
            .remove(KEY_PENDING_ALERT_LOOP_LIMIT)
            .remove(KEY_PENDING_ALERT_PLAYED_LOOPS)
            .remove(KEY_PENDING_ALERT_SOURCE_APP)
            .apply()
        Log.i("SharedPreferencesHelper", "未确认报警已清除。")
    }

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
        if (count == null) obj.remove(keyword) else obj.put(keyword, count)
        prefs.edit().putString(KEY_KEYWORD_LOOP_COUNTS, obj.toString()).apply()
        Log.i("SharedPreferencesHelper", "关键词循环次数覆盖已保存: $keyword -> $count")
    }

    /** 关键词的循环次数覆盖；未覆盖返回 null（调用方用全局默认）。 */
    fun getKeywordLoopCount(keyword: String): Int? {
        val obj = JSONObject(prefs.getString(KEY_KEYWORD_LOOP_COUNTS, null) ?: "{}")
        return if (obj.has(keyword)) obj.getInt(keyword) else null
    }

    fun getKeywordLoopCountMap(): Map<String, Int> {
        val obj = JSONObject(prefs.getString(KEY_KEYWORD_LOOP_COUNTS, null) ?: "{}")
        return obj.keys().asSequence().associateWith { obj.getInt(it) }
    }

    /** 移除某关键词的全部个性化配置（删除关键词时调用）。 */
    fun clearKeywordConfig(keyword: String) {
        saveKeywordRingtone(keyword, null)
        saveKeywordLoopCount(keyword, null)
    }

    /** 全局默认循环次数：0 = 直到用户确认（无限）。 */
    fun saveDefaultLoopCount(count: Int) {
        prefs.edit().putInt(KEY_DEFAULT_LOOP_COUNT, count).apply()
        Log.i("SharedPreferencesHelper", "默认循环次数已保存: $count")
    }

    fun getDefaultLoopCount(): Int {
        return prefs.getInt(KEY_DEFAULT_LOOP_COUNT, 0)
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
        val obj = JSONObject().apply {
            put("keyword", record.keyword)
            put("sourceApp", record.sourceApp ?: JSONObject.NULL)
            put("timestamp", record.timestamp)
            put("endType", record.endType.name)
        }
        val newArr = JSONArray().put(obj)
        for (i in 0 until minOf(arr.length(), MAX_ALERT_HISTORY - 1)) {
            newArr.put(arr.get(i))
        }
        prefs.edit().putString(KEY_ALERT_HISTORY, newArr.toString()).apply()
        Log.i("SharedPreferencesHelper", "报警记录已写入: ${record.keyword} (${record.endType})")
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
}
