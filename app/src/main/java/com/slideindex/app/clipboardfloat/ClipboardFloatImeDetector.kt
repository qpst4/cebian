package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.roundToInt

object ClipboardFloatImeDetector {
    private const val MIN_IME_HEIGHT_DP = 80

    fun detectImeBounds(service: AccessibilityService): Rect? {
        val minHeightPx = (MIN_IME_HEIGHT_DP * service.resources.displayMetrics.density).roundToInt()
        var best: Rect? = null
        for (window in service.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            if (bounds.height() < minHeightPx) continue
            if (best == null || bounds.height() > best.height()) {
                best = bounds
            }
        }
        return best
    }
}
