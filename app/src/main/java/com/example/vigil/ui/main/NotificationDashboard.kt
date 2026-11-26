package com.example.vigil.ui.main

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.data.OverlayAlertStyle
import com.example.vigil.data.SharedPreferencesHelper
import com.example.vigil.ui.components.VigilTopBar
import com.example.vigil.ui.history.AlertHistoryScreen
import com.example.vigil.ui.monitoring.ServiceState
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.util.ListenerRecovery
import com.example.vigil.util.PermissionUtils

/**
 * 统一风格主容器（暖黑石墨 + 琥珀金 · 单一入口信息架构）。
 *
 * 4 个一级页面严格各司其职，同一个能力全应用只保留唯一入口：
 * - 监控：唯一监听主开关（居中发光守望环）+ 纯只读运行概览 + 异常恢复
 * - 规则：关键词管理 + 应用过滤
 * - 记录：完整报警历史与清空
 * - 设置：声音策略、铃声库、应用外浮窗、系统权限与版本更新
 */
@Composable
internal fun NotificationDashboard(
    serviceEnabled: Boolean,
    serviceState: ServiceState,
    keywordCount: Int,
    overlayStyle: OverlayAlertStyle,
    settingsViewModel: SettingsViewModel,
    onToggleLocal: () -> Unit,
    settingsContent: @Composable (Boolean) -> Unit
) {
    val context = LocalContext.current
    val alarmPrefs = remember { SharedPreferencesHelper(context) }
    var page by rememberSaveable { mutableStateOf("监控") }
    var recordCount by remember { mutableIntStateOf(alarmPrefs.getAlertHistory().size) }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "alert_history") recordCount = alarmPrefs.getAlertHistory().size
        }
        alarmPrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { alarmPrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val colors = MaterialTheme.colorScheme
    val navItems = remember {
        listOf(
            Triple("监控", Icons.Outlined.Sensors, "监控"),
            Triple("规则", Icons.Outlined.Tune, "规则"),
            Triple("记录", Icons.Outlined.History, "记录"),
            Triple("设置", Icons.Outlined.Settings, "设置")
        )
    }

    val overlayStyleLabel = when (overlayStyle) {
        OverlayAlertStyle.AUTO -> "自动"
        OverlayAlertStyle.TOP_BANNER -> "顶部"
        OverlayAlertStyle.CENTER_CARD -> "居中"
        OverlayAlertStyle.CORNER_COMPACT -> "角落"
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val wideLayout = maxWidth >= 660.dp

        if (wideLayout) {
            Row(Modifier.fillMaxSize()) {
                // 宽屏 4 格极简图标导航轨（唯一导航入口）
                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .fillMaxHeight()
                        .background(colors.surface)
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    navItems.forEach { (id, icon, label) ->
                        RailNavItem(icon = icon, label = label, selected = page == id) { page = id }
                    }
                }
                VerticalDivider(color = colors.outlineVariant)

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (page) {
                        "监控" -> MonitorPane(
                            wide = true,
                            serviceEnabled = serviceEnabled,
                            serviceState = serviceState,
                            keywordCount = keywordCount,
                            recordCount = recordCount,
                            overlayStyleLabel = overlayStyleLabel,
                            onToggle = onToggleLocal
                        )
                        "记录" -> AlertHistoryScreen(onNavigateBack = null, viewModel = settingsViewModel)
                        else -> Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(page, color = colors.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            settingsContent(page == "规则")
                        }
                    }
                }
            }
        } else {
            // 窄屏：顶栏（纯标题，无重复功能按钮）+ 内容区 + 4 格底部导航
            Column(Modifier.fillMaxSize()) {
                if (page != "记录") {
                    VigilTopBar(title = if (page == "监控") "Vigil" else page)
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        "监控" -> MonitorPane(
                            wide = false,
                            serviceEnabled = serviceEnabled,
                            serviceState = serviceState,
                            keywordCount = keywordCount,
                            recordCount = recordCount,
                            overlayStyleLabel = overlayStyleLabel,
                            onToggle = onToggleLocal
                        )
                        "记录" -> AlertHistoryScreen(onNavigateBack = null, viewModel = settingsViewModel)
                        else -> Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            settingsContent(page == "规则")
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                HorizontalDivider(color = colors.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEach { (id, icon, label) ->
                        BottomNavItem(icon = icon, label = label, selected = page == id) { page = id }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorPane(
    wide: Boolean,
    serviceEnabled: Boolean,
    serviceState: ServiceState,
    keywordCount: Int,
    recordCount: Int,
    overlayStyleLabel: String,
    onToggle: () -> Unit
) {
    val content: @Composable () -> Unit = {
        SentinelHeroRing(
            serviceEnabled = serviceEnabled,
            serviceState = serviceState,
            onToggle = onToggle
        )
    }

    val statusSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListenerRecoveryBanner(serviceState = serviceState)
            // 三联精密仪表条（居中对称、悬浮竖线分割、零多余标题，纯只读状态反馈）
            SentinelMetricBar(
                keywordCount = keywordCount,
                recordCount = recordCount,
                overlayStyleLabel = overlayStyleLabel,
                active = serviceEnabled
            )
        }
    }

    if (wide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
            Box(Modifier.weight(1f)) { statusSection() }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(8.dp))
            statusSection()
        }
    }
}

@Composable
private fun SentinelMetricBar(
    keywordCount: Int,
    recordCount: Int,
    overlayStyleLabel: String,
    active: Boolean
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricCell(
                icon = Icons.Outlined.Tune,
                label = "监控词",
                value = "$keywordCount",
                highlight = active && keywordCount > 0,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(colors.outlineVariant)
            )
            MetricCell(
                icon = Icons.Outlined.History,
                label = "记录",
                value = "$recordCount",
                highlight = false,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(colors.outlineVariant)
            )
            MetricCell(
                icon = Icons.Outlined.Layers,
                label = "浮窗",
                value = overlayStyleLabel,
                highlight = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCell(
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value,
            color = if (highlight) colors.primary else colors.onSurface,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                color = colors.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

/** 居中发光脉冲守望环：全应用唯一的监听总开关。 */
@Composable
private fun SentinelHeroRing(
    serviceEnabled: Boolean,
    serviceState: ServiceState,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isRunning = serviceEnabled && (serviceState == ServiceState.RUNNING || serviceState == ServiceState.RUNNING_LIMITED)
    val isWarning = serviceEnabled && serviceState in listOf(
        ServiceState.LISTENER_DISCONNECTED,
        ServiceState.HEARTBEAT_TIMEOUT,
        ServiceState.NO_PERMISSION
    )
    val ringColor = when {
        isWarning -> colors.error
        isRunning -> colors.primary
        serviceEnabled -> colors.primary.copy(alpha = 0.65f)
        else -> colors.outline
    }
    val statusText = when {
        !serviceEnabled -> "已停止"
        serviceState == ServiceState.RUNNING || serviceState == ServiceState.RUNNING_LIMITED -> "监听中"
        serviceState == ServiceState.NO_PERMISSION -> "未授权"
        serviceState == ServiceState.INITIALIZING -> "连接中"
        serviceState == ServiceState.HEARTBEAT_TIMEOUT -> "心跳超时"
        serviceState == ServiceState.LISTENER_DISCONNECTED -> "重连中"
        else -> "异常"
    }

    val pulseRaw = if (isRunning || isWarning) rememberFrameDrivenProgress(2200) else 0f
    val pulseTri = if (pulseRaw < 0.5f) pulseRaw * 2f else (1f - pulseRaw) * 2f
    val glowAlpha = if (isRunning || isWarning) 0.16f + 0.18f * pulseTri else 0.04f

    Box(
        modifier = Modifier
            .size(196.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = glowAlpha), Color.Transparent),
                    radius = radius
                ),
                radius = radius
            )
            drawCircle(
                color = ringColor.copy(alpha = if (isRunning || isWarning) 0.24f else 0.14f),
                radius = radius * 0.80f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = ringColor,
                radius = radius * 0.72f,
                style = Stroke(width = 3.5.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Outlined.WarningAmber else if (serviceEnabled) Icons.Outlined.Sensors else Icons.Outlined.NotificationsOff,
                contentDescription = statusText,
                tint = ringColor,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = statusText,
                color = if (isRunning || isWarning) colors.onBackground else colors.onSurfaceVariant,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ListenerRecoveryBanner(serviceState: ServiceState) {
    if (serviceState !in listOf(ServiceState.LISTENER_DISCONNECTED, ServiceState.HEARTBEAT_TIMEOUT, ServiceState.NO_PERMISSION)) return
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.errorContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (serviceState == ServiceState.NO_PERMISSION) "通知使用权未授予" else "监听连接异常",
                color = colors.onErrorContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (serviceState != ServiceState.NO_PERMISSION) {
                TextButton(onClick = { ListenerRecovery.startFastRecovery(context) }) {
                    Text("重试", color = colors.error)
                }
            }
            TextButton(onClick = { (context as? Activity)?.let(PermissionUtils::openNotificationListenerSettings) }) {
                Text("授权", color = colors.error)
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RailNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
