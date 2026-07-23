package com.slideindex.app.message

import android.view.View
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper

internal object MessageGestureHaptics {
    fun longPress(view: View) {
        val settings = OverlayDependencyAccess.overlayDependencies(view.context)
            ?.settingsRepository
            ?.readSnapshot()
            ?: AppSettings()
        HapticHelper.longThreshold(view, settings)
    }
}
