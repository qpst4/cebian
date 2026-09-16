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
    private val cloudConfigPort: SettingsBackupCloudConfigPort,
) {
    suspend fun exportToZip(
        appVersionName: String,
        sensitive: SensitiveBackupSections? = null,
        outputStream: OutputStream,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            materializeSearchEngineIconsForExport()
            val preferences = editor.readRawPreferences()
            val json = SettingsBackupCodec.encode(preferences, appVersionName, sensitive)

            ZipOutputStream(outputStream).use { zos ->
                zos.putNextEntry(ZipEntry("settings.json"))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val cloudConfigJson = cloudConfigPort.exportRawJson()
                if (cloudConfigJson.isNotBlank()) {
                    zos.putNextEntry(ZipEntry(SettingsBackupPaths.VLM_OCR_CONFIG_JSON))
                    zos.write(cloudConfigJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                val dirsToBackup = SettingsBackupPaths.dirsForExport(
                    includeSensitiveDirectories = sensitive?.includeDirectories == true,
                )
                for (dirName in dirsToBackup) {
                    val dir = File(context.filesDir, dirName)
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relativePath = file.relativeTo(context.filesDir).path
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
            var cloudConfigJson: String? = null
            val importedDirs = mutableSetOf<String>()
            val clearedDirs = mutableSetOf<String>()

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    if (currentEntry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }

                    val name = currentEntry.name
                    when (name) {
                        "settings.json" -> {
                            val json = zis.readBytes().toString(Charsets.UTF_8)
                            val decoded = SettingsBackupCodec.decode(json)
                            SettingsBackupCodec.validate(decoded)
                            document = decoded
                        }
                        SettingsBackupPaths.VLM_OCR_CONFIG_JSON -> {
                            cloudConfigJson = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        else -> if (SettingsBackupPaths.isBackupPath(name)) {
                            if (name.contains("..")) {
                                zis.closeEntry()
                                continue
                            }

                            val normalizedName = SettingsBackupPaths.normalizeEntryPath(name)
                            val topLevelDir = SettingsBackupPaths.topLevelDir(normalizedName)
                            if (replaceExisting && topLevelDir != null && topLevelDir !in clearedDirs) {
                                File(context.filesDir, topLevelDir).deleteRecursively()
                                clearedDirs += topLevelDir
                            }

                            val targetFile = File(context.filesDir, normalizedName)
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { out ->
                                zis.copyTo(out)
                            }
                            topLevelDir?.let { importedDirs += it }
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

            cloudConfigJson?.let { raw ->
                cloudConfigPort.importRawJson(raw, replaceExisting = true)
            }

            SettingsBackupImportResult(
                preferencesImported = finalDocument.preferences.size,
                sensitive = finalDocument.toOptionalSections(),
                importedClipboardDirectory = "clipboard" in importedDirs,
                importedShareImageOcrHistoryDirectory = "share_image_ocr_history" in importedDirs,
                importedCloudLlmConfig = !cloudConfigJson.isNullOrBlank(),
            )
        }
    }

    suspend fun previewZipImport(inputStream: InputStream): Result<SettingsBackupPreview> = runCatching {
        withContext(Dispatchers.IO) {
            var document: SettingsBackupDocument? = null
            var hasCloudLlmConfig = false
            val importedDirs = mutableSetOf<String>()

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    if (currentEntry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }

                    val name = currentEntry.name
                    when (name) {
                        "settings.json" -> {
                            val json = zis.readBytes().toString(Charsets.UTF_8)
                            val decoded = SettingsBackupCodec.decode(json)
                            SettingsBackupCodec.validate(decoded)
                            document = decoded
                        }
                        SettingsBackupPaths.VLM_OCR_CONFIG_JSON -> hasCloudLlmConfig = true
                        else -> SettingsBackupPaths.topLevelDir(name)?.let { importedDirs += it }
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
                hasNotificationFilterRules = !finalDocument.notificationFilterRulesJson.isNullOrBlank(),
                hasNotificationFilterPreferences = !finalDocument.notificationFilterPreferencesJson.isNullOrBlank(),
                hasOtpAutoFillStats = !finalDocument.otpAutoFillStatsJson.isNullOrBlank(),
                hasShellOutputHistory = !finalDocument.shellOutputHistoryJson.isNullOrBlank(),
                hasSearchPanelHistory = !finalDocument.searchPanelHistoryJson.isNullOrBlank(),
                hasClipboardDirectory = "clipboard" in importedDirs,
                hasShareImageOcrHistoryDirectory = "share_image_ocr_history" in importedDirs,
                hasCloudLlmConfig = hasCloudLlmConfig,
                importDiff = importDiff,
            )
        }
    }

    private suspend fun materializeSearchEngineIconsForExport() {
        val prefs = editor.readRawPreferences()
        val initialized = prefs[SettingsPreferenceKeys.SEARCH_ENGINES_INITIALIZED] ?: false
        if (!initialized) return
        val raw = prefs[SettingsPreferenceKeys.SEARCH_ENGINES_JSON]
        val before = SearchEngineStore.decode(context, raw)
        val after = SearchEngineIconMaterializer.materialize(context, before)
        if (!SearchEngineIconMaterializer.needsPersist(before, after)) return
        editor.edit {
            it[SettingsPreferenceKeys.SEARCH_ENGINES_JSON] = SearchEngineStore.encode(after)
        }
    }

    private fun SettingsBackupDocument.toOptionalSections(): SensitiveBackupSections =
        SensitiveBackupSections(
            otpRecordsJson = otpRecordsJson,
            notificationHistoryJson = notificationHistoryJson,
            notificationFilterRulesJson = notificationFilterRulesJson,
            notificationFilterPreferencesJson = notificationFilterPreferencesJson,
            otpAutoFillStatsJson = otpAutoFillStatsJson,
            shellOutputHistoryJson = shellOutputHistoryJson,
            searchPanelHistoryJson = searchPanelHistoryJson,
        )
}
