package com.slideindex.app.util

import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

/**
 * Dispatches Back to the resumed app [ComponentActivity] (same path as toolbar back),
 * so edge-gesture [com.slideindex.app.gesture.GestureAction.Back] works inside our settings UI
 * where [android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK] is unreliable.
 */
object AppLocalBack {
    private var resumedActivity: WeakReference<ComponentActivity>? = null

    fun setResumed(activity: ComponentActivity) {
        resumedActivity = WeakReference(activity)
    }

    fun clearResumed(activity: ComponentActivity) {
        if (resumedActivity?.get() === activity) {
            resumedActivity = null
        }
    }

    fun dispatch(): Boolean {
        val activity = resumedActivity?.get() ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        activity.onBackPressedDispatcher.onBackPressed()
        return true
    }
}
