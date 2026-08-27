package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences

data class HolographicLauncherSettings(
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val rotationSensitivity: Float = DEFAULT_ROTATION_SENSITIVITY,
    val hapticLevel: Int = DEFAULT_HAPTIC_LEVEL,
    val hiddenAppPackages: Set<String> = emptySet(),
    val backgroundStyle: Int = DEFAULT_BACKGROUND_STYLE,
    val blurDp: Int = DEFAULT_BLUR_DP,
    val dimPercent: Int = DEFAULT_DIM_PERCENT,
) {
    companion object {
        const val BACKGROUND_BLUR = HoneycombDisplaySettings.BACKGROUND_BLUR
        const val BACKGROUND_BLACK = HoneycombDisplaySettings.BACKGROUND_BLACK
        const val BACKGROUND_WALLPAPER_BLUR = HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR

        const val DEFAULT_TIMEOUT_SECONDS = 30
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 300
        /** 按 dp 换算；跟手档约 0.012～0.016，MEIZU 21 上 100px 横向约 25°～35° */
        const val DEFAULT_ROTATION_SENSITIVITY = 0.014f
        const val MIN_ROTATION_SENSITIVITY = 0.001f
        const val MAX_ROTATION_SENSITIVITY = 0.05f
        const val DEFAULT_HAPTIC_LEVEL = 1

        const val DEFAULT_BACKGROUND_STYLE = BACKGROUND_BLUR
        const val DEFAULT_BLUR_DP = HoneycombDisplaySettings.DEFAULT_BLUR_DP
        const val MIN_BLUR_DP = HoneycombDisplaySettings.MIN_BLUR_DP
        const val MAX_BLUR_DP = HoneycombDisplaySettings.MAX_BLUR_DP
        const val DEFAULT_DIM_PERCENT = HoneycombDisplaySettings.DEFAULT_DIM_PERCENT
        const val MIN_DIM_PERCENT = HoneycombDisplaySettings.MIN_DIM_PERCENT
        const val MAX_DIM_PERCENT = HoneycombDisplaySettings.MAX_DIM_PERCENT

        fun fromPreferences(prefs: Preferences): HolographicLauncherSettings {
            val timeout = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_TIMEOUT_SECONDS] ?: DEFAULT_TIMEOUT_SECONDS
            val sensitivity = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_ROTATION_SENSITIVITY]
                ?: DEFAULT_ROTATION_SENSITIVITY
            val haptic = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_HAPTIC_LEVEL] ?: DEFAULT_HAPTIC_LEVEL
            val hidden = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_HIDDEN_APP_PACKAGES] ?: emptySet()
            val backgroundStyle = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_BACKGROUND_STYLE]
                ?: DEFAULT_BACKGROUND_STYLE
            val blurDp = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_BLUR_DP] ?: DEFAULT_BLUR_DP
            val dimPercent = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_DIM_PERCENT] ?: DEFAULT_DIM_PERCENT
            return HolographicLauncherSettings(
                timeoutSeconds = timeout.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
                rotationSensitivity = sensitivity.coerceIn(MIN_ROTATION_SENSITIVITY, MAX_ROTATION_SENSITIVITY),
                hapticLevel = haptic.coerceIn(0, 3),
                hiddenAppPackages = hidden,
                backgroundStyle = backgroundStyle.coerceIn(BACKGROUND_BLUR, BACKGROUND_WALLPAPER_BLUR),
                blurDp = blurDp.coerceIn(MIN_BLUR_DP, MAX_BLUR_DP),
                dimPercent = dimPercent.coerceIn(MIN_DIM_PERCENT, MAX_DIM_PERCENT),
            )
        }
    }
}
