package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.gesture.SelectedHintMetrics

data class AppSwitcherDisplaySettings(
    val iconSizeDp: Int = DEFAULT_ICON_SIZE_DP,
    val spacingDp: Int = DEFAULT_SPACING_DP,
    val selectionScale: Int = DEFAULT_SELECTION_SCALE,
    val pinOnRelease: Boolean = true,
    val emptyTapClose: Boolean = true,
    val showSelectedName: Boolean = true,
    val selectedHintIconSizeDp: Int = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
    val dimPercent: Int = DEFAULT_DIM_PERCENT,
    val blurDp: Int = DEFAULT_BLUR_DP,
    val slotHaptic: Boolean = true,
    val initialRadiusRatioPercent: Int = DEFAULT_INITIAL_RADIUS_RATIO_PERCENT,
) {
    companion object {
        const val MIN_ICON_SIZE_DP = 28
        const val MAX_ICON_SIZE_DP = 56
        const val DEFAULT_ICON_SIZE_DP = 40

        const val MIN_SPACING_DP = 2
        const val MAX_SPACING_DP = 16
        const val DEFAULT_SPACING_DP = 6

        const val MIN_SELECTION_SCALE = 105
        const val MAX_SELECTION_SCALE = 130
        const val DEFAULT_SELECTION_SCALE = 110

        const val DEFAULT_DIM_PERCENT = 18
        const val MIN_DIM_PERCENT = 0
        const val MAX_DIM_PERCENT = 50

        const val DEFAULT_BLUR_DP = 24
        const val MIN_BLUR_DP = 0
        const val MAX_BLUR_DP = 48

        const val MIN_INITIAL_RADIUS_RATIO_PERCENT = 90
        const val MAX_INITIAL_RADIUS_RATIO_PERCENT = 130
        const val DEFAULT_INITIAL_RADIUS_RATIO_PERCENT = 102

        const val MAX_SLOTS = 48

        fun fromPreferences(prefs: Preferences): AppSwitcherDisplaySettings =
            AppSwitcherDisplaySettings(
                iconSizeDp = prefs[SettingsPreferenceKeys.APP_SWITCHER_ICON_SIZE_DP] ?: DEFAULT_ICON_SIZE_DP,
                spacingDp = prefs[SettingsPreferenceKeys.APP_SWITCHER_SPACING_DP] ?: DEFAULT_SPACING_DP,
                selectionScale = prefs[SettingsPreferenceKeys.APP_SWITCHER_SELECTION_SCALE] ?: DEFAULT_SELECTION_SCALE,
                pinOnRelease = prefs[SettingsPreferenceKeys.APP_SWITCHER_PIN_ON_RELEASE] ?: true,
                emptyTapClose = prefs[SettingsPreferenceKeys.APP_SWITCHER_EMPTY_TAP_CLOSE] ?: true,
                showSelectedName = prefs[SettingsPreferenceKeys.APP_SWITCHER_SHOW_SELECTED_NAME] ?: true,
                selectedHintIconSizeDp = SelectedHintMetrics.clampIconSizeDp(
                    prefs[SettingsPreferenceKeys.APP_SWITCHER_SELECTED_HINT_ICON_SIZE_DP]
                        ?: SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
                ),
                dimPercent = prefs[SettingsPreferenceKeys.APP_SWITCHER_DIM_PERCENT] ?: DEFAULT_DIM_PERCENT,
                blurDp = prefs[SettingsPreferenceKeys.APP_SWITCHER_BLUR_DP] ?: DEFAULT_BLUR_DP,
                slotHaptic = prefs[SettingsPreferenceKeys.APP_SWITCHER_SLOT_HAPTIC] ?: true,
                initialRadiusRatioPercent = prefs[SettingsPreferenceKeys.APP_SWITCHER_INITIAL_RADIUS_RATIO_PERCENT]
                    ?: DEFAULT_INITIAL_RADIUS_RATIO_PERCENT,
            )
    }
}
