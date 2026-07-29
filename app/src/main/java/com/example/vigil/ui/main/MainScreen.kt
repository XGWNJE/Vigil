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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.MainActivity
import com.example.vigil.PermissionUtils
import com.example.vigil.R
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
    onNavigateToAppFilter: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val serviceState by monitoringViewModel.serviceState
    val serviceEnabled by monitoringViewModel.serviceEnabled

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
            settingsViewModel.onRingtoneUriSelected(uri)
        }
    }

    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf<PermissionGuide?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    // 关键词删除二次确认：待删除的关键词，非空时弹出确认框
    var keywordPendingDelete by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VigilDirABg)
    ) {
        // ---- 底层：全屏涟漪，从中央核心开关扩散，颜色/节奏即状态 ----
        RippleBackground(state = serviceState, modifier = Modifier.fillMaxSize())

        // ---- 顶栏：字标 + 心跳 + 设置齿轮 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 24.dp, end = 24.dp, top = 44.dp),
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
                Spacer(modifier = Modifier.width(14.dp))
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = VigilDirADim,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { showSettingsSheet = true }
                )
            }
        }

        // ---- 状态词：悬浮于核心开关正上方 ----
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-148).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (serviceState) {
                    ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> "监听中"
                    ServiceState.DISABLED -> "已停止"
                    ServiceState.NO_PERMISSION -> "未授权"
                    ServiceState.INITIALIZING -> "连接中"
                    ServiceState.LISTENER_DISCONNECTED -> "重连中"
                    ServiceState.HEARTBEAT_TIMEOUT -> "心跳超时"
                    ServiceState.ERROR -> "异常"
                },
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = VigilDirAInk
            )
            Text(
                text = "${keywordList.size} KEYWORDS",
                style = MonoTextStyle,
                fontSize = 10.sp,
                color = VigilDirADim,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
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

        // ---- SERVICE ON/OFF：核心正下方 ----
        Text(
            text = if (serviceEnabled) "SERVICE ON" else "SERVICE OFF",
            style = MonoTextStyle,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = if (serviceEnabled) VigilDirAAcid else VigilDirADim,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 64.dp)
        )
    }

    // ---- 设置 Sheet：底部浮出 ----
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VigilDirABg,
            tonalElevation = 0.dp
        ) {
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
                                onRemove = { keywordPendingDelete = keyword }
                            )
                        }
                        AddChip(onClick = { showAddKeywordDialog = true })
                    }
                }

                // 2. 铃声
                LineRow(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择报警铃声")
                        settingsViewModel.selectedRingtoneUri.value?.let { uri ->
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                        }
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    }
                    try {
                        ringtonePickerLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开铃声选择器: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    RowLabel("铃声")
                    RowValue(selectedRingtoneName, withArrow = true)
                }

                // 3. 应用过滤
                LineRow(onClick = onNavigateToAppFilter) {
                    RowLabel("应用过滤")
                    RowValue(if (isAppFilterEnabled) "仅指定应用" else "全部应用", withArrow = true)
                }

                // 4. 通知使用权
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

                // 5. 电池白名单
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

                // 6. 自启动管理（国产 ROM 通用引导，跳转系统对应设置页）
                LineRow(onClick = {
                    activity?.let { PermissionUtils.openAutoStartSettings(it) }
                }) {
                    RowLabel("自启动管理")
                    RowValue("允许自启动", withArrow = true)
                }

                // 7. 后台运行（省电策略/耗电管理设为无限制）
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
        null -> Unit
    }
}

/** 主屏三个权限引导弹窗的种类 */
private enum class PermissionGuide {
    NotificationAccess,
    Battery,
    BackgroundRun
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
        1f + 0.2f * FastOutSlowInEasing.transform(tri)
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(88.dp)
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

/** 关键词 chip：暗底胶囊 + 独立圆形删除按钮（点按触发二次确认） */
@Composable
private fun KeywordChip(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .background(VigilDirALine.copy(alpha = 0.45f), CircleShape)
            .border(1.dp, VigilDirAFaint, CircleShape)
            .padding(start = 12.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(text = text, fontSize = 11.sp, color = VigilDirAInk)
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
