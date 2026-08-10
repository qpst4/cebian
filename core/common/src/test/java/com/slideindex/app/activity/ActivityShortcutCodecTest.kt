package com.slideindex.app.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityShortcutCodecTest {
    @Test
    fun encodeDecode_legacyFourFields_roundTripAsComponent() {
        val legacy = listOf("id-1", "扫一扫", "com.tencent.mm", "com.tencent.mm.plugin.scanner.ui.BaseScanUI")
            .joinToString("\u001E")
        val decoded = ActivityShortcutCodec.decode(legacy)
        assertNotNull(decoded)
        assertEquals(ActivityShortcutKind.COMPONENT, decoded!!.kind)
        assertEquals("扫一扫", decoded.label)
        assertEquals("com.tencent.mm/com.tencent.mm.plugin.scanner.ui.BaseScanUI", decoded.identityKey())
        assertNull(decoded.iconPath)
    }

    @Test
    fun encodeDecode_dynamicWithIcon_roundTrip() {
        val item = ActivityShortcut.dynamic(
            id = "d1",
            label = "付款",
            packageName = "com.bank",
            shortcutId = "pay",
            iconPath = "shortcut_icons/custom-1.png",
        )
        val decoded = ActivityShortcutCodec.decode(ActivityShortcutCodec.encode(item))
        assertEquals(item, decoded)
        assertEquals(ActivityShortcutKind.DYNAMIC, decoded!!.kind)
        assertEquals("com.bank\u001Cpay", decoded.identityKey())
    }

    @Test
    fun encodeDecode_intentList_roundTrip() {
        val item = ActivityShortcut.intent(
            id = "i1",
            label = "多意图",
            packageName = "com.example",
            intentUris = listOf("intent:#Intent;end", "intent:#Intent;action=x;end"),
        )
        val decoded = ActivityShortcutCodec.decode(ActivityShortcutCodec.encode(item))
        assertEquals(item, decoded)
        assertEquals(ActivityShortcutKind.INTENT, decoded!!.kind)
        assertTrue(decoded.identityKey().startsWith("intents:"))
    }

    @Test
    fun encodeAll_decodeAll_preservesList() {
        val items = listOf(
            ActivityShortcut.component("收藏", "com.tencent.mm", "com.tencent.mm.plugin.fav.ui.FavoriteIndexUI"),
            ActivityShortcut.dynamic("扫", "com.tencent.mm", "scan"),
        )
        val roundTrip = ActivityShortcutCodec.decodeAll(ActivityShortcutCodec.encodeAll(items))
        assertEquals(items.map { it.identityKey() }, roundTrip.map { it.identityKey() })
        assertEquals(items.map { it.label }, roundTrip.map { it.label })
    }

    @Test
    fun findByIdentityKey_matchesComponent() {
        val items = listOf(
            ActivityShortcut.component("收藏", "com.tencent.mm", "com.tencent.mm.plugin.fav.ui.FavoriteIndexUI"),
        )
        val found = items.findByIdentityKey("com.tencent.mm/com.tencent.mm.plugin.fav.ui.FavoriteIndexUI")
        assertNotNull(found)
        assertEquals("收藏", found!!.label)
    }
}
