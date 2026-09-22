package com.slideindex.app.settings

import android.content.Context
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

    fun decode(context: Context, raw: String?): List<SearchEngineConfig> {
        if (raw.isNullOrBlank()) return SearchEngineCatalog.defaultEngines(context)
        return runCatching {
            json.decodeFromString(listSerializer, raw)
        }.getOrElse { SearchEngineCatalog.defaultEngines(context) }
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

    /**
     * 按 id 解析文本搜索引擎。
     *
     * 解析范围是全部文本引擎（含已移入隐藏池的「[SearchEngineConfig.showInPickPanel] = false」项）——
     * 隐藏只表示不在面板网格里占位，不代表不能作为默认引擎使用。
     */
    fun findTextEngineById(
        engines: List<SearchEngineConfig>,
        id: String?,
    ): SearchEngineConfig? {
        if (id.isNullOrBlank()) return null
        return textSettingsEngines(engines).find { it.id == id }
    }

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
