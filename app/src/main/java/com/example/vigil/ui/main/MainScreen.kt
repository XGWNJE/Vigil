// src/main/java/com/example/vigil/ui/main/MainScreen.kt
package com.example.vigil.ui.main

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.MainActivity
import com.example.vigil.ListenerRecovery
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.RingtoneLibrary
import com.example.vigil.SharedPreferencesHelper
import com.example.vigil.UpdateChecker
import com.example.vigil.UpdateViewModel
import com.example.vigil.VigilEventBus
import com.example.vigil.VigilLogger
import com.example.vigil.ui.dialogs.PermissionGuideDialog
import com.example.vigil.ui.monitoring.MonitoringViewModel
import com.example.vigil.ui.monitoring.ServiceState
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.ui.theme.VigilDirAAcid
import com.example.vigil.ui.theme.VigilDirAAmber
import com.example.vigil.ui.theme.VigilDirABg
import com.example.vigil.ui.theme.VigilDirADim
import com.example.vigil.ui.theme.VigilDirAFaint
import com.example.vigil.ui.theme.VigilDirAInk
import com.example.vigil.ui.theme.VigilDirALine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「A · 一线」单页主屏：状态即首页。
 * 全屏涟漪（RippleBackground）从中央核心开关扩散，涟漪颜色/节奏即服务状态；
 * 设置项收进底部浮出 Sheet（顶栏齿轮入口）。
 * 两个 ViewModel 由 MainActivity 传入（与原来双屏共用同一实例），本屏只做 UI 接线。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    monitoringViewModel: MonitoringViewModel,
    settingsViewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel,
    onNavigateToAppFilter: () -> Unit,
    onNavigateToAlertHistory: () -> Unit,
    onNavigateToRingtoneLibrary: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // 当前版本名（更新检查 / 版本号文本展示用）
    val currentVersionName = remember { UpdateChecker.currentVersionName(context) }

    val serviceState by monitoringViewModel.serviceState
    val serviceEnabled by monitoringViewModel.serviceEnabled
    val listenerRecoveryFailed by monitoringViewModel.listenerRecoveryFailed

    val keywordList = settingsViewModel.keywordList
    val selectedRingtoneName by settingsViewModel.selectedRingtoneName
    val isAppFilterEnabled by settingsViewModel.isAppFilterEnabled
    val hasNotificationAccess by settingsViewModel.hasNotificationAccess
    val isIgnoringBatteryOptimizations by settingsViewModel.isIgnoringBatteryOptimizations

    // 心跳：UI 侧直接收集事件总线（payload 为服务发射时刻，replay=1，冷启动立即有值）
    var lastHeartbeatAt by remember { mutableLongStateOf(0L) }
    var nowTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        launch {
            VigilEventBus.heartbeat.collect { beatAt ->
                lastHeartbeatAt = beatAt
            }
        }
        while (true) {
            nowTick = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val heartbeatText = if (lastHeartbeatAt == 0L) {
        "HEARTBEAT --"
    } else {
        val seconds = (nowTick - lastHeartbeatAt) / 1000
        when {
            seconds < 60 -> "HEARTBEAT ${seconds}s AGO"
            seconds < 3600 -> "HEARTBEAT ${seconds / 60}m AGO"
            else -> "HEARTBEAT ${seconds / 3600}h AGO"
        }
    }

    // 铃声选择器（逻辑搬自原 SettingsScreen）
    // ringtonePickTarget 非空时表示正在为某个关键词选铃声，null = 全局默认铃声
    var ringtonePickTarget by remember { mutableStateOf<String?>(null) }
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val target = ringtonePickTarget
            if (target == null) {
                settingsViewModel.onRingtoneUriSelected(uri)
            } else {
                settingsViewModel.onKeywordRingtoneSelected(target, uri?.toString())
            }
        }
        ringtonePickTarget = null
    }

    // 打开系统铃声选择器：target 为 null 选默认铃声，否则为指定关键词选
    val launchRingtonePicker: (String?) -> Unit = { target ->
        ringtonePickTarget = target
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择报警铃声")
            val existing = target?.let { settingsViewModel.getKeywordRingtoneUri(it) }
                ?: settingsViewModel.selectedRingtoneUri.value
            existing?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it) }
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        }
        try {
            ringtonePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开铃声选择器: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf<PermissionGuide?>(null) }
    // 设置面板开关：rememberSaveable——从面板进子页（主屏出组合）再返回时自动恢复展开，
    // 实现「返回一次只退一个层级」：子页 → 面板 → 主屏
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // 面板跟手进度：0=收起 1=全展开；拖动中同步改值实时贴手指，松手后 animate 吸附。
    // 不用 Animatable：drag 高频 launch snapTo 会在互斥锁上排队/与吸附动画互相取消，状态竞态。
    var sheetProgress by remember { mutableFloatStateOf(0f) }
    // 拖动结束信号：每次松手/取消自增。sheetOpen 未变化时 LaunchedEffect 不会重触发，
    // 低于阈值的拖动会把面板卡在半开——用它保证松手后吸附动画无条件执行。
    var sheetSnapNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(sheetOpen, sheetSnapNonce) {
        // 磁铁吸附段：FastOutSlowInEasing 非线性弹出/收回
        animate(
            initialValue = sheetProgress,
            targetValue = if (sheetOpen) 1f else 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) { value, _ -> sheetProgress = value }
    }
    // 面板像素高（屏高 0.85），drag 距离↔进度换算与 translationY 都用它
    var panelHeightPx by remember { mutableFloatStateOf(1f) }
    // 关键词删除二次确认：待删除的关键词，非空时弹出确认框
    var keywordPendingDelete by remember { mutableStateOf<String?>(null) }
    // 关键词个性化配置弹窗：待配置的关键词，非空时弹出（铃声 + 循环次数）
    var keywordPendingConfig by remember { mutableStateOf<String?>(null) }
    // 循环次数档位弹窗：null=不显示；""=全局默认档位；非空=该关键词的覆盖档位
    var loopCountDialogTarget by remember { mutableStateOf<String?>(null) }
    // 铃声选择弹窗：null=不显示；""=默认铃声；非空=该关键词的铃声
    var ringtoneSelectTarget by remember { mutableStateOf<String?>(null) }
    val defaultLoopCount by settingsViewModel.defaultLoopCount
    // 关键词配置变化信号（读取即订阅，chip 弹窗里的值随配置保存即时刷新）
    val keywordConfigVersion by settingsViewModel.keywordConfigVersion

    // 入口提示与引导行的持久化状态（纯 UI 标记，与服务/ViewModel 无关，直接读 prefs）
    val prefs = remember { SharedPreferencesHelper(context) }
    var swipeHintShown by remember { mutableStateOf(prefs.hasShownSwipeHint()) }
    var lockTaskTipDismissed by remember { mutableStateOf(prefs.isLockTaskTipDismissed()) }
    // 设置面板被打开过一次（任意入口）即视为已发现入口，收起手势提示文案
    LaunchedEffect(sheetOpen) {
        if (sheetOpen && !swipeHintShown) {
            prefs.markSwipeHintShown()
            swipeHintShown = true
        }
    }

    // 返回键/侧边返回手势：面板展开时先收面板，一次只退一个层级
    BackHandler(enabled = sheetOpen) {
        sheetOpen = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VigilDirABg)
            .onSizeChanged { panelHeightPx = it.height * 0.85f }
            // 磁铁式上滑开面板：前段跟手，进度越过 35% 意图阈值立即交非线性动画吸附全开，
            // 之后本次拖动不再跟手；未越阈值则松手时按进度吸附开/关。
            .pointerInput(Unit) {
                var committed = false
                detectVerticalDragGestures(
                    onDragStart = { committed = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (committed) return@detectVerticalDragGestures
                        sheetProgress = (sheetProgress - dragAmount / panelHeightPx).coerceIn(0f, 1f)
                        if (sheetProgress >= 0.35f) {
                            committed = true
                            sheetOpen = true
                        }
                    },
                    onDragEnd = {
                        if (!committed) sheetOpen = sheetProgress > 0.35f
                        sheetSnapNonce++
                    },
                    onDragCancel = {
                        if (!committed) sheetOpen = sheetProgress > 0.35f
                        sheetSnapNonce++
                    }
                )
            }
    ) {
        // ---- 底层：全屏涟漪，从中央核心开关扩散，颜色/节奏即状态 ----
        RippleBackground(state = serviceState, modifier = Modifier.fillMaxSize())

        // ---- 顶栏：字标 + 心跳 + 设置齿轮（statusBarsPadding 避让状态栏） ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VIGIL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.7.sp, // ≈ .18em
                    color = VigilDirAInk
                )
                Text(
                    text = "_",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.7.sp,
                    color = VigilDirAAcid
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = heartbeatText,
                    style = MonoTextStyle,
                    fontSize = 10.sp,
                    color = VigilDirADim,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                // 齿轮：18dp 字形 + 48dp 触控热区（右端与页面右边距对齐）
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { sheetOpen = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = VigilDirADim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ---- 状态词：悬浮于核心开关正上方；状态切换时交叉淡入 + 轻微上浮 ----
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-148).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val stateText = when (serviceState) {
                ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> "监听中"
                ServiceState.DISABLED -> "已停止"
                ServiceState.NO_PERMISSION -> "未授权"
                ServiceState.INITIALIZING -> "连接中"
                ServiceState.LISTENER_DISCONNECTED -> "重连中"
                ServiceState.HEARTBEAT_TIMEOUT -> "心跳超时"
                ServiceState.ERROR -> "异常"
            }
            AnimatedContent(
                targetState = stateText,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 })
                        .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 })
                },
                label = "stateWord"
            ) { word ->
                Text(
                    text = word,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = VigilDirAInk
                )
            }
            Text(
                text = "${keywordList.size} KEYWORDS",
                style = MonoTextStyle,
                fontSize = 10.sp,
                color = VigilDirADim,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            // ---- 自动重连失败逃生通道：看门狗完整重连序列也无效时出现 ----
            // HyperOS 等 ROM 上 requestRebind 会被系统静默忽略，唯一可靠恢复是
            // 系统级撤销+重新授权（用户卸载重装/清数据即此效果），这里直接给出入口
            if (serviceState == ServiceState.LISTENER_DISCONNECTED && listenerRecoveryFailed) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "自动重连失败 · 需重新授权",
                        style = MonoTextStyle,
                        fontSize = 10.sp,
                        letterSpacing = 1.0.sp,
                        color = VigilDirAAmber
                    )
                    Row(horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = {
                            Toast.makeText(context, "已尝试重连", Toast.LENGTH_SHORT).show()
                            ListenerRecovery.startFastRecovery(context)
                        }) {
                            Text("立即重试", style = MonoTextStyle, fontSize = 12.sp, color = VigilDirAInk)
                        }
                        TextButton(onClick = {
                            activity?.let { PermissionUtils.openNotificationListenerSettings(it) }
                        }) {
                            Text("重新授权", style = MonoTextStyle, fontSize = 12.sp, color = VigilDirAAcid)
                        }
                    }
                }
            }
        }

        // ---- 核心开关：涟漪环心，点按切换服务 ----
        CoreSwitch(
            enabled = serviceEnabled,
            modifier = Modifier.align(Alignment.Center),
            onClick = {
                val targetEnabled = !serviceEnabled
                if (targetEnabled && !hasNotificationAccess) {
                    Toast.makeText(context, "请先授予通知使用权", Toast.LENGTH_SHORT).show()
                    return@CoreSwitch
                }
                monitoringViewModel.onServiceEnabledChange(
                    enabled = targetEnabled,
                    startServiceCallback = { hasPermission ->
                        (activity as? MainActivity)?.startVigilService(hasPermission)
                    },
                    stopServiceCallback = {
                        (activity as? MainActivity)?.stopVigilService()
                    }
                )
            }
        )

        // ---- SERVICE ON/OFF：核心正下方；颜色随开关平滑过渡 ----
        val serviceLabelColor by animateColorAsState(
            targetValue = if (serviceEnabled) VigilDirAAcid else VigilDirADim,
            animationSpec = tween(400),
            label = "serviceLabelColor"
        )
        Text(
            text = if (serviceEnabled) "SERVICE ON" else "SERVICE OFF",
            style = MonoTextStyle,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = serviceLabelColor,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 64.dp)
        )

        // ---- 底部把手：上滑手势的视觉锚点（点按也能打开设置 Sheet），避让手势导航条 ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .padding(vertical = 12.dp)
                .clickable { sheetOpen = true },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!swipeHintShown) {
                Text(
                    text = "上滑打开设置",
                    style = MonoTextStyle,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = VigilDirADim,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(1.dp)
                    .background(VigilDirAFaint)
            )
        }
    }

    // ---- 设置面板：跟手底部浮出（进度驱动，拖动实时贴手指） ----
    if (sheetProgress > 0.001f) {
        Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：透明度随进度，展开后才拦截点击（点遮罩收面板）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f * sheetProgress))
                .clickable(enabled = sheetProgress > 0.5f) { sheetOpen = false }
        )
        // 面板本体：translationY 随进度，松手吸附
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = (1f - sheetProgress) * panelHeightPx }
                .background(VigilDirABg)
        ) {
            // 顶部把手：视觉锚点 + 下拉跟手收面板的热区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    // 对称的磁铁式下拉关面板：跟手到越过 65% 进度即交动画吸附关闭
                    .pointerInput(Unit) {
                        var committed = false
                        detectVerticalDragGestures(
                            onDragStart = { committed = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (committed) return@detectVerticalDragGestures
                                sheetProgress = (sheetProgress - dragAmount / panelHeightPx).coerceIn(0f, 1f)
                                if (sheetProgress <= 1f - 0.35f) {
                                    committed = true
                                    sheetOpen = false
                                }
                            },
                            onDragEnd = {
                                if (!committed) sheetOpen = sheetProgress > 0.35f
                                sheetSnapNonce++
                            },
                            onDragCancel = {
                                if (!committed) sheetOpen = sheetProgress > 0.35f
                                sheetSnapNonce++
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(VigilDirAFaint, RoundedCornerShape(2.dp))
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
            ) {
                // 列表顶部发丝线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(VigilDirALine)
                )
                // 1. 关键词
                LineRow {
                    RowLabel("关键词")
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        keywordList.forEach { keyword ->
                            KeywordChip(
                                text = keyword,
                                onClick = { keywordPendingConfig = keyword },
                                onRemove = { keywordPendingDelete = keyword }
                            )
                        }
                        AddChip(onClick = { showAddKeywordDialog = true })
                    }
                }

                // 2. 默认铃声（未单独配置的关键词统一使用；系统铃声或铃声库文件）
                LineRow(onClick = { ringtoneSelectTarget = "" }) {
                    RowLabel("默认铃声")
                    RowValue(selectedRingtoneName, withArrow = true)
                }

                // 3. 循环次数：1..10；关键词可在 chip 弹窗里逐条覆盖
                LineRow(onClick = { loopCountDialogTarget = "" }) {
                    RowLabel("循环次数")
                    RowValue(formatLoopCount(defaultLoopCount), withArrow = true)
                }

                // 4. 铃声库（自定义铃声来源：导入音频 / 录音，支持命名、删除、试听）
                LineRow(onClick = onNavigateToRingtoneLibrary) {
                    RowLabel("铃声库")
                    RowValue("导入 / 录音", withArrow = true)
                }

                // 5. 报警记录
                LineRow(onClick = onNavigateToAlertHistory) {
                    RowLabel("报警记录")
                    RowValue("查看历史", withArrow = true)
                }

                // 6. 应用过滤
                LineRow(onClick = onNavigateToAppFilter) {
                    RowLabel("应用过滤")
                    RowValue(if (isAppFilterEnabled) "仅指定应用" else "全部应用", withArrow = true)
                }

                // ---- 权限与保活：按必需性分级（必需 / 推荐 / 可选） ----
                GroupHeader("REQUIRED")

                // 通知使用权：功能前提，未开启则监听不存在
                LineRow(onClick = {
                    if (!hasNotificationAccess) {
                        showGuideDialog = PermissionGuide.NotificationAccess
                    }
                }) {
                    RowLabel("通知使用权")
                    if (hasNotificationAccess) {
                        GrantedValue()
                    } else {
                        WarnCapsule()
                    }
                }

                GroupHeader("RECOMMENDED")

                // 电池白名单：后台稳定检测的前提（Device Guard 类强杀防护），状态可检测
                LineRow(onClick = {
                    if (!isIgnoringBatteryOptimizations) {
                        showGuideDialog = PermissionGuide.Battery
                    }
                }) {
                    RowLabel("电池白名单")
                    if (isIgnoringBatteryOptimizations) {
                        GrantedValue()
                    } else {
                        WarnCapsule()
                    }
                }

                // 锁定任务卡片：防一键清理，无 API 可检测/跳转，纯图文引导（可「不再提示」关闭本行）
                if (!lockTaskTipDismissed) {
                    LineRow(onClick = {
                        showGuideDialog = PermissionGuide.LockTask
                    }) {
                        RowLabel("锁定任务卡片")
                        RowValue("如何锁定", withArrow = true)
                    }
                }

                GroupHeader("OPTIONAL")

                // 自启动管理（国产 ROM 通用引导，跳转系统对应设置页；状态不可检测）
                LineRow(onClick = {
                    activity?.let { PermissionUtils.openAutoStartSettings(it) }
                }) {
                    RowLabel("自启动管理")
                    RowValue("允许自启动", withArrow = true)
                }

                // 后台运行（省电策略/耗电管理设为无限制；状态不可检测）
                LineRow(onClick = {
                    showGuideDialog = PermissionGuide.BackgroundRun
                }) {
                    RowLabel("后台运行")
                    RowValue("设为无限制", withArrow = true)
                }

                // 8. 导出日志（真机长测诊断：拼接诊断头 + 本地持久化日志，调起系统分享）
                LineRow(onClick = {
                    try {
                        val exportFile = VigilLogger.export(context)
                        val uri = FileProvider.getUriForFile(
                            context, "com.example.vigil.fileprovider", exportFile
                        )
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Vigil 诊断日志")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "发送诊断日志"))
                        VigilLogger.i(context, "MainScreen", "日志已导出并调起分享: ${exportFile.name}")
                    } catch (e: Exception) {
                        VigilLogger.e(context, "MainScreen", "导出日志失败", e)
                        Toast.makeText(context, "导出日志失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }) {
                    RowLabel("导出日志")
                    RowValue("分享诊断日志", withArrow = true)
                }

                // 9. 开源地址（GitHub 仓库，跳转浏览器）
                LineRow(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/XGWNJE/Vigil"))
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开浏览器: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    RowLabel("开源地址")
                    RowValue("GitHub", withArrow = true)
                }

                // 10. 检查更新 / 版本号文本：点击重新检查（冷启动已自动检查过，此处手动触发）
                LineRow(onClick = { updateViewModel.checkForUpdate(isColdStart = false) }) {
                    RowLabel("检查更新")
                    RowValue("版本 v$currentVersionName", withArrow = true)
                }
            }
        }
        }
    }

    if (showAddKeywordDialog) {
        AddKeywordDialog(
            onAdd = {
                settingsViewModel.addKeyword(it)
                showAddKeywordDialog = false
            },
            onDismiss = { showAddKeywordDialog = false }
        )
    }

    // 关键词删除二次确认
    keywordPendingDelete?.let { keyword ->
        DeleteKeywordDialog(
            keyword = keyword,
            onConfirm = {
                settingsViewModel.removeKeyword(keyword)
                keywordPendingDelete = null
            },
            onDismiss = { keywordPendingDelete = null }
        )
    }

    // 关键词个性化配置弹窗（铃声 + 循环次数覆盖）
    keywordPendingConfig?.let { keyword ->
        KeywordConfigDialog(
            keyword = keyword,
            ringtoneName = settingsViewModel.getKeywordRingtoneName(keyword),
            loopCountOverride = settingsViewModel.getKeywordLoopCount(keyword),
            defaultLoopCount = defaultLoopCount,
            onPickRingtone = { ringtoneSelectTarget = keyword },
            onClearRingtone = { settingsViewModel.onKeywordRingtoneSelected(keyword, null) },
            onPickLoopCount = { loopCountDialogTarget = keyword },
            onDismiss = { keywordPendingConfig = null }
        )
    }

    // 铃声选择弹窗（系统铃声或铃声库文件；默认铃声与关键词共用）
    ringtoneSelectTarget?.let { target ->
        val isDefault = target.isEmpty()
        val keyword = target.takeIf { !isDefault }
        val currentValue = if (isDefault) {
            prefs.getRingtoneValue()
        } else {
            prefs.getKeywordRingtoneValue(target)
        }
        RingtoneSelectDialog(
            libraryEntries = settingsViewModel.ringtoneLibrary,
            currentValue = currentValue,
            onPickPreset = { preset ->
                val value = RingtoneLibrary.presetValue(context, preset)
                if (isDefault) {
                    settingsViewModel.onRingtoneValueSelected(value)
                } else {
                    settingsViewModel.onKeywordRingtoneSelected(target, value)
                }
                ringtoneSelectTarget = null
            },
            onPickSystemRingtone = {
                ringtoneSelectTarget = null
                launchRingtonePicker(keyword)
            },
            onPickLibraryFile = { fileName ->
                val path = java.io.File(RingtoneLibrary.libraryDir(context), fileName).absolutePath
                if (isDefault) {
                    settingsViewModel.onRingtoneValueSelected(path)
                } else {
                    settingsViewModel.onKeywordRingtoneSelected(target, path)
                }
                ringtoneSelectTarget = null
            },
            onDismiss = { ringtoneSelectTarget = null }
        )
    }

    // 循环次数档位弹窗（全局默认与关键词覆盖共用；关键词多一档「跟随默认」）
    loopCountDialogTarget?.let { target ->
        val isGlobal = target.isEmpty()
        LoopCountDialog(
            selected = if (isGlobal) defaultLoopCount else settingsViewModel.getKeywordLoopCount(target),
            defaultValue = defaultLoopCount,
            showFollowDefault = !isGlobal,
            onSelect = { count ->
                if (isGlobal) {
                    settingsViewModel.onDefaultLoopCountSelected(count ?: SharedPreferencesHelper.DEFAULT_LOOP_COUNT)
                } else {
                    settingsViewModel.onKeywordLoopCountSelected(target, count)
                }
                loopCountDialogTarget = null
            },
            onDismiss = { loopCountDialogTarget = null }
        )
    }

    // 权限引导弹窗（DirA 风格，替代原系统 AlertDialog）
    when (showGuideDialog) {
        PermissionGuide.NotificationAccess -> PermissionGuideDialog(
            title = stringResource(R.string.notification_permission_dialog_title),
            message = stringResource(R.string.notification_permission_dialog_message),
            onConfirm = { activity?.let { PermissionUtils.openNotificationListenerSettings(it) } },
            onDismiss = { showGuideDialog = null }
        )
        PermissionGuide.Battery -> PermissionGuideDialog(
            title = stringResource(R.string.battery_optimization_dialog_title),
            message = stringResource(R.string.battery_optimization_dialog_message),
            onConfirm = { activity?.let { PermissionUtils.requestIgnoreBatteryOptimizations(it) } },
            onDismiss = { showGuideDialog = null }
        )
        PermissionGuide.BackgroundRun -> PermissionGuideDialog(
            title = stringResource(R.string.background_run_dialog_title),
            message = stringResource(R.string.background_run_dialog_message),
            onConfirm = { activity?.let { PermissionUtils.openAppDetailsSettings(it) } },
            onDismiss = { showGuideDialog = null }
        )
        PermissionGuide.LockTask -> LockTaskGuideDialog(
            onClose = { showGuideDialog = null },
            onNeverShow = {
                prefs.markLockTaskTipDismissed()
                lockTaskTipDismissed = true
                showGuideDialog = null
            }
        )
        null -> Unit
    }
}

