package com.example.vigil.ui.history

import android.app.Application
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.data.AlertEndType
import com.example.vigil.data.AlertRecord
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.ui.settings.SettingsViewModelFactory
import com.example.vigil.ui.theme.VigilPrimary
import com.example.vigil.ui.theme.VigilError
import com.example.vigil.ui.theme.VigilBackground
import com.example.vigil.ui.theme.VigilTextSecondary
import com.example.vigil.ui.theme.VigilTextPrimary
import com.example.vigil.ui.theme.VigilDivider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「A · 一线」报警记录页：与应用过滤页同一语言——暗底、发丝线分区、等宽元信息。
 * 记录由服务在报警结束（手动确认 / 循环次数用完自动结束）时写入，本页只读 + 清空。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlertHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    // 清空操作通过 version 状态触发重组重新读取 prefs
    val historyVersion by viewModel.alertHistoryVersion
    val records = remember(historyVersion) { viewModel.getAlertHistory() }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VigilBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)
    ) {
        // ---- 顶部：返回 + 标题 + 清空 ----
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
                    Text(text = "←", fontSize = 18.sp, color = VigilTextSecondary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "HISTORY // 报警记录",
                    style = BodyTextStyle,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = VigilTextPrimary
                )
            }
            if (records.isNotEmpty()) {
                Text(
                    text = "CLEAR",
                    style = BodyTextStyle,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = VigilError,
                    modifier = Modifier
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }

        // ---- 统计 ----
        Text(
            text = "${records.size} 条记录",
            style = BodyTextStyle,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = VigilTextSecondary,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(VigilDivider)
        )

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无报警记录",
                    fontSize = 13.sp,
                    color = VigilTextSecondary
                )
            }
        } else {
            LazyColumn {
                items(records, key = { it.id }) { record ->
                    AlertRecordRow(record, modifier = Modifier.animateItemPlacement())
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = VigilBackground,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, VigilDivider, RoundedCornerShape(8.dp)),
            title = {
                Text(
                    text = "清空报警记录",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = VigilTextPrimary
                )
            },
            text = {
                Text(
                    text = "确认清空全部 ${records.size} 条报警记录？此操作不可撤销。",
                    fontSize = 13.sp,
                    color = VigilTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAlertHistory()
                    showClearConfirm = false
                }) {
                    Text(text = "清空", color = VigilError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = "取消", color = VigilTextSecondary)
                }
            }
        )
    }
}

/** 单条记录：关键词 + 结束方式徽标 / 来源应用 + 时间，底部发丝线 */
@Composable
private fun AlertRecordRow(record: AlertRecord, modifier: Modifier = Modifier) {
    val rowLineColor = VigilDivider
    val timeText = remember(record.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(record.timestamp))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    rowLineColor,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            }
            .padding(horizontal = 2.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = record.keyword,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = VigilTextPrimary
            )
            Text(
                text = when (record.endType) {
                    AlertEndType.AUTO -> "自动结束"
                    AlertEndType.MANUAL -> "手动确认"
                    AlertEndType.ERROR -> "播放失败"
                },
                style = BodyTextStyle,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = if (record.endType == AlertEndType.AUTO) VigilError else VigilTextSecondary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = record.sourceApp ?: "未知来源",
                fontSize = 11.sp,
                color = VigilTextSecondary
            )
            Text(
                text = timeText,
                style = BodyTextStyle,
                fontSize = 10.sp,
                color = VigilTextSecondary
            )
        }
    }
}

private val BodyTextStyle = TextStyle(fontFamily = FontFamily.SansSerif)
