package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/**
 * 线条触摸窗：空闲时 WM 层仅为线条触发区；[ACTION_DOWN] 命中后立即扩全屏跟手，手势锁到 UP/CANCEL。
 */
@SuppressLint("ViewConstructor")
internal class FloatBallStripHost(
    context: Context,
    private val sceneState: FloatBallSceneState,
    private val settingsProvider: () -> AppSettings,
    private val activeSideProvider: () -> FloatBallSide,
    private val screenSizeProvider: () -> Pair<Int, Int>,
    private val onExpandTouchCapture: () -> Unit = {},
) : FrameLayout(context) {
    private val gestureDetector = FloatBallGestureDetector()
    var stripTouchable: Boolean = true
    private var gestureActive = false

    private var onDragStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onDrag: ((dx: Float, dy: Float) -> Unit)? = null
    private var onDragEnd: (() -> Unit)? = null
    private var onDragCancel: (() -> Unit)? = null
    private var onGesture: ((FloatBallGestureType, rawX: Float, rawY: Float) -> Unit)? = null
    private var onGestureHint: ((FloatBallGestureType?) -> Unit)? = null
    private var onPickPreviewStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onPickPreviewProgress: ((progress: Float) -> Unit)? = null
    private var onPickPreviewCancel: (() -> Unit)? = null

    fun updateSettings(settings: AppSettings) {
        val density = resources.displayMetrics.density
        gestureDetector.bind(
            settings = settings,
            density = density,
            onPickStart = { x, y -> onDragStart?.invoke(x, y) },
            onPickDrag = { dx, dy -> onDrag?.invoke(dx, dy) },
            onPickEnd = { onDragEnd?.invoke() },
            onPickCancel = { onDragCancel?.invoke() },
            onGesture = { type, rawX, rawY -> onGesture?.invoke(type, rawX, rawY) },
            onGestureHint = { type -> onGestureHint?.invoke(type) },
            onPickPreviewStart = { x, y -> onPickPreviewStart?.invoke(x, y) },
            onPickPreviewProgress = { progress -> onPickPreviewProgress?.invoke(progress) },
            onPickPreviewCancel = { onPickPreviewCancel?.invoke() },
        )
    }

    fun bindDragCallbacks(
        onDragStart: (screenX: Float, screenY: Float) -> Unit,
        onDrag: (dx: Float, dy: Float) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onGesture: (FloatBallGestureType, rawX: Float, rawY: Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit = {},
        onPickPreviewStart: (screenX: Float, screenY: Float) -> Unit = { _, _ -> },
        onPickPreviewProgress: (progress: Float) -> Unit = {},
        onPickPreviewCancel: () -> Unit = {},
    ) {
        this.onDragStart = onDragStart
        this.onDrag = onDrag
        this.onDragEnd = onDragEnd
        this.onDragCancel = onDragCancel
        this.onGesture = onGesture
        this.onGestureHint = onGestureHint
        this.onPickPreviewStart = onPickPreviewStart
        this.onPickPreviewProgress = onPickPreviewProgress
        this.onPickPreviewCancel = onPickPreviewCancel
        settingsProvider().let { updateSettings(it) }
    }

    fun cancelGesture() {
        gestureActive = false
        gestureDetector.cancel()
    }

    fun lockPickFromPause() {
        gestureDetector.lockPickFromPause()
    }

    fun unlockPickFromPause() {
        gestureDetector.unlockPickFromPause()
    }

    private fun hitTestLine(x: Float, y: Float): Boolean {
        if (!stripTouchable) return false
        val settings = settingsProvider()
        if (!sceneState.lineVisible.value || !FloatBallLayout.shouldShowLine(settings)) return false
        val metrics = resources.displayMetrics
        val inactiveSide = FloatBallSide.opposite(activeSideProvider())
        val (screenW, screenH) = screenSizeProvider()
        val rect = sceneState.lineHitRect(
            settings = settings,
            metrics = metrics,
            inactiveSide = inactiveSide,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
        )
        return rect.contains(x.roundToInt(), y.roundToInt())
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (gestureActive) return true
        if (!stripTouchable) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hitTestLine(event.rawX, event.rawY)) {
            onExpandTouchCapture()
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gestureActive && !stripTouchable) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitTestLine(event.rawX, event.rawY)) return false
                gestureActive = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureActive) return false
                val handled = gestureDetector.onTouchEvent(event)
                gestureActive = false
                return handled
            }
        }
        if (!gestureActive) return false
        return gestureDetector.onTouchEvent(event)
    }
}
