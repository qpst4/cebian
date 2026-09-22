package com.slideindex.app.ui.searchengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore

/**
 * 默认搜索引擎的候选顺序：面板可见引擎在前，已移入隐藏池的在后。
 *
 * 隐藏只表示不在面板网格里占位、配置仍保留，所以依旧可以被选为默认引擎。
 */
internal fun defaultSearchEngineCandidates(
    engines: List<SearchEngineConfig>,
): List<SearchEngineConfig> {
    val all = SearchEngineStore.textSettingsEngines(engines)
    return all.filter { it.showInPickPanel } + all.filterNot { it.showInPickPanel }
}

/** 下拉项文案：隐藏引擎标注「（已隐藏）」，避免用户选完在面板网格里找不到它。 */
@Composable
internal fun defaultSearchEngineItemLabel(engine: SearchEngineConfig): String =
    if (engine.showInPickPanel) {
        engine.name
    } else {
        stringResource(R.string.search_engine_default_hidden_marker, engine.name)
    }
