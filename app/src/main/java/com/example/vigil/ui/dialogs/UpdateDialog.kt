package com.example.vigil.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.ui.components.VigilDialog
import com.example.vigil.ui.components.VigilDialogShape
import com.example.vigil.update.UpdateViewModel

/**
 * 统一应用更新弹窗（暖黑石墨 + 琥珀金）。
 */
@Composable
fun UpdateDialog(
    state: UpdateViewModel.UiState,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    when (val dialog = state.dialog) {
        is UpdateViewModel.UpdateDialog.Available -> {
            val info = dialog.info
            VigilDialog(
                title = "发现新版本",
                onDismiss = onDismiss,
                confirmText = "立即更新",
                dismissText = "稍后",
                onConfirm = onUpdate
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("v$currentVersionName", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                        Text("→", fontSize = 13.sp, color = colors.onSurfaceVariant)
                        Text("v${info.versionName}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Text(
                        text = info.releaseNotes,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }

        is UpdateViewModel.UpdateDialog.UpToDate -> {
            VigilDialog(
                title = "已是最新版本",
                onDismiss = onDismiss,
                confirmText = "确定",
                dismissText = "",
                onConfirm = onDismiss
            ) {
                Text("当前版本 v$currentVersionName", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
            }
        }

        is UpdateViewModel.UpdateDialog.NetworkUnavailable -> {
            VigilDialog(
                title = "网络不可用",
                onDismiss = onDismiss,
                confirmText = "确定",
                dismissText = "",
                onConfirm = onDismiss
            ) {
                Text("无法连接更新服务器，请检查网络后重试。", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }

        is UpdateViewModel.UpdateDialog.Error -> {
            VigilDialog(
                title = "检查更新失败",
                onDismiss = onDismiss,
                confirmText = "确定",
                dismissText = "",
                onConfirm = onDismiss
            ) {
                Text(dialog.message, fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }

        null -> Unit
    }

    if (state.isDownloading) {
        val progress = state.downloadProgress
        AlertDialog(
            onDismissRequest = {},
            containerColor = colors.surface,
            shape = VigilDialogShape,
            tonalElevation = 0.dp,
            title = {
                Text("正在下载更新", color = colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (progress != null && progress >= 0f) {
                        val clamped = progress.coerceIn(0f, 1f)
                        val percent = (clamped * 100f).toInt()
                        LinearProgressIndicator(
                            progress = { clamped },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant
                        )
                        Text("$percent%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}
