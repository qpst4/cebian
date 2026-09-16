package com.slideindex.app.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SearchEngineIconMaterializerTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = testSettingsRepository(context)
        runBlocking { clearTestSettings(context) }
    }

    @Test
    fun materialize_keepsExistingUriFile() {
        val iconPath = writeTestIconFile()
        val engine = SearchEngineConfig(
            id = "uri-engine",
            name = "Uri",
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "https://example.com?q=%s",
            iconType = SearchIconType.URI,
            iconPath = iconPath,
        )
        val after = SearchEngineIconMaterializer.materialize(context, listOf(engine)).single()
        assertEquals(engine, after)
    }

    @Test
    fun materialize_otherWithPackage_attemptsMaterializeWithoutCrashing() {
        val engine = SearchEngineConfig(
            id = "other-engine",
            name = "Other",
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "https://example.com?q=%s",
            targetPackage = "com.example.not.installed",
            iconType = SearchIconType.OTHER,
        )
        val after = SearchEngineIconMaterializer.materialize(context, listOf(engine)).single()
        assertTrue(after.iconType == SearchIconType.URI || after.iconType == SearchIconType.OTHER)
    }

    @Test
    fun exportSettings_includesSearchEngineIconFilesAlreadyOnDisk() = runBlocking {
        val iconPath = writeTestIconFile()
        repository.setSearchEngines(
            listOf(
                SearchEngineConfig(
                    id = "export-engine",
                    name = "Export",
                    engineType = SearchEngineType.DIRECT_LINK,
                    searchLink = "https://example.com?q=%s",
                    iconType = SearchIconType.URI,
                    iconPath = iconPath,
                    sortOrder = 0,
                ),
            ),
        )
        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        var hasSearchIconFile = false
        ZipInputStream(outStream.toByteArray().inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory && entry.name.startsWith("search_icons/")) {
                    hasSearchIconFile = true
                }
                zis.closeEntry()
            }
        }
        assertTrue(hasSearchIconFile)
    }

    private fun writeTestIconFile(): String {
        val dir = File(context.filesDir, "search_icons").apply { mkdirs() }
        val fileName = "unit-test-icon.png"
        val file = File(dir, fileName)
        val bytes = ByteArrayOutputStream().use { stream ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        file.writeBytes(bytes)
        return "search_icons/$fileName"
    }
}

private suspend fun clearTestSettings(context: Context) {
    val editor = SettingsPreferencesEditor(context)
    editor.edit { prefs ->
        prefs.asMap().keys.toList().forEach { key -> prefs.remove(key) }
    }
}
