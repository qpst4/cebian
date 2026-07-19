package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.search.ImageSearchEngine
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig

fun resolveImageSearchEngine(engineId: String): ImageSearchEngine? =
    ImageSearchEngine.entries.find { it.name == engineId }

@Composable
fun aggregatedImageSearchEngineStatusSummary(config: AggregatedImageSearchEngineConfig): String {
    if (!config.showInPanel) {
        return stringResource(R.string.image_search_engine_status_hidden)
    }
    return if (config.preloadOnOpen) {
        stringResource(R.string.image_search_engine_status_visible_preload)
    } else {
        stringResource(R.string.image_search_engine_status_visible_on_tab)
    }
}
