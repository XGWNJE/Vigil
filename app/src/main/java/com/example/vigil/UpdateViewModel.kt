// src/main/java/com/example/vigil/UpdateViewModel.kt
package com.example.vigil

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 应用自动更新的 UI 状态机。
 *
 * 只做状态与协程编排，网络/下载/安装的纯逻辑在 [UpdateChecker]；
 * 「安装未知应用」权限的申请（需 ActivityResultLauncher）由 Compose 层负责，
 * 本 ViewModel 只暴露 [UiState.installReady] 供其触发安装。
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    /** 更新相关弹窗内容：有新版本 / 已最新 / 网络不可达 / 其它错误 */
    sealed class UpdateDialog {
        data class Available(val info: UpdateChecker.UpdateInfo) : UpdateDialog()
        data object UpToDate : UpdateDialog()
        data object NetworkUnavailable : UpdateDialog()
        data class Error(val message: String) : UpdateDialog()
    }

    data class UiState(
        val isChecking: Boolean = false,
        val dialog: UpdateDialog? = null,
        val isDownloading: Boolean = false,
        val downloadProgress: Float = 0f,
        /** 已下载好的 APK，交给 Compose 层触发系统安装 */
        val installReady: File? = null,
        val installing: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val prefs = SharedPreferencesHelper(app)

    /**
     * 检查更新。
     * @param isColdStart 冷启动自动检查：仅当「发现新版本且用户未对同版本点过稍后」才弹窗，
     *                   已最新/网络失败等情况静默（避免冷启动被打扰）。
     *                    手动检查（点版本号文本）则任何结果都给出明确提示。
     */
    fun checkForUpdate(isColdStart: Boolean) {
        if (_state.value.isChecking) return
        _state.value = _state.value.copy(isChecking = true)
        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = UpdateChecker.check(context)
            val cur = _state.value
            when (result) {
                is UpdateChecker.CheckResult.UpdateAvailable -> {
                    val info = result.info
                    val show = if (isColdStart) {
                        info.versionName != prefs.getLastDismissedUpdateVersion()
                    } else {
                        true
                    }
                    _state.value = cur.copy(
                        isChecking = false,
                        dialog = if (show) UpdateDialog.Available(info) else null
                    )
                }
                UpdateChecker.CheckResult.UpToDate -> {
                    // 冷启动静默；手动检查给「已是最新」提示
                    _state.value = cur.copy(
                        isChecking = false,
                        dialog = if (isColdStart) null else UpdateDialog.UpToDate
                    )
                }
                UpdateChecker.CheckResult.NetworkUnavailable -> {
                    _state.value = cur.copy(
                        isChecking = false,
                        dialog = if (isColdStart) null else UpdateDialog.NetworkUnavailable
                    )
                }
                is UpdateChecker.CheckResult.Error -> {
                    _state.value = cur.copy(
                        isChecking = false,
                        dialog = if (isColdStart) null else UpdateDialog.Error(result.message)
                    )
                }
            }
        }
    }

    /** 关闭更新弹窗；若用户点「稍后」，记住该版本，冷启动不再重复提示。 */
    fun dismissDialog() {
        val d = _state.value.dialog
        if (d is UpdateDialog.Available) {
            prefs.setLastDismissedUpdateVersion(d.info.versionName)
        }
        _state.value = _state.value.copy(dialog = null)
    }

    /** 用户选择「更新」：下载 APK → 完成后置 installReady 交给 UI 触发系统安装。 */
    fun updateNow() {
        val d = _state.value.dialog as? UpdateDialog.Available ?: return
        _state.value = _state.value.copy(
            dialog = null,
            isDownloading = true,
            downloadProgress = 0f,
            installing = false
        )
        val info = d.info
        viewModelScope.launch {
            val context = getApplication<Application>()
            val url = info.apkUrl
            if (url.isNullOrBlank()) {
                // release 未挂 APK 资产：退化为打开浏览器发版页
                _state.value = _state.value.copy(isDownloading = false)
                Log.w(TAG, "最新版本无 APK 资产，改打开发版页")
                openReleasePage(context, info.releasePageUrl)
                return@launch
            }
            val apk = UpdateChecker.download(context, url, info.versionName) { progress ->
                // 下载进度回主线程
                viewModelScope.launch {
                    _state.value = _state.value.copy(downloadProgress = progress)
                }
            }
            if (apk != null) {
                _state.value = _state.value.copy(isDownloading = false, installReady = apk)
            } else {
                // 下载失败
                _state.value = _state.value.copy(
                    isDownloading = false,
                    dialog = UpdateDialog.Error("下载更新失败，请检查网络后重试")
                )
            }
        }
    }

    /** 已具备安装权限，调起系统安装器。 */
    fun completeInstall() {
        val apk = _state.value.installReady ?: return
        _state.value = _state.value.copy(installReady = null, installing = true)
        val context = getApplication<Application>()
        UpdateChecker.triggerInstall(context, apk)
        _state.value = _state.value.copy(installing = false)
    }

    /** 用户拒绝授予安装权限或放弃安装时，清理待安装文件。 */
    fun clearInstallReady() {
        _state.value = _state.value.copy(installReady = null, installing = false)
    }

    private fun openReleasePage(context: Application, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开发版页失败", e)
        }
    }

    private companion object {
        const val TAG = "UpdateViewModel"
    }
}
