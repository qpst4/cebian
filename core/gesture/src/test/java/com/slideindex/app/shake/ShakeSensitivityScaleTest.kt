package com.slideindex.app.shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeSensitivityScaleTest {

    @Test
    fun effectiveThreshold_higherUiMeansLowerThreshold() {
        assertEquals(20f, ShakeSensitivityScale.effectiveThreshold(1f), 0.001f)
        assertEquals(2f, ShakeSensitivityScale.effectiveThreshold(20f), 0.001f)
        assertTrue(ShakeSensitivityScale.effectiveThreshold(14f) > ShakeSensitivityScale.effectiveThreshold(18f))
    }

    @Test
    fun migrateUiFromV2_preservesLegacyThreshold() {
        val legacyValues = listOf(1f, 3f, 6f, 10f)
        for (legacy in legacyValues) {
            val legacyThreshold = 11f - legacy
            val migratedThreshold = ShakeSensitivityScale.effectiveThreshold(
                ShakeSensitivityScale.migrateUiFromV2(legacy),
            )
            val expected = if (legacy == 10f) 2f else legacyThreshold
            assertEquals(expected, migratedThreshold, 0.05f)
        }
    }

    @Test
    fun clampUi_limitsToOneThroughTwenty() {
        assertEquals(1f, ShakeSensitivityScale.clampUi(0.2f))
        assertEquals(20f, ShakeSensitivityScale.clampUi(99f))
        assertEquals(14f, ShakeSensitivityScale.clampUi(14f))
    }
}
