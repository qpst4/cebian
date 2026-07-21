@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import kotlin.math.roundToInt
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.ui.viewmodel.SearchEngineImportPreviewState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineSettingsScreen(
    settings: AppSettings,
    importPreviewState: SearchEngineImportPreviewState?,
    onBack: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onDismissImportPreview: () -> Unit,
    onConfirmImport: (replaceExisting: Boolean) -> Unit,
    onUpsertEngine: (SearchEngineEditorResult) -> Unit,
    onDeleteEngine: (String) -> Unit,
    onMoveEngine: (String, Int) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onGridRowsChange: (Int) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onSetDefaultEngineId: (String?) -> Unit,
    onSetSearchPanelInputBehavior: (SearchPanelInputBehavior) -> Unit,
    onOpenPreviewSort: () -> Unit,
) {
    val engines = remember(settings.searchEngines) {
        SearchEngineStore.textSettingsEngines(settings.searchEngines)
    }
    var showEditor by remember { mutableStateOf(false) }
    var editingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var deletingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var showDefaultEngineDialog by remember { mutableStateOf(false) }
    var showInputBehaviorDialog by remember { mutableStateOf(false) }

    if (showEditor) {
        SearchEngineEditorScreen(
            initialEngine = editingEngine,
            editorCategory = SearchEngineEditorCategory.TEXT,
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

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_engine_settings_title),
        subtitle = stringResource(R.string.search_engine_settings_subtitle),
        onBack = onBack,
    ) {
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.DragHandle, contentDescription = label) },
                title = stringResource(R.string.search_engine_settings_preview_mode),
                subtitle = stringResource(R.string.search_engine_settings_preview_mode_summary),
                enabled = engines.isNotEmpty(),
                onClick = onOpenPreviewSort,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.DragHandle, contentDescription = label) },
                title = stringResource(R.string.search_panel_default_engine_title),
                subtitle = engines.find { it.id == settings.searchPanelDefaultEngineId }?.name ?: stringResource(R.string.search_panel_default_engine_none),
                enabled = engines.isNotEmpty(),
                onClick = { showDefaultEngineDialog = true },
            )
            // New row for input behavior
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.DragHandle, contentDescription = label) },
                title = "搜索面板输入行为",
                subtitle = when (settings.searchPanelInputBehavior) {
                    SearchPanelInputBehavior.SELECT_ALL -> "全选输入框文本"
                    SearchPanelInputBehavior.CLEAR -> "清空输入框文本"
                    SearchPanelInputBehavior.KEEP -> "保留输入框文本"
                },
                enabled = true,
                onClick = { showInputBehaviorDialog = true },
            )
        }

        SettingsSectionTitle(stringResource(R.string.search_engine_settings_display_section))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.search_engine_grid_columns),
                value = settings.searchEngineGridColumns.toFloat(),
                valueRange = 3f..7f,
                steps = 3,
                enabled = true,
                label = pluralStringResource(
                    R.plurals.search_engine_grid_columns_value,
                    settings.searchEngineGridColumns,
                    settings.searchEngineGridColumns,
                ),
                onValueChange = { onGridColumnsChange(it.roundToInt()) },
            )
            SettingsSliderRow(
                title = stringResource(R.string.search_engine_grid_rows),
                value = settings.searchEngineGridRows.toFloat(),
                valueRange = 1f..4f,
                steps = 2,
                enabled = true,
                label = pluralStringResource(
                    R.plurals.search_engine_grid_rows_value,
                    settings.searchEngineGridRows,
                    settings.searchEngineGridRows,
                ),
                onValueChange = { onGridRowsChange(it.roundToInt()) },
            )
            SettingSwitchRow(
                title = stringResource(R.string.search_engine_show_labels),
                subtitle = stringResource(R.string.search_engine_show_labels_desc),
                checked = settings.searchEngineShowLabels,
                enabled = true,
                onCheckedChange = onShowLabelsChange,
            )
        }

        SettingsSectionTitle(stringResource(R.string.search_engine_settings_import_section))
        OutlinedButton(
            onClick = {
                importLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/json",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Text(
                text = stringResource(R.string.search_engine_settings_import),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        SettingsSectionTitle(
            stringResource(R.string.search_engine_settings_list_section, engines.size),
        )
        Button(
            onClick = {
                editingEngine = null
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.search_engine_add_title),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (engines.isEmpty()) {
            SettingsHintText(stringResource(R.string.search_engine_settings_empty))
        } else {
            SettingsCard {
                engines.forEachIndexed { index, engine ->
                    SearchEngineListRow(
                        engine = engine,
                        canMoveUp = index > 0,
                        canMoveDown = index < engines.lastIndex,
                        onClick = {
                            editingEngine = engine
                            showEditor = true
                        },
                        onMoveUp = { onMoveEngine(engine.id, -1) },
                        onMoveDown = { onMoveEngine(engine.id, 1) },
                        onDelete = { deletingEngine = engine },
                    )
                    if (index < engines.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (importPreviewState != null) {
        SearchEngineImportPreviewDialog(
            preview = importPreviewState,
            onDismiss = onDismissImportPreview,
            onConfirmMerge = { onConfirmImport(false) },
            onConfirmReplace = { onConfirmImport(true) },
        )
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

    if (showDefaultEngineDialog) {
        SearchEngineDefaultEngineDialog(
            engines = engines,
            selectedId = settings.searchPanelDefaultEngineId,
            onDismiss = { showDefaultEngineDialog = false },
            onSelect = {
                onSetDefaultEngineId(it)
                showDefaultEngineDialog = false
            },
        )
    }
    if (showInputBehaviorDialog) {
        AlertDialog(
            onDismissRequest = { showInputBehaviorDialog = false },
            title = { Text("搜索面板输入行为") },
            text = {
                Column {
                    SearchPanelInputBehavior.values().forEach { behavior ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetSearchPanelInputBehavior(behavior); showInputBehaviorDialog = false },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            ) {
                            androidx.compose.material3.RadioButton(
                                selected = settings.searchPanelInputBehavior == behavior,
                                onClick = { onSetSearchPanelInputBehavior(behavior); showInputBehaviorDialog = false }
                            )
                            Text(
                                text = when (behavior) {
                                    SearchPanelInputBehavior.SELECT_ALL -> "全选输入框文本"
                                    SearchPanelInputBehavior.CLEAR -> "清空输入框文本"
                                    SearchPanelInputBehavior.KEEP -> "保留输入框文本"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInputBehaviorDialog = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showInputBehaviorDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SearchEngineListRow(
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
                text = searchEngineTypeLabel(engine.engineType),
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

@Composable
private fun searchEngineTypeLabel(type: SearchEngineType): String = when (type) {
    SearchEngineType.DIRECT_LINK -> stringResource(R.string.search_engine_type_direct_link)
    SearchEngineType.JUMP_TO_ACTIVITY -> stringResource(R.string.search_engine_type_jump_activity)
    SearchEngineType.EXTERN_JUMP_LINK -> stringResource(R.string.search_engine_type_extern_jump)
    SearchEngineType.SHARE_TO_APP -> stringResource(R.string.search_engine_type_share)
    SearchEngineType.SHARE_IMAGE_TO_APP -> stringResource(R.string.search_engine_type_share_image)
}

@Composable
private fun SearchEngineImportPreviewDialog(
    preview: SearchEngineImportPreviewState,
    onDismiss: () -> Unit,
    onConfirmMerge: () -> Unit,
    onConfirmReplace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_engine_import_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.search_engine_import_preview_source,
                        preview.sourceLabel,
                    ),
                )
                Text(
                    pluralStringResource(
                        R.plurals.search_engine_import_preview_count,
                        preview.importedCount,
                        preview.importedCount,
                        preview.skippedCount,
                    ),
                )
                Text(
                    text = stringResource(R.string.search_engine_import_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmMerge) {
                Text(stringResource(R.string.search_engine_import_merge))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(onClick = onConfirmReplace) {
                    Text(stringResource(R.string.search_engine_import_replace))
                }
            }
        }
    )
}

@Composable
private fun SearchEngineDefaultEngineDialog(
    engines: List<SearchEngineConfig>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_panel_default_engine_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                    Text(
                        text = stringResource(R.string.search_panel_default_engine_none),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                engines.forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(engine.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = engine.id == selectedId,
                            onClick = { onSelect(engine.id) },
                        )
                        Text(
                            text = engine.name,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
