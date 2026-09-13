package com.slideindex.app.gesture

import android.graphics.Rect
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.KeyboardTriggerBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTriggerBoundsAdjusterTest {
    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect =
        Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }

    @Test
    fun moveUp_shiftsStripAboveKeyboard() {
        val rect = rect(0, 1800, 80, 2200)
        val adjusted = KeyboardTriggerBoundsAdjuster.adjustRect(
            rect = rect,
            behavior = KeyboardTriggerBehavior.MOVE_UP,
            imeTop = 2000,
            density = 3f,
        )
        assertTrue(adjusted.bottom < rect.bottom)
        assertTrue(adjusted.bottom <= 2000 - (8f * 3f).toInt())
    }

    @Test
    fun narrow_reducesFloatBallLineWidth() {
        val rect = rect(0, 1800, 160, 2200)
        val adjusted = KeyboardTriggerBoundsAdjuster.adjustRect(
            rect = rect,
            behavior = KeyboardTriggerBehavior.NARROW,
            imeTop = 2000,
            density = 1f,
        )
        assertTrue(adjusted.right - adjusted.left < rect.right - rect.left)
        assertEquals(rect.bottom - rect.top, adjusted.bottom - adjusted.top)
    }

    @Test
    fun narrow_reducesWidthEvenWhenTriggerDoesNotOverlapKeyboard() {
        val rect = rect(0, 400, 80, 1200)
        val adjusted = KeyboardTriggerBoundsAdjuster.adjustRect(
            rect = rect,
            behavior = KeyboardTriggerBehavior.NARROW,
            imeTop = 1800,
            density = 1f,
        )
        assertTrue(adjusted.right - adjusted.left < rect.right - rect.left)
        assertEquals(rect.bottom - rect.top, adjusted.bottom - adjusted.top)
    }

    @Test
    fun narrow_reducesLeftRightEdgeTriggerWidth() {
        val rect = rect(0, 1800, 80, 2200)
        val adjusted = KeyboardTriggerBoundsAdjuster.adjustRect(
            rect = rect,
            behavior = KeyboardTriggerBehavior.NARROW,
            imeTop = 2000,
            density = 1f,
        )
        assertTrue(adjusted.right - adjusted.left < rect.right - rect.left)
        assertEquals(rect.bottom - rect.top, adjusted.bottom - adjusted.top)
    }

    @Test
    fun narrow_honorsOnePercentWithoutMinimumWidthFloor() {
        val rect = rect(0, 0, 240, 100)
        val adjusted = KeyboardTriggerBoundsAdjuster.adjustRect(
            rect = rect,
            behavior = KeyboardTriggerBehavior.NARROW,
            imeTop = 1800,
            density = 3f,
            narrowScale = 0.01f,
        )
        assertEquals(2, adjusted.right - adjusted.left)
    }

    @Test
    fun disable_zeroesTouchStrip() {
        val adjusted = KeyboardTriggerBoundsAdjuster.adjustRect(
            rect = rect(0, 1800, 80, 2200),
            behavior = KeyboardTriggerBehavior.DISABLE,
            imeTop = 2000,
            density = 3f,
        )
        assertEquals(0, adjusted.right - adjusted.left)
    }

    @Test
    fun adjustCollapsedBounds_skipsTopBottomTriggers() {
        val bounds = listOf(
            CollapsedWindowBounds(widthPx = 400, heightPx = 48, xPx = 100, yPx = 0),
        )
        val adjusted = KeyboardTriggerBoundsAdjuster.adjustCollapsedBounds(
            bounds = bounds,
            side = PanelSide.TOP,
            behavior = KeyboardTriggerBehavior.NARROW,
            imeTop = 2000,
            density = 3f,
        )
        assertEquals(bounds, adjusted)
    }
}
