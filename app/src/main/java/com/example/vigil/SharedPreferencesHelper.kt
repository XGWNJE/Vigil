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

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "vigil_prefs"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        // 新增: 应用过滤相关键
        private const val KEY_FILTER_APPS_ENABLED = "filter_apps_enabled"
        private const val KEY_FILTERED_APP_PACKAGES = "filtered_app_packages"


        fun isServiceEnabledByUser(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, false)
        }
    }

    fun saveKeywords(keywords: List<String>) {
        prefs.edit().putString(KEY_KEYWORDS, keywords.joinToString(",")).apply()
        Log.i("SharedPreferencesHelper", "关键词已保存: $keywords")
    }

    fun getKeywords(): List<String> {
        val keywordsString = prefs.getString(KEY_KEYWORDS, null)
        return keywordsString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
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
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        Log.i("SharedPreferencesHelper", "服务启用状态已保存: $enabled")
    }

    fun getServiceEnabledState(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    // --- 新增: 应用过滤相关方法 ---

    /**
     * 保存“仅监听指定应用”功能的启用状态。
     * @param enabled true 表示启用过滤，false 表示监听所有应用。
     */
    fun saveFilterAppsEnabledState(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_APPS_ENABLED, enabled).apply()
        Log.i("SharedPreferencesHelper", "应用过滤启用状态已保存: $enabled")
    }

    /**
     * 获取“仅监听指定应用”功能的启用状态。
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
        prefs.edit().putStringSet(KEY_FILTERED_APP_PACKAGES, appPackages).apply()
        Log.i("SharedPreferencesHelper", "被过滤的应用包名列表已保存: ${appPackages?.size ?: 0}个")
    }

    /**
     * 获取用户选择的被监听的应用包名列表。
     * @return 返回应用包名集合。如果未保存，则返回空集合。
     */
    fun getFilteredAppPackages(): Set<String> {
        return prefs.getStringSet(KEY_FILTERED_APP_PACKAGES, emptySet()) ?: emptySet()
    }
}
