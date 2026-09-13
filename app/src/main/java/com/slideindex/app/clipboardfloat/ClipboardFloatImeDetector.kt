package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import com.slideindex.app.overlay.ImeBoundsDetector

object ClipboardFloatImeDetector {
    fun detectImeBounds(service: AccessibilityService): Rect? =
        ImeBoundsDetector.detectImeBounds(service)
}
