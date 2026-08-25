package com.example.vigil.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.PermissionUtils
import com.example.vigil.RingtoneLibrary
import com.example.vigil.ui.dialogs.PermissionGuideDialog
import com.example.vigil.ui.main.rememberFrameDrivenProgress
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.ui.settings.SettingsViewModelFactory
import com.example.vigil.ui.theme.VigilDirAAcid
import com.example.vigil.ui.theme.VigilDirAAmber
import com.example.vigil.ui.theme.VigilDirABg
import com.example.vigil.ui.theme.VigilDirADim
import com.example.vigil.ui.theme.VigilDirAFaint
import com.example.vigil.ui.theme.VigilDirAInk
import com.example.vigil.ui.theme.VigilDirALine
import com.example.vigil.ui.theme.VigilSurface
import kotlinx.coroutines.delay

/**
 * 「A · 一线」铃声库页：自定义铃声来源管理（导入音频 / 录音）。
 * 条目支持命名、删除、试听；选中铃声的入口在设置 Sheet「默认铃声」与关键词 chip 弹窗。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RingtoneLibraryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val entries = viewModel.ringtoneLibrary
    // 版本号必须在组合时真正读取（.value）才会订阅状态：isRecording/previewingFileName
    // 是 RingtoneLibrary 的普通 getter，自身不可观察，靠版本号变化触发重组刷新。
    @Suppress("UNUSED_VARIABLE") val previewVersion = viewModel.previewVersion.value
    @Suppress("UNUSED_VARIABLE") val recordingVersion = viewModel.recordingVersion.value
    val previewing = viewModel.previewingFileName
    val isRecording = viewModel.isRecording

    var showMicGuide by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    // 录音计时（仅 UI 展示）
    var recordSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRecording) {
        recordSeconds = 0L
        while (isRecording) {
            delay(1000)
            recordSeconds++
        }
    }

    // 离开页面时停掉试听与未完成的录音
    DisposableEffect(Unit) {
        onDispose {
            RingtoneLibrary.stopPreview()
            RingtoneLibrary.cancelRecording()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRingtone(uri) { ok ->
                toastMsg = if (ok) "已导入铃声" else "导入失败"
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            showMicGuide = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VigilDirABg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)
    ) {
        // ---- 顶部：返回 + 标题 + 导入 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 48.dp)
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = "←", fontSize = 18.sp, color = VigilDirADim)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "RINGTONES // 铃声库",
                    style = MonoTextStyle,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = VigilDirAInk
                )
            }
            Text(
                text = "IMPORT",
                style = MonoTextStyle,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = VigilDirAAcid,
                modifier = Modifier
                    .clickable {
                        importLauncher.launch(arrayOf("audio/*"))
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // ---- 录音行 ----
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VigilDirALine)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(
                            VigilDirALine,
                            start = Offset(0f, size.height - stroke / 2),
                            end = Offset(size.width, size.height - stroke / 2),
                            strokeWidth = stroke
                        )
                    }
                    .clickable {
                        if (isRecording) {
                            val saved = viewModel.stopRecording()
                            toastMsg = if (saved) "录音已保存" else "录音太短或失败，未保存"
                        } else {
                            if (PermissionUtils.isRecordAudioGranted(context)) {
                                viewModel.startRecording()
                            } else {
                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    .padding(horizontal = 2.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "录音", fontSize = 12.sp, letterSpacing = 0.6.sp, color = VigilDirADim)
                if (isRecording) {
                    // 红点 1s 脉冲：透明度 0.15→1 全幅明暗 + 0.8→1.2 缩放，录音进行中一眼可见
                    val pulse = rememberFrameDrivenProgress(1000)
                    val pulseTri = if (pulse < 0.5f) pulse * 2f else (1f - pulse) * 2f
                    val dotAlpha = 0.15f + 0.85f * pulseTri
                    val dotScale = 0.8f + 0.4f * pulseTri
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "●",
                            fontSize = 13.sp,
                            color = VigilDirAAmber.copy(alpha = dotAlpha),
                            modifier = Modifier.scale(dotScale)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${recordSeconds}s 停止",
                            style = MonoTextStyle,
                            fontSize = 11.sp,
                            color = VigilDirAAmber
                        )
                    }
                } else {
                    Text(text = "开始录音", fontSize = 13.sp, color = VigilDirAInk)
                }
            }
            Text(
                text = "${RingtoneLibrary.PRESETS.size} PRESET · ${entries.size} RINGTONES · 点击名称重命名",
                style = MonoTextStyle,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = VigilDirADim,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        // ---- 条目列表（内置预设 + 铃声库文件；预设不可删除/重命名） ----
        LazyColumn {
            items(RingtoneLibrary.PRESETS, key = { "preset:${it.rawName}" }) { preset ->
                PresetEntryRow(
                    preset = preset,
                    isPreviewing = previewing == "preset:${preset.rawName}",
                    onTogglePreview = { viewModel.togglePresetPreview(preset) }
                )
            }
            if (entries.isEmpty()) {
                item(key = "empty_hint") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "铃声库为空，点 IMPORT 导入音频或录音",
                            fontSize = 13.sp,
                            color = VigilDirADim
                        )
                    }
                }
            } else {
                items(entries, key = { it.first }) { (fileName, displayName) ->
                    RingtoneEntryRow(
                        displayName = displayName,
                        isPreviewing = previewing == fileName,
                        onTogglePreview = { viewModel.togglePreview(fileName) },
                        onRename = { renameTarget = fileName to displayName },
                        onDelete = { deleteTarget = fileName to displayName },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }

    // ---- 应用内 Toast：底部暗条（fade + 上滑进出，2s 自消），替代风格不统一的系统 Toast ----
    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            delay(2000)
            toastMsg = null
        }
    }
    InAppToast(
        message = toastMsg,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 32.dp)
    )
    }

    // ---- 录音权限引导（PermissionGuideDialog 模式） ----
    if (showMicGuide) {
        PermissionGuideDialog(
            title = "需要麦克风权限",
            message = "录音功能需要麦克风权限。请在系统设置中允许 Vigil 使用麦克风。",
            onConfirm = {
                showMicGuide = false
                (context as? android.app.Activity)?.let { PermissionUtils.openAppDetailsSettings(it) }
            },
            onDismiss = { showMicGuide = false }
        )
    }

    // ---- 重命名弹窗 ----
    renameTarget?.let { (fileName, oldName) ->
        RenameRingtoneDialog(
            oldName = oldName,
            onConfirm = { newName ->
                viewModel.renameLibraryRingtone(fileName, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    // ---- 删除二次确认 ----
    deleteTarget?.let { (fileName, displayName) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = VigilDirABg,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
            title = {
                Text(
                    text = "删除铃声",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = VigilDirAInk
                )
            },
            text = {
                Text(
                    text = "确认删除「$displayName」？引用它的关键词/默认铃声将回落到默认闹钟铃声。",
                    fontSize = 13.sp,
                    color = VigilDirADim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLibraryRingtone(fileName)
                    deleteTarget = null
                }) {
                    Text(text = "删除", color = VigilDirAAmber)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = "取消", color = VigilDirADim)
                }
            }
        )
    }
}

/** 应用内极简提示条：暗底 + 发丝描边，底部居中，fade + 上滑进出 */
@Composable
private fun InAppToast(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 }
    ) {
        Box(
            modifier = Modifier
                .background(VigilSurface, RoundedCornerShape(4.dp))
                .border(1.dp, VigilDirAFaint, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message ?: "",
                fontSize = 12.sp,
                color = VigilDirAInk
            )
        }
    }
}