/** 主屏权限引导弹窗的种类 */
private enum class PermissionGuide {
    NotificationAccess,
    Battery,
    BackgroundRun,
    LockTask
}

private val MonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace)

/**
 * 核心开关：涟漪的环心。
 * ON：酸橙绿描边 + 实心圆点呼吸缩放；OFF：暗灰描边 + 静止暗点。
 */
@Composable
private fun CoreSwitch(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val ringColor by animateColorAsState(
        targetValue = if (enabled) VigilDirAAcid else VigilDirAFaint,
        animationSpec = tween(durationMillis = 400),
        label = "coreColor"
    )
    // 呼吸：仅 ON 时启动，帧驱动（与涟漪同因：InfiniteTransition 会被系统动画缩放=0 挂起）
    val breatheScale = if (enabled) {
        val p = rememberFrameDrivenProgress(4000)
        // 三角波往返 + 缓动，与涟漪 4s 周期同步
        val tri = if (p < 0.5f) p * 2f else (1f - p) * 2f
        1f + 0.3f * FastOutSlowInEasing.transform(tri)
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(88.dp)
            // 裁剪成圆形再加 clickable：按压涟漪不溢出圆环外成方形
            .clip(CircleShape)
            .border(width = 1.dp, color = ringColor, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
            // 呼吸缩发放内层，避免影响点击热区
                .scale(breatheScale)
                .background(ringColor, CircleShape)
        )
    }
}

