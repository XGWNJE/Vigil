// src/main/java/com/example/vigil/SharedPreferencesHelper.kt
package com.example.vigil

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

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
     */
    fun savePendingAlert(keyword: String) {
        prefs.edit()
            .putString(KEY_PENDING_ALERT_KEYWORD, keyword)
            .putLong(KEY_PENDING_ALERT_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Log.i("SharedPreferencesHelper", "未确认报警已持久化: $keyword")
    }

    /**
     * 读取未确认报警，返回 关键词 to 触发时间戳；无则返回 null。
     */
    fun getPendingAlert(): Pair<String, Long>? {
        val keyword = prefs.getString(KEY_PENDING_ALERT_KEYWORD, null) ?: return null
        val timestamp = prefs.getLong(KEY_PENDING_ALERT_TIMESTAMP, 0L)
        return keyword to timestamp
    }

    /**
     * 清除未确认报警。用户在弹窗中确认后调用。
     */
    fun clearPendingAlert() {
        prefs.edit()
            .remove(KEY_PENDING_ALERT_KEYWORD)
            .remove(KEY_PENDING_ALERT_TIMESTAMP)
            .apply()
        Log.i("SharedPreferencesHelper", "未确认报警已清除。")
    }
}
