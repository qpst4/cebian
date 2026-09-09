package com.slideindex.app.search

import android.content.Context
import android.net.Uri
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchIconType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

data class SearchEngineImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val sourceLabel: String,
    val mergedEngines: List<SearchEngineConfig>
)

object SearchEngineImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun importFromUri(
        context: Context,
        uri: Uri,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean
    ): Result<SearchEngineImportResult> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("cannot_read_file")
        val name = uri.lastPathSegment.orEmpty().lowercase()
        when {
            isZipArchive(name, bytes) -> importZipBytes(context, bytes, existing, replaceExisting)
            else -> importJsonBytes(context, bytes.decodeToString(), existing, replaceExisting)
        }
    }

    private fun importZipBytes(
        context: Context,
        bytes: ByteArray,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean
    ): SearchEngineImportResult {
        val entries = readZipEntries(bytes)
        val gevoJson = entries["settings/search.json"]
        if (gevoJson != null) {
            val imported = parseGevoSearchJson(gevoJson.decodeToString(), entries)
            return finalizeImport(context, existing, replaceExisting, imported, entries, "GestureEVO")
        }
        val sevoJson = entries["info.json"]
        if (sevoJson != null) {
            val imported = parseSevoSearchJson(sevoJson.decodeToString(), entries)
            return finalizeImport(context, existing, replaceExisting, imported, entries, "SearchEVO")
        }
        error("unsupported_backup")
    }

    private fun importJsonBytes(
        context: Context,
        raw: String,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean
    ): SearchEngineImportResult {
        val imported = parseGevoExportJson(raw)
        return finalizeImport(context, existing, replaceExisting, imported, emptyMap(), "JSON")
    }

    private fun finalizeImport(
        context: Context,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean,
        imported: List<SearchEngineConfig>,
        zipEntries: Map<String, ByteArray>,
        sourceLabel: String
    ): SearchEngineImportResult {
        if (imported.isEmpty()) error("no_search_engines")
        copyIconsFromZip(context, zipEntries, imported)
        val merged = SearchEngineStore.mergeEngines(existing, imported, replaceExisting)
        val added = if (replaceExisting) merged.size else merged.size - existing.size
        val skipped = if (replaceExisting) 0 else (imported.size - added).coerceAtLeast(0)
        return SearchEngineImportResult(
            importedCount = added,
            skippedCount = skipped,
            sourceLabel = sourceLabel,
            mergedEngines = merged
        )
    }

    fun parseGevoSearchJson(
        settingsJson: String,
        zipEntries: Map<String, ByteArray>
    ): List<SearchEngineConfig> {
        val root = json.parseToJsonElement(settingsJson).jsonObject
        val entries = root["entries"]?.jsonArray ?: return emptyList()
        val listEntry = entries.firstOrNull { entry ->
            entry.jsonObject["key"]?.jsonPrimitive?.content == "key_search_engine_list"
        } ?: return emptyList()
        val listJson = listEntry.jsonObject["value"]?.jsonPrimitive?.content.orEmpty()
        if (listJson.isBlank()) return emptyList()
        return parseGevoEngineArray(listJson, zipEntries)
    }

    fun parseGevoExportJson(raw: String): List<SearchEngineConfig> {
        val element = json.parseToJsonElement(raw)
        return when (element) {
            is JsonArray -> parseGevoEngineArray(raw, emptyMap())
            is JsonObject -> {
                if (element["entries"] != null) {
                    parseGevoSearchJson(raw, emptyMap())
                } else {
                    listOf(mapGevoEngine(element, emptyMap()))
                }
            }
            else -> emptyList()
        }
    }

    private fun parseGevoEngineArray(
        listJson: String,
        zipEntries: Map<String, ByteArray>
    ): List<SearchEngineConfig> {
        val array = json.parseToJsonElement(listJson).jsonArray
        return array.mapIndexed { index, item ->
            mapGevoEngine(item.jsonObject, zipEntries).copy(sortOrder = index)
        }
    }

    private fun mapGevoEngine(
        obj: JsonObject,
        zipEntries: Map<String, ByteArray>
    ): SearchEngineConfig {
        val engineType = runCatching {
            SearchEngineType.valueOf(obj.stringValue("engineType") ?: SearchEngineType.DIRECT_LINK.name)
        }.getOrDefault(SearchEngineType.DIRECT_LINK)
        val iconType = runCatching {
            SearchIconType.valueOf(obj.stringValue("iconType") ?: SearchIconType.OTHER.name)
        }.getOrDefault(SearchIconType.OTHER)
        val iconRef = obj.stringValue("icon")
        val iconFileName = iconRef?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val hasZipIcon = iconFileName != null && zipEntries.keys.any { it.endsWith(iconFileName) }
        return SearchEngineConfig(
            id = obj.stringValue("tag") ?: UUID.randomUUID().toString(),
            name = obj.stringValue("name").orEmpty(),
            engineType = engineType,
            iconType = if (hasZipIcon) SearchIconType.URI else iconType,
            iconPath = if (hasZipIcon) "search_icons/$iconFileName" else null,
            textIcon = obj.stringValue("textIcon"),
            searchLink = obj.stringValue("searchLink"),
            externJumpLink = obj.stringValue("externJumpLink"),
            externJumpPackage = obj.stringValue("externJumpPackage"),
            targetPackage = obj.stringValue("targetPackage"),
            targetActivity = obj.stringValue("targetActivity"),
            autoInputEnter = obj.booleanValue("autoInputEnter") ?: true,
            showInPickPanel = obj.booleanValue("showInSearchEngineList") ?: true
        )
    }

    fun parseSevoSearchJson(
        infoJson: String,
        zipEntries: Map<String, ByteArray>
    ): List<SearchEngineConfig> {
        val searchArray = extractSevoSearchArray(infoJson) ?: error("sevo_search_parse_failed")
        return searchArray.mapIndexed { index, item ->
            mapSevoEngine(item.jsonObject, zipEntries).copy(sortOrder = index)
        }
    }

    private fun mapSevoEngine(
        obj: JsonObject,
        zipEntries: Map<String, ByteArray>
    ): SearchEngineConfig {
        val url = obj.stringValue("url").orEmpty()
        val externUrl = obj.stringValue("externUrl").orEmpty()
        val pkg = obj.stringValue("pkgName").orEmpty()
        val targetClass = obj.stringValue("targetClass").orEmpty()
        val iconName = obj.stringValue("icon").orEmpty()
        val hasIcon = iconName.isNotBlank() && zipEntries.containsKey("files/icon/$iconName")
        val engineType = when {
            url.isNotBlank() -> SearchEngineType.DIRECT_LINK
            externUrl.isNotBlank() -> SearchEngineType.EXTERN_JUMP_LINK
            pkg.isNotBlank() -> SearchEngineType.JUMP_TO_ACTIVITY
            else -> SearchEngineType.DIRECT_LINK
        }
        return SearchEngineConfig(
            id = UUID.randomUUID().toString(),
            name = obj.stringValue("name").orEmpty(),
            engineType = engineType,
            iconType = if (hasIcon) SearchIconType.URI else SearchIconType.OTHER,
            iconPath = if (hasIcon) "search_icons/$iconName" else null,
            searchLink = url.takeIf { it.isNotBlank() },
            externJumpLink = externUrl.takeIf { it.isNotBlank() },
            targetPackage = pkg.takeIf { it.isNotBlank() },
            targetActivity = targetClass.takeIf { it.isNotBlank() },
            autoInputEnter = true,
            showInPickPanel = true
        )
    }

    private fun extractSevoSearchArray(infoJson: String): JsonArray? {
        runCatching {
            val root = json.parseToJsonElement(infoJson).jsonObject
            return root["search"]?.jsonArray
        }
        val marker = "\"search\":"
        val start = infoJson.indexOf(marker)
        if (start < 0) return null
        val arrayStart = infoJson.indexOf('[', start)
        if (arrayStart < 0) return null
        var depth = 0
        for (i in arrayStart until infoJson.length) {
            when (infoJson[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        val slice = infoJson.substring(arrayStart, i + 1)
                        return json.parseToJsonElement(slice).jsonArray
                    }
                }
            }
        }
        return null
    }

    private fun copyIconsFromZip(
        context: Context,
        zipEntries: Map<String, ByteArray>,
        engines: List<SearchEngineConfig>
    ) {
        val iconDir = File(context.filesDir, "search_icons").apply { mkdirs() }
        engines.forEach { engine ->
            val relative = engine.iconPath ?: return@forEach
            val fileName = relative.substringAfterLast('/')
            val zipKey = zipEntries.keys.firstOrNull { it.endsWith(fileName) } ?: return@forEach
            val bytes = zipEntries[zipKey] ?: return@forEach
            File(iconDir, fileName).writeBytes(bytes)
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    result[entry.name.replace('\\', '/')] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun isZipArchive(name: String, bytes: ByteArray): Boolean =
        name.endsWith(".zip") || name.endsWith(".evobak") || isZip(bytes)

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.content

    private fun JsonObject.booleanValue(key: String): Boolean? =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
}
