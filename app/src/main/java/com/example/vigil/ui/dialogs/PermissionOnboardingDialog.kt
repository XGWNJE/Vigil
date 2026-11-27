package com.example.vigil.ui.dialogs

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.ui.components.VigilDialog

/**
 * 程序初始化核心权限引导弹窗。
 * 区分「必需」与「推荐」权限，允许逐项或顺序一键授权，同时支持暂不配置以免阻塞使用。
 */
@Composable
fun PermissionOnboardingDialog(
    hasNotificationAccess: Boolean,
    canPostNotifications: Boolean,
    hasOverlayPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onDismissLater: () -> Unit,
    onComplete: () -> Unit
) {
    val showPostNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val nextAction: (() -> Unit)? = when {
        !hasNotificationAccess -> onRequestNotificationAccess
        showPostNotifications && !canPostNotifications -> onRequestPostNotifications
        !hasOverlayPermission -> onRequestOverlayPermission
        !isIgnoringBatteryOptimizations -> onRequestBatteryWhitelist
        else -> null
    }
    val allGranted = nextAction == null

    VigilDialog(
        title = "开启核心权限",
        onDismiss = onDismissLater,
        confirmText = if (allGranted) "开始使用" else "授权下一项",
        dismissText = if (allGranted) "关闭" else "稍后配置",
        onConfirm = {
            if (nextAction != null) {
                nextAction()
            } else {
                onComplete()
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "为确保不错过重要提醒，建议先开启以下权限：",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OnboardingPermissionItem(
                title = "通知使用权",
                subtitle = "读取并匹配关键词通知",
                badge = "必需",
                granted = hasNotificationAccess,
                onClick = onRequestNotificationAccess
            )

            if (showPostNotifications) {
                OnboardingPermissionItem(
                    title = "发送通知",
                    subtitle = "维持前台监听与报警通知",
                    badge = "必需",
                    granted = canPostNotifications,
                    onClick = onRequestPostNotifications
                )
            }

            OnboardingPermissionItem(
                title = "系统悬浮窗",
                subtitle = "在其他应用上方弹出报警卡",
                badge = "推荐",
                granted = hasOverlayPermission,
                onClick = onRequestOverlayPermission
            )

            OnboardingPermissionItem(
                title = "电池白名单",
                subtitle = "防止后台监听被系统清理",
                badge = "推荐",
                granted = isIgnoringBatteryOptimizations,
                onClick = onRequestBatteryWhitelist
            )
        }
    }
}

@Composable
private fun OnboardingPermissionItem(
    title: String,
    subtitle: String,
    badge: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isRequired = badge == "必需"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .clickable(enabled = !granted, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = badge,
                    color = if (isRequired) colors.error else colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            (if (isRequired) colors.error else colors.primary).copy(alpha = 0.14f)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        if (granted) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "已开启",
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "去开启",
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
