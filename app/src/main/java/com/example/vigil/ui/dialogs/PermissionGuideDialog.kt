// src/main/java/com/example/vigil/ui/dialogs/PermissionGuideDialog.kt
package com.example.vigil.ui.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.ui.theme.VigilPrimary
import com.example.vigil.ui.theme.VigilBackground
import com.example.vigil.ui.theme.VigilTextSecondary
import com.example.vigil.ui.theme.VigilTextPrimary
import com.example.vigil.ui.theme.VigilDivider

/**
 * 「A · 一线」权限引导确认弹窗：替代 PermissionUtils 原生的 android.app.AlertDialog。
 * 只负责"说明 + 确认/取消"，确认后的系统跳转逻辑仍由 PermissionUtils 执行。
 * 视觉与主屏 AddKeywordDialog 同一语言：暗底、发丝线边框、酸橙确认键。
 */
@Composable
fun PermissionGuideDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "前往设置"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VigilBackground,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDivider, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = VigilTextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(text = confirmText, color = VigilPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilTextSecondary)
            }
        }
    )
}
