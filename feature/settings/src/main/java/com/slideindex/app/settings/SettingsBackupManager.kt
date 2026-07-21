package com.slideindex.app.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editor: SettingsPreferencesEditor,
) {
    suspend fun exportToZip(
        appVersionName: String,
        sensitive: SensitiveBackupSections? = null,
        outputStream: OutputStream,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val preferences = editor.readRawPreferences()
            val json = SettingsBackupCodec.encode(preferences, appVersionName, sensitive)

            ZipOutputStream(outputStream).use { zos ->
                // Write settings.json
                zos.putNextEntry(ZipEntry("settings.json"))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // Copy asset directories
                val dirsToBackup = listOf(
                    "search_engine_icons",
                    "float_ball_assets",
                    "stash"
                )
                
                for (dirName in dirsToBackup) {
                    val dir = File(context.filesDir, dirName)
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relativePath = file.relativeTo(context.filesDir).path
                                // ZipEntry uses forward slash for paths
                                val entryPath = relativePath.replace(File.separatorChar, '/')
                                zos.putNextEntry(ZipEntry(entryPath))
                                file.inputStream().use { input ->
                                    input.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun importFromZip(
        inputStream: InputStream,
        replaceExisting: Boolean = true,
    ): Result<SettingsBackupImportResult> = runCatching {
        withContext(Dispatchers.IO) {
            var document: SettingsBackupDocument? = null
            
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    if (currentEntry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }
                    
                    val name = currentEntry.name
                    if (name == "settings.json") {
                        val json = zis.readBytes().toString(Charsets.UTF_8)
                        document = SettingsBackupCodec.decode(json)
                        SettingsBackupCodec.validate(document!!)
                    } else if (name.startsWith("search_engine_icons/") || 
                               name.startsWith("float_ball_assets/") || 
                               name.startsWith("stash/")) {
                        // Prevent path traversal
                        if (name.contains("..")) continue
                        
                        val targetFile = File(context.filesDir, name)
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { out ->
                            zis.copyTo(out)
                        }
                    }
                    zis.closeEntry()
                }
            }
            
            val finalDocument = requireNotNull(document) { "settings.json not found in backup" }
            
            editor.edit { prefs ->
                if (replaceExisting) {
                    prefs.asMap().keys
                        .filter { it.name != SettingsPreferenceKeys.ONBOARDING_COMPLETED.name }
                        .forEach { key -> prefs.remove(key) }
                }
                SettingsBackupCodec.apply(finalDocument, prefs)
            }
            
            SettingsBackupImportResult(
                preferencesImported = finalDocument.preferences.size,
                sensitive = SensitiveBackupSections(
                    otpRecordsJson = finalDocument.otpRecordsJson,
                    notificationHistoryJson = finalDocument.notificationHistoryJson,
                ),
            )
        }
    }

    suspend fun previewZipImport(inputStream: InputStream): Result<SettingsBackupPreview> = runCatching {
        withContext(Dispatchers.IO) {
            var document: SettingsBackupDocument? = null
            
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry?.name == "settings.json") {
                        val json = zis.readBytes().toString(Charsets.UTF_8)
                        document = SettingsBackupCodec.decode(json)
                        SettingsBackupCodec.validate(document!!)
                        break
                    }
                    zis.closeEntry()
                }
            }
            
            val finalDocument = requireNotNull(document) { "settings.json not found in backup" }
            val currentPrefs = editor.readRawPreferences()
            val importDiff = computeSettingsBackupImportDiff(currentPrefs, finalDocument)
            val domains = finalDocument.preferences.map { mapPreferenceKeyToDomain(it.key) }.toSet()
            SettingsBackupPreview(
                formatVersion = finalDocument.formatVersion,
                exportedAtEpochMs = finalDocument.exportedAtEpochMs,
                appVersionName = finalDocument.appVersionName,
                totalPreferencesCount = finalDocument.preferences.size,
                domains = domains,
                hasOtpRecords = !finalDocument.otpRecordsJson.isNullOrBlank(),
                hasNotificationHistory = !finalDocument.notificationHistoryJson.isNullOrBlank(),
                importDiff = importDiff,
            )
        }
    }
}