/** 单行：左侧灰标签 + 右侧值，底部 1dp 发丝线 */
@Composable
private fun LineRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
            val stroke = 1.dp.toPx()
            drawLine(VigilDirALine, start = androidx.compose.ui.geometry.Offset(0f, size.height - stroke / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height - stroke / 2), strokeWidth = stroke)
        }
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(horizontal = 2.dp, vertical = 15.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        color = VigilDirADim
    )
}

@Composable
private fun RowValue(text: String, withArrow: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = text, fontSize = 13.sp, color = VigilDirAInk)
        if (withArrow) {
            Text(text = "→", fontSize = 13.sp, color = VigilDirAFaint)
        }
    }
}

@Composable
private fun GrantedValue() {
    Text(
        text = "GRANTED",
        style = MonoTextStyle,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = VigilDirAAcid
    )
}

@Composable
private fun WarnCapsule() {
    Text(
        text = "去设置",
        style = MonoTextStyle,
        fontSize = 11.sp,
        color = VigilDirAAmber,
        modifier = Modifier
            .border(1.dp, VigilDirAAmber, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/** 分组小标题：mono 大写灰字，设置 Sheet 权限分级（REQUIRED / RECOMMENDED / OPTIONAL）用 */
@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MonoTextStyle,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = VigilDirAFaint,
        modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 4.dp)
    )
}

