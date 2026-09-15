package com.slideindex.app.imageeditor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.slideindex.app.imageeditor.model.NumberBadgeStyle

class NumberBadgeStylePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = Color.WHITE
    }
    private var style: NumberBadgeStyle = NumberBadgeStyle.FILLED_CIRCLE
    private var accentColor: Int = Color.RED

    fun setStylePreview(numberStyle: NumberBadgeStyle, color: Int) {
        style = numberStyle
        accentColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        if (right <= left || bottom <= top) return
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val radius = minOf(right - left, bottom - top) * 0.34f
        labelPaint.textSize = radius * 1.05f
        when (style) {
            NumberBadgeStyle.FILLED_CIRCLE -> {
                fillPaint.style = Paint.Style.FILL
                fillPaint.color = accentColor
                canvas.drawCircle(cx, cy, radius, fillPaint)
                labelPaint.color = contrastingTextColor(accentColor)
                drawLabel(canvas, cx, cy, "1")
            }
            NumberBadgeStyle.OUTLINE_CIRCLE -> {
                strokePaint.color = accentColor
                canvas.drawCircle(cx, cy, radius, strokePaint)
                labelPaint.color = accentColor
                drawLabel(canvas, cx, cy, "1")
            }
            NumberBadgeStyle.FILLED_SQUARE -> {
                val half = radius * 0.92f
                val rect = RectF(cx - half, cy - half, cx + half, cy + half)
                fillPaint.style = Paint.Style.FILL
                fillPaint.color = accentColor
                canvas.drawRoundRect(rect, half * 0.28f, half * 0.28f, fillPaint)
                labelPaint.color = contrastingTextColor(accentColor)
                drawLabel(canvas, cx, cy, "1")
            }
        }
    }

    private fun drawLabel(canvas: Canvas, cx: Float, cy: Float, label: String) {
        val metrics = labelPaint.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, cx, baseline, labelPaint)
    }

    private fun contrastingTextColor(background: Int): Int {
        val r = Color.red(background)
        val g = Color.green(background)
        val b = Color.blue(background)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.62) Color.BLACK else Color.WHITE
    }
}
