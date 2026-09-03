package com.slideindex.app.gesture

internal class GestureSessionThresholdTracker(
    private val pathRecognizer: SwipePathRecognizer,
    private val callbacks: GestureSession.Callbacks,
    private val cancelLongPressCheck: () -> Unit,
    private val isTriggerConfigured: (GestureTriggerType) -> Boolean = { true },
) {
    private var wasAboveShortThreshold = false
    private var wasAboveLongThreshold = false
    private var wasAboveCompoundShortThreshold = false
    private var wasAboveCompoundLongThreshold = false
    private var longPressHapticFired = false
    private var returnHapticFired = false
    private var hoverHapticFired = false

    fun trackDistanceHaptics(rawX: Float, rawY: Float) {
        if (pathRecognizer.isCompoundModeArmed() &&
            pathRecognizer.hasMovedFromCompoundAnchor(rawX, rawY)
        ) {
            trackCompoundSecondSegmentHaptics(rawX, rawY)
        } else {
            trackPrimarySegmentHaptics(rawX, rawY)
        }
        trackReturnHaptics(rawX, rawY)
        trackHoverHaptics()
    }

    private fun trackPrimarySegmentHaptics(rawX: Float, rawY: Float) {
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
        wasAboveShortThreshold = aboveShort
        wasAboveLongThreshold = aboveLong
    }

    private fun trackCompoundSecondSegmentHaptics(rawX: Float, rawY: Float) {
        if (!isCompoundCornerHapticEnabled()) return
        val distance = pathRecognizer.compoundSecondSegmentDistance(rawX, rawY)
        val aboveShort = distance >= pathRecognizer.compoundSecondSegmentTurnThresholdPx()
        val aboveLong = distance >= pathRecognizer.longThresholdPx()
        if (aboveShort && !wasAboveCompoundShortThreshold) {
            callbacks.hapticGestureStart()
        }
        if (aboveLong && !wasAboveCompoundLongThreshold) {
            callbacks.hapticLongThreshold()
        }
        wasAboveCompoundShortThreshold = aboveShort
        wasAboveCompoundLongThreshold = aboveLong
    }

    private fun trackReturnHaptics(rawX: Float, rawY: Float) {
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
    }

    private fun trackHoverHaptics() {
        if (pathRecognizer.consumeHoverJustSatisfied()) {
            val hoverTrigger = pathRecognizer.activeHoverTrigger()
            if (hoverTrigger == null || isTriggerConfigured(hoverTrigger)) {
                if (!hoverHapticFired) {
                    hoverHapticFired = true
                    callbacks.hapticGestureStart()
                }
            }
        }
    }

    private fun isCompoundCornerHapticEnabled(): Boolean =
        isTriggerConfigured(GestureTriggerType.SHORT_SWIPE_IN_UP) ||
            isTriggerConfigured(GestureTriggerType.LONG_SWIPE_IN_UP) ||
            isTriggerConfigured(GestureTriggerType.SHORT_SWIPE_IN_DOWN) ||
            isTriggerConfigured(GestureTriggerType.LONG_SWIPE_IN_DOWN)

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
        wasAboveCompoundShortThreshold = false
        wasAboveCompoundLongThreshold = false
        longPressHapticFired = false
        returnHapticFired = false
        hoverHapticFired = false
    }
}
