package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.PermissionHelper

/**
 * Resolves a [Context] suitable for message reminder overlays.
 * Prefers the accessibility service when connected; otherwise falls back to the app
 * context when [PermissionHelper.canDrawOverlays] is granted.
 */
object MessageOverlayHost {
    fun resolveHostContext(context: Context): Context? {
        SlideIndexAccessibilityService.overlayHostContext()?.let { return it }
        val appContext = context.applicationContext
        return if (PermissionHelper.canDrawOverlays(appContext)) appContext else null
    }

    /** 内容面板优先使用 App Overlay，避免 OEM 对 a11y overlay 强制半透明。 */
    fun resolveContentPanelContext(context: Context): Context? {
        val appContext = context.applicationContext
        if (PermissionHelper.canDrawOverlays(appContext)) return appContext
        return resolveHostContext(context)
    }

    fun canShow(context: Context): Boolean = resolveHostContext(context) != null
}
