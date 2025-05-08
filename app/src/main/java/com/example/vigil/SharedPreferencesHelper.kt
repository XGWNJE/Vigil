package com.example.vigil

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * SharedPreferencesHelper 用于管理应用的持久化存储。
 *
 * @property context 上下文环境，用于访问 SharedPreferences。
 */
class SharedPreferencesHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "vigil_prefs"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_SERVICE_ENABLED = "service_enabled" // 用于持久化服务开关状态

        // 用于 MyNotificationListenerService 检查自身是否应该运行
        // 注意：这只是一个持久化的用户意图，实际服务是否运行还受系统影响
        fun isServiceEnabledByUser(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, false)
        }
    }

    /**
     * 保存关键词列表。
     * 关键词以逗号分隔的字符串形式存储。
     *
     * @param keywords 关键词列表。
     */
    fun saveKeywords(keywords: List<String>) {
        prefs.edit().putString(KEY_KEYWORDS, keywords.joinToString(",")).apply()
        // 中文日志：关键词已保存
        android.util.Log.i("SharedPreferencesHelper", "关键词已保存: $keywords")
    }

    /**
     * 获取已保存的关键词列表。
     *
     * @return 返回关键词列表。如果未保存，则返回空列表。
     */
    fun getKeywords(): List<String> {
        val keywordsString = prefs.getString(KEY_KEYWORDS, null)
        return keywordsString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /**
     * 保存选定的铃声 URI。
     *
     * @param ringtoneUri 铃声的 URI。如果为 null，则表示清除已选铃声。
     */
    fun saveRingtoneUri(ringtoneUri: Uri?) {
        prefs.edit().putString(KEY_RINGTONE_URI, ringtoneUri?.toString()).apply()
        // 中文日志：铃声 URI 已保存
        android.util.Log.i("SharedPreferencesHelper", "铃声 URI 已保存: $ringtoneUri")
    }

    /**
     * 获取已保存的铃声 URI。
     *
     * @return 返回铃声的 URI。如果未保存或无效，则返回 null。
     */
    fun getRingtoneUri(): Uri? {
        val uriString = prefs.getString(KEY_RINGTONE_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    /**
     * 保存服务启用状态（用户通过开关设置的）。
     * @param enabled true 表示用户希望启用服务，false 表示禁用。
     */
    fun saveServiceEnabledState(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        // 中文日志：服务启用状态已保存
        android.util.Log.i("SharedPreferencesHelper", "服务启用状态已保存: $enabled")
    }

    /**
     * 获取用户设置的服务启用状态。
     * @return true 如果用户设置启用服务，默认为 false。
     */
    fun getServiceEnabledState(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }
}
