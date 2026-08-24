package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardFloatOrientationGeometryTest {
    @Test
    fun mergePreservingUnset_keepsExistingCoordinatesWhenIncomingUnset() {
        val existing = ClipboardFloatOrientationGeometry(
            panelX = 100,
            panelY = 200,
            panelWidthDp = 320,
            panelHeightDp = 400,
            chipX = 500,
            chipY = 600,
        )
        val incoming = ClipboardFloatOrientationGeometry(
            panelX = ClipboardFloatWindowMetrics.UNSET_POSITION,
            panelY = ClipboardFloatWindowMetrics.UNSET_POSITION,
            panelWidthDp = 280,
            panelHeightDp = 360,
            chipX = ClipboardFloatWindowMetrics.UNSET_POSITION,
            chipY = ClipboardFloatWindowMetrics.UNSET_POSITION,
        )

        val merged = incoming.mergePreservingUnset(existing)

        assertEquals(100, merged.panelX)
        assertEquals(200, merged.panelY)
        assertEquals(500, merged.chipX)
        assertEquals(600, merged.chipY)
        assertEquals(280, merged.panelWidthDp)
        assertEquals(360, merged.panelHeightDp)
    }

    @Test
    fun mergePreservingUnset_overwritesWhenIncomingHasValues() {
        val existing = ClipboardFloatOrientationGeometry(
            chipX = 10,
            chipY = 20,
            panelX = 30,
            panelY = 40,
        )
        val incoming = ClipboardFloatOrientationGeometry(
            chipX = 111,
            chipY = 222,
            panelX = 333,
            panelY = 444,
        )

        val merged = incoming.mergePreservingUnset(existing)

        assertEquals(111, merged.chipX)
        assertEquals(222, merged.chipY)
        assertEquals(333, merged.panelX)
        assertEquals(444, merged.panelY)
    }
}
