package com.slideindex.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.Path
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
    private val cache = object : LruCache<String, Bitmap>(48) {}

    fun preload(
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int = android.graphics.Color.WHITE,
    ) {
        get(action, sizePx, tintArgb)
    }

    fun get(
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int = android.graphics.Color.WHITE,
    ): Bitmap {
        val safeSize = sizePx.coerceAtLeast(1)
        val cacheKey = cacheKey(action, safeSize, tintArgb)
        cache.get(cacheKey)?.let { return it }
        val bitmap = render(gestureActionImageVector(action), safeSize, tintArgb)
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    fun clear() {
        cache.evictAll()
    }

    private fun cacheKey(action: GestureAction, sizePx: Int, tintArgb: Int): String =
        "${action.type.id}:${action.payload}:$sizePx:$tintArgb"

    private fun render(imageVector: ImageVector, sizePx: Int, tintArgb: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintArgb
            style = Paint.Style.FILL
        }
        val scale = minOf(
            sizePx / imageVector.viewportWidth,
            sizePx / imageVector.viewportHeight,
        ) * 0.72f
        val dx = (sizePx - imageVector.viewportWidth * scale) / 2f
        val dy = (sizePx - imageVector.viewportHeight * scale) / 2f
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        drawGroup(canvas, paint, imageVector.root)
        return bitmap
    }

    private fun drawGroup(canvas: Canvas, paint: Paint, group: VectorGroup) {
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

    private fun drawNode(canvas: Canvas, paint: Paint, node: VectorNode) {
        when (node) {
            is VectorPath -> drawVectorPath(canvas, paint, node)
            is VectorGroup -> drawGroup(canvas, paint, node)
        }
    }

    private fun drawVectorPath(canvas: Canvas, paint: Paint, vectorPath: VectorPath) {
        val composePath = vectorPath.pathData.toPath()
        val pathPaint = Paint(paint).apply {
            alpha = (vectorPath.fillAlpha * paint.alpha).toInt().coerceIn(0, 255)
        }
        canvas.drawPath(composePath.asAndroidPath(), pathPaint)
    }
}
