// src/main/java/com/example/vigil/ui/settings/SettingsScreen.kt
package com.example.vigil.ui.settings

import android.app.Activity
import android.app.Application
import android.app.AppOpsManager // 新增
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error // 新增：用于表示需要操作或未授权
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid // 新增：MIUI 图标示例
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton // 新增：用于权限项的按钮
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        SettingsCard(title = stringResource(R.string.permissions_settings_title)) {
            PermissionItem(
                title = stringResource(R.string.grant_notification_access_button),
                isChecked = hasNotificationAccess,
                onClick = { viewModel.requestNotificationListenerPermissionCallback?.invoke() },
                icon = Icons.Filled.Notifications
            )
            PermissionItem(
                title = stringResource(R.string.grant_dnd_access_button),
                isChecked = hasDndAccess,
                onClick = { viewModel.requestDndAccessPermissionCallback?.invoke() },
                icon = Icons.Filled.Notifications // Consider a DND specific icon
            )
            PermissionItem(
                title = stringResource(R.string.grant_overlay_permission_button),
                isChecked = canDrawOverlays,
                onClick = { viewModel.requestOverlayPermissionCallback?.invoke() },
                icon = Icons.Filled.Settings // Consider an overlay specific icon
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = stringResource(R.string.grant_post_notifications_permission_button),
                    isChecked = canPostNotifications,
                    onClick = { viewModel.requestPostNotificationsPermissionCallback?.invoke() },
                    icon = Icons.Filled.Notifications // Consider a post notification specific icon
                )
            }
            // MIUI 后台弹窗权限引导
            if (isMiuiDevice) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                MiuiPermissionItem(
                    title = stringResource(R.string.permission_miui_background_popup_title),
                    description = stringResource(R.string.permission_miui_background_popup_description),
                    // 对于 MIUI，我们不直接显示 "已授予/未授予"，而是提供一个操作按钮
                    // isChecked 的概念在这里不完全适用，因为我们无法准确检测状态
                    // 我们用 status 来决定右侧显示什么
                    status = miuiBackgroundPopupPermissionStatus,
                    onClick = { viewModel.requestMiuiBackgroundPopupPermissionCallback?.invoke() },
                    icon = Icons.Filled.PhoneAndroid // MIUI 特有权限的图标
                )
            }
        }

        SettingsCard(title = stringResource(R.string.app_filter_settings_title)) {
            // 应用过滤开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                Switch(
                    checked = isAppFilterEnabled,
                    onCheckedChange = { viewModel.onAppFilterEnabledChange(it) }
                )
            }
            
            // 应用过滤说明文本
            Text(
                text = stringResource(R.string.filter_apps_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            
            // 添加刷新按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                
                TextButton(
                    onClick = { viewModel.loadInstalledApps() }
                ) {
                    Text(stringResource(R.string.refresh_app_list))
                }
            }
            
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                label = { Text(stringResource(R.string.search_apps_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
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
        
        SettingsCard(title = stringResource(R.string.about_title)) {
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
                    modifier = Modifier.fillMaxWidth()
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
                    Card(
                        modifier = Modifier.padding(start = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
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
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            this.content()
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp), // 调整垂直内边距
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Icon(
            imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.Error, // 根据状态显示不同图标
            contentDescription = if (isChecked) stringResource(R.string.permission_granted_desc) else stringResource(R.string.permission_not_granted_desc),
            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(alpha = 0.7f) // 未授予时使用错误颜色
        )
    }
}

// 专门为 MIUI 权限项设计的 Composable
@Composable
fun MiuiPermissionItem(
    title: String,
    description: String,
    status: Int, // 使用 AppOpsManager 的 MODE_* 常量
    onClick: () -> Unit,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // 整个行可点击
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant // 中性颜色
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // 右侧显示一个操作按钮，而不是简单的勾选状态
        TextButton(onClick = onClick) {
            Text(
                text = stringResource(R.string.permission_status_action_required),
                color = MaterialTheme.colorScheme.primary
            )
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
