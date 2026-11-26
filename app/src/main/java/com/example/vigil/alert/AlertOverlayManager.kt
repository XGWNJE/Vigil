package com.example.vigil.alert

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
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
 * 方案 B · 静默暗夜（Obsidian Sentinel）应用外系统浮窗管理器。
 *
 * 告警触发时直接通过 WindowManager (TYPE_APPLICATION_OVERLAY) 弹出独立浮窗，
 * 无需切入 Vigil 主程序；支持顶部悬浮卡、居中强提醒、角落紧凑浮窗三种形态及自适应切换。
 */
object AlertOverlayManager {

    private const val TAG = "AlertOverlayManager"

    private const val COLOR_CARD_BG = 0xF50E1319.toInt()
    private const val COLOR_SNIPPET_BG = 0xFF171E27.toInt()
    private const val COLOR_CRIMSON = 0xFFF43F5E.toInt()
    private const val COLOR_CRIMSON_DARK = 0xFF3A1520.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFF8FAFC.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFF94A3B8.toInt()
    private const val COLOR_TRACK_BG = 0xFF232C38.toInt()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var pulseAnimator: ValueAnimator? = null
    private var progressBarRef: ProgressBar? = null
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
        return showInternal(
            context = appContext,
            alertId = item.id,
            keyword = item.keyword,
            sourceApp = item.sourceApp ?: "本机通知",
            snippet = item.snippet,
            timestampMs = item.firstTriggeredAt,
            playedLoops = item.playedLoops,
            loopLimit = item.loopLimit.coerceAtLeast(1),
            queueSize = queueSize.coerceAtLeast(1),
            style = resolved,
            isPreview = false,
            onConfirm = onConfirm
        )
    }

    /** 设置页预览真实系统浮窗（不响铃、不写历史，点按钮直接关闭）。 */
    fun showPreview(context: Context, style: OverlayAlertStyle): Boolean {
        val appContext = context.applicationContext
        if (!PermissionUtils.canDrawOverlays(appContext)) return false
        val resolved = resolveStyle(appContext, style)
        return showInternal(
            context = appContext,
            alertId = "preview_${System.currentTimeMillis()}",
            keyword = "服务器告警",
            sourceApp = "Vigil 预览",
            snippet = "核心节点负载异常 · 确认后即刻关闭浮窗",
            timestampMs = System.currentTimeMillis(),
            playedLoops = 1,
            loopLimit = 3,
            queueSize = 1,
            style = resolved,
            isPreview = true,
            onConfirm = { dismiss() }
        )
    }

    fun updateProgress(playedLoops: Int, loopLimit: Int, queueSize: Int) {
        mainHandler.post {
            val bar = progressBarRef ?: return@post
            val safeLimit = loopLimit.coerceAtLeast(1)
            val pct = (((playedLoops + 1).coerceAtMost(safeLimit)) * 100) / safeLimit
            bar.progress = pct.coerceIn(10, 100)
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
        return if (widthDp >= 600f) OverlayAlertStyle.CORNER_COMPACT else OverlayAlertStyle.TOP_BANNER
    }

    private fun showInternal(
        context: Context,
        alertId: String,
        keyword: String,
        sourceApp: String,
        snippet: String?,
        timestampMs: Long,
        playedLoops: Int,
        loopLimit: Int,
        queueSize: Int,
        style: OverlayAlertStyle,
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

                val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestampMs))
                currentAlertId = alertId
                currentSourceApp = sourceApp
                currentTimeText = timeText

                val cardDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(if (style == OverlayAlertStyle.CORNER_COMPACT) 16 else 20).toFloat()
                    setColor(COLOR_CARD_BG)
                    setStroke(dp(2), COLOR_CRIMSON)
                }

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = cardDrawable
                    elevation = dp(14).toFloat()
                    val padH = dp(if (style == OverlayAlertStyle.CORNER_COMPACT) 14 else 18)
                    val padV = dp(if (style == OverlayAlertStyle.CENTER_CARD) 22 else 14)
                    setPadding(padH, padV, padH, padV)
                }

                if (style == OverlayAlertStyle.CENTER_CARD) {
                    // 02 · 居中强提醒浮窗：顶部醒目警戒图标 + 居中大号关键词 + 元信息
                    val badge = TextView(context).apply {
                        text = "!"
                        setTextColor(COLOR_CRIMSON)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(COLOR_CRIMSON_DARK)
                            setStroke(dp(1), COLOR_CRIMSON)
                        }
                    }
                    card.addView(badge, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = dp(10)
                    })

                    val kwView = TextView(context).apply {
                        text = keyword
                        setTextColor(COLOR_TEXT_PRIMARY)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    card.addView(kwView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))

                    val meta = TextView(context).apply {
                        text = formatMeta(sourceApp, timeText, queueSize)
                        setTextColor(COLOR_TEXT_SECONDARY)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = Typeface.MONOSPACE
                        gravity = Gravity.CENTER
                    }
                    metaTextRef = meta
                    card.addView(meta, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) })
                } else {
                    // 01 顶部悬浮卡 & 03 宽屏角落浮窗：紧凑单行标题头
                    val header = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val dot = View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(COLOR_CRIMSON)
                        }
                    }
                    header.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                        marginEnd = dp(8)
                    })

                    val kwView = TextView(context).apply {
                        text = keyword
                        setTextColor(COLOR_TEXT_PRIMARY)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (style == OverlayAlertStyle.CORNER_COMPACT) 16f else 18f)
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    header.addView(kwView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                    val meta = TextView(context).apply {
                        text = formatMeta(sourceApp, timeText, queueSize)
                        setTextColor(COLOR_TEXT_SECONDARY)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.MONOSPACE
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    metaTextRef = meta
                    header.addView(meta, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(8) })

                    card.addView(header, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))
                }

                // 通知摘要（角落紧凑模式不为空也保留单行，其它最多 2 行）
                if (!snippet.isNullOrBlank()) {
                    val snippetView = TextView(context).apply {
                        text = snippet
                        setTextColor(COLOR_TEXT_PRIMARY)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (style == OverlayAlertStyle.CORNER_COMPACT) 12f else 13f)
                        maxLines = if (style == OverlayAlertStyle.CORNER_COMPACT) 1 else 2
                        ellipsize = TextUtils.TruncateAt.END
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(10).toFloat()
                            setColor(COLOR_SNIPPET_BG)
                        }
                        setPadding(dp(10), dp(8), dp(10), dp(8))
                    }
                    card.addView(snippetView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) })
                }

                // 播放进度条
                val trackDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(COLOR_TRACK_BG)
                }
                val fillDrawable = ClipDrawable(
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(3).toFloat()
                        setColor(COLOR_CRIMSON)
                    },
                    Gravity.START,
                    ClipDrawable.HORIZONTAL
                )
                val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    val safeLimit = loopLimit.coerceAtLeast(1)
                    progress = ((((playedLoops + 1).coerceAtMost(safeLimit)) * 100) / safeLimit).coerceIn(15, 100)
                    progressDrawable = LayerDrawable(arrayOf(trackDrawable, fillDrawable)).apply {
                        setId(0, android.R.id.background)
                        setId(1, android.R.id.progress)
                    }
                }
                progressBarRef = progressBar
                card.addView(progressBar, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(4)
                ).apply {
                    topMargin = dp(12)
                    bottomMargin = dp(12)
                })

                // 主操作按钮：已知晓，停止报警
                val confirmBtn = TextView(context).apply {
                    text = if (isPreview) "关闭浮窗预览" else "已知晓，停止报警"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (style == OverlayAlertStyle.CORNER_COMPACT) 14f else 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(999).toFloat()
                        setColor(COLOR_CRIMSON)
                    }
                    setPadding(dp(16), dp(if (style == OverlayAlertStyle.CORNER_COMPACT) 10 else 12), dp(16), dp(if (style == OverlayAlertStyle.CORNER_COMPACT) 10 else 12))
                    setOnClickListener {
                        dismissImmediate()
                        onConfirm()
                    }
                }
                card.addView(confirmBtn, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))

                val container = FrameLayout(context).apply {
                    val margin = dp(12)
                    setPadding(margin, margin, margin, margin)
                    addView(card, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ))
                }

                val targetWidth = when (style) {
                    OverlayAlertStyle.TOP_BANNER -> minOf(screenWidthPx, dp(480))
                    OverlayAlertStyle.CENTER_CARD -> minOf((screenWidthPx * 0.90f).toInt(), dp(390))
                    OverlayAlertStyle.CORNER_COMPACT, OverlayAlertStyle.AUTO -> minOf((screenWidthPx * 0.78f).toInt(), dp(320))
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
                            y = dp(36)
                        }
                        OverlayAlertStyle.CENTER_CARD -> {
                            gravity = Gravity.CENTER
                        }
                        OverlayAlertStyle.CORNER_COMPACT, OverlayAlertStyle.AUTO -> {
                            gravity = Gravity.TOP or Gravity.END
                            x = dp(8)
                            y = dp(36)
                        }
                    }
                }

                wm.addView(container, lp)
                windowManager = wm
                rootView = container

                // 警戒红框呼吸脉冲动效
                pulseAnimator = ValueAnimator.ofInt(110, 255).apply {
                    duration = 750L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { anim ->
                        val alpha = anim.animatedValue as Int
                        val strokeColor = (alpha shl 24) or (COLOR_CRIMSON and 0x00FFFFFF)
                        cardDrawable.setStroke(dp(2), strokeColor)
                    }
                    start()
                }
                VigilLogger.i(context, TAG, "系统告警浮窗已弹出 (style=$style, keyword=$keyword, preview=$isPreview)")
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

    private fun formatMeta(sourceApp: String, timeText: String, queueSize: Int): String {
        return if (queueSize > 1) "$sourceApp · $timeText · 1/$queueSize" else "$sourceApp · $timeText"
    }

    private fun dismissImmediate() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        progressBarRef = null
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
