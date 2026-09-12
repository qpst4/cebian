package com.slideindex.app.launcher

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLauncherPanelCodecSanitizeTest {

    @Test
    fun decodePanel_sanitizesIneligibleActionItems() {
        val panel = QuickLauncherPanelDefaults.defaultPanel(
            items = listOf(QuickLauncherItem.action(GestureAction.RegionalScreenshotPick)),
        )
        val encoded = QuickLauncherPanelCodec.encodeAll(listOf(panel)).first()
        val decoded = QuickLauncherPanelCodec.decodePanel(encoded)!!
        assertTrue(decoded.items.single().let { item ->
            QuickLauncherItemCodec.parseActionPayload(item.payload) is GestureAction.None
        })
    }
}
