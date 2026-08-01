// src/main/java/com/example/vigil/ui/dialogs/KeywordAlertDialog.kt
package com.example.vigil.ui.dialogs

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.vigil.ui.main.rememberFrameDrivenProgress
import com.example.vigil.ui.theme.VigilDirAAcid
import com.example.vigil.ui.theme.VigilDirABg
import com.example.vigil.ui.theme.VigilDirADim
import com.example.vigil.ui.theme.VigilDirAInk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「A · 一线」报警弹窗：酸橙绿 1dp 边框大框 + 等宽元信息 + 实心大按钮。
 * 确认/关闭逻辑与原实现一致。
 */
@Composable
fun KeywordAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    matchedKeyword: String?,
    sourceApp: String? = null,
    snippet: String? = null,
    eventTimeMillis: Long? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 进入动效：整体淡入 + 内容轻微上滑（帧驱动进度，避免 InfiniteTransition 被动画缩放挂起）
        var enterProgress by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = System.nanoTime()
            val durationMs = 260L
            while (enterProgress < 1f) {
                withFrameNanos {
                    enterProgress = ((System.nanoTime() - start) / 1_000_000L / durationMs.toFloat())
                        .coerceIn(0f, 1f)
                }
            }
        }
        val enterAlpha = FastOutSlowInEasing.transform(enterProgress)
        val enterOffset = ((1f - FastOutSlowInEasing.transform(enterProgress)) * 40).dp
        // 酸橙边框呼吸脉冲：报警是强提醒，边框 1.2s 周期全幅明暗（0.25→1）
        val pulse = rememberFrameDrivenProgress(1200)
        val pulseTri = if (pulse < 0.5f) pulse * 2f else (1f - pulse) * 2f
        val borderAlpha = 0.25f + 0.75f * FastOutSlowInEasing.transform(pulseTri)

        // 外层：暗底，内容整体居中，避开系统手势栏/导航栏
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VigilDirABg)
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .graphicsLayer { alpha = enterAlpha }
                .offset(y = enterOffset),
            contentAlignment = Alignment.Center
        ) {
            // 内层：酸橙绿 1dp 大框（呼吸脉冲），wrap 内容并垂直居中
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VigilDirAAcid.copy(alpha = borderAlpha))
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "KEYWORD ALERT // 关键词报警",
                    style = MonoTextStyle,
                    fontSize = 10.sp,
                    letterSpacing = 3.sp,
                    color = VigilDirAAcid
                )

                Text(
                    text = matchedKeyword ?: "未知",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp,
                    lineHeight = 44.sp,
                    color = VigilDirAInk
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AlertMetaRow("FROM", sourceApp?.takeIf { it.isNotBlank() } ?: "--")
                    AlertMetaRow("TEXT", snippet?.takeIf { it.isNotBlank() } ?: "--", maxLines = 3)
                    AlertMetaRow("TIME", formatAlertTime(eventTimeMillis))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部：酸橙绿实心大按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VigilDirAAcid, RoundedCornerShape(2.dp))
                        .clickable {
                            onConfirm()
                            onDismissRequest()
                        }
                        .padding(17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "已知晓，停止报警",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = VigilDirABg
                    )
                }
            }
        }
    }
}

private val MonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace)

@Composable
private fun AlertMetaRow(label: String, value: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MonoTextStyle,
            fontSize = 11.sp,
            color = VigilDirADim
        )
        Text(
            text = value,
            style = MonoTextStyle,
            fontSize = 11.sp,
            color = VigilDirAInk,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatAlertTime(eventTimeMillis: Long?): String {
    val millis = eventTimeMillis ?: System.currentTimeMillis()
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
