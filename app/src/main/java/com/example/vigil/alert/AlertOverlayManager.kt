package com.example.vigil.alert

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.vigil.data.AlertQueueItem
import com.example.vigil.data.OverlayAlertStyle
import com.example.vigil.data.SharedPreferencesHelper
import com.example.vigil.util.PermissionUtils
import com.example.vigil.util.VigilLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 静默暗夜（Obsidian Sentinel）无边框像素猫头鹰插画系统浮窗管理器。
 *
 * 彻底去掉外层矩形大框与背光晕，以透明底 + 像素猫头鹰栖枝插画为核心，
 * 搭配指向猫头鹰的轻量暗色报信气泡/枝下挂签与确认按钮：
 * - 响铃报警：使用 ALARM（扑翼 + 感叹号）与警戒红灯笼
 * - 静音报警：使用 WARNING（歪头举翅 ?!）与琥珀金灯笼
 * - 点击猫头鹰本身或确认按钮均可立即停铃并关闭浮窗
 */
object AlertOverlayManager {

    private const val TAG = "AlertOverlayManager"

    private const val COLOR_BUBBLE_BG = 0xEE171412.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFF5F2EB.toInt()
    private const val COLOR_TEXT_SNIPPET = 0xFFCFC8BC.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFF9B9387.toInt()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var owlViewRef: OwlOverlayIllustrationView? = null
    private var enterAnimator: AnimatorSet? = null
    private var pulseAnimator: ValueAnimator? = null
    private var metaTextRef: TextView? = null
    private var currentAlertId: String? = null
    private var currentSourceApp: String? = null
    private var currentTimeText: String = ""

    fun isShowing(): Boolean = rootView != null

    /**
     * 展示真实告警的系统浮窗。返回 true 表示已成功显示，false 表示无权限或创建失败（调用方应走兜底）。
     */
    fun show(
        context: Context,
        item: AlertQueueItem,
        queueSize: Int,
        onConfirm: () -> Unit
    ): Boolean {
        val appContext = context.applicationContext
        if (!PermissionUtils.canDrawOverlays(appContext)) {
            Log.w(TAG, "未授予悬浮窗权限，跳过应用外浮窗展示")
            return false
        }
        val style = SharedPreferencesHelper(appContext).getOverlayAlertStyle()
        val resolved = resolveStyle(appContext, style)
        val isSilent = item.ringtoneUri?.startsWith("silent:") == true
        return showInternal(
            context = appContext,
            alertId = item.id,
            keyword = item.keyword,
            sourceApp = item.sourceApp ?: "本机通知",
            snippet = item.snippet,
            timestampMs = item.firstTriggeredAt,
            queueSize = queueSize.coerceAtLeast(1),
            style = resolved,
            mode = if (isSilent) OwlOverlayMode.WARNING else OwlOverlayMode.ALARM,
            isPreview = false,
            onConfirm = onConfirm
        )
    }

    /** 设置页预览真实系统浮窗（不响铃、不写历史，点按钮或猫头鹰直接关闭）。 */
    fun showPreview(context: Context, style: OverlayAlertStyle): Boolean {
        val appContext = context.applicationContext
        if (!PermissionUtils.canDrawOverlays(appContext)) return false
        val resolved = resolveStyle(appContext, style)
        return showInternal(
            context = appContext,
            alertId = "preview_${System.currentTimeMillis()}",
            keyword = "服务器告警",
            sourceApp = "Vigil 预览",
            snippet = "核心节点负载异常 · 点击猫头鹰或按钮关闭",
            timestampMs = System.currentTimeMillis(),
            queueSize = 1,
            style = resolved,
            mode = OwlOverlayMode.ALARM,
            isPreview = true,
            onConfirm = { dismiss() }
        )
    }

    fun updateProgress(playedLoops: Int, loopLimit: Int, queueSize: Int) {
        mainHandler.post {
            metaTextRef?.text = formatMeta(currentSourceApp ?: "本机通知", currentTimeText, queueSize)
        }
    }

    fun dismiss() {
        mainHandler.post { dismissImmediate() }
    }

    private fun resolveStyle(context: Context, configured: OverlayAlertStyle): OverlayAlertStyle {
        if (configured != OverlayAlertStyle.AUTO) return configured
        val dm = context.resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        return if (widthDp >= 600f) OverlayAlertStyle.CORNER_COMPACT else OverlayAlertStyle.CENTER_CARD
    }

