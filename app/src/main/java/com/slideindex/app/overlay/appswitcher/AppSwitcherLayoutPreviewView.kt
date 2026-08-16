package com.slideindex.app.overlay.appswitcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.slideindex.app.overlay.layout.AppSwitcherLayoutEngine
import com.slideindex.app.overlay.layout.AppSwitcherSide
import com.slideindex.app.settings.AppSwitcherDisplaySettings

@SuppressLint("ViewConstructor")
internal class AppSwitcherLayoutPreviewView(context: Context) : View(context) {
    private var display = AppSwitcherDisplaySettings()
    private var density = 1f
    private val previewItemCount = 6

    private val leftFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x330D9488 }
    private val leftStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xCC2DD4BF.toInt()
        strokeWidth = 2f
    }
    private val rightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x330D9488 }
    private val rightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xCC14B8A6.toInt()
        strokeWidth = 2f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
    }

    fun update(display: AppSwitcherDisplaySettings, density: Float) {
        this.display = display
        this.density = density
        leftStrokePaint.strokeWidth = 1.5f * density
        rightStrokePaint.strokeWidth = 1.5f * density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val anchorY = screenH * 0.5f
        val radiusRatio = display.initialRadiusRatioPercent / 100f
        val spacingRatio = 1f + display.spacingDp / display.iconSizeDp.coerceAtLeast(1).toFloat()

        drawSide(
            canvas = canvas,
            side = AppSwitcherSide.LEFT,
            screenW = screenW,
            screenH = screenH,
            anchorY = anchorY,
            radiusRatio = radiusRatio,
            spacingRatio = spacingRatio,
            fillPaint = leftFillPaint,
            strokePaint = leftStrokePaint,
        )
        drawSide(
            canvas = canvas,
            side = AppSwitcherSide.RIGHT,
            screenW = screenW,
            screenH = screenH,
            anchorY = anchorY,
            radiusRatio = radiusRatio,
            spacingRatio = spacingRatio,
            fillPaint = rightFillPaint,
            strokePaint = rightStrokePaint,
        )
    }

    private fun drawSide(
        canvas: Canvas,
        side: AppSwitcherSide,
        screenW: Float,
        screenH: Float,
        anchorY: Float,
        radiusRatio: Float,
        spacingRatio: Float,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val layout = AppSwitcherLayoutEngine.layout(
            itemCount = previewItemCount,
            side = side,
            anchorRawY = anchorY,
            screenWidth = screenW,
            screenHeight = screenH,
            itemSizeDp = display.iconSizeDp.toFloat(),
            spacingDp = display.spacingDp.toFloat(),
            density = density,
            initialRadiusRatio = radiusRatio,
            itemSpacingRatio = spacingRatio,
        )
        val radius = layout.itemSizePx * 0.5f
        for (slot in layout.slots) {
            canvas.drawCircle(slot.centerX, slot.centerY, radius, fillPaint)
            canvas.drawCircle(slot.centerX, slot.centerY, radius, strokePaint)
        }
    }
}
