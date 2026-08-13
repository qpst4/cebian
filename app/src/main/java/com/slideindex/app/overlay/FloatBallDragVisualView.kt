package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/**
 * FV FloatIconView-style drag shell: native [ImageView] only, no Compose during drag.
 */
internal class FloatBallDragVisualView(context: Context) : FrameLayout(context) {

    private val ballImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var ownedBitmap: Bitmap? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
        addView(ballImage, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun show(settings: AppSettings, composeSnapshot: Bitmap?, activeSide: FloatBallSide) {
        releaseOwnedBitmap()
        val density = resources.displayMetrics.density
        val sizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt().coerceAtLeast(1)
        val bitmap = composeSnapshot ?: FloatBallDragVisualRenderer.render(context, settings)
        ownedBitmap = if (composeSnapshot == null) bitmap else null
        ballImage.setImageBitmap(bitmap)
        ballImage.clipToOutline = true
        ballImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        val params = ballImage.layoutParams as LayoutParams
        params.width = sizePx
        params.height = sizePx
        params.gravity = layoutGravity(settings, activeSide)
        ballImage.layoutParams = params
        visibility = VISIBLE
    }

    fun release() {
        releaseOwnedBitmap()
        ballImage.setImageDrawable(null)
        visibility = GONE
    }

    private fun releaseOwnedBitmap() {
        ownedBitmap?.recycle()
        ownedBitmap = null
    }

    private fun layoutGravity(settings: AppSettings, activeSide: FloatBallSide): Int {
        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            return Gravity.CENTER
        }
        return when (activeSide) {
            FloatBallSide.LEFT -> Gravity.CENTER_VERTICAL or Gravity.START
            FloatBallSide.RIGHT -> Gravity.CENTER_VERTICAL or Gravity.END
        }
    }
}
