package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import kotlin.math.roundToInt

data class QuickLauncherDisplaySettings(
    val backgroundOpacityPercent: Int = DEFAULT_BACKGROUND_OPACITY_PERCENT,
) {
    companion object {
        const val MIN_BACKGROUND_OPACITY_PERCENT = 20
        const val MAX_BACKGROUND_OPACITY_PERCENT = 100
        /** 对齐旧版 `225 × panelOpacity(0.95)` → alpha 213 ≈ 84% */
        const val DEFAULT_BACKGROUND_OPACITY_PERCENT = 84

        fun legacyBackgroundOpacityPercent(panelOpacity: Float): Int {
            val alpha = (225f * panelOpacity).toInt().coerceIn(150, 225)
            return (alpha * 100f / 255f).roundToInt()
                .coerceIn(MIN_BACKGROUND_OPACITY_PERCENT, MAX_BACKGROUND_OPACITY_PERCENT)
        }

        fun backgroundAlphaArgb(backgroundOpacityPercent: Int): Int =
            (255f * backgroundOpacityPercent.coerceIn(
                MIN_BACKGROUND_OPACITY_PERCENT,
                MAX_BACKGROUND_OPACITY_PERCENT,
            ) / 100f).roundToInt().coerceIn(0, 255)

        fun fromPreferences(prefs: Preferences, panelOpacity: Float): QuickLauncherDisplaySettings {
            val stored = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_BACKGROUND_OPACITY_PERCENT]
            return QuickLauncherDisplaySettings(
                backgroundOpacityPercent = stored?.coerceIn(
                    MIN_BACKGROUND_OPACITY_PERCENT,
                    MAX_BACKGROUND_OPACITY_PERCENT,
                ) ?: legacyBackgroundOpacityPercent(panelOpacity),
            )
        }
    }
}
