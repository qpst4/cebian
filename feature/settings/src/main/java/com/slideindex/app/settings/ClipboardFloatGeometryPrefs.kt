package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

object ClipboardFloatGeometryPrefs {
    fun readOrientationGeometry(
        prefs: Preferences,
        landscape: Boolean,
    ): ClipboardFloatOrientationGeometry {
        val legacyPanelX = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X]
            ?: prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_X]
        val legacyPanelY = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y]
            ?: prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_Y]
        val legacyPanelWidth = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP]
            ?: prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_WIDTH_DP]
        val legacyPanelHeight = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP]
            ?: prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_HEIGHT_DP]
        val legacyChipX = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X]
        val legacyChipY = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y]

        val panelX = if (landscape) {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X_LANDSCAPE]
        } else {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X_PORTRAIT] ?: legacyPanelX
        } ?: ClipboardFloatWindowMetrics.UNSET_POSITION

        val panelY = if (landscape) {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y_LANDSCAPE]
        } else {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y_PORTRAIT] ?: legacyPanelY
        } ?: ClipboardFloatWindowMetrics.UNSET_POSITION

        val panelWidthDp = ClipboardFloatWindowMetrics.coerceWidth(
            if (landscape) {
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP_LANDSCAPE]
            } else {
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP_PORTRAIT] ?: legacyPanelWidth
            } ?: ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP,
        )

        val panelHeightDp = ClipboardFloatWindowMetrics.coerceHeight(
            if (landscape) {
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP_LANDSCAPE]
            } else {
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP_PORTRAIT] ?: legacyPanelHeight
            } ?: ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP,
        )

        val chipX = if (landscape) {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X_LANDSCAPE]
        } else {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X_PORTRAIT] ?: legacyChipX
        } ?: ClipboardFloatWindowMetrics.UNSET_POSITION

        val chipY = if (landscape) {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y_LANDSCAPE]
        } else {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y_PORTRAIT] ?: legacyChipY
        } ?: ClipboardFloatWindowMetrics.UNSET_POSITION

        return ClipboardFloatOrientationGeometry(
            panelX = panelX,
            panelY = panelY,
            panelWidthDp = panelWidthDp,
            panelHeightDp = panelHeightDp,
            chipX = chipX,
            chipY = chipY,
        )
    }

    fun writeOrientationGeometry(
        prefs: MutablePreferences,
        landscape: Boolean,
        geometry: ClipboardFloatOrientationGeometry,
    ) {
        if (landscape) {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X_LANDSCAPE] = geometry.panelX
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y_LANDSCAPE] = geometry.panelY
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP_LANDSCAPE] =
                ClipboardFloatWindowMetrics.coerceWidth(geometry.panelWidthDp)
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP_LANDSCAPE] =
                ClipboardFloatWindowMetrics.coerceHeight(geometry.panelHeightDp)
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X_LANDSCAPE] = geometry.chipX
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y_LANDSCAPE] = geometry.chipY
        } else {
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X_PORTRAIT] = geometry.panelX
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y_PORTRAIT] = geometry.panelY
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP_PORTRAIT] =
                ClipboardFloatWindowMetrics.coerceWidth(geometry.panelWidthDp)
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP_PORTRAIT] =
                ClipboardFloatWindowMetrics.coerceHeight(geometry.panelHeightDp)
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X_PORTRAIT] = geometry.chipX
            prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y_PORTRAIT] = geometry.chipY
        }
    }

    fun resetAllGeometry(prefs: MutablePreferences) {
        val unset = ClipboardFloatWindowMetrics.UNSET_POSITION
        val defaultWidth = ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP
        val defaultHeight = ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP
        listOf(true, false).forEach { landscape ->
            writeOrientationGeometry(
                prefs,
                landscape,
                ClipboardFloatOrientationGeometry(
                    panelX = unset,
                    panelY = unset,
                    panelWidthDp = defaultWidth,
                    panelHeightDp = defaultHeight,
                    chipX = unset,
                    chipY = unset,
                ),
            )
        }
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X] = unset
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y] = unset
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP] = defaultWidth
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP] = defaultHeight
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X] = unset
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y] = unset
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_X] = unset
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_Y] = unset
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_WIDTH_DP] = defaultWidth
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_HEIGHT_DP] = defaultHeight
    }
}
