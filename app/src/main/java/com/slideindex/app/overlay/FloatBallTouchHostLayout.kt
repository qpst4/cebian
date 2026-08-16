package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/**
 * 球体触摸窗：空闲时 WM 层仅为球区；滑出 slop 进入取词后由 [FloatBallOverlay] 扩全屏跟手，手势锁到 UP/CANCEL。
 */
@SuppressLint("ViewConstructor")
internal class FloatBallTouchHostLayout(
    context: Context,
    private val sceneState: FloatBallSceneState,
    private val settingsProvider: () -> AppSettings,
    private val activeSideProvider: () -> FloatBallSide,
    private val screenSizeProvider: () -> Pair<Int, Int>,
) : FrameLayout(context) {

    private val ballDetector = FloatBallGestureDetector()

    var ballStripTouchable: Boolean = true

    private var gestureCaptureActive = false

    private var onBallDragStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onBallDrag: ((dx: Float, dy: Float) -> Unit)? = null
    private var onBallDragEnd: (() -> Unit)? = null
    private var onBallDragCancel: (() -> Unit)? = null
    private var onBallGesture: ((FloatBallGestureType, rawX: Float, rawY: Float) -> Unit)? = null
    private var onBallGestureHint: ((FloatBallGestureType?) -> Unit)? = null
    private var onBallPickPreviewStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onBallPickPreviewProgress: ((progress: Float) -> Unit)? = null
    private var onBallPickPreviewCancel: (() -> Unit)? = null
    private var onLauncherCaptureMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureUp: ((rawX: Float, rawY: Float) -> Unit)? = null

    private var idleChromeView: View? = null

    init {
        isClickable = false
        isFocusable = false
    }

    /** 空闲态球体视觉叠在触摸窗内，避免全屏 display 挡触摸。 */
    fun setIdleChrome(view: View?, owner: OverlayComposeOwner?) {
        if (idleChromeView === view) return
        idleChromeView?.let { removeView(it) }
        idleChromeView = view
        if (owner != null) {
            OverlayCompose.bindOwners(this, owner)
        } else {
            OverlayCompose.clearViewTreeOwners(this)
        }
        if (view != null) {
            view.isClickable = false
            view.isFocusable = false
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    fun updateSettings(settings: AppSettings) {
        val density = resources.displayMetrics.density
        bindDetector(
            detector = ballDetector,
            settings = settings,
            density = density,
            onDragStart = { x, y -> onBallDragStart?.invoke(x, y) },
            onDrag = { dx, dy -> onBallDrag?.invoke(dx, dy) },
            onDragEnd = { onBallDragEnd?.invoke() },
            onDragCancel = { onBallDragCancel?.invoke() },
            onGesture = { type, x, y -> onBallGesture?.invoke(type, x, y) },
            onGestureHint = { type -> onBallGestureHint?.invoke(type) },
            onPickPreviewStart = { x, y -> onBallPickPreviewStart?.invoke(x, y) },
            onPickPreviewProgress = { p -> onBallPickPreviewProgress?.invoke(p) },
            onPickPreviewCancel = { onBallPickPreviewCancel?.invoke() },
            onLauncherCaptureMove = { x, y -> onLauncherCaptureMove?.invoke(x, y) },
            onLauncherCaptureUp = { x, y -> onLauncherCaptureUp?.invoke(x, y) },
        )
    }

    fun bindBallCallbacks(
        onDragStart: (screenX: Float, screenY: Float) -> Unit,
        onDrag: (dx: Float, dy: Float) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onGesture: (FloatBallGestureType, rawX: Float, rawY: Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit = {},
        onPickPreviewStart: (screenX: Float, screenY: Float) -> Unit = { _, _ -> },
        onPickPreviewProgress: (progress: Float) -> Unit = {},
        onPickPreviewCancel: () -> Unit = {},
        onLauncherCaptureMove: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
        onLauncherCaptureUp: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
    ) {
        onBallDragStart = onDragStart
        onBallDrag = onDrag
        onBallDragEnd = onDragEnd
        onBallDragCancel = onDragCancel
        onBallGesture = onGesture
        onBallGestureHint = onGestureHint
        onBallPickPreviewStart = onPickPreviewStart
        onBallPickPreviewProgress = onPickPreviewProgress
        onBallPickPreviewCancel = onPickPreviewCancel
        this.onLauncherCaptureMove = onLauncherCaptureMove
        this.onLauncherCaptureUp = onLauncherCaptureUp
        settingsProvider().let { updateSettings(it) }
    }

    fun endGestureCapture() {
        gestureCaptureActive = false
    }

    fun forceEndGestureCapture() {
        if (gestureCaptureActive) {
            ballDetector.cancel()
        }
        endGestureCapture()
    }

    fun lockPickFromPause() {
        ballDetector.lockPickFromPause()
    }

    fun unlockPickFromPause() {
        ballDetector.unlockPickFromPause()
    }

    private fun bindDetector(
        detector: FloatBallGestureDetector,
        settings: AppSettings,
        density: Float,
        onDragStart: (Float, Float) -> Unit,
        onDrag: (Float, Float) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onGesture: (FloatBallGestureType, Float, Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit,
        onPickPreviewStart: (Float, Float) -> Unit,
        onPickPreviewProgress: (Float) -> Unit,
        onPickPreviewCancel: () -> Unit,
        onLauncherCaptureMove: (Float, Float) -> Unit = { _, _ -> },
        onLauncherCaptureUp: (Float, Float) -> Unit = { _, _ -> },
    ) {
        detector.bind(
            settings = settings,
            density = density,
            onPickStart = onDragStart,
            onPickDrag = onDrag,
            onPickEnd = onDragEnd,
            onPickCancel = onDragCancel,
            onGesture = onGesture,
            onGestureHint = onGestureHint,
            onPickPreviewStart = onPickPreviewStart,
            onPickPreviewProgress = onPickPreviewProgress,
        onPickPreviewCancel = onPickPreviewCancel,
        onLauncherCaptureMove = onLauncherCaptureMove,
        onLauncherCaptureUp = onLauncherCaptureUp,
    )
    }

    fun beginLauncherCaptureMode() {
        gestureCaptureActive = true
        ballDetector.enterLauncherCaptureMode()
    }

    fun isLauncherCaptureMode(): Boolean = ballDetector.isLauncherCaptureMode()

    fun cancelLauncherCaptureMode() {
        ballDetector.cancelLauncherCaptureMode()
        endGestureCapture()
    }

    private fun hitTestBall(x: Float, y: Float): Boolean {
        if (!ballStripTouchable) return false
        val settings = settingsProvider()
        val metrics = resources.displayMetrics
        val activeSide = activeSideProvider()
        val (screenW, screenH) = screenSizeProvider()
        val ballRect = sceneState.ballHitRect(settings, metrics, activeSide, screenW, screenH)
        return ballRect.contains(x.roundToInt(), y.roundToInt())
    }

    /**
     * WM 窗在 z-order 重挂后可能大于命中区；未命中时返回 false，让触摸落到下层应用。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!gestureCaptureActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!hitTestBall(event.rawX, event.rawY)) return false
                }
                else -> return false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (gestureCaptureActive) return true
        if (!ballStripTouchable) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hitTestBall(event.rawX, event.rawY)) {
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gestureCaptureActive && !ballStripTouchable) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitTestBall(event.rawX, event.rawY)) return false
                gestureCaptureActive = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureCaptureActive && !ballDetector.isLauncherCaptureMode()) return false
                val handled = ballDetector.onTouchEvent(event)
                if (!ballDetector.isLauncherCaptureMode()) {
                    gestureCaptureActive = false
                }
                return handled
            }
        }
        if (!gestureCaptureActive) return false
        return ballDetector.onTouchEvent(event)
    }
}
