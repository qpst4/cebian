package com.slideindex.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerTrailType
import kotlin.math.hypot
import kotlin.math.pow

internal data class FloatingPointerTrailPoint(
    val x: Float,
    val y: Float,
    val timeMs: Long,
)

internal fun DrawScope.drawFloatingPointerAreaPreview(
    layout: FloatingPointerBounds.AreaPreviewLayout,
    settings: AppSettings,
    screenWidth: Float,
    screenHeight: Float,
) {
    drawRoundRect(
        color = Color.White.copy(alpha = 0.04f),
        topLeft = Offset.Zero,
        size = Size(screenWidth, screenHeight),
        cornerRadius = CornerRadius(0f, 0f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f),
        topLeft = Offset.Zero,
        size = Size(screenWidth, screenHeight),
        cornerRadius = CornerRadius(0f, 0f),
        style = Stroke(width = 2f),
    )

    val area = layout.areaRect
    if (area.width > 0f && area.height > 0f) {
        drawRoundRect(
            color = Color(0x22FFFFFF),
            topLeft = area.topLeft,
            size = area.size,
            cornerRadius = CornerRadius(8f, 8f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.38f),
            topLeft = area.topLeft,
            size = area.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            ),
        )
    }
    val onScreen = layout.areaRectOnScreen
    if (onScreen.width > 0f && onScreen.height > 0f) {
        drawRoundRect(
            color = Color(0x55FFFFFF),
            topLeft = onScreen.topLeft,
            size = onScreen.size,
            cornerRadius = CornerRadius(8f, 8f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.78f),
            topLeft = onScreen.topLeft,
            size = onScreen.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2f),
        )
    }

    val pointerRadius = (settings.floatingPointerPointerDiameterPx / 2f)
        .coerceAtMost(screenWidth * 0.05f)
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = pointerRadius,
        center = layout.pointerPosition,
        style = Stroke(width = 2.5f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = 5f,
        center = layout.pointerPosition,
    )

    drawQcJoystickDisc(
        center = layout.joystickCenter,
        radiusPx = layout.joystickRadiusPx,
        innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
        outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
        gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
        pressed = false,
    )

    drawCircle(
        color = Color(0xFFFF5252),
        radius = 9f,
        center = layout.trigger,
    )
    drawCircle(
        color = Color.White,
        radius = 4f,
        center = layout.trigger,
    )
}

@Composable
fun FloatingPointerJoystickPreview(
    settings: AppSettings,
    pressed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val radius = settings.floatingPointerJoystickDiameterPx / 2f
        drawQcJoystickDisc(
            center = Offset(size.width / 2f, size.height / 2f),
            radiusPx = radius.coerceAtMost(size.minDimension / 2f - 8f),
            innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
            outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
            gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
            pressed = pressed,
        )
    }
}

@Composable
fun FloatingPointerRingPreview(
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val diameter = settings.floatingPointerPointerDiameterPx.coerceAtMost(size.minDimension - 16f)
        drawQcRingPointer(
            center = Offset(size.width / 2f, size.height / 2f),
            diameterPx = diameter,
            ringThicknessPx = settings.floatingPointerRingThicknessPx,
            dotDiameterPx = settings.floatingPointerDotDiameterPx,
            ringColor = Color(settings.floatingPointerRingColorArgb),
            fillColor = Color(settings.floatingPointerFillColorArgb),
            dotColor = Color(settings.floatingPointerDotColorArgb),
        )
    }
}

