package com.example.vigil.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.alert.RingtoneLibrary
import com.example.vigil.ui.components.VigilDialog
import com.example.vigil.ui.components.VigilPillChip
import com.example.vigil.ui.components.VigilPrimaryButton
import com.example.vigil.ui.components.VigilSectionCard
import com.example.vigil.ui.components.VigilTopBar
import com.example.vigil.ui.dialogs.PermissionGuideDialog
import com.example.vigil.util.PermissionUtils
import kotlinx.coroutines.delay

/**
 * 统一风格铃声库页（暖黑石墨 + 琥珀金 · 圆角卡片语言）。
 */
@Composable
fun RingtoneLibraryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val entries = viewModel.ringtoneLibrary
    @Suppress("UNUSED_VARIABLE") val previewVersion = viewModel.previewVersion.value
    @Suppress("UNUSED_VARIABLE") val recordingVersion = viewModel.recordingVersion.value
    val previewing = viewModel.previewingFileName
    val isRecording = viewModel.isRecording

    var showMicGuide by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var recordSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRecording) {
        recordSeconds = 0L
        while (isRecording) {
            delay(1000)
            recordSeconds++
        }
    }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(2400)
            statusBanner = null
        }
    }

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
                statusBanner = if (ok) "已导入铃声" else "导入失败"
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        VigilTopBar(title = "铃声库", onBack = onNavigateBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 导入与录音操作卡
            item {
                VigilSectionCard(title = "添加自定义铃声") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VigilPrimaryButton(
                            text = "导入音频",
                            onClick = { importLauncher.launch(arrayOf("audio/*")) },
                            enabled = !isRecording,
                            modifier = Modifier.weight(1f)
                        )
                        VigilPrimaryButton(
                            text = if (isRecording) "停止录音 ${recordSeconds}s" else "录制铃声",
                            danger = isRecording,
                            onClick = {
                                if (isRecording) {
                                    val saved = viewModel.stopRecording()
                                    statusBanner = if (saved != null) "录音已保存" else "录音过短或失败"
                                } else if (PermissionUtils.isRecordAudioGranted(context)) {
                                    viewModel.startRecording()
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (statusBanner != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(statusBanner!!, color = colors.primary, fontSize = 12.sp)
                    }
                }
            }

            // 内置预设语音
            item {
                VigilSectionCard(title = "内置语音") {
                    RingtoneLibrary.PRESETS.forEachIndexed { idx, preset ->
                        if (idx > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                        val playing = previewing == "preset:${preset.rawName}"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.displayName,
                                color = colors.onSurface,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            VigilPillChip(
                                label = if (playing) "停止" else "试听",
                                selected = playing,
                                onClick = { viewModel.togglePresetPreview(preset) }
                            )
                        }
                    }
                }
            }

            // 我的自定义铃声
            item {
                Text(
                    text = "我的铃声 (${entries.size})",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            if (entries.isEmpty()) {
                item {
                    VigilSectionCard {
                        Text("暂无自定义铃声", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else {
                items(entries, key = { it.first }) { (fileName, displayName) ->
                    val playing = previewing == fileName
                    VigilSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = displayName,
                                color = colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            VigilPillChip(
                                label = if (playing) "停止" else "试听",
                                selected = playing,
                                onClick = { viewModel.togglePreview(fileName) }
                            )
                            IconButton(
                                onClick = { renameTarget = fileName to displayName },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "重命名", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { deleteTarget = fileName to displayName },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = colors.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    renameTarget?.let { (fileName, oldName) ->
        var text by remember(fileName) { mutableStateOf(oldName) }
        VigilDialog(
            title = "重命名铃声",
            onDismiss = { renameTarget = null },
            confirmText = "保存",
            confirmEnabled = text.trim().isNotEmpty(),
            onConfirm = {
                viewModel.renameLibraryRingtone(fileName, text.trim())
                renameTarget = null
            }
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    deleteTarget?.let { (fileName, name) ->
        VigilDialog(
            title = "删除铃声",
            onDismiss = { deleteTarget = null },
            confirmText = "删除",
            dangerConfirm = true,
            onConfirm = {
                viewModel.deleteLibraryRingtone(fileName)
                deleteTarget = null
            }
        ) {
            Text("确定删除「$name」？", color = colors.onSurfaceVariant, fontSize = 13.sp)
        }
    }

    if (showMicGuide) {
        PermissionGuideDialog(
            title = "需要录音权限",
            message = "录制自定义铃声需要麦克风权限。",
            onConfirm = { (context as? android.app.Activity)?.let(PermissionUtils::openAppDetailsSettings) },
            onDismiss = { showMicGuide = false }
        )
    }
}
