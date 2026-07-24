package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/**
 * Circular clipped AVD surface for built-in float-ball animations.
 */
internal class FloatBallBuiltinAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val circlePath = Path()
    private val circleRect = RectF()
    private var animDrawable: Drawable? = null

    fun setAnimation(@DrawableRes resId: Int) {
        releaseAnimation()
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return
        animDrawable = drawable
        updateDrawableBounds()
        startAnimation()
        invalidate()
    }

    fun setPaused(paused: Boolean) {
        val animatable = animDrawable as? Animatable ?: return
        if (paused) {
            animatable.stop()
            setAnimating(false)
        } else {
            startAnimation()
        }
    }

    fun releaseAnimation() {
        (animDrawable as? Animatable)?.stop()
        animDrawable = null
        setAnimating(false)
    }

    fun snapshotCurrentFrame(): Bitmap? {
        val drawable = animDrawable ?: return null
        val outW = width.coerceAtLeast(1)
        val outH = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.clipPath(circlePath)
        drawable.setBounds(0, 0, outW, outH)
        drawable.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun startAnimation() {
        val animatable = animDrawable as? Animatable ?: return
        animatable.start()
        setAnimating(true)
    }

    private fun setAnimating(animating: Boolean) {
        val layer = if (animating) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE
        if (layerType != layer) {
            setLayerType(layer, null)
        }
    }

    private fun updateDrawableBounds() {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        animDrawable?.setBounds(0, 0, w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circleRect.set(0f, 0f, w.toFloat(), h.toFloat())
        circlePath.reset()
        circlePath.addOval(circleRect, Path.Direction.CW)
        updateDrawableBounds()
    }

    override fun onDraw(canvas: Canvas) {
        val drawable = animDrawable ?: return
        if (width <= 0 || height <= 0) return
        canvas.save()
        canvas.clipPath(circlePath)
        drawable.draw(canvas)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        releaseAnimation()
        super.onDetachedFromWindow()
    }
}
