package com.example.vigil.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val BAYER_4X4 = arrayOf(
    floatArrayOf(0f, 8f, 2f, 10f),
    floatArrayOf(12f, 4f, 14f, 6f),
    floatArrayOf(3f, 11f, 1f, 9f),
    floatArrayOf(15f, 7f, 13f, 5f)
).map { row -> FloatArray(4) { i -> row[i] / 16f } }.toTypedArray()

private data class StarSpec(val xRatio: Float, val yRatio: Float, val phase: Float, val major: Boolean)

private val NIGHT_STARS = listOf(
    StarSpec(0.11f, 0.14f, 0.0f, true),
    StarSpec(0.24f, 0.21f, 1.3f, false),
    StarSpec(0.39f, 0.11f, 2.7f, false),
    StarSpec(0.58f, 0.16f, 0.8f, true),
    StarSpec(0.71f, 0.24f, 3.5f, false),
    StarSpec(0.88f, 0.28f, 1.9f, true),
    StarSpec(0.16f, 0.34f, 4.1f, false),
    StarSpec(0.82f, 0.42f, 2.2f, false)
)

/**
 * 监控页昼夜动静像素风远景与中景背景（纯 Canvas 整数像素绘制，零位图资源体积）：
 * - 静态远景离屏缓存：4x4 Bayer 像素抖动天幕、浅色晨阳微晕 / 深色像素弯月、底部低对比远处松林剪影
 * - 动态中景循环：浅色缓慢飘移像素云 + 晨光微尘；深色星点错峰闪烁 + 暗夜云 + 萤火虫呼吸飘浮
 */
