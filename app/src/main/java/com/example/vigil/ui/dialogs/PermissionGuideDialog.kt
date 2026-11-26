package com.example.vigil.ui.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.example.vigil.ui.components.VigilDialog

/**
 * 统一权限引导确认弹窗（基于 VigilDialog，暖黑石墨 + 琥珀金）。
 */
@Composable
fun PermissionGuideDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "前往设置"
) {
    VigilDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = {
            onConfirm()
            onDismiss()
        }
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