    private fun showInternal(
        context: Context,
        alertId: String,
        keyword: String,
        sourceApp: String,
        snippet: String?,
        timestampMs: Long,
        queueSize: Int,
        style: OverlayAlertStyle,
        mode: OwlOverlayMode,
        isPreview: Boolean,
        onConfirm: () -> Unit
    ): Boolean {
        val showAction = {
            dismissImmediate()
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val density = context.resources.displayMetrics.density
                val screenWidthPx = context.resources.displayMetrics.widthPixels
                fun dp(v: Int): Int = (v * density + 0.5f).toInt()
                fun dpF(v: Float): Float = v * density

                val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestampMs))
                currentAlertId = alertId
                currentSourceApp = sourceApp
                currentTimeText = timeText

                val accentColor = mode.accentColor
                val pointerSize = dp(8)
                val confirmAction = {
                    dismissImmediate()
                    onConfirm()
                }

                val owlSizePx = when (style) {
                    OverlayAlertStyle.CENTER_CARD -> dp(148)
                    OverlayAlertStyle.CORNER_COMPACT -> dp(84)
                    OverlayAlertStyle.TOP_BANNER, OverlayAlertStyle.AUTO -> dp(102)
                }

                val owlView = OwlOverlayIllustrationView(
                    context = context,
                    mode = mode,
                    owlSizePx = owlSizePx,
                    showLantern = true
                ).apply {
                    contentDescription = if (isPreview) "关闭浮窗预览" else "点击停止报警"
                    setOnClickListener { confirmAction() }
                }
                owlViewRef = owlView

                val pointerEdge = when (style) {
                    OverlayAlertStyle.CENTER_CARD -> SpeechBubbleDrawable.PointerEdge.TOP
                    OverlayAlertStyle.CORNER_COMPACT -> SpeechBubbleDrawable.PointerEdge.RIGHT
                    OverlayAlertStyle.TOP_BANNER, OverlayAlertStyle.AUTO -> SpeechBubbleDrawable.PointerEdge.LEFT
                }

                val bubbleDrawable = SpeechBubbleDrawable(
                    pointerEdge = pointerEdge,
                    fillColor = COLOR_BUBBLE_BG,
                    strokeColor = accentColor,
                    cornerRadiusPx = dp(if (style == OverlayAlertStyle.CORNER_COMPACT) 14 else 16).toFloat(),
                    strokeWidthPx = dpF(1.2f),
                    pointerSizePx = pointerSize.toFloat()
                )

