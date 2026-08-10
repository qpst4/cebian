package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickLauncherDisplaySettingsTest {
    @Test
    fun legacyBackgroundOpacityPercent_matchesOldFormulaAtDefaultPanelOpacity() {
        assertEquals(84, QuickLauncherDisplaySettings.legacyBackgroundOpacityPercent(0.95f))
    }

    @Test
    fun backgroundAlphaArgb_mapsPercentToArgbChannel() {
        assertEquals(214, QuickLauncherDisplaySettings.backgroundAlphaArgb(84))
        assertEquals(255, QuickLauncherDisplaySettings.backgroundAlphaArgb(100))
        assertEquals(51, QuickLauncherDisplaySettings.backgroundAlphaArgb(20))
    }

    @Test
    fun coerceIconShape_mapsUnknownAndLegacyRoundedToDefault() {
        assertEquals(
            QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT,
            QuickLauncherDisplaySettings.coerceIconShape(-1),
        )
        assertEquals(
            QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT,
            QuickLauncherDisplaySettings.coerceIconShape(
                QuickLauncherDisplaySettings.ICON_SHAPE_ROUNDED_LEGACY,
            ),
        )
        assertEquals(
            QuickLauncherDisplaySettings.ICON_SHAPE_ADAPTIVE,
            QuickLauncherDisplaySettings.coerceIconShape(
                QuickLauncherDisplaySettings.ICON_SHAPE_ADAPTIVE,
            ),
        )
    }

    @Test
    fun defaults_matchOverlayHardcodedBaseline() {
        val defaults = QuickLauncherDisplaySettings()
        assertEquals(38, defaults.iconSizeDp)
        assertEquals(QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT, defaults.iconShape)
        assertEquals(84, defaults.backgroundOpacityPercent)
    }
}
