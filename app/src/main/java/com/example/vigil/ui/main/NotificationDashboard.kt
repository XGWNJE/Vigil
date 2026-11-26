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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.data.DelayMode
import com.example.vigil.data.DelayPolicy
import com.example.vigil.data.SharedPreferencesHelper
import com.example.vigil.ui.monitoring.ServiceState
import com.example.vigil.util.ListenerRecovery
import com.example.vigil.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 方案 B · 静默暗夜（Obsidian Sentinel）主仪表盘。
 *
 * 遵循「秩序、自适应、不说明」：
 * - 居中发光守望环直接承载主开关与状态（点按即启停，零废话副标题）；
 * - 紧凑数字胶囊直达关键词、默认铃声/次数、延时模式；
 * - 随容器空间在单栏与左右双栏之间自然自适应，不按固定机型硬切。
 */
@Composable
internal fun NotificationDashboard(
    serviceEnabled: Boolean,
    serviceState: ServiceState,
    keywordCount: Int,
    ringtone: String,
    loops: Int,
    delayPolicy: DelayPolicy,
    hasOverlayPermission: Boolean,
    onToggleLocal: () -> Unit,
    onRingtone: () -> Unit,
    onLoops: () -> Unit,
    onDelay: () -> Unit,
    onRequestOverlay: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    settingsContent: @Composable (Boolean) -> Unit
) {
    val context = LocalContext.current
    val alarmPrefs = remember { SharedPreferencesHelper(context) }
    var page by rememberSaveable { mutableStateOf("监控") }
    var records by remember { mutableStateOf(alarmPrefs.getAlertHistory()) }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "alert_history") records = alarmPrefs.getAlertHistory()
        }
        alarmPrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { alarmPrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val colors = MaterialTheme.colorScheme
    val delayShortLabel = when (delayPolicy.mode) {
        DelayMode.IMMEDIATE -> "即时"
        DelayMode.FIXED -> {
            val secs = (delayPolicy.fixedDelayMs / 1000L).coerceAtLeast(1L)
            if (secs >= 60L) "+${secs / 60}m" else "+${secs}s"
        }
        DelayMode.SCHEDULED -> "定点"
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 按容器实际容纳能力自适应，不按固定机型硬切
        val wideTwoPane = maxWidth >= 660.dp

        if (wideTwoPane) {
            Row(Modifier.fillMaxSize()) {
                // 宽屏极简图标导航轨（紧凑秩序，不占内容空间）
                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .fillMaxHeight()
                        .background(colors.surface)
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RailNavItem(Icons.Outlined.Sensors, "监控", page == "监控") { page = "监控" }
                    RailNavItem(Icons.Outlined.Tune, "规则", page == "规则") { page = "规则" }
                    RailNavItem(Icons.Outlined.History, "记录", false) { onHistory() }
                    RailNavItem(Icons.Outlined.MusicNote, "铃声", false) { onLibrary() }
                    Spacer(Modifier.weight(1f))
                    RailNavItem(Icons.Outlined.Settings, "设置", page == "设置") { page = "设置" }
                }
                VerticalDivider(color = colors.outlineVariant)

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    if (page == "监控") {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                Modifier
                                    .weight(0.95f)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                SentinelHeroRing(
                                    serviceEnabled = serviceEnabled,
                                    serviceState = serviceState,
                                    onToggle = onToggleLocal
                                )
                                SentinelStatusPills(
                                    keywordCount = keywordCount,
                                    ringtone = ringtone,
                                    loops = loops,
                                    delayLabel = delayShortLabel,
                                    onKeywords = { page = "规则" },
                                    onSound = onRingtone,
                                    onLoops = onLoops,
                                    onDelay = onDelay
                                )
                                RecoveryOrOverlayBanner(
                                    serviceState = serviceState,
                                    hasOverlayPermission = hasOverlayPermission,
                                    onRequestOverlay = onRequestOverlay
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1.05f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RecentAlertsCard(records = records, onViewAll = onHistory)
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = page,
                                color = colors.onBackground,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            settingsContent(page == "规则")
                        }
                    }
                }
            }
        } else {
            // 窄屏竖向单栏 + 极简 3 格底栏
            Column(Modifier.fillMaxSize()) {
                // 极简顶栏：品牌字标 + 设置入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (page == "监控") "Vigil" else page,
                        color = colors.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (page == "规则") {
                        IconButton(onClick = onLibrary) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = "铃声库", tint = colors.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { page = if (page == "设置") "监控" else "设置" }) {
                        Icon(
                            imageVector = if (page == "设置") Icons.Outlined.Close else Icons.Outlined.Settings,
                            contentDescription = "设置",
                            tint = if (page == "设置") colors.primary else colors.onSurfaceVariant
                        )
                    }
                }

                // 主内容区
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (page == "监控") {
                        Spacer(Modifier.height(4.dp))
                        SentinelHeroRing(
                            serviceEnabled = serviceEnabled,
                            serviceState = serviceState,
                            onToggle = onToggleLocal
                        )
                        SentinelStatusPills(
                            keywordCount = keywordCount,
                            ringtone = ringtone,
                            loops = loops,
                            delayLabel = delayShortLabel,
                            onKeywords = { page = "规则" },
                            onSound = onRingtone,
                            onLoops = onLoops,
                            onDelay = onDelay
                        )
                        RecoveryOrOverlayBanner(
                            serviceState = serviceState,
                            hasOverlayPermission = hasOverlayPermission,
                            onRequestOverlay = onRequestOverlay
                        )
                        RecentAlertsCard(records = records, onViewAll = onHistory)
                        Spacer(Modifier.height(8.dp))
                    } else {
                        settingsContent(page == "规则")
                    }
                }

                // 极简 3 格底栏：监控 / 规则 / 记录
                HorizontalDivider(color = colors.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavItem(Icons.Outlined.Sensors, "监控", page == "监控") { page = "监控" }
                    BottomNavItem(Icons.Outlined.Tune, "规则", page == "规则") { page = "规则" }
                    BottomNavItem(Icons.Outlined.History, "记录", false) { onHistory() }
                }
            }
        }
    }
}

