@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
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
import com.slideindex.app.ui.viewmodel.SearchEngineImportPreviewState
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import com.slideindex.app.ui.searchengine.AggregatedSearchEngineManager
import com.slideindex.app.settings.SearchEngineCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineSettingsScreen(
    settings: AppSettings,
    importPreviewState: SearchEngineImportPreviewState?,
    onBack: () -> Unit,
    onOpenPresetPicker: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onDismissImportPreview: () -> Unit,
    onConfirmImport: (replaceExisting: Boolean) -> Unit,
    onUpsertEngine: (SearchEngineEditorResult) -> Unit,
    onDeleteEngine: (String) -> Unit,
    onMoveEngine: (String, Int) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onGridRowsChange: (Int) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onOpenPreviewSort: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    onUpdateEngines: (List<SearchEngineConfig>) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engines = remember(settings.searchEngines) {
        SearchEngineStore.textSettingsEngines(settings.searchEngines)
    }
    var deletingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    SettingsScreenScaffold(
        title = "聚合搜索",
        onBack = onBack,
        actions = {
            // 排序菜单按钮
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(
                        text = "排序",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("按名称正序 (A-Z)") },
                        onClick = {
                            sortMenuExpanded = false
                            val sorted = engines.sortedBy { it.name.lowercase() }
                            onUpdateEngines(sorted)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("全部恢复显示在面板") },
                        onClick = {
                            sortMenuExpanded = false
                            val allShown = engines.map { it.copy(showInPickPanel = true) }
                            onUpdateEngines(allShown)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("重置为默认引擎") },
                        onClick = {
                            sortMenuExpanded = false
                            val defaults = SearchEngineCatalog.defaultEngines(context)
                            onUpdateEngines(defaults)
                        },
                    )
                }
            }

            // 更多操作菜单
            Box {
                IconButton(onClick = { moreMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多选项",
                    )
                }
                DropdownMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("预设搜索引擎库") },
                        onClick = {
                            moreMenuExpanded = false
                            onOpenPresetPicker()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("从文件导入备份") },
                        onClick = {
                            moreMenuExpanded = false
                            importLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/json",
                                    "application/octet-stream",
                                    "*/*",
                                ),
                            )
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加搜索引擎",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) {
        item(key = "aggregated-search-manager") {
            AggregatedSearchEngineManager(
                engines = engines,
                gridColumns = settings.searchEngineGridColumns,
                gridRows = settings.searchEngineGridRows,
                showLabels = settings.searchEngineShowLabels,
                onUpdateEngines = onUpdateEngines,
                onGridColumnsChange = onGridColumnsChange,
                onGridRowsChange = onGridRowsChange,
                onShowLabelsChange = onShowLabelsChange,
                onAddEngine = { onOpenEditor(null) },
                onEditEngine = onOpenEditor,
                onDeleteEngine = { id -> deletingEngine = engines.find { it.id == id } },
                onPresetCatalog = onOpenPresetPicker,
                modifier = Modifier.padding(top = 8.dp),
            )
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

@Composable
private fun SearchEngineImportPreviewDialog(
    preview: SearchEngineImportPreviewState,
    onDismiss: () -> Unit,
    onConfirmMerge: () -> Unit,
    onConfirmReplace: () -> Unit,
) {
    MiuixConfirmDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.search_engine_import_preview_title),
        confirmText = stringResource(R.string.search_engine_import_merge),
        onConfirm = onConfirmMerge,
        dismissText = stringResource(android.R.string.cancel),
        secondaryConfirmText = stringResource(R.string.search_engine_import_replace),
        onSecondaryConfirm = onConfirmReplace,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.search_engine_import_preview_source,
                        preview.sourceLabel,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    pluralStringResource(
                        R.plurals.search_engine_import_preview_count,
                        preview.importedCount,
                        preview.importedCount,
                        preview.skippedCount,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.search_engine_import_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
