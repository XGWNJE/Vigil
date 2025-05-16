// src/main/java/com/example/vigil/ui/settings/SettingsScreen.kt
package com.example.vigil.ui.settings

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
// import com.example.vigil.BuildConfig // 暂时注释，如果IDE能找到就取消注释
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.ui.theme.VigilTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application))
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val hasNotificationAccess by viewModel.hasNotificationAccess
    val hasDndAccess by viewModel.hasDndAccess
    val canDrawOverlays by viewModel.canDrawOverlays
    val canPostNotifications by viewModel.canPostNotifications

    val isAppFilterEnabled by viewModel.isAppFilterEnabled
    val licenseKeyInput by viewModel.licenseKeyInput
    val licenseStatusText by viewModel.licenseStatusText

    LaunchedEffect(Unit) {
        viewModel.updatePermissionStates()
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
                icon = Icons.Filled.Notifications
            )
            PermissionItem(
                title = stringResource(R.string.grant_overlay_permission_button),
                isChecked = canDrawOverlays,
                onClick = { viewModel.requestOverlayPermissionCallback?.invoke() },
                icon = Icons.Filled.Settings
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = stringResource(R.string.grant_post_notifications_permission_button),
                    isChecked = canPostNotifications,
                    onClick = { viewModel.requestPostNotificationsPermissionCallback?.invoke() },
                    icon = Icons.Filled.Notifications
                )
            }
        }

        SettingsCard(title = stringResource(R.string.app_filter_settings_title)) {
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
            Text(
                text = stringResource(R.string.filter_apps_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        SettingsCard(title = stringResource(R.string.license_key_title)) {
            OutlinedTextField(
                value = licenseKeyInput,
                onValueChange = { viewModel.onLicenseKeyInputChange(it) },
                label = { Text(stringResource(R.string.license_key_label)) },
                placeholder = { Text(stringResource(R.string.license_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val currentAppId = context.packageName // 使用 context.packageName 作为备选
                    // 如果 BuildConfig 问题解决，可以换回: com.example.vigil.BuildConfig.APPLICATION_ID
                    viewModel.activateLicense(currentAppId)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.activate_license_button))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.license_status_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = licenseStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (viewModel.isLicensed.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        val uriHandler = LocalUriHandler.current
        val githubUrl = stringResource(R.string.github_url)
        SettingsCard(title = stringResource(R.string.about_title)) { // 使用字符串资源
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { try { uriHandler.openUri(githubUrl) } catch (e: Exception) { /* 处理无法打开链接的情况 */ } }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.github_link_desc)) // 使用字符串资源
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.visit_project_link), style = MaterialTheme.typography.bodyLarge) // 使用字符串资源
            }
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
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title, // contentDescription 应该是描述性的
                tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = if (isChecked) stringResource(R.string.permission_granted_desc) else stringResource(R.string.permission_not_granted_desc),
            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
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
            viewModel = SettingsViewModel(LocalContext.current.applicationContext as Application)
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SettingsScreenDarkPreview() {
    VigilTheme(darkTheme = true) {
        SettingsScreen(
            viewModel = SettingsViewModel(LocalContext.current.applicationContext as Application)
        )
    }
}
