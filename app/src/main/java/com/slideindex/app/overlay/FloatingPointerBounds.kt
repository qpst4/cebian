package com.slideindex.app.overlay

import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.slideindex.app.settings.AppSettings
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt


internal object FloatingPointerBounds {
    /** Linear mapping (1.0) keeps pointer speed proportional to finger speed at all positions. */
    const val EDGE_CURVE_POWER_X = 1.0f
    const val EDGE_CURVE_POWER_Y = 1.0f

    fun pointerForFingerInArea(
        fingerX: Float,
        fingerY: Float,
        areaLeft: Float,
        areaTop: Float,
        areaWidth: Float,
        areaHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        curvePowerX: Float = EDGE_CURVE_POWER_X,
        curvePowerY: Float = EDGE_CURVE_POWER_Y,
    ): Offset {
        val normX = if (areaWidth > 0f) {
            ((fingerX - areaLeft) / areaWidth).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val normY = if (areaHeight > 0f) {
            ((fingerY - areaTop) / areaHeight).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        return Offset(
            x = mapTravel(0f, normX, 0f, screenWidth, curvePowerX),
            y = mapTravel(0f, normY, 0f, screenHeight, curvePowerY),
        )
    }

    /** Maps finger travel since touch-down to pointer movement from the pointer position at down. */
    fun pointerForFingerDeltaInArea(
        deltaX: Float,
        deltaY: Float,
        areaWidth: Float,
        areaHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        pointerAnchorX: Float,
        pointerAnchorY: Float,
    ): Offset {
        val normDeltaX = if (areaWidth > 0f) deltaX / areaWidth else 0f
        val normDeltaY = if (areaHeight > 0f) deltaY / areaHeight else 0f
        return Offset(
            x = (pointerAnchorX + normDeltaX * screenWidth).coerceIn(0f, screenWidth),
            y = (pointerAnchorY + normDeltaY * screenHeight).coerceIn(0f, screenHeight),
        )
    }

    fun effectiveJoystickAreaSize(
        settings: AppSettings,
        screenWidth: Float,
        screenHeight: Float,
    ): Pair<Float, Float> {
        val zoom = settings.floatingPointerJoystickAreaZoomFraction.coerceIn(0.1f, 1f)
        val width = settings.floatingPointerJoystickAreaWidthPx.coerceIn(120f, 800f) * zoom
        val height = if (settings.floatingPointerMatchJoystickToScreenAspect && screenWidth > 0f) {
            width * (screenHeight / screenWidth)
        } else {
            settings.floatingPointerJoystickAreaHeightPx.coerceIn(120f, 1400f) * zoom
        }
        return width to height
    }

    fun clampJoystickCenter(
        rawX: Float,
        rawY: Float,
        joystickRadiusPx: Float,
        areaWidth: Float,
        areaHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
    ): Offset {
        val margin = 16f * density
        val insetX = maxOf(joystickRadiusPx, areaWidth / 2f) + margin
        val insetY = maxOf(joystickRadiusPx, areaHeight / 2f) + margin
        val x = if (insetX * 2f > screenWidth) {
            screenWidth / 2f
        } else {
            rawX.coerceIn(insetX, screenWidth - insetX)
        }
        val y = if (insetY * 2f > screenHeight) {
            screenHeight / 2f
        } else {
            rawY.coerceIn(insetY, screenHeight - insetY)
        }
        return Offset(x, y)
    }

    internal data class AreaPreviewLayout(
        val trigger: Offset,
        val joystickCenter: Offset,
        val joystickRadiusPx: Float,
        val areaWidth: Float,
        val areaHeight: Float,
        val areaRect: Rect,
        val areaRectOnScreen: Rect,
        val pointerPosition: Offset,
    )

    fun computeAreaPreviewLayout(
        settings: AppSettings,
        density: Float,
        screenWidth: Float,
        screenHeight: Float,
        triggerRawX: Float,
        triggerRawY: Float,
    ): AreaPreviewLayout {
        val joystickRadiusPx = settings.floatingPointerJoystickDiameterPx / 2f
        val (areaWidth, areaHeight) = effectiveJoystickAreaSize(settings, screenWidth, screenHeight)
        val joystickCenter = clampJoystickCenter(
            rawX = triggerRawX,
            rawY = triggerRawY,
            joystickRadiusPx = joystickRadiusPx,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
        val areaLeft = joystickCenter.x - areaWidth / 2f
        val areaTop = joystickCenter.y - areaHeight / 2f
        val areaRect = Rect(
            left = areaLeft,
            top = areaTop,
            right = areaLeft + areaWidth,
            bottom = areaTop + areaHeight,
        )
        val screenRect = Rect(0f, 0f, screenWidth, screenHeight)
        val pointerPosition = pointerForFingerInArea(
            fingerX = triggerRawX,
            fingerY = triggerRawY,
            areaLeft = areaLeft,
            areaTop = areaTop,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        return AreaPreviewLayout(
            trigger = Offset(triggerRawX, triggerRawY),
            joystickCenter = joystickCenter,
            joystickRadiusPx = joystickRadiusPx,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            areaRect = areaRect,
            areaRectOnScreen = areaRect.intersect(screenRect),
            pointerPosition = pointerPosition,
        )
    }

    fun mapTravel(
        start: Float,
        normalized: Float,
        min: Float,
        max: Float,
        curvePower: Float,
    ): Float {
        val curved = applyDeflectionCurve(normalized, curvePower)
        return when {
            normalized < 0f -> (start + curved * (start - min)).coerceIn(min, max)
            normalized > 0f -> (start + curved * (max - start)).coerceIn(min, max)
            else -> start.coerceIn(min, max)
        }
    }

    private fun applyDeflectionCurve(normalized: Float, curvePower: Float): Float {
        val sign = if (normalized < 0f) -1f else 1f
        val magnitude = kotlin.math.abs(normalized).coerceIn(0f, 1f)
        return sign * magnitude.pow(curvePower)
    }

    fun clamp(
        position: Offset,
        screenWidth: Float,
        screenHeight: Float,
    ): Offset {
        return Offset(
            x = position.x.coerceIn(0f, screenWidth),
            y = position.y.coerceIn(0f, screenHeight),
        )
    }
}
