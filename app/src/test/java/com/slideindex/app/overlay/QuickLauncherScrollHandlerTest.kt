package com.slideindex.app.overlay

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLauncherScrollHandlerTest {

    @Test
    fun pageCommitOffsetCompensation_leftPanel_advancesWithPositiveOffset() {
        assertEquals(
            200f,
            QuickLauncherScrollHandler.pageCommitOffsetCompensation(
                delta = 1,
                pageWidth = 200f,
                side = PanelSide.LEFT,
            ),
            0.01f,
        )
        assertEquals(
            -200f,
            QuickLauncherScrollHandler.pageCommitOffsetCompensation(
                delta = -1,
                pageWidth = 200f,
                side = PanelSide.LEFT,
            ),
            0.01f,
        )
    }

    @Test
    fun pageCommitOffsetCompensation_rightPanel_invertsDirection() {
        assertEquals(
            -200f,
            QuickLauncherScrollHandler.pageCommitOffsetCompensation(
                delta = 1,
                pageWidth = 200f,
                side = PanelSide.RIGHT,
            ),
            0.01f,
        )
        assertEquals(
            200f,
            QuickLauncherScrollHandler.pageCommitOffsetCompensation(
                delta = -1,
                pageWidth = 200f,
                side = PanelSide.RIGHT,
            ),
            0.01f,
        )
    }

    @Test
    fun computePageCommitDelta_leftPanel_advancesWhenSwipedLeftPastThreshold() {
        val delta = QuickLauncherScrollHandler.computePageCommitDelta(
            offset = -50f,
            panelWidth = 200f,
            pageIndex = 0,
            pageCount = 3,
            side = PanelSide.LEFT,
        )

        assertEquals(1, delta)
    }

    @Test
    fun computePageCommitDelta_leftPanel_retreatsWhenSwipedRightPastThreshold() {
        val delta = QuickLauncherScrollHandler.computePageCommitDelta(
            offset = 50f,
            panelWidth = 200f,
            pageIndex = 2,
            pageCount = 3,
            side = PanelSide.LEFT,
        )

        assertEquals(-1, delta)
    }

    @Test
    fun computePageCommitDelta_rightPanel_advancesWhenSwipedRightPastThreshold() {
        val delta = QuickLauncherScrollHandler.computePageCommitDelta(
            offset = 50f,
            panelWidth = 200f,
            pageIndex = 0,
            pageCount = 3,
            side = PanelSide.RIGHT,
        )

        assertEquals(1, delta)
    }

    @Test
    fun computePageCommitDelta_rightPanel_retreatsWhenSwipedLeftPastThreshold() {
        val delta = QuickLauncherScrollHandler.computePageCommitDelta(
            offset = -50f,
            panelWidth = 200f,
            pageIndex = 2,
            pageCount = 3,
            side = PanelSide.RIGHT,
        )

        assertEquals(-1, delta)
    }

    @Test
    fun computePageCommitDelta_staysWhenBelowThreshold() {
        val delta = QuickLauncherScrollHandler.computePageCommitDelta(
            offset = -10f,
            panelWidth = 200f,
            pageIndex = 0,
            pageCount = 3,
        )

        assertEquals(0, delta)
    }

    @Test
    fun computePageCommitDelta_doesNotAdvancePastLastPage() {
        val delta = QuickLauncherScrollHandler.computePageCommitDelta(
            offset = -50f,
            panelWidth = 200f,
            pageIndex = 2,
            pageCount = 3,
        )

        assertEquals(0, delta)
    }

    @Test
    fun adjacentPagesForDrag_leftPanel_revealsNextPageToTheRight() {
        val layers = QuickLauncherScrollHandler.adjacentPagesForDrag(
            dragOffset = -40f,
            currentPageIndex = 0,
            pageCount = 3,
            pageWidth = 200f,
            side = PanelSide.LEFT,
        )

        assertEquals(1, layers.size)
        assertEquals(1, layers[0].pageIndex)
        assertEquals(160f, layers[0].translateX, 0.01f)
    }

    @Test
    fun adjacentPagesForDrag_rightPanel_revealsNextPageToTheLeft() {
        val layers = QuickLauncherScrollHandler.adjacentPagesForDrag(
            dragOffset = 40f,
            currentPageIndex = 0,
            pageCount = 3,
            pageWidth = 200f,
            side = PanelSide.RIGHT,
        )

        assertEquals(1, layers.size)
        assertEquals(1, layers[0].pageIndex)
        assertEquals(-160f, layers[0].translateX, 0.01f)
    }

    @Test
    fun computeEdgePageZone_leftPanel_outerEdgeIsPreviousPage() {
        val panel = RectF(0f, 0f, 100f, 100f)

        assertEquals(
            -1,
            QuickLauncherScrollHandler.computeEdgePageZone(
                touchX = 5f,
                panelRect = panel,
                side = PanelSide.LEFT,
                edgePx = 14f,
            ),
        )
    }

    @Test
    fun computeEdgePageZone_leftPanel_innerEdgeIsNextPage() {
        val panel = RectF(0f, 0f, 100f, 100f)

        assertEquals(
            1,
            QuickLauncherScrollHandler.computeEdgePageZone(
                touchX = 95f,
                panelRect = panel,
                side = PanelSide.LEFT,
                edgePx = 14f,
            ),
        )
    }

    @Test
    fun computeEdgePageZone_rightPanel_invertsEdges() {
        val panel = RectF(0f, 0f, 100f, 100f)

        assertEquals(
            1,
            QuickLauncherScrollHandler.computeEdgePageZone(
                touchX = 5f,
                panelRect = panel,
                side = PanelSide.RIGHT,
                edgePx = 14f,
            ),
        )
        assertEquals(
            -1,
            QuickLauncherScrollHandler.computeEdgePageZone(
                touchX = 95f,
                panelRect = panel,
                side = PanelSide.RIGHT,
                edgePx = 14f,
            ),
        )
    }

    @Test
    fun isWithinTapSlop_allowsSmallMovement() {
        assertTrue(QuickLauncherManagementTouchHandler.isWithinTapSlop(3f, 4f, 24f))
    }

    @Test
    fun isWithinTapSlop_rejectsLargeMovement() {
        assertFalse(QuickLauncherManagementTouchHandler.isWithinTapSlop(30f, 0f, 24f))
    }
}
