// src/main/java/com/example/vigil/ui/monitoring/MonitoringScreen.kt
package com.example.vigil.ui.monitoring

import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.MainActivity
import com.example.vigil.PermissionUtils
import com.example.vigil.SharedPreferencesHelper
import com.example.vigil.ui.theme.*

@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefsHelper = remember { SharedPreferencesHelper(context) }

    val serviceState by viewModel.serviceState
    val serviceEnabled by viewModel.serviceEnabled
    val debugInfo by viewModel.debugInfo
    val showKeywordAlertDialog by viewModel.showKeywordAlertDialog
    val matchedKeyword by viewModel.matchedKeywordForDialog

    // Debug panel expand state
    var debugExpanded by remember { mutableStateOf(false) }

    // Keywords from prefs
    val keywords = remember { prefsHelper.getKeywords().toList() }

    // Real-time permission states
    val hasNotifAccess = remember { mutableStateOf(PermissionUtils.isNotificationListenerEnabled(context)) }
    // Refresh permission states on composition
    LaunchedEffect(Unit) {
        hasNotifAccess.value = PermissionUtils.isNotificationListenerEnabled(context)
    }

    // Animation states
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val initRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "initRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VigilBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // TopBar
        TopBar()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Status Card
            StatusCard(
                serviceState = serviceState,
                debugInfo = debugInfo,
                keywordsCount = keywords.size,
                pulseAlpha = pulseAlpha,
                initRotation = initRotation,
                debugExpanded = debugExpanded,
                onDebugToggle = { debugExpanded = !debugExpanded },
                onRestartClick = {
                    viewModel.onRestartServiceClick {
                        if (activity is MainActivity) activity.restartService()
                    }
                },
                onGrantPermissionClick = {
                    if (activity is MainActivity) {
                        PermissionUtils.requestNotificationListenerPermission(activity)
                    }
                }
            )

            // Switch Row Card
            SwitchRowCard(
                serviceEnabled = serviceEnabled,
                serviceState = serviceState,
                hasNotificationAccess = hasNotifAccess.value,
                onToggle = { newEnabled ->
                    viewModel.onServiceEnabledChange(
                        enabled = newEnabled,
                        isLicensed = true,
                        startServiceCallback = { hasPermission ->
                            if (activity is MainActivity) activity.startVigilService(hasPermission)
                        },
                        stopServiceCallback = {
                            if (activity is MainActivity) activity.stopVigilService()
                        }
                    )
                }
            )

            // Permission Card
            PermCard(hasNotifAccess = hasNotifAccess.value)

            // Keyword Section
            KeywordSection(keywords = keywords)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Alert Dialog
    if (showKeywordAlertDialog) {
        com.example.vigil.ui.dialogs.KeywordAlertDialog(
            onDismissRequest = { viewModel.onKeywordAlertDialogDismiss() },
            onConfirm = { viewModel.onKeywordAlertDialogConfirm() },
            matchedKeyword = matchedKeyword
        )
    }
}

@Composable
private fun TopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Vigil",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = VigilTextPrimary
        )
    }
}

