package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.slideindex.app.util.ContinuousAdjustController
import kotlin.math.roundToInt

/**
 * Floating vertical level pill shown while adjusting volume or brightness.
 */
object AdjustLevelIndicator {
    data class Layout(
        val bounds: RectF,
        val track: RectF,
    )

    fun layout(
        viewWidth: Int,
        viewHeight: Int,
        side: PanelSide,
        anchorY: Float,
        density: Float,
    ): Layout {
        val pillWidth = 52f * density
        val pillHeight = 196f * density
        val edgeInset = 18f * density
        val marginY = 24f * density
        val centerY = anchorY.coerceIn(
            marginY + pillHeight / 2f,
            viewHeight - marginY - pillHeight / 2f,
        )
        val left = when (side) {
            PanelSide.LEFT -> edgeInset
            PanelSide.RIGHT -> viewWidth - edgeInset - pillWidth
        }
        val top = centerY - pillHeight / 2f
        val bounds = RectF(left, top, left + pillWidth, top + pillHeight)
        val inset = 10f * density
        val iconArea = 34f * density
        val labelArea = 22f * density
        val track = RectF(
            bounds.left + inset,
            bounds.top + iconArea,
            bounds.right - inset,
            bounds.bottom - labelArea,
        )
        return Layout(bounds, track)
    }

    fun hitBounds(layout: Layout, side: PanelSide, density: Float): RectF {
        val verticalPad = 14f * density
        val innerPad = 10f * density
        return RectF(layout.bounds).apply {
            top -= verticalPad
            bottom += verticalPad
            when (side) {
                PanelSide.LEFT -> {
                    left = layout.bounds.left
                    right = layout.bounds.right + innerPad
                }
                PanelSide.RIGHT -> {
                    left = layout.bounds.left - innerPad
                    right = layout.bounds.right
                }
            }
        }
    }

    fun containsTouch(
        layout: Layout,
        side: PanelSide,
        localX: Float,
        localY: Float,
        density: Float,
    ): Boolean = hitBounds(layout, side, density).contains(localX, localY)

    fun draw(
        canvas: Canvas,
        layout: Layout,
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        enterProgress: Float,
        density: Float,
        side: PanelSide,
    ) {
        if (enterProgress <= 0f) return
        val eased = easeOutCubic(enterProgress.coerceIn(0f, 1f))
        val scale = 0.82f + 0.18f * eased
        val alphaScale = eased
        val slidePx = 22f * density * (1f - eased)
        val slideX = when (side) {
            PanelSide.LEFT -> -slidePx
            PanelSide.RIGHT -> slidePx
        }

        canvas.save()
        val cx = layout.bounds.centerX()
        val cy = layout.bounds.centerY()
        canvas.translate(slideX, 0f)
        canvas.scale(scale, scale, cx, cy)

        drawShadow(canvas, layout.bounds, 16f * density, alphaScale)
        drawPillBackground(canvas, layout.bounds, 18f * density, alphaScale)
        drawTrack(canvas, layout.track, mode, fraction.coerceIn(0f, 1f), density, alphaScale)
        drawIcon(canvas, layout.bounds, layout.track.top, mode, density, alphaScale)
        drawPercentLabel(canvas, layout.bounds, fraction.coerceIn(0f, 1f), density, alphaScale)

        canvas.restore()
    }

