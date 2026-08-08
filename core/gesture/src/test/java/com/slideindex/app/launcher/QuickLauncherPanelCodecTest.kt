package com.slideindex.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickLauncherPanelCodecTest {
    @Test
    fun encodeDecode_roundTrip() {
        val panel = QuickLauncherPanelDefaults.defaultPanel(
            name = "工作",
            columnsPerPage = 4,
            rowsPerPage = 5,
            items = listOf(QuickLauncherItem.app("com.example.app", "示例")),
            id = "panel-1",
        )
        val decoded = QuickLauncherPanelCodec.decodeAll(QuickLauncherPanelCodec.encodeAll(listOf(panel))).first()
        assertEquals(panel, decoded)
    }

    @Test
    fun encodeDecode_multiplePanels() {
        val panels = listOf(
            QuickLauncherPanelDefaults.defaultPanel(id = "a", name = "A"),
            QuickLauncherPanelDefaults.defaultPanel(id = "b", name = "B"),
        )
        val decoded = QuickLauncherPanelCodec.decodeAll(QuickLauncherPanelCodec.encodeAll(panels))
        assertEquals(panels, decoded)
    }

    @Test
    fun encodeDecode_panelWithManyItems() {
        val items = List(12) { index ->
            QuickLauncherItem.app("com.example.app$index", "App $index")
        }
        val panel = QuickLauncherPanelDefaults.defaultPanel(
            id = "panel-many",
            name = "Many",
            items = items,
        )
        val decoded = QuickLauncherPanelCodec.decodeAll(QuickLauncherPanelCodec.encodeAll(listOf(panel))).first()
        assertEquals(panel, decoded)
    }
}
