package com.slideindex.app.clipboardfloat

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardPasteTextLogicTest {

    @Test
    fun effectiveInputText_blankOrWhitespace_returnsEmpty() {
        assertEquals("", ClipboardPasteTextLogic.effectiveInputText(null))
        assertEquals("", ClipboardPasteTextLogic.effectiveInputText(""))
        assertEquals("", ClipboardPasteTextLogic.effectiveInputText("   "))
        assertEquals("", ClipboardPasteTextLogic.effectiveInputText("\n\n"))
    }

    @Test
    fun effectiveInputText_matchesHint_returnsEmpty() {
        assertEquals(
            "",
            ClipboardPasteTextLogic.effectiveInputText("输入消息", "输入消息"),
        )
    }

    @Test
    fun effectiveInputText_stripsLeadingPlaceholderNewlines() {
        assertEquals("hello", ClipboardPasteTextLogic.effectiveInputText("\n\nhello"))
    }

    @Test
    fun snapshotEditableText_newlineOnlyPlaceholder_tracksLength() {
        val snapshot = ClipboardPasteTextLogic.snapshotEditableText("\n\n")
        assertEquals("", snapshot.content)
        assertEquals(2, snapshot.leadingPlaceholderLength)
    }

    @Test
    fun snapshotEditableText_tracksLeadingPlaceholderLength() {
        val snapshot = ClipboardPasteTextLogic.snapshotEditableText("\n\nhello")
        assertEquals("hello", snapshot.content)
        assertEquals(2, snapshot.leadingPlaceholderLength)
    }

    @Test
    fun mergeClipAtSelection_emptyCurrent_returnsClipOnly() {
        assertEquals(
            "hello",
            ClipboardPasteTextLogic.mergeClipAtSelection("", "hello", 0, 0),
        )
    }

    @Test
    fun mergeClipAtSelection_withSelection_insertsAtCursor() {
        assertEquals(
            "heXXllo",
            ClipboardPasteTextLogic.mergeClipAtSelection("hello", "XX", 2, 2),
        )
    }
}
