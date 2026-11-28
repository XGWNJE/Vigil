// src/main/java/com/example/vigil/ui/dialogs/KeywordAlertDialog.kt
package com.example.vigil.ui.dialogs

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.vigil.R
import com.example.vigil.ui.main.PixelBranchPerch
import com.example.vigil.ui.main.rememberFrameDrivenProgress
import com.example.vigil.ui.theme.VigilError
import com.example.vigil.ui.theme.VigilPrimary
import com.example.vigil.ui.theme.VigilSurface
import com.example.vigil.ui.theme.VigilTextPrimary
import com.example.vigil.ui.theme.VigilTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内兜底报警弹窗：与系统浮窗统一的「像素猫头鹰树梢立绘 + 枝下报信签 + 停铃胶囊」风格。
 */
@Composable
fun KeywordAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    matchedKeyword: String?,
    sourceApp: String? = null,
    snippet: String? = null,
    eventTimeMillis: Long? = null,
    isSilent: Boolean = false
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
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && dialogWindow != null) {
                val lp = dialogWindow.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                dialogWindow.attributes = lp
            }
        }

        var enterProgress by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = System.nanoTime()
            val durationMs = 240L
            while (enterProgress < 1f) {
                withFrameNanos {
                    enterProgress = ((System.nanoTime() - start) / 1_000_000L / durationMs.toFloat())
                        .coerceIn(0f, 1f)
                }
            }
        }
        val enterAlpha = FastOutSlowInEasing.transform(enterProgress)
        val enterOffset = ((1f - FastOutSlowInEasing.transform(enterProgress)) * 32).dp

        val accentColor = if (isSilent) VigilPrimary else VigilError
        val pulse = rememberFrameDrivenProgress(if (isSilent) 1600 else 1000)
        val pulseTri = if (pulse < 0.5f) pulse * 2f else (1f - pulse) * 2f
        val borderAlpha = 0.35f + 0.65f * FastOutSlowInEasing.transform(pulseTri)
        val glowAlpha = 0f

        val resId = if (isSilent) R.drawable.owl_strip_warning else R.drawable.owl_strip_alarm
        val cols = if (isSilent) 12 else 8
        val frameCount = if (isSilent) 174 else 88
        val cycleMs = if (isSilent) 7102 else 3385
        val resources = LocalContext.current.resources
        val atlas = remember(resId) { ImageBitmap.imageResource(resources, resId) }
        val animProgress = rememberFrameDrivenProgress(cycleMs, resetKey = resId)
        val frameIndex = (animProgress * frameCount).toInt().coerceIn(0, frameCount - 1)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD90E0D0C))
                .padding(horizontal = 24.dp, vertical = 36.dp)
                .graphicsLayer { alpha = enterAlpha }
                .offset(y = enterOffset),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val owlSize = 156.dp
                Box(
                    modifier = Modifier
                        .size(width = owlSize * 1.28f, height = owlSize + 14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onConfirm
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PixelBranchPerch(
                        owlSize = owlSize,
                        isDark = true,
                        isIdle = false,
                        isAlerting = !isSilent,
                        isWarning = isSilent,
                        isConnecting = false,
                        isJumping = false,
                        glowColor = accentColor,
                        glowAlpha = glowAlpha,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.size(owlSize)) {
                        val frameSize = atlas.width / cols
                        if (frameSize > 0) {
                            val col = frameIndex % cols
                            val row = frameIndex / cols
                            drawImage(
                                image = atlas,
                                srcOffset = IntOffset(col * frameSize, row * frameSize),
                                srcSize = IntSize(frameSize, frameSize),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                filterQuality = FilterQuality.None
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VigilSurface.copy(alpha = 0.94f), RoundedCornerShape(18.dp))
                        .border(1.5.dp, accentColor.copy(alpha = borderAlpha), RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = matchedKeyword ?: "未知",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center,
                        color = VigilTextPrimary
                    )

                    val timeText = remember(eventTimeMillis) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                            .format(Date(eventTimeMillis ?: System.currentTimeMillis()))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AlertMetaRow("FROM", sourceApp?.takeIf { it.isNotBlank() } ?: "--")
                        AlertMetaRow("TEXT", snippet?.takeIf { it.isNotBlank() } ?: "--", maxLines = 3)
                        AlertMetaRow("TIME", timeText)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                        .clickable { onConfirm() }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "已知晓，停止报警",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
            .padding(vertical = 2.dp),
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
