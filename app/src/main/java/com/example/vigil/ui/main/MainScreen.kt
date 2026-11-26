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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import com.example.vigil.ui.components.VigilCardShape
import com.example.vigil.ui.components.VigilDialogShape
import com.example.vigil.ui.components.VigilPillChip
import com.example.vigil.ui.components.VigilPillShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.MainActivity
import com.example.vigil.alert.AlertOverlayManager
import com.example.vigil.data.DelayMode
import com.example.vigil.data.DelayPolicy
import com.example.vigil.data.OverlayAlertStyle
import com.example.vigil.alert.DelayedAlertScheduler
import com.example.vigil.util.ListenerRecovery
import com.example.vigil.util.PermissionUtils
import com.example.vigil.R
import com.example.vigil.alert.RingtoneLibrary
import com.example.vigil.data.ScheduledAlert
import com.example.vigil.data.SharedPreferencesHelper
import com.example.vigil.update.UpdateChecker
import com.example.vigil.update.UpdateViewModel
import com.example.vigil.util.VigilEventBus
import com.example.vigil.util.VigilLogger
import com.example.vigil.ui.dialogs.PermissionGuideDialog
import com.example.vigil.ui.monitoring.MonitoringViewModel
import com.example.vigil.ui.monitoring.ServiceState
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.ui.theme.VigilPrimary
import com.example.vigil.ui.theme.VigilError
import com.example.vigil.ui.theme.VigilBackground
import com.example.vigil.ui.theme.VigilTextSecondary
import com.example.vigil.ui.theme.VigilBorder
import com.example.vigil.ui.theme.VigilTextPrimary
import com.example.vigil.ui.theme.VigilDivider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 本机通知设置与报警策略；主界面由 NotificationDashboard 承载。 */
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

    // 铃声选择器（逻辑搬自原 SettingsScreen）
    // ringtonePickTarget 非空时表示正在为某个关键词选铃声，null = 全局默认铃声
    var ringtonePickTarget by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
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
                settingsViewModel.onRingtoneValueSelected(uri?.toString() ?: RingtoneLibrary.SILENT_VALUE)
            } else {
                settingsViewModel.onKeywordRingtoneSelected(target, uri?.toString() ?: RingtoneLibrary.SILENT_VALUE)
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
            val value = target?.let { settingsViewModel.getKeywordRingtoneValue(it) }
                ?: settingsViewModel.getDefaultRingtoneValue()
            val existing = if (value == RingtoneLibrary.SILENT_VALUE) null
                else value?.takeIf { it.startsWith("content://") }?.let(Uri::parse)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
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
    var expandedKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    var showGuideDialog by remember { mutableStateOf<PermissionGuide?>(null) }
    // 关键词删除二次确认：待删除的关键词，非空时弹出确认框
    var keywordPendingDelete by remember { mutableStateOf<String?>(null) }
    // 关键词个性化配置弹窗：待配置的关键词，非空时弹出（铃声 + 循环次数）
    var keywordPendingConfig by remember { mutableStateOf<String?>(null) }
    // 循环次数档位弹窗：null=不显示；""=全局默认档位；非空=该关键词的覆盖档位
    var loopCountDialogTarget by remember { mutableStateOf<String?>(null) }
    var showKeywordCooldownDialog by remember { mutableStateOf(false) }
    // 延时报警弹窗：null=不显示；""=全局默认策略；非空=该关键词的覆盖策略
    var delayDialogTarget by remember { mutableStateOf<String?>(null) }
    // 待触发延时报警清单弹窗
    var showPendingDelayedDialog by remember { mutableStateOf(false) }
    // 铃声选择弹窗：null=不显示；""=默认铃声；非空=该关键词的铃声
    var ringtoneSelectTarget by remember { mutableStateOf<String?>(null) }
    val defaultLoopCount by settingsViewModel.defaultLoopCount
    val keywordCooldownSeconds by settingsViewModel.keywordCooldownSeconds
    val defaultDelayPolicy by settingsViewModel.defaultDelayPolicy
    val exactAlarmAllowed by settingsViewModel.exactAlarmAllowed
    val hasDelayedPolicy by settingsViewModel.hasDelayedPolicy
    val hasOverlayPermission by settingsViewModel.hasOverlayPermission
    val overlayAlertStyle by settingsViewModel.overlayAlertStyle
    val pendingDelayedCount by monitoringViewModel.pendingDelayedCount
    // 关键词配置变化信号（读取即订阅，chip 弹窗里的值随配置保存即时刷新）
    val keywordConfigVersion by settingsViewModel.keywordConfigVersion

    // 入口提示与引导行的持久化状态（纯 UI 标记，与服务/ViewModel 无关，直接读 prefs）
    val prefs = remember { SharedPreferencesHelper(context) }
    var lockTaskTipDismissed by remember { mutableStateOf(prefs.isLockTaskTipDismissed()) }
    NotificationDashboard(
        serviceEnabled = serviceEnabled,
        serviceState = serviceState,
        keywordCount = keywordList.size,
        overlayStyle = overlayAlertStyle,
        settingsViewModel = settingsViewModel,
        onToggleLocal = {
            if (!serviceEnabled && !hasNotificationAccess) {
                showGuideDialog = PermissionGuide.NotificationAccess
            } else monitoringViewModel.onServiceEnabledChange(!serviceEnabled,
                startServiceCallback = { (activity as? MainActivity)?.startVigilService(it) },
                stopServiceCallback = { (activity as? MainActivity)?.stopVigilService() })
        },
        settingsContent = { keywordPage ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (keywordPage) {
                    // 方案 C · 顶部双胶囊操作栏（+ 添加关键词 | 应用过滤）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colors = MaterialTheme.colorScheme
                        Surface(
                            onClick = { showAddKeywordDialog = true },
                            modifier = Modifier.weight(1f),
                            color = colors.primaryContainer,
                            shape = VigilPillShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.65f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "添加关键词",
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = onNavigateToAppFilter,
                            modifier = Modifier.weight(1f),
                            color = colors.surface,
                            shape = VigilPillShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FilterAlt,
                                    contentDescription = null,
                                    tint = if (isAppFilterEnabled) colors.primary else colors.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isAppFilterEnabled) "仅指定应用" else "全部应用",
                                    color = colors.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 方案 C · 手风琴原地展开规则卡列表
                    if (keywordList.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = VigilCardShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "暂无关键词，点击上方「添加关键词」开始监听",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                            )
                        }
                    } else {
                        keywordList.forEach { keyword ->
                            val configTrigger = keywordConfigVersion
                            val ringtoneOverride = remember(keyword, configTrigger) {
                                settingsViewModel.getKeywordRingtoneName(keyword)
                            }
                            val loopCountOverride = remember(keyword, configTrigger) {
                                settingsViewModel.getKeywordLoopCount(keyword)
                            }
                            val delayPolicyOverride = remember(keyword, configTrigger) {
                                settingsViewModel.getKeywordDelayPolicy(keyword)
                            }
                            AccordionKeywordCard(
                                keyword = keyword,
                                expanded = expandedKeyword == keyword,
                                ringtoneLabel = ringtoneOverride ?: "默认",
                                loopLabel = formatLoopCount(loopCountOverride ?: defaultLoopCount),
                                delayLabel = formatDelayShort(delayPolicyOverride ?: defaultDelayPolicy),
                                hasCustomRule = ringtoneOverride != null || loopCountOverride != null || delayPolicyOverride != null,
                                onToggleExpand = {
                                    expandedKeyword = if (expandedKeyword == keyword) null else keyword
                                },
                                onPickRingtone = { ringtoneSelectTarget = keyword },
                                onPickLoopCount = { loopCountDialogTarget = keyword },
                                onPickDelay = { delayDialogTarget = keyword },
                                onDelete = { keywordPendingDelete = keyword }
                            )
                        }
                    }
                }
                if (!keywordPage) {
                GroupHeader("声音与报警")

                // 2. 默认铃声
                LineRow(onClick = { ringtoneSelectTarget = "" }) {
                    RowLabel("默认铃声")
                    RowValue(selectedRingtoneName, withArrow = true)
                }

                // 2.5 铃声库（全应用唯一入口）
                LineRow(onClick = onNavigateToRingtoneLibrary) {
                    RowLabel("铃声库")
                    RowValue("管理与录音", withArrow = true)
                }

                // 3. 循环次数：1..10；关键词可在 chip 弹窗里逐条覆盖
                LineRow(onClick = { loopCountDialogTarget = "" }) {
                    RowLabel("循环次数")
                    RowValue(formatLoopCount(defaultLoopCount), withArrow = true)
                }

                // 4. 同一来源、同一关键词的重复提醒冷却时间
                LineRow(onClick = { showKeywordCooldownDialog = true }) {
                    RowLabel("重复提醒间隔")
                    RowValue(formatKeywordCooldown(keywordCooldownSeconds), withArrow = true)
                }

                // 5. 延时报警：命中后不立即响铃，改为固定时长后或每日定点时间响；关键词可逐条覆盖
                LineRow(onClick = { delayDialogTarget = "" }) {
                    RowLabel("延时报警")
                    RowValue(formatDelayPolicy(defaultDelayPolicy), withArrow = true)
                }

                // 5.5 应用外系统浮窗（全应用唯一入口：权限 + 样式 + 预览）
                GroupHeader("应用外告警浮窗")
                LineRow(onClick = {
                    if (!hasOverlayPermission) {
                        activity?.let(PermissionUtils::openOverlaySettings)
                    } else {
                        AlertOverlayManager.showPreview(context, overlayAlertStyle)
                    }
                }) {
                    RowLabel("系统浮窗")
                    if (hasOverlayPermission) {
                        RowValue("预览", withArrow = true)
                    } else {
                        WarnCapsule()
                    }
                }
                LineRow {
                    RowLabel("浮窗样式")
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            OverlayAlertStyle.AUTO to "自动",
                            OverlayAlertStyle.TOP_BANNER to "顶部",
                            OverlayAlertStyle.CENTER_CARD to "居中",
                            OverlayAlertStyle.CORNER_COMPACT to "角落"
                        ).forEach { (style, label) ->
                            VigilPillChip(
                                label = label,
                                selected = overlayAlertStyle == style,
                                onClick = {
                                    settingsViewModel.updateOverlayAlertStyle(style)
                                    if (hasOverlayPermission) {
                                        AlertOverlayManager.showPreview(context, style)
                                    }
                                }
                            )
                        }
                    }
                }

                // 6. 待触发延时报警：有排定项时才出现，可查看/取消
                if (pendingDelayedCount > 0) {
                    LineRow(onClick = { showPendingDelayedDialog = true }) {
                        RowLabel("待触发延时报警")
                        RowValue("$pendingDelayedCount 条", withArrow = true)
                    }
                }

                }
                if (!keywordPage) {
                // ---- 权限与保活：按必需性分级（必需 / 推荐 / 可选） ----
                GroupHeader("本机通知权限")

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

                GroupHeader("后台运行建议")

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

                // 精确闹钟（Android 12+）：只在使用延时报警且未授权时提示，否则纯噪音
                if (hasDelayedPolicy && !exactAlarmAllowed) {
                    LineRow(onClick = {
                        if (!DelayedAlertScheduler.openExactAlarmSettings(context)) {
                            Toast.makeText(context, "无法打开闹钟和提醒设置", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        RowLabel("精确闹钟")
                        WarnCapsule()
                    }
                }

                GroupHeader("其他设置")

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
                            context, "${context.packageName}.fileprovider", exportFile
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
    )

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
            delayPolicyOverride = settingsViewModel.getKeywordDelayPolicy(keyword),
            defaultDelayPolicy = defaultDelayPolicy,
            onPickRingtone = { ringtoneSelectTarget = keyword },
            onClearRingtone = { settingsViewModel.onKeywordRingtoneSelected(keyword, null) },
            onPickLoopCount = { loopCountDialogTarget = keyword },
            onPickDelay = { delayDialogTarget = keyword },
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

    if (showKeywordCooldownDialog) {
        KeywordCooldownDialog(
            selected = keywordCooldownSeconds,
            onSelect = {
                settingsViewModel.onKeywordCooldownSelected(it)
                showKeywordCooldownDialog = false
            },
            onDismiss = { showKeywordCooldownDialog = false }
        )
    }

    // 延时报警策略弹窗（全局默认与关键词覆盖共用；关键词多一档「跟随默认」）
    delayDialogTarget?.let { target ->
        val isGlobal = target.isEmpty()
        val overridePolicy = if (isGlobal) null else settingsViewModel.getKeywordDelayPolicy(target)
        DelayPolicyDialog(
            isGlobal = isGlobal,
            overridePolicy = overridePolicy,
            defaultPolicy = defaultDelayPolicy,
            pendingCount = pendingDelayedCount,
            exactAlarmAllowed = exactAlarmAllowed,
            onSelect = { policy ->
                if (isGlobal) {
                    settingsViewModel.onDefaultDelayPolicySelected(policy ?: DelayPolicy.IMMEDIATE)
                } else {
                    settingsViewModel.onKeywordDelayPolicySelected(target, policy)
                }
                delayDialogTarget = null
            },
            onCancelAllPending = {
                settingsViewModel.cancelAllScheduledAlerts()
                monitoringViewModel.refreshPendingDelayedCount()
            },
            onDismiss = { delayDialogTarget = null }
        )
    }

    // 待触发延时报警清单（查看计划时刻 / 单条或全部取消）
    if (showPendingDelayedDialog) {
        ScheduledAlertsDialog(
            items = settingsViewModel.getScheduledAlerts(),
            onCancelOne = { id ->
                settingsViewModel.cancelScheduledAlert(id)
                monitoringViewModel.refreshPendingDelayedCount()
            },
            onCancelAll = {
                settingsViewModel.cancelAllScheduledAlerts()
                monitoringViewModel.refreshPendingDelayedCount()
                showPendingDelayedDialog = false
            },
            onDismiss = { showPendingDelayedDialog = false }
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

private val BodyTextStyle = TextStyle(fontFamily = FontFamily.SansSerif)

/**
 * 核心开关：涟漪的环心。
 * ON：酸橙绿描边 + 实心圆点呼吸缩放；OFF：暗灰描边 + 静止暗点。
 */
@Composable
private fun LineRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VigilCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = colors.surface,
        shape = VigilCardShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RowValue(text: String, withArrow: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = text, fontSize = 13.sp, color = colors.onSurfaceVariant)
        if (withArrow) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun GrantedValue() {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer,
        shape = VigilPillShape
    ) {
        Text(
            text = "已授权",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WarnCapsule() {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.errorContainer,
        shape = VigilPillShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.error.copy(alpha = 0.5f))
    ) {
        Text(
            text = "去授权",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.error,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.lock_task_dialog_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            Text(
                text = stringResource(R.string.lock_task_dialog_message),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = VigilTextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(text = "知道了", color = VigilPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onNeverShow) {
                Text(text = "不再提示", color = VigilTextSecondary)
            }
        }
    )
}

/**
 * 方案 C · 手风琴原地展开规则卡：
 * - 折叠态：左关键词（最多 2 行优雅省略，绝不撑破）+ 右摘要胶囊与展开箭头；
 * - 展开态：琥珀金边框高亮，原地显示「铃声 / 循环 / 延时」三联直调胶囊与右下角独立红色删除键。
 */
@Composable
private fun AccordionKeywordCard(
    keyword: String,
    expanded: Boolean,
    ringtoneLabel: String,
    loopLabel: String,
    delayLabel: String,
    hasCustomRule: Boolean,
    onToggleExpand: () -> Unit,
    onPickRingtone: () -> Unit,
    onPickLoopCount: () -> Unit,
    onPickDelay: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = colors.surface,
        shape = VigilCardShape,
        border = androidx.compose.foundation.BorderStroke(
            width = if (expanded) 1.5.dp else 1.dp,
            color = if (expanded) colors.primary else colors.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 折叠态主行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = keyword,
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (expanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    color = if (hasCustomRule) colors.primaryContainer else colors.surfaceVariant,
                    shape = VigilPillShape
                ) {
                    Text(
                        text = "$loopLabel · $delayLabel",
                        color = if (hasCustomRule) colors.primary else colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = if (expanded) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 展开态：原地配置胶囊 + 右下角独立删除按钮
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccordionInlineField(
                            icon = Icons.Outlined.Notifications,
                            label = "铃声",
                            value = ringtoneLabel,
                            onClick = onPickRingtone,
                            modifier = Modifier.weight(1f)
                        )
                        AccordionInlineField(
                            icon = Icons.Outlined.Repeat,
                            label = "循环",
                            value = loopLabel,
                            onClick = onPickLoopCount,
                            modifier = Modifier.weight(1f)
                        )
                        AccordionInlineField(
                            icon = Icons.Outlined.Schedule,
                            label = "延时",
                            value = delayLabel,
                            onClick = onPickDelay,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = onDelete,
                            color = colors.error,
                            shape = VigilPillShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "删除关键词 $keyword",
                                    tint = colors.onError,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "删除",
                                    color = colors.onError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccordionInlineField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            fontSize = 11.sp
        )
        Surface(
            onClick = onClick,
            color = colors.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = value,
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatDelayShort(policy: DelayPolicy): String = when (policy.mode) {
    DelayMode.IMMEDIATE -> "即时"
    DelayMode.FIXED -> {
        val secs = (policy.fixedDelayMs / 1000L).coerceAtLeast(1L)
        when {
            secs >= 3600L -> "+${secs / 3600L}h"
            secs >= 60L -> "+${secs / 60L}m"
            else -> "+${secs}s"
        }
    }
    DelayMode.SCHEDULED -> "定点"
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "删除关键词",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            Text(
                text = "确认删除「$keyword」？删除后该关键词的通知将不再触发报警。",
                fontSize = 13.sp,
                color = VigilTextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "删除", color = VigilError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilTextSecondary)
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "添加关键词",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("输入关键词", fontSize = 14.sp, color = VigilTextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VigilPrimary,
                    unfocusedBorderColor = VigilBorder,
                    focusedTextColor = VigilTextPrimary,
                    unfocusedTextColor = VigilTextPrimary,
                    cursorColor = VigilPrimary
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
                    color = if (input.isNotBlank()) VigilPrimary else VigilTextSecondary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilTextSecondary)
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
    delayPolicyOverride: DelayPolicy?,
    defaultDelayPolicy: DelayPolicy,
    onPickRingtone: () -> Unit,
    onClearRingtone: () -> Unit,
    onPickLoopCount: () -> Unit,
    onPickDelay: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "「$keyword」",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
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
                    Text(text = "铃声", fontSize = 13.sp, color = VigilTextSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ringtoneName ?: "默认",
                            fontSize = 13.sp,
                            color = VigilTextPrimary
                        )
                        if (ringtoneName != null) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除自定义铃声",
                                tint = VigilTextSecondary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable(onClick = onClearRingtone)
                            )
                        } else {
                            Text(text = "→", fontSize = 13.sp, color = VigilBorder)
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
                    Text(text = "循环次数", fontSize = 13.sp, color = VigilTextSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = loopCountOverride?.let { formatLoopCount(it) }
                                ?: "默认（${formatLoopCount(defaultLoopCount)}）",
                            fontSize = 13.sp,
                            color = VigilTextPrimary
                        )
                        Text(text = "→", fontSize = 13.sp, color = VigilBorder)
                    }
                }
                // 延时报警行（未覆盖则跟随默认策略）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickDelay)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "延时报警", fontSize = 13.sp, color = VigilTextSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = delayPolicyOverride?.let { formatDelayPolicy(it) }
                                ?: "默认（${formatDelayPolicy(defaultDelayPolicy)}）",
                            fontSize = 13.sp,
                            color = VigilTextPrimary
                        )
                        Text(text = "→", fontSize = 13.sp, color = VigilBorder)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "完成", color = VigilPrimary)
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "选择铃声",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                // 内置预设（随 APK 打包的 WAV 语音）
                RingtoneLibrary.PRESETS.forEachIndexed { index, preset ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(VigilDivider)
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
                            color = if (isSelected) VigilPrimary else VigilTextPrimary
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(VigilPrimary, CircleShape)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(VigilDivider)
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
                    Text(text = "系统铃声…", fontSize = 13.sp, color = VigilTextPrimary)
                    Text(text = "→", fontSize = 13.sp, color = VigilBorder)
                }
                if (libraryEntries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(VigilDivider)
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
                            color = if (isSelected) VigilPrimary else VigilTextPrimary
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(VigilPrimary, CircleShape)
                            )
                        }
                    }
                }
                if (libraryEntries.isEmpty()) {
                    Text(
                        text = "铃声库为空，可在「铃声库」页导入音频或录音",
                        fontSize = 11.sp,
                        color = VigilTextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilTextSecondary)
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "循环次数",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
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
                            color = if (followDefault) VigilPrimary else VigilTextPrimary
                        )
                        if (followDefault) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(VigilPrimary, CircleShape)
                            )
                        }
                    }
                }
                Text(
                    text = if (followDefault) "默认（$defaultValue 次）" else "$pendingValue 次",
                    fontSize = 13.sp,
                    color = if (followDefault) VigilTextSecondary else VigilPrimary,
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
                    Text("1 次", fontSize = 11.sp, color = VigilTextSecondary)
                    Text("10 次", fontSize = 11.sp, color = VigilTextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(if (followDefault) null else pendingValue) }) {
                Text(text = "确定", color = VigilPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilTextSecondary)
            }
        }
    )
}

