// src/main/java/com/example/vigil/ui/monitoring/MonitoringScreen.kt
package com.example.vigil.ui.monitoring

import android.app.Activity
import android.app.Application
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.MainActivity
import com.example.vigil.ui.theme.VigilTheme

// ---- 状态到 UI 属性的映射 ----

private data class StateVisual(
    val icon: ImageVector,
    val primaryColor: Color,
    val label: String,
    val sublabel: String
)

@Composable
private fun serviceStateVisual(state: ServiceState, debugInfo: String): StateVisual {
    return when (state) {
        ServiceState.DISABLED -> StateVisual(
            icon = Icons.Filled.PowerSettingsNew,
            primaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
            label = "已停止",
            sublabel = "通知监听服务未启动"
        )
        ServiceState.INITIALIZING -> StateVisual(
            icon = Icons.Filled.Sync,
            primaryColor = MaterialTheme.colorScheme.tertiary,
            label = "初始化中",
            sublabel = "等待服务连接... [$debugInfo]"
        )
        ServiceState.RUNNING -> StateVisual(
            icon = Icons.Filled.Sensors,
            primaryColor = MaterialTheme.colorScheme.primary,
            label = "运行中",
            sublabel = debugInfo
        )
        ServiceState.RUNNING_LIMITED -> StateVisual(
            icon = Icons.Filled.SensorsOff,
            primaryColor = Color(0xFFFF9800), // 橙色 warning
            label = "运行中 (权限受限)",
            sublabel = "悬浮窗或通知权限缺失，报警可能无法弹出 | $debugInfo"
        )
        ServiceState.HEARTBEAT_TIMEOUT -> StateVisual(
            icon = Icons.Filled.HeartBroken,
            primaryColor = MaterialTheme.colorScheme.error,
            label = "服务心跳超时",
            sublabel = "heartbeat timeout — Service 可能被系统杀死 | $debugInfo"
        )
        ServiceState.NO_PERMISSION -> StateVisual(
            icon = Icons.Filled.NotificationsOff,
            primaryColor = MaterialTheme.colorScheme.error,
            label = "权限未授予",
            sublabel = "通知使用权未开启，服务无法监听通知 | $debugInfo"
        )
        ServiceState.ERROR -> StateVisual(
            icon = Icons.Filled.ErrorOutline,
            primaryColor = MaterialTheme.colorScheme.error,
            label = "未知错误",
            sublabel = debugInfo
        )
    }
}

// ---- 主界面 ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val serviceState by viewModel.serviceState
    val serviceEnabled by viewModel.serviceEnabled
    val debugInfo by viewModel.debugInfo
    val showKeywordAlertDialog by viewModel.showKeywordAlertDialog
    val matchedKeyword by viewModel.matchedKeywordForDialog

    val visual = serviceStateVisual(serviceState, debugInfo)

    // 开关弹簧动画
    val switchScale = remember { Animatable(1f) }
    LaunchedEffect(serviceEnabled) {
        switchScale.animateTo(1.15f, animationSpec = tween(80))
        switchScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    // 运行时脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    // 初始化旋转动画
    val initRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "initRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {

        // ---- 区域1：状态卡片 ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = visual.primaryColor.copy(alpha = 0.08f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = visual.primaryColor.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 状态图标 + 动画
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = visual.primaryColor.copy(
                                alpha = if (serviceState == ServiceState.RUNNING) pulseAlpha.value * 0.15f else 0.12f
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconModifier = if (serviceState == ServiceState.INITIALIZING) {
                        Modifier
                            .size(40.dp)
                            .rotate(initRotation.value)
                    } else {
                        Modifier.size(40.dp)
                    }
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = visual.label,
                        tint = visual.primaryColor,
                        modifier = iconModifier
                    )
                }

                // 主状态文字
                Text(
                    text = visual.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = visual.primaryColor
                )

                // 副状态文字（技术信息）
                if (visual.sublabel.isNotEmpty()) {
                    Text(
                        text = visual.sublabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }

                // 条件：权限缺失时显示跳转按钮
                if (serviceState == ServiceState.NO_PERMISSION) {
                    TextButton(
                        onClick = {
                            if (activity is MainActivity) {
                                com.example.vigil.PermissionUtils.requestNotificationListenerPermission(activity)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("前往系统设置授权")
                    }
                }

                // 条件：权限受限时显示提示
                if (serviceState == ServiceState.RUNNING_LIMITED) {
                    TextButton(
                        onClick = {
                            if (activity is MainActivity) {
                                com.example.vigil.PermissionUtils.requestOverlayPermission(activity)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("授予悬浮窗权限")
                    }
                }

                // 条件：心跳超时时显示重启按钮
                if (serviceState == ServiceState.HEARTBEAT_TIMEOUT) {
                    Button(
                        onClick = {
                            viewModel.onRestartServiceClick {
                                if (activity is MainActivity) {
                                    activity.restartService()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重启服务")
                    }
                }
            }
        }

        // ---- 区域2：服务开关 ----
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "通知监听服务",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )

            // 大型开关
            Box(
                modifier = Modifier
                    .scale(switchScale.value)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(36.dp))
                    .clip(RoundedCornerShape(36.dp))
                    .background(
                        if (serviceEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                    .size(width = 112.dp, height = 56.dp)
                    .clickable {
                        viewModel.onServiceEnabledChange(
                            enabled = !serviceEnabled,
                            isLicensed = true,
                            startServiceCallback = { hasPermission ->
                                if (activity is MainActivity) activity.startVigilService(hasPermission)
                            },
                            stopServiceCallback = {
                                if (activity is MainActivity) activity.stopVigilService()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (serviceEnabled) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (serviceEnabled) MaterialTheme.colorScheme.onPrimary else Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (serviceEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 开关说明文字：明确告诉用户这个开关控制什么
            Text(
                text = when (serviceState) {
                    ServiceState.DISABLED -> "点击开关启动监听"
                    ServiceState.INITIALIZING -> "正在连接后台服务..."
                    ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> "服务运行中，点击可停止"
                    ServiceState.HEARTBEAT_TIMEOUT -> "服务响应异常，建议重启"
                    ServiceState.NO_PERMISSION -> "需要先授予通知使用权"
                    ServiceState.ERROR -> "服务状态异常"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 报警对话框
    if (showKeywordAlertDialog) {
        com.example.vigil.ui.dialogs.KeywordAlertDialog(
            onDismissRequest = { viewModel.onKeywordAlertDialogDismiss() },
            onConfirm = { viewModel.onKeywordAlertDialogConfirm() },
            matchedKeyword = matchedKeyword
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MonitoringScreenPreview() {
    VigilTheme {
        MonitoringScreen(viewModel = MonitoringViewModel(LocalContext.current.applicationContext as Application))
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MonitoringScreenDarkPreview() {
    VigilTheme(darkTheme = true) {
        MonitoringScreen(viewModel = MonitoringViewModel(LocalContext.current.applicationContext as Application))
    }
}
