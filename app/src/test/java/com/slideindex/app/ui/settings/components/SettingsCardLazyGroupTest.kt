package com.slideindex.app.ui.settings.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCardLazyGroupTest {
    @Test
    fun emitCoordinatorGroupedCardSkipsEmptyCoordinator() {
        val coordinator = SettingsCardGroupCoordinator()
        assertEquals(0, coordinator.rowCount)
    }

    @Test
    fun coordinatorRowsPreserveOrderForEmit() {
        val coordinator = SettingsCardGroupCoordinator()
        coordinator.register("first") {}
        coordinator.register("second") {}

        val rows = coordinator.rowsSnapshot()
        assertEquals(2, rows.size)
        assertEquals("first", rows[0].key)
        assertEquals("second", rows[1].key)
    }
}