@Composable
fun FloatingPointerRadialMenuPreview(
    settings: AppSettings,
    slots: List<com.slideindex.app.gesture.GestureAction>,
    highlightedSlot: Int = -1,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        val maxOuter = settings.floatingPointerRadialOuterDiameterPx / 2f
        val scale = (size.minDimension / 2f - 12f) / maxOuter.coerceAtLeast(1f)
        val outer = settings.floatingPointerRadialOuterDiameterPx * scale
        val inner = settings.floatingPointerRadialInnerDiameterPx * scale
        val previewSettings = settings.copy(
            floatingPointerRadialOuterDiameterPx = outer,
            floatingPointerRadialInnerDiameterPx = inner,
            floatingPointerRadialDividerThicknessPx = settings.floatingPointerRadialDividerThicknessPx * scale,
        )
        drawQcJoystickDisc(
            center = Offset(size.width / 2f, size.height / 2f),
            radiusPx = (settings.floatingPointerJoystickDiameterPx / 2f * scale)
                .coerceAtMost(inner / 2f - 4f),
            innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
            outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
            gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
            pressed = true,
        )
        drawFloatingPointerRadialMenu(
            center = Offset(size.width / 2f, size.height / 2f),
            settings = previewSettings,
            slots = slots,
            highlightedSlot = highlightedSlot,
        )
    }
}

fun DrawScope.drawQcJoystickDisc(
    center: Offset,
    radiusPx: Float,
    innerColor: Color,
    outerColor: Color,
    gradientRadiusFraction: Float,
    pressed: Boolean,
) {
    val alphaScale = if (pressed) 1f else 0.94f
    val gradientStop = gradientRadiusFraction.coerceIn(0.5f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to innerColor.copy(alpha = innerColor.alpha * alphaScale),
                gradientStop to outerColor.copy(alpha = outerColor.alpha * alphaScale),
                1f to outerColor.copy(alpha = outerColor.alpha * alphaScale * 0.85f),
            ),
            center = center,
            radius = radiusPx,
        ),
        radius = radiusPx,
        center = center,
    )
}

fun DrawScope.drawQcRingPointer(
    center: Offset,
    diameterPx: Float,
    ringThicknessPx: Float,
    dotDiameterPx: Float,
    ringColor: Color,
    fillColor: Color,
    dotColor: Color,
) {
    val outerRadius = diameterPx / 2f
    val dotRadius = dotDiameterPx / 2f
    val innerRingRadius = (outerRadius - ringThicknessPx).coerceAtLeast(dotRadius)

    if (innerRingRadius > dotRadius) {
        drawAnnulus(
            center = center,
            outerRadius = innerRingRadius,
            innerRadius = dotRadius,
            color = fillColor,
        )
    }

    if (outerRadius > innerRingRadius) {
        drawAnnulus(
            center = center,
            outerRadius = outerRadius,
            innerRadius = innerRingRadius,
            color = ringColor,
        )
    }

    drawCircle(
        color = ringColor.copy(alpha = ringColor.alpha * 0.9f),
        radius = outerRadius,
        center = center,
        style = Stroke(width = 1.4f),
    )
    drawCircle(
        color = ringColor.copy(alpha = ringColor.alpha * 0.75f),
        radius = innerRingRadius,
        center = center,
        style = Stroke(width = 1.1f),
    )
    drawCircle(
        color = dotColor,
        radius = dotRadius,
        center = center,
    )
}

private fun DrawScope.drawAnnulus(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color,
) {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(
            Rect(
                center.x - outerRadius,
                center.y - outerRadius,
                center.x + outerRadius,
                center.y + outerRadius,
            ),
        )
        addOval(
            Rect(
                center.x - innerRadius,
                center.y - innerRadius,
                center.x + innerRadius,
                center.y + innerRadius,
            ),
        )
    }
    drawPath(path, color)
}