private fun formatKeywordCooldown(seconds: Int): String = when (seconds) {
    0 -> "不忽略"
    30 -> "30 秒"
    60 -> "1 分钟"
    180 -> "3 分钟"
    300 -> "5 分钟"
    600 -> "10 分钟"
    else -> "$seconds 秒"
}

/** 同一来源、同一关键词再次出现时的忽略间隔。 */
@Composable
private fun KeywordCooldownDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0, 30, 60, 180, 300, 600)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "重复提醒间隔",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "同一应用的同一关键词在间隔内再次出现时，不重复响铃。",
                    fontSize = 12.sp,
                    color = VigilTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { seconds ->
                    val isSelected = selected == seconds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatKeywordCooldown(seconds),
                            fontSize = 13.sp,
                            color = if (isSelected) VigilPrimary else VigilTextPrimary
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(VigilPrimary, CircleShape)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = VigilTextSecondary)
            }
        }
    )
}

// ---- 延时报警：文案格式化 ----

/** 延时策略显示文案：立即 / 延时 N 分钟 / 定点 08:00·18:00。 */
private fun formatDelayPolicy(policy: DelayPolicy): String = when (policy.mode) {
    DelayMode.IMMEDIATE -> "立即"
    DelayMode.FIXED -> "延时 ${formatDelayDuration(policy.fixedDelayMs)}"
    DelayMode.SCHEDULED -> {
        val times = policy.timesOfDay
        val shown = times.take(2).joinToString("·") { formatTimeOfDay(it) }
        if (times.size > 2) "定点 $shown 等${times.size}个" else "定点 $shown"
    }
}

