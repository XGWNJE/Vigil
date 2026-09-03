// src/main/java/com/example/vigil/UpdateChecker.kt
package com.example.vigil

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GitHub 渠道的自动更新检查。
 *
 * 刻意不引入网络/JSON 第三方库（保持 APK 极致轻量卖点）：网络用 [HttpURLConnection]，
 * 解析用 Android 内置 [org.json]。所有网络调用都在 [Dispatchers.IO]，不阻塞主线程。
 *
 * 数据源：`GET https://api.github.com/repos/XGWNJE/Vigil/releases/latest`，
 * 取最新 release 的 `tag_name`（版本号）、`body`（发版说明）、`assets[]` 里的 APK 下载地址。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /** GitHub 仓库 */
    const val REPO = "XGWNJE/Vigil"

    /** 生产环境的 GitHub API 基址 */
    const val PROD_API_BASE = "https://api.github.com/repos/$REPO"

    /** FileProvider authority（与 manifest 中的 provider 一致） */
    private fun fileProviderAuthority(context: Context) = "${context.packageName}.fileprovider"

    /** 一次更新检查结果 */
    sealed class CheckResult {
        /** 发现新版本，携带发版信息 */
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()

        /** 已是最新 */
        data object UpToDate : CheckResult()

        /** 网络环境无法访问 GitHub（无网络 / 被墙 / DNS 失败 / 超时） */
        data object NetworkUnavailable : CheckResult()

        /** 其它异常（如 API 返回异常、解析失败） */
        data class Error(val message: String) : CheckResult()
    }

    /** 一次更新的完整信息（来自 GitHub 最新 release） */
    data class UpdateInfo(
        val versionName: String,      // 不带 v 前缀，如 "1.17.0"
        val releaseNotes: String,     // 清洗后的发版说明
        val apkUrl: String?,          // APK 资产直链；null 表示该 release 未上传 APK
        val releasePageUrl: String,   // 浏览器打开的发版页
        val publishedAt: String       // ISO 时间
    )

    /**
     * 当前应用版本名（如 "1.16.0"）。从 PackageManager 读取，避免依赖 BuildConfig。
     */
    fun currentVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "读取版本名失败", e)
            ""
        }
    }

    /** 是否 debug 构建（用于是否放行调试 API 覆盖）。 */
    private fun isDebuggable(context: Context): Boolean {
        return try {
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 决定实际使用的 API 基址。生产恒为 GitHub；debug 构建可通过 SharedPreferences
     * 覆盖为本地模拟地址（自动化验证用），生产包读不到该覆盖。
     */
    fun resolveApiBase(context: Context): String {
        if (!isDebuggable(context)) return PROD_API_BASE
        val override = SharedPreferencesHelper(context).getDebugUpdateApiBase()
        return override?.takeIf { it.isNotBlank() } ?: PROD_API_BASE
    }

    /**
     * 检查是否有新版本。返回密封结果；网络不可达时返回 [CheckResult.NetworkUnavailable]，
     * 调用方据此给用户「无法访问 GitHub」的合适提示。
     */
    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val apiBase = resolveApiBase(context)
        try {
            val conn = URL("$apiBase/releases/latest").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub API 强制要求 User-Agent，缺失会 403
            conn.setRequestProperty("User-Agent", "Vigil-App-Updater")
            val code = conn.responseCode
            if (code == 404) {
                return@withContext CheckResult.Error("未找到发布信息")
            }
            if (code != 200) {
                return@withContext CheckResult.Error("GitHub 返回异常（HTTP $code）")
            }
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(raw)

            val tagName = json.optString("tag_name")
            val versionName = tagName.removePrefix("v").trim()
            if (versionName.isEmpty()) {
                return@withContext CheckResult.Error("无法解析版本号")
            }

            val info = UpdateInfo(
                versionName = versionName,
                releaseNotes = cleanReleaseNotes(json.optString("body")),
                apkUrl = findApkAssetUrl(json),
                releasePageUrl = json.optString("html_url"),
                publishedAt = json.optString("published_at")
            )

            if (!isNewer(versionName, currentVersionName(context))) {
                CheckResult.UpToDate
            } else {
                CheckResult.UpdateAvailable(info)
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "无法解析 GitHub 域名（可能网络受限）", e)
            CheckResult.NetworkUnavailable
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "连接 GitHub 超时", e)
            CheckResult.NetworkUnavailable
        } catch (e: IOException) {
            Log.w(TAG, "访问 GitHub 失败", e)
            CheckResult.NetworkUnavailable
        } catch (e: Exception) {
            Log.e(TAG, "检查更新异常", e)
            CheckResult.Error(e.message ?: "检查更新失败")
        }
    }

    /** 从 release 的 assets 数组里挑出 .apk 资产，返回其浏览器直链；没有则返回 null。 */
    private fun findApkAssetUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank()) return url
            }
        }
        return null
    }

    /** 轻度清洗 GitHub 发的 Markdown 发版说明：去掉标题井号，去掉空行，供纯文本展示。 */
    fun cleanReleaseNotes(body: String): String {
        if (body.isBlank()) return "（暂无发版说明）"
        return body.lines()
            .map { line -> line.trim().trimStart('#').trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .ifBlank { "（暂无发版说明）" }
    }

    /**
     * 版本号比较：latest 是否比 current 新。支持 `1.2.3`、`1.2`、`1.2.3-beta` 等，
     * 按点分数字段逐个比较，缺省字段视作 0。任一非数字解析失败则视为「不更新」。
     */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest) ?: return false
        val c = parseVersion(current) ?: return false
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val li = l.getOrElse(i) { 0 }
            val ci = c.getOrElse(i) { 0 }
            if (li > ci) return true
            if (li < ci) return false
        }
        return false
    }

    private fun parseVersion(v: String): List<Int>? {
        val parts = v.trim()
            .dropWhile { it.isLetter() }     // 去掉 v 前缀
            .split('.', '-', '_')
            .map { it.trim().toIntOrNull() }
            .filterNotNull()
        return if (parts.isEmpty()) null else parts
    }

    /**
     * 下载 APK 到应用缓存目录，返回文件；失败返回 null 并写日志。
     * [onProgress] 回调 0f..1f（回调可能从 IO 线程触发，调用方注意线程切换）。
     */
    suspend fun download(
        context: Context,
        url: String,
        versionName: String,
        onProgress: ((Float) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val cancelled = AtomicBoolean(false)
        val sanitized = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(context.cacheDir, "vigil-update-$sanitized.apk")
        try {
            if (dest.exists()) dest.delete()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Vigil-App-Updater")
            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "下载 APK 返回异常 HTTP $code")
                return@withContext null
            }
            val contentLength = conn.contentLength.toLong()
            var total = 0L
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        if (cancelled.get()) return@withContext null
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        total += read
                        if (contentLength > 0) {
                            onProgress?.invoke((total.toFloat() / contentLength).coerceIn(0f, 1f))
                        }
                    }
                    out.flush()
                }
            }
            if (dest.length() == 0L) {
                dest.delete()
                return@withContext null
            }
            Log.i(TAG, "下载完成: ${dest.absolutePath} (${dest.length()} bytes)")
            dest
        } catch (e: Exception) {
            Log.e(TAG, "下载更新 APK 失败", e)
            dest.delete()
            null
        }
    }

    /**
     * 触发系统安装界面（FileProvider 授权给系统安装器）。
     * 注意：需要已获得 REQUEST_INSTALL_PACKAGES（安装未知应用）权限，由调用方先行检查/引导。
     */
    fun triggerInstall(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "已调起安装界面: $apkFile")
        } catch (e: Exception) {
            Log.e(TAG, "调起安装界面失败", e)
        }
    }

    /** 是否已具备安装未知应用能力（Android 8.0+ 需用户授权）。 */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 例外：更新已安装且同签名应用，系统允许免授权安装
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }
}
