@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.search.ImageSearchEngine
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSearchEngineSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpsertEngine: (SearchEngineEditorResult) -> Unit,
    onDeleteEngine: (String) -> Unit,
    onMoveEngine: (String, Int) -> Unit,
) {
    val shareEngines = remember(settings.searchEngines) {
        SearchEngineStore.imageSharePanelEngines(settings.searchEngines)
    }
    val aggregatedEngines = remember { ImageSearchEngine.entries }
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
            SettingsCard {
                shareEngines.forEachIndexed { index, engine ->
                    ImageShareEngineListRow(
                        engine = engine,
                        canMoveUp = index > 0,
                        canMoveDown = index < shareEngines.lastIndex,
                        onClick = {
                            editingEngine = engine
                            showEditor = true
                        },
                        onMoveUp = { onMoveEngine(engine.id, -1) },
                        onMoveDown = { onMoveEngine(engine.id, 1) },
                        onDelete = { deletingEngine = engine },
                    )
                    if (index < shareEngines.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        SettingsSectionTitle(stringResource(R.string.image_search_engine_aggregated_section))
        SettingsHintText(stringResource(R.string.image_search_engine_aggregated_hint))
        SettingsCard {
            aggregatedEngines.forEachIndexed { index, engine ->
                AggregatedImageSearchEngineRow(engine = engine)
                if (index < aggregatedEngines.lastIndex) {
                    HorizontalDivider()
                }
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

@Composable
private fun AggregatedImageSearchEngineRow(engine: ImageSearchEngine) {
    val siteLabel = remember(engine) {
        engine.faviconSourceUrl?.toUri()?.host?.takeIf { it.isNotBlank() }
            ?: engine.faviconSourceUrl
    }
    val modeLabel = when {
        engine.usesDirectPost -> stringResource(R.string.image_search_engine_mode_direct_post)
        engine.usesHostedUrl -> stringResource(R.string.image_search_engine_mode_hosted_url)
        else -> ""
    }
    val builtinLabel = stringResource(R.string.image_search_engine_aggregated_builtin)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AggregatedImageSearchEngineIcon(
            engine = engine,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = engine.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!siteLabel.isNullOrBlank()) {
                Text(
                    text = siteLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = listOf(modeLabel, builtinLabel).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImageShareEngineListRow(
    engine: SearchEngineConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchEngineIcon(engine = engine, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = engine.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.search_engine_type_share_image),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = stringResource(R.string.search_engine_move_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = stringResource(R.string.search_engine_move_down),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.search_engine_delete_confirm),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
