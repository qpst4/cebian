package com.slideindex.app.util

import com.slideindex.app.service.SlideIndexAccessibilityService

object InputTapUtil {
    /** Inject a screen tap via accessibility (call from a background thread). */
    fun dispatchTap(rawX: Float, rawY: Float): Boolean =
        SlideIndexAccessibilityService.dispatchTapSync(rawX, rawY)
}
