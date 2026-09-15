package com.slideindex.app.imageeditor

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import com.google.android.material.R as MaterialR
import com.google.android.material.color.ColorResourcesOverride
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.ui.miuix.theme.resolveAppMaterialColorScheme

object ImageEditorThemeApplicator {
    /**
     * 在 [Activity.setContentView] 之前调用，使 M3 控件通过 [MaterialColors] 读到与主界面一致的色板。
     */
    @SuppressLint("RestrictedApi")
    fun applyBeforeContent(activity: Activity, settings: AppSettings) {
        val systemDark =
            (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val darkTheme = AppThemeMode.fromId(settings.themeModeId).resolveIsDark(systemDark)
        val scheme = resolveAppMaterialColorScheme(activity, settings, darkTheme)

        val overlay =
            if (darkTheme) {
                MaterialR.style.ThemeOverlay_Material3_DynamicColors_Dark
            } else {
                MaterialR.style.ThemeOverlay_Material3_DynamicColors_Light
            }
        activity.theme.applyStyle(overlay, true)
        activity.window?.peekDecorView()?.context?.theme?.applyStyle(overlay, true)

        ColorResourcesOverride.getInstance()
            ?.applyIfPossible(activity, scheme.toMaterialPersonalizedColorMap())
    }
}
