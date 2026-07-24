package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Lightweight GIF surface: draw cached bitmap or Movie directly in onDraw,
 * avoiding per-frame setImageBitmap / bitmap allocation.
 */
internal class FloatBallGifView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val circlePath = Path()
    private val circleRect = RectF()
    private val bitmapDst = Rect()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var cachedBitmap: Bitmap? = null
    private var streamingMovie: Movie? = null
    private var streamingElapsedMs = 0
    private var streamingOutW = 0
    private var streamingOutH = 0
    private var streamingMode = false

    fun showCachedFrame(bitmap: Bitmap) {
        streamingMode = false
        streamingMovie = null
        cachedBitmap = bitmap
        invalidate()
    }

    fun showStreamingFrame(movie: Movie, elapsedMs: Int, outW: Int, outH: Int) {
        streamingMode = true
        cachedBitmap = null
        streamingMovie = movie
        streamingElapsedMs = elapsedMs
        streamingOutW = outW
        streamingOutH = outH
        invalidate()
    }

    fun clearFrame() {
        streamingMode = false
        streamingMovie = null
        cachedBitmap = null
        invalidate()
    }

    /** Current visible frame for drag shell — never mutates internal state. */
    fun snapshotCurrentFrame(): Bitmap? {
        val outW = width.coerceAtLeast(1)
        val outH = height.coerceAtLeast(1)
        if (outW <= 0 || outH <= 0) return null
        return if (!streamingMode) {
            cachedBitmap
                ?.takeIf { !it.isRecycled }
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            val movie = streamingMovie ?: return null
            val durationMs = movie.duration().takeIf { it > 0 } ?: 1_000
            val bitmap = Bitmap.createBitmap(
                streamingOutW.coerceAtLeast(1),
                streamingOutH.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            movie.setTime(streamingElapsedMs % durationMs)
            val scaleX = streamingOutW.toFloat() / movie.width().coerceAtLeast(1)
            val scaleY = streamingOutH.toFloat() / movie.height().coerceAtLeast(1)
            canvas.scale(scaleX, scaleY)
            movie.draw(canvas, 0f, 0f)
            bitmap
        }
    }

    fun setAnimating(animating: Boolean) {
        val layer = if (animating) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE
        if (layerType != layer) {
            setLayerType(layer, null)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circleRect.set(0f, 0f, w.toFloat(), h.toFloat())
        circlePath.reset()
        circlePath.addOval(circleRect, Path.Direction.CW)
        bitmapDst.set(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        canvas.save()
        canvas.clipPath(circlePath)
        if (streamingMode) {
            val movie = streamingMovie
            if (movie == null) {
                canvas.restore()
                return
            }
            val durationMs = movie.duration().takeIf { it > 0 } ?: 1_000
            movie.setTime(streamingElapsedMs % durationMs)
            val scaleX = streamingOutW.toFloat() / movie.width().coerceAtLeast(1)
            val scaleY = streamingOutH.toFloat() / movie.height().coerceAtLeast(1)
            canvas.save()
            canvas.scale(scaleX, scaleY)
            movie.draw(canvas, 0f, 0f)
            canvas.restore()
        } else {
            val bitmap = cachedBitmap
            if (bitmap == null) {
                canvas.restore()
                return
            }
            canvas.drawBitmap(bitmap, null, bitmapDst, bitmapPaint)
        }
        canvas.restore()
    }
}
