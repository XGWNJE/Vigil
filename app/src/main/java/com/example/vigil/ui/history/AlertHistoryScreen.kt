package com.example.vigil.ui.history

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.vigil.data.AlertEndType
import com.example.vigil.data.AlertRecord
import com.example.vigil.ui.components.VigilDialog
import com.example.vigil.ui.components.VigilPillChip
import com.example.vigil.ui.components.VigilSectionCard
import com.example.vigil.ui.components.VigilTopBar
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.ui.settings.SettingsViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一风格报警记录页（也支持作为主导航「记录」页内容嵌入）。
 */
@Composable
fun AlertHistoryScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val colors = MaterialTheme.colorScheme
    val historyVersion by viewModel.alertHistoryVersion
    val records = remember(historyVersion) { viewModel.getAlertHistory() }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .then(if (onNavigateBack != null) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
    ) {
        VigilTopBar(
            title = "报警记录",
            onBack = onNavigateBack,
            trailing = if (records.isNotEmpty()) {
                {
                    VigilPillChip(
                        label = "清空",
                        selected = false,
                        danger = true,
                        onClick = { showClearConfirm = true }
                    )
                }
            } else null
        )

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无报警记录", color = colors.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    AlertRecordCard(record)
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (showClearConfirm) {
        VigilDialog(
            title = "清空报警记录",
            onDismiss = { showClearConfirm = false },
            confirmText = "清空",
            dangerConfirm = true,
            onConfirm = {
                viewModel.clearAlertHistory()
                showClearConfirm = false
            }
        ) {
            Text(
                text = "将清除全部 ${records.size} 条记录。",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlertRecordCard(record: AlertRecord) {
    val colors = MaterialTheme.colorScheme
    val timeText = remember(record.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(record.timestamp))
    }
    val statusText = when (record.endType) {
        AlertEndType.MANUAL -> "已确认"
        AlertEndType.AUTO -> "自动结束"
        AlertEndType.ERROR -> "播放失败"
    }
    val statusColor = when (record.endType) {
        AlertEndType.MANUAL -> colors.primary
        AlertEndType.AUTO -> colors.onSurfaceVariant
        AlertEndType.ERROR -> colors.error
    }

    VigilSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = record.keyword,
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.sourceApp ?: "本机通知",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = timeText,
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
