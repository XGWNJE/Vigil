// src/main/java/com/example/vigil/ui/main/RippleBackground.kt
package com.example.vigil.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.vigil.ui.monitoring.ServiceState
import com.example.vigil.ui.theme.VigilDirAAcid
import com.example.vigil.ui.theme.VigilDirAAmber
import com.example.vigil.ui.theme.VigilDirADim
import com.example.vigil.ui.theme.VigilDirAFaint
import kotlin.math.hypot

/**
 * 全屏涟漪背景，参考 magicui ripple（numCircles=8、基础透明度 0.24、交错相位扩散）。
 * 环心 = 屏幕中心，与中央核心开关对齐：涟漪即从开关处向外扩散。
 * 颜色与节奏随服务状态变化，状态本身即是动效语言。
 */
@Composable
fun RippleBackground(state: ServiceState, modifier: Modifier = Modifier) {
    if (state == ServiceState.DISABLED) {
        StaticRings(modifier)
    } else {
        AnimatedRipple(state, modifier)
    }
}

private const val NUM_CIRCLES = 8
private const val BASE_ALPHA = 0.24f

@Composable
private fun AnimatedRipple(state: ServiceState, modifier: Modifier = Modifier) {
    val color = when (state) {
        ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> VigilDirAAcid
        ServiceState.INITIALIZING -> VigilDirADim
        // LISTENER_DISCONNECTED / HEARTBEAT_TIMEOUT / NO_PERMISSION / ERROR
        else -> VigilDirAAmber
    }
    val periodMs = when (state) {
        ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> 4000   // 平稳呼吸
        ServiceState.INITIALIZING -> 6000                            // 慢速等待
        else -> 2500                                                 // 急促警示
    }

    val transition = rememberInfiniteTransition(label = "ripple")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Canvas(modifier = modifier) {
        // 内圈起点略大于核心开关半径（44dp），让涟漪视觉上从开关边缘生长出来
        val minRadius = 56.dp.toPx()
        val maxRadius = hypot(size.width, size.height) / 2f
        val strokeWidth = 1.dp.toPx()
        for (i in 0 until NUM_CIRCLES) {
            val progress = (t + i.toFloat() / NUM_CIRCLES) % 1f
            val alpha = (1f - progress) * BASE_ALPHA
            if (alpha < 0.004f) continue
            drawCircle(
                color = color,
                radius = minRadius + (maxRadius - minRadius) * progress,
                center = center,
                alpha = alpha,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

/** 服务停止：不启动动画（省电），只画 4 圈静止暗环 */
@Composable
private fun StaticRings(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val minRadius = 56.dp.toPx()
        val maxRadius = hypot(size.width, size.height) / 2f * 0.8f
        val strokeWidth = 1.dp.toPx()
        for (i in 0 until 4) {
            drawCircle(
                color = VigilDirAFaint,
                radius = minRadius + (maxRadius - minRadius) * i / 3f,
                center = center,
                alpha = 0.12f,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}
