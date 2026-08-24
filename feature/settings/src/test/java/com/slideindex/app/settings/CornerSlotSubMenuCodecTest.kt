package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerSlotSubMenuCodecTest {

    @Test
    fun encodeDecode_roundTrip() {
        val shortcuts = listOf(
            GestureAction.LaunchShortcut.intent("alipayqr://platformapi/startapp?saId=10000007", "扫一扫"),
            GestureAction.LaunchShortcut.component("com.test/.Main", "测试"),
        )
        val slots = CornerSlotSubMenuCodec.defaultSlotSubMenus().toMutableList()
        slots[2] = CornerSlotSubMenuConfig(enabled = true, items = shortcuts)
        val encoded = CornerSlotSubMenuCodec.encode(slots)
        val decoded = CornerSlotSubMenuCodec.decode(encoded, CornerSlotSubMenuCodec.defaultSlotSubMenus())
        assertEquals(CornerSlotSubMenuConfig(enabled = true, items = shortcuts), decoded[2])
    }

    @Test
    fun encodeDecode_preservesLabelsWithEntrySeparatorInPayload() {
        val shortcuts = listOf(
            GestureAction.LaunchShortcut.intent("alipayqr://platformapi/startapp?saId=10000007", "扫一扫"),
            GestureAction.LaunchShortcut.dynamic("com.tencent.mm", "pay", "付款码"),
        )
        val slots = CornerSlotSubMenuCodec.defaultSlotSubMenus().toMutableList()
        slots[1] = CornerSlotSubMenuConfig(enabled = true, items = shortcuts)
        val encoded = CornerSlotSubMenuCodec.encode(slots)
        val decoded = CornerSlotSubMenuCodec.decode(encoded, CornerSlotSubMenuCodec.defaultSlotSubMenus())
        assertEquals(CornerSlotSubMenuConfig(enabled = true, items = shortcuts), decoded[1])
    }
}
