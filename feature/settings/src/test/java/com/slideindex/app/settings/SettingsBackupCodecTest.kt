package com.slideindex.app.settings

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SettingsBackupCodecTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            repository = testSettingsRepository(context)
            clearTestSettings(context)
            repository.setServiceEnabled(true)
            repository.setLeftEdgeEnabled(false)
            repository.setOnboardingCompleted(true)
        }
    }

    @Test
    fun exportImport_roundTrip_restoresSettingsWithoutOnboardingFlag() = runBlocking {
        val outStream = java.io.ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()
        
        repository.setServiceEnabled(false)
        repository.setLeftEdgeEnabled(true)
        repository.setOnboardingCompleted(false)

        val inStream = java.io.ByteArrayInputStream(outStream.toByteArray())
        val importedCount = repository.importSettings(inStream).getOrThrow().preferencesImported

        assertTrue(importedCount > 0)
        assertTrue(repository.readSnapshot().serviceEnabled)
        assertFalse(repository.readSnapshot().leftEdgeEnabled)
        assertFalse(repository.readSnapshot().onboardingCompleted)
    }

    @Test
    fun importSettings_rejectsUnsupportedFormat() = runBlocking {
        val invalid = """{"formatVersion":99,"exportedAtEpochMs":1,"appVersionName":"1.0","preferences":[]}"""
        val outStream = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(outStream).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("settings.json"))
            zos.write(invalid.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val inStream = java.io.ByteArrayInputStream(outStream.toByteArray())

        val result = repository.importSettings(inStream)

        assertTrue(result.isFailure)
    }
}

private suspend fun clearTestSettings(context: Context) {
    val editor = SettingsPreferencesEditor(context)
    editor.edit { prefs ->
        prefs.asMap().keys.toList().forEach { key -> prefs.remove(key) }
    }
}
