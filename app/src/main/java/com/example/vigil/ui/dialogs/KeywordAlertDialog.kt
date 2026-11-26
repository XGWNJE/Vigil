// src/main/java/com/example/vigil/ui/dialogs/KeywordAlertDialog.kt
package com.example.vigil.ui.dialogs

import android.os.Build
import android.view.WindowManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.vigil.ui.main.rememberFrameDrivenProgress
import com.example.vigil.ui.theme.VigilBackground
import com.example.vigil.ui.theme.VigilError
import com.example.vigil.ui.theme.VigilSurface
import com.example.vigil.ui.theme.VigilTextSecondary
import com.example.vigil.ui.theme.VigilTextPrimary
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
        // 横屏刘海/打孔避让修复：Dialog 窗口默认按 cutout 安全区内缩，
        // 横屏时挖孔（竖屏顶部）落在左侧，窗口被右推 136px 导致内容右缘被裁、按钮文字跑出屏外。
        // 本弹窗本来就是全屏暗底设计，声明 always 直接占用完整显示区。
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && dialogWindow != null) {
                val lp = dialogWindow.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                dialogWindow.attributes = lp
            }
        }

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
                .background(VigilBackground)
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .graphicsLayer { alpha = enterAlpha }
                .offset(y = enterOffset),
            contentAlignment = Alignment.Center
        ) {
            // 内层：酸橙绿 1dp 大框（呼吸脉冲），wrap 内容并垂直居中；
            // verticalScroll：横屏高度紧张（411dp 级）时内容可滚动，保证按钮完整可达
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(VigilSurface, RoundedCornerShape(20.dp))
                    .border(2.dp, VigilError.copy(alpha = borderAlpha), RoundedCornerShape(20.dp))
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "ALERT // 关键词报警",
                    style = BodyTextStyle,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = VigilError
                )

                Text(
                    text = matchedKeyword ?: "未知",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 40.sp,
                    color = VigilTextPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AlertMetaRow("FROM", sourceApp?.takeIf { it.isNotBlank() } ?: "--")
                    AlertMetaRow("TEXT", snippet?.takeIf { it.isNotBlank() } ?: "--", maxLines = 3)
                    val timeText = remember(eventTimeMillis) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                            .format(Date(eventTimeMillis ?: System.currentTimeMillis()))
                    }
                    AlertMetaRow("TIME", timeText)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VigilError, RoundedCornerShape(999.dp))
                        .clickable {
                            onConfirm()
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "已知晓，停止报警",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}

private val BodyTextStyle = TextStyle(fontFamily = FontFamily.SansSerif)

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
            style = BodyTextStyle,
            fontSize = 11.sp,
            color = VigilTextSecondary
        )
        Text(
            text = value,
            style = BodyTextStyle,
            fontSize = 11.sp,
            color = VigilTextPrimary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
