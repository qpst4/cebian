package com.slideindex.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.ui.gestureActionImageVector

object GestureActionIconBitmap {
    private const val CACHE_VERSION = 31

    /** 保持与 Filled 面板内边距一致的比例 */
    private const val CONTENT_SCALE = 0.75f

    private val cache = object : LruCache<String, Bitmap>(48) {}

    fun preload(
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int = android.graphics.Color.WHITE,
        outlined: Boolean = false,
    ) {
        get(action, sizePx, tintArgb, outlined)
    }

    fun get(
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int = android.graphics.Color.WHITE,
        outlined: Boolean = false,
    ): Bitmap {
        val safeSize = sizePx.coerceAtLeast(1)
        val cacheKey = cacheKey(action, safeSize, tintArgb, outlined)
        cache.get(cacheKey)?.let { return it }
        val imageVector = gestureActionImageVector(action, outlined)
        val bitmap = render(imageVector, safeSize, tintArgb)
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    fun clear() {
        cache.evictAll()
    }

    private fun cacheKey(action: GestureAction, sizePx: Int, tintArgb: Int, outlined: Boolean): String =
        "$CACHE_VERSION:${action.type.id}:${action.payload}:$sizePx:$tintArgb:$outlined"

    private fun render(
        imageVector: ImageVector,
        sizePx: Int,
        tintArgb: Int,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintArgb
        }
        val scale = minOf(
            sizePx / imageVector.viewportWidth,
            sizePx / imageVector.viewportHeight,
        ) * CONTENT_SCALE
        val dx = (sizePx - imageVector.viewportWidth * scale) / 2f
        val dy = (sizePx - imageVector.viewportHeight * scale) / 2f
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        drawGroup(canvas, paint, imageVector.root)
        return bitmap
    }

    private fun drawGroup(
        canvas: Canvas,
        paint: Paint,
        group: VectorGroup,
    ) {
        canvas.withTranslation(group.translationX, group.translationY) {
            if (group.pivotX != 0f || group.pivotY != 0f) {
                scale(group.scaleX, group.scaleY, group.pivotX, group.pivotY)
                rotate(group.rotation, group.pivotX, group.pivotY)
            } else {
                scale(group.scaleX, group.scaleY)
                rotate(group.rotation)
            }
            group.forEach { node -> drawNode(this, paint, node) }
        }
    }

    private fun drawNode(
        canvas: Canvas,
        paint: Paint,
        node: VectorNode,
    ) {
        when (node) {
            is VectorPath -> drawVectorPath(canvas, paint, node)
            is VectorGroup -> drawGroup(canvas, paint, node)
        }
    }

    private fun drawVectorPath(
        canvas: Canvas,
        paint: Paint,
        vectorPath: VectorPath,
    ) {
        val composePath = Path().apply {
            addPath(vectorPath.pathData.toPath())
            fillType = vectorPath.pathFillType
        }
        val androidPath = composePath.asAndroidPath()
        val baseAlpha = paint.alpha

        if (vectorPath.hasVisibleFill()) {
            val fillPaint = Paint(paint).apply {
                style = Paint.Style.FILL
                alpha = (vectorPath.fillAlpha * baseAlpha).toInt().coerceIn(0, 255)
            }
            canvas.drawPath(androidPath, fillPaint)
        }

        val strokeWidth = vectorPath.strokeLineWidth
        if (strokeWidth <= 0f || vectorPath.strokeAlpha <= 0f) return
        if (vectorPath.hasVisibleFill() && vectorPath.pathFillType == PathFillType.EvenOdd) return

        val strokePaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            alpha = (vectorPath.strokeAlpha * baseAlpha).toInt().coerceIn(0, 255)
            strokeCap = vectorPath.strokeLineCap.toAndroidCap()
            strokeJoin = vectorPath.strokeLineJoin.toAndroidJoin()
            strokeMiter = vectorPath.strokeLineMiter
        }
        canvas.drawPath(androidPath, strokePaint)
    }

    private fun VectorPath.hasVisibleFill(): Boolean {
        if (fillAlpha <= 0f) return false
        return when (val brush = fill) {
            null -> false
            is SolidColor -> brush.value.alpha > 0.01f
            else -> true
        }
    }

    private fun StrokeCap.toAndroidCap(): Paint.Cap = when (this) {
        StrokeCap.Round -> Paint.Cap.ROUND
        StrokeCap.Square -> Paint.Cap.SQUARE
        else -> Paint.Cap.BUTT
    }

    private fun StrokeJoin.toAndroidJoin(): Paint.Join = when (this) {
        StrokeJoin.Round -> Paint.Join.ROUND
        StrokeJoin.Bevel -> Paint.Join.BEVEL
        else -> Paint.Join.MITER
    }
}
