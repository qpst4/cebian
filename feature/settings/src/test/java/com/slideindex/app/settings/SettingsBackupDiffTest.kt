package com.slideindex.app.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupDiffTest {
    @Test
    fun computeImportDiff_splitsOverwrittenAndNewKeys() {
        val current = mutablePreferencesOf(
            booleanPreferencesKey("service_enabled") to true,
            booleanPreferencesKey("left_edge_enabled") to true,
        )
        val backup = SettingsBackupDocument(
            formatVersion = 2,
            exportedAtEpochMs = 1L,
            appVersionName = "1.0",
            preferences = listOf(
                SettingsPreferenceEntry("service_enabled", "boolean", "false"),
                SettingsPreferenceEntry("right_edge_enabled", "boolean", "true"),
            ),
        )
        val diff = computeSettingsBackupImportDiff(current, backup)
        assertEquals(listOf("service_enabled"), diff.overwrittenKeys)
        assertEquals(listOf("right_edge_enabled"), diff.newKeys)
        assertTrue(diff.hasChanges)
    }
}
