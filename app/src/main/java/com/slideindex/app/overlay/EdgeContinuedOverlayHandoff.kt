package com.slideindex.app.overlay

/**
 * Edge gesture handed off to floating pointer / regional pick while the finger stays down.
 */
internal object EdgeContinuedOverlayHandoff {
    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var gestureHintDismissed: Boolean = false
        private set

    fun begin() {
        active = true
        gestureHintDismissed = false
    }

    fun markGestureHintDismissed() {
        gestureHintDismissed = true
    }

    fun shouldDismissGestureHint(): Boolean = active && !gestureHintDismissed

    fun clearIfInactive() {
        if (!FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch() &&
            !RegionalPickOverlay.isConsumingEdgeGestureTouch() &&
            !RegionalPickOverlay.isActive
        ) {
            clear()
        }
    }

    fun clear() {
        active = false
        gestureHintDismissed = false
    }
}