/** 延时长度文案：非零单位依次拼出（如「1 小时 2 分 3 秒」「45 秒」）。 */
private fun formatDelayDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val parts = buildList {
        if (hours > 0L) add("$hours 小时")
        if (minutes > 0L) add("$minutes 分")
        if (seconds > 0L) add("$seconds 秒")
    }
    return if (parts.isEmpty()) "0 秒" else parts.joinToString(" ")
}

/** 当日分钟数（0..1439）→ HH:mm。 */
private fun formatTimeOfDay(minutesOfDay: Int): String =
    "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

/** 待触发项的绝对计划时刻（跨天也能一眼看清）。 */
private fun formatScheduledAt(triggerAtMs: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(triggerAtMs))

/**
 * 延时报警策略弹窗（DirA 风格）。
 * 全局场景：立即 / 固定延时 / 每日定点三选一；
 * 关键词场景：多一档「跟随默认」。定点支持增删时间点，添加时间用同弹窗内的滑杆，
 * 不叠系统选择器。
 */
@Composable
private fun DelayPolicyDialog(
    isGlobal: Boolean,
    overridePolicy: DelayPolicy?,
    defaultPolicy: DelayPolicy,
    pendingCount: Int,
    exactAlarmAllowed: Boolean,
    onSelect: (DelayPolicy?) -> Unit,
    onCancelAllPending: () -> Unit,
    onDismiss: () -> Unit
) {
    val effective = overridePolicy ?: defaultPolicy
    var followDefault by remember { mutableStateOf(!isGlobal && overridePolicy == null) }
    var mode by remember { mutableStateOf(effective.mode) }
    // 固定延时：精确到秒的可编辑时/分/秒（三格合计即延时长度）
    val effectiveFixedDelayMs = remember {
        SharedPreferencesHelper.normalizeFixedDelayMs(effective.fixedDelayMs)
    }
    var hoursText by remember { mutableStateOf((effectiveFixedDelayMs / 3_600_000L).toString()) }
    var minutesText by remember {
        mutableStateOf(((effectiveFixedDelayMs / 60_000L) % 60L).toString())
    }
    var secondsText by remember { mutableStateOf(((effectiveFixedDelayMs / 1_000L) % 60L).toString()) }
    val fixedDelayMs = (hoursText.toIntOrNull() ?: 0) * 3_600_000L +
        (minutesText.toIntOrNull() ?: 0) * 60_000L +
        (secondsText.toIntOrNull() ?: 0) * 1_000L
    var times by remember { mutableStateOf(effective.timesOfDay) }
    // 添加时间点的内联编辑态（避免叠加第二个对话框）
    var addingTime by remember { mutableStateOf(false) }
    var draftHour by remember { mutableIntStateOf(8) }
    var draftMinute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "延时报警",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (addingTime) {
                    Text(text = "添加时间点", fontSize = 13.sp, color = VigilTextSecondary)
                    Text(
                        text = formatTimeOfDay(draftHour * 60 + draftMinute),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = VigilPrimary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = "时",
                        fontSize = 11.sp,
                        color = VigilTextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Slider(
                        value = draftHour.toFloat(),
                        onValueChange = { draftHour = it.toInt().coerceIn(0, 23) },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("00", fontSize = 11.sp, color = VigilTextSecondary)
                        Text("23", fontSize = 11.sp, color = VigilTextSecondary)
                    }
                    Text(text = "分", fontSize = 11.sp, color = VigilTextSecondary)
                    Slider(
                        value = draftMinute.toFloat(),
                        onValueChange = { draftMinute = it.toInt().coerceIn(0, 55) },
                        valueRange = 0f..55f,
                        steps = 10
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("00", fontSize = 11.sp, color = VigilTextSecondary)
                        Text("55", fontSize = 11.sp, color = VigilTextSecondary)
                    }
                } else {
                    // 关键词场景：可回到「跟随默认」
                    if (!isGlobal) {
                        SelectableRow(
                            label = "跟随默认",
                            detail = formatDelayPolicy(defaultPolicy),
                            selected = followDefault
                        ) {
                            followDefault = true
                        }
                    }
                    SelectableRow(
                        label = "立即报警",
                        detail = "命中即响",
                        selected = !followDefault && mode == DelayMode.IMMEDIATE
                    ) {
                        followDefault = false
                        mode = DelayMode.IMMEDIATE
                    }
                    SelectableRow(
                        label = "固定延时",
                        detail = "命中后等待设定时长",
                        selected = !followDefault && mode == DelayMode.FIXED
                    ) {
                        followDefault = false
                        mode = DelayMode.FIXED
                    }
                    if (!followDefault && mode == DelayMode.FIXED) {
                        // 精确到秒的延时编辑：三格分别填时/分/秒，合计即延时长度
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DelayTimeField("时", hoursText, 23) { hoursText = it }
                            DelayTimeField("分", minutesText, 59) { minutesText = it }
                            DelayTimeField("秒", secondsText, 59) { secondsText = it }
                        }
                        Text(
                            text = if (fixedDelayMs < SharedPreferencesHelper.MIN_FIXED_DELAY_MS) {
                                "至少 1 秒"
                            } else {
                                "命中后等 ${formatDelayDuration(fixedDelayMs)}再响铃"
                            },
                            fontSize = 12.sp,
                            color = if (fixedDelayMs < SharedPreferencesHelper.MIN_FIXED_DELAY_MS) {
                                VigilError
                            } else {
                                VigilTextSecondary
                            },
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        )
                    }
                    SelectableRow(
                        label = "每日定点",
                        detail = "命中后最近的每日时间点",
                        selected = !followDefault && mode == DelayMode.SCHEDULED
                    ) {
                        followDefault = false
                        mode = DelayMode.SCHEDULED
                    }
                    if (!followDefault && mode == DelayMode.SCHEDULED) {
                        if (times.isEmpty()) {
                            Text(
                                text = "还没有时间点，先添加一个",
                                fontSize = 12.sp,
                                color = VigilError,
                                modifier = Modifier.padding(top = 6.dp, start = 12.dp)
                            )
                        }
                        times.forEach { minutes ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTimeOfDay(minutes),
                                    fontSize = 14.sp,
                                    color = VigilTextPrimary
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除时间点",
                                    tint = VigilTextSecondary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { times = times - minutes }
                                )
                            }
                        }
                        Text(
                            text = "+ 添加时间点",
                            fontSize = 13.sp,
                            color = VigilPrimary,
                            modifier = Modifier
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                                .clickable {
                                    draftHour = 8
                                    draftMinute = 0
                                    addingTime = true
                                }
                        )
                    }
                    if (pendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .height(1.dp)
                                .background(VigilDivider)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已排定 $pendingCount 条待触发",
                                fontSize = 12.sp,
                                color = VigilTextSecondary
                            )
                            Text(
                                text = "全部取消",
                                fontSize = 12.sp,
                                color = VigilError,
                                modifier = Modifier.clickable(onClick = onCancelAllPending)
                            )
                        }
                    }
                    if (mode != DelayMode.IMMEDIATE && !exactAlarmAllowed) {
                        Text(
                            text = "系统未授权「闹钟和提醒」，到点可能延迟几分钟；请在设置里授予精确闹钟。",
                            fontSize = 11.sp,
                            color = VigilError,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (addingTime) {
                TextButton(onClick = {
                    times = SharedPreferencesHelper.normalizeTimesOfDay(
                        times + (draftHour * 60 + draftMinute)
                    )
                    addingTime = false
                }) {
                    Text(text = "添加", color = VigilPrimary)
                }
            } else {
                val fixedDelayValid = followDefault || when (mode) {
                    DelayMode.FIXED -> fixedDelayMs >= SharedPreferencesHelper.MIN_FIXED_DELAY_MS
                    DelayMode.SCHEDULED -> times.isNotEmpty()
                    DelayMode.IMMEDIATE -> true
                }
                TextButton(
                    onClick = {
                        val policy = when {
                            followDefault -> null
                            mode == DelayMode.IMMEDIATE -> DelayPolicy.IMMEDIATE
                            mode == DelayMode.FIXED -> DelayPolicy(DelayMode.FIXED, fixedDelayMs)
                            else -> DelayPolicy(DelayMode.SCHEDULED, 0L, times)
                        }
                        onSelect(policy)
                    },
                    enabled = fixedDelayValid
                ) {
                    Text(
                        text = "确定",
                        color = if (fixedDelayValid) VigilPrimary else VigilTextSecondary
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (addingTime) addingTime = false else onDismiss() }) {
                Text(text = if (addingTime) "返回" else "取消", color = VigilTextSecondary)
            }
        }
    )
}