@Composable
internal fun PixelSentinelBackdrop(
    isDark: Boolean,
    isIdle: Boolean,
    isAlerting: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pxSize = remember(density) { with(density) { 3.5f * this.density }.roundToInt().coerceAtLeast(3) }

    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = -1L
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                timeSec = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val wPx = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }
        val hPx = with(density) { maxHeight.toPx().roundToInt().coerceAtLeast(1) }

        val staticLayer = remember(wPx, hPx, isDark, pxSize) {
            buildStaticBackdropBitmap(wPx, hPx, isDark, pxSize)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = staticLayer,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )

            val p = pxSize.toFloat()
            val w = size.width
            val h = size.height
            val speed = when {
                isAlerting -> 1.6f
                isIdle -> 0.55f
                else -> 1.0f
            }

            // 1. Drifting pixel clouds (slow, low contrast)
            val cTop = if (isDark) Color(0xFF1F1C18).copy(alpha = 0.72f) else Color(0xFFFCF9F2).copy(alpha = 0.85f)
            val cBody = if (isDark) Color(0xFF181512).copy(alpha = 0.72f) else Color(0xFFF3ECE0).copy(alpha = 0.80f)
            val cEdge = if (isDark) Color(0xFF13110F).copy(alpha = 0.72f) else Color(0xFFE7DEC9).copy(alpha = 0.75f)

            val cloud1X = ((timeSec * 8f * speed + w * 0.12f) % (w + p * 20f)) - p * 14f
            drawPixelCloud(cloud1X, h * 0.14f, p, big = true, topCol = cTop, bodyCol = cBody, edgeCol = cEdge)

            val cloud2X = ((timeSec * 5.2f * speed + w * 0.62f) % (w + p * 18f)) - p * 12f
            drawPixelCloud(cloud2X, h * 0.22f, p, big = false, topCol = cTop, bodyCol = cBody, edgeCol = cEdge)

            // 2. Twinkling pixel stars (night) or subtle sunbeams/dust (day)
            if (isDark) {
                for (st in NIGHT_STARS) {
                    val tw = 0.5f + 0.5f * sin(timeSec * 1.8f + st.phase)
                    val baseAlpha = (if (isIdle) 0.28f else 0.48f) * (0.35f + 0.65f * tw)
                    val col = if (st.major) Color(0xFFF5A524).copy(alpha = baseAlpha) else Color(0xFFA39B8F).copy(alpha = baseAlpha * 0.8f)
                    val sx = snap(w * st.xRatio, p)
                    val sy = snap(h * st.yRatio, p)
                    drawRect(col, Offset(sx, sy), Size(p, p))
                    if (st.major && tw > 0.62f) {
                        val armCol = col.copy(alpha = baseAlpha * 0.45f)
                        drawRect(armCol, Offset(sx - p, sy), Size(p, p))
                        drawRect(armCol, Offset(sx + p, sy), Size(p, p))
                        drawRect(armCol, Offset(sx, sy - p), Size(p, p))
                        drawRect(armCol, Offset(sx, sy + p), Size(p, p))
                    }
                }
            }

            // 3. Fireflies (night) / Floating warm leaf-dust (day) around mid-scene
            val moteCount = if (isIdle) 2 else 4
            for (i in 0 until moteCount) {
                val ph = i * 1.7f
                val mx = w * (0.14f + 0.23f * i) + sin(timeSec * 0.7f * speed + ph) * (p * 6f)
                val my = if (isDark) {
                    h * (0.34f + 0.08f * (i % 2)) + cos(timeSec * 0.9f * speed + ph * 1.3f) * (p * 5f)
                } else {
                    val drift = ((timeSec * 11f * speed + i * 95f) % (h * 0.46f))
                    h * 0.14f + drift
                }
                val pulse = 0.4f + 0.6f * (0.5f + 0.5f * sin(timeSec * 2.2f + ph))
                val moteColor = when {
                    isAlerting -> Color(0xFFE5484D).copy(alpha = 0.55f * pulse)
                    isDark -> Color(0xFFF5A524).copy(alpha = (if (isIdle) 0.25f else 0.55f) * pulse)
                    else -> Color(0xFFC87A08).copy(alpha = 0.28f * pulse)
                }
                val sx = snap(mx, p)
                val sy = snap(my, p)
                drawRect(moteColor, Offset(sx, sy), Size(p, p))
                if (isDark && pulse > 0.72f) {
                    drawRect(moteColor.copy(alpha = moteColor.alpha * 0.35f), Offset(sx - p, sy), Size(p * 3f, p))
                    drawRect(moteColor.copy(alpha = moteColor.alpha * 0.35f), Offset(sx, sy - p), Size(p, p * 3f))
                }
            }
        }
    }
}

/**
 * 猫头鹰前景像素栖木舞台：
 * - 5 阶像素化径向微光（替代模糊渐变，与像素风彻底统一）
 * - 立体像素横枝（枝顶严格对齐猫头鹰双脚，带树皮纹理与脚底接触影）
 * - 随微风轻摇的左右像素小叶簇
 * - 右侧枝下悬挂的微型像素哨兵灯笼（随状态亮/暗/变色：休眠熄灯、监听暖琥珀、报警警戒红）
 */
