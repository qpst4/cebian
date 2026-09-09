package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.slideindex.app.settings.AppSettings
import kotlin.math.pow


internal object FloatingPointerBounds {
    /** Linear mapping (1.0) keeps pointer speed proportional to finger speed at all positions. */
    const val EDGE_CURVE_POWER_X = 1.0f
    const val EDGE_CURVE_POWER_Y = 1.0f

    const val SENSITIVITY_MIN = 0.2f
    const val SENSITIVITY_MAX = 0.75f
    const val DEFAULT_SENSITIVITY_FRACTION = 0.52f
  /** Reference width used when migrating legacy px-based area settings. */
    private const val MIGRATION_REFERENCE_SCREEN_WIDTH = 1080f

    fun sensitivityFraction(settings: AppSettings): Float =
        settings.floatingPointerSensitivityFraction.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)

    /** UI speed fraction (higher = faster) → finger travel fraction (lower = faster). */
    fun travelFractionFromSpeed(speedFraction: Float): Float {
        val speed = speedFraction.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
        return SENSITIVITY_MIN + SENSITIVITY_MAX - speed
    }

    /**
     * Finger travel along each axis needed to move the pointer across the full screen.
     * Higher [sensitivityFraction] (UI speed %) = shorter travel = faster pointer.
     */
    fun effectivePointerTravel(
        settings: AppSettings,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> = effectivePointerTravelForSpeed(
        speedFraction = sensitivityFraction(settings),
        screenWidth = screenWidth,
        screenHeight = screenHeight
    )

    /** Same mapping as edge floating pointer, but takes an explicit speed fraction. */
    fun effectivePointerTravelForSpeed(
        speedFraction: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {
        val travelFraction = travelFractionFromSpeed(speedFraction)
        return (screenWidth * travelFraction) to (screenHeight * travelFraction)
    }

    /** Migrates legacy width/zoom area prefs to the unified speed fraction. */
    fun migrateLegacySensitivityFraction(
        legacyWidthPx: Float,
        legacyZoomFraction: Float
    ): Float {
        val travelPx = legacyWidthPx.coerceIn(120f, 800f) * legacyZoomFraction.coerceIn(0.1f, 1f)
        val travelFraction =
            (travelPx / MIGRATION_REFERENCE_SCREEN_WIDTH).coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
        return travelFractionFromSpeed(travelFraction)
    }

    /** Maps finger travel since touch-down to pointer movement from the pointer position at down. */
    fun pointerForFingerDeltaInArea(
        deltaX: Float,
        deltaY: Float,
        travelWidth: Float,
        travelHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        pointerAnchorX: Float,
        pointerAnchorY: Float
    ): Offset {
        val normDeltaX = if (travelWidth > 0f) deltaX / travelWidth else 0f
        val normDeltaY = if (travelHeight > 0f) deltaY / travelHeight else 0f
        return Offset(
            x = (pointerAnchorX + normDeltaX * screenWidth).coerceIn(0f, screenWidth),
            y = (pointerAnchorY + normDeltaY * screenHeight).coerceIn(0f, screenHeight)
        )
    }

    /**
     * Initial pointer position when continuing an in-flight edge gesture without lifting the finger.
     * Snaps to the nearest screen edge at the trigger finger's proportional position.
     */
    fun pointerStartForEdgeTrigger(
        rawX: Float,
        rawY: Float,
        screenWidth: Float,
        screenHeight: Float,
        edgeThresholdPx: Float
    ): Offset {
        if (screenWidth <= 0f || screenHeight <= 0f) {
            return Offset(screenWidth / 2f, screenHeight / 2f)
        }
        val x = rawX.coerceIn(0f, screenWidth)
        val y = rawY.coerceIn(0f, screenHeight)
        val threshold = edgeThresholdPx.coerceAtLeast(1f)
        val nearLeft = x <= threshold
        val nearRight = x >= screenWidth - threshold
        val nearTop = y <= threshold
        val nearBottom = y >= screenHeight - threshold
        return when {
            nearLeft && nearTop -> Offset(0f, 0f)
            nearRight && nearTop -> Offset(screenWidth, 0f)
            nearLeft && nearBottom -> Offset(0f, screenHeight)
            nearRight && nearBottom -> Offset(screenWidth, screenHeight)
            nearLeft -> Offset(0f, y)
            nearRight -> Offset(screenWidth, y)
            nearTop -> Offset(x, 0f)
            nearBottom -> Offset(x, screenHeight)
            else -> Offset(x, y)
        }
    }

    fun clampJoystickCenter(
        rawX: Float,
        rawY: Float,
        joystickRadiusPx: Float,
        travelWidth: Float,
        travelHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        density: Float
    ): Offset {
        val margin = 16f * density
        val insetX = maxOf(joystickRadiusPx, travelWidth / 2f) + margin
        val insetY = maxOf(joystickRadiusPx, travelHeight / 2f) + margin
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
        val travelWidth: Float,
        val travelHeight: Float,
        val travelRect: Rect,
        val travelRectOnScreen: Rect,
        val pointerPosition: Offset
    )

    fun computeAreaPreviewLayout(
        settings: AppSettings,
        density: Float,
        screenWidth: Float,
        screenHeight: Float,
        triggerRawX: Float,
        triggerRawY: Float
    ): AreaPreviewLayout {
        val joystickRadiusPx = settings.floatingPointerJoystickDiameterPx / 2f
        val (travelWidth, travelHeight) = effectivePointerTravel(settings, screenWidth, screenHeight)
        val joystickCenter = clampJoystickCenter(
            rawX = triggerRawX,
            rawY = triggerRawY,
            joystickRadiusPx = joystickRadiusPx,
            travelWidth = travelWidth,
            travelHeight = travelHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density
        )
        val travelLeft = joystickCenter.x - travelWidth / 2f
        val travelTop = joystickCenter.y - travelHeight / 2f
        val travelRect = Rect(
            left = travelLeft,
            top = travelTop,
            right = travelLeft + travelWidth,
            bottom = travelTop + travelHeight
        )
        val screenRect = Rect(0f, 0f, screenWidth, screenHeight)
        val edgeThresholdPx = 48f * density
        val pointerPosition = pointerStartForEdgeTrigger(
            rawX = triggerRawX,
            rawY = triggerRawY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeThresholdPx = edgeThresholdPx
        )
        return AreaPreviewLayout(
            trigger = Offset(triggerRawX, triggerRawY),
            joystickCenter = joystickCenter,
            joystickRadiusPx = joystickRadiusPx,
            travelWidth = travelWidth,
            travelHeight = travelHeight,
            travelRect = travelRect,
            travelRectOnScreen = travelRect.intersect(screenRect),
            pointerPosition = pointerPosition
        )
    }

    fun mapTravel(
        start: Float,
        normalized: Float,
        min: Float,
        max: Float,
        curvePower: Float
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
        screenHeight: Float
    ): Offset {
        return Offset(
            x = position.x.coerceIn(0f, screenWidth),
            y = position.y.coerceIn(0f, screenHeight)
        )
    }
}
