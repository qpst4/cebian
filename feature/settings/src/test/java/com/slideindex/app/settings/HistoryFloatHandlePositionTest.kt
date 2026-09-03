package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFloatHandlePositionTest {
    @Test
    fun defaultY_matchesLegacyCenterOffset() {
        assertEquals(360, HistoryFloatHandlePosition.defaultY(2160))
    }

    @Test
    fun resolveY_usesStoredValueWhenSet() {
        assertEquals(
            400,
            HistoryFloatHandlePosition.resolveY(
                storedY = 400,
                screenHeightPx = 2160,
                handleHeightPx = 200,
            ),
        )
    }

    @Test
    fun resolveY_clampsToScreen() {
        assertEquals(
            1960,
            HistoryFloatHandlePosition.resolveY(
                storedY = 3000,
                screenHeightPx = 2160,
                handleHeightPx = 200,
            ),
        )
    }

    @Test
    fun resolveY_usesDefaultWhenUnset() {
        assertEquals(
            HistoryFloatHandlePosition.defaultY(2160),
            HistoryFloatHandlePosition.resolveY(
                storedY = HistoryFloatHandlePosition.UNSET_POSITION,
                screenHeightPx = 2160,
                handleHeightPx = 200,
            ),
        )
    }
}
