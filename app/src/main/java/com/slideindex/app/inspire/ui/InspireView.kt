package com.slideindex.app.inspire.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.toColorInt
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * GestureEVO InspireView — fullscreen drag-select overlay with double-confirm.
 */
class InspireView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    init {
        setWillNotDraw(false)
    }

    private val dragSelectRect = Rect()
    private val dragSelectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = "#FF4081".toColorInt()
    }
    private val dragSelectFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 255, 64, 129)
    }
    private val dragSelectShrunkRect = RectF()
    private var interactionEnabled = true
    private var needDoubleCheck = true
    private var isDrawingPath = false
    private var isAdjustingEdge = false
    private var isDraggingFabTouchArea = false
    private var downX = 0f
    private var downY = 0f
    private var moveStartX = 0f
    private var moveStartY = 0f
    private val originalRect = Rect()
    private var isMovingRect = false
    private var touchCancel = false

    var inspireViewCallback: InspireViewCallback = object : InspireViewCallback {
        override fun onDragUp(dragSelectRect: Rect) = Unit
    }

    fun getDragSelectRect(): Rect = dragSelectRect

    fun setInteractionEnabled(enabled: Boolean): Boolean {
        interactionEnabled = enabled
        if (!enabled) {
            functionBar?.visibility = GONE
        }
        return true
    }

    fun refreshDynamicColors() = Unit

    fun stopTransitionAnimation() = Unit

    fun startTransitionAnimation() = Unit

    fun showGlowEdge() = invalidate()

    fun showScreenShotFab(visibility: Int) = Unit

    suspend fun checkIsNeedDoubleCheck(): Boolean {
        needDoubleCheck = true
        return true
    }

    fun reSelect() {
        needDoubleCheck = true
        isDrawingPath = true
        showFunctionItem()
        invalidate()
    }

    private var functionBar: View? = null

    private fun showFunctionItem() {
        if (dragSelectRect.isEmpty) return
        if (functionBar == null) {
            functionBar = buildFunctionBar()
            addView(functionBar)
        }
        functionBar?.visibility = VISIBLE
        positionFunctionBar()
    }

    private fun hideFunctionItem() {
        functionBar?.visibility = INVISIBLE
    }

    private fun buildFunctionBar(): View {
        val density = resources.displayMetrics.density
        val bar = FrameLayout(context)
        val confirm = View(context).apply {
            setBackgroundColor(Color.argb(220, 30, 30, 30))
            setOnClickListener {
                inspireViewCallback.onDragUp(Rect(dragSelectRect))
            }
        }
        val cancel = View(context).apply {
            setBackgroundColor(Color.argb(180, 80, 80, 80))
            setOnClickListener { inspireViewCallback.onDragCancel() }
        }
        val buttonWidth = (72 * density).toInt()
        val buttonHeight = (36 * density).toInt()
        bar.addView(
            cancel,
            LayoutParams(buttonWidth, buttonHeight).apply {
                gravity = android.view.Gravity.START
            },
        )
        bar.addView(
            confirm,
            LayoutParams(buttonWidth, buttonHeight).apply {
                gravity = android.view.Gravity.END
            },
        )
        bar.layoutParams = LayoutParams(
            (160 * density).toInt(),
            buttonHeight,
        )
        return bar
    }

    private fun positionFunctionBar() {
        val bar = functionBar ?: return
        val density = resources.displayMetrics.density
        val margin = (8 * density).toInt()
        val lp = bar.layoutParams as LayoutParams
        lp.leftMargin = dragSelectRect.centerX() - lp.width / 2
        lp.topMargin = min(height - lp.height - margin, dragSelectRect.bottom + margin)
        bar.layoutParams = lp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dragSelectRect.isEmpty) return
        dragSelectShrunkRect.set(dragSelectRect)
        canvas.drawRoundRect(dragSelectShrunkRect, dp(8f), dp(8f), dragSelectFillPaint)
        canvas.drawRoundRect(dragSelectShrunkRect, dp(8f), dp(8f), dragSelectPaint)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled || isDraggingFabTouchArea) {
            return super.dispatchTouchEvent(event)
        }
        if (handleTouchEvent(event)) return true
        return super.dispatchTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchCancel = false
                handleDown(event)
            }
            MotionEvent.ACTION_MOVE -> if (!touchCancel) handleMove(event)
            MotionEvent.ACTION_UP -> if (!touchCancel) handleUp()
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                touchCancel = true
                inspireViewCallback.onDragCancel()
            }
            MotionEvent.ACTION_CANCEL -> touchCancel = true
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        isDrawingPath = true
        if (needDoubleCheck && !dragSelectRect.isEmpty) {
            if (isInsideRect(event.rawX, event.rawY)) {
                isMovingRect = true
                moveStartX = event.rawX
                moveStartY = event.rawY
                originalRect.set(dragSelectRect)
            }
        }
        if (!isMovingRect) {
            downX = event.rawX
            downY = event.rawY
            dragSelectRect.set(downX.toInt(), downY.toInt(), downX.toInt(), downY.toInt())
        }
        hideFunctionItem()
    }

    private fun handleMove(event: MotionEvent) {
        if (isMovingRect) {
            val dx = event.rawX - moveStartX
            val dy = event.rawY - moveStartY
            dragSelectRect.set(
                originalRect.left + dx.toInt(),
                originalRect.top + dy.toInt(),
                originalRect.right + dx.toInt(),
                originalRect.bottom + dy.toInt(),
            )
            clampRectToView()
        } else {
            val x = event.rawX.coerceIn(0f, width.toFloat())
            val y = event.rawY.coerceIn(0f, height.toFloat())
            dragSelectRect.set(
                min(downX, x).toInt(),
                min(downY, y).toInt(),
                max(downX, x).toInt(),
                max(downY, y).toInt(),
            )
        }
        inspireViewCallback.onDragMove(Rect(dragSelectRect))
        invalidate()
    }

    private fun handleUp() {
        val tooSmall = dragSelectRect.width() < dp(20f) && dragSelectRect.height() < dp(20f)
        if (needDoubleCheck && !tooSmall) {
            isMovingRect = false
            isAdjustingEdge = false
            showFunctionItem()
            invalidate()
            return
        }
        inspireViewCallback.onDragUp(Rect(dragSelectRect))
    }

    private fun clampRectToView() {
        var left = dragSelectRect.left
        var top = dragSelectRect.top
        var right = dragSelectRect.right
        var bottom = dragSelectRect.bottom
        if (left < 0) {
            right -= left
            left = 0
        }
        if (top < 0) {
            bottom -= top
            top = 0
        }
        if (right > width) {
            left -= right - width
            right = width
        }
        if (bottom > height) {
            top -= bottom - height
            bottom = height
        }
        dragSelectRect.set(left, top, right, bottom)
    }

    private fun isInsideRect(x: Float, y: Float): Boolean {
        val inset = dp(20f).toInt()
        val inner = Rect(
            dragSelectRect.left + inset,
            dragSelectRect.top + inset,
            dragSelectRect.right - inset,
            dragSelectRect.bottom - inset,
        )
        return inner.width() > 0 && inner.height() > 0 && inner.contains(x.toInt(), y.toInt())
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
