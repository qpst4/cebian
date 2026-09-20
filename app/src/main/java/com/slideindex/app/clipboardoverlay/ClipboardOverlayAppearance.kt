package com.slideindex.app.clipboardoverlay

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.material3.ColorScheme
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.ClipboardOverlayScale
import com.slideindex.app.ui.miuix.theme.resolveAppMaterialColorScheme

internal fun Context.clipboardOverlayThemedContext(settings: AppSettings): Pair<Context, ColorScheme> {
    val systemDark =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    val darkTheme = AppThemeMode.fromId(settings.themeModeId).resolveIsDark(systemDark)
    val themed = ContextThemeWrapper(this, R.style.Theme_SlideIndex_ClipboardOverlay)
    val config = Configuration(resources.configuration)
    val night = if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    val scale = ClipboardOverlayScale.toFactor(settings.clipboardOverlayScalePercent)
    if (scale < 1f) {
        val baseDpi = resources.displayMetrics.densityDpi.coerceAtLeast(1)
        config.densityDpi = (baseDpi * scale).toInt().coerceAtLeast(1)
    }
    themed.applyOverrideConfiguration(config)
    return themed to resolveAppMaterialColorScheme(themed, settings, darkTheme)
}