/**
 * 锁定任务卡片图文引导弹窗（DirA 风格）。
 * 与 PermissionGuideDialog 不同：纯说明、无系统页可跳，确认键仅关闭；
 * 「不再提示」才持久化关闭设置 Sheet 中的引导行，点外部/返回只是关闭弹窗。
 */
@Composable
private fun LockTaskGuideDialog(
    onClose: () -> Unit,
    onNeverShow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = stringResource(R.string.lock_task_dialog_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilDirAInk
            )
        },
        text = {
            Text(
                text = stringResource(R.string.lock_task_dialog_message),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = VigilDirADim
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(text = "知道了", color = VigilDirAAcid)
            }
        },
        dismissButton = {
            TextButton(onClick = onNeverShow) {
                Text(text = "不再提示", color = VigilDirADim)
            }
        }
    )
}

/** 关键词 chip：点按文本进个性化配置（铃声/循环次数），× 独立圆形删除按钮（点按触发二次确认） */
@Composable
private fun KeywordChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .background(VigilDirALine.copy(alpha = 0.45f), CircleShape)
            .border(1.dp, VigilDirAFaint, CircleShape)
            .padding(start = 12.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = VigilDirAInk,
            modifier = Modifier.clickable(onClick = onClick)
        )
        // × 独立热区：圆形暗底，矢量图标天然居中（字符 × 有字形基线偏移）
        Box(
            modifier = Modifier
                .size(17.dp)
                .background(VigilDirAFaint.copy(alpha = 0.45f), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除关键词 $text",
                tint = VigilDirAInk,
                modifier = Modifier.size(9.dp)
            )
        }
    }
}

