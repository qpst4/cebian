package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import com.slideindex.app.gesture.KeyboardTriggerBoundsAdjuster
import com.slideindex.app.service.SlideIndexAccessibilityService

object KeyboardTriggerImeCoordinator {
    @Volatile
    private var lastImeTop: Int? = null

    @Volatile
    private var lastImeVisible: Boolean = false

    fun onWindowsChanged(service: AccessibilityService) {
        val imeBounds = ImeBoundsDetector.detectImeBounds(service)
        val visible = imeBounds != null
        val top = imeBounds?.top
        val visibilityChanged = visible != lastImeVisible
        val shouldRelayout = visibilityChanged || KeyboardTriggerBoundsAdjuster.shouldRelayout(
            previousTop = lastImeTop,
            visible = visible,
            top = top,
        )
        KeyboardTriggerImeState.update(visible = visible, top = top)
        if (shouldRelayout) {
            FloatBallOverlay.onKeyboardImeChanged()
            SlideIndexAccessibilityService.onKeyboardImeChanged()
        }
        lastImeVisible = visible
        lastImeTop = top
    }
}