/** 方案 B 核心标志：居中发光脉冲守望环（点按直接启停监听）。 */
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

    // 仅在运行或告警态启用帧驱动呼吸光晕
    val pulseRaw = if (isRunning || isWarning) rememberFrameDrivenProgress(2200) else 0f
    val pulseTri = if (pulseRaw < 0.5f) pulseRaw * 2f else (1f - pulseRaw) * 2f
    val glowAlpha = if (isRunning || isWarning) 0.14f + 0.18f * pulseTri else 0.04f

    Box(
        modifier = Modifier
            .size(188.dp)
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
                color = ringColor.copy(alpha = if (isRunning || isWarning) 0.22f else 0.12f),
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
                modifier = Modifier.size(28.dp)
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

/** 守望环下方的紧凑等宽数字胶囊：零说明文字，直接点击调节。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentinelStatusPills(
    keywordCount: Int,
    ringtone: String,
    loops: Int,
    delayLabel: String,
    onKeywords: () -> Unit,
    onSound: () -> Unit,
    onLoops: () -> Unit,
    onDelay: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusPill(text = "$keywordCount 词", onClick = onKeywords)
        StatusPill(text = ringtone, onClick = onSound, maxChars = 10)
        StatusPill(text = "${loops}×", onClick = onLoops, mono = true)
        StatusPill(text = delayLabel, onClick = onDelay, mono = true)
    }
}

@Composable
private fun StatusPill(
    text: String,
    onClick: () -> Unit,
    mono: Boolean = false,
    maxChars: Int = 16
) {
    val colors = MaterialTheme.colorScheme
    val display = if (text.length > maxChars) text.take(maxChars - 1) + "…" else text
    Surface(
        onClick = onClick,
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Text(
            text = display,
            color = colors.onSurface,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

/** 断连恢复入口 & 应用外浮窗权限一键引导（仅在异常或未授权时出现）。 */
@Composable
private fun RecoveryOrOverlayBanner(
    serviceState: ServiceState,
    hasOverlayPermission: Boolean,
    onRequestOverlay: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    if (serviceState in listOf(ServiceState.LISTENER_DISCONNECTED, ServiceState.HEARTBEAT_TIMEOUT, ServiceState.NO_PERMISSION)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.errorContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (serviceState == ServiceState.NO_PERMISSION) "通知使用权未授予" else "监听未连接",
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

    if (!hasOverlayPermission) {
        Surface(
            onClick = onRequestOverlay,
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Layers, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "应用外告警浮窗 · 去授权",
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 极简最近报警卡片。 */
@Composable
private fun RecentAlertsCard(
    records: List<com.example.vigil.data.AlertRecord>,
    onViewAll: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最近报警",
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("全部", color = colors.primary, fontSize = 13.sp)
                }
            }

            if (records.isEmpty()) {
                Text(
                    text = "暂无记录",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
            } else {
                records.take(4).forEachIndexed { idx, record ->
                    if (idx > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
                    else Spacer(Modifier.height(6.dp))
                    val timeText = remember(record.timestamp) {
                        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(record.timestamp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = record.keyword,
                                color = colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = record.sourceApp ?: "本机通知",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = timeText,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
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
            .padding(horizontal = 20.dp, vertical = 6.dp),
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
