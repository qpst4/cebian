package com.slideindex.app.util

import com.slideindex.app.service.SlideIndexAccessibilityService

object InputTapUtil {
    /** Inject a screen tap via accessibility (call from a background thread). */
    fun dispatchTap(rawX: Float, rawY: Float): Boolean =
        SlideIndexAccessibilityService.dispatchTapSync(rawX, rawY)

    /** Fire-and-forget tap injection; safe to call from the main thread. */
    fun dispatchTapAsync(
        rawX: Float,
        rawY: Float,
        onFinished: (Boolean) -> Unit = {},
        durationMs: Long = SlideIndexAccessibilityService.TAP_DURATION_MS,
    ) {
        SlideIndexAccessibilityService.dispatchTap(rawX, rawY, onFinished, durationMs)
    }

    /** Floating-pointer tap (gesture first for press feedback, node click fallback). */
    fun dispatchPointerTapAsync(rawX: Float, rawY: Float, onFinished: (Boolean) -> Unit = {}) {
        SlideIndexAccessibilityService.dispatchPointerTap(rawX, rawY, onFinished)
    }

    fun dispatchPointerSwipeAsync(
        startX: Float,
        startY: Float,
        config: com.slideindex.app.gesture.PointerSwipeConfig,
        onFinished: (Boolean) -> Unit = {},
    ) {
        SlideIndexAccessibilityService.dispatchPointerSwipe(startX, startY, config, onFinished)
    }
}
