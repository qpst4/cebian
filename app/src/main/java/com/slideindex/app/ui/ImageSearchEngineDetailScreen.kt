@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.search.ImageSearchEngine
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.ui.settings.components.SettingSwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSearchEngineDetailScreen(
    engine: ImageSearchEngine,
    config: AggregatedImageSearchEngineConfig,
    onBack: () -> Unit,
    onShowInPanelChange: (Boolean) -> Unit,
    onPreloadChange: (Boolean) -> Unit,
) {
    SettingsScreenScaffold(
        title = engine.displayName,
        onBack = onBack,
    ) {
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.image_search_engine_show_in_panel),
                checked = config.showInPanel,
                enabled = true,
                onCheckedChange = onShowInPanelChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.image_search_engine_preload_on_open),
                subtitle = stringResource(R.string.image_search_engine_preload_on_open_desc),
                checked = config.preloadOnOpen,
                enabled = config.showInPanel,
                onCheckedChange = onPreloadChange,
            )
        }
    }
}
