package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.withSave
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.AppSettings

internal object TriggerZonePreviewRenderer {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    fun draw(
        canvas: Canvas,
        side: PanelSide,
        settings: AppSettings,
        zoneLayout: GestureZoneLayout,
        density: Float,
        dp: (Float) -> Float,
    ) {
        val corner = dp(6f)
        if (settings.interceptSystemBackGesture) {
            val intercept = zoneLayout.interceptZoneRect()
            fillPaint.color = Color.argb(36, 33, 150, 243)
            canvas.drawRoundRect(intercept, corner, corner, fillPaint)
            strokePaint.color = Color.argb(120, 66, 165, 245)
            strokePaint.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(intercept, corner, corner, strokePaint)
        }
        zoneLayout.triggerZoneRects().forEach { (handleId, zone) ->
            val handle = settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
            if (handle.design.isVisible) {
                val glowWidth = zoneLayout.glowAwareEdgeWidthPx()
                canvas.withSave {
                    val drawLeft = when (side) {
                        PanelSide.LEFT -> 0f
                        PanelSide.RIGHT -> zone.right - glowWidth
                    }
                    translate(drawLeft, zone.top)
                    TriggerHandleRenderer.draw(
                        canvas = this,
                        side = side,
                        design = handle.design,
                        density = density,
                        widthPx = glowWidth,
                        heightPx = zone.height().toInt().coerceAtLeast(1),
                    )
                }
            } else {
                fillPaint.color = Color.argb(72, 255, 152, 0)
                canvas.drawRoundRect(zone, corner, corner, fillPaint)
                strokePaint.color = Color.argb(210, 255, 167, 38)
                strokePaint.strokeWidth = dp(2f)
                canvas.drawRoundRect(zone, corner, corner, strokePaint)
            }
            drawSwipeDistancePreview(canvas, side, settings, zone, handleId, dp)
        }
    }

    private fun drawSwipeDistancePreview(
        canvas: Canvas,
        side: PanelSide,
        settings: AppSettings,
        zone: RectF,
        handleId: String,
        dp: (Float) -> Float,
    ) {
        val handle = settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
        val shortR = dp(handle.shortSwipeDistanceDp)
        val longR = dp(handle.longSwipeDistanceDp)
        if (longR <= shortR) return
        val cx = when (side) {
            PanelSide.LEFT -> zone.right
            PanelSide.RIGHT -> zone.left
        }
        val cy = zone.centerY()
        val startAngle = when (side) {
            PanelSide.LEFT -> -90f
            PanelSide.RIGHT -> 90f
        }
        val sweep = 180f
        strokePaint.style = Paint.Style.STROKE
        fillPaint.style = Paint.Style.FILL

        fillPaint.color = Color.argb(28, 186, 104, 200)
        canvas.drawArc(cx - longR, cy - longR, cx + longR, cy + longR, startAngle, sweep, true, fillPaint)
        strokePaint.color = Color.argb(170, 171, 71, 188)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawArc(cx - longR, cy - longR, cx + longR, cy + longR, startAngle, sweep, false, strokePaint)

        fillPaint.color = Color.argb(40, 255, 183, 77)
        canvas.drawArc(cx - shortR, cy - shortR, cx + shortR, cy + shortR, startAngle, sweep, true, fillPaint)
        strokePaint.color = Color.argb(220, 255, 152, 0)
        strokePaint.strokeWidth = dp(2.5f)
        canvas.drawArc(cx - shortR, cy - shortR, cx + shortR, cy + shortR, startAngle, sweep, false, strokePaint)
    }
}
