package com.slideindex.app.util

import android.content.Context
import com.slideindex.app.settings.AppSettings

object SystemBackGestureConflictHelper {
    fun isGestureNavigation(context: Context): Boolean {
        val resourceId = context.resources.getIdentifier(
            "config_navBarInteractionMode",
            "integer",
            "android",
        )
        if (resourceId <= 0) return false
        return context.resources.getInteger(resourceId) == 2
    }

    fun hasPotentialConflict(settings: AppSettings, context: Context): Boolean {
        if (!isGestureNavigation(context)) return false
        if (settings.interceptSystemBackGesture) return false
        return settings.leftEdgeEnabled || settings.rightEdgeEnabled
    }
}
