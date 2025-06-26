// src/main/java/com/example/vigil/ui/settings/SettingsScreen.kt
package com.example.vigil.ui.settings

import android.app.Activity
import android.app.Application
import android.app.AppOpsManager // 新增
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error // 新增：用于表示需要操作或未授权
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid // 新增：MIUI 图标示例
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.ui.theme.VigilTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application))
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 权限状态
    val hasNotificationAccess by viewModel.hasNotificationAccess
    val hasDndAccess by viewModel.hasDndAccess
    val canDrawOverlays by viewModel.canDrawOverlays
    val canPostNotifications by viewModel.canPostNotifications

    // MIUI 相关状态
    val isMiuiDevice by viewModel.isMiuiDevice
    val miuiBackgroundPopupPermissionStatus by viewModel.miuiBackgroundPopupPermissionStatus

    // 应用过滤状态
    val isAppFilterEnabled by viewModel.isAppFilterEnabled
    val isLoadingApps by viewModel.isLoadingApps
    val showOnlySelected by viewModel.showOnlySelectedApps
    val searchQuery by viewModel.searchQuery
    val filteredApps = viewModel.getFilteredApps()
    
    // 关键词和铃声设置
    val keywords by viewModel.keywords
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
        
        // 当通知访问权限获得时，刷新应用列表
        if (hasNotificationAccess) {
            Log.d("SettingsScreen", "通知权限已获取，正在刷新应用列表")
            viewModel.loadInstalledApps()
        }
        
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
        // MIUI 权限请求回调
        viewModel.requestMiuiBackgroundPopupPermissionCallback = {
            activity?.let { PermissionUtils.requestMiuiBackgroundPopupPermission(it) }
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
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            // 后台弹窗权限 - 现在适用于所有设备
            PermissionItem(
                title = stringResource(R.string.permission_background_popup_title),
                description = stringResource(R.string.permission_background_popup_description),
                isChecked = canDrawOverlays, // 与悬浮窗权限保持一致
                onClick = { 
                    // 对于MIUI设备使用专用方法，其他设备则直接使用悬浮窗权限方法
                    if (isMiuiDevice) {
                        viewModel.requestMiuiBackgroundPopupPermissionCallback?.invoke()
                        Toast.makeText(context, "正在跳转到后台弹窗权限设置", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.requestOverlayPermissionCallback?.invoke()
                        Toast.makeText(context, "正在跳转到悬浮窗权限设置", Toast.LENGTH_SHORT).show()
                    }
                },
                icon = Icons.Filled.PhoneAndroid
            )
        }
        
        // 关键词监控设置区域
        SettingsSection(title = stringResource(R.string.notification_configuration_title)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 使用提取出来的关键词输入组件
                KeywordInput(
                    keywords = keywords,
                    onKeywordsChange = { viewModel.onKeywordsChange(it) },
                    onSave = { viewModel.saveSettings() }
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
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                
                // 保存按钮
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.save_settings_button))
                    }
                }
            }
        }

        // 应用过滤设置区域
        SettingsSection(title = stringResource(R.string.app_filter_settings_title)) {
            // 应用过滤开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.onAppFilterEnabledChange(!isAppFilterEnabled) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isAppFilterEnabled) 
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
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (isAppFilterEnabled) 
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
                            text = stringResource(R.string.filter_apps_switch_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isAppFilterEnabled) stringResource(R.string.filter_apps_switch_summary_on)
                            else stringResource(R.string.filter_apps_switch_summary_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Switch(
                    checked = isAppFilterEnabled,
                    onCheckedChange = { viewModel.onAppFilterEnabledChange(it) },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            // 应用过滤说明文本
            Text(
                text = stringResource(R.string.filter_apps_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            
            // 替换刷新按钮部分
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 添加应用计数显示
                if (!isLoadingApps) {
                    Text(
                        text = stringResource(
                            R.string.app_count_format, 
                            filteredApps.size, 
                            filteredApps.count { it.isSelected }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                
                Button(
                    onClick = { viewModel.loadInstalledApps() },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh, 
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh_app_list))
                    }
                }
            }
            
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                label = { Text(stringResource(R.string.search_apps_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                }
            )
            
            // "仅显示已选择的应用" 开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.toggleShowOnlySelected() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showOnlySelected,
                    onCheckedChange = { viewModel.toggleShowOnlySelected() }
                )
                Text(
                    text = stringResource(R.string.show_only_selected_apps),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 应用列表
            if (isLoadingApps) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Text(
                        text = stringResource(R.string.no_apps_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 移除应用列表周围的卡片，直接显示列表
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    tonalElevation = 0.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps) { app ->
                            AppItem(
                                app = app,
                                onToggleSelection = { viewModel.toggleAppSelection(app.packageName) }
                            )
                            if (filteredApps.indexOf(app) < filteredApps.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        val uriHandler = LocalUriHandler.current
        val donateUrl = stringResource(R.string.donate_url)
        
        // 关于与支持区域
        SettingsSection(title = stringResource(R.string.about_title)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 添加捐赠信息
                Text(
                    text = stringResource(R.string.donate_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                
                // 捐赠按钮
                Button(
                    onClick = { try { uriHandler.openUri(donateUrl) } catch (e: Exception) { /* 处理错误 */ } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.donate_button))
                }
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

@Composable
fun AppItem(
    app: AppInfo,
    onToggleSelection: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        try {
            val packageManager = context.packageManager
            packageManager.getApplicationIcon(app.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelection) // 整行可点击
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标
        appIcon?.let { icon ->
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        } ?: Box(modifier = Modifier.size(40.dp))
        
        // 应用信息
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                // 添加系统应用标记
                if (app.isSystemApp) {
                    Surface(
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = stringResource(R.string.system_app_label),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 复选框 - 确保它也可以独立点击
        Box(
            modifier = Modifier
                .clickable(
                    onClick = onToggleSelection,
                    indication = null, // 移除点击指示效果，防止与父级重叠
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(8.dp) // 增加可点击区域
        ) {
            Checkbox(
                checked = app.isSelected,
                onCheckedChange = { onToggleSelection() }
            )
        }
    }
}

@Composable
fun KeywordChips(keywords: String) {
    val keywordList = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (keywordList.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            var currentRowTags = mutableListOf<String>()
            var currentRowWidth = 0f
            val density = LocalDensity.current
            val maxWidth = with(density) { 
                LocalConfiguration.current.screenWidthDp.dp.toPx() - 32.dp.toPx() 
            }
            
            // 测量每个标签的宽度并组织成多行
            keywordList.forEachIndexed { index, keyword ->
                val textMeasurer = rememberTextMeasurer()
                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(keyword),
                    style = MaterialTheme.typography.bodyMedium
                )
                val tagWidth = with(density) { 
                    textLayoutResult.size.width.toFloat() + 16.dp.toPx() + 16.dp.toPx() // 文本宽度+左右padding
                }
                
                // 如果当前行放不下这个标签，开始一个新行
                if (currentRowWidth + tagWidth > maxWidth) {
                    // 显示当前行的所有标签
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        currentRowTags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    // 重置当前行，将当前标签作为新行的第一个
                    currentRowTags = mutableListOf(keyword)
                    currentRowWidth = tagWidth
                } else {
                    // 当前行可以放下这个标签
                    currentRowTags.add(keyword)
                    currentRowWidth += tagWidth
                }
                
                // 如果是最后一个标签，确保显示最后一行
                if (index == keywordList.size - 1 && currentRowTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        currentRowTags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 创建一个组件专门处理关键词输入、显示和编辑
@Composable
fun KeywordInput(
    keywords: String,
    onKeywordsChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    
    // 标题和关键词编辑按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.keywords_label),
            style = MaterialTheme.typography.titleMedium
        )
        
        // 编辑/确认按钮
        if (!isEditing) {
            IconButton(onClick = { isEditing = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑关键词",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Row {
                // 取消按钮
                IconButton(onClick = { isEditing = false }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                // 保存按钮
                IconButton(
                    onClick = { 
                        isEditing = false
                        onSave()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "保存",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // 显示格式化的关键词
    KeywordChips(keywords)
    
    // 可编辑文本框，仅在编辑状态下显示
    AnimatedVisibility(
        visible = isEditing,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        OutlinedTextField(
            value = keywords,
            onValueChange = onKeywordsChange,
            placeholder = { Text(stringResource(R.string.keywords_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = MaterialTheme.shapes.small,
            minLines = 2,
            maxLines = 4
        )
    }
    
    // 添加提示文本
    val keywordList = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    Text(
        text = if (keywordList.isEmpty()) 
            "请添加需要监控的关键词" 
        else 
            "系统将自动监控包含上述关键词的通知",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
    )
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