/**
 * 固定延时的时/分/秒输入格：只收数字、两位上限、超上限自动夹到 max（时 23 / 分秒 59）。
 * 空串按 0 处理，便于用户清空后重新输入。
 */
@Composable
private fun DelayTimeField(
    label: String,
    value: String,
    max: Int,
    onValueChange: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(2)
                val normalized = if (digits.isEmpty()) {
                    ""
                } else {
                    minOf(digits.trimStart('0').ifEmpty { "0" }.toInt(), max).toString()
                }
                onValueChange(normalized)
            },
            singleLine = true,
            label = { Text(label, fontSize = 11.sp, color = VigilTextSecondary) },
            textStyle = TextStyle(fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VigilPrimary,
                unfocusedBorderColor = VigilBorder,
                focusedTextColor = VigilTextPrimary,
                unfocusedTextColor = VigilTextPrimary,
                cursorColor = VigilPrimary
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(74.dp)
        )
    }
}

/** 弹窗内的单选项行（左侧标题 + 可选说明，右侧选中圆点）。 */@Composable
private fun SelectableRow(
    label: String,
    detail: String?,
    selected: Boolean,
    indent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indent) 12.dp else 0.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (selected) VigilPrimary else VigilTextPrimary
            )
            if (detail != null) {
                Text(text = detail, fontSize = 11.sp, color = VigilTextSecondary)
            }
        }
        if (selected) {
            Box(modifier = Modifier.size(8.dp).background(VigilPrimary, CircleShape))
        }
    }
}

/** 待触发延时报警清单：逐条显示计划时刻，可单条或全部取消。 */
@Composable
private fun ScheduledAlertsDialog(
    items: List<ScheduledAlert>,
    onCancelOne: (String) -> Unit,
    onCancelAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = VigilDialogShape,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "待触发延时报警",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (items.isEmpty()) {
                    Text(text = "暂无待触发报警", fontSize = 13.sp, color = VigilTextSecondary)
                }
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.keyword, fontSize = 13.sp, color = VigilTextPrimary)
                            Text(
                                text = buildString {
                                    append(formatScheduledAt(item.triggerAtMs))
                                    item.sourceApp?.let { append(" · $it") }
                                },
                                fontSize = 11.sp,
                                color = VigilTextSecondary
                            )
                        }
                        Text(
                            text = "取消",
                            fontSize = 12.sp,
                            color = VigilError,
                            modifier = Modifier.clickable { onCancelOne(item.id) }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(VigilDivider)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭", color = VigilPrimary)
            }
        },
        dismissButton = {
            if (items.isNotEmpty()) {
                TextButton(onClick = onCancelAll) {
                    Text(text = "全部取消", color = VigilTextSecondary)
                }
            }
        }
    )
}
