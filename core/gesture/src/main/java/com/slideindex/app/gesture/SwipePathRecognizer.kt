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
    private var shortThresholdAnchorX = 0f
    private var shortThresholdAnchorY = 0f
    private var hasShortThresholdAnchor = false
    private var slotHoverDirection: SwipeDirection? = null
    private var slotHoverAnchorX = 0f
    private var slotHoverAnchorY = 0f
    private var slotHoverSatisfiedAnchorX = 0f
    private var slotHoverSatisfiedAnchorY = 0f
    private var slotHoverHoldStartMs = 0L
    private var slotHoverPeakDirectionDistance = 0f
    private var slotHoverTracking = false
    private var slotHoverSatisfied = false
    private var slotHoverCancelled = false
    private var slotHoverJustSatisfied = false
    private var compoundGateAnchorX = 0f
    private var compoundGateAnchorY = 0f
    private var compoundGateHoldStartMs = 0L
    private var compoundGatePeakDirectionDistance = 0f
    private var compoundGateTracking = false
    private var compoundGateCancelled = false
    private var compoundAnchorX = 0f
    private var compoundAnchorY = 0f
    private var compoundModeArmed = false
    private var hoverDurationMs = DEFAULT_HOVER_DURATION_MS
    private var inwardHoverCompoundEnabled = true
    private var lCornerHoverGateRequired = false
    private var returnSwipeHoverGateRequired = false
    private var slotHoverConfigured: (SwipeDirection) -> Boolean = { false }

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

    fun applyHoverSettings(durationMs: Long, inwardCompoundEnabled: Boolean) {
        hoverDurationMs = durationMs.coerceIn(HOVER_DURATION_MIN_MS, HOVER_DURATION_MAX_MS)
        inwardHoverCompoundEnabled = inwardCompoundEnabled
    }

    fun applyCompoundGestureGate(options: ClassifyOptions) {
        lCornerHoverGateRequired = isLCornerConfigured(options)
        returnSwipeHoverGateRequired = isReturnSwipeConfigured(options)
        val filter = options.isTriggerConfigured
        slotHoverConfigured = fun(direction: SwipeDirection): Boolean {
            val hoverTrigger = direction.toHoverTrigger() ?: return false
            return filter == null || filter(hoverTrigger)
        }
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
        resetHoverState()
    }

    private fun resetHoverState() {
        inwardReachedShortThreshold = false
        shortThresholdAnchorX = 0f
        shortThresholdAnchorY = 0f
        hasShortThresholdAnchor = false
        resetSlotHoverState()
        resetCompoundGateState()
        compoundAnchorX = 0f
        compoundAnchorY = 0f
        compoundModeArmed = false
        lCornerHoverGateRequired = false
        returnSwipeHoverGateRequired = false
        slotHoverConfigured = { false }
    }

    private fun resetSlotHoverState() {
        slotHoverDirection = null
        slotHoverAnchorX = 0f
        slotHoverAnchorY = 0f
        slotHoverSatisfiedAnchorX = 0f
        slotHoverSatisfiedAnchorY = 0f
        slotHoverHoldStartMs = 0L
        slotHoverPeakDirectionDistance = 0f
        slotHoverTracking = false
        slotHoverSatisfied = false
        slotHoverCancelled = false
        slotHoverJustSatisfied = false
    }

    private fun resetCompoundGateState() {
        compoundGateAnchorX = 0f
        compoundGateAnchorY = 0f
        compoundGateHoldStartMs = 0L
        compoundGatePeakDirectionDistance = 0f
        compoundGateTracking = false
        compoundGateCancelled = false
    }

    private fun cancelSlotHover() {
        slotHoverCancelled = true
        slotHoverTracking = false
        slotHoverSatisfied = false
    }

    private fun cancelCompoundGate() {
        compoundGateCancelled = true
        compoundGateTracking = false
    }

    private fun disarmCompoundMode() {
        compoundModeArmed = false
        compoundAnchorX = 0f
        compoundAnchorY = 0f
        cancelCompoundGate()
    }

    private fun passedLongInwardThreshold(rawX: Float, rawY: Float): Boolean {
        val directionDistance = measureDistanceForDirection(rawX, rawY, SwipeDirection.IN)
        return directionDistance >= longDistanceDp * density
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
        resetHoverState()
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
            if (!inwardReachedShortThreshold) {
                shortThresholdAnchorX = rawX
                shortThresholdAnchorY = rawY
                hasShortThresholdAnchor = true
            }
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
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (!movedBeyondLongPressSlop && dist >= TAP_SLOP_DP * density) {
            movedBeyondLongPressSlop = true
        }
        updateSlotHoverState(rawX, rawY, resolvedDir, swipeDist)
        updateCompoundGateState(rawX, rawY, resolvedDir, swipeDist)
    }

    private fun updateSlotHoverState(
        rawX: Float,
        rawY: Float,
        resolvedDir: SwipeDirection?,
        swipeDist: Float,
    ) {
        val shortPx = shortDistanceDp * density
        val longPx = longDistanceDp * density

        if (resolvedDir != null && swipeDist >= shortPx && slotHoverDirection == null) {
            if (slotHoverConfigured(resolvedDir)) {
                slotHoverDirection = resolvedDir
                slotHoverAnchorX = rawX
                slotHoverAnchorY = rawY
                slotHoverPeakDirectionDistance = measureDistanceForDirection(rawX, rawY, resolvedDir)
                slotHoverHoldStartMs = 0L
                slotHoverTracking = true
            }
        }

        if (slotHoverCancelled || !slotHoverTracking || slotHoverDirection == null) return

        val directionDistance = measureDistanceForDirection(rawX, rawY, slotHoverDirection)
        if (directionDistance >= longPx) {
            cancelSlotHover()
            return
        }

        if (!slotHoverSatisfied) {
            if (directionDistance > slotHoverPeakDirectionDistance + DIRECTION_PROGRESS_EPSILON_DP * density) {
                val advanced = directionDistance - slotHoverPeakDirectionDistance
                slotHoverPeakDirectionDistance = directionDistance
                slotHoverAnchorX = rawX
                slotHoverAnchorY = rawY
                if (slotHoverHoldStartMs == 0L || advanced >= HOVER_HOLD_RESET_DP * density) {
                    slotHoverHoldStartMs = 0L
                }
                return
            }

            val anchorDist = hypot(
                (rawX - slotHoverAnchorX).toDouble(),
                (rawY - slotHoverAnchorY).toDouble(),
            ).toFloat()
            if (anchorDist >= HOVER_SLOP_DP * density) {
                cancelSlotHover()
                return
            }

            val now = System.currentTimeMillis()
            if (slotHoverHoldStartMs == 0L) {
                slotHoverHoldStartMs = now
            }
            if (now - slotHoverHoldStartMs < hoverDurationMs) return

            slotHoverSatisfied = true
            slotHoverJustSatisfied = true
            slotHoverSatisfiedAnchorX = rawX
            slotHoverSatisfiedAnchorY = rawY
        }
    }

    private fun updateCompoundGateState(
        rawX: Float,
        rawY: Float,
        resolvedDir: SwipeDirection?,
        swipeDist: Float,
    ) {
        if (passedLongInwardThreshold(rawX, rawY)) {
            if (compoundModeArmed) {
                disarmCompoundMode()
            } else {
                cancelCompoundGate()
            }
            return
        }

        if (compoundModeArmed) return

        val shortPx = shortDistanceDp * density
        val compoundGateRequired = lCornerHoverGateRequired || returnSwipeHoverGateRequired
        if (!compoundGateRequired) return

        if (!compoundGateTracking && resolvedDir == SwipeDirection.IN && swipeDist >= shortPx) {
            compoundGateAnchorX = rawX
            compoundGateAnchorY = rawY
            compoundGatePeakDirectionDistance = measureDistanceForDirection(rawX, rawY, SwipeDirection.IN)
            compoundGateHoldStartMs = 0L
            compoundGateTracking = true
        }

        if (compoundGateCancelled || !compoundGateTracking) return

        val directionDistance = measureDistanceForDirection(rawX, rawY, SwipeDirection.IN)
        if (directionDistance > compoundGatePeakDirectionDistance + DIRECTION_PROGRESS_EPSILON_DP * density) {
            val advanced = directionDistance - compoundGatePeakDirectionDistance
            compoundGatePeakDirectionDistance = directionDistance
            compoundGateAnchorX = rawX
            compoundGateAnchorY = rawY
            if (compoundGateHoldStartMs == 0L || advanced >= HOVER_HOLD_RESET_DP * density) {
                compoundGateHoldStartMs = 0L
            }
            return
        }

        val anchorDist = hypot(
            (rawX - compoundGateAnchorX).toDouble(),
            (rawY - compoundGateAnchorY).toDouble(),
        ).toFloat()
        if (anchorDist >= HOVER_SLOP_DP * density) {
            cancelCompoundGate()
            return
        }

        val now = System.currentTimeMillis()
        if (compoundGateHoldStartMs == 0L) {
            compoundGateHoldStartMs = now
        }
        if (now - compoundGateHoldStartMs < hoverDurationMs) return

        compoundModeArmed = true
        compoundAnchorX = rawX
        compoundAnchorY = rawY
    }

    fun consumeHoverJustSatisfied(): Boolean {
        if (!slotHoverJustSatisfied) return false
        slotHoverJustSatisfied = false
        return true
    }

    fun activeHoverTrigger(): GestureTriggerType? =
        slotHoverDirection?.toHoverTrigger()

    fun isHoverSatisfied(): Boolean = slotHoverSatisfied

    fun hoverHoldRemainingMs(): Long? {
        if (slotHoverCancelled || !slotHoverTracking || slotHoverSatisfied || compoundModeArmed) return null
        if (slotHoverDirection == null || slotHoverHoldStartMs == 0L) return null
        val elapsed = System.currentTimeMillis() - slotHoverHoldStartMs
        return (hoverDurationMs - elapsed).coerceAtLeast(0L)
    }

    fun isCompoundModeArmed(): Boolean = compoundModeArmed

    fun hasMovedFromCompoundAnchor(rawX: Float, rawY: Float): Boolean =
        compoundModeArmed && movedFromCompoundAnchor(rawX, rawY)

    private fun movedFromCompoundAnchor(rawX: Float, rawY: Float): Boolean {
        return hypot(
            (rawX - compoundAnchorX).toDouble(),
            (rawY - compoundAnchorY).toDouble(),
        ) >= HOVER_SLOP_DP * density
    }

    private fun movedFromSlotHoverAnchor(rawX: Float, rawY: Float): Boolean {
        return hypot(
            (rawX - slotHoverSatisfiedAnchorX).toDouble(),
            (rawY - slotHoverSatisfiedAnchorY).toDouble(),
        ) >= HOVER_SLOP_DP * density
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

    fun hasMetThreshold(
        trigger: GestureTriggerType,
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): Boolean {
        if (!tracking) return false
        if (trigger.isHoverSwipe) return isHoverReady(rawX, rawY)
        if (shouldDeferBaseSwipeForHover(rawX, rawY, options)) {
            val baseTrigger = slotHoverDirection?.toBaseShortTrigger()
            if (trigger == baseTrigger && !slotHoverSatisfied && !compoundModeArmed) return false
            if (trigger == GestureTriggerType.SHORT_SWIPE_IN &&
                isCompoundGateWaiting() &&
                !compoundModeArmed
            ) {
                return false
            }
        }
        if (trigger.isCornerSwipe && compoundModeArmed) {
            val distance = compoundSecondSegmentDistance(rawX, rawY)
            return if (trigger.isLongDistance) {
                distance >= longDistanceDp * density
            } else {
                distance >= shortDistanceDp * density
            }
        }
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
        resetHoverState()
    }

    private fun isHoverReady(rawX: Float, rawY: Float): Boolean {
        if (!slotHoverSatisfied || slotHoverCancelled || slotHoverDirection == null) return false
        return !movedFromSlotHoverAnchor(rawX, rawY)
    }

    private fun computeClassification(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions,
        partial: Boolean,
    ): SwipeClassification? {
        applyCompoundGestureGate(options)
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
            partial && distance < shortDistanceDp * density && !longPressTriggered -> {
                if (isReturnSwipeActive(rawX, rawY, options)) {
                    directionTrigger(rawX, rawY, distance, options, partial)
                } else {
                    null
                }
            }
            else -> directionTrigger(rawX, rawY, distance, options, partial)
        }
        return trigger?.let { SwipeClassification(it, inward, dy) }
    }

    private fun inwardDelta(dx: Float, dy: Float = 0f): Float =
        SwipePathGeometry.inwardDelta(dx, dy, side)

    fun isReturnSwipeActive(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): Boolean {
        if (!tracking) return false
        if (returnSwipeHoverGateRequired && !compoundModeArmed) return false
        return SwipePathGeometry.resolveReturnSwipeTrigger(
            side = side,
            inwardReachedThreshold = inwardReachedShortThreshold,
            peakInward = peakInward,
            currentInward = inwardDelta(rawX - startRawX, rawY - startRawY),
            shortThresholdPx = shortDistanceDp * density,
            startX = startRawX,
            startY = startRawY,
            fingerX = rawX,
            fingerY = rawY,
            returnThresholdPx = RETURN_SLOP_DP * density,
        ) != null
    }

    private fun directionTrigger(
        rawX: Float,
        rawY: Float,
        distance: Float,
        options: ClassifyOptions,
        partial: Boolean,
    ): GestureTriggerType? {
        if (!returnSwipeHoverGateRequired || compoundModeArmed) {
            val returnSwipe = SwipePathGeometry.resolveReturnSwipeTrigger(
                side = side,
                inwardReachedThreshold = inwardReachedShortThreshold,
                peakInward = peakInward,
                currentInward = inwardDelta(rawX - startRawX, rawY - startRawY),
                shortThresholdPx = shortDistanceDp * density,
                startX = startRawX,
                startY = startRawY,
                fingerX = rawX,
                fingerY = rawY,
                returnThresholdPx = RETURN_SLOP_DP * density,
            )
            if (returnSwipe != null) {
                val filter = options.isTriggerConfigured
                if (filter == null || filter(returnSwipe)) {
                    return returnSwipe
                }
            }
        }

        if (compoundModeArmed && movedFromCompoundAnchor(rawX, rawY)) {
            val compoundCorner = SwipePathGeometry.resolveCornerSwipeTrigger(
                side = side,
                stripBounds = stripBounds,
                inwardReachedThreshold = true,
                currentInward = inwardDelta(rawX - startRawX, rawY - startRawY),
                shortThresholdPx = shortDistanceDp * density,
                longThresholdPx = longDistanceDp * density,
                gestureStartX = startRawX,
                gestureStartY = startRawY,
                anchorX = compoundAnchorX,
                anchorY = compoundAnchorY,
                fingerX = rawX,
                fingerY = rawY,
                turnThresholdPx = TURN_SLOP_DP * density,
                angle = gestureAngle,
                longFromSecondSegmentOnly = true,
            )
            if (compoundCorner != null && isCornerConfigured(compoundCorner, options)) {
                return compoundCorner
            }
        }

        resolveHoverTrigger(rawX, rawY, options, partial)?.let { return it }

        if (partial && shouldDeferBaseSwipeForHover(rawX, rawY, options)) {
            return null
        }

        val corner = if (hasShortThresholdAnchor && !compoundModeArmed && !lCornerHoverGateRequired) {
            SwipePathGeometry.resolveCornerSwipeTrigger(
                side = side,
                stripBounds = stripBounds,
                inwardReachedThreshold = inwardReachedShortThreshold,
                currentInward = inwardDelta(rawX - startRawX, rawY - startRawY),
                shortThresholdPx = shortDistanceDp * density,
                longThresholdPx = longDistanceDp * density,
                gestureStartX = startRawX,
                gestureStartY = startRawY,
                anchorX = shortThresholdAnchorX,
                anchorY = shortThresholdAnchorY,
                fingerX = rawX,
                fingerY = rawY,
                turnThresholdPx = TURN_SLOP_DP * density,
                angle = gestureAngle,
            )
        } else {
            null
        }
        if (corner != null) {
            if (isCornerConfigured(corner, options)) {
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

    private fun isLCornerConfigured(options: ClassifyOptions): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        return filter(GestureTriggerType.SHORT_SWIPE_IN_UP) ||
            filter(GestureTriggerType.LONG_SWIPE_IN_UP) ||
            filter(GestureTriggerType.SHORT_SWIPE_IN_DOWN) ||
            filter(GestureTriggerType.LONG_SWIPE_IN_DOWN)
    }

    private fun isReturnSwipeConfigured(options: ClassifyOptions): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        return filter(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK)
    }

    fun compoundSecondSegmentTurnThresholdPx(): Float = TURN_SLOP_DP * density

    fun compoundSecondSegmentDistance(rawX: Float, rawY: Float): Float {
        if (!compoundModeArmed) return 0f
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = compoundAnchorX,
            startY = compoundAnchorY,
            fingerX = rawX,
            fingerY = rawY,
            angle = gestureAngle,
        ) ?: return hypot(
            (rawX - compoundAnchorX).toDouble(),
            (rawY - compoundAnchorY).toDouble(),
        ).toFloat()
        return SwipePathGeometry.measureTriggerDistance(
            side = side,
            direction = direction,
            startX = compoundAnchorX,
            startY = compoundAnchorY,
            fingerX = rawX,
            fingerY = rawY,
            stripBounds = stripBounds,
        )
    }

    private fun isCornerConfigured(
        corner: GestureTriggerType,
        options: ClassifyOptions,
    ): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        val counterpart = when (corner) {
            GestureTriggerType.SHORT_SWIPE_IN_UP -> GestureTriggerType.LONG_SWIPE_IN_UP
            GestureTriggerType.LONG_SWIPE_IN_UP -> GestureTriggerType.SHORT_SWIPE_IN_UP
            GestureTriggerType.SHORT_SWIPE_IN_DOWN -> GestureTriggerType.LONG_SWIPE_IN_DOWN
            GestureTriggerType.LONG_SWIPE_IN_DOWN -> GestureTriggerType.SHORT_SWIPE_IN_DOWN
            else -> null
        }
        return filter(corner) || (counterpart?.let { filter(it) } ?: false)
    }

    private fun isHoverSlotConfigured(options: ClassifyOptions): Boolean {
        val hoverDirection = slotHoverDirection ?: return false
        val hoverTrigger = hoverDirection.toHoverTrigger() ?: return false
        val filter = options.isTriggerConfigured ?: return true
        return filter(hoverTrigger)
    }

    private fun isSlotHoverTrackingActive(): Boolean =
        slotHoverTracking && !slotHoverCancelled && slotHoverDirection != null

    private fun isCompoundGateWaiting(): Boolean =
        compoundGateTracking && !compoundGateCancelled && !compoundModeArmed

    private fun isCompoundGateHoldingAtAnchor(): Boolean =
        isCompoundGateWaiting() && compoundGateHoldStartMs != 0L

    private fun shouldDeferBaseSwipeForHover(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions,
    ): Boolean {
        if (isSlotHoverTrackingActive() && isHoverSlotConfigured(options)) return true
        if (!isCompoundGateHoldingAtAnchor()) return false
        if (!(lCornerHoverGateRequired || returnSwipeHoverGateRequired)) return false
        val inwardDistance = measureDistanceForDirection(rawX, rawY, SwipeDirection.IN)
        return inwardDistance < longDistanceDp * density
    }

    private fun resolveHoverTrigger(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions,
        partial: Boolean,
    ): GestureTriggerType? {
        if (!isHoverReady(rawX, rawY)) return null
        // 组合已就绪但尚未离开悬停点：松手仍走槽位悬停；离开锚点后才交给 L/回弹第二段。
        if (compoundModeArmed && movedFromCompoundAnchor(rawX, rawY)) return null
        val hoverTrigger = slotHoverDirection?.toHoverTrigger() ?: return null
        val filter = options.isTriggerConfigured
        if (filter != null && !filter(hoverTrigger)) return null
        return hoverTrigger
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
        const val RETURN_SLOP_DP = 16f
        const val HOVER_SLOP_DP = 12f
        const val DIRECTION_PROGRESS_EPSILON_DP = 2f
        const val HOVER_HOLD_RESET_DP = 8f
        const val DEFAULT_HOVER_DURATION_MS = 250L
        const val HOVER_DURATION_MIN_MS = 150L
        const val HOVER_DURATION_MAX_MS = 500L
        private const val INDEX_ENTER_DP = 24f
        private const val TAP_MAX_MS = 220L
        private const val TAP_LENIENT_MAX_MS = 450L
        private const val VERTICAL_DOMINANCE_RATIO = 1.2f
    }
}
