package com.example.vigil.ui.settings

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.ui.components.VigilPillChip
import com.example.vigil.ui.components.VigilSectionCard
import com.example.vigil.ui.components.VigilSettingRow
import com.example.vigil.ui.components.VigilTopBar

/**
 * 统一风格应用过滤页（暖黑石墨 + 琥珀金，圆角卡片语言）。
 */
@Composable
fun AppFilterScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val colors = MaterialTheme.colorScheme
    val isAppFilterEnabled by viewModel.isAppFilterEnabled
    val isLoadingApps by viewModel.isLoadingApps
    val searchQuery by viewModel.searchQuery
    val showOnlySelected by viewModel.showOnlySelectedApps
    val filteredApps = viewModel.getFilteredApps()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        VigilTopBar(
            title = "应用过滤",
            onBack = onNavigateBack,
            trailing = {
                IconButton(onClick = { viewModel.loadInstalledApps() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = colors.onSurfaceVariant)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VigilSectionCard {
                VigilSettingRow(
                    title = "仅监听指定应用",
                    trailingContent = {
                        Switch(
                            checked = isAppFilterEnabled,
                            onCheckedChange = { viewModel.onAppFilterEnabledChange(it) }
                        )
                    }
                )
            }

            if (isAppFilterEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        singleLine = true,
                        placeholder = { Text("搜索应用", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outlineVariant,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    VigilPillChip(
                        label = "已选",
                        selected = showOnlySelected,
                        onClick = { viewModel.toggleShowOnlySelected() }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (!isAppFilterEnabled) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前监听全部应用通知", color = colors.onSurfaceVariant, fontSize = 13.sp)
            }
        } else if (isLoadingApps) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary, strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppFilterItemCard(
                        app = app,
                        onToggle = { viewModel.toggleAppSelection(app.packageName) }
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun AppFilterItemCard(
    app: AppInfo,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    VigilSectionCard(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (app.isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (app.isSelected) colors.primary else colors.outline,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
