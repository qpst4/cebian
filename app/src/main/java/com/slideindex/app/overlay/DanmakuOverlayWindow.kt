package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.slideindex.app.R
import com.slideindex.app.message.MessageThemeSpec
import com.slideindex.app.message.applyMessageThemeBackground
import com.slideindex.app.message.NotificationData
import kotlin.math.max
import kotlin.math.min

@SuppressLint("StaticFieldLeak") // Overlay singleton; views cleared on detach
object DanmakuOverlayWindow {
    private const val TAG = "DanmakuOverlay"
    private const val TRACK_HEIGHT_DP = 52f
    private const val ANIMATION_DURATION_MS = 5_500L
    private const val MAX_TRACKS = 6
    private const val ATTACH_RETRY_MS = 5_000L
    private const val TOP_INSET_DP = 72f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayContext: Context? = null
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var rootLayoutParams: WindowManager.LayoutParams? = null
    private var attachFailedAt = 0L
    private val occupiedTracks = BooleanArray(MAX_TRACKS)
    private var screenWidth = 0
    private var bandHeightPx = 0

    fun show(
        context: Context,
        data: NotificationData,
        theme: MessageThemeSpec,
        opacity: Float,
        maxLines: Int = 1,
        speedLevel: Int = com.slideindex.app.message.DanmakuSpeed.NORMAL,
    ) {
        mainHandler.post {
            if (!ensureAttached(context)) {
                Log.w(TAG, "show skipped: overlay not attached")
                return@post
            }
            val root = rootView ?: return@post
            val track = pickTrack()
            occupiedTracks[track] = true
            val item = createDanmakuItem(root.context, data, theme, opacity, track, maxLines)
            root.addView(item)
            item.post {
                bringToFrontInternal()
                startScrollAnimation(item, track, speedLevel)
            }
        }
    }

    fun bringToFront() {
        mainHandler.post { bringToFrontInternal() }
    }

    fun detach() {
        mainHandler.post {
            val wm = windowManager
            val root = rootView
            if (wm != null && root != null) {
                runCatching { wm.removeView(root) }
            }
            windowManager = null
            rootView = null
            rootLayoutParams = null
            overlayContext = null
            attachFailedAt = 0L
            occupiedTracks.fill(false)
        }
    }

    private fun bringToFrontInternal() {
        val wm = windowManager ?: return
        val root = rootView ?: return
        val params = rootLayoutParams ?: return
        if (!root.isAttachedToWindow) return
        runCatching {
            wm.removeView(root)
            wm.addView(root, params)
        }.onFailure { error ->
            Log.w(TAG, "bringToFront failed", error)
        }
    }

    private fun ensureAttached(context: Context): Boolean {
        if (rootView != null) return true
        val now = System.currentTimeMillis()
        if (attachFailedAt > 0L && now - attachFailedAt < ATTACH_RETRY_MS) {
            Log.w(TAG, "ensureAttached skipped: retry cooldown")
            return false
        }
        attachFailedAt = 0L

        val hostContext = MessageOverlayHost.resolveHostContext(context) ?: run {
            Log.w(TAG, "overlay permission not granted")
            return false
        }

        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val dm = hostContext.resources.displayMetrics
        screenWidth = dm.widthPixels
        val density = dm.density
        val statusBarHeight = statusBarHeight(hostContext)
        val desiredBandHeight = (TRACK_HEIGHT_DP * MAX_TRACKS * density).toInt()
        val maxBandHeight = dm.heightPixels * 3 / 5
        bandHeightPx = min(desiredBandHeight, maxBandHeight).coerceAtLeast((TRACK_HEIGHT_DP * density).toInt())

        val params = OverlayWindowTypes.createPresentationParams(hostContext).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = bandHeightPx
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = statusBarHeight + (TOP_INSET_DP * density).toInt()
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val root = FrameLayout(hostContext).apply {
            clipChildren = false
            clipToPadding = false
        }
        val attached = runCatching { wm.addView(root, params) }
            .onFailure { error ->
                attachFailedAt = System.currentTimeMillis()
                Log.e(TAG, "addView failed", error)
            }
            .isSuccess
        if (!attached) return false

        overlayContext = hostContext
        windowManager = wm
        rootView = root
        rootLayoutParams = params
        Log.d(TAG, "danmaku overlay attached bandHeight=$bandHeightPx y=${params.y}")
        return true
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource") // status_bar_height is the standard inset probe
    private fun statusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun pickTrack(): Int {
        for (i in 0 until MAX_TRACKS) {
            if (!occupiedTracks[i]) return i
        }
        return 0
    }

    private fun createDanmakuItem(
        context: Context,
        data: NotificationData,
        theme: MessageThemeSpec,
        opacity: Float,
        track: Int,
        maxLines: Int,
    ): RelativeLayout {
        val density = context.resources.displayMetrics.density
        val trackHeightPx = (TRACK_HEIGHT_DP * density).toInt()
        val topPx = track * trackHeightPx

        val container = RelativeLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = topPx
            }
            applyMessageThemeBackground(this, theme, opacity)
            val padH = (theme.paddingHorizontalDp * density).toInt()
            val padV = (theme.paddingVerticalDp * density).toInt()
            setPadding(padH, padV, padH, padV)
            elevation = 8f * density
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val iconView = ImageView(context).apply {
            val size = (32 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (8 * density).toInt()
                topMargin = (theme.avatarTranslationYDp * density).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            val icon = data.largeIcon ?: data.appIcon
            if (icon != null) {
                setImageBitmap(icon)
            } else {
                setImageResource(R.drawable.ic_notification)
            }
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val titleView = TextView(context).apply {
            text = data.title.ifBlank { data.packageName }
            setTextColor(theme.titleColorArgb)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setMaxLines(1)
        }

        textColumn.addView(titleView)
        if (data.content.isNotBlank()) {
            val contentView = TextView(context).apply {
                text = data.content
                setTextColor(theme.contentColorArgb)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setMaxLines(maxLines.coerceIn(1, 3))
            }
            textColumn.addView(contentView)
        }

        row.addView(iconView)
        row.addView(textColumn)
        container.addView(row)
        return container
    }

    private fun startScrollAnimation(item: View, track: Int, speedLevel: Int) {
        if (item.parent == null) {
            occupiedTracks[track] = false
            return
        }
        item.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(bandHeightPx, View.MeasureSpec.AT_MOST),
        )
        val itemWidth = max(if (item.width > 0) item.width else item.measuredWidth, 1)
        val startX = screenWidth.toFloat()
        val endX = -(itemWidth + screenWidth * 0.12f)
        item.x = startX
        val durationMs = com.slideindex.app.message.DanmakuSpeed.durationMs(speedLevel)
        ObjectAnimator.ofFloat(item, View.X, startX, endX).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    mainHandler.post {
                        rootView?.removeView(item)
                        occupiedTracks[track] = false
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    mainHandler.post {
                        rootView?.removeView(item)
                        occupiedTracks[track] = false
                    }
                }
            })
        }.start()
    }
}
