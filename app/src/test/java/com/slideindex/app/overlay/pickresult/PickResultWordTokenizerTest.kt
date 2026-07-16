package com.slideindex.app.overlay.pickresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickResultWordTokenizerTest {
    @Test
    fun `tokenizeSelectableWords splits latin words`() {
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords("hello world")
        assertEquals(listOf("hello", "world"), tokens)
    }

    @Test
    fun `tokenizeSelectableWords preserves chinese text`() {
        val source = "北京大学"
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords(source)
        assertTrue(tokens.isNotEmpty())
        assertEquals(source, tokens.joinToString(""))
    }

    @Test
    fun `breakCjkWords falls back to single characters when no dictionary split`() {
        val words = PickResultWordTokenizer.breakCjkWords("北京大学")
        assertEquals("北京大学", words.joinToString(""))
        assertTrue(words.size >= 2)
    }

    @Test
    fun `tokenizeSelectableWords handles mixed chinese and latin`() {
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords("我有10个video")
        assertTrue(tokens.contains("10"))
        assertTrue(tokens.any { it.contains("video", ignoreCase = true) })
        assertEquals("我有10个video", tokens.filterNot {
            PickResultWordTokenizer.isDelimiterToken(it)
        }.joinToString(""))
    }

    @Test
    fun `splitSelectedTokensToChars splits multi char selections`() {
        val result = PickResultWordTokenizer.splitSelectedTokensToChars(
            tokens = listOf("北京", "大学"),
            selectedIndices = setOf(0),
        )
        requireNotNull(result)
        assertEquals(listOf("北", "京", "大学"), result.tokens)
        assertEquals(setOf(0, 1), result.selectedIndices)
    }

    @Test
    fun `breakCjkWords keeps punctuation as separate tokens in selectable flow`() {
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords("你好，世界")
        assertTrue(tokens.contains("，"))
    }
}