/** 虚线 "+" chip */
@Composable
private fun AddChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawRoundRect(
                    color = VigilDirADim,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    ),
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+", fontSize = 11.sp, color = VigilDirADim)
    }
}

/** 关键词删除二次确认对话框（DirA 风格，与 AddKeywordDialog 一致） */
@Composable
private fun DeleteKeywordDialog(
    keyword: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = "删除关键词",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilDirAInk
            )
        },
        text = {
            Text(
                text = "确认删除「$keyword」？删除后该关键词的通知将不再触发报警。",
                fontSize = 13.sp,
                color = VigilDirADim
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "删除", color = VigilDirAAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilDirADim)
            }
        }
    )
}

/** 极简添加关键词对话框 */
@Composable
private fun AddKeywordDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = "添加关键词",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilDirAInk
            )
        },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("输入关键词", fontSize = 14.sp, color = VigilDirADim) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VigilDirAAcid,
                    unfocusedBorderColor = VigilDirAFaint,
                    focusedTextColor = VigilDirAInk,
                    unfocusedTextColor = VigilDirAInk,
                    cursorColor = VigilDirAAcid
                ),
                shape = RoundedCornerShape(4.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(input) },
                enabled = input.isNotBlank()
            ) {
                Text(
                    text = "添加",
                    color = if (input.isNotBlank()) VigilDirAAcid else VigilDirADim
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilDirADim)
            }
        }
    )
}

