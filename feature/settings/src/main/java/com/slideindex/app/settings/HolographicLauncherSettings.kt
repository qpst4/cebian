package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences

data class HolographicLauncherSettings(
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val rotationSensitivity: Float = DEFAULT_ROTATION_SENSITIVITY,
    val hapticLevel: Int = DEFAULT_HAPTIC_LEVEL,
    val hiddenAppPackages: Set<String> = emptySet(),
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 300
        /** 按 dp 换算；跟手档约 0.012～0.016，MEIZU 21 上 100px 横向约 25°～35° */
        const val DEFAULT_ROTATION_SENSITIVITY = 0.014f
        const val MIN_ROTATION_SENSITIVITY = 0.001f
        const val MAX_ROTATION_SENSITIVITY = 0.05f
        const val DEFAULT_HAPTIC_LEVEL = 1

        fun fromPreferences(prefs: Preferences): HolographicLauncherSettings {
            val timeout = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_TIMEOUT_SECONDS] ?: DEFAULT_TIMEOUT_SECONDS
            val sensitivity = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_ROTATION_SENSITIVITY]
                ?: DEFAULT_ROTATION_SENSITIVITY
            val haptic = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_HAPTIC_LEVEL] ?: DEFAULT_HAPTIC_LEVEL
            val hidden = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_HIDDEN_APP_PACKAGES] ?: emptySet()
            return HolographicLauncherSettings(
                timeoutSeconds = timeout.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
                rotationSensitivity = sensitivity.coerceIn(MIN_ROTATION_SENSITIVITY, MAX_ROTATION_SENSITIVITY),
                hapticLevel = haptic.coerceIn(0, 3),
                hiddenAppPackages = hidden,
            )
        }
    }
}
