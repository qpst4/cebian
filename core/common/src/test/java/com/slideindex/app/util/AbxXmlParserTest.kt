package com.slideindex.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AbxXmlParserTest {
    @Test
    fun isAbxMagic_detectsHeader() {
        val bytes = byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'X'.code.toByte(), 1)
        assertTrue(AbxXmlParser.isAbxMagic(bytes))
    }

    @Test
    fun isAbxMagic_rejectsShortPayload() {
        assertFalse(AbxXmlParser.isAbxMagic(byteArrayOf('A'.code.toByte(), 'B'.code.toByte())))
    }

    @Test
    fun looksLikeTextXml_plainXml() {
        assertTrue(AbxXmlParser.looksLikeTextXml("<shortcuts>"))
        assertTrue(AbxXmlParser.looksLikeTextXml("  <?xml version=\"1.0\"?>"))
    }

    @Test
    fun canReadShortcutServiceXml_usesShellFallback() {
        assertTrue(AbxXmlParser.canReadShortcutServiceXml(abx2XmlShellAvailable = true))
    }
}