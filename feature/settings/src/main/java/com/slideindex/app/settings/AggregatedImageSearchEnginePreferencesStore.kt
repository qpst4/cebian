package com.slideindex.app.settings

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object AggregatedImageSearchEnginePreferencesStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val listSerializer = ListSerializer(AggregatedImageSearchEngineConfig.serializer())

    fun encode(configs: List<AggregatedImageSearchEngineConfig>): String =
        json.encodeToString(listSerializer, configs.sortedBy { it.sortOrder })

    fun decode(raw: String?): List<AggregatedImageSearchEngineConfig> {
        if (raw.isNullOrBlank()) return AggregatedImageSearchEngineCatalog.defaultConfigs()
        val stored = runCatching {
            json.decodeFromString(listSerializer, raw)
        }.getOrElse { emptyList() }
        return mergeWithCatalog(stored)
    }

    fun mergeWithCatalog(stored: List<AggregatedImageSearchEngineConfig>): List<AggregatedImageSearchEngineConfig> {
        val storedById = stored.associateBy { it.engineId }
        val knownIds = AggregatedImageSearchEngineCatalog.knownEngineIds.toSet()
        val orderedKnownIds = stored
            .filter { it.engineId in knownIds }
            .sortedBy { it.sortOrder }
            .map { it.engineId }
            .let { ordered ->
                val missing = AggregatedImageSearchEngineCatalog.knownEngineIds.filter { it !in ordered }
                ordered + missing
            }
        val mergedKnown = orderedKnownIds.mapIndexed { index, engineId ->
            storedById[engineId]?.copy(sortOrder = index)
                ?: AggregatedImageSearchEngineConfig(
                    engineId = engineId,
                    sortOrder = index,
                    showInPanel = true,
                    preloadOnOpen = true,
                )
        }
        val extras = stored
            .filter { it.engineId !in knownIds }
            .sortedBy { it.sortOrder }
            .mapIndexed { offset, config ->
                config.copy(sortOrder = mergedKnown.size + offset)
            }
        return mergedKnown + extras
    }

    fun panelConfigs(configs: List<AggregatedImageSearchEngineConfig>): List<AggregatedImageSearchEngineConfig> =
        configs.filter { it.showInPanel }.sortedBy { it.sortOrder }

    fun preloadConfigs(configs: List<AggregatedImageSearchEngineConfig>): List<AggregatedImageSearchEngineConfig> =
        panelConfigs(configs).filter { it.preloadOnOpen }
}
