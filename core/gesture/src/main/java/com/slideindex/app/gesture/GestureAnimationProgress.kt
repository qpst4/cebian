package com.slideindex.app.gesture

import com.slideindex.app.overlay.animation.GestureAnimationPosition
import kotlin.math.abs

/** Pure helpers for gesture-hint animation progress (unit-testable). */
object GestureAnimationProgress {
    fun progress(
        position: GestureAnimationPosition,
        originX: Float,
        originY: Float,
        fingerX: Float,
        fingerY: Float,
        swipeDirection: SwipeDirection?,
    ): Float {
        val along = abs(fingerX - originX)
        return when (position) {
            GestureAnimationPosition.Left -> fingerX
            GestureAnimationPosition.Right -> -fingerX
            GestureAnimationPosition.Bottom -> {
                val inward = originY - fingerY
                if (isHorizontalAlongEdge(position, swipeDirection)) {
                    along
                } else {
                    maxOf(inward, along)
                }
            }
            GestureAnimationPosition.Top -> {
                val inward = fingerY - originY
                if (isHorizontalAlongEdge(position, swipeDirection)) {
                    along
                } else {
                    maxOf(inward, along)
                }
            }
        }.coerceAtLeast(0f)
    }

    fun isHorizontalAlongEdge(
        position: GestureAnimationPosition,
        swipeDirection: SwipeDirection?,
    ): Boolean {
        if (position != GestureAnimationPosition.Top && position != GestureAnimationPosition.Bottom) {
            return false
        }
        return when (swipeDirection) {
            SwipeDirection.UP, SwipeDirection.DOWN -> true
            SwipeDirection.UP_RIGHT, SwipeDirection.DOWN_RIGHT, SwipeDirection.IN, null -> false
        }
    }
}
