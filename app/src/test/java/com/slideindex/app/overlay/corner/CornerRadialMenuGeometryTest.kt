package com.slideindex.app.overlay.corner

import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CornerRadialMenuGeometryTest {
    private val settings = CornerGestureSettings()
    private val density = 3f
    private val slots = CornerRadialMenuCodec.defaultLeftSlots()

    @Test
    fun slotIndexAt_hitsMiddleAndOuterLayers() {
        val anchorX = 0f
        val anchorY = 2400f
        for (slot in listOf(0, 3, 5, 8, 9)) {
            val center = CornerRadialMenuGeometry.bubbleCenterForSlot(
                anchor = CornerAnchor.LEFT,
                anchorX = anchorX,
                anchorY = anchorY,
                slotIndex = slot,
                settings = settings,
                density = density,
            )
            val hit = CornerRadialMenuGeometry.slotIndexAt(
                anchor = CornerAnchor.LEFT,
                anchorX = anchorX,
                anchorY = anchorY,
                fingerX = center.x,
                fingerY = center.y,
                settings = settings,
                density = density,
                slots = slots,
                editMode = false,
                activeLayerCount = 3,
                revealProgress = 1f,
            )
            assertNotNull("slot $slot should be hit at its center", hit)
            assertEquals(slot, hit)
        }
    }

    @Test
    fun displayLayerCount_expandsForHighlightedOuterSlot() {
        assertEquals(3, CornerRadialMenuGeometry.displayLayerCount(1, 8))
        assertEquals(2, CornerRadialMenuGeometry.displayLayerCount(1, 5))
        assertEquals(1, CornerRadialMenuGeometry.displayLayerCount(1, -1))
    }
}
