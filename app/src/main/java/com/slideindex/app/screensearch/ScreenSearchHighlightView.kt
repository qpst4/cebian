package com.slideindex.app.screensearch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

class ScreenSearchHighlightView(context: Context) : View(context) {
    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    private val rects = ArrayList<Rect>()
    private val density = resources.displayMetrics.density
    private val outerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 5f
        color = Color.argb(230, 0, 0, 0)
    }
    private val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 2.5f
        color = Color.argb(255, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(density * 10f, density * 6f), 0f)
    }
    private val accentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 2.5f
        color = Color.argb(255, 33, 150, 243)
        pathEffect = DashPathEffect(floatArrayOf(density * 10f, density * 6f), density * 8f)
    }

    fun showRects(screenRects: List<Rect>) {
        rects.clear()
        rects.addAll(screenRects)
        visibility = VISIBLE
        invalidate()
    }

    fun hideRects() {
        rects.clear()
        visibility = GONE
        invalidate()
    }

    fun clearRects() = hideRects()

    fun hasRects(): Boolean = rects.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (rect in rects) {
            canvas.drawRect(rect, outerStrokePaint)
            canvas.drawRect(rect, innerStrokePaint)
            canvas.drawRect(rect, accentStrokePaint)
        }
    }
}