@Composable
private fun StatusCard(
    serviceState: ServiceState,
    debugInfo: String,
    keywordsCount: Int,
    pulseAlpha: Float,
    initRotation: Float,
    debugExpanded: Boolean,
    onDebugToggle: () -> Unit,
    onRestartClick: () -> Unit,
    onGrantPermissionClick: () -> Unit
) {
    val (icon, iconColor, badgeColor, badgeLabel, statusTitle, statusSub) = when (serviceState) {
        ServiceState.DISABLED -> Sextet(
            Icons.Filled.PowerSettingsNew,
            VigilTextDisabled,
            VigilTextDisabled,
            "已停止",
            "通知监听服务",
            "点击下方开关启动监听"
        )
        ServiceState.INITIALIZING -> Sextet(
            Icons.Filled.Sync,
            VigilInfo,
            VigilInfo,
            "初始化中",
            "等待服务连接...",
            "正在与后台通知监听服务建立连接"
        )
        ServiceState.RUNNING -> Sextet(
            Icons.Filled.Sensors,
            VigilPrimary,
            VigilSuccess,
            "运行中",
            "通知监听服务",
            "正在监听 $keywordsCount 个关键词 · 全部权限已就绪"
        )
        ServiceState.RUNNING_LIMITED -> Sextet(
            Icons.Filled.SensorsOff,
            VigilWarning,
            VigilWarning,
            "运行受限",
            "通知监听服务",
            "部分权限缺失，报警可能无法弹出"
        )
        ServiceState.HEARTBEAT_TIMEOUT -> Sextet(
            Icons.Filled.HeartBroken,
            VigilError,
            VigilError,
            "心跳超时",
            "服务心跳超时",
            "服务可能已被系统强制停止"
        )
        ServiceState.NO_PERMISSION -> Sextet(
            Icons.Filled.NotificationsOff,
            VigilWarning,
            VigilWarning,
            "权限未授予",
            "权限未授予",
            "通知使用权未开启，服务无法监听通知"
        )
        ServiceState.ERROR -> Sextet(
            Icons.Filled.ErrorOutline,
            VigilError,
            VigilError,
            "未知错误",
            "未知错误",
            debugInfo
        )
    }

    val cardBg = when (serviceState) {
        ServiceState.HEARTBEAT_TIMEOUT -> VigilErrorContainer
        ServiceState.NO_PERMISSION -> VigilWarningContainer
        ServiceState.INITIALIZING -> VigilInfoContainer.copy(alpha = 0.3f)
        else -> VigilSurfaceVariant
    }

    val cardBorderColor = when (serviceState) {
        ServiceState.HEARTBEAT_TIMEOUT -> VigilErrorBorder
        ServiceState.NO_PERMISSION -> VigilWarningContainer
        ServiceState.RUNNING -> VigilPrimary.copy(alpha = 0.2f)
        else -> VigilBorder
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top row: icon + badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon wrapper
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = iconColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (serviceState == ServiceState.INITIALIZING) {
                            Icons.Filled.Sync
                        } else {
                            icon
                        },
                        contentDescription = null,
                        tint = iconColor,
                        modifier = if (serviceState == ServiceState.INITIALIZING) {
                            Modifier.size(22.dp).rotate(initRotation)
                        } else {
                            Modifier.size(22.dp)
                        }
                    )
                }

                // Status badge
                Row(
                    modifier = Modifier
                        .background(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(9999.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(badgeColor, CircleShape)
                    )
                    Text(
                        text = badgeLabel,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Title
            Text(
                text = statusTitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )

            // Subtitle
            Text(
                text = statusSub,
                fontSize = 13.sp,
                color = VigilTextSecondary
            )

            // Action buttons for specific states
            when (serviceState) {
                ServiceState.HEARTBEAT_TIMEOUT -> {
                    RestartButton(onClick = onRestartClick)
                }
                ServiceState.NO_PERMISSION -> {
                    GrantPermissionButton(onClick = onGrantPermissionClick)
                }
                else -> {}
            }

            // Debug Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Debug toggle row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDebugToggle() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "诊断信息",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = VigilTextDisabled
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (debugExpanded) "收起" else "展开",
                            fontSize = 10.sp,
                            color = VigilTextDisabled.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(
                            imageVector = if (debugExpanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = VigilTextDisabled.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Debug panel
                if (debugExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VigilSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, VigilBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp, 12.dp)
                    ) {
                        Text(
                            text = debugInfo,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = VigilTextDisabled,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private data class Quintet<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private data class Sextet<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

@Composable
private fun RestartButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VigilErrorSubtle, RoundedCornerShape(12.dp))
            .border(1.dp, VigilErrorBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.RestartAlt,
            contentDescription = null,
            tint = VigilError,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "重启服务",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = VigilError
        )
    }
}

@Composable
private fun GrantPermissionButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VigilWarningContainer, RoundedCornerShape(12.dp))
            .border(1.dp, VigilWarning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = VigilWarning,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "前往系统设置授权",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = VigilWarning
        )
    }
}

@Composable
private fun SwitchRowCard(
    serviceEnabled: Boolean,
    serviceState: ServiceState,
    hasNotificationAccess: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val desc = when (serviceState) {
        ServiceState.DISABLED -> "点击开关启动监听"
        ServiceState.INITIALIZING -> "正在连接后台服务..."
        ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> "服务运行中，点击可停止"
        ServiceState.HEARTBEAT_TIMEOUT -> "服务响应异常，建议重启"
        ServiceState.NO_PERMISSION -> "需要先授予通知使用权"
        ServiceState.ERROR -> "服务状态异常"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VigilSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, VigilBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val targetEnabled = !serviceEnabled
                    if (targetEnabled && !hasNotificationAccess) {
                        // Try to enable but missing notification permission — reject
                        Toast.makeText(context, "请先授予通知使用权", Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    onToggle(targetEnabled)
                }
                .padding(16.dp, 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "通知监听服务",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VigilTextPrimary
                )
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = VigilTextTertiary
                )
            }

            // Switch pill
            Box(
                modifier = Modifier
                    .size(52.dp, 28.dp)
                    .background(
                        if (serviceEnabled) VigilPrimary else VigilTextDisabled,
                        RoundedCornerShape(9999.dp)
                    )
                    .padding(2.dp),
                contentAlignment = if (serviceEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(VigilSwitchThumb, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun PermCard(hasNotifAccess: Boolean) {
    val (icon, text, color, containerColor, borderColor) = if (hasNotifAccess) {
        Quintet(
            Icons.Filled.Shield,
            "核心权限已就绪",
            VigilSuccess,
            VigilSuccessContainer,
            VigilSuccessBorder
        )
    } else {
        Quintet(
            Icons.Filled.Shield,
            "通知使用权未授予",
            VigilWarning,
            VigilWarningContainer,
            VigilWarning.copy(alpha = 0.3f)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
private fun KeywordSection(keywords: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "监控关键词",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VigilTextSecondary
            )
            // Count badge
            Box(
                modifier = Modifier
                    .background(VigilPrimary.copy(alpha = 0.12f), RoundedCornerShape(9999.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${keywords.size} 个",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = VigilPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Keywords chips row
        if (keywords.isEmpty()) {
            Text(
                text = "暂无关键词，请在设置中添加",
                fontSize = 12.sp,
                color = VigilTextDisabled
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(keywords) { keyword ->
                    KeywordChip(keyword = keyword)
                }
            }
        }
    }
}

@Composable
private fun KeywordChip(keyword: String) {
    Row(
        modifier = Modifier
            .background(VigilChipBackground, RoundedCornerShape(9999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = keyword,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = VigilChipText
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = VigilTextTertiary,
            modifier = Modifier.size(12.dp)
        )
    }
}
