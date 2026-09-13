package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

data class AppCarouselSwitcherSettings(
    val cancelDistanceUpDp: Int = DEFAULT_CANCEL_DISTANCE_DP,
    val cancelDistanceDownDp: Int = DEFAULT_CANCEL_DISTANCE_DP,
) {
    fun cancelDistanceUpPx(density: Float): Float {
        return cancelDistanceUpDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP) * density
    }

    fun cancelDistanceDownPx(density: Float): Float {
        return cancelDistanceDownDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP) * density
    }

    companion object {
        const val MIN_CANCEL_DISTANCE_DP = 60
        const val MAX_CANCEL_DISTANCE_DP = 400
        const val DEFAULT_CANCEL_DISTANCE_DP = 120

        fun fromPreferences(prefs: Preferences): AppCarouselSwitcherSettings {
            val legacyUnified = prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DP]
                ?: DEFAULT_CANCEL_DISTANCE_DP
            return AppCarouselSwitcherSettings(
                cancelDistanceUpDp = prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_UP_DP]
                    ?: legacyUnified,
                cancelDistanceDownDp = prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DOWN_DP]
                    ?: legacyUnified,
            )
        }

        fun writeToPreferences(settings: AppCarouselSwitcherSettings, prefs: MutablePreferences) {
            val up = settings.cancelDistanceUpDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP)
            val down = settings.cancelDistanceDownDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP)
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_UP_DP] = up
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DOWN_DP] = down
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DP] = down
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_SEPARATE_CANCEL_DISTANCES] = true
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_BOTH_DIRECTIONS] = true
        }
    }
}
