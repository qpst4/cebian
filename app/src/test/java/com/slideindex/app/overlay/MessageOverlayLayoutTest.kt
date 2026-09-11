package com.slideindex.app.overlay

import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.SideBubbleHorizontalEdge
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

    @Test
    fun cNotice_rightEdge_placesTowardRightSide() {
        val listWidthPx = 120
        val listHeightPx = 300
        val (left, _) = MessageOverlayLayout.cNoticeTopLeft(
            settings = settings.copy(
                cNoticeHorizontalEdge = SideBubbleHorizontalEdge.Right,
                cNoticeYFraction = 0.5f,
            ),
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            listWidthPx = listWidthPx,
            listHeightPx = listHeightPx,
        )
        assertTrue(left > 1080 / 2)
    }

    @Test
    fun cNotice_anchorY_usesListTopNotCenter() {
        val top = MessageOverlayLayout.cNoticeAnchorY(
            settings = settings.copy(cNoticeYFraction = 0.5f),
            screenHeightPx = 2400,
            density = 3f,
            listHeightPx = 400,
        )
        assertTrue(top in 1100..1300)
    }

    @Test
    fun cNotice_leftEdge_placesTowardLeftSide() {
        val listWidthPx = 120
        val listHeightPx = 300
        val (left, _) = MessageOverlayLayout.cNoticeTopLeft(
            settings = settings.copy(
                cNoticeHorizontalEdge = SideBubbleHorizontalEdge.Left,
                cNoticeYFraction = 0.5f,
            ),
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            listWidthPx = listWidthPx,
            listHeightPx = listHeightPx,
        )
        assertTrue(left < 1080 / 2)
    }
}
