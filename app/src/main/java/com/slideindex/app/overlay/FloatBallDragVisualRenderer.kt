package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallStyleType
import kotlin.math.roundToInt

/**
 * Renders float-ball appearance to a bitmap without Compose — used during drag.
 */
internal object FloatBallDragVisualRenderer {

    fun captureFromComposeTree(composeRoot: View?): Bitmap? {
        if (composeRoot == null) return null
        val queue = ArrayDeque<View>()
        queue.add(composeRoot)
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is ImageView && view.drawable != null && view.width > 0 && view.height > 0) {
                return snapshotDrawable(view.drawable, view.width, view.height)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    queue.add(view.getChildAt(i))
                }
            }
        }
        return null
    }

    fun render(context: Context, settings: AppSettings): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val alpha = settings.floatBallOpacity.coerceIn(0.3f, 1f)
        when (settings.floatBallStyleType) {
            FloatBallStyleType.DEFAULT -> drawDefault(canvas, sizePx, settings.themeColorArgb, alpha)
            FloatBallStyleType.PRESET_1 -> drawPreset(
                canvas, sizePx, alpha,
                outer = 0xFF42A5F5.toInt(),
                inner = 0xFF1565C0.toInt(),
            )
            FloatBallStyleType.PRESET_2 -> drawPreset(
                canvas, sizePx, alpha,
                outer = 0xFF66BB6A.toInt(),
                inner = 0xFF2E7D32.toInt(),
                square = true,
            )
            FloatBallStyleType.PRESET_3 -> drawPreset(
                canvas, sizePx, alpha,
                outer = 0xFFFFA726.toInt(),
                inner = 0xFFE65100.toInt(),
                ring = true,
            )
            FloatBallStyleType.PRESET_4 -> drawPreset(
                canvas, sizePx, alpha,
                outer = 0xFFAB47BC.toInt(),
                inner = 0xFF6A1B9A.toInt(),
                ring = true,
                square = true,
            )
            FloatBallStyleType.CUSTOM_IMAGE -> drawUri(
                canvas, context, sizePx, alpha, settings.floatBallCustomImageUri,
                fallbackArgb = settings.themeColorArgb,
            )
            FloatBallStyleType.SLIDESHOW -> {
                val uri = settings.floatBallSlideshowUris.firstOrNull().orEmpty()
                drawUri(canvas, context, sizePx, alpha, uri, fallbackArgb = settings.themeColorArgb)
            }
            FloatBallStyleType.GIF -> drawUri(
                canvas, context, sizePx, alpha, settings.floatBallGifUri,
                fallbackArgb = settings.themeColorArgb,
            )
        }
        return bitmap
    }

    private fun snapshotDrawable(drawable: Drawable, width: Int, height: Int): Bitmap? {
        if (drawable is BitmapDrawable) {
            val source = drawable.bitmap ?: return null
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        return runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        }.getOrNull()
    }

    private fun drawDefault(canvas: Canvas, sizePx: Int, colorArgb: Int, alpha: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = sizePx / 2f
        val radius = sizePx / 2f
        paint.color = applyAlpha(colorArgb, alpha)
        canvas.drawCircle(cx, cx, radius, paint)
        paint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha * 0.9f)
        canvas.drawCircle(cx, cx, radius * 0.35f, paint)
    }

    private fun drawPreset(
        canvas: Canvas,
        sizePx: Int,
        alpha: Float,
        outer: Int,
        inner: Int,
        square: Boolean = false,
        ring: Boolean = false,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        val cx = sizePx / 2f
        paint.shader = RadialGradient(
            cx, cx, sizePx / 2f,
            intArrayOf(applyAlpha(inner, alpha), applyAlpha(outer, alpha)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        if (square) {
            val inset = sizePx * 0.08f
            val path = Path()
            val r = sizePx * 0.18f
            path.addRoundRect(
                RectF(inset, inset, sizePx - inset, sizePx - inset),
                r, r,
                Path.Direction.CW,
            )
            canvas.drawPath(path, paint)
        } else {
            canvas.drawCircle(cx, cx, sizePx / 2f, paint)
        }
        paint.shader = null
        if (ring) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sizePx * 0.06f
            paint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha * 0.85f)
            canvas.drawCircle(cx, cx, sizePx * 0.32f, paint)
            paint.style = Paint.Style.FILL
        } else if (!square) {
            paint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha * 0.9f)
            canvas.drawCircle(cx, cx, sizePx * 0.35f / 2f, paint)
        }
    }

    private fun drawUri(
        canvas: Canvas,
        context: Context,
        sizePx: Int,
        alpha: Float,
        uri: String,
        fallbackArgb: Int,
    ) {
        val loaded = FloatBallImageLoader.loadBitmap(context, uri)
        if (loaded != null) {
            val dst = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                this.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(loaded, null, dst, paint)
            if (!loaded.isRecycled) {
                loaded.recycle()
            }
            return
        }
        drawDefault(canvas, sizePx, fallbackArgb, alpha)
    }

    private fun applyAlpha(colorArgb: Int, alpha: Float): Int {
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        return (colorArgb and 0x00FFFFFF) or (a shl 24)
    }
}
