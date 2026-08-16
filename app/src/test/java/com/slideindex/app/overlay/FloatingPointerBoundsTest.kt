package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPointerBoundsTest {
    @Test
    fun travelFractionFromSpeed_higherSpeedMeansShorterTravel() {
        assertEquals(0.2f, FloatingPointerBounds.travelFractionFromSpeed(0.75f), 0.001f)
        assertEquals(0.75f, FloatingPointerBounds.travelFractionFromSpeed(0.2f), 0.001f)
    }

    @Test
    fun effectivePointerTravel_75PercentIsFasterThan20Percent() {
        val screenW = 1080f
        val screenH = 2400f
        val fast = FloatingPointerBounds.effectivePointerTravel(
            AppSettings(floatingPointer = FloatingPointerSettings(floatingPointerSensitivityFraction = 0.75f)),
            screenW,
            screenH,
        )
        val slow = FloatingPointerBounds.effectivePointerTravel(
            AppSettings(floatingPointer = FloatingPointerSettings(floatingPointerSensitivityFraction = 0.2f)),
            screenW,
            screenH,
        )
        assertTrue(fast.first < slow.first)
        assertTrue(fast.second < slow.second)
    }
}
