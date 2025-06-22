// src/main/java/com/example/vigil/SharedPreferencesHelper.kt
package com.example.vigil

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

/**
 * SharedPreferencesHelper 用于管理应用的持久化存储。
 * 新增了授权状态的保存和读取。
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

        // 新增: 授权状态相关键
        private const val KEY_LICENSE_TYPE = "license_type"
        private const val KEY_LICENSE_EXPIRY_TIMESTAMP = "license_expiry_timestamp" // 使用 Long 存储秒级时间戳
        // 新增: 首次使用和捐赠提示相关键
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_HAS_SHOWN_DONATE_DIALOG = "has_shown_donate_dialog"

        // 将 KEY_IS_LICENSED 的访问修饰符改为 internal
        internal const val KEY_IS_LICENSED = "is_licensed" // 标记是否已成功验证过有效授权码

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

    // --- 新增: 授权状态相关方法 ---

    /**
     * 保存授权状态。
     * @param licensePayload 授权信息对象，如果为 null 表示清除授权状态。
     */
    fun saveLicenseStatus(licensePayload: LicensePayload?) {
        val editor = prefs.edit()
        if (licensePayload != null) {
            editor.putBoolean(KEY_IS_LICENSED, true) // 标记为已授权
            editor.putString(KEY_LICENSE_TYPE, licensePayload.licenseType)
            // 保存过期时间戳，null 时保存一个特殊值或不保存
            if (licensePayload.expiresAt != null) {
                editor.putLong(KEY_LICENSE_EXPIRY_TIMESTAMP, licensePayload.expiresAt)
            } else {
                editor.remove(KEY_LICENSE_EXPIRY_TIMESTAMP) // 没有过期时间则移除
            }
            // 简化处理，features 暂时不保存，如果后续需要再添加
            Log.i("SharedPreferencesHelper", "授权状态已保存: Type=${licensePayload.licenseType}, ExpiresAt=${licensePayload.expiresAt}")
        } else {
            // 清除授权状态
            editor.putBoolean(KEY_IS_LICENSED, false) // 标记为未授权
            editor.remove(KEY_LICENSE_TYPE)
            editor.remove(KEY_LICENSE_EXPIRY_TIMESTAMP)
            Log.i("SharedPreferencesHelper", "授权状态已清除。")
        }
        editor.apply()
    }

    /**
     * 获取保存的授权信息。
     * @return 如果存在有效的授权信息，返回 LicensePayload 对象，否则返回 null。
     */
    fun getLicenseStatus(): LicensePayload? {
        val isLicensed = prefs.getBoolean(KEY_IS_LICENSED, false)
        if (!isLicensed) {
            return null // 没有保存过有效授权
        }

        val licenseType = prefs.getString(KEY_LICENSE_TYPE, null) ?: return null // 授权类型缺失也视为无效
        val expiresAt = if (prefs.contains(KEY_LICENSE_EXPIRY_TIMESTAMP)) {
            prefs.getLong(KEY_LICENSE_EXPIRY_TIMESTAMP, -1) // -1 作为默认值，表示存在但读取失败
        } else {
            null // 没有保存过期时间
        }

        // 检查过期时间（如果存在）
        if (expiresAt != null && expiresAt != -1L) {
            val currentTimeSeconds = System.currentTimeMillis() / 1000
            if (currentTimeSeconds > expiresAt) {
                Log.w("SharedPreferencesHelper", "加载授权时发现已过期。")
                // 授权已过期，这里可以考虑清除保存的状态
                // saveLicenseStatus(null) // 为了简化，暂不在读取时自动清除过期状态，由 MainActivity 处理
                return null // 返回 null 表示授权无效
            }
        } else if (expiresAt == -1L) {
            Log.e("SharedPreferencesHelper", "读取授权过期时间时出错。")
            return null // 读取过期时间失败也视为无效
        }


        // 简化处理，features 暂时不读取
        val features: List<String>? = null

        val dummyIssuedAt = 0L // 加载时无法获取原始 issuedAt，使用占位符
        val licensePayload = LicensePayload("com.example.vigil", licenseType, dummyIssuedAt, expiresAt, features) // appId 也使用硬编码或从 BuildConfig 获取
        Log.d("SharedPreferencesHelper", "加载到授权信息: $licensePayload")
        return licensePayload
    }

    /**
     * 检查当前是否存在有效且未过期的授权。
     * @return true 表示授权有效且未过期，否则返回 false。
     */
    fun isAuthenticated(): Boolean {
        // 移除授权码验证，让应用完全免费
        return true
    }
}
