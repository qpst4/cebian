package com.slideindex.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSwitcherFlingLogicTest {
    @Test
    fun `finger fling up maps to positive scroll velocity`() {
        assertEquals(1200, TaskSwitcherScrollHandler.mapTrackerVelocityToFling(-1200f))
    }

    @Test
    fun `finger fling down maps to negative scroll velocity`() {
        assertEquals(-900, TaskSwitcherScrollHandler.mapTrackerVelocityToFling(900f))
    }

    @Test
    fun `fling into top edge produces positive overscroll`() {
        val overscroll = TaskSwitcherScrollHandler.overscrollFromFlingVelocity(
            axisVelocity = -2400f,
            resistance = 0.36f,
            maxOverscrollPx = 52f
        )
        assert(overscroll > 0f)
        assert(overscroll <= 52f)
    }

    @Test
    fun `fling into bottom edge produces negative overscroll`() {
        val overscroll = TaskSwitcherScrollHandler.overscrollFromFlingVelocity(
            axisVelocity = 2400f,
            resistance = 0.36f,
            maxOverscrollPx = 52f
        )
        assert(overscroll < 0f)
        assert(overscroll >= -52f)
    }

    @Test
    fun `terminal top bounce uses retained start velocity when peak is weak`() {
        val bounceVelocity = TaskSwitcherScrollHandler.resolveFlingEdgeBounceVelocity(
            offset = 0f,
            maxOffset = 600f,
            terminalVelocity = -20f,
            startOffset = 240f,
            startVelocity = -2800f,
            peakTopVelocity = -40f,
            peakBottomVelocity = 0f,
            minVelocity = 50f,
            fromTerminalFrame = true
        )
        assertEquals(-616f, bounceVelocity)
    }

    @Test
    fun `small fling overscroll is clamped to a visible minimum`() {
        val overscroll = TaskSwitcherScrollHandler.overscrollFromFlingVelocity(
            axisVelocity = -80f,
            resistance = 0.36f,
            maxOverscrollPx = 52f
        )
        assertEquals(7.28f, overscroll, 0.01f)
    }
}
