package com.slideindex.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun pickBetterManifest_prefersHigherVersion() {
        val older = manifest("1.8.2", apkSize = 100L)
        val newer = manifest("1.8.3", apkSize = 0L)
        assertEquals(newer, UpdateChecker.pickBetterManifest(older, newer))
        assertEquals(newer, UpdateChecker.pickBetterManifest(newer, older))
    }

    @Test
    fun pickBetterManifest_sameVersion_prefersNonZeroApkSize() {
        val placeholder = manifest("1.8.3", apkSize = 0L)
        val ready = manifest("1.8.3", apkSize = 49_243_678L)
        assertEquals(ready, UpdateChecker.pickBetterManifest(placeholder, ready))
        assertEquals(ready, UpdateChecker.pickBetterManifest(ready, placeholder))
    }

    @Test
    fun pickBetterManifest_sameVersionBothZero_keepsCurrent() {
        val first = manifest("1.8.3", apkSize = 0L)
        val second = manifest("1.8.3", apkSize = 0L, notes = "other")
        assertEquals(first, UpdateChecker.pickBetterManifest(first, second))
    }

    private fun manifest(version: String, apkSize: Long, notes: String = "") =
        UpdateManifest(
            version = version,
            versionCode = 1,
            apkUrl = "https://example.com/cebian-$version.apk",
            apkSize = apkSize,
            notes = notes,
        )
}
