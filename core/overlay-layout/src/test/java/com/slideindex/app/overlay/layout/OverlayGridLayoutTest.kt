package com.slideindex.app.overlay.layout

import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGridLayoutTest {

    @Test
    fun visualColumn_rightPanel_singleItem_alignsToOnlyColumn() {
        assertEquals(0, visualColumn(index = 0, m = 4, appCount = 1, side = PanelSide.RIGHT))
    }

    @Test
    fun visualColumn_rightPanel_partialRow_packsFromRight() {
        assertEquals(2, visualColumn(index = 0, m = 4, appCount = 3, side = PanelSide.RIGHT))
        assertEquals(1, visualColumn(index = 1, m = 4, appCount = 3, side = PanelSide.RIGHT))
        assertEquals(0, visualColumn(index = 2, m = 4, appCount = 3, side = PanelSide.RIGHT))
    }

    @Test
    fun gridLayoutInfo_withZeroApps_returnsZeroDimensions() {
        val info = gridLayoutInfo(
            appCount = 0,
            appsPerRow = 3,
            cellWidth = 68f,
            gridPadding = 10f,
        )
        assertEquals(3, info.appsPerRow)
        assertEquals(0, info.panelColumns)
        assertEquals(0, info.rows)
        assertEquals(0f, info.panelWidth, 0.001f)
    }

    @Test
    fun gridLayoutInfo_withAppsLessThanAppsPerRow_adjustsColumns() {
        val info = gridLayoutInfo(
            appCount = 2,
            appsPerRow = 4,
            cellWidth = 68f,
            gridPadding = 10f,
        )
        assertEquals(4, info.appsPerRow)
        assertEquals(2, info.panelColumns)
        assertEquals(1, info.rows)
        assertEquals(2 * 68f + 20f, info.panelWidth, 0.001f)
    }

    @Test
    fun gridLayoutInfo_withMultipleRows() {
        val info = gridLayoutInfo(
            appCount = 7,
            appsPerRow = 3,
            cellWidth = 68f,
            gridPadding = 10f,
        )
        assertEquals(3, info.appsPerRow)
        assertEquals(3, info.panelColumns)
        assertEquals(3, info.rows)
        assertEquals(3 * 68f + 20f, info.panelWidth, 0.001f)
    }

    @Test
    fun visualColumn_leftAndRightSides() {
        assertEquals(0, visualColumn(index = 0, m = 3, appCount = 3, side = PanelSide.LEFT))
        assertEquals(1, visualColumn(index = 1, m = 3, appCount = 3, side = PanelSide.LEFT))
        assertEquals(2, visualColumn(index = 2, m = 3, appCount = 3, side = PanelSide.LEFT))

        assertEquals(2, visualColumn(index = 0, m = 3, appCount = 3, side = PanelSide.RIGHT))
        assertEquals(1, visualColumn(index = 1, m = 3, appCount = 3, side = PanelSide.RIGHT))
        assertEquals(0, visualColumn(index = 2, m = 3, appCount = 3, side = PanelSide.RIGHT))
    }
}
