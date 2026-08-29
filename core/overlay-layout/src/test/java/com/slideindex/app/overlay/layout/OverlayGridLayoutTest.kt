package com.slideindex.app.overlay.layout

import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
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
    fun visualColumn_rightPanel_fullRow_mirrorsColumns() {
        assertEquals(3, visualColumn(index = 0, m = 4, appCount = 4, side = PanelSide.RIGHT))
        assertEquals(0, visualColumn(index = 3, m = 4, appCount = 4, side = PanelSide.RIGHT))
    }

    @Test
    fun visualColumn_rightPanel_partialLastRow_ordersRightToLeft() {
        assertEquals(3, visualColumn(index = 8, m = 4, appCount = 10, side = PanelSide.RIGHT))
        assertEquals(2, visualColumn(index = 9, m = 4, appCount = 10, side = PanelSide.RIGHT))
    }

    @Test
    fun visualColumn_leftPanel_partialRow_packsFromLeft() {
        assertEquals(0, visualColumn(index = 0, m = 4, appCount = 1, side = PanelSide.LEFT))
        assertEquals(1, visualColumn(index = 1, m = 4, appCount = 3, side = PanelSide.LEFT))
    }
}
