package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import kotlin.math.roundToInt

const val GESTURE_ANGLE_BASE = 180f

data class GestureAngles(
    val left: GestureAngle = GestureAngle.DEFAULT_LEFT,
    val right: GestureAngle = GestureAngle.DEFAULT_LEFT,
    val bottom: GestureAngle = GestureAngle.DEFAULT_BOTTOM,
    val top: GestureAngle = GestureAngle.DEFAULT_TOP,
)

data class GestureAngle(
    val p1: Float = DEFAULT_P1,
    val p2: Float = DEFAULT_P2,
    val p3: Float = DEFAULT_P3_LEFT_RIGHT,
    val p4: Float = DEFAULT_P4,
) {
    val ps: List<Float> = listOf(p1, p2, p3, p4)

    init {
        require(p1 in 0f..1f && p1 <= p2 && p2 <= p3 && p3 <= p4 && p4 <= 1f) {
            "Illegal gesture angle points: $p1, $p2, $p3, $p4"
        }
    }

    fun getDegree(index: Int): Float = GESTURE_ANGLE_BASE * ps[index]

    fun getArcDegree(index: Int): Float = when (index) {
        0 -> GESTURE_ANGLE_BASE * p1
        1 -> GESTURE_ANGLE_BASE * (p2 - p1)
        2 -> GESTURE_ANGLE_BASE * (p3 - p2)
        3 -> GESTURE_ANGLE_BASE * (p4 - p3)
        4 -> GESTURE_ANGLE_BASE * (1f - p4)
        else -> error("Unknown arc index: $index")
    }

    fun getArcDegrees(): List<Float> = List(5) { getArcDegree(it) }

    fun toSwipeDirection(degree: Float): SwipeDirection? = when {
        degree < getDegree(0) -> SwipeDirection.UP
        degree <= getDegree(1) -> SwipeDirection.UP_RIGHT
        degree <= getDegree(2) -> SwipeDirection.IN
        degree <= getDegree(3) -> SwipeDirection.DOWN_RIGHT
        else -> SwipeDirection.DOWN
    }

    fun copyPoint(field: GestureAnglePoint, newP: Float, minGapP: Float): GestureAngle {
        val notInEdgeMinGapP = minGapP * 2f
        return when (field) {
            GestureAnglePoint.P1 -> {
                val min = minGapP
                val max = p2 - notInEdgeMinGapP
                copy(p1 = newP.coerceIn(min, max))
            }
            GestureAnglePoint.P2 -> {
                val min = p1 + notInEdgeMinGapP
                val max = (p3 - notInEdgeMinGapP).coerceAtMost(0.5f - minGapP)
                copy(p2 = newP.coerceIn(min, max))
            }
            GestureAnglePoint.P3 -> {
                val min = (p2 + notInEdgeMinGapP).coerceAtLeast(0.5f + minGapP)
                val max = p4 - notInEdgeMinGapP
                copy(p3 = newP.coerceIn(min, max))
            }
            GestureAnglePoint.P4 -> {
                val min = p3 + notInEdgeMinGapP
                val max = 1f - minGapP
                copy(p4 = newP.coerceIn(min, max))
            }
        }
    }

    companion object {
        const val DEFAULT_P1 = 0.12f
        const val DEFAULT_P2 = 0.40f
        const val DEFAULT_P3_LEFT_RIGHT = 0.70f
        const val DEFAULT_P3_BOTTOM = 0.60f
        const val DEFAULT_P4 = 0.88f
        const val MIN_GAP_P = 0.02f

        val DEFAULT_LEFT = GestureAngle(
            p1 = DEFAULT_P1,
            p2 = DEFAULT_P2,
            p3 = DEFAULT_P3_LEFT_RIGHT,
            p4 = DEFAULT_P4,
        )
        val DEFAULT_BOTTOM = GestureAngle(
            p1 = DEFAULT_P1,
            p2 = DEFAULT_P2,
            p3 = DEFAULT_P3_BOTTOM,
            p4 = DEFAULT_P4,
        )
        val DEFAULT_TOP = GestureAngle(
            p1 = DEFAULT_P1,
            p2 = DEFAULT_P2,
            p3 = DEFAULT_P3_BOTTOM,
            p4 = DEFAULT_P4,
        )

        fun fromLegacyConfig(config: GestureAngleConfig): GestureAngle {
            val widths = config.orderedSectorWidths().map { it.second }
            var cumulative = 0f
            val boundaries = mutableListOf(0f)
            widths.forEach { width ->
                cumulative += width
                boundaries.add(cumulative / GESTURE_ANGLE_BASE)
            }
            return GestureAngle(
                p1 = boundaries[1],
                p2 = boundaries[2],
                p3 = boundaries[3],
                p4 = boundaries[4],
            )
        }
    }
}

enum class GestureAnglePoint {
    P1,
    P2,
    P3,
    P4,
}

fun GestureAngles.forSide(side: PanelSide): GestureAngle = when (side) {
    PanelSide.LEFT -> left
    PanelSide.RIGHT -> right
    PanelSide.BOTTOM -> bottom
    PanelSide.TOP -> top
}

fun GestureAngles.withSide(side: PanelSide, angle: GestureAngle): GestureAngles = when (side) {
    PanelSide.LEFT -> copy(left = angle)
    PanelSide.RIGHT -> copy(right = angle)
    PanelSide.BOTTOM -> copy(bottom = angle)
    PanelSide.TOP -> copy(top = angle)
}

private fun Float.roundToP(): Float = (this * 1000f).roundToInt() / 1000f
