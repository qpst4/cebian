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
        /**
         * true 时内滑分量按"相对 start（转向锚点）"计算，用于组合第二段；
         * false 时按"距屏幕边缘的绝对深度"计算，用于第一段（触钮的滑出距离语义）。
         */
        anchorRelativeInward: Boolean = false,
    ): Float {
        val absoluteInwardSlide = when (side) {
            PanelSide.LEFT -> fingerX - stripBounds.left
            PanelSide.RIGHT -> stripBounds.right - fingerX
            PanelSide.BOTTOM -> stripBounds.bottom - fingerY
            PanelSide.TOP -> fingerY - stripBounds.top
        }
        val inwardSlide = if (anchorRelativeInward) {
            inwardDelta(fingerX - startX, fingerY - startY, side)
        } else {
            absoluteInwardSlide
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
        stripBounds: RectF,
        inwardReachedThreshold: Boolean,
        currentInward: Float,
        shortThresholdPx: Float,
        longThresholdPx: Float,
        gestureStartX: Float,
        gestureStartY: Float,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        turnThresholdPx: Float,
        angle: GestureAngle,
        longFromSecondSegmentOnly: Boolean = false,
    ): GestureTriggerType? {
        if (!inwardReachedThreshold) return null
        if (currentInward < shortThresholdPx * 0.45f) return null
        val overallDirection = resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = gestureStartX,
            startY = gestureStartY,
            fingerX = fingerX,
            fingerY = fingerY,
            angle = angle,
        ) ?: return null
        // 整体轨迹仍在侧滑扇形内时，始终保持侧滑，不升级为 L 手势。
        // 悬停组合模式已确认第一段内滑，第二段单独判向，不受整体仍偏内滑影响。
        if (!longFromSecondSegmentOnly && overallDirection == SwipeDirection.IN) return null
        val secondSegmentDirection = resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = anchorX,
            startY = anchorY,
            fingerX = fingerX,
            fingerY = fingerY,
            angle = angle,
        ) ?: return null
        if (secondSegmentDirection == SwipeDirection.IN) return null
        val secondSegmentInward = inwardDelta(fingerX - anchorX, fingerY - anchorY, side).coerceAtLeast(0f)
        val secondSegmentAlong = alongDelta(fingerX - anchorX, fingerY - anchorY, side)
        // 第二段须以沿边位移为主；仍在斜向推进时视为同一段侧滑/斜滑。
        if (secondSegmentInward >= turnThresholdPx * 0.35f &&
            abs(secondSegmentAlong) < secondSegmentInward * 1.2f
        ) {
            return null
        }
        val secondSegmentDistance = measureTriggerDistance(
            side = side,
            direction = secondSegmentDirection,
            startX = anchorX,
            startY = anchorY,
            fingerX = fingerX,
            fingerY = fingerY,
            stripBounds = stripBounds,
            anchorRelativeInward = true,
        )
        if (secondSegmentDistance < turnThresholdPx) return null
        val alongFromStart = alongDelta(fingerX - gestureStartX, fingerY - gestureStartY, side)
        val totalDistance = hypot(currentInward.toDouble(), alongFromStart.toDouble()).toFloat()
        val isLong = if (longFromSecondSegmentOnly) {
            secondSegmentDistance >= longThresholdPx
        } else {
            currentInward >= longThresholdPx || totalDistance >= longThresholdPx
        }
        return when (secondSegmentDirection) {
            SwipeDirection.UP, SwipeDirection.UP_RIGHT -> {
                if (isLong) GestureTriggerType.LONG_SWIPE_IN_UP else GestureTriggerType.SHORT_SWIPE_IN_UP
            }
            SwipeDirection.DOWN, SwipeDirection.DOWN_RIGHT -> {
                if (isLong) GestureTriggerType.LONG_SWIPE_IN_DOWN else GestureTriggerType.SHORT_SWIPE_IN_DOWN
            }
            SwipeDirection.IN -> null
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
        /** 当前内距小于等于该值（回到起手位置/屏幕边缘附近）视为撤销，不判折返。 */
        cancelInwardPx: Float = 0f,
    ): GestureTriggerType? {
        if (!inwardReachedThreshold && peakInward < shortThresholdPx) return null
        val retraction = peakInward - currentInward
        if (retraction < returnThresholdPx) return null
        if (currentInward <= cancelInwardPx) return null
        val along = alongDelta(fingerX - startX, fingerY - startY, side)
        if (abs(along) > shortThresholdPx * 0.75f && abs(along) > retraction * 1.2f) return null
        return GestureTriggerType.SHORT_SWIPE_IN_AND_BACK
    }

    /**
     * 沿边首段（上/下）后的第二段向内侧滑：次段必须以向内为主且走够 [turnThresholdPx]。
     * 用于「先上滑再向内」「先下滑再向内」组合；斜滑/自然弧线不会升级为组合。
     */
    fun resolveAlongToInwardTrigger(
        side: PanelSide,
        firstDirection: SwipeDirection,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        turnThresholdPx: Float,
        longThresholdPx: Float,
    ): GestureTriggerType? {
        if (firstDirection != SwipeDirection.UP && firstDirection != SwipeDirection.DOWN) return null
        val secondInward = inwardDelta(fingerX - anchorX, fingerY - anchorY, side).coerceAtLeast(0f)
        if (secondInward < turnThresholdPx) return null
        val secondAlong = abs(alongDelta(fingerX - anchorX, fingerY - anchorY, side))
        // 次段必须以内滑为主：向内与沿边分量接近时按斜滑处理。
        if (secondAlong > secondInward * ALONG_TURN_MAX_ALONG_RATIO) return null
        val isLong = secondInward >= longThresholdPx
        return when (firstDirection) {
            SwipeDirection.UP ->
                if (isLong) GestureTriggerType.LONG_SWIPE_UP_IN else GestureTriggerType.SHORT_SWIPE_UP_IN
            else ->
                if (isLong) GestureTriggerType.LONG_SWIPE_DOWN_IN else GestureTriggerType.SHORT_SWIPE_DOWN_IN
        }
    }

    /**
     * 沿边首段（上/下）的折返：相对峰值回缩 ≥ [returnThresholdPx]，
     * 回到手势起点附近（[cancelProgressPx] 以内）视为撤销。
     */
    fun resolveAlongReturnTrigger(
        side: PanelSide,
        firstDirection: SwipeDirection,
        startX: Float,
        startY: Float,
        peakProgress: Float,
        fingerX: Float,
        fingerY: Float,
        returnThresholdPx: Float,
        cancelProgressPx: Float,
        turnThresholdPx: Float,
    ): GestureTriggerType? {
        if (firstDirection != SwipeDirection.UP && firstDirection != SwipeDirection.DOWN) return null
        val alongFromStart = alongDelta(fingerX - startX, fingerY - startY, side)
        val progress = if (firstDirection == SwipeDirection.UP) -alongFromStart else alongFromStart
        val retraction = peakProgress - progress
        if (retraction < returnThresholdPx) return null
        if (progress <= cancelProgressPx) return null
        // 若这段里已经明显向内侧推进，说明是斜滑而非折返。
        val inward = inwardDelta(fingerX - startX, fingerY - startY, side).coerceAtLeast(0f)
        if (inward >= turnThresholdPx && inward > retraction * INWARD_RETURN_MAX_RATIO) return null
        return if (firstDirection == SwipeDirection.UP) {
            GestureTriggerType.SHORT_SWIPE_UP_AND_BACK
        } else {
            GestureTriggerType.SHORT_SWIPE_DOWN_AND_BACK
        }
    }

    /** 沿边转向内滑时，次段沿边分量相对内滑分量的上限。 */
    private const val ALONG_TURN_MAX_ALONG_RATIO = 0.8f

    /** 沿边折返时，向内分量相对回缩量的上限。 */
    private const val INWARD_RETURN_MAX_RATIO = 1.2f

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
