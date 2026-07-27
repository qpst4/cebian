package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/**
 * 统一触摸窗：全屏 [MATCH_PARENT]，空闲时按球/线条区域 hit-test，区域外返回 false 穿透。
 * 拖拽开始后通过 [onExpandTouchCapture] 确保全屏捕获，避免手指移出条带后丢事件。
 */
@SuppressLint("ViewConstructor")
internal class FloatBallTouchHostLayout(
    context: Context,
    private val sceneState: FloatBallSceneState,
    private val settingsProvider: () -> AppSettings,
    private val activeSideProvider: () -> FloatBallSide,
    private val onExpandTouchCapture: () -> Unit,
    private val onCollapseTouchCapture: () -> Unit,
) : FrameLayout(context) {

    private enum class ActiveStrip {
        NONE,
        BALL,
        LINE,
    }

    private val ballDetector = FloatBallGestureDetector()
    private val lineDetector = FloatBallGestureDetector()

    var ballStripTouchable: Boolean = true
    var lineStripTouchable: Boolean = true

    private var activeStrip = ActiveStrip.NONE
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

    private var onLineDragStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onLineDrag: ((dx: Float, dy: Float) -> Unit)? = null
    private var onLineDragEnd: (() -> Unit)? = null
    private var onLineDragCancel: (() -> Unit)? = null
    private var onLineGesture: ((FloatBallGestureType, rawX: Float, rawY: Float) -> Unit)? = null
    private var onLineGestureHint: ((FloatBallGestureType?) -> Unit)? = null
    private var onLinePickPreviewStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onLinePickPreviewProgress: ((progress: Float) -> Unit)? = null
    private var onLinePickPreviewCancel: (() -> Unit)? = null

    init {
        isClickable = false
        isFocusable = false
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
        )
        bindDetector(
            detector = lineDetector,
            settings = settings,
            density = density,
            onDragStart = { x, y -> onLineDragStart?.invoke(x, y) },
            onDrag = { dx, dy -> onLineDrag?.invoke(dx, dy) },
            onDragEnd = { onLineDragEnd?.invoke() },
            onDragCancel = { onLineDragCancel?.invoke() },
            onGesture = { type, x, y -> onLineGesture?.invoke(type, x, y) },
            onGestureHint = { type -> onLineGestureHint?.invoke(type) },
            onPickPreviewStart = { x, y -> onLinePickPreviewStart?.invoke(x, y) },
            onPickPreviewProgress = { p -> onLinePickPreviewProgress?.invoke(p) },
            onPickPreviewCancel = { onLinePickPreviewCancel?.invoke() },
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
        settingsProvider().let { updateSettings(it) }
    }

    fun bindLineCallbacks(
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
        onLineDragStart = onDragStart
        onLineDrag = onDrag
        onLineDragEnd = onDragEnd
        onLineDragCancel = onDragCancel
        onLineGesture = onGesture
        onLineGestureHint = onGestureHint
        onLinePickPreviewStart = onPickPreviewStart
        onLinePickPreviewProgress = onPickPreviewProgress
        onLinePickPreviewCancel = onPickPreviewCancel
        settingsProvider().let { updateSettings(it) }
    }

    fun endGestureCapture() {
        gestureCaptureActive = false
        activeStrip = ActiveStrip.NONE
        onCollapseTouchCapture()
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
        )
    }

    private fun hitTestStrip(x: Float, y: Float): ActiveStrip {
        val settings = settingsProvider()
        val metrics = resources.displayMetrics
        val activeSide = activeSideProvider()
        val (screenW, screenH) = metrics.widthPixels to metrics.heightPixels
        if (ballStripTouchable) {
            val ballRect = sceneState.ballHitRect(settings, metrics, activeSide, screenW, screenH)
            if (ballRect.contains(x.roundToInt(), y.roundToInt())) {
                return ActiveStrip.BALL
            }
        }
        if (lineStripTouchable && sceneState.lineVisible.value && FloatBallLayout.shouldShowLine(settings)) {
            val inactiveSide = FloatBallSide.opposite(activeSide)
            val lineRect = sceneState.lineHitRect(settings, metrics, inactiveSide, screenW, screenH)
            if (lineRect.contains(x.roundToInt(), y.roundToInt())) {
                return ActiveStrip.LINE
            }
        }
        return ActiveStrip.NONE
    }

    private fun activeDetector(): FloatBallGestureDetector? = when (activeStrip) {
        ActiveStrip.BALL -> ballDetector
        ActiveStrip.LINE -> lineDetector
        ActiveStrip.NONE -> null
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (gestureCaptureActive) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val strip = hitTestStrip(event.rawX, event.rawY)
            if (strip != ActiveStrip.NONE) {
                activeStrip = strip
                gestureCaptureActive = true
                onExpandTouchCapture()
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureCaptureActive) {
            val detector = activeDetector() ?: return true
            val handled = detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                endGestureCapture()
            }
            return handled
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val strip = hitTestStrip(event.rawX, event.rawY)
            if (strip == ActiveStrip.NONE) return false
            activeStrip = strip
            gestureCaptureActive = true
            onExpandTouchCapture()
            return activeDetector()?.onTouchEvent(event) == true
        }
        return false
    }
}
