package com.slideindex.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IndexRailLettersTest {
    @Test
    fun resolve_showAll_returnsFullAlphabet() {
        assertEquals(IndexRailLetters.full, IndexRailLetters.resolve(setOf('A'), hideEmpty = false))
    }

    @Test
    fun resolve_hideEmpty_keepsOnlyPresentLettersInOrder() {
        assertEquals(
            listOf('A', 'B', 'Z', '#'),
            IndexRailLetters.resolve(setOf('Z', '#', 'A', 'B'), hideEmpty = true),
        )
    }

    @Test
    fun resolve_hideEmpty_withoutHash_omitsHash() {
        assertEquals(
            listOf('M'),
            IndexRailLetters.resolve(setOf('M'), hideEmpty = true),
        )
    }

    @Test
    fun resolve_hideEmpty_withNoApps_fallsBackToFullAlphabet() {
        assertEquals(IndexRailLetters.full, IndexRailLetters.resolve(emptySet(), hideEmpty = true))
    }
}
