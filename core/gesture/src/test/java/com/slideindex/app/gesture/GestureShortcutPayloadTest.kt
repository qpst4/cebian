package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureShortcutPayloadTest {

    @Test
    fun encodeDecode_dynamic_roundTrip() {
        val encoded = GestureShortcutPayload.encodeDynamic("com.bank", "pay", "付款")

        val decoded = GestureShortcutPayload.decode(encoded) as GestureShortcutPayload.Decoded.Dynamic

        assertEquals("com.bank", decoded.packageName)
        assertEquals("pay", decoded.shortcutId)
        assertEquals("付款", decoded.label)
    }

    @Test
    fun encodeDecode_component_roundTrip() {
        val encoded = GestureShortcutPayload.encodeComponent("com.app/.MainActivity", "主页")

        val decoded = GestureShortcutPayload.decode(encoded) as GestureShortcutPayload.Decoded.Component

        assertEquals("com.app/.MainActivity", decoded.componentFlat)
        assertEquals("主页", decoded.label)
    }

    @Test
    fun encodeDecode_intent_roundTrip() {
        val uri = "#Intent;action=android.intent.action.VIEW;end"
        val encoded = GestureShortcutPayload.encodeIntent(uri, "打开")

        val decoded = GestureShortcutPayload.decode(encoded) as GestureShortcutPayload.Decoded.IntentShortcut

        assertEquals(uri, decoded.intentUri)
        assertEquals("打开", decoded.label)
    }

    @Test
    fun encodeDecode_intents_roundTrip() {
        val uris = listOf("#Intent;action=one;end", "#Intent;action=two;end")
        val encoded = GestureShortcutPayload.encodeIntents(uris, "多意图")

        val decoded = GestureShortcutPayload.decode(encoded) as GestureShortcutPayload.Decoded.IntentsShortcut

        assertEquals(uris, decoded.intentUris)
        assertEquals("多意图", decoded.label)
    }

    @Test
    fun decode_blank_returnsNull() {
        assertNull(GestureShortcutPayload.decode(""))
        assertNull(GestureShortcutPayload.decode("   "))
    }

    @Test
    fun encode_withoutLabel_omitsLabelSeparator() {
        val encoded = GestureShortcutPayload.encodeComponent("com.app/.Activity", "")

        assertTrue(!encoded.contains("\u001D"))
        assertEquals(
            GestureShortcutPayload.Decoded.Component("com.app/.Activity", ""),
            GestureShortcutPayload.decode(encoded),
        )
    }
}
