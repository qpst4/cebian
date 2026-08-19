package com.slideindex.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.ui.gestureActionImageVector

enum class GestureActionPlateShape {
    ROUNDED_RECT,
    CIRCLE,
}

object GestureActionIconBitmap {
    private const val CACHE_VERSION = 34

    /** 保持与 Filled 面板内边距一致的比例（无底板时） */
    private const val CONTENT_SCALE_BARE = 0.75f
    /** 置于自适应底板内的图标比例（留出舒适内边距，如同标准 App 图标） */
    private const val CONTENT_SCALE_PLATE = 0.54f
    /** 圆形满铺底板（边角轮盘等圆形槽位） */
    private const val CONTENT_SCALE_CIRCLE_PLATE = 0.66f

    private val cache = object : LruCache<String, Bitmap>(96) {}

    fun preload(
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int = android.graphics.Color.WHITE,
        outlined: Boolean = false,
        withPlate: Boolean = false,
        plateShape: GestureActionPlateShape = GestureActionPlateShape.ROUNDED_RECT,
    ) {
        get(action, sizePx, tintArgb, outlined, withPlate, plateShape)
    }

    fun get(
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int = android.graphics.Color.WHITE,
        outlined: Boolean = false,
        withPlate: Boolean = false,
        plateShape: GestureActionPlateShape = GestureActionPlateShape.ROUNDED_RECT,
    ): Bitmap {
        val safeSize = sizePx.coerceAtLeast(1)
        val cacheKey = cacheKey(action, safeSize, tintArgb, outlined, withPlate, plateShape)
        cache.get(cacheKey)?.let { return it }
        val imageVector = gestureActionImageVector(action, outlined)
        val bitmap = render(imageVector, safeSize, tintArgb, withPlate, plateShape, action)
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    fun clear() {
        cache.evictAll()
    }

    private fun cacheKey(
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int,
        outlined: Boolean,
        withPlate: Boolean,
        plateShape: GestureActionPlateShape,
    ): String =
        "$CACHE_VERSION:${action.type.id}:${action.payload}:$sizePx:$tintArgb:$outlined:$withPlate:$plateShape"

    private fun plateColorsForAction(action: GestureAction): Pair<Int, Int> {
        return when (action.type) {
            GestureActionType.BACK,
            GestureActionType.HOME,
            GestureActionType.RECENTS,
            GestureActionType.PREVIOUS_APP,
            GestureActionType.LOCK_SCREEN,
            GestureActionType.LOCK_SCREEN_AND_SILENCE_RING,
            GestureActionType.LOCK_SCREEN_AND_MUTE_ALL,
            GestureActionType.OPEN_NOTIFICATIONS,
            GestureActionType.OPEN_QUICK_SETTINGS,
            GestureActionType.POWER_MENU,
            GestureActionType.TASK_SWITCHER -> {
                // 深蓝钢灰质感
                0xFF2D3748.toInt() to 0xFF1A202C.toInt()
            }
            GestureActionType.SCREENSHOT,
            GestureActionType.REGIONAL_SCREENSHOT_PICK,
            GestureActionType.FULLSCREEN_SCREENSHOT_PICK,
            GestureActionType.SCREEN_RECORD,
            GestureActionType.FLASHLIGHT -> {
                // 蓝紫微光质感
                0xFF3B336A.toInt() to 0xFF231E44.toInt()
            }
            GestureActionType.EXECUTE_SHELL_COMMAND,
            GestureActionType.SHELL_COMMAND_PANEL -> {
                // 极客暗黑终端质感
                0xFF24292E.toInt() to 0xFF161A1D.toInt()
            }
            GestureActionType.OPEN_STASH_PANEL,
            GestureActionType.OPEN_CLIPBOARD_PANEL,
            GestureActionType.OPEN_CLIPBOARD_FLOAT,
            GestureActionType.CLIPBOARD_PICK,
            GestureActionType.QUICK_LAUNCHER,
            GestureActionType.HONEYCOMB_LAUNCHER,
            GestureActionType.APP_SWITCHER,
            GestureActionType.SEARCH_PANEL,
            GestureActionType.QUICK_TOOLS_OVERLAY -> {
                // 沉稳冷青灰
                0xFF23353A.toInt() to 0xFF142226.toInt()
            }
            else -> {
                // 默认优雅深钛灰
                0xFF2F323A.toInt() to 0xFF1E2026.toInt()
            }
        }
    }

    private fun render(
        imageVector: ImageVector,
        sizePx: Int,
        tintArgb: Int,
        withPlate: Boolean,
        plateShape: GestureActionPlateShape,
        action: GestureAction,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        if (withPlate) {
            val plateRect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            val cornerRadius = sizePx * 0.22f
            val center = sizePx / 2f
            val plateRadius = center

            val (topColor, bottomColor) = plateColorsForAction(action)
            val shader = LinearGradient(
                0f, 0f, 0f, sizePx.toFloat(),
                topColor, bottomColor,
                Shader.TileMode.CLAMP,
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader
            }
            when (plateShape) {
                GestureActionPlateShape.ROUNDED_RECT ->
                    canvas.drawRoundRect(plateRect, cornerRadius, cornerRadius, bgPaint)
                GestureActionPlateShape.CIRCLE ->
                    canvas.drawCircle(center, center, plateRadius, bgPaint)
            }

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (sizePx / 64f).coerceIn(1f, 2.5f)
                color = 0x33FFFFFF.toInt()
            }
            val inset = strokePaint.strokeWidth / 2f
            when (plateShape) {
                GestureActionPlateShape.ROUNDED_RECT -> {
                    plateRect.inset(inset, inset)
                    canvas.drawRoundRect(plateRect, cornerRadius - inset, cornerRadius - inset, strokePaint)
                }
                GestureActionPlateShape.CIRCLE ->
                    canvas.drawCircle(center, center, plateRadius - inset, strokePaint)
            }
        }

        val contentScale = when {
            !withPlate -> CONTENT_SCALE_BARE
            plateShape == GestureActionPlateShape.CIRCLE -> CONTENT_SCALE_CIRCLE_PLATE
            else -> CONTENT_SCALE_PLATE
        }
        val effectiveTint = if (withPlate) android.graphics.Color.WHITE else tintArgb
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveTint
        }
        val scale = minOf(
            sizePx / imageVector.viewportWidth,
            sizePx / imageVector.viewportHeight,
        ) * contentScale
        val dx = (sizePx - imageVector.viewportWidth * scale) / 2f
        val dy = (sizePx - imageVector.viewportHeight * scale) / 2f
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        drawGroup(canvas, paint, imageVector.root)
        canvas.restore()
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
