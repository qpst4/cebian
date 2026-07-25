package com.slideindex.app.ui.settings.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCardScopeTest {
    @Test
    fun groupCoordinatorRegistersRowsInOrder() {
        val coordinator = SettingsCardGroupCoordinator()
        coordinator.register("read_logs") {}
        coordinator.register("self_hook") {}

        assertEquals(2, coordinator.rowCount)
    }

    @Test
    fun groupCoordinatorClearRemovesRows() {
        val coordinator = SettingsCardGroupCoordinator()
        coordinator.register("row") {}
        coordinator.clear()

        assertEquals(0, coordinator.rowCount)
    }
}
