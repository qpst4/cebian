package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * FV p1.o1-style cross/plus marker: native [onDraw] at window center.
 * Position follows finger via WM layout only — no Compose recomposition on MOVE.
 */
internal class FloatBallCursorCrossView(context: Context) : View(context) {

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val plusLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
    }

    private var paused = false

    private val density: Float
        get() = resources.displayMetrics.density

    private val armPx: Float
        get() = CROSS_ARM_DP * density

    private val strokePx: Float
        get() = CROSS_STROKE_DP * density

    private val plusRadiusPx: Float
        get() = 11f * density

    fun setMarkerPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        if (paused) {
            fillPaint.color = COLOR_PAUSED
            canvas.drawCircle(centerX, centerY, plusRadiusPx, fillPaint)
            plusLinePaint.strokeWidth = strokePx * 1.1f
            val plusArm = plusRadiusPx * 0.42f
            canvas.drawLine(centerX - plusArm, centerY, centerX + plusArm, centerY, plusLinePaint)
            canvas.drawLine(centerX, centerY - plusArm, centerX, centerY + plusArm, plusLinePaint)
        } else {
            crossPaint.color = COLOR_ACTIVE
            crossPaint.strokeWidth = strokePx
            canvas.drawLine(centerX - armPx, centerY, centerX + armPx, centerY, crossPaint)
            canvas.drawLine(centerX, centerY - armPx, centerX, centerY + armPx, crossPaint)
            canvas.drawCircle(centerX, centerY, strokePx * 1.2f, crossPaint)
        }
    }

    companion object {
        private const val CROSS_ARM_DP = 14f
        private const val CROSS_STROKE_DP = 2.5f
        private const val COLOR_ACTIVE = 0xFFE53935.toInt()
        private const val COLOR_PAUSED = 0xFFFFC107.toInt()
    }
}
