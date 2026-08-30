package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot

/** Pure geometry helpers for swipe classification (unit-testable). */
internal object SwipePathGeometry {
    fun inwardDelta(dx: Float, dy: Float, side: PanelSide): Float = when (side) {
        PanelSide.LEFT -> dx
        PanelSide.RIGHT -> -dx
        PanelSide.BOTTOM -> -dy
        PanelSide.TOP -> dy
    }

    fun inwardDelta(dx: Float, side: PanelSide): Float = inwardDelta(dx, 0f, side)

    fun classifySwipeTrigger(
        side: PanelSide,
        stripBounds: RectF,
        startX: Float,
        startY: Float,
        fingerX: Float,
        fingerY: Float,
        shortThresholdPx: Float,
        longThresholdPx: Float,
        angle: GestureAngle,
    ): GestureTriggerType? {
        val direction = resolveSwipeDirection(side, stripBounds, startX, startY, fingerX, fingerY, angle)
            ?: return null
        val distance = measureTriggerDistance(
            side = side,
            direction = direction,
            startX = startX,
            startY = startY,
            fingerX = fingerX,
            fingerY = fingerY,
            stripBounds = stripBounds,
        )
        if (distance < shortThresholdPx) return null
        if (direction != SwipeDirection.UP &&
            direction != SwipeDirection.DOWN &&
            distance <= 0f
        ) {
            return null
        }
        val long = distance >= longThresholdPx
        return direction.toTrigger(long)
    }

    fun resolveSwipeDirection(
        side: PanelSide,
        stripBounds: RectF,
        startX: Float,
        startY: Float,
        fingerX: Float,
        fingerY: Float,
        angle: GestureAngle,
    ): SwipeDirection? {
        // 用相对起点的内滑增量判断方向，避免触钮条内起手时绝对内距把水平滑误判为斜向。
        val opposite = inwardDelta(fingerX - startX, fingerY - startY, side).coerceAtLeast(0f)
        val neighbor = when (side) {
            PanelSide.LEFT, PanelSide.RIGHT -> abs(fingerY - startY)
            PanelSide.BOTTOM, PanelSide.TOP -> abs(fingerX - startX)
        }
        if (opposite <= 0f && neighbor <= 0f) return null
        val tanVal = if (neighbor == 0f) Float.MAX_VALUE else opposite / neighbor
        val radians = atan(tanVal)
        val isPreviousArea = when (side) {
            PanelSide.LEFT, PanelSide.RIGHT -> fingerY < startY
            PanelSide.BOTTOM, PanelSide.TOP -> fingerX < startX
        }
        val degree = if (isPreviousArea) {
            Math.toDegrees(radians.toDouble()).toFloat()
        } else {
            GESTURE_ANGLE_BASE - Math.toDegrees(radians.toDouble()).toFloat()
        }
        return angle.toSwipeDirection(degree)
    }

    fun measureTriggerDistance(
        side: PanelSide,
        direction: SwipeDirection,
        startX: Float,
        startY: Float,
        fingerX: Float,
        fingerY: Float,
        stripBounds: RectF,
    ): Float {
        val inwardSlide = when (side) {
            PanelSide.LEFT -> fingerX - stripBounds.left
            PanelSide.RIGHT -> stripBounds.right - fingerX
            PanelSide.BOTTOM -> stripBounds.bottom - fingerY
            PanelSide.TOP -> fingerY - stripBounds.top
        }
        val alongForExtreme = when (side) {
            PanelSide.LEFT, PanelSide.RIGHT -> startY - fingerY
            PanelSide.BOTTOM, PanelSide.TOP -> fingerX - startX
        }
        return when (direction) {
            SwipeDirection.UP, SwipeDirection.DOWN -> abs(alongForExtreme)
            SwipeDirection.IN -> inwardSlide
            SwipeDirection.UP_RIGHT, SwipeDirection.DOWN_RIGHT -> {
                val along = when (side) {
                    PanelSide.LEFT, PanelSide.RIGHT -> abs(fingerY - startY)
                    PanelSide.BOTTOM, PanelSide.TOP -> abs(fingerX - startX)
                }
                hypot(inwardSlide.coerceAtLeast(0f).toDouble(), along.toDouble()).toFloat()
            }
        }
    }

    fun alongDelta(dx: Float, dy: Float, side: PanelSide): Float = when (side) {
        PanelSide.LEFT, PanelSide.RIGHT -> dy
        PanelSide.BOTTOM, PanelSide.TOP -> dx
    }

    fun resolveCornerSwipeTrigger(
        side: PanelSide,
        inwardReachedThreshold: Boolean,
        currentInward: Float,
        shortThresholdPx: Float,
        longThresholdPx: Float,
        startX: Float,
        startY: Float,
        fingerX: Float,
        fingerY: Float,
        turnThresholdPx: Float,
    ): GestureTriggerType? {
        if (!inwardReachedThreshold) return null
        if (currentInward < shortThresholdPx * 0.45f) return null
        val along = alongDelta(fingerX - startX, fingerY - startY, side)
        if (abs(along) < turnThresholdPx) return null
        val totalDistance = hypot(currentInward.toDouble(), along.toDouble()).toFloat()
        val isLong = currentInward >= longThresholdPx || totalDistance >= longThresholdPx
        return if (along < 0f) {
            if (isLong) GestureTriggerType.LONG_SWIPE_IN_UP else GestureTriggerType.SHORT_SWIPE_IN_UP
        } else {
            if (isLong) GestureTriggerType.LONG_SWIPE_IN_DOWN else GestureTriggerType.SHORT_SWIPE_IN_DOWN
        }
    }

    fun resolveReturnSwipeTrigger(
        side: PanelSide,
        inwardReachedThreshold: Boolean,
        peakInward: Float,
        currentInward: Float,
        shortThresholdPx: Float,
        startX: Float,
        startY: Float,
        fingerX: Float,
        fingerY: Float,
        returnThresholdPx: Float,
    ): GestureTriggerType? {
        if (!inwardReachedThreshold && peakInward < shortThresholdPx) return null
        val retraction = peakInward - currentInward
        if (retraction < returnThresholdPx) return null
        val along = alongDelta(fingerX - startX, fingerY - startY, side)
        if (abs(along) > shortThresholdPx * 0.75f && abs(along) > retraction * 1.2f) return null
        return GestureTriggerType.SHORT_SWIPE_IN_AND_BACK
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
        val angleDegrees = Math.toDegrees(atan(-dy.toDouble() / inward.toDouble())).toFloat()
        val direction = angleConfig.resolveDirection(angleDegrees) ?: return null
        if (distancePx < shortThresholdPx) return null
        val long = distancePx >= longThresholdPx
        return direction.toTrigger(long)
    }
}
