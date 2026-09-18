package com.slideindex.app.imageeditor.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.slideindex.app.imageeditor.model.ShapeType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ShapeTypePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = resources.displayMetrics.density * 2.1f
        color = Color.WHITE
    }
    private val arrowPath = Path()
    private val polygonPath = Path()
    private var shapeType = ShapeType.RECTANGLE

    fun setShapePreview(type: ShapeType, color: Int) {
        shapeType = type
        shapePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        if (right <= left || bottom <= top) return
        val stroke = shapePaint.strokeWidth
        val bounds = RectF(left + stroke, top + stroke, right - stroke, bottom - stroke)
        when (shapeType) {
            ShapeType.RECTANGLE -> canvas.drawRect(bounds, shapePaint)
            ShapeType.ROUNDED_RECTANGLE -> canvas.drawRoundRect(
                bounds,
                bounds.width() * 0.16f,
                bounds.height() * 0.16f,
                shapePaint,
            )
            ShapeType.OVAL -> canvas.drawOval(bounds, shapePaint)
            ShapeType.LINE -> canvas.drawLine(bounds.left, bounds.bottom, bounds.right, bounds.top, shapePaint)
            ShapeType.ARROW -> drawArrow(canvas, bounds)
            ShapeType.DOUBLE_ARROW -> drawDoubleArrow(canvas, bounds)
            ShapeType.DIAMOND -> drawDiamond(canvas, bounds)
            ShapeType.TRIANGLE -> drawTriangle(canvas, bounds)
        }
    }

    private fun drawArrow(canvas: Canvas, bounds: RectF) {
        arrowPath.reset()
        val startX = bounds.left + bounds.width() * 0.06f
        val startY = bounds.bottom - bounds.height() * 0.18f
        val endX = bounds.right - bounds.width() * 0.04f
        val endY = bounds.top + bounds.height() * 0.18f
        val dx = endX - startX
        val dy = endY - startY
        val length = max(sqrt(dx * dx + dy * dy), 1f)
        val ux = dx / length
        val uy = dy / length
        val head = max(min(bounds.width(), bounds.height()) * 0.34f, shapePaint.strokeWidth * 3f)
        arrowPath.moveTo(startX, startY)
        arrowPath.lineTo(endX, endY)
        val hx = ux * head
        val hy = uy * head
        val wingX = -uy * head * 0.45f
        val wingY = ux * head * 0.45f
        arrowPath.moveTo(endX, endY)
        arrowPath.lineTo(endX - hx + wingX, endY - hy + wingY)
        arrowPath.moveTo(endX, endY)
        arrowPath.lineTo(endX - hx - wingX, endY - hy - wingY)
        canvas.drawPath(arrowPath, shapePaint)
    }

    private fun drawDoubleArrow(canvas: Canvas, bounds: RectF) {
        arrowPath.reset()
        val startX = bounds.left + bounds.width() * 0.06f
        val startY = bounds.bottom - bounds.height() * 0.18f
        val endX = bounds.right - bounds.width() * 0.04f
        val endY = bounds.top + bounds.height() * 0.18f
        val dx = endX - startX
        val dy = endY - startY
        val length = max(sqrt(dx * dx + dy * dy), 1f)
        val ux = dx / length
        val uy = dy / length
        val head = max(min(bounds.width(), bounds.height()) * 0.30f, shapePaint.strokeWidth * 2.6f)
        arrowPath.moveTo(startX, startY)
        arrowPath.lineTo(endX, endY)
        val hx = ux * head
        val hy = uy * head
        val wingX = -uy * head * 0.45f
        val wingY = ux * head * 0.45f
        // End arrow head
        arrowPath.moveTo(endX, endY)
        arrowPath.lineTo(endX - hx + wingX, endY - hy + wingY)
        arrowPath.moveTo(endX, endY)
        arrowPath.lineTo(endX - hx - wingX, endY - hy - wingY)
        // Start arrow head
        arrowPath.moveTo(startX, startY)
        arrowPath.lineTo(startX + hx + wingX, startY + hy + wingY)
        arrowPath.moveTo(startX, startY)
        arrowPath.lineTo(startX + hx - wingX, startY + hy - wingY)
        canvas.drawPath(arrowPath, shapePaint)
    }

    private fun drawDiamond(canvas: Canvas, bounds: RectF) {
        polygonPath.reset()
        polygonPath.moveTo(bounds.centerX(), bounds.top)
        polygonPath.lineTo(bounds.right, bounds.centerY())
        polygonPath.lineTo(bounds.centerX(), bounds.bottom)
        polygonPath.lineTo(bounds.left, bounds.centerY())
        polygonPath.close()
        canvas.drawPath(polygonPath, shapePaint)
    }

    private fun drawTriangle(canvas: Canvas, bounds: RectF) {
        polygonPath.reset()
        polygonPath.moveTo(bounds.centerX(), bounds.top)
        polygonPath.lineTo(bounds.right, bounds.bottom)
        polygonPath.lineTo(bounds.left, bounds.bottom)
        polygonPath.close()
        canvas.drawPath(polygonPath, shapePaint)
    }
}
