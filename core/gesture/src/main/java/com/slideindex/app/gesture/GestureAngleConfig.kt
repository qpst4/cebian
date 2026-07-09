package com.slideindex.app.gesture

import kotlin.math.roundToInt

enum class SwipeDirection {
    UP,
    UP_RIGHT,
    IN,
    DOWN_RIGHT,
    DOWN,
    ;

    fun toTrigger(long: Boolean): GestureTriggerType = when (this) {
        UP -> if (long) GestureTriggerType.LONG_SWIPE_UP else GestureTriggerType.SHORT_SWIPE_UP
        UP_RIGHT -> if (long) GestureTriggerType.LONG_SWIPE_UP_RIGHT else GestureTriggerType.SHORT_SWIPE_UP_RIGHT
        IN -> if (long) GestureTriggerType.LONG_SWIPE_IN else GestureTriggerType.SHORT_SWIPE_IN
        DOWN_RIGHT -> if (long) GestureTriggerType.LONG_SWIPE_DOWN_RIGHT else GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT
        DOWN -> if (long) GestureTriggerType.LONG_SWIPE_DOWN else GestureTriggerType.SHORT_SWIPE_DOWN
    }

    companion object {
        fun ordered(): List<SwipeDirection> = listOf(UP, UP_RIGHT, IN, DOWN_RIGHT, DOWN)
    }
}

data class GestureAngleConfig(
    val upDegrees: Float = DEFAULT_UP,
    val upRightDegrees: Float = DEFAULT_UP_RIGHT,
    val inDegrees: Float = DEFAULT_IN,
    val downRightDegrees: Float = DEFAULT_DOWN_RIGHT,
    val downDegrees: Float = DEFAULT_DOWN,
) {
    fun degreesFor(direction: SwipeDirection): Float = when (direction) {
        SwipeDirection.UP -> upDegrees
        SwipeDirection.UP_RIGHT -> upRightDegrees
        SwipeDirection.IN -> inDegrees
        SwipeDirection.DOWN_RIGHT -> downRightDegrees
        SwipeDirection.DOWN -> downDegrees
    }

    fun withDegrees(direction: SwipeDirection, degrees: Float): GestureAngleConfig {
        val clamped = degrees.coerceIn(MIN_DEGREES, MAX_DEGREES)
        val others = SwipeDirection.ordered().filter { it != direction }
        val remaining = (TOTAL_DEGREES - clamped).coerceAtLeast(MIN_DEGREES * others.size)
        val perOther = remaining / others.size
        var updated = copyFor(direction, clamped)
        others.forEach { other ->
            updated = updated.copyFor(other, perOther.coerceIn(MIN_DEGREES, MAX_DEGREES))
        }
        return updated.normalized()
    }

    fun normalized(): GestureAngleConfig {
        val total = totalDegrees()
        if (total == TOTAL_DEGREES) return this
        if (total <= 0f) return DEFAULT
        val scale = TOTAL_DEGREES / total
        var adjusted = copy(
            upDegrees = (upDegrees * scale).roundToDegrees(),
            upRightDegrees = (upRightDegrees * scale).roundToDegrees(),
            inDegrees = (inDegrees * scale).roundToDegrees(),
            downRightDegrees = (downRightDegrees * scale).roundToDegrees(),
            downDegrees = (downDegrees * scale).roundToDegrees(),
        )
        val diff = TOTAL_DEGREES - adjusted.totalDegrees()
        if (diff != 0f) {
            val widest = SwipeDirection.ordered().maxBy { adjusted.degreesFor(it) }
            adjusted = adjusted.copyFor(widest, adjusted.degreesFor(widest) + diff)
        }
        return adjusted
    }

    fun totalDegrees(): Float =
        upDegrees + upRightDegrees + inDegrees + downRightDegrees + downDegrees

    fun orderedSectorWidths(): List<Pair<SwipeDirection, Float>> =
        SwipeDirection.ordered().map { it to degreesFor(it) }

    fun sectorCenterAngles(): List<Pair<SwipeDirection, Float>> {
        var upper = 90f
        return orderedSectorWidths().map { (direction, width) ->
            val center = upper - width / 2f
            upper -= width
            direction to center
        }
    }

    fun sectorBoundaryAngles(): List<Float> {
        var upper = 90f
        val boundaries = mutableListOf(upper)
        orderedSectorWidths().forEach { (_, width) ->
            upper -= width
            boundaries.add(upper)
        }
        return boundaries
    }

    fun withMovedBoundary(boundaryIndex: Int, angleDegrees: Float): GestureAngleConfig {
        require(boundaryIndex in 1..4) { "Only internal boundaries (1..4) can be moved" }
        val bounds = sectorBoundaryAngles().toMutableList()
        val prev = bounds[boundaryIndex - 1]
        val next = bounds[boundaryIndex + 1]
        val clamped = angleDegrees.coerceIn(
            next + MIN_DEGREES,
            prev - MIN_DEGREES,
        )
        bounds[boundaryIndex] = clamped
        return fromBoundaries(bounds).normalized()
    }

    private fun fromBoundaries(bounds: List<Float>): GestureAngleConfig {
        require(bounds.size == 6) { "Expected 6 boundary angles" }
        return GestureAngleConfig(
            upDegrees = bounds[0] - bounds[1],
            upRightDegrees = bounds[1] - bounds[2],
            inDegrees = bounds[2] - bounds[3],
            downRightDegrees = bounds[3] - bounds[4],
            downDegrees = bounds[4] - bounds[5],
        )
    }

    fun resolveDirection(angleDegrees: Float): SwipeDirection? {
        if (angleDegrees > 90f || angleDegrees < -90f) return null
        var upper = 90f
        val sectors = orderedSectorWidths()
        sectors.forEachIndexed { index, (direction, width) ->
            val lower = upper - width
            val matches = if (index == sectors.lastIndex) {
                angleDegrees >= lower && angleDegrees <= upper
            } else {
                angleDegrees > lower && angleDegrees <= upper
            }
            if (matches) return direction
            upper = lower
        }
        return null
    }

    private fun copyFor(direction: SwipeDirection, value: Float): GestureAngleConfig = when (direction) {
        SwipeDirection.UP -> copy(upDegrees = value.roundToDegrees())
        SwipeDirection.UP_RIGHT -> copy(upRightDegrees = value.roundToDegrees())
        SwipeDirection.IN -> copy(inDegrees = value.roundToDegrees())
        SwipeDirection.DOWN_RIGHT -> copy(downRightDegrees = value.roundToDegrees())
        SwipeDirection.DOWN -> copy(downDegrees = value.roundToDegrees())
    }

    companion object {
        const val TOTAL_DEGREES = 180f
        const val MIN_DEGREES = 8f
        const val MAX_DEGREES = 100f
        const val DEFAULT_UP = 22f
        const val DEFAULT_UP_RIGHT = 50f
        const val DEFAULT_IN = 54f
        const val DEFAULT_DOWN_RIGHT = 32f
        const val DEFAULT_DOWN = 22f

        val DEFAULT = GestureAngleConfig()
    }
}

private fun Float.roundToDegrees(): Float = (this * 10f).roundToInt() / 10f
