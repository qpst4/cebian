package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureZoneLayoutTouchBoundsTest {
    @Test
    fun touchCaptureBounds_useTriggerWidthNotHalo() {
        val handle = TriggerHandle.default().copy(
            edgeWidthDp = 0f,
            design = TriggerHandleDesign(
                kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
                sizeDp = 1f,
                haloSizeDp = 10f,
            ),
        )
        val settings = testSettings(handle)
        val density = 3f
        val touch = GestureZoneLayout.computeTouchCaptureWindowBounds(
            settings = settings,
            side = PanelSide.LEFT,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = density,
        )
        val visual = GestureZoneLayout.computeTriggerVisualWindowBounds(
            settings = settings,
            side = PanelSide.LEFT,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = density,
        )
        assertEquals(0, touch.single().widthPx)
        assertTrue(visual.single().widthPx > touch.single().widthPx)
    }

    private fun testSettings(handle: TriggerHandle): AppSettings =
        AppSettings(
            freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
            edgeTrigger = EdgeTriggerSettings(leftTriggerHandles = listOf(handle)),
        )
}
