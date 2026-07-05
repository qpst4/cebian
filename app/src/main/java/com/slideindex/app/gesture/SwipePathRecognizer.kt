package com.slideindex.app.gesture

import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.SwipeDirection
import com.slideindex.app.overlay.PanelSide
import kotlin.math.abs
import kotlin.math.hypot

data class SwipeClassification(
    val trigger: GestureTriggerType,
    val inwardDelta: Float,
    val verticalDelta: Float,
)

class SwipePathRecognizer(
    private val side: PanelSide,
    private val density: Float,
) {
    data class ClassifyOptions(
        val tapSlopMultiplier: Float = 1f,
        val tapMaxMs: Long = TAP_MAX_MS,
        val preferSingleTap: Boolean = false,
    ) {
        companion object {
            val DEFAULT = ClassifyOptions()
            val LENIENT_SINGLE_TAP = ClassifyOptions(
                tapSlopMultiplier = TAP_LENIENT_SLOP_DP / TAP_SLOP_DP,
                tapMaxMs = TAP_LENIENT_MAX_MS,
                preferSingleTap = true,
            )
        }
    }

    private var startRawX = 0f
    private var startRawY = 0f
    private var startTime = 0L
    private var tracking = false
    private var longPressTriggered = false
    private var movedBeyondLongPressSlop = false
    private var peakInward = 0f
    private var peakSwipeDistance = 0f
    private var peakDy = 0f
    private var shortDistanceDp = DEFAULT_SHORT_DISTANCE_DP
    private var longDistanceDp = DEFAULT_LONG_DISTANCE_DP
    private var angleConfig = GestureAngleConfig.DEFAULT
    private var lastRawX = 0f
    private var lastRawY = 0f

    fun applyDistances(shortDp: Float, longDp: Float) {
        shortDistanceDp = shortDp.coerceIn(0f, MAX_DISTANCE_DP)
        val longMin = if (shortDistanceDp <= 0f) {
            MIN_DISTANCE_GAP_DP
        } else {
            shortDistanceDp + MIN_DISTANCE_GAP_DP
        }
        longDistanceDp = longDp.coerceIn(longMin, MAX_DISTANCE_DP)
    }

    fun applyAngles(config: GestureAngleConfig) {
        angleConfig = config.normalized()
    }

    fun currentSwipeAngleDegrees(): Float? {
        if (!tracking) return null
        val dx = lastRawX - startRawX
        val dy = lastRawY - startRawY
        val inward = inwardDelta(dx)
        if (inward <= 0f) return null
        return Math.toDegrees(kotlin.math.atan2(-dy.toDouble(), inward.toDouble())).toFloat()
    }

    fun onTouchDown(rawX: Float, rawY: Float) {
        startRawX = rawX
        startRawY = rawY
        lastRawX = rawX
        lastRawY = rawY
        startTime = System.currentTimeMillis()
        tracking = true
        longPressTriggered = false
        movedBeyondLongPressSlop = false
        peakInward = 0f
        peakSwipeDistance = 0f
        peakDy = 0f
    }

    fun gestureStartRawX(): Float = startRawX

    fun gestureStartRawY(): Float = startRawY

    fun gestureDistance(rawX: Float, rawY: Float): Float {
        if (!tracking) return 0f
        return hypot(rawX - startRawX, rawY - startRawY)
    }

    fun isWithinLenientTapSlop(rawX: Float, rawY: Float): Boolean =
        gestureDistance(rawX, rawY) < TAP_LENIENT_SLOP_DP * density

    fun isLongPressArmed(): Boolean = tracking && longPressTriggered

    fun onTouchMove(rawX: Float, rawY: Float) {
        if (!tracking) return
        lastRawX = rawX
        lastRawY = rawY
        recordMovement(rawX, rawY)
        refreshLongPress(rawX, rawY)
    }

    fun refreshLongPress(rawX: Float, rawY: Float) {
        if (!tracking || longPressTriggered || movedBeyondLongPressSlop) return
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= LONG_PRESS_MS) {
            val dist = hypot(rawX - startRawX, rawY - startRawY)
            if (dist < TAP_SLOP_DP * density) {
                longPressTriggered = true
            }
        }
    }

    private fun recordMovement(rawX: Float, rawY: Float) {
        if (!tracking) return
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = inwardDelta(dx)
        val swipeDist = hypot(inward.toDouble(), dy.toDouble()).toFloat()
        if (swipeDist > peakSwipeDistance) {
            peakSwipeDistance = swipeDist
            peakInward = inward
            peakDy = dy
        } else {
            peakInward = maxOf(peakInward, inward)
        }
        if (movedBeyondLongPressSlop) return
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist >= TAP_SLOP_DP * density) {
            movedBeyondLongPressSlop = true
        }
    }

    fun swipeDistance(rawX: Float, rawY: Float): Float {
        if (!tracking) return 0f
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = inwardDelta(dx)
        return hypot(inward.toDouble(), dy.toDouble()).toFloat()
    }

    fun effectiveSwipeDistance(rawX: Float, rawY: Float): Float =
        maxOf(swipeDistance(rawX, rawY), peakSwipeDistance)

    fun currentSwipeDistancePx(): Float = swipeDistance(lastRawX, lastRawY)

    fun currentInwardPx(): Float {
        if (!tracking) return 0f
        val dx = lastRawX - startRawX
        return inwardDelta(dx).coerceAtLeast(0f)
    }

    fun currentEdgeOffsetPx(): Float {
        if (!tracking) return 0f
        return lastRawY - startRawY
    }

    fun lastRawX(): Float = lastRawX

    fun lastRawY(): Float = lastRawY

    fun shortThresholdPx(): Float = shortDistanceDp * density

    fun longThresholdPx(): Float = longDistanceDp * density

    fun disqualifyLongPress() {
        movedBeyondLongPressSlop = true
    }

    fun longPressEligible(): Boolean = tracking && !movedBeyondLongPressSlop

    fun classifyPartial(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): SwipeClassification? {
        if (!tracking) return null
        return computeClassification(rawX, rawY, options, partial = true)
    }

    fun classifyOnUp(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): SwipeClassification? {
        if (!tracking) return null
        val classification = computeClassification(rawX, rawY, options, partial = false)
        reset()
        return classification
    }

    fun hasMetThreshold(trigger: GestureTriggerType, rawX: Float, rawY: Float): Boolean {
        if (!tracking) return false
        val distance = swipeDistance(rawX, rawY)
        return when {
            trigger.isLongPress -> longPressTriggered
            trigger.isSingleTap -> false
            trigger.isLongDistance -> distance >= longDistanceDp * density
            else -> distance >= shortDistanceDp * density
        }
    }

    fun isVerticalDominant(rawX: Float, rawY: Float): Boolean {
        if (!tracking) return false
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = inwardDelta(dx)
        if (hypot(inward.toDouble(), dy.toDouble()) < INDEX_ENTER_DP * density) return false
        return abs(dy) > abs(inward) * VERTICAL_DOMINANCE_RATIO
    }

    fun verticalDirection(rawY: Float): GestureTriggerType? {
        if (!tracking) return null
        val dy = rawY - startRawY
        if (abs(dy) < INDEX_ENTER_DP * density) return null
        return if (dy < 0) GestureTriggerType.SHORT_SWIPE_UP else GestureTriggerType.SHORT_SWIPE_DOWN
    }

    fun reset() {
        tracking = false
        longPressTriggered = false
        movedBeyondLongPressSlop = false
        peakInward = 0f
        peakSwipeDistance = 0f
        peakDy = 0f
    }

    private fun computeClassification(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions,
        partial: Boolean,
    ): SwipeClassification? {
        recordMovement(rawX, rawY)
        refreshLongPress(rawX, rawY)
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = inwardDelta(dx)
        val distance = hypot(inward.toDouble(), dy.toDouble()).toFloat()
        val elapsed = System.currentTimeMillis() - startTime
        val tapSlop = TAP_SLOP_DP * density * options.tapSlopMultiplier

        val movedBeyondTap = peakSwipeDistance >= TAP_SLOP_DP * density ||
            peakSwipeDistance >= shortDistanceDp * density
        val trigger = when {
            longPressTriggered && distance < tapSlop * 2 -> {
                if (distance >= longDistanceDp * density) GestureTriggerType.LONG_LONG_PRESS
                else GestureTriggerType.SHORT_LONG_PRESS
            }
            !partial && options.preferSingleTap && !longPressTriggered &&
                !movedBeyondTap && distance < tapSlop && elapsed < options.tapMaxMs -> {
                GestureTriggerType.SHORT_SINGLE_TAP
            }
            !partial && !movedBeyondTap && distance < TAP_SLOP_DP * density -> {
                if (elapsed < TAP_MAX_MS) GestureTriggerType.SHORT_SINGLE_TAP
                else null
            }
            partial && options.preferSingleTap && distance < tapSlop -> null
            partial && distance < shortDistanceDp * density && !longPressTriggered -> null
            else -> directionTrigger(inward = inward, dy = dy, distance = distance)
        }
        return trigger?.let { SwipeClassification(it, inward, dy) }
    }

    private fun inwardDelta(dx: Float): Float = when (side) {
        PanelSide.LEFT -> dx
        PanelSide.RIGHT -> -dx
    }

    private fun directionTrigger(inward: Float, dy: Float, distance: Float): GestureTriggerType? {
        val (dirInward, dirDy) = directionVector(inward, dy)
        val direction = resolveDirection(dirInward, dirDy) ?: return null
        if (distance < shortDistanceDp * density) return null
        val long = distance >= longDistanceDp * density
        return direction.toTrigger(long)
    }

    fun currentSwipeDirection(): SwipeDirection? {
        if (!tracking) return null
        val dx = lastRawX - startRawX
        val dy = lastRawY - startRawY
        val inward = inwardDelta(dx)
        val (dirInward, dirDy) = directionVector(inward, dy)
        return resolveDirection(dirInward, dirDy)
    }

    /** Prefer the peak swipe vector so release jitter does not change the sector. */
    private fun directionVector(inward: Float, dy: Float): Pair<Float, Float> {
        if (peakInward > 0f && peakSwipeDistance > 0f) {
            return peakInward to peakDy
        }
        return inward to dy
    }

    private fun resolveDirection(inward: Float, dy: Float): SwipeDirection? {
        if (inward <= 0f) return null
        val angle = Math.toDegrees(kotlin.math.atan2(-dy.toDouble(), inward.toDouble())).toFloat()
        return angleConfig.resolveDirection(angle)
    }

    companion object {
        private const val MIN_DISTANCE_DP = 24f
        private const val MAX_DISTANCE_DP = 240f
        private const val MIN_DISTANCE_GAP_DP = 16f

        const val DEFAULT_SHORT_DISTANCE_DP = 60f
        const val DEFAULT_LONG_DISTANCE_DP = 120f
        const val SHORT_DISTANCE_MIN_DP = 0f
        const val SHORT_DISTANCE_MAX_DP = 160f
        const val LONG_DISTANCE_MIN_DP = MIN_DISTANCE_GAP_DP
        const val LONG_DISTANCE_MAX_DP = MAX_DISTANCE_DP
        const val LONG_PRESS_MS = 450L
        private const val TAP_SLOP_DP = 12f
        private const val TAP_LENIENT_SLOP_DP = 36f
        private const val INDEX_ENTER_DP = 24f
        private const val TAP_MAX_MS = 220L
        private const val TAP_LENIENT_MAX_MS = 450L
        private const val VERTICAL_DOMINANCE_RATIO = 1.2f
    }
}
