package com.slideindex.app.settings

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object SearchEngineStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val listSerializer = ListSerializer(SearchEngineConfig.serializer())

    fun encode(engines: List<SearchEngineConfig>): String =
        json.encodeToString(listSerializer, engines.sortedBy { it.sortOrder })

    fun decode(raw: String?): List<SearchEngineConfig> {
        if (raw.isNullOrBlank()) return SearchEngineCatalog.defaultEngines()
        return runCatching {
            json.decodeFromString(listSerializer, raw)
        }.getOrElse { SearchEngineCatalog.defaultEngines() }
    }

    fun textPickPanelEngines(engines: List<SearchEngineConfig>): List<SearchEngineConfig> =
        engines.filter { it.isTextSearchEngine() }.sortedBy { it.sortOrder }

    fun imageSharePanelEngines(engines: List<SearchEngineConfig>): List<SearchEngineConfig> =
        engines.filter {
            it.engineType == SearchEngineType.SHARE_IMAGE_TO_APP && it.showInPickPanel
        }.sortedBy { it.sortOrder }

    fun textSettingsEngines(engines: List<SearchEngineConfig>): List<SearchEngineConfig> =
        engines.filter { it.engineType != SearchEngineType.SHARE_IMAGE_TO_APP }
            .sortedBy { it.sortOrder }

    fun mergeEngines(
        existing: List<SearchEngineConfig>,
        imported: List<SearchEngineConfig>,
        replaceExisting: Boolean,
    ): List<SearchEngineConfig> {
        if (replaceExisting) {
            return imported.mapIndexed { index, engine -> engine.copy(sortOrder = index) }
        }
        val usedNames = existing.map { it.name.lowercase() }.toMutableSet()
        val merged = existing.toMutableList()
        var order = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        imported.forEach { engine ->
            val key = engine.name.lowercase()
            if (key in usedNames) return@forEach
            usedNames += key
            merged += engine.copy(
                id = java.util.UUID.randomUUID().toString(),
                sortOrder = order++,
            )
        }
        return merged
    }
}