    private fun drawShadow(canvas: Canvas, bounds: RectF, corner: Float, alphaScale: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((55 * alphaScale).roundToInt(), 0, 0, 0)
        }
        val spread = 6f
        canvas.drawRoundRect(
            bounds.left - spread,
            bounds.top - spread + 2f,
            bounds.right + spread,
            bounds.bottom + spread + 4f,
            corner + 4f,
            corner + 4f,
            shadowPaint,
        )
    }

    private fun drawPillBackground(canvas: Canvas, bounds: RectF, corner: Float, alphaScale: Float) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((210 * alphaScale).roundToInt(), 22, 24, 30)
        }
        canvas.drawRoundRect(bounds, corner, corner, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.argb((70 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(bounds, corner, corner, strokePaint)
    }

    private fun drawTrack(
        canvas: Canvas,
        track: RectF,
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        density: Float,
        alphaScale: Float,
    ) {
        val corner = 8f * density
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((45 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(track, corner, corner, trackPaint)

        if (fraction <= 0f) return

        val fillHeight = track.height() * fraction
        val fillTop = track.bottom - fillHeight
        val fillRect = RectF(track.left, fillTop, track.right, track.bottom)

        val (startColor, endColor) = when (mode) {
            ContinuousAdjustController.Mode.VOLUME -> {
                Color.argb((230 * alphaScale).roundToInt(), 66, 133, 244) to
                    Color.argb((255 * alphaScale).roundToInt(), 120, 190, 255)
            }
            ContinuousAdjustController.Mode.BRIGHTNESS -> {
                Color.argb((230 * alphaScale).roundToInt(), 255, 183, 77) to
                    Color.argb((255 * alphaScale).roundToInt(), 255, 236, 179)
            }
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                fillRect.left,
                fillRect.bottom,
                fillRect.left,
                fillRect.top,
                startColor,
                endColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(fillRect, corner, corner, fillPaint)

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((35 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(
            fillRect.left + 2f * density,
            fillTop + 2f * density,
            fillRect.right - 2f * density,
            fillTop + 8f * density,
            corner,
            corner,
            highlightPaint,
        )
    }

    private fun drawIcon(
        canvas: Canvas,
        bounds: RectF,
        trackTop: Float,
        mode: ContinuousAdjustController.Mode,
        density: Float,
        alphaScale: Float,
    ) {
        val iconSize = 18f * density
        val cx = bounds.centerX()
        val cy = bounds.top + (trackTop - bounds.top) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb((220 * alphaScale).roundToInt(), 255, 255, 255)
        }
        when (mode) {
            ContinuousAdjustController.Mode.VOLUME -> drawVolumeIcon(canvas, cx, cy, iconSize, paint)
            ContinuousAdjustController.Mode.BRIGHTNESS -> drawBrightnessIcon(canvas, cx, cy, iconSize, paint)
        }
    }

    private fun drawVolumeIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val half = size * 0.42f
        val path = Path().apply {
            moveTo(cx - half * 0.35f, cy - half * 0.55f)
            lineTo(cx - half * 0.95f, cy - half * 0.55f)
            lineTo(cx - half * 0.95f, cy + half * 0.55f)
            lineTo(cx - half * 0.35f, cy + half * 0.55f)
            lineTo(cx + half * 0.15f, cy + half * 0.95f)
            lineTo(cx + half * 0.15f, cy - half * 0.95f)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.drawArc(
            cx + half * 0.55f,
            cy - half * 0.65f,
            cx + half * 1.55f,
            cy + half * 0.65f,
            -42f,
            84f,
            false,
            paint,
        )
        canvas.drawArc(
            cx + half * 0.95f,
            cy - half * 0.95f,
            cx + half * 2.05f,
            cy + half * 0.95f,
            -36f,
            72f,
            false,
            paint,
        )
    }

    private fun drawBrightnessIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.28f
        canvas.drawCircle(cx, cy, r, paint)
        val ray = size * 0.48f
        val inner = r + 2.5f
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45 - 90).toDouble())
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            canvas.drawLine(
                cx + cos * inner,
                cy + sin * inner,
                cx + cos * ray,
                cy + sin * ray,
                paint,
            )
        }
    }

    private fun drawPercentLabel(
        canvas: Canvas,
        bounds: RectF,
        fraction: Float,
        density: Float,
        alphaScale: Float,
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f * density
            color = Color.argb((200 * alphaScale).roundToInt(), 255, 255, 255)
        }
        val label = "${(fraction * 100f).roundToInt()}%"
        val baseline = bounds.bottom - 9f * density
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t
        return 1f - inv * inv * inv
    }
}