@Composable
internal fun PixelBranchPerch(
    owlSize: Dp,
    isDark: Boolean,
    isIdle: Boolean,
    isAlerting: Boolean,
    isWarning: Boolean,
    isConnecting: Boolean,
    isJumping: Boolean,
    glowColor: Color,
    glowAlpha: Float,
    modifier: Modifier = Modifier
) {
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = -1L
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                timeSec = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier) {
        val p = (owlSize.toPx() / 74f).coerceIn(3f, 5.5f).roundToInt().toFloat()
        val cx = size.width / 2f
        val owlPx = owlSize.toPx()
        // Owl feet sit at y=118/128 inside owl canvas
        val owlTop = (size.height - owlPx) / 2f
        val footY = snap(owlTop + owlPx * (117f / 128f), p)
        val glowCy = snap(owlTop + owlPx * 0.56f, p)

        // 1. Stepped 5-band pixelated radial glow behind owl
        val maxR = owlPx * 0.62f
        val bands = 5
        for (b in bands downTo 1) {
            val r = maxR * (b / bands.toFloat())
            val stepAlpha = glowAlpha * ((bands - b + 1) / bands.toFloat()) * 0.55f
            val col = glowColor.copy(alpha = stepAlpha)
            val rx = r
            val ry = r * 0.88f
            var y = -ry
            while (y <= ry) {
                val normY = y / ry
                val halfW = rx * sqrt((1f - normY * normY).coerceAtLeast(0f))
                val x0 = snap(cx - halfW, p)
                val x1 = snap(cx + halfW, p)
                val py = snap(glowCy + y, p)
                if (x1 > x0) {
                    drawRect(col, Offset(x0, py), Size(x1 - x0, p))
                }
                y += p
            }
        }

        // 2. Stepped pixel horizontal branch perch directly beneath the owl's orange feet
        val hiCol = if (isDark) Color(0xFF423A30) else Color(0xFFD5CAB7)
        val midCol = if (isDark) Color(0xFF2D2720) else Color(0xFFBBA98E)
        val dkCol = if (isDark) Color(0xFF1B1713) else Color(0xFF9C8B72)
        val contactCol = if (isDark) Color(0xB0000000) else Color(0x667D6B52)

        val halfSpan = (owlPx * 0.64f).coerceAtMost(size.width * 0.45f)
        val xStart = snap(cx - halfSpan, p)
        val xEnd = snap(cx + halfSpan, p)

        var x = xStart
        while (x < xEnd) {
            val dist = abs(x - cx)
            val thick = when {
                dist < halfSpan * 0.62f -> 3
                dist < halfSpan * 0.84f -> 2
                else -> 1
            }
            val curveY = if (dist > halfSpan * 0.72f) -p else 0f
            val y0 = footY + curveY
            for (t in 0 until thick) {
                val col = when (t) {
                    0 -> hiCol
                    1 -> midCol
                    else -> dkCol
                }
                drawRect(col, Offset(x, y0 + t * p), Size(p, p))
            }
            // Subtle bark knot accents
            if (abs(x - (cx - halfSpan * 0.42f)) < p || abs(x - (cx + halfSpan * 0.28f)) < p) {
                drawRect(dkCol, Offset(x, y0 + p), Size(p, p))
            }
            x += p
        }

        // Contact shadow under the owl's two feet (fades when owl hops in PRESS state)
        val shadowAlphaScale = if (isJumping) 0.25f else 1.0f
        val cSh = contactCol.copy(alpha = contactCol.alpha * shadowAlphaScale)
        val footOffset = owlPx * 0.135f
        for (fx in listOf(cx - footOffset, cx + footOffset)) {
            val fx0 = snap(fx - p * 3f, p)
            drawRect(cSh, Offset(fx0, footY), Size(p * 6f, p))
        }

        // 3. Gentle swaying pixel leaf clusters on left & right branch tips
        val swayOffset = if (sin(timeSec * 1.6f) > 0.35f) p else if (sin(timeSec * 1.6f) < -0.35f) -p else 0f
        val leafMain = if (isDark) Color(0xFF3A4433) else Color(0xFFB3C09D)
        val leafDark = if (isDark) Color(0xFF283024) else Color(0xFF93A37B)

        // Left tip leaves
        val lx = xStart + p * 2f + swayOffset * 0.5f
        val ly = footY - p * 2f
        drawRect(leafMain, Offset(lx, ly), Size(p * 2f, p))
        drawRect(leafDark, Offset(lx + p, ly - p), Size(p * 2f, p))
        drawRect(leafMain, Offset(lx + p * 2f, ly - p * 2f), Size(p, p))

        // Right tip leaves
        val rx = xEnd - p * 5f + swayOffset * 0.5f
        val ry = footY - p * 2f
        drawRect(leafMain, Offset(rx + p, ry), Size(p * 2f, p))
        drawRect(leafDark, Offset(rx, ry - p), Size(p * 2f, p))
        drawRect(leafMain, Offset(rx, ry - p * 2f), Size(p, p))

        // 4. Micro pixel sentinel lantern hanging under the right branch (clear of centered status text)
        val lanX = snap(cx + owlPx * 0.36f + (if (sin(timeSec * 2.1f) > 0.55f) p else 0f), p)
        val lanY = footY + p * 2f
        val frameCol = if (isDark) Color(0xFF4F463B) else Color(0xFF7B6C58)
        val dimGlass = if (isDark) Color(0xFF25211C) else Color(0xFFD5CCBD)

        // Hook & cord
        drawRect(frameCol, Offset(lanX + p, lanY), Size(p, p))
        // Top cap
        drawRect(frameCol, Offset(lanX, lanY + p), Size(p * 3f, p))
        // Lantern body sides
        drawRect(frameCol, Offset(lanX, lanY + p * 2f), Size(p, p * 2f))
        drawRect(frameCol, Offset(lanX + p * 2f, lanY + p * 2f), Size(p, p * 2f))
        // Bottom cap
        drawRect(frameCol, Offset(lanX, lanY + p * 4f), Size(p * 3f, p))

        // Flame state linked to Vigil service status
        val flamePulse = 0.65f + 0.35f * sin(timeSec * (if (isAlerting) 9f else 3.2f))
        val flameColor = when {
            isIdle -> dimGlass
            isAlerting || isWarning -> Color(0xFFE5484D).copy(alpha = 0.95f * flamePulse)
            isConnecting -> Color(0xFFF5A524).copy(alpha = 0.70f * flamePulse)
            else -> Color(0xFFF5A524).copy(alpha = 0.95f * flamePulse)
        }
        drawRect(flameColor, Offset(lanX + p, lanY + p * 2f), Size(p, p * 2f))
        if (!isIdle) {
            val haloAlpha = (if (isDark) 0.24f else 0.16f) * flamePulse
            val halo = flameColor.copy(alpha = haloAlpha)
            drawRect(halo, Offset(lanX - p, lanY + p), Size(p * 5f, p * 4f))
        }
    }
}

