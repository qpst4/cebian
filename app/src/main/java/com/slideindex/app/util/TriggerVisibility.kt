package com.slideindex.app.util

import android.content.Context
import android.content.res.Configuration
import com.slideindex.app.settings.AppSettings

object TriggerVisibility {
    fun shouldSuppress(settings: AppSettings, context: Context, foregroundPackage: String?): Boolean {
        if (settings.hideTriggerInLandscape && isLandscape(context)) return true
        if (settings.hideTriggerOnLockScreen && TriggerEnvironmentState.lockScreenActive) return true
        if (settings.hideTriggerOnLauncher) {
            val pkg = foregroundPackage ?: return false
            if (LauncherUtils.isHomePackage(context, pkg)) return true
        }
        val pkg = foregroundPackage ?: return false
        return pkg in settings.excludedTriggerAppPackages
    }

    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
