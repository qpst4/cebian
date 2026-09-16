package com.slideindex.app.screensearch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

class ScreenSearchHighlightView(context: Context) : View(context) {
    private val rects = ArrayList<Rect>()
    private val density = resources.displayMetrics.density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 3f
        color = Color.WHITE
        pathEffect = DashPathEffect(floatArrayOf(density * 12f, density * 6f), 0f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 8f
        color = Color.argb(96, 255, 255, 255)
    }

    fun showRects(screenRects: List<Rect>) {
        rects.clear()
        rects.addAll(screenRects)
        visibility = VISIBLE
        invalidate()
    }

    fun clearRects() {
        rects.clear()
        visibility = GONE
        invalidate()
    }

    fun hasRects(): Boolean = rects.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (rect in rects) {
            canvas.drawRect(rect, glowPaint)
            canvas.drawRect(rect, strokePaint)
        }
    }
}
