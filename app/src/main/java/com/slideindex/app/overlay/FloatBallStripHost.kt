package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType

/**
 * 悬浮球触摸宿主：拦截触摸并交给 [FloatBallGestureDetector] 区分手势与文本拾取拖拽。
 */
@SuppressLint("ViewConstructor")
internal class FloatBallStripHost(context: Context) : FrameLayout(context) {
    private val gestureDetector = FloatBallGestureDetector()
    private var settings: AppSettings? = null
    var stripTouchable: Boolean = true

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
        this.settings = settings
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
        settings?.let { updateSettings(it) }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = stripTouchable

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        stripTouchable && gestureDetector.onTouchEvent(event)
}
