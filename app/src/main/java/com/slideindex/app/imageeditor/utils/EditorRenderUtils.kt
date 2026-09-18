package com.slideindex.app.imageeditor.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.slideindex.app.imageeditor.model.EditAction
import com.slideindex.app.imageeditor.model.EditorPoint
import com.slideindex.app.imageeditor.model.NumberBadgeStyle
import com.slideindex.app.imageeditor.model.ShapeType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object EditorRenderUtils {
    fun createPath(points: List<EditorPoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        if (points.size == 1) {
            path.lineTo(points.first().x, points.first().y)
            return path
        }
        var previous = points.first()
        for (i in 1 until points.size) {
            val current = points[i]
            val midX = (previous.x + current.x) / 2f
            val midY = (previous.y + current.y) / 2f
            if (i == 1) {
                path.lineTo(midX, midY)
            } else {
                path.quadTo(previous.x, previous.y, midX, midY)
            }
            previous = current
        }
        path.lineTo(previous.x, previous.y)
        return path
    }

    fun drawAction(
        canvas: Canvas,
        action: EditAction,
        mosaicBitmap: Bitmap?,
        overrideTextAlpha: Int? = null,
    ) {
        when (action) {
            is EditAction.Doodle -> drawDoodle(canvas, action)
            is EditAction.Shape -> drawShape(canvas, action)
            is EditAction.Text -> drawText(canvas, action, overrideTextAlpha)
            is EditAction.Mosaic -> drawMosaic(canvas, action, mosaicBitmap)
            is EditAction.NumberBadge -> drawNumberBadge(canvas, action)
        }
    }

    fun buildNumberBadgeBounds(action: EditAction.NumberBadge, padding: Float = 8f): RectF {
        val radius = badgeRadius(action.badgeSize)
        return RectF(
            action.anchor.x - radius - padding,
            action.anchor.y - radius - padding,
            action.anchor.x + radius + padding,
            action.anchor.y + radius + padding,
        )
    }

    fun buildNumberBadgeResizeHandleCenter(action: EditAction.NumberBadge, padding: Float = 8f): EditorPoint {
        val bounds = buildNumberBadgeBounds(action, padding)
        return EditorPoint(bounds.right, bounds.bottom)
    }

    fun buildNumberBadgeDeleteHandleCenter(action: EditAction.NumberBadge, padding: Float = 8f): EditorPoint {
        val bounds = buildNumberBadgeBounds(action, padding)
        return EditorPoint(bounds.left, bounds.top)
    }

    private fun badgeRadius(badgeSize: Float): Float = badgeSize * 0.52f

    /** 序号本体命中半径（不含选区 padding），用于空白处落点。 */
    fun numberBadgeBodyHitRadius(badgeSize: Float): Float = badgeRadius(badgeSize) * 1.08f

    private fun drawNumberBadge(canvas: Canvas, action: EditAction.NumberBadge) {
        val radius = badgeRadius(action.badgeSize)
        val label = action.number.toString()
        when (action.style) {
            NumberBadgeStyle.FILLED_CIRCLE -> {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = action.color
                }
                canvas.drawCircle(action.anchor.x, action.anchor.y, radius, fill)
                drawNumberLabel(canvas, label, action.anchor, action.badgeSize, contrastingTextColor(action.color))
            }
            NumberBadgeStyle.OUTLINE_CIRCLE -> {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(3f, action.badgeSize * 0.08f)
                    color = action.color
                }
                canvas.drawCircle(action.anchor.x, action.anchor.y, radius, stroke)
                drawNumberLabel(canvas, label, action.anchor, action.badgeSize, action.color)
            }
            NumberBadgeStyle.FILLED_SQUARE -> {
                val half = radius * 0.92f
                val rect = RectF(
                    action.anchor.x - half,
                    action.anchor.y - half,
                    action.anchor.x + half,
                    action.anchor.y + half,
                )
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = action.color
                }
                val corner = half * 0.28f
                canvas.drawRoundRect(rect, corner, corner, fill)
                drawNumberLabel(canvas, label, action.anchor, action.badgeSize, contrastingTextColor(action.color))
            }
        }
    }

    private fun drawNumberLabel(
        canvas: Canvas,
        label: String,
        anchor: EditorPoint,
        badgeSize: Float,
        color: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = badgeSize * 0.52f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val metrics = paint.fontMetrics
        val baseline = anchor.y - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, anchor.x, baseline, paint)
    }

    private fun contrastingTextColor(background: Int): Int {
        val r = Color.red(background)
        val g = Color.green(background)
        val b = Color.blue(background)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.62) Color.BLACK else Color.WHITE
    }

    fun buildTextBounds(action: EditAction.Text, padding: Float = 12f): RectF {
        val paint = createTextPaint(action)
        val lines = action.text.split('\n')
        val maxWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val metrics = paint.fontMetrics
        val halfWidth = maxWidth / 2f
        val halfHeight = (max(1, lines.size) * (metrics.descent - metrics.ascent)) / 2f
        return RectF(
            action.anchor.x - halfWidth - padding,
            action.anchor.y - halfHeight - padding,
            action.anchor.x + halfWidth + padding,
            action.anchor.y + halfHeight + padding,
        )
    }

    fun buildTextResizeHandleCenter(action: EditAction.Text, padding: Float = 12f): EditorPoint {
        val bounds = buildTextBounds(action, padding)
        return EditorPoint(bounds.right, bounds.bottom)
    }

    fun buildTextDeleteHandleCenter(action: EditAction.Text, padding: Float = 12f): EditorPoint {
        val bounds = buildTextBounds(action, padding)
        return EditorPoint(bounds.left, bounds.top)
    }

    fun buildShapeBounds(action: EditAction.Shape, padding: Float = 12f): RectF {
        val content = buildShapeContentBounds(action)
        val pad = max(padding, action.strokeWidth * 0.75f)
        return RectF(content.left - pad, content.top - pad, content.right + pad, content.bottom + pad)
    }

    fun buildShapeContentBounds(action: EditAction.Shape): RectF {
        return RectF(
            min(action.start.x, action.end.x),
            min(action.start.y, action.end.y),
            max(action.start.x, action.end.x),
            max(action.start.y, action.end.y),
        )
    }

    fun buildShapeResizeHandleCenter(action: EditAction.Shape, padding: Float = 12f): EditorPoint {
        val bounds = buildShapeBounds(action, padding)
        return EditorPoint(bounds.right, bounds.bottom)
    }

    fun buildShapeDeleteHandleCenter(action: EditAction.Shape, padding: Float = 12f): EditorPoint {
        val bounds = buildShapeBounds(action, padding)
        return EditorPoint(bounds.left, bounds.top)
    }

    fun generateMosaicBitmap(source: Bitmap): Bitmap {
        val block = max(1, min(source.width, source.height) / 36)
        val small = Bitmap.createScaledBitmap(
            source,
            max(1, source.width / block),
            max(1, source.height / block),
            false,
        )
        return Bitmap.createScaledBitmap(small, source.width, source.height, false).also {
            if (!small.isRecycled) small.recycle()
        }
    }

    private fun drawDoodle(canvas: Canvas, action: EditAction.Doodle) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = action.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = action.color
            if (action.isEraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }
        canvas.drawPath(createPath(action.points), paint)
    }

    private fun drawShape(canvas: Canvas, action: EditAction.Shape) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = action.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = action.color
        }
        val rect = buildShapeContentBounds(action)
        when (action.type) {
            ShapeType.RECTANGLE -> canvas.drawRect(rect, paint)
            ShapeType.ROUNDED_RECTANGLE -> {
                val radius = min(rect.width(), rect.height()) * 0.18f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            ShapeType.OVAL -> canvas.drawOval(rect, paint)
            ShapeType.LINE -> canvas.drawLine(action.start.x, action.start.y, action.end.x, action.end.y, paint)
            ShapeType.ARROW -> drawArrow(canvas, action.start, action.end, paint)
            ShapeType.DOUBLE_ARROW -> drawDoubleArrow(canvas, action.start, action.end, paint)
            ShapeType.DIAMOND -> drawDiamond(canvas, rect, paint)
            ShapeType.TRIANGLE -> drawTriangle(canvas, rect, paint)
        }
    }

    private fun drawDiamond(canvas: Canvas, rect: RectF, paint: Paint) {
        val path = Path().apply {
            moveTo(rect.centerX(), rect.top)
            lineTo(rect.right, rect.centerY())
            lineTo(rect.centerX(), rect.bottom)
            lineTo(rect.left, rect.centerY())
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawTriangle(canvas: Canvas, rect: RectF, paint: Paint) {
        val path = Path().apply {
            moveTo(rect.centerX(), rect.top)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.left, rect.bottom)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawArrow(canvas: Canvas, start: EditorPoint, end: EditorPoint, paint: Paint) {
        canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = max(sqrt(dx * dx + dy * dy), 1f)
        val ux = dx / length
        val uy = dy / length
        val head = max(28f, paint.strokeWidth * 2.2f)
        val hx = ux * head
        val hy = uy * head
        val wingX = -uy * head * 0.45f
        val wingY = ux * head * 0.45f
        canvas.drawLine(end.x, end.y, end.x - hx + wingX, end.y - hy + wingY, paint)
        canvas.drawLine(end.x, end.y, end.x - hx - wingX, end.y - hy - wingY, paint)
    }

    private fun drawDoubleArrow(canvas: Canvas, start: EditorPoint, end: EditorPoint, paint: Paint) {
        canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = max(sqrt(dx * dx + dy * dy), 1f)
        val ux = dx / length
        val uy = dy / length
        val head = max(28f, paint.strokeWidth * 2.2f)
        val hx = ux * head
        val hy = uy * head
        val wingX = -uy * head * 0.45f
        val wingY = ux * head * 0.45f
        // End arrow head
        canvas.drawLine(end.x, end.y, end.x - hx + wingX, end.y - hy + wingY, paint)
        canvas.drawLine(end.x, end.y, end.x - hx - wingX, end.y - hy - wingY, paint)
        // Start arrow head
        canvas.drawLine(start.x, start.y, start.x + hx + wingX, start.y + hy + wingY, paint)
        canvas.drawLine(start.x, start.y, start.x + hx - wingX, start.y + hy - wingY, paint)
    }

    private fun drawText(canvas: Canvas, action: EditAction.Text, overrideAlpha: Int?) {
        val paint = createTextPaint(action)
        overrideAlpha?.let { paint.alpha = it }
        val lines = action.text.split('\n')
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val baselineAdjust = -((metrics.ascent + metrics.descent) / 2f)
        var y = action.anchor.y - ((max(1, lines.size) - 1) * lineHeight) / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, action.anchor.x, index * lineHeight + y + baselineAdjust, paint)
        }
    }

    private fun drawMosaic(canvas: Canvas, action: EditAction.Mosaic, mosaicBitmap: Bitmap?) {
        if (mosaicBitmap == null) return
        val strokePath = createPath(action.points)
        val fillPath = Path()
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = action.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokePaint.getFillPath(strokePath, fillPath)
        canvas.save()
        canvas.clipPath(fillPath)
        canvas.drawBitmap(mosaicBitmap, 0f, 0f, null)
        canvas.restore()
    }

    private fun createTextPaint(action: EditAction.Text): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = action.color
            textSize = action.textSize
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            setShadowLayer(max(2f, action.textSize * 0.08f), 0f, 0f, Color.argb(96, 0, 0, 0))
        }
    }
}
