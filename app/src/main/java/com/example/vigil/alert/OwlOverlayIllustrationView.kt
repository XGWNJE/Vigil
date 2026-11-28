package com.example.vigil.alert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import com.example.vigil.R
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

enum class OwlOverlayMode(
    val resId: Int,
    val cols: Int,
    val frameCount: Int,
    val cycleMs: Int,
    val accentColor: Int
) {
    ALARM(
        resId = R.drawable.owl_strip_alarm,
        cols = 8,
        frameCount = 88,
        cycleMs = 3385,
        accentColor = 0xFFE5484D.toInt()
    ),
    WARNING(
        resId = R.drawable.owl_strip_warning,
        cols = 12,
        frameCount = 174,
        cycleMs = 7102,
        accentColor = 0xFFF5A524.toInt()
    )
}

/**
 * 轻量像素猫头鹰栖枝插画 View：
 * - 透明底 + 5 阶暗夜像素背光晕，保证在任意亮/暗第三方应用上方轮廓清晰
 * - 脚下像素横枝、微摇小叶与联动闪烁微型哨兵灯笼
 * - 最近邻采样绘制雪碧图，关闭视图时立即停止动画并释放位图引用
 */
class OwlOverlayIllustrationView(
    context: Context,
    private val mode: OwlOverlayMode,
    private val owlSizePx: Int,
    private val showLantern: Boolean = true
) : View(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val pixelPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val srcRect = Rect()
    private val dstRect = RectF()

    @Volatile
    private var released = false
    private var spriteBitmap: Bitmap? = null
    private var animStartMs = 0L
    private var frameIndex = 0
    private var animRunning = false

    private val frameTick = object : Runnable {
        override fun run() {
            if (released || !animRunning) return
            val elapsedMs = (android.os.SystemClock.uptimeMillis() - animStartMs).coerceAtLeast(0L)
            val progress = (elapsedMs % mode.cycleMs) / mode.cycleMs.toFloat()
            frameIndex = (progress * mode.frameCount).toInt().coerceIn(0, mode.frameCount - 1)
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        decodeExecutor.execute {
            if (released) return@execute
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = try {
                BitmapFactory.decodeResource(context.resources, mode.resId, opts)
            } catch (_: Throwable) {
                null
            }
            mainHandler.post {
                if (released) {
                    decoded?.recycle()
                    return@post
                }
                spriteBitmap = decoded
                if (decoded != null && isAttachedToWindow) {
                    startAnimation()
                } else {
                    invalidate()
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (owlSizePx * 1.24f).roundToInt().coerceAtLeast(1)
        val h = (owlSizePx * 1.14f).roundToInt().coerceAtLeast(1)
        setMeasuredDimension(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!released && spriteBitmap != null) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        if (released) return
        released = true
        animRunning = false
        removeCallbacks(frameTick)
        val bmp = spriteBitmap
        spriteBitmap = null
        if (bmp != null && !bmp.isRecycled) {
            bmp.recycle()
        }
    }

    private fun startAnimation() {
        if (animRunning || released) return
        animRunning = true
        animStartMs = android.os.SystemClock.uptimeMillis()
        removeCallbacks(frameTick)
        postOnAnimation(frameTick)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val owlPx = owlSizePx.toFloat()
        val p = (owlPx / 34f).coerceIn(2f, 4f).roundToInt().toFloat()
        val cx = w / 2f
        val owlLeft = cx - owlPx / 2f
        val owlTop = ((h - owlPx) * 0.36f).coerceAtLeast(0f)
        val footY = snap(owlTop + owlPx * (117f / 128f), p)
        val timeSec = if (animStartMs > 0L) {
            (android.os.SystemClock.uptimeMillis() - animStartMs) / 1000f
        } else {
            0f
        }

        // 1. 像素横枝与接触影
        val halfSpan = (owlPx * 0.58f).coerceAtMost(w * 0.48f)
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
                pixelPaint.color = when (t) {
                    0 -> 0xFF423A30.toInt()
                    1 -> 0xFF2D2720.toInt()
                    else -> 0xFF1B1713.toInt()
                }
                canvas.drawRect(x, y0 + t * p, x + p, y0 + (t + 1) * p, pixelPaint)
            }
            if (abs(x - (cx - halfSpan * 0.40f)) < p || abs(x - (cx + halfSpan * 0.26f)) < p) {
                pixelPaint.color = 0xFF1B1713.toInt()
                canvas.drawRect(x, y0 + p, x + p, y0 + p * 2f, pixelPaint)
            }
            x += p
        }

        pixelPaint.color = 0xB0000000.toInt()
        val footOffset = owlPx * 0.135f
        for (fx in listOf(cx - footOffset, cx + footOffset)) {
            val fx0 = snap(fx - p * 2.5f, p)
            canvas.drawRect(fx0, footY, fx0 + p * 5f, footY + p, pixelPaint)
        }

        // 3. 微摇像素小叶簇
        val swayOffset = if (sin(timeSec * 1.8f) > 0.35f) p else if (sin(timeSec * 1.8f) < -0.35f) -p else 0f
        val leafMain = 0xFF3A4433.toInt()
        val leafDark = 0xFF283024.toInt()
        val lx = xStart + p * 2f + swayOffset * 0.5f
        val ly = footY - p * 2f
        pixelPaint.color = leafMain
        canvas.drawRect(lx, ly, lx + p * 2f, ly + p, pixelPaint)
        canvas.drawRect(lx + p * 2f, ly - p * 2f, lx + p * 3f, ly - p, pixelPaint)
        pixelPaint.color = leafDark
        canvas.drawRect(lx + p, ly - p, lx + p * 3f, ly, pixelPaint)

        val rx = xEnd - p * 4f + swayOffset * 0.5f
        val ry = footY - p * 2f
        pixelPaint.color = leafMain
        canvas.drawRect(rx + p, ry, rx + p * 3f, ry + p, pixelPaint)
        canvas.drawRect(rx, ry - p * 2f, rx + p, ry - p, pixelPaint)
        pixelPaint.color = leafDark
        canvas.drawRect(rx, ry - p, rx + p * 2f, ry, pixelPaint)

        // 4. 右侧枝下悬挂微型像素哨兵灯笼
        if (showLantern) {
            val lanX = snap(cx + owlPx * 0.34f + (if (sin(timeSec * 2.4f) > 0.55f) p else 0f), p)
            val lanY = footY + p * 2f
            val frameCol = 0xFF4F463B.toInt()
            pixelPaint.color = frameCol
            canvas.drawRect(lanX + p, lanY, lanX + p * 2f, lanY + p, pixelPaint)
            canvas.drawRect(lanX, lanY + p, lanX + p * 3f, lanY + p * 2f, pixelPaint)
            canvas.drawRect(lanX, lanY + p * 2f, lanX + p, lanY + p * 4f, pixelPaint)
            canvas.drawRect(lanX + p * 2f, lanY + p * 2f, lanX + p * 3f, lanY + p * 4f, pixelPaint)
            canvas.drawRect(lanX, lanY + p * 4f, lanX + p * 3f, lanY + p * 5f, pixelPaint)

            val flamePulse = 0.65f + 0.35f * sin(timeSec * (if (mode == OwlOverlayMode.ALARM) 9f else 3.2f))
            val flameAlpha = (0.95f * flamePulse * 255f).roundToInt().coerceIn(80, 255)
            val haloAlpha = (0.26f * flamePulse * 255f).roundToInt().coerceIn(20, 90)
            pixelPaint.color = (haloAlpha shl 24) or (mode.accentColor and 0x00FFFFFF)
            canvas.drawRect(lanX - p, lanY + p, lanX + p * 4f, lanY + p * 5f, pixelPaint)
            pixelPaint.color = (flameAlpha shl 24) or (mode.accentColor and 0x00FFFFFF)
            canvas.drawRect(lanX + p, lanY + p * 2f, lanX + p * 2f, lanY + p * 4f, pixelPaint)
        }

        // 5. 最近邻采样绘制猫头鹰雪碧图当前帧
        val bmp = spriteBitmap ?: return
        if (bmp.isRecycled) return
        val frameSize = bmp.width / mode.cols
        if (frameSize <= 0) return
        val safeIdx = frameIndex.coerceIn(0, mode.frameCount - 1)
        val col = safeIdx % mode.cols
        val row = safeIdx / mode.cols
        srcRect.set(col * frameSize, row * frameSize, (col + 1) * frameSize, (row + 1) * frameSize)
        dstRect.set(owlLeft, owlTop, owlLeft + owlPx, owlTop + owlPx)
        canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
    }

    private fun snap(value: Float, step: Float): Float = floor(value / step) * step

    companion object {
        private val decodeExecutor = Executors.newSingleThreadExecutor()
    }
}

/**
 * 依附于猫头鹰身侧/枝下的轻量对话气泡与挂签背景，带指向猫头鹰的三角形尖角与动态呼吸描边。
 */
class SpeechBubbleDrawable(
    private val pointerEdge: PointerEdge,
    fillColor: Int,
    strokeColor: Int,
    private val cornerRadiusPx: Float,
    private val strokeWidthPx: Float,
    private val pointerSizePx: Float
) : Drawable() {

    enum class PointerEdge { LEFT, RIGHT, TOP }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeJoin = Paint.Join.ROUND
        color = strokeColor
    }
    private val bubblePath = Path()
    private val bodyRect = RectF()

    fun setStrokeColor(color: Int) {
        strokePaint.color = color
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val inset = strokeWidthPx * 0.5f
        val left = b.left + inset + if (pointerEdge == PointerEdge.LEFT) pointerSizePx else 0f
        val top = b.top + inset + if (pointerEdge == PointerEdge.TOP) pointerSizePx else 0f
        val right = b.right - inset - if (pointerEdge == PointerEdge.RIGHT) pointerSizePx else 0f
        val bottom = b.bottom - inset
        if (right <= left || bottom <= top) return

        bodyRect.set(left, top, right, bottom)
        val r = cornerRadiusPx.coerceAtMost(minOf(bodyRect.width(), bodyRect.height()) * 0.45f)

        bubblePath.reset()
        when (pointerEdge) {
            PointerEdge.LEFT -> {
                val tipY = (top + bodyRect.height() * 0.36f).coerceIn(top + r + pointerSizePx, bottom - r - pointerSizePx)
                val halfBase = pointerSizePx * 0.78f
                bubblePath.moveTo(left + r, top)
                bubblePath.lineTo(right - r, top)
                bubblePath.quadTo(right, top, right, top + r)
                bubblePath.lineTo(right, bottom - r)
                bubblePath.quadTo(right, bottom, right - r, bottom)
                bubblePath.lineTo(left + r, bottom)
                bubblePath.quadTo(left, bottom, left, bottom - r)
                bubblePath.lineTo(left, tipY + halfBase)
                bubblePath.lineTo(left - pointerSizePx, tipY)
                bubblePath.lineTo(left, tipY - halfBase)
                bubblePath.lineTo(left, top + r)
                bubblePath.quadTo(left, top, left + r, top)
                bubblePath.close()
            }
            PointerEdge.RIGHT -> {
                val tipY = (top + bodyRect.height() * 0.36f).coerceIn(top + r + pointerSizePx, bottom - r - pointerSizePx)
                val halfBase = pointerSizePx * 0.78f
                bubblePath.moveTo(left + r, top)
                bubblePath.lineTo(right - r, top)
                bubblePath.quadTo(right, top, right, top + r)
                bubblePath.lineTo(right, tipY - halfBase)
                bubblePath.lineTo(right + pointerSizePx, tipY)
                bubblePath.lineTo(right, tipY + halfBase)
                bubblePath.lineTo(right, bottom - r)
                bubblePath.quadTo(right, bottom, right - r, bottom)
                bubblePath.lineTo(left + r, bottom)
                bubblePath.quadTo(left, bottom, left, bottom - r)
                bubblePath.lineTo(left, top + r)
                bubblePath.quadTo(left, top, left + r, top)
                bubblePath.close()
            }
            PointerEdge.TOP -> {
                val tipX = bodyRect.centerX()
                val halfBase = pointerSizePx * 0.85f
                bubblePath.moveTo(left + r, top)
                bubblePath.lineTo(tipX - halfBase, top)
                bubblePath.lineTo(tipX, top - pointerSizePx)
                bubblePath.lineTo(tipX + halfBase, top)
                bubblePath.lineTo(right - r, top)
                bubblePath.quadTo(right, top, right, top + r)
                bubblePath.lineTo(right, bottom - r)
                bubblePath.quadTo(right, bottom, right - r, bottom)
                bubblePath.lineTo(left + r, bottom)
                bubblePath.quadTo(left, bottom, left, bottom - r)
                bubblePath.lineTo(left, top + r)
                bubblePath.quadTo(left, top, left + r, top)
                bubblePath.close()
            }
        }

        canvas.drawPath(bubblePath, fillPaint)
        canvas.drawPath(bubblePath, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
