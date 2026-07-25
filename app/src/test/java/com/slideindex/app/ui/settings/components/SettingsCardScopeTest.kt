package com.slideindex.app.ui.settings.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCardScopeTest {
    @Test
    fun segmentKeysAreStableAcrossConditionalRebuilds() {
        val scope = SettingsCardScope()
        scope.reset()
        scope.segment("read_logs") {}
        scope.segment("self_hook") {}

        assertEquals(listOf("read_logs", "self_hook"), scope.segments.map { it.key })
    }

    @Test
    fun resetClearsSegmentsAndDecorations() {
        val scope = SettingsCardScope()
        scope.decoration("hint") {}
        scope.segment("row") {}
        scope.reset()

        assertEquals(0, scope.segments.size)
        assertEquals(0, scope.decorations.size)
    }
}
