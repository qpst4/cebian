package com.slideindex.app.gesture

import android.graphics.RectF
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
        val isTriggerConfigured: ((GestureTriggerType) -> Boolean)? = null,
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
    private var gestureAngle = GestureAngle.DEFAULT_LEFT
    private var stripBounds = RectF()
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var inwardReachedShortThreshold = false

    fun applyDistances(shortDp: Float, longDp: Float) {
        shortDistanceDp = shortDp.coerceIn(0f, MAX_DISTANCE_DP)
        val longMin = if (shortDistanceDp <= 0f) {
            MIN_DISTANCE_GAP_DP
        } else {
            shortDistanceDp + MIN_DISTANCE_GAP_DP
        }
        longDistanceDp = longDp.coerceIn(longMin, MAX_DISTANCE_DP)
    }

    fun applyAngles(angles: GestureAngles) {
        gestureAngle = angles.forSide(side)
    }

    fun onTouchDown(rawX: Float, rawY: Float, bounds: RectF) {
        stripBounds = RectF(bounds)
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
        inwardReachedShortThreshold = false
    }

    fun gestureStartRawX(): Float = startRawX

    fun gestureStartRawY(): Float = startRawY

    fun gestureStartUptimeMs(): Long = if (tracking) startTime else 0L

    fun gestureElapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (!tracking) return 0L
        return (nowMs - startTime).coerceAtLeast(0L)
    }

    fun seedExternalAnchor(rawX: Float, rawY: Float) {
        startRawX = rawX
        startRawY = rawY
        lastRawX = rawX
        lastRawY = rawY
        startTime = System.currentTimeMillis()
        tracking = false
        longPressTriggered = false
        movedBeyondLongPressSlop = false
        peakInward = 0f
        peakSwipeDistance = 0f
        peakDy = 0f
    }

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
        val inward = inwardDelta(dx, dy)
        val resolvedDir = resolveDirectionAt(rawX, rawY)
        if (resolvedDir == SwipeDirection.IN && inward >= shortDistanceDp * density) {
            inwardReachedShortThreshold = true
        }
        val swipeDist = resolvedDir?.let { direction ->
            measureDistanceForDirection(rawX, rawY, direction)
        } ?: hypot(inward.toDouble(), dy.toDouble()).toFloat()
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
        return measureDistanceForDirection(
            fingerX = rawX,
            fingerY = rawY,
            direction = resolveDirectionAt(rawX, rawY),
        )
    }

    fun effectiveSwipeDistance(rawX: Float, rawY: Float): Float =
        maxOf(swipeDistance(rawX, rawY), peakSwipeDistance)

    fun currentSwipeDistancePx(): Float = swipeDistance(lastRawX, lastRawY)

    fun currentInwardPx(): Float {
        if (!tracking) return 0f
        val dx = lastRawX - startRawX
        val dy = lastRawY - startRawY
        return inwardDelta(dx, dy).coerceAtLeast(0f)
    }

    fun currentEdgeOffsetPx(): Float {
        if (!tracking) return 0f
        return when (side) {
            PanelSide.LEFT, PanelSide.RIGHT -> lastRawY - startRawY
            PanelSide.BOTTOM, PanelSide.TOP -> lastRawX - startRawX
        }
    }

    fun movementPxFromStart(): Float {
        if (!tracking) return 0f
        return hypot(
            (lastRawX - startRawX).toDouble(),
            (lastRawY - startRawY).toDouble(),
        ).toFloat()
    }

    /** 手势提示动画开始跟手的位移门槛（约 12dp），与单击宽松模式无关。 */
    fun gestureHintStartThresholdPx(): Float = TAP_SLOP_DP * density

    /** 与 classifyOnUp 一致：低于此位移仍可能判为单击。 */
    fun tapDisqualifyMovementPx(options: ClassifyOptions = ClassifyOptions.DEFAULT): Float =
        if (options.preferSingleTap) {
            TAP_LENIENT_SLOP_DP * density
        } else {
            TAP_SLOP_DP * density * options.tapSlopMultiplier
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
        val inward = inwardDelta(dx, dy)
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
        inwardReachedShortThreshold = false
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
        val inward = inwardDelta(dx, dy)
        val direction = resolveDirectionAt(rawX, rawY)
        val distance = measureDistanceForDirection(rawX, rawY, direction)
        val elapsed = System.currentTimeMillis() - startTime
        val tapSlop = TAP_SLOP_DP * density * options.tapSlopMultiplier
        val tapDisqualifyPx = if (options.preferSingleTap) {
            TAP_LENIENT_SLOP_DP * density
        } else {
            TAP_SLOP_DP * density
        }
        val movementPx = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val movedBeyondTap = if (options.preferSingleTap) {
            movementPx >= tapDisqualifyPx
        } else {
            peakSwipeDistance >= tapDisqualifyPx
        }
        val trigger = when {
            longPressTriggered && distance < tapSlop * 2 -> {
                if (distance >= longDistanceDp * density) GestureTriggerType.LONG_LONG_PRESS
                else GestureTriggerType.SHORT_LONG_PRESS
            }
            !partial && options.preferSingleTap && !longPressTriggered &&
                !movedBeyondTap && movementPx < tapSlop && elapsed < options.tapMaxMs -> {
                GestureTriggerType.SHORT_SINGLE_TAP
            }
            !partial && !movedBeyondTap && distance < TAP_SLOP_DP * density -> {
                if (elapsed < TAP_MAX_MS) GestureTriggerType.SHORT_SINGLE_TAP
                else null
            }
            partial && options.preferSingleTap && distance < tapSlop -> null
            partial && distance < shortDistanceDp * density && !longPressTriggered -> null
            else -> directionTrigger(rawX, rawY, distance, options)
        }
        return trigger?.let { SwipeClassification(it, inward, dy) }
    }

    private fun inwardDelta(dx: Float, dy: Float = 0f): Float =
        SwipePathGeometry.inwardDelta(dx, dy, side)

    private fun directionTrigger(
        rawX: Float,
        rawY: Float,
        distance: Float,
        options: ClassifyOptions,
    ): GestureTriggerType? {
        val corner = SwipePathGeometry.resolveCornerSwipeTrigger(
            side = side,
            inwardReachedThreshold = inwardReachedShortThreshold,
            currentInward = inwardDelta(rawX - startRawX, rawY - startRawY),
            shortThresholdPx = shortDistanceDp * density,
            longThresholdPx = longDistanceDp * density,
            startX = startRawX,
            startY = startRawY,
            fingerX = rawX,
            fingerY = rawY,
            turnThresholdPx = TURN_SLOP_DP * density,
        )
        if (corner != null) {
            val filter = options.isTriggerConfigured
            if (filter == null) {
                return corner
            }
            val counterpart = when (corner) {
                GestureTriggerType.SHORT_SWIPE_IN_UP -> GestureTriggerType.LONG_SWIPE_IN_UP
                GestureTriggerType.LONG_SWIPE_IN_UP -> GestureTriggerType.SHORT_SWIPE_IN_UP
                GestureTriggerType.SHORT_SWIPE_IN_DOWN -> GestureTriggerType.LONG_SWIPE_IN_DOWN
                GestureTriggerType.LONG_SWIPE_IN_DOWN -> GestureTriggerType.SHORT_SWIPE_IN_DOWN
                else -> null
            }
            val thisConfigured = filter(corner)
            val counterpartConfigured = counterpart?.let { filter(it) } ?: false
            if (thisConfigured || counterpartConfigured) {
                return corner
            }
        }

        return SwipePathGeometry.classifySwipeTrigger(
            side = side,
            stripBounds = stripBounds,
            startX = startRawX,
            startY = startRawY,
            fingerX = rawX,
            fingerY = rawY,
            shortThresholdPx = shortDistanceDp * density,
            longThresholdPx = longDistanceDp * density,
            angle = gestureAngle,
        )
    }

    fun currentSwipeDirection(): SwipeDirection? {
        if (!tracking) return null
        return resolveDirectionAt(lastRawX, lastRawY)
    }

    private fun resolveDirectionAt(fingerX: Float, fingerY: Float): SwipeDirection? =
        SwipePathGeometry.resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = startRawX,
            startY = startRawY,
            fingerX = fingerX,
            fingerY = fingerY,
            angle = gestureAngle,
        )

    private fun measureDistanceForDirection(
        fingerX: Float,
        fingerY: Float,
        direction: SwipeDirection?,
    ): Float {
        val resolved = direction ?: return hypot(
            inwardDelta(fingerX - startRawX, fingerY - startRawY).toDouble(),
            0.0,
        ).toFloat()
        return SwipePathGeometry.measureTriggerDistance(
            side = side,
            direction = resolved,
            startX = startRawX,
            startY = startRawY,
            fingerX = fingerX,
            fingerY = fingerY,
            stripBounds = stripBounds,
        )
    }

    private fun directionVector(inward: Float, dy: Float): Pair<Float, Float> {
        if (peakInward > 0f && peakSwipeDistance > 0f) {
            return peakInward to peakDy
        }
        return inward to dy
    }

    private fun resolveDirection(inward: Float, dy: Float): SwipeDirection? {
        val (inw, d) = directionVector(inward, dy)
        return SwipePathGeometry.resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = startRawX,
            startY = startRawY,
            fingerX = startRawX + inw,
            fingerY = startRawY + d,
            angle = gestureAngle,
        )
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
        const val TURN_SLOP_DP = 32f
        private const val INDEX_ENTER_DP = 24f
        private const val TAP_MAX_MS = 220L
        private const val TAP_LENIENT_MAX_MS = 450L
        private const val VERTICAL_DOMINANCE_RATIO = 1.2f
    }
}
