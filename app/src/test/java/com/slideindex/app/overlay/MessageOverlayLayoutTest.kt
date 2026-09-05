package com.slideindex.app.overlay

import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessageSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageOverlayLayoutTest {
    private val settings = MessageSettings()

    @Test
    fun floatIcon_bottomEnd_defaultsNearBottomRight() {
        val (left, top) = MessageOverlayLayout.floatIconTopLeft(
            settings = settings.copy(
                floatIconCorner = MessageOverlayCorner.BottomEnd,
                floatIconYFraction = 0.85f,
                floatIconSizeDp = 44f,
            ),
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
        )
        assertTrue(left > 1080 / 2)
        assertTrue(top > 2400 / 2)
    }

    @Test
    fun floatIcon_topStart_defaultsNearTopLeft() {
        val (left, top) = MessageOverlayLayout.floatIconTopLeft(
            settings = settings.copy(
                floatIconCorner = MessageOverlayCorner.TopStart,
                floatIconYFraction = 0.15f,
                floatIconSizeDp = 44f,
            ),
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
        )
        assertTrue(left < 1080 / 2)
        assertTrue(top < 2400 / 2)
    }
}
