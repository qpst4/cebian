package com.slideindex.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentPackageResolverTest {

    @Test
    fun normalizeIdentifier_stripsActivitySuffix() {
        assertEquals(
            "com.example.app",
            RecentPackageResolver.normalizeIdentifier("com.example.app.MainActivity"),
        )
    }

    @Test
    fun normalizeIdentifier_keepsSlashPackage() {
        assertEquals(
            "com.example.app",
            RecentPackageResolver.normalizeIdentifier("com.example.app/.MainActivity"),
        )
    }

    @Test
    fun matches_treatsSettingsAliasesAsSameOwner() {
        assertTrue(
            RecentPackageResolver.matches(
                "com.android.settings",
                "com.meizu.flyme.settings",
            ),
        )
    }

    @Test
    fun matches_rejectsUnrelatedPackages() {
        assertFalse(
            RecentPackageResolver.matches(
                "com.example.a",
                "com.example.b",
            ),
        )
    }
}