/** 循环次数显示文案。 */
private fun formatLoopCount(count: Int): String {
    return "$count 次"
}

/**
 * 关键词个性化配置弹窗（DirA 风格）：铃声 + 循环次数覆盖。
 * 未配置的项跟随全局默认；铃声行右侧 × 清除自定义铃声。
 */
@Composable
private fun KeywordConfigDialog(
    keyword: String,
    ringtoneName: String?,
    loopCountOverride: Int?,
    defaultLoopCount: Int,
    onPickRingtone: () -> Unit,
    onClearRingtone: () -> Unit,
    onPickLoopCount: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = "「$keyword」",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilDirAInk
            )
        },
        text = {
            Column {
                // 铃声行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickRingtone)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "铃声", fontSize = 13.sp, color = VigilDirADim)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ringtoneName ?: "默认",
                            fontSize = 13.sp,
                            color = VigilDirAInk
                        )
                        if (ringtoneName != null) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除自定义铃声",
                                tint = VigilDirADim,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable(onClick = onClearRingtone)
                            )
                        } else {
                            Text(text = "→", fontSize = 13.sp, color = VigilDirAFaint)
                        }
                    }
                }
                // 循环次数行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickLoopCount)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "循环次数", fontSize = 13.sp, color = VigilDirADim)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = loopCountOverride?.let { formatLoopCount(it) }
                                ?: "默认（${formatLoopCount(defaultLoopCount)}）",
                            fontSize = 13.sp,
                            color = VigilDirAInk
                        )
                        Text(text = "→", fontSize = 13.sp, color = VigilDirAFaint)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "完成", color = VigilDirAAcid)
            }
        }
    )
}

