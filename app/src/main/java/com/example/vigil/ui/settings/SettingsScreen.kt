// src/main/java/com/example/vigil/ui/settings/SettingsScreen.kt
package com.example.vigil.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.ui.theme.VigilTheme
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application)),
    onNavigateToAppFilter: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 权限状态
    val hasNotificationAccess by viewModel.hasNotificationAccess
    val hasDndAccess by viewModel.hasDndAccess
    val canDrawOverlays by viewModel.canDrawOverlays
    val canPostNotifications by viewModel.canPostNotifications

    // 应用过滤状态（仅用于导航入口显示文字）
    val isAppFilterEnabled by viewModel.isAppFilterEnabled
    
    // 关键词和铃声设置
    val keywordList = viewModel.keywordList
    val selectedRingtoneName by viewModel.selectedRingtoneName
    val ringtoneSelectionTitle = stringResource(R.string.ringtone_selection_title)

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
            viewModel.onRingtoneUriSelected(uri)
        }
    }

    // 监听权限变化，当获取通知权限后刷新应用列表
    LaunchedEffect(hasNotificationAccess) {
        viewModel.updatePermissionStates() // This will also update MIUI permission status
        
        // 移除自动刷新应用列表的代码，仅在用户主动刷新时获取列表
        
        viewModel.requestNotificationListenerPermissionCallback = {
            activity?.let { PermissionUtils.requestNotificationListenerPermission(it) }
        }
        viewModel.requestDndAccessPermissionCallback = {
            activity?.let { PermissionUtils.requestDndAccessPermission(it) }
        }
        viewModel.requestOverlayPermissionCallback = {
            activity?.let { PermissionUtils.requestOverlayPermission(it) }
        }
        viewModel.requestPostNotificationsPermissionCallback = {
            activity?.let { PermissionUtils.requestPostNotificationsPermission(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 必需权限设置区域
        SettingsSection(title = stringResource(R.string.permissions_settings_title)) {
            // 通知监听权限
            PermissionItem(
                title = stringResource(R.string.grant_notification_access_button),
                isChecked = hasNotificationAccess,
                onClick = { 
                    viewModel.requestNotificationListenerPermissionCallback?.invoke()
                    // 显示一个Toast提示，帮助用户知道点击有效果
                    Toast.makeText(context, "正在跳转到通知使用权设置", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Filled.Notifications
            )
            
            // 勿扰模式权限
            PermissionItem(
                title = stringResource(R.string.grant_dnd_access_button),
                isChecked = hasDndAccess,
                onClick = { 
                    viewModel.requestDndAccessPermissionCallback?.invoke()
                    Toast.makeText(context, "正在跳转到勿扰模式权限设置", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Filled.Notifications
            )
            
            // 悬浮窗权限
            PermissionItem(
                title = stringResource(R.string.grant_overlay_permission_button),
                isChecked = canDrawOverlays,
                onClick = { 
                    viewModel.requestOverlayPermissionCallback?.invoke()
                    Toast.makeText(context, "正在跳转到悬浮窗权限设置", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Filled.Settings
            )
            
            // 发送通知权限(Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = stringResource(R.string.grant_post_notifications_permission_button),
                    isChecked = canPostNotifications,
                    onClick = { 
                        viewModel.requestPostNotificationsPermissionCallback?.invoke()
                        Toast.makeText(context, "正在请求发送通知权限", Toast.LENGTH_SHORT).show()
                    },
                    icon = Icons.Filled.Notifications
                )
            }
            
        }

        // 关键词监控设置区域
        SettingsSection(title = stringResource(R.string.notification_configuration_title)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                KeywordChipInput(
                    keywords = keywordList,
                    onAdd = { viewModel.addKeyword(it) },
                    onRemove = { viewModel.removeKeyword(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // 提醒音设置部分
                Text(
                    text = stringResource(R.string.select_ringtone_button),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtoneSelectionTitle)
                                viewModel.selectedRingtoneUri.value?.let { uri ->
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
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = selectedRingtoneName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 提示文本
                Text(
                    text = "当检测到关键词时，将播放此提醒音",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
        }

        // 应用过滤设置区域 — 导航到独立全屏页，避免嵌套滚动
        SettingsSection(title = stringResource(R.string.app_filter_settings_title)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAppFilter() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.filter_apps_switch_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (isAppFilterEnabled)
                            stringResource(R.string.filter_apps_switch_summary_on)
                        else
                            stringResource(R.string.filter_apps_switch_summary_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // 分区标题
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        // 分隔线
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // 内容
        this.content()
    }
}

@Composable
fun PermissionItem(
    title: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    description: String? = null // 可选的描述文本
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() } // 确保点击响应整个区域
            .padding(vertical = 8.dp),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 图标使用圆形背景
                Surface(
                    color = if (isChecked) 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isChecked) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 状态指示器
            if (isChecked) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.permission_granted_desc),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                // 当权限未授予时，显示"前往设置"按钮而不是图标
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.permission_status_action_required),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}


/**
 * Chip 标签式关键词输入组件。
 * 每个关键词显示为可删除的 chip，通过单行输入框逐条添加，自动保存，无需"保存"按钮。
 */
@Composable
fun KeywordChipInput(
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题
        Text(
            text = "监控关键词",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 已添加的关键词 chip 列表
        if (keywords.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(keywords) { keyword ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = keyword,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onRemove(keyword) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = "请添加需要监控的关键词",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 单条添加输入框
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入关键词") },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onAdd(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "添加",
                    tint = if (inputText.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    VigilTheme {
        SettingsScreen(
            viewModel = SettingsViewModel(LocalContext.current.applicationContext as Application).apply {
                // For previewing MIUI item
                // this._isMiuiDevice.value = true
                // this._miuiBackgroundPopupPermissionStatus.value = AppOpsManager.MODE_IGNORED
            }
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SettingsScreenDarkPreview() {
    VigilTheme(darkTheme = true) {
        SettingsScreen(
            viewModel = SettingsViewModel(LocalContext.current.applicationContext as Application).apply {
                // this._isMiuiDevice.value = true
            }
        )
    }
}
