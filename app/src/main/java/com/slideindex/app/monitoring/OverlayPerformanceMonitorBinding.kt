package com.slideindex.app.monitoring

import android.content.Context
import com.slideindex.app.BuildConfig
import com.slideindex.app.settings.AppSettings

/** Binds debug overlay lifecycles to the global [PerformanceMonitor] refcount API. */
object OverlayPerformanceMonitorBinding {
    fun syncUserPreference(enabled: Boolean, context: Context? = null) {
        if (!BuildConfig.DEBUG) return
        PerformanceMonitor.setUserPreference(enabled)
        context?.let { DebugPerformanceOverlay.sync(it, enabled) }
    }

    fun syncUserPreference(settings: AppSettings, context: Context? = null) {
        syncUserPreference(settings.debugPerformanceMonitorEnabled, context)
    }

    fun onOverlayShown(settings: AppSettings, context: Context? = null) {
        if (!BuildConfig.DEBUG) return
        PerformanceMonitor.setUserPreference(settings.debugPerformanceMonitorEnabled)
        PerformanceMonitor.acquireOverlay()
        context?.let { DebugPerformanceOverlay.sync(it, settings.debugPerformanceMonitorEnabled) }
    }

    fun onOverlayHidden(context: Context? = null) {
        if (!BuildConfig.DEBUG) return
        PerformanceMonitor.releaseOverlay()
        context?.let { DebugPerformanceOverlay.sync(it, userPreferenceEnabled = false) }
    }
}
