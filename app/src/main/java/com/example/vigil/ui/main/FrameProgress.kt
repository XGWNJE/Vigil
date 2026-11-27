package com.example.vigil.ui.main

import androidx.compose.runtime.*

/** 报警与录音的既有帧驱动反馈。 */
@Composable
fun rememberFrameDrivenProgress(periodMs: Int, resetKey: Any = Unit): Float {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs, resetKey) {
        val startNanos = System.nanoTime()
        while (true) withFrameNanos {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            progress = (elapsedMs % periodMs) / periodMs.toFloat()
        }
    }
    return progress
}