internal fun DrawScope.drawFloatingPointerTrail(
    trailPoints: List<FloatingPointerTrailPoint>,
    settings: AppSettings,
    nowMs: Long,
) {
    val trailType = FloatingPointerTrailType.fromId(settings.floatingPointerTrailTypeId)
    if (trailType == FloatingPointerTrailType.OFF || trailPoints.size < 2) return

    val duration = settings.floatingPointerTrailDurationMs.coerceAtLeast(50)
    val baseColor = Color(settings.floatingPointerTrailColorArgb)
    val pointerRadius = settings.floatingPointerPointerDiameterPx / 2f

    val visible = trailPoints.filter { nowMs - it.timeMs <= duration }
    if (visible.size < 2) return

    when (trailType) {
        FloatingPointerTrailType.SIMPLE -> {
            visible.forEach { point ->
                val age = (nowMs - point.timeMs).toFloat() / duration
                val alpha = trailAlphaForAge(age) * baseColor.alpha
                val radius = pointerRadius * (0.35f + (1f - age) * 0.2f)
                drawTrailDot(
                    center = Offset(point.x, point.y),
                    radius = radius,
                    color = baseColor,
                    alpha = alpha,
                )
            }
        }
        FloatingPointerTrailType.HIGH_DETAIL -> {
            for (index in 1 until visible.size) {
                val prev = visible[index - 1]
                val next = visible[index]
                val distance = hypot((next.x - prev.x).toDouble(), (next.y - prev.y).toDouble()).toFloat()
                val steps = (distance / 3f).toInt().coerceIn(2, 28)
                repeat(steps) { step ->
                    val t = step / steps.toFloat()
                    val x = prev.x + (next.x - prev.x) * t
                    val y = prev.y + (next.y - prev.y) * t
                    val sampleTime = prev.timeMs + ((next.timeMs - prev.timeMs) * t).toLong()
                    val age = (nowMs - sampleTime).toFloat() / duration
                    if (age > 1f) return@repeat
                    val alpha = trailAlphaForAge(age) * baseColor.alpha
                    val headBias = 1f - t * 0.55f
                    val radius = pointerRadius * (0.08f + headBias * 0.14f)
                    drawTrailDot(
                        center = Offset(x, y),
                        radius = radius,
                        color = baseColor,
                        alpha = alpha * (0.55f + headBias * 0.45f),
                    )
                }
            }
        }
        FloatingPointerTrailType.OFF -> Unit
    }
}

private fun trailAlphaForAge(age: Float): Float {
    val clamped = age.coerceIn(0f, 1f)
    return (1f - clamped).pow(1.6f)
}

private fun DrawScope.drawTrailDot(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
) {
    if (alpha <= 0.01f || radius <= 0.5f) return
    drawCircle(
        color = color.copy(alpha = alpha * 0.18f),
        radius = radius * 2.1f,
        center = center,
    )
    drawCircle(
        color = color.copy(alpha = alpha * 0.42f),
        radius = radius * 1.35f,
        center = center,
    )
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center,
    )
}

internal fun DrawScope.drawFloatingPointerRipple(
    center: Offset,
    elapsedMs: Long,
    ringColor: Color,
    pointerDiameterPx: Float,
    durationMs: Long = 280L,
) {
    if (elapsedMs < 0L || elapsedMs > durationMs) return
    val progress = elapsedMs.toFloat() / durationMs
    val eased = 1f - (1f - progress).pow(2f)
    val maxRadius = pointerDiameterPx * 1.35f
    val radius = maxRadius * eased
    val alpha = (1f - progress).pow(1.4f) * ringColor.alpha

    drawCircle(
        color = ringColor.copy(alpha = alpha * 0.12f),
        radius = radius * 1.08f,
        center = center,
    )
    drawCircle(
        color = ringColor.copy(alpha = alpha * 0.55f),
        radius = radius,
        center = center,
        style = Stroke(width = (2.8f - progress * 1.2f).coerceAtLeast(1.2f)),
    )
    val innerProgress = (progress * 1.35f).coerceIn(0f, 1f)
    val innerRadius = maxRadius * 0.35f * (1f - (1f - innerProgress).pow(2f))
    val innerAlpha = (1f - innerProgress).pow(1.8f) * ringColor.alpha
    drawCircle(
        color = ringColor.copy(alpha = innerAlpha * 0.35f),
        radius = innerRadius,
        center = center,
        style = Stroke(width = 1.6f),
    )
}
