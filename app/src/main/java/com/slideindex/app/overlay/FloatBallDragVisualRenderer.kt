package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
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
            if (view is FloatBallGifView && view.width > 0 && view.height > 0) {
                return view.snapshotCurrentFrame()
            }
            if (view is FloatBallBuiltinAnimView && view.width > 0 && view.height > 0) {
                return view.snapshotCurrentFrame()
            }
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
            FloatBallStyleType.ANIMATED_PLANE,
            FloatBallStyleType.ANIMATED_PULSE,
            FloatBallStyleType.ANIMATED_ORBIT,
            -> drawBuiltinAnim(canvas, context, sizePx, alpha, settings.floatBallStyleType)
            FloatBallStyleType.CUSTOM_IMAGE -> drawUri(
                canvas, context, sizePx, alpha, settings.floatBallCustomImageUri,
                fallbackArgb = settings.themeColorArgb,
            )
            FloatBallStyleType.SLIDESHOW -> {
                val uri = settings.floatBallSlideshowUris.firstOrNull().orEmpty()
                drawUri(canvas, context, sizePx, alpha, uri, fallbackArgb = settings.themeColorArgb)
            }
            FloatBallStyleType.GIF -> {
                val dragFrame = FloatBallGifDragSnapshot.copyForDrag(
                    uri = settings.floatBallGifUri,
                    targetPx = sizePx,
                )
                if (dragFrame != null) {
                    drawBitmap(canvas, sizePx, alpha, dragFrame)
                    dragFrame.recycle()
                } else {
                    drawUri(
                        canvas, context, sizePx, alpha, settings.floatBallGifUri,
                        fallbackArgb = settings.themeColorArgb,
                    )
                }
            }
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

    private fun drawBuiltinAnim(
        canvas: Canvas,
        context: Context,
        sizePx: Int,
        alpha: Float,
        styleType: FloatBallStyleType,
    ) {
        val resId = FloatBallBuiltinAnimCatalog.animatedDrawableRes(styleType) ?: run {
            drawDefault(canvas, sizePx, 0xFF42A5F5.toInt(), alpha)
            return
        }
        val drawable = ContextCompat.getDrawable(context, resId)
            ?: run {
                drawDefault(canvas, sizePx, 0xFF42A5F5.toInt(), alpha)
                return
            }
        val save = canvas.save()
        canvas.clipPath(
            android.graphics.Path().apply {
                addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, android.graphics.Path.Direction.CW)
            },
        )
        drawable.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawDefault(canvas: Canvas, sizePx: Int, colorArgb: Int, alpha: Float) {
        FloatBallDefaultVisual.draw(canvas, sizePx, colorArgb, alpha)
    }

    private fun drawBitmap(canvas: Canvas, sizePx: Int, alpha: Float, bitmap: Bitmap) {
        val dst = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            this.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(bitmap, null, dst, paint)
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
            drawBitmap(canvas, sizePx, alpha, loaded)
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
