package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.slideindex.app.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FV pointer_op_hint-style badge: yellow circle + mode icon beside the cross marker.
 */
internal class FloatBallCursorHintView(context: Context) : View(context) {

    enum class Mode {
        HIDDEN,
        TEXT,
        SCREENSHOT,
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_BADGE
    }

    private val textIcon: Drawable? =
        ResourcesCompat.getDrawable(context.resources, R.drawable.float_ball_hint_text, null)
    private val screenshotIcon: Drawable? =
        ResourcesCompat.getDrawable(context.resources, R.drawable.float_ball_hint_screenshot, null)

    private var mode = Mode.HIDDEN

    fun setHintMode(value: Mode) {
        if (mode == value) return
        mode = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (mode == Mode.HIDDEN) return
        val icon = when (mode) {
            Mode.TEXT -> textIcon
            Mode.SCREENSHOT -> screenshotIcon
            Mode.HIDDEN -> return
        } ?: return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        val iconSize = (radius * 1.25f).roundToInt()
        val half = iconSize / 2
        icon.setBounds(
            (centerX - half).roundToInt(),
            (centerY - half).roundToInt(),
            (centerX + half).roundToInt(),
            (centerY + half).roundToInt(),
        )
        icon.draw(canvas)
    }

    companion object {
        private const val COLOR_BADGE = 0xFFFFC107.toInt()
    }
}
