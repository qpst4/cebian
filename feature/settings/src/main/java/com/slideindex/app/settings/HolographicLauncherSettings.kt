package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences

data class HolographicLauncherSettings(
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val rotationSensitivity: Float = DEFAULT_ROTATION_SENSITIVITY,
    val hapticLevel: Int = DEFAULT_HAPTIC_LEVEL,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 300
        const val DEFAULT_ROTATION_SENSITIVITY = 0.008f
        const val MIN_ROTATION_SENSITIVITY = 0.001f
        const val MAX_ROTATION_SENSITIVITY = 0.05f
        const val DEFAULT_HAPTIC_LEVEL = 1

        fun fromPreferences(prefs: Preferences): HolographicLauncherSettings {
            val timeout = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_TIMEOUT_SECONDS] ?: DEFAULT_TIMEOUT_SECONDS
            val sensitivity = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_ROTATION_SENSITIVITY]
                ?: DEFAULT_ROTATION_SENSITIVITY
            val haptic = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_HAPTIC_LEVEL] ?: DEFAULT_HAPTIC_LEVEL
            return HolographicLauncherSettings(
                timeoutSeconds = timeout.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
                rotationSensitivity = sensitivity.coerceIn(MIN_ROTATION_SENSITIVITY, MAX_ROTATION_SENSITIVITY),
                hapticLevel = haptic.coerceIn(0, 3),
            )
        }
    }
}
