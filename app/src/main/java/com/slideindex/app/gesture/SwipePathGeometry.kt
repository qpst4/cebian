package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import kotlin.math.atan2

/** Pure geometry helpers for swipe classification (unit-testable). */
internal object SwipePathGeometry {
    fun inwardDelta(dx: Float, side: PanelSide): Float = when (side) {
        PanelSide.LEFT -> dx
        PanelSide.RIGHT -> -dx
    }

    fun classifySwipeTrigger(
        inward: Float,
        dy: Float,
        distancePx: Float,
        shortThresholdPx: Float,
        longThresholdPx: Float,
        angleConfig: GestureAngleConfig,
    ): GestureTriggerType? {
        if (inward <= 0f) return null
        val angleDegrees = Math.toDegrees(atan2(-dy.toDouble(), inward.toDouble())).toFloat()
        val direction = angleConfig.resolveDirection(angleDegrees) ?: return null
        if (distancePx < shortThresholdPx) return null
        val long = distancePx >= longThresholdPx
        return direction.toTrigger(long)
    }
}
