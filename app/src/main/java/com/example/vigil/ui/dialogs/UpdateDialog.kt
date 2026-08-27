// src/main/java/com/example/vigil/ui/dialogs/UpdateDialog.kt
package com.example.vigil.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.UpdateViewModel
import com.example.vigil.ui.theme.VigilDirAAcid
import com.example.vigil.ui.theme.VigilDirABg
import com.example.vigil.ui.theme.VigilDirADim
import com.example.vigil.ui.theme.VigilDirAInk
import com.example.vigil.ui.theme.VigilDirALine

/**
 * 应用自动更新弹窗（DirA 风格）。
 * 渲染 [state]：有新版本时给出「更新 / 稍后」；已最新、网络不可达、异常给出对应提示；
 * 下载中给出进度；其余情况不渲染任何内容（返回 null 由调用方控制）。
 *
 * @param onUpdate 用户点「更新」→ 触发下载安装流程
 * @param onDismiss 用户点「稍后」或关闭 → 记住该版本不再冷启动提示
 */
@Composable
fun UpdateDialog(
    state: UpdateViewModel.UiState,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    when (val dialog = state.dialog) {
        is UpdateViewModel.UpdateDialog.Available -> {
            val info = dialog.info
            DirAAlertDialog(
                onDismissRequest = onDismiss,
                title = "发现新版本",
                text = {
                    Column {
                        Text(
                            text = "当前版本  v$currentVersionName",
                            fontSize = 13.sp,
                            color = VigilDirADim
                        )
                        Text(
                            text = "最新版本  v${info.versionName}",
                            fontSize = 13.sp,
                            color = VigilDirAAcid,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(VigilDirALine)
                        )
                        Text(
                            text = "更新内容",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = VigilDirAInk,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                        Text(
                            text = info.releaseNotes,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = VigilDirADim,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                },
                confirm = { TextButton(onClick = onUpdate) { Text("更新", color = VigilDirAAcid) } },
                dismiss = { TextButton(onClick = onDismiss) { Text("稍后", color = VigilDirADim) } }
            )
        }

        UpdateViewModel.UpdateDialog.UpToDate -> {
            DirAAlertDialog(
                onDismissRequest = onDismiss,
                title = "已是最新版本",
                text = { Text("当前 v$currentVersionName 已是最新版本。", fontSize = 13.sp, color = VigilDirADim) },
                confirm = { TextButton(onClick = onDismiss) { Text("确定", color = VigilDirAAcid) } }
            )
        }

        UpdateViewModel.UpdateDialog.NetworkUnavailable -> {
            DirAAlertDialog(
                onDismissRequest = onDismiss,
                title = "无法访问 GitHub",
                text = {
                    Text(
                        text = "当前网络环境可能无法访问 GitHub，请检查网络连接后重试。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = VigilDirADim
                    )
                },
                confirm = { TextButton(onClick = onDismiss) { Text("知道了", color = VigilDirAAcid) } }
            )
        }

        is UpdateViewModel.UpdateDialog.Error -> {
            DirAAlertDialog(
                onDismissRequest = onDismiss,
                title = "检查更新失败",
                text = { Text(dialog.message, fontSize = 13.sp, color = VigilDirADim) },
                confirm = { TextButton(onClick = onDismiss) { Text("知道了", color = VigilDirAAcid) } }
            )
        }

        null -> {
            if (state.isDownloading) {
                DownloadProgressDialog(progress = state.downloadProgress)
            }
            // 其余情况不渲染
        }
    }
}

/** 下载进度对话框（无关闭按钮，下载完成/失败自动消失）。 */
@Composable
private fun DownloadProgressDialog(progress: Float) {
    DirAAlertDialog(
        onDismissRequest = { /* 下载中不可关闭 */ },
        title = "正在下载更新",
        text = {
            Column {
                Text(
                    text = "下载完成后将自动进入安装。",
                    fontSize = 12.sp,
                    color = VigilDirADim,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LinearProgressIndicator(
                    progress = { progress },
                    color = VigilDirAAcid,
                    trackColor = VigilDirALine,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = VigilDirADim,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirm = {}
    )
}

/** DirA 风格的通用 AlertDialog 容器（背景/形状/分隔与其它弹窗一致）。 */
@Composable
private fun DirAAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirm: @Composable () -> Unit,
    dismiss: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = VigilDirAInk) },
        text = text,
        confirmButton = confirm,
        dismissButton = dismiss
    )
}
