package com.slideindex.app.util

import android.content.Context
import com.slideindex.app.settings.AppSettings

object TriggerVisibility {
    fun shouldSuppress(settings: AppSettings, context: Context, foregroundPackage: String?): Boolean =
        OverlaySuppression.shouldSuppress(
            settings = settings,
            context = context,
            foregroundPackage = foregroundPackage,
            scope = OverlaySuppressionScope.TRIGGER,
        )

    fun isLandscape(context: Context): Boolean = OverlaySuppression.isLandscape(context)
}
