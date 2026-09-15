package com.slideindex.app.util

import android.content.Context
import com.slideindex.app.gesture.ActionExecutorPolicy
import com.slideindex.app.service.OverlayService

object ForegroundHostPackageResolver {
    fun resolveForFreeWindow(context: Context): String? {
        val selfPackage = context.packageName
        val live = AccessibilityForegroundResolver.resolve(context)
        return ActionExecutorPolicy.resolveFreeWindowTargetPackage(
            selfPackage = selfPackage,
            liveForegroundPackage = live,
            gestureForegroundPackage = OverlayService.gestureForegroundPackage,
            cachedForegroundPackage = OverlayService.foregroundPackage,
        )
    }
}
