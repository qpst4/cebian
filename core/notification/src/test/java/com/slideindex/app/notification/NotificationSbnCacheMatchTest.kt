package com.slideindex.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSbnCacheMatchTest {

    @Test
    fun matchesPostTime_whenLookupUnknown_allowsAnyCachedPostTime() {
        assertTrue(NotificationSbnCache.matchesPostTimeForTest(cachedPostTime = 0L, lookupPostTime = 0L))
        assertTrue(NotificationSbnCache.matchesPostTimeForTest(cachedPostTime = 1_234L, lookupPostTime = 0L))
    }

    @Test
    fun matchesPostTime_whenCachedPostTimeUnknown_allowsLegacyLookup() {
        assertTrue(
            NotificationSbnCache.matchesPostTimeForTest(
                cachedPostTime = 0L,
                lookupPostTime = 1_784_805_010_878L,
            ),
        )
    }

    @Test
    fun matchesPostTime_whenBothKnown_requiresExactMatch() {
        assertTrue(
            NotificationSbnCache.matchesPostTimeForTest(
                cachedPostTime = 1_784_805_010_878L,
                lookupPostTime = 1_784_805_010_878L,
            ),
        )
        assertFalse(
            NotificationSbnCache.matchesPostTimeForTest(
                cachedPostTime = 1_784_805_010_897L,
                lookupPostTime = 1_784_805_010_878L,
            ),
        )
    }
}
