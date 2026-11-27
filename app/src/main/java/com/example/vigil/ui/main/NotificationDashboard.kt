package com.example.vigil.ui.main

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.R
import com.example.vigil.data.OverlayAlertStyle
import com.example.vigil.data.SharedPreferencesHelper
import com.example.vigil.ui.components.VigilTopBar
import com.example.vigil.ui.history.AlertHistoryScreen
import com.example.vigil.ui.monitoring.ServiceState
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.util.ListenerRecovery
import com.example.vigil.util.PermissionUtils
import kotlinx.coroutines.delay

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
    isAlerting: Boolean,
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
    ) {
        val wideLayout = maxWidth >= 660.dp

        if (wideLayout) {
            Row(Modifier.fillMaxSize()) {
                // 宽屏 4 格极简图标导航轨（唯一导航入口）
                Column(
                    modifier = Modifier
                        .background(colors.surface)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .width(68.dp)
                        .fillMaxHeight()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    navItems.forEach { (id, icon, label) ->
                        RailNavItem(icon = icon, label = label, selected = page == id) { page = id }
                    }
                }
                VerticalDivider(color = colors.outlineVariant)

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    when (page) {
                        "监控" -> MonitorPane(
                            wide = true,
                            serviceEnabled = serviceEnabled,
                            serviceState = serviceState,
                            isAlerting = isAlerting,
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
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                if (page != "记录") {
                    VigilTopBar(title = if (page == "监控") "Vigil" else page)
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        "监控" -> MonitorPane(
                            wide = false,
                            serviceEnabled = serviceEnabled,
                            serviceState = serviceState,
                            isAlerting = isAlerting,
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
                        .navigationBarsPadding()
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
    isAlerting: Boolean,
    keywordCount: Int,
    recordCount: Int,
    overlayStyleLabel: String,
    onToggle: () -> Unit
) {
    val content: @Composable () -> Unit = {
        SentinelHeroRing(
            serviceEnabled = serviceEnabled,
            serviceState = serviceState,
            isAlerting = isAlerting,
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
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
            statusSection()
            Spacer(Modifier.height(6.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 340.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp),
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
                .height(16.dp)
                .background(colors.outlineVariant.copy(alpha = 0.55f))
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
                .height(16.dp)
                .background(colors.outlineVariant.copy(alpha = 0.55f))
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
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            color = if (highlight) colors.primary else colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                color = colors.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

private enum class OwlButtonState(
    val resId: Int,
    val cols: Int,
    val frameCount: Int,
    val cycleMs: Int
) {
    IDLE(R.drawable.owl_strip_idle, cols = 10, frameCount = 60, cycleMs = 2500),
    PRESS(R.drawable.owl_strip_press, cols = 8, frameCount = 79, cycleMs = 1350),
    CONNECTING(R.drawable.owl_strip_connecting, cols = 4, frameCount = 16, cycleMs = 1150),
    WATCHING(R.drawable.owl_strip_watching, cols = 15, frameCount = 220, cycleMs = 8800),
    WARNING(R.drawable.owl_strip_warning, cols = 10, frameCount = 60, cycleMs = 2400),
    ALARM(R.drawable.owl_strip_alarm, cols = 8, frameCount = 94, cycleMs = 3615)
}

/** 居中发光脉冲守望环（像素猫头鹰多状态透明序列动图 + 60fps 物理弹性光环）：全应用唯一的监听总开关。 */
@Composable
private fun SentinelHeroRing(
    serviceEnabled: Boolean,
    serviceState: ServiceState,
    isAlerting: Boolean,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isRunning = serviceEnabled && (serviceState == ServiceState.RUNNING || serviceState == ServiceState.RUNNING_LIMITED)
    val isWarning = serviceEnabled && serviceState in listOf(
        ServiceState.LISTENER_DISCONNECTED,
        ServiceState.HEARTBEAT_TIMEOUT,
        ServiceState.NO_PERMISSION
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var tapBounceActive by remember { mutableStateOf(false) }
    var pressNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(tapBounceActive, pressNonce) {
        if (tapBounceActive) {
            delay(OwlButtonState.PRESS.cycleMs.toLong())
            tapBounceActive = false
        }
    }

    val owlState = when {
        isAlerting -> OwlButtonState.ALARM
        isPressed || tapBounceActive -> OwlButtonState.PRESS
        isWarning -> OwlButtonState.WARNING
        isRunning -> OwlButtonState.WATCHING
        serviceEnabled -> OwlButtonState.CONNECTING
        else -> OwlButtonState.IDLE
    }

    val ringColor = when {
        isAlerting || isWarning -> colors.error
        isRunning -> colors.primary
        serviceEnabled -> colors.primary.copy(alpha = 0.75f)
        else -> colors.outline
    }
    val statusText = when {
        isAlerting -> "报警中"
        !serviceEnabled -> "已停止"
        serviceState == ServiceState.RUNNING || serviceState == ServiceState.RUNNING_LIMITED -> "监听中"
        serviceState == ServiceState.NO_PERMISSION -> "未授权"
        serviceState == ServiceState.INITIALIZING -> "连接中"
        serviceState == ServiceState.HEARTBEAT_TIMEOUT -> "心跳超时"
        serviceState == ServiceState.LISTENER_DISCONNECTED -> "重连中"
        else -> "异常"
    }

    val pulseRaw = if (isRunning || isWarning || isAlerting) rememberFrameDrivenProgress(if (isAlerting) 900 else 2600) else 0f
    val pulseTri = if (pulseRaw < 0.5f) pulseRaw * 2f else (1f - pulseRaw) * 2f
    val glowAlpha = when {
        isAlerting -> 0.14f + 0.10f * pulseTri
        isRunning || isWarning -> 0.07f + 0.06f * pulseTri
        else -> 0.025f
    }

    // 按需解码并缓存 2D 图集，预热高频状态，避免启动瞬间同时解码全部大图
    val resources = LocalContext.current.resources
    val atlasCache = remember { mutableMapOf<OwlButtonState, ImageBitmap>() }
    LaunchedEffect(Unit) {
        for (state in listOf(OwlButtonState.IDLE, OwlButtonState.PRESS, OwlButtonState.WATCHING)) {
            atlasCache.getOrPut(state) { ImageBitmap.imageResource(resources, state.resId) }
        }
    }
    val activeAtlas = atlasCache.getOrPut(owlState) {
        ImageBitmap.imageResource(resources, owlState.resId)
    }

    val spriteProgress = rememberFrameDrivenProgress(owlState.cycleMs, resetKey = owlState to pressNonce)
    val frameIndex = ((spriteProgress * owlState.frameCount).toInt()).coerceIn(0, owlState.frameCount - 1)

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else if (tapBounceActive) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "HeroRingScale"
    )

    Box(
        modifier = Modifier
            .size(220.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    pressNonce++
                    tapBounceActive = true
                    onToggle()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(156.dp),
                contentAlignment = Alignment.Center
            ) {
                // 紧贴猫头鹰轮廓的克制光晕（略偏下并做轻微椭圆压缩以贴合躯干轮廓）
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.0f
                            scaleY = 0.90f
                            translationY = size.height * 0.06f
                        }
                ) {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to ringColor.copy(alpha = glowAlpha),
                            0.50f to ringColor.copy(alpha = glowAlpha * 0.38f),
                            0.80f to ringColor.copy(alpha = glowAlpha * 0.08f),
                            1.0f to Color.Transparent,
                            radius = radius
                        ),
                        radius = radius
                    )
                }

                Canvas(modifier = Modifier.size(148.dp)) {
                    val frameSize = activeAtlas.width / owlState.cols
                    val col = frameIndex % owlState.cols
                    val row = frameIndex / owlState.cols
                    val dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    drawImage(
                        image = activeAtlas,
                        srcOffset = IntOffset(col * frameSize, row * frameSize),
                        srcSize = IntSize(frameSize, frameSize),
                        dstOffset = IntOffset.Zero,
                        dstSize = dstSize,
                        filterQuality = FilterQuality.None
                    )
                }
            }
            Text(
                text = statusText,
                color = if (isRunning || isWarning || isAlerting) colors.onBackground else colors.onSurfaceVariant,
                fontSize = 16.sp,
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
