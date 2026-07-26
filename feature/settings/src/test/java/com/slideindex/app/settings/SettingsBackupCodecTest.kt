package com.slideindex.app.settings

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        repository.setServiceEnabled(false)
        repository.setLeftEdgeEnabled(true)
        repository.setOnboardingCompleted(false)

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val importedCount = repository.importSettings(inStream).getOrThrow().preferencesImported

        assertTrue(importedCount > 0)
        assertTrue(repository.readSnapshot().serviceEnabled)
        assertFalse(repository.readSnapshot().leftEdgeEnabled)
        assertFalse(repository.readSnapshot().onboardingCompleted)
    }

    @Test
    fun exportImport_roundTrip_preservesLongPreferences() = runBlocking {
        repository.setFaceDownHoldDurationMs(1_200L)
        repository.setFaceDownCooldownMs(4_000L)

        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        repository.setFaceDownHoldDurationMs(800L)
        repository.setFaceDownCooldownMs(2_000L)

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        repository.importSettings(inStream).getOrThrow()

        assertEquals(1_200L, repository.readSnapshot().faceDownGestureSettings.holdDurationMs)
        assertEquals(4_000L, repository.readSnapshot().faceDownGestureSettings.cooldownMs)
    }

    @Test
    fun exportImport_includesSearchIconsDirectory() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val iconDir = File(context.filesDir, "search_icons").apply { mkdirs() }
        File(iconDir, "custom-test.png").writeBytes(byteArrayOf(1, 2, 3))

        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        iconDir.deleteRecursively()

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        repository.importSettings(inStream).getOrThrow()

        val restored = File(iconDir, "custom-test.png")
        assertTrue(restored.exists())
        assertEquals(3, restored.readBytes().size)
    }

    @Test
    fun importSettings_rejectsUnsupportedFormat() = runBlocking {
        val invalid = """{"formatVersion":99,"exportedAtEpochMs":1,"appVersionName":"1.0","preferences":[]}"""
        val outStream = ByteArrayOutputStream()
        ZipOutputStream(outStream).use { zos ->
            zos.putNextEntry(ZipEntry("settings.json"))
            zos.write(invalid.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val inStream = ByteArrayInputStream(outStream.toByteArray())

        val result = repository.importSettings(inStream)

        assertTrue(result.isFailure)
    }

    @Test
    fun importSettings_acceptsLegacySearchEngineIconsPath() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val outStream = ByteArrayOutputStream()
        ZipOutputStream(outStream).use { zos ->
            zos.putNextEntry(ZipEntry("settings.json"))
            zos.write(
                """
                {
                  "formatVersion": 2,
                  "exportedAtEpochMs": 1,
                  "appVersionName": "1.0",
                  "preferences": [
                    {"key":"service_enabled","type":"boolean","value":"true"}
                  ]
                }
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("search_engine_icons/legacy.png"))
            zos.write(byteArrayOf(9, 8, 7))
            zos.closeEntry()
        }

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        repository.importSettings(inStream).getOrThrow()

        val restored = File(context.filesDir, "search_icons/legacy.png")
        assertTrue(restored.exists())
        assertEquals(3, restored.readBytes().size)
    }
}

private suspend fun clearTestSettings(context: Context) {
    val editor = SettingsPreferencesEditor(context)
    editor.edit { prefs ->
        prefs.asMap().keys.toList().forEach { key -> prefs.remove(key) }
    }
}
