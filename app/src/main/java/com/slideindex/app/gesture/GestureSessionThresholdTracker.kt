package com.slideindex.app.gesture

internal class GestureSessionThresholdTracker(
    private val pathRecognizer: SwipePathRecognizer,
    private val callbacks: GestureSession.Callbacks,
    private val cancelLongPressCheck: () -> Unit,
    private val isTriggerConfigured: (GestureTriggerType) -> Boolean = { true },
) {
    private var wasAboveShortThreshold = false
    private var wasAboveLongThreshold = false
    private var longPressHapticFired = false
    private var returnHapticFired = false
    private var hoverHapticFired = false

    fun trackDistanceHaptics(rawX: Float, rawY: Float) {
        val distance = pathRecognizer.swipeDistance(rawX, rawY)
        val aboveShort = distance >= pathRecognizer.shortThresholdPx()
        val aboveLong = distance >= pathRecognizer.longThresholdPx()
        if (aboveShort && !wasAboveShortThreshold) {
            cancelLongPressCheck()
            pathRecognizer.disqualifyLongPress()
            callbacks.hapticGestureStart()
        }
        if (aboveLong && !wasAboveLongThreshold) {
            callbacks.hapticLongThreshold()
        }
        if (isTriggerConfigured(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK) &&
            pathRecognizer.isReturnSwipeActive(rawX, rawY)
        ) {
            if (!returnHapticFired) {
                returnHapticFired = true
                callbacks.hapticGestureStart()
            }
        } else {
            returnHapticFired = false
        }
        if (pathRecognizer.consumeHoverJustSatisfied()) {
            val hoverTrigger = pathRecognizer.activeHoverTrigger()
            if (hoverTrigger == null || isTriggerConfigured(hoverTrigger)) {
                if (!hoverHapticFired) {
                    hoverHapticFired = true
                    callbacks.hapticGestureStart()
                }
            }
        }
        wasAboveShortThreshold = aboveShort
        wasAboveLongThreshold = aboveLong
    }

    fun maybeHapticLongPress(rawX: Float, rawY: Float) {
        if (longPressHapticFired) return
        pathRecognizer.refreshLongPress(rawX, rawY)
        if (pathRecognizer.isLongPressArmed()) {
            longPressHapticFired = true
            callbacks.hapticLongThreshold()
        }
    }

    fun reset() {
        wasAboveShortThreshold = false
        wasAboveLongThreshold = false
        longPressHapticFired = false
        returnHapticFired = false
        hoverHapticFired = false
    }
}
