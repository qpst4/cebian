package com.slideindex.app.overlay

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SideBubbleTextTest {
    @Test
    fun buildContentText_keepsGroupSenderPrefix() {
        val annotated = buildSideBubbleContentText(
            text = "Alice: hello",
            titleColor = Color.White,
            contentColor = Color.Gray,
        )
        assertEquals("Alice: hello", annotated.text)
    }

    @Test
    fun buildContentText_plainWhenNoColon() {
        val annotated = buildSideBubbleContentText(
            text = "验证码 123456",
            titleColor = Color.White,
            contentColor = Color.Gray,
        )
        assertEquals("验证码 123456", annotated.text)
    }

    @Test
    fun resolveContent_stripsSenderForDirectChat() {
        val resolved = resolveSideBubbleContent(
            title = "Alice",
            content = "Alice: hello",
        )
        assertEquals("hello", resolved)
    }

    @Test
    fun resolveContent_keepsSenderForGroupChat() {
        val resolved = resolveSideBubbleContent(
            title = "项目群",
            content = "Alice: hello",
        )
        assertEquals("Alice: hello", resolved)
    }

    @Test
    fun resolveContent_removesBracketCountMarker() {
        val resolved = resolveSideBubbleContent(
            title = "项目群",
            content = "Alice[3条]: hello",
        )
        assertEquals("Alice: hello", resolved)
    }
}
