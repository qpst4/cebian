package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import kotlin.math.roundToInt

object ImeBoundsDetector {
    private const val MIN_IME_HEIGHT_DP = 48
    private const val ESTIMATED_KEYBOARD_HEIGHT_FRACTION = 0.40f

    fun detectImeBounds(service: AccessibilityService): Rect? {
        detectFromAccessibilityWindows(service)?.let { return it }
        return detectFromInputMethodManager(service)
    }

    private fun detectFromAccessibilityWindows(service: AccessibilityService): Rect? {
        val minHeightPx = (MIN_IME_HEIGHT_DP * service.resources.displayMetrics.density).roundToInt()
        var best: Rect? = null
        for (window in service.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            if (bounds.isEmpty) continue
            if (bounds.height() < minHeightPx) continue
            if (best == null || bounds.height() > best.height()) {
                best = bounds
            }
        }
        return best
    }

    private fun detectFromInputMethodManager(service: AccessibilityService): Rect? {
        val imm = service.getSystemService(InputMethodManager::class.java) ?: return null
        if (!imm.isAcceptingText) return null
        val metrics = service.resources.displayMetrics
        val minHeightPx = (MIN_IME_HEIGHT_DP * metrics.density).roundToInt()
        val estimatedHeight = (metrics.heightPixels * ESTIMATED_KEYBOARD_HEIGHT_FRACTION)
            .roundToInt()
            .coerceAtLeast(minHeightPx)
        val top = (metrics.heightPixels - estimatedHeight).coerceAtLeast(0)
        return Rect(0, top, metrics.widthPixels, metrics.heightPixels)
    }

}
