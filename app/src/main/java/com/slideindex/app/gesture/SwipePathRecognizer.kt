package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
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

    fun onTouchDown(rawX: Float, rawY: Float) {
        startRawX = rawX
        startRawY = rawY
        startTime = System.currentTimeMillis()
        tracking = true
        longPressTriggered = false
    }

    fun gestureStartRawX(): Float = startRawX

    fun gestureStartRawY(): Float = startRawY

    fun gestureDistance(rawX: Float, rawY: Float): Float {
        if (!tracking) return 0f
        return hypot(rawX - startRawX, rawY - startRawY)
    }

    fun isWithinLenientTapSlop(rawX: Float, rawY: Float): Boolean =
        gestureDistance(rawX, rawY) < TAP_LENIENT_SLOP_DP * density

    fun onTouchMove(rawX: Float, rawY: Float) {
        if (!tracking) return
        val elapsed = System.currentTimeMillis() - startTime
        if (!longPressTriggered && elapsed >= LONG_PRESS_MS) {
            val dist = hypot(rawX - startRawX, rawY - startRawY)
            if (dist < TAP_SLOP_DP * density) {
                longPressTriggered = true
            }
        }
    }

    fun classifyOnUp(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): SwipeClassification? {
        if (!tracking) return null
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = when (side) {
            PanelSide.LEFT -> dx
            PanelSide.RIGHT -> -dx
        }
        val distance = hypot(inward.toDouble(), dy.toDouble()).toFloat()
        val elapsed = System.currentTimeMillis() - startTime
        val tapSlop = TAP_SLOP_DP * density * options.tapSlopMultiplier

        val trigger = when {
            longPressTriggered && distance < tapSlop * 2 -> {
                if (distance >= LONG_DISTANCE_DP * density) GestureTriggerType.LONG_LONG_PRESS
                else GestureTriggerType.SHORT_LONG_PRESS
            }
            options.preferSingleTap && !longPressTriggered &&
                distance < tapSlop && elapsed < options.tapMaxMs -> {
                GestureTriggerType.SHORT_SINGLE_TAP
            }
            distance < TAP_SLOP_DP * density -> {
                if (elapsed < TAP_MAX_MS) GestureTriggerType.SHORT_SINGLE_TAP
                else null
            }
            else -> directionTrigger(inward, dy, distance)
        }
        reset()
        return trigger?.let { SwipeClassification(it, inward, dy) }
    }

    fun isVerticalDominant(rawX: Float, rawY: Float): Boolean {
        if (!tracking) return false
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = when (side) {
            PanelSide.LEFT -> dx
            PanelSide.RIGHT -> -dx
        }
        if (hypot(inward.toDouble(), dy.toDouble()) < INDEX_ENTER_DP * density) return false
        return kotlin.math.abs(dy) > kotlin.math.abs(inward) * VERTICAL_DOMINANCE_RATIO
    }

    fun verticalDirection(rawY: Float): GestureTriggerType? {
        if (!tracking) return null
        val dy = rawY - startRawY
        if (kotlin.math.abs(dy) < INDEX_ENTER_DP * density) return null
        return if (dy < 0) GestureTriggerType.SHORT_SWIPE_UP else GestureTriggerType.SHORT_SWIPE_DOWN
    }

    fun reset() {
        tracking = false
        longPressTriggered = false
    }

    private fun directionTrigger(inward: Float, dy: Float, distance: Float): GestureTriggerType? {
        val long = distance >= LONG_DISTANCE_DP * density
        if (inward <= 0f) return null
        val angle = Math.toDegrees(kotlin.math.atan2(-dy.toDouble(), inward.toDouble()))
        val bucket = when {
            angle in -22.5..22.5 -> if (long) GestureTriggerType.LONG_SWIPE_IN else GestureTriggerType.SHORT_SWIPE_IN
            angle in 22.5..67.5 -> if (long) GestureTriggerType.LONG_SWIPE_UP_RIGHT else GestureTriggerType.SHORT_SWIPE_UP_RIGHT
            angle in -67.5..-22.5 -> if (long) GestureTriggerType.LONG_SWIPE_DOWN_RIGHT else GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT
            angle in 67.5..112.5 -> if (long) GestureTriggerType.LONG_SWIPE_UP else GestureTriggerType.SHORT_SWIPE_UP
            angle in -112.5..-67.5 -> if (long) GestureTriggerType.LONG_SWIPE_DOWN else GestureTriggerType.SHORT_SWIPE_DOWN
            else -> null
        }
        return bucket
    }

    companion object {
        private const val LONG_DISTANCE_DP = 120f
        private const val TAP_SLOP_DP = 12f
        private const val TAP_LENIENT_SLOP_DP = 36f
        private const val INDEX_ENTER_DP = 24f
        private const val TAP_MAX_MS = 220L
        private const val TAP_LENIENT_MAX_MS = 450L
        private const val LONG_PRESS_MS = 450L
        private const val VERTICAL_DOMINANCE_RATIO = 1.2f
    }
}