/**
 * 铃声选择弹窗：内置预设 + 系统铃声（系统选择器）+ 铃声库文件。
 * 当前生效的铃声值高亮；铃声库为空时提示去「铃声库」页导入/录音。
 */
@Composable
private fun RingtoneSelectDialog(
    libraryEntries: List<Pair<String, String>>,
    currentValue: String?,
    onPickPreset: (RingtoneLibrary.PresetRingtone) -> Unit,
    onPickSystemRingtone: () -> Unit,
    onPickLibraryFile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = "选择铃声",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilDirAInk
            )
        },
        text = {
            Column {
                // 内置预设（随 APK 打包的 WAV 语音）
                RingtoneLibrary.PRESETS.forEachIndexed { index, preset ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(VigilDirALine)
                        )
                    }
                    val isSelected = currentValue == RingtoneLibrary.presetValue(context, preset)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickPreset(preset) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.displayName,
                            fontSize = 13.sp,
                            color = if (isSelected) VigilDirAAcid else VigilDirAInk
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(VigilDirAAcid, CircleShape)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(VigilDirALine)
                )
                // 系统铃声入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickSystemRingtone)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "系统铃声…", fontSize = 13.sp, color = VigilDirAInk)
                    Text(text = "→", fontSize = 13.sp, color = VigilDirAFaint)
                }
                if (libraryEntries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(VigilDirALine)
                    )
                }
                libraryEntries.forEach { (fileName, displayName) ->
                    val path = java.io.File(RingtoneLibrary.libraryDir(context), fileName).absolutePath
                    val isSelected = currentValue == path
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickLibraryFile(fileName) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 13.sp,
                            color = if (isSelected) VigilDirAAcid else VigilDirAInk
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(VigilDirAAcid, CircleShape)
                            )
                        }
                    }
                }
                if (libraryEntries.isEmpty()) {
                    Text(
                        text = "铃声库为空，可在「铃声库」页导入音频或录音",
                        fontSize = 11.sp,
                        color = VigilDirADim,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilDirADim)
            }
        }
    )
}

