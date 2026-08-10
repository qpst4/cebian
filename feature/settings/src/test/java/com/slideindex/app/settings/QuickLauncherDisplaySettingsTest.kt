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
}
