package com.slideindex.app.freezer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezerBootstrapTest {
    @Test
    fun `importable packages skip user excluded apps`() {
        val importable = FreezerBootstrap.importablePackages(
            scanned = setOf("com.example.a", "com.example.b"),
            excluded = setOf("com.example.a"),
        )
        assertEquals(setOf("com.example.b"), importable)
    }

    @Test
    fun `importable packages is empty when all scanned are excluded`() {
        val importable = FreezerBootstrap.importablePackages(
            scanned = setOf("com.example.a"),
            excluded = setOf("com.example.a"),
        )
        assertTrue(importable.isEmpty())
    }
}