private fun buildStaticBackdropBitmap(wPx: Int, hPx: Int, isDark: Boolean, p: Int): ImageBitmap {
    val bmp = ImageBitmap(wPx, hPx)
    val canvas = ComposeCanvas(bmp)
    val paint = Paint()

    // Base fill
    paint.color = if (isDark) Color(0xFF121110) else Color(0xFFF6F4EF)
    canvas.drawRect(0f, 0f, wPx.toFloat(), hPx.toFloat(), paint)

    // 1. Top 4x4 Bayer ordered dithering sky band
    val skyCol = if (isDark) Color(0xFF0A0908) else Color(0xFFECE4D3)
    val skyH = (hPx * 0.27f).roundToInt()
    paint.color = skyCol
    var y = 0
    while (y < skyH) {
        val prob = 1f - (y / skyH.toFloat())
        val by = (y / p) % 4
        var x = 0
        while (x < wPx) {
            val bx = (x / p) % 4
            if (prob > BAYER_4X4[by][bx]) {
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + p).toFloat(), (y + p).toFloat(), paint)
            }
            x += p
        }
        y += p
    }

    // 2. Bottom 4x4 Bayer ordered dithering band (soft horizon)
    val botCol = if (isDark) Color(0xFF191613) else Color(0xFFEDE6D7)
    val botStart = (hPx * 0.72f).roundToInt()
    val botSpan = (hPx - botStart).coerceAtLeast(1)
    paint.color = botCol
    y = botStart
    while (y < hPx) {
        val prob = ((y - botStart) / botSpan.toFloat()) * 0.85f
        val by = (y / p) % 4
        var x = 0
        while (x < wPx) {
            val bx = (x / p) % 4
            if (prob > BAYER_4X4[by][bx]) {
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + p).toFloat(), (y + p).toFloat(), paint)
            }
            x += p
        }
        y += p
    }

    // 3. Distant two-layer low-contrast pixel pine silhouettes near the bottom metric area
    val ridgeFar = if (isDark) Color(0xFF181512) else Color(0xFFE9E2D3)
    val ridgeNear = if (isDark) Color(0xFF1E1A16) else Color(0xFFE3DAC7)
    val pf = p.toFloat()
    val baseFarY = snap(hPx * 0.87f, pf)
    val baseNearY = snap(hPx * 0.92f, pf)

    paint.color = ridgeFar
    var tx = 0f
    var idx = 0
    while (tx < wPx) {
        val treeH = ((idx * 3 + 2) % 5 + 2) * pf
        canvas.drawRect(tx, baseFarY - treeH, tx + pf * 2f, hPx.toFloat(), paint)
        tx += pf * 2f
        idx++
    }

    paint.color = ridgeNear
    tx = 0f
    idx = 0
    while (tx < wPx) {
        val treeH = ((idx * 5 + 1) % 4 + 1) * pf
        canvas.drawRect(tx, baseNearY - treeH, tx + pf * 3f, hPx.toFloat(), paint)
        tx += pf * 3f
        idx++
    }

    // 4. Celestial motif in upper-right: Pixel crescent moon (night) or soft dawn sun halo (day)
    val celX = snap(wPx * 0.82f, pf)
    val celY = snap(hPx * 0.10f, pf)
    if (isDark) {
        val moonHalo = Color(0xFFF5A524).copy(alpha = 0.10f)
        val moonBody = Color(0xFFF5A524).copy(alpha = 0.78f)
        val moonPattern = arrayOf(
            "  111 ",
            " 111  ",
            "111   ",
            "111   ",
            " 111  ",
            "  111 "
        )
        paint.color = moonHalo
        canvas.drawRect(celX - pf, celY - pf, celX + pf * 7f, celY + pf * 7f, paint)
        paint.color = moonBody
        for (r in moonPattern.indices) {
            for (c in moonPattern[r].indices) {
                if (moonPattern[r][c] == '1') {
                    val rx = celX + c * pf
                    val ry = celY + r * pf
                    canvas.drawRect(rx, ry, rx + pf, ry + pf, paint)
                }
            }
        }
    } else {
        // Gentle dawn sun stepped pixel glow in upper-right
        val sunCols = listOf(
            3 to Color(0xFFF5A524).copy(alpha = 0.06f),
            2 to Color(0xFFF5A524).copy(alpha = 0.09f),
            1 to Color(0xFFFFD166).copy(alpha = 0.14f)
        )
        for ((radCells, col) in sunCols) {
            paint.color = col
            val r = radCells * pf * 2f
            canvas.drawRect(celX - r, celY - r, celX + r, celY + r, paint)
        }
    }

    return bmp
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelCloud(
    x0: Float,
    y0: Float,
    p: Float,
    big: Boolean,
    topCol: Color,
    bodyCol: Color,
    edgeCol: Color
) {
    val spec = if (big) {
        listOf(3 to 5, 1 to 9, 0 to 12, 1 to 10)
    } else {
        listOf(2 to 4, 0 to 8, 1 to 7)
    }
    val sx = snap(x0, p)
    val sy = snap(y0, p)
    spec.forEachIndexed { rowIdx, (leftOff, widthCells) ->
        val col = when (rowIdx) {
            0 -> topCol
            spec.lastIndex -> edgeCol
            else -> bodyCol
        }
        drawRect(
            color = col,
            topLeft = Offset(sx + leftOff * p, sy + rowIdx * p),
            size = Size(widthCells * p, p)
        )
    }
}

private fun snap(value: Float, step: Float): Float = floor(value / step) * step
