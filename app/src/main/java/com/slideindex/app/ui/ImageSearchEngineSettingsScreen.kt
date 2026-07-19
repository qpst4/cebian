@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.settings.components.SettingsVerticalReorderList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSearchEngineSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpsertEngine: (SearchEngineEditorResult) -> Unit,
    onDeleteEngine: (String) -> Unit,
    onReorderShareEngines: (List<SearchEngineConfig>) -> Unit,
    onReorderAggregatedEngines: (List<AggregatedImageSearchEngineConfig>) -> Unit,
    onOpenAggregatedEngine: (String) -> Unit,
) {
    val shareEngines = remember(settings.searchEngines) {
        SearchEngineStore.imageSharePanelEngines(settings.searchEngines)
    }
    val aggregatedConfigs = remember(settings.aggregatedImageSearchEngines) {
        settings.aggregatedImageSearchEngines.sortedBy { it.sortOrder }
    }
    val visibleAggregatedCount = remember(aggregatedConfigs) {
        aggregatedConfigs.count { it.showInPanel }
    }
    var showEditor by remember { mutableStateOf(false) }
    var editingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var deletingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    if (showEditor) {
        SearchEngineEditorScreen(
            initialEngine = editingEngine,
            editorCategory = SearchEngineEditorCategory.IMAGE_SHARE,
            onBack = {
                showEditor = false
                editingEngine = null
            },
            onSave = { result ->
                onUpsertEngine(result)
                showEditor = false
                editingEngine = null
            },
        )
        return
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.image_search_engine_settings_title),
        subtitle = stringResource(R.string.image_search_engine_settings_subtitle),
        onBack = onBack,
    ) {
        SettingsSectionTitle(
            stringResource(
                R.string.image_search_engine_share_section,
                shareEngines.size,
            ),
        )
        SettingsHintText(stringResource(R.string.image_search_engine_share_hint))
        Button(
            onClick = {
                editingEngine = null
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.image_search_engine_add_share_target),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (shareEngines.isEmpty()) {
            SettingsHintText(stringResource(R.string.image_search_engine_share_empty))
        } else {
            SettingsVerticalReorderList(
                items = shareEngines,
                key = { it.id },
                onReorder = onReorderShareEngines,
            ) { engine, _, segmentIndex, segmentCount, dragModifier ->
                ImageShareEngineReorderRow(
                    engine = engine,
                    segmentIndex = segmentIndex,
                    segmentCount = segmentCount,
                    onClick = {
                        editingEngine = engine
                        showEditor = true
                    },
                    onDelete = { deletingEngine = engine },
                    modifier = dragModifier,
                )
            }
        }

        SettingsSectionTitle(
            stringResource(
                R.string.image_search_engine_aggregated_section,
                visibleAggregatedCount,
            ),
        )
        SettingsHintText(stringResource(R.string.image_search_engine_aggregated_hint))
        if (visibleAggregatedCount == 0) {
            SettingsHintText(stringResource(R.string.image_search_engine_aggregated_empty))
        }
        SettingsVerticalReorderList(
            items = aggregatedConfigs,
            key = { it.engineId },
            onReorder = onReorderAggregatedEngines,
        ) { config, _, segmentIndex, segmentCount, dragModifier ->
            val engine = resolveImageSearchEngine(config.engineId)
            if (engine != null) {
                AggregatedImageSearchEngineReorderRow(
                    engine = engine,
                    subtitle = aggregatedImageSearchEngineStatusSummary(config),
                    segmentIndex = segmentIndex,
                    segmentCount = segmentCount,
                    onClick = { onOpenAggregatedEngine(config.engineId) },
                    modifier = dragModifier,
                )
            }
        }
    }

    deletingEngine?.let { engine ->
        AlertDialog(
            onDismissRequest = { deletingEngine = null },
            title = { Text(stringResource(R.string.search_engine_delete_title)) },
            text = {
                Text(stringResource(R.string.search_engine_delete_message, engine.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEngine(engine.id)
                        deletingEngine = null
                    },
                ) {
                    Text(stringResource(R.string.search_engine_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEngine = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
