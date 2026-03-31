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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.ui.theme.*

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

    // 应用过滤状态
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

    LaunchedEffect(hasNotificationAccess) {
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
            .background(VigilBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // TopBar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Vigil",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Permission Group
            SettingsGroup(title = "权限状态") {
                PermRow(
                    icon = Icons.Filled.Notifications,
                    label = "通知使用权",
                    iconTint = if (hasNotificationAccess) VigilSuccess else VigilWarning,
                    isGranted = hasNotificationAccess,
                    onClick = {
                        viewModel.requestNotificationListenerPermissionCallback?.invoke()
                        Toast.makeText(context, "正在跳转到通知使用权设置", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = VigilBorder, thickness = 0.5.dp)
                PermRow(
                    icon = Icons.Filled.Layers,
                    label = "悬浮窗权限",
                    iconTint = if (canDrawOverlays) VigilSuccess else VigilWarning,
                    isGranted = canDrawOverlays,
                    onClick = {
                        viewModel.requestOverlayPermissionCallback?.invoke()
                        Toast.makeText(context, "正在跳转到悬浮窗权限设置", Toast.LENGTH_SHORT).show()
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(color = VigilBorder, thickness = 0.5.dp)
                    PermRow(
                        icon = Icons.Filled.Send,
                        label = "发送通知权限",
                        iconTint = if (canPostNotifications) VigilSuccess else VigilWarning,
                        isGranted = canPostNotifications,
                        onClick = {
                            viewModel.requestPostNotificationsPermissionCallback?.invoke()
                            Toast.makeText(context, "正在请求发送通知权限", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Ringtone Group
            SettingsGroup(title = "报警铃声") {
                Row(
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
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = VigilPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedRingtoneName,
                            fontSize = 14.sp,
                            color = VigilTextPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = VigilTextDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Keyword Group
            SettingsGroup(title = "监控关键词") {
                var inputText by remember { mutableStateOf("") }

                // Keywords chips
                if (keywordList.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(keywordList) { keyword ->
                            Row(
                                modifier = Modifier
                                    .background(VigilChipBackground, RoundedCornerShape(9999.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = keyword,
                                    fontSize = 12.sp,
                                    color = VigilChipText
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "删除",
                                    tint = VigilTextTertiary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.removeKeyword(keyword) }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "暂无关键词",
                        fontSize = 12.sp,
                        color = VigilTextDisabled
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("输入关键词", fontSize = 14.sp, color = VigilTextDisabled)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VigilPrimary,
                            unfocusedBorderColor = VigilBorder,
                            focusedTextColor = VigilTextPrimary,
                            unfocusedTextColor = VigilTextPrimary,
                            cursorColor = VigilPrimary,
                            focusedContainerColor = VigilSurface,
                            unfocusedContainerColor = VigilSurface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "添加",
                        tint = if (inputText.isNotBlank()) VigilPrimary else VigilTextDisabled,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = inputText.isNotBlank()) {
                                viewModel.addKeyword(inputText)
                                inputText = ""
                            }
                    )
                }
            }

            // App Filter Group
            SettingsGroup(title = "应用过滤") {
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
                            text = "仅监听指定应用",
                            fontSize = 14.sp,
                            color = VigilTextPrimary
                        )
                        Text(
                            text = if (isAppFilterEnabled) "已开启 · 监听部分应用" else "已关闭 · 监听所有应用",
                            fontSize = 11.sp,
                            color = VigilTextDisabled
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = VigilTextDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VigilSurface, RoundedCornerShape(14.dp))
            .border(1.dp, VigilBorder, RoundedCornerShape(14.dp))
            .padding(14.dp, 16.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = VigilTextTertiary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun PermRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: androidx.compose.ui.graphics.Color,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = VigilTextPrimary
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = VigilSuccess,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .background(VigilWarning.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "去设置",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = VigilWarning
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
