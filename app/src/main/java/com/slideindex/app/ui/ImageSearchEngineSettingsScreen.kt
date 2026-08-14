@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsVerticalReorderList
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

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
    onImageSearchPickPanelTransparencyChange: (Float) -> Unit,
    onOpenEditor: (String?) -> Unit,
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
    var deletingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    val shareSectionTitle = stringResource(
        R.string.image_search_engine_share_section,
        shareEngines.size,
    )
    val shareHint = stringResource(R.string.image_search_engine_share_hint)
    val shareEmptyHint = stringResource(R.string.image_search_engine_share_empty)
    val aggregatedSectionTitle = pluralStringResource(
        R.plurals.image_search_engine_aggregated_section,
        visibleAggregatedCount,
        visibleAggregatedCount,
    )
    val aggregatedHint = stringResource(R.string.image_search_engine_aggregated_hint)
    val aggregatedEmptyHint = stringResource(R.string.image_search_engine_aggregated_empty)

    SettingsScreenScaffold(
        title = stringResource(R.string.image_search_engine_settings_title),
        subtitle = stringResource(R.string.image_search_engine_settings_subtitle),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "image-search-transparency",
            items = listOf(
                settingsCardScopeItem("transparency") {
                    SettingsSliderRow(
                        title = stringResource(R.string.float_ball_image_search_pick_panel_transparency),
                        value = settings.floatBallImageSearchPickPanelTransparency,
                        valueRange = 0f..1f,
                        enabled = true,
                        label = stringResource(
                            R.string.floating_pointer_percent_value,
                            (settings.floatBallImageSearchPickPanelTransparency * 100).roundToInt(),
                        ),
                        keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                        onValueChange = onImageSearchPickPanelTransparencyChange,
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "image-search-share-section",
            title = shareSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(key = "image-search-share-hint", text = shareHint)
        LazySettingsItem(key = "image-search-share-list") {
            Button(
                onClick = { onOpenEditor(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = stringResource(R.string.image_search_engine_add_share_target),
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            if (shareEngines.isEmpty()) {
                Text(
                    text = shareEmptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
                        onClick = { onOpenEditor(engine.id) },
                        onDelete = { deletingEngine = engine },
                        modifier = dragModifier,
                    )
                }
            }
        }

        settingsLazySmallTitle(
            key = "image-search-aggregated-section",
            title = aggregatedSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(key = "image-search-aggregated-hint", text = aggregatedHint)
        LazySettingsItem(key = "image-search-aggregated-list") {
            if (visibleAggregatedCount == 0) {
                Text(
                    text = aggregatedEmptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
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
                        subtitle = aggregatedImageSearchEngineRowSubtitle(engine, config),
                        segmentIndex = segmentIndex,
                        segmentCount = segmentCount,
                        onClick = { onOpenAggregatedEngine(config.engineId) },
                        modifier = dragModifier,
                    )
                }
            }
        }
    }

    val engineToDelete = deletingEngine
    MiuixConfirmDialog(
        show = engineToDelete != null,
        onDismissRequest = { deletingEngine = null },
        title = stringResource(R.string.search_engine_delete_title),
        message = engineToDelete?.let {
            stringResource(R.string.search_engine_delete_message, it.name)
        },
        confirmText = stringResource(R.string.search_engine_delete_confirm),
        onConfirm = {
            engineToDelete?.let { engine ->
                onDeleteEngine(engine.id)
                deletingEngine = null
            }
        },
    )
}