                val bubbleContent = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = bubbleDrawable
                    elevation = dp(10).toFloat()
                    val baseH = dp(if (style == OverlayAlertStyle.CORNER_COMPACT) 12 else 14)
                    val baseV = dp(if (style == OverlayAlertStyle.CENTER_CARD) 16 else 12)
                    when (pointerEdge) {
                        SpeechBubbleDrawable.PointerEdge.LEFT -> setPadding(baseH + pointerSize, baseV, baseH, baseV)
                        SpeechBubbleDrawable.PointerEdge.RIGHT -> setPadding(baseH, baseV, baseH + pointerSize, baseV)
                        SpeechBubbleDrawable.PointerEdge.TOP -> setPadding(dp(18), baseV + pointerSize, dp(18), dp(16))
                    }
                }

                when (style) {
                    OverlayAlertStyle.CENTER_CARD -> {
                        val metaRow = createMetaRow(
                            context = context,
                            accentColor = accentColor,
                            dotSizePx = dp(6),
                            sourceApp = sourceApp,
                            timeText = timeText,
                            queueSize = queueSize,
                            textSizeSp = 11.5f,
                            centered = true,
                            dp = ::dp
                        )
                        bubbleContent.addView(metaRow, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

                        val kwView = TextView(context).apply {
                            text = keyword
                            setTextColor(COLOR_TEXT_PRIMARY)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                        }
                        bubbleContent.addView(kwView, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(5) })

                        if (!snippet.isNullOrBlank()) {
                            bubbleContent.addView(
                                createSnippetView(context, snippet, 13.5f, 2, Gravity.CENTER, dpF(2f)),
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { topMargin = dp(7) }
                            )
                        }
                    }

                    OverlayAlertStyle.TOP_BANNER, OverlayAlertStyle.AUTO -> {
                        val kwView = TextView(context).apply {
                            text = keyword
                            setTextColor(COLOR_TEXT_PRIMARY)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                            typeface = Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }
                        bubbleContent.addView(kwView, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ))

                        if (!snippet.isNullOrBlank()) {
                            bubbleContent.addView(
                                createSnippetView(context, snippet, 13f, 2, Gravity.START, dpF(2f)),
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { topMargin = dp(5) }
                            )
                        }

                        val bottomRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val metaRow = createMetaRow(
                            context = context,
                            accentColor = accentColor,
                            dotSizePx = dp(6),
                            sourceApp = sourceApp,
                            timeText = timeText,
                            queueSize = queueSize,
                            textSizeSp = 11f,
                            centered = false,
                            dp = ::dp
                        )
                        bottomRow.addView(metaRow, LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply { marginEnd = dp(10) })

                        val confirmBtn = createPillButton(
                            context = context,
                            label = if (isPreview) "关闭预览" else "已知晓",
                            accentColor = accentColor,
                            textSizeSp = 12.5f,
                            padH = dp(14),
                            padV = dp(7),
                            radiusPx = dp(999).toFloat(),
                            onClick = confirmAction
                        )
                        bottomRow.addView(confirmBtn, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ))
                        bubbleContent.addView(bottomRow, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(10) })
                    }

                    OverlayAlertStyle.CORNER_COMPACT -> {
                        val kwView = TextView(context).apply {
                            text = keyword
                            setTextColor(COLOR_TEXT_PRIMARY)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                            typeface = Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }
                        bubbleContent.addView(kwView, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ))

                        if (!snippet.isNullOrBlank()) {
                            bubbleContent.addView(
                                createSnippetView(context, snippet, 12f, 2, Gravity.START, dpF(1.5f)),
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { topMargin = dp(4) }
                            )
                        }

                        val bottomRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val metaRow = createMetaRow(
                            context = context,
                            accentColor = accentColor,
                            dotSizePx = dp(5),
                            sourceApp = sourceApp,
                            timeText = timeText,
                            queueSize = queueSize,
                            textSizeSp = 10.5f,
                            centered = false,
                            dp = ::dp
                        )
                        bottomRow.addView(metaRow, LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply { marginEnd = dp(8) })

                        val confirmBtn = createPillButton(
                            context = context,
                            label = if (isPreview) "关闭" else "已知晓",
                            accentColor = accentColor,
                            textSizeSp = 12f,
                            padH = dp(12),
                            padV = dp(6),
                            radiusPx = dp(999).toFloat(),
                            onClick = confirmAction
                        )
                        bottomRow.addView(confirmBtn, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ))
                        bubbleContent.addView(bottomRow, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(9) })
                    }
                }

                val contentLayout = LinearLayout(context).apply {
                    clipChildren = false
                    clipToPadding = false
                    setBackgroundColor(Color.TRANSPARENT)
                    when (style) {
                        OverlayAlertStyle.CENTER_CARD -> {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_HORIZONTAL
                            addView(owlView, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ))
                            addView(bubbleContent, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = -dp(4)
                            })
                            val centerBtn = createPillButton(
                                context = context,
                                label = if (isPreview) "关闭浮窗预览" else "已知晓，停止报警",
                                accentColor = accentColor,
                                textSizeSp = 15f,
                                padH = dp(26),
                                padV = dp(12),
                                radiusPx = dp(999).toFloat(),
                                onClick = confirmAction
                            ).apply {
                                minWidth = dp(210)
                                elevation = dp(8).toFloat()
                            }
                            addView(centerBtn, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = dp(12)
                            })
                        }

                        OverlayAlertStyle.TOP_BANNER, OverlayAlertStyle.AUTO -> {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            addView(owlView, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ))
                            addView(bubbleContent, LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                marginStart = -dp(8)
                            })
                        }

                        OverlayAlertStyle.CORNER_COMPACT -> {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            addView(bubbleContent, LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                marginEnd = -dp(8)
                            })
                            addView(owlView, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ))
                        }
                    }
                }

                // 完全透明的外层容器，关闭所有子视图裁切，保持不规则插画剪影完整
                val container = FrameLayout(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    clipChildren = false
                    clipToPadding = false
                    val pad = dp(8)
                    setPadding(pad, pad, pad, pad)
                    addView(contentLayout, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ))
                }

                val targetWidth = when (style) {
                    OverlayAlertStyle.TOP_BANNER -> minOf(screenWidthPx, dp(460))
                    OverlayAlertStyle.CENTER_CARD -> minOf((screenWidthPx * 0.86f).toInt(), dp(350))
                    OverlayAlertStyle.CORNER_COMPACT, OverlayAlertStyle.AUTO -> minOf((screenWidthPx * 0.88f).toInt(), dp(356))
                }

                val lp = WindowManager.LayoutParams(
                    targetWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    when (style) {
                        OverlayAlertStyle.TOP_BANNER -> {
                            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            y = dp(32)
                        }
                        OverlayAlertStyle.CENTER_CARD -> {
                            gravity = Gravity.CENTER
                        }
                        OverlayAlertStyle.CORNER_COMPACT, OverlayAlertStyle.AUTO -> {
                            gravity = Gravity.TOP or Gravity.END
                            x = dp(8)
                            y = dp(32)
                        }
                    }
                }

                contentLayout.alpha = 0f
                contentLayout.scaleX = 0.90f
                contentLayout.scaleY = 0.90f
                contentLayout.translationY = -dpF(10f)

                wm.addView(container, lp)
                windowManager = wm
                rootView = container

                enterAnimator = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(contentLayout, View.ALPHA, 0f, 1f),
                        ObjectAnimator.ofFloat(contentLayout, View.SCALE_X, 0.90f, 1f),
                        ObjectAnimator.ofFloat(contentLayout, View.SCALE_Y, 0.90f, 1f),
                        ObjectAnimator.ofFloat(contentLayout, View.TRANSLATION_Y, -dpF(10f), 0f)
                    )
                    duration = 220L
                    interpolator = OvershootInterpolator(1.05f)
                    start()
                }

                // 气泡细描边柔和呼吸脉冲
                pulseAnimator = ValueAnimator.ofInt(120, 255).apply {
                    duration = 800L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { anim ->
                        val alpha = anim.animatedValue as Int
                        val strokeColor = (alpha shl 24) or (accentColor and 0x00FFFFFF)
                        bubbleDrawable.setStrokeColor(strokeColor)
                    }
                    start()
                }
                VigilLogger.i(context, TAG, "插画告警浮窗已弹出 (style=$style, mode=$mode, keyword=$keyword, preview=$isPreview)")
            } catch (e: Exception) {
                Log.e(TAG, "弹出系统浮窗失败", e)
                VigilLogger.e(context, TAG, "弹出系统浮窗失败: ${e.message}")
                dismissImmediate()
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAction()
            return rootView != null
        } else {
            mainHandler.post(showAction)
            return true
        }
    }

    private fun createMetaRow(
        context: Context,
        accentColor: Int,
        dotSizePx: Int,
        sourceApp: String,
        timeText: String,
        queueSize: Int,
        textSizeSp: Float,
        centered: Boolean,
        dp: (Int) -> Int
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentColor)
            }
        }
        row.addView(dot, LinearLayout.LayoutParams(dotSizePx, dotSizePx).apply {
            marginEnd = dp(6)
        })
        val meta = TextView(context).apply {
            text = formatMeta(sourceApp, timeText, queueSize)
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.MONOSPACE
            gravity = if (centered) Gravity.CENTER else Gravity.START
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        metaTextRef = meta
        row.addView(meta, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return row
    }

    private fun createSnippetView(
        context: Context,
        snippet: String,
        sizeSp: Float,
        maxLines: Int,
        gravity: Int,
        lineSpacingExtraPx: Float
    ): TextView {
        return TextView(context).apply {
            text = snippet
            setTextColor(COLOR_TEXT_SNIPPET)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setLineSpacing(lineSpacingExtraPx, 1f)
            this.gravity = gravity
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun createPillButton(
        context: Context,
        label: String,
        accentColor: Int,
        textSizeSp: Float,
        padH: Int,
        padV: Int,
        radiusPx: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(accentColor)
            }
            setPadding(padH, padV, padH, padV)
            setOnClickListener { onClick() }
        }
    }

    private fun formatMeta(sourceApp: String, timeText: String, queueSize: Int): String {
        return if (queueSize > 1) "$sourceApp · $timeText · 1/$queueSize" else "$sourceApp · $timeText"
    }

    private fun dismissImmediate() {
        enterAnimator?.cancel()
        enterAnimator = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        owlViewRef?.release()
        owlViewRef = null
        metaTextRef = null
        currentAlertId = null
        val view = rootView ?: return
        rootView = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
            Log.w(TAG, "移除浮窗视图失败: ${e.message}")
        }
    }
}
