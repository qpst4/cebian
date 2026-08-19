package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.PanelSide.LEFT
import com.slideindex.app.overlay.PanelSide.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class QuickLauncherPanelLayoutEngineTest {

    @Test
    fun gridLayoutInfo_clampsColumnsAndRows() {
        val info = QuickLauncherPanelLayoutEngine.gridLayoutInfo(
            columnsPerPage = 10,
            rowsPerPage = 20,
            cellWidth = 100f,
            gridPadding = 8f,
        )
        assertEquals(6, info.panelColumns)
        assertEquals(9, info.rows)
        assertEquals(6 * 100f + 16f, info.panelWidth, 0.01f)
    }

    @Test
    fun contentHeight_includesHeaderAndPadding() {
        val height = QuickLauncherPanelLayoutEngine.contentHeight(
            rows = 4,
            cellHeight = 120f,
            gridPadding = 8f,
            headerHeight = 48f,
        )
        assertEquals(4 * 120f + 16f + 48f, height, 0.01f)
    }

    @Test
    fun anchoredPanelRect_placesPanelToRightOfLeftTrigger() {
        val host = fakeHost(
            side = LEFT,
            trigger = RectF(0f, 400f, 24f, 800f),
        )
        val rect = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 600f,
        )
        assertEquals(24f + host.dp(8f), rect.left, 0.01f)
        assertEquals(400f, rect.height(), 0.01f)
        assertEquals(600f - 200f, rect.top, 0.01f)
    }

    @Test
    fun anchoredPanelRect_placesPanelToLeftOfRightTrigger() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 1080,
            trigger = RectF(1056f, 400f, 1080f, 800f),
        )
        val rect = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 600f,
        )
        assertEquals(1056f - host.dp(8f) - 300f, rect.left, 0.01f)
    }

    @Test
    fun offsetForToolbar_shiftsRightPanelAwayFromToolbarReserve() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 400,
            trigger = RectF(376f, 0f, 400f, 200f),
        )
        val base = RectF(20f, 100f, 320f, 500f)
        val adjusted = QuickLauncherPanelLayoutEngine.offsetForToolbar(host, base, reserveWidth = 120f)
        assertEquals(84f, adjusted.left, 0.01f)
        assertEquals(384f, adjusted.right, 0.01f)
    }

    private fun fakeHost(
        side: PanelSide,
        viewWidth: Int = 1080,
        viewHeight: Int = 1920,
        trigger: RectF = RectF(0f, 400f, 24f, 800f),
    ): OverlayPanelLayoutHost = object : OverlayPanelLayoutHost {
        override fun side(): PanelSide = side
        override fun activeTriggerZoneRect(): RectF = trigger
        override fun viewWidth(): Int = viewWidth
        override fun viewHeight(): Int = viewHeight
        override fun dp(value: Float): Float = value
    }
}
