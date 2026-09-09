package com.slideindex.app.settings

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FvAppSwitcherSlotMoveTest {
    private fun appSlot(index: Int): QuickLauncherItem =
        QuickLauncherItem(QuickLauncherItemType.APP, "pkg$index", "App $index")

    @Test
    fun move_to_empty_slot_removes_source() {
        val slots = mutableMapOf(0 to appSlot(0), 2 to appSlot(2))
        assertTrue(slots.moveFvAppSwitcherSlot(fromIndex = 0, toIndex = 1))
        assertNull(slots[0])
        assertEquals(appSlot(0).payload, slots[1]?.payload)
        assertEquals(appSlot(2).payload, slots[2]?.payload)
    }

    @Test
    fun move_swaps_configured_slots() {
        val slots = mutableMapOf(0 to appSlot(0), 3 to appSlot(3))
        assertTrue(slots.moveFvAppSwitcherSlot(fromIndex = 0, toIndex = 3))
        assertEquals(appSlot(3).payload, slots[0]?.payload)
        assertEquals(appSlot(0).payload, slots[3]?.payload)
    }

    @Test
    fun move_same_index_is_noop() {
        val slots = mutableMapOf(0 to appSlot(0))
        assertFalse(slots.moveFvAppSwitcherSlot(fromIndex = 0, toIndex = 0))
    }

    @Test
    fun move_from_empty_slot_is_noop() {
        val slots = mutableMapOf(1 to appSlot(1))
        assertFalse(slots.moveFvAppSwitcherSlot(fromIndex = 0, toIndex = 1))
    }
}