/** 内置预设铃声条目：展示名 + 试听切换（不可重命名/删除），底部发丝线 */
@Composable
private fun PresetEntryRow(
    preset: RingtoneLibrary.PresetRingtone,
    isPreviewing: Boolean,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    VigilDirALine,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            }
            .padding(horizontal = 2.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "预设",
                style = MonoTextStyle,
                fontSize = 10.sp,
                color = VigilDirAAcid,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = preset.displayName,
                fontSize = 14.sp,
                color = VigilDirAInk
            )
        }
        Text(
            text = if (isPreviewing) "■ 停止" else "试听",
            style = MonoTextStyle,
            fontSize = 11.sp,
            color = if (isPreviewing) VigilDirAAmber else VigilDirADim,
            modifier = Modifier.clickable(onClick = onTogglePreview)
        )
    }
}

/** 铃声库条目：名称（点按重命名）+ 试听切换 + 删除，底部发丝线 */
@Composable
private fun RingtoneEntryRow(
    displayName: String,
    isPreviewing: Boolean,
    onTogglePreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    VigilDirALine,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            }
            .padding(horizontal = 2.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            fontSize = 14.sp,
            color = VigilDirAInk,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRename)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isPreviewing) "■ 停止" else "试听",
                style = MonoTextStyle,
                fontSize = 11.sp,
                color = if (isPreviewing) VigilDirAAmber else VigilDirADim,
                modifier = Modifier.clickable(onClick = onTogglePreview)
            )
            Text(
                text = "删除",
                fontSize = 11.sp,
                color = VigilDirADim,
                modifier = Modifier.clickable(onClick = onDelete)
            )
        }
    }
}

/** 重命名弹窗（与 AddKeywordDialog 同一风格） */
@Composable
private fun RenameRingtoneDialog(
    oldName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(oldName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VigilDirABg,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, VigilDirALine, RoundedCornerShape(8.dp)),
        title = {
            Text(
                text = "重命名铃声",
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
                placeholder = { Text("输入名称", fontSize = 14.sp, color = VigilDirADim) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VigilDirAAcid,
                    unfocusedBorderColor = VigilDirADim,
                    focusedTextColor = VigilDirAInk,
                    unfocusedTextColor = VigilDirAInk,
                    cursorColor = VigilDirAAcid
                ),
                shape = RoundedCornerShape(4.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = input.isNotBlank()
            ) {
                Text(
                    text = "保存",
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

private val MonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace)
