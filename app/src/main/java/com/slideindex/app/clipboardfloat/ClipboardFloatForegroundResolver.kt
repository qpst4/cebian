package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import com.slideindex.app.util.AccessibilityForegroundResolver

internal object ClipboardFloatForegroundResolver {
    fun resolveHostPackage(service: AccessibilityService): String? =
        AccessibilityForegroundResolver.resolveHostPackage(service)
}
