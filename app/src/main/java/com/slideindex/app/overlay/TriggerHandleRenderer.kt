package com.slideindex.app.overlay

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.slideindex.app.gesture.TriggerCornerMode
import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerHandleDesign
import kotlin.math.min

object TriggerHandleRenderer {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(
        canvas: Canvas,
        side: PanelSide,
        design: TriggerHandleDesign,
        density: Float,
        widthPx: Int,
        heightPx: Int,
    ) {
        if (!design.isVisible) return
        when (design.kind) {
            TriggerDesignKind.HIDE -> Unit
            TriggerDesignKind.CUSTOM_IMAGE -> Unit
            TriggerDesignKind.CONFIGURABLE_RECTANGLE -> drawConfigurableRectangle(
                canvas = canvas,
                side = side,
                design = design,
                density = density,
                widthPx = widthPx,
                heightPx = heightPx,
            )
        }
    }

    private fun drawConfigurableRectangle(
        canvas: Canvas,
        side: PanelSide,
        design: TriggerHandleDesign,
        density: Float,
        widthPx: Int,
        heightPx: Int,
    ) {
        val margin = dp(design.marginDp, density)
        val size = dp(design.sizeDp, density)
        val halo = dp(design.haloSizeDp, density)
        val border = dp(design.borderSizeDp, density)
        val radius = dp(design.cornerRadiusDp, density)

        val top = margin
        val bottom = heightPx - margin
        if (bottom <= top) return

        val bodyRect = when (side) {
            PanelSide.LEFT -> RectF(0f, top, size.coerceAtMost(widthPx.toFloat()), bottom)
            PanelSide.RIGHT -> RectF(
                (widthPx - size).coerceAtLeast(0f),
                top,
                widthPx.toFloat(),
                bottom,
            )
        }

        if (halo > 0f) {
            drawHalo(canvas, side, design.haloColor, bodyRect, halo, widthPx, heightPx)
        }

        if (size <= 0f && border <= 0f) return

        cornerRadii(side, design.cornerMode, radius, bodyRect).let { radii ->
            path.reset()
            path.addRoundRect(bodyRect, radii, Path.Direction.CW)
            if (Color.alpha(design.backgroundColor) > 0) {
                fillPaint.style = Paint.Style.FILL
                fillPaint.color = design.backgroundColor
                canvas.drawPath(path, fillPaint)
            }
            if (border > 0f && Color.alpha(design.borderColor) > 0) {
                strokePaint.strokeWidth = border
                strokePaint.color = design.borderColor
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun drawHalo(
        canvas: Canvas,
        side: PanelSide,
        color: Int,
        bodyRect: RectF,
        haloSize: Float,
        widthPx: Int,
        heightPx: Int,
    ) {
        if (Color.alpha(color) <= 0) return
        val haloRect = when (side) {
            PanelSide.LEFT -> RectF(
                0f,
                bodyRect.top - haloSize * 0.35f,
                (bodyRect.right + haloSize).coerceAtMost(widthPx.toFloat()),
                bodyRect.bottom + haloSize * 0.35f,
            )
            PanelSide.RIGHT -> RectF(
                (bodyRect.left - haloSize).coerceAtLeast(0f),
                bodyRect.top - haloSize * 0.35f,
                widthPx.toFloat(),
                bodyRect.bottom + haloSize * 0.35f,
            )
        }
        haloPaint.color = color
        haloPaint.maskFilter = BlurMaskFilter(haloSize * 0.45f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(
            haloRect,
            haloSize * 0.35f,
            haloSize * 0.35f,
            haloPaint,
        )
        haloPaint.maskFilter = null
    }

    private fun cornerRadii(
        side: PanelSide,
        mode: TriggerCornerMode,
        radius: Float,
        bounds: RectF,
    ): FloatArray {
        val capped = radius.coerceAtMost(min(bounds.width(), bounds.height()) / 2f)
        return when (mode) {
            TriggerCornerMode.ALL -> floatArrayOf(
                capped, capped, capped, capped, capped, capped, capped, capped,
            )
            TriggerCornerMode.OUTER -> when (side) {
                PanelSide.LEFT -> floatArrayOf(
                    capped, capped, 0f, 0f, 0f, 0f, capped, capped,
                )
                PanelSide.RIGHT -> floatArrayOf(
                    0f, 0f, capped, capped, capped, capped, 0f, 0f,
                )
            }
        }
    }

    private fun dp(value: Float, density: Float): Float = value * density
}
