package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
        val iconArea = 36f * density
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
        drawIcon(
            canvas = canvas,
            bounds = layout.bounds,
            trackTop = layout.track.top,
            mode = mode,
            fraction = fraction.coerceIn(0f, 1f),
            density = density,
            alphaScale = alphaScale,
        )
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

    private data class IconAccent(
        val primary: Int,
        val secondary: Int,
        val glow: Int,
    )

    private fun iconAccent(mode: ContinuousAdjustController.Mode, level: Float, alphaScale: Float): IconAccent {
        val a = alphaScale.coerceIn(0f, 1f)
        return when (mode) {
            ContinuousAdjustController.Mode.VOLUME -> IconAccent(
                primary = scaledColor(160, 210, 255, a),
                secondary = scaledColor(90, 165, 255, a),
                glow = scaledColor(66, 133, 244, a * (0.35f + 0.65f * level)),
            )
            ContinuousAdjustController.Mode.BRIGHTNESS -> IconAccent(
                primary = scaledColor(255, 236, 170, a),
                secondary = scaledColor(255, 196, 90, a),
                glow = scaledColor(255, 183, 77, a * (0.30f + 0.70f * level)),
            )
        }
    }

    private fun drawIcon(
        canvas: Canvas,
        bounds: RectF,
        trackTop: Float,
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        density: Float,
        alphaScale: Float,
    ) {
        val level = fraction.coerceIn(0f, 1f)
        val iconSize = 19f * density
        val cx = bounds.centerX()
        val cy = bounds.top + (trackTop - bounds.top) / 2f
        val accent = iconAccent(mode, level, alphaScale)

        when (mode) {
            ContinuousAdjustController.Mode.VOLUME -> drawVolumeIcon(canvas, cx, cy, iconSize, level, accent, density, alphaScale)
            ContinuousAdjustController.Mode.BRIGHTNESS -> {
                drawIconHeaderGlow(canvas, cx, cy, bounds.width(), trackTop - bounds.top, accent, level, density, alphaScale)
                drawBrightnessIcon(canvas, cx, cy, iconSize, level, accent, density)
            }
        }
    }

    private fun drawIconHeaderGlow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        width: Float,
        headerHeight: Float,
        accent: IconAccent,
        level: Float,
        density: Float,
        alphaScale: Float,
    ) {
        if (level <= 0.02f) return
        val radius = (width.coerceAtMost(headerHeight) * 0.62f) * (0.85f + 0.15f * level)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy,
                radius,
                Color.argb((110 * alphaScale * (0.35f + 0.65f * level)).roundToInt(), Color.red(accent.glow), Color.green(accent.glow), Color.blue(accent.glow)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, glowPaint)
    }

    private fun drawVolumeIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        level: Float,
        accent: IconAccent,
        density: Float,
        alphaScale: Float,
    ) {
        val halfH = size * 0.36f
        val bodyInset = size * 0.04f
        val speakerWidth = size * 0.52f
        val waveCount = volumeWaveCount(level)
        val waveOffsets = floatArrayOf(0.18f, 0.36f, 0.54f)
        val waveRadii = floatArrayOf(0.17f, 0.27f, 0.36f)

        val contentWidth = when {
            level <= 0.02f -> size * 0.88f
            waveCount == 0 -> bodyInset + speakerWidth + bodyInset
            else -> {
                val last = waveCount - 1
                bodyInset + speakerWidth + size * (waveOffsets[last] + waveRadii[last])
            }
        }
        val groupLeft = cx - contentWidth / 2f
        val bodyLeft = groupLeft + bodyInset
        val speakerRight = bodyLeft + speakerWidth

        val iconAlpha = (210f * alphaScale).roundToInt()
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.65f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(iconAlpha, 245, 247, 250)
        }

        val speakerPath = Path().apply {
            moveTo(bodyLeft + halfH * 0.55f, cy - halfH * 0.62f)
            lineTo(bodyLeft, cy - halfH * 0.62f)
            lineTo(bodyLeft, cy + halfH * 0.62f)
            lineTo(bodyLeft + halfH * 0.55f, cy + halfH * 0.62f)
            lineTo(speakerRight, cy + halfH * 1.05f)
            lineTo(speakerRight, cy - halfH * 1.05f)
            close()
        }
        canvas.drawPath(speakerPath, strokePaint)

        if (level <= 0.02f) {
            val mutePaint = Paint(strokePaint).apply {
                color = Color.argb((150f * alphaScale).roundToInt(), 190, 194, 202)
                strokeWidth = 1.5f * density
            }
            val slashPad = size * 0.14f
            canvas.drawLine(
                groupLeft + slashPad,
                cy - halfH * 0.75f,
                groupLeft + contentWidth - slashPad,
                cy + halfH * 0.75f,
                mutePaint,
            )
            return
        }

        if (waveCount == 0) return

        val wavePaint = Paint(strokePaint).apply {
            strokeWidth = 1.55f * density
            color = Color.argb(
                iconAlpha,
                Color.red(accent.primary),
                Color.green(accent.primary),
                Color.blue(accent.primary),
            )
        }
        for (i in 0 until waveCount) {
            val r = size * waveRadii[i]
            val waveCenter = speakerRight + size * waveOffsets[i]
            canvas.drawArc(
                waveCenter - r,
                cy - r * 1.15f,
                waveCenter + r,
                cy + r * 1.15f,
                -68f,
                136f,
                false,
                wavePaint,
            )
        }
    }

    private fun volumeWaveCount(level: Float): Int = when {
        level <= 0.02f -> 0
        level < 0.08f -> 0
        level < 0.45f -> 1
        level < 0.80f -> 2
        else -> 3
    }

    private fun drawBrightnessIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        level: Float,
        accent: IconAccent,
        density: Float,
    ) {
        val coreRadius = size * (0.22f + 0.08f * level)
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - coreRadius * 0.2f,
                cy - coreRadius * 0.2f,
                coreRadius * 1.2f,
                blendColors(scaledColor(255, 255, 245, 1f), accent.primary, 0.25f + 0.55f * level),
                accent.secondary,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        val coreRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.55f * density
            color = blendColors(accent.primary, scaledColor(255, 255, 255, 1f), 0.35f + 0.45f * level)
            alpha = (140 + 80 * level).roundToInt()
        }
        canvas.drawCircle(cx, cy, coreRadius, coreRing)

        if (level <= 0.03f) return

        val rayCount = 8
        val visibleRays = when {
            level < 0.20f -> 4
            level < 0.55f -> 6
            else -> 8
        }
        val rayInner = coreRadius + 2.5f * density
        val rayOuter = size * (0.32f + 0.22f * level)
        for (i in 0 until visibleRays) {
            val angle = Math.toRadians((i * (360.0 / visibleRays) - 90.0))
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = (1.45f + 0.55f * level) * density
                color = blendColors(accent.secondary, accent.primary, level)
                alpha = (110 + 120 * level).roundToInt()
            }
            canvas.drawLine(
                cx + cos * rayInner,
                cy + sin * rayInner,
                cx + cos * rayOuter,
                cy + sin * rayOuter,
                rayPaint,
            )
        }
    }

    private fun scaledColor(r: Int, g: Int, b: Int, alphaScale: Float): Int =
        Color.argb((255f * alphaScale.coerceIn(0f, 1f)).roundToInt(), r, g, b)

    private fun blendColors(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt(),
        )
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