/**
 * 循环次数弹窗：以离散滑杆在 1..10 次之间逐次调节。
 * 关键词覆盖场景可选择「跟随默认」（selected=null 时启用）。
 */
@Composable
private fun LoopCountDialog(
    selected: Int?,
    defaultValue: Int,
    showFollowDefault: Boolean,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingValue by remember(selected) {
        mutableIntStateOf(selected ?: defaultValue)
    }
    var followDefault by remember(selected) { mutableStateOf(showFollowDefault && selected == null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = "循环次数",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilDirAInk
            )
        },
        text = {
            Column {
                if (showFollowDefault) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { followDefault = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "跟随默认",
                            fontSize = 13.sp,
                            color = if (followDefault) VigilDirAAcid else VigilDirAInk
                        )
                        if (followDefault) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(VigilDirAAcid, CircleShape)
                            )
                        }
                    }
                }
                Text(
                    text = if (followDefault) "默认（$defaultValue 次）" else "$pendingValue 次",
                    fontSize = 13.sp,
                    color = if (followDefault) VigilDirADim else VigilDirAAcid,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = pendingValue.toFloat(),
                    onValueChange = {
                        pendingValue = it.toInt().coerceIn(
                            SharedPreferencesHelper.MIN_LOOP_COUNT,
                            SharedPreferencesHelper.MAX_LOOP_COUNT
                        )
                        followDefault = false
                    },
                    valueRange = SharedPreferencesHelper.MIN_LOOP_COUNT.toFloat()..
                        SharedPreferencesHelper.MAX_LOOP_COUNT.toFloat(),
                    steps = SharedPreferencesHelper.MAX_LOOP_COUNT - SharedPreferencesHelper.MIN_LOOP_COUNT - 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 次", fontSize = 11.sp, color = VigilDirADim)
                    Text("10 次", fontSize = 11.sp, color = VigilDirADim)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(if (followDefault) null else pendingValue) }) {
                Text(text = "确定", color = VigilDirAAcid)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilDirADim)
            }
        }
    )
}
