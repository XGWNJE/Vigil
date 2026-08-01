// src/main/java/com/example/vigil/ui/main/RippleBackground.kt
package com.example.vigil.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
 * 帧驱动的 0→1 循环进度，替代 rememberInfiniteTransition。
 * Compose 的 InfiniteTransition 在「开发者选项 → 动画程序时长缩放 = 关闭」时会被系统挂起
 * （小米等机型常见设置，真机实测复现涟漪静止），而涟漪是状态语言的一部分，必须始终播放；
 * 直接消费 vsync 帧回调不受该缩放影响。
 */
@Composable
fun rememberFrameDrivenProgress(periodMs: Int): Float {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs) {
        val startNanos = System.nanoTime()
        while (true) {
            withFrameNanos {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                progress = (elapsedMs % periodMs) / periodMs.toFloat()
            }
        }
    }
    return progress
}

/**
 * 全屏涟漪背景，参考 magicui ripple（numCircles=8、基础透明度 0.24、交错相位扩散）。
 * 环心 = 屏幕中心，与中央核心开关对齐：涟漪即从开关处向外扩散。
 * 颜色与节奏随服务状态变化，状态本身即是动效语言。
 * 静止环（DISABLED）与动画涟漪之间用 Crossfade 过渡，避免开关瞬间背景硬闪。
 */
@Composable
fun RippleBackground(state: ServiceState, modifier: Modifier = Modifier) {
    Crossfade(targetState = state == ServiceState.DISABLED, animationSpec = tween(500), label = "rippleMode") { disabled ->
        if (disabled) {
            StaticRings(modifier)
        } else {
            AnimatedRipple(state, modifier)
        }
    }
}

private const val NUM_CIRCLES = 8
private const val BASE_ALPHA = 0.30f

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

    val t = rememberFrameDrivenProgress(periodMs)

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
