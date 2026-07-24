package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.slideindex.app.settings.FloatBallStyleType

/**
 * Circular clipped canvas animation surface (~20fps Handler tick, no AVD/Lottie).
 */
internal class FloatBallBuiltinAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val circlePath = Path()
    private val circleRect = RectF()
    private var styleType: FloatBallStyleType? = null
    private var paused = false
    private var startUptimeMs = SystemClock.uptimeMillis()

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (paused || styleType == null) return
            invalidate()
            mainHandler.postDelayed(this, TICK_MS)
        }
    }

    fun setStyle(type: FloatBallStyleType) {
        if (styleType == type) return
        styleType = type
        startUptimeMs = SystemClock.uptimeMillis()
        invalidate()
        if (!paused) {
            startTicking()
        }
    }

    fun setPaused(pause: Boolean) {
        if (paused == pause) return
        paused = pause
        if (pause) {
            stopTicking()
        } else {
            startUptimeMs = SystemClock.uptimeMillis()
            startTicking()
            invalidate()
        }
    }

    fun releaseAnimation() {
        stopTicking()
        styleType = null
        invalidate()
    }

    fun snapshotCurrentFrame(): Bitmap? {
        val type = styleType ?: return null
        val sizePx = width.coerceAtLeast(1)
        return FloatBallBuiltinAnimRenderer.snapshot(
            sizePx = sizePx,
            alpha = viewOpacity(),
            styleType = type,
            timeMs = elapsedMs(),
        )
    }

    private fun elapsedMs(): Long = SystemClock.uptimeMillis() - startUptimeMs

    private fun startTicking() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    private fun stopTicking() {
        mainHandler.removeCallbacks(tickRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circleRect.set(0f, 0f, w.toFloat(), h.toFloat())
        circlePath.reset()
        circlePath.addOval(circleRect, Path.Direction.CW)
        if (w > 0 && h > 0) {
            invalidate()
        }
    }

    private fun viewOpacity(): Float = alpha.coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val type = styleType ?: return
        if (width <= 0 || height <= 0) return
        canvas.save()
        canvas.clipPath(circlePath)
        FloatBallBuiltinAnimRenderer.draw(
            canvas = canvas,
            sizePx = width.coerceAtMost(height).coerceAtLeast(1),
            alpha = viewOpacity(),
            styleType = type,
            timeMs = elapsedMs(),
        )
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        releaseAnimation()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TICK_MS = 50L
    }
}
