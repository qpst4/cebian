package com.slideindex.app.ui.quicklauncher

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.AppPackageEntry
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerListGroupSpacing
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerSearchListHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.QuickLauncherGridEditor
import com.slideindex.app.ui.QuickLauncherLayoutSettings
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.ShortcutScanProgressContent
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.picker.activityShortcutPickerToggleSection
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.gestureActionDescription
import com.slideindex.app.ui.gestureActionLabel
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class QuickLauncherEditorAddTab { ACTIONS, APPS, SHORTCUTS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuickLauncherEditorMainSection(
    padding: PaddingValues,
    settings: AppSettings,
    items: List<QuickLauncherItem>,
    appsByPackage: Map<String, AppInfo>,
    gridInteractionActive: Boolean,
    onColumnsChange: (Int) -> Unit,
    onRowsChange: (Int) -> Unit,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onAdd: () -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit,
    descriptionResId: Int = R.string.quick_launcher_editor_desc,
    showLayoutSettings: Boolean = true,
    itemsSectionTitleResId: Int = R.string.quick_launcher_page_switch,
    showPageSwitcher: Boolean = true,
    gridColumnsOverride: Int? = null,
    gridRowsOverride: Int? = null,
    bottomContent: @Composable (ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mainScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(mainScrollState, enabled = !gridInteractionActive)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(descriptionResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showLayoutSettings) {
            SettingsSectionTitle(stringResource(R.string.quick_launcher_layout_section))
            QuickLauncherLayoutSettings(
                settings = settings,
                enabled = true,
                onColumnsChange = onColumnsChange,
                onRowsChange = onRowsChange,
            )
        }
        SettingsSectionTitle(stringResource(itemsSectionTitleResId))
        QuickLauncherGridEditor(
            settings = settings,
            items = items,
            appsByPackage = appsByPackage,
            onItemsChange = onItemsChange,
            onAdd = onAdd,
            onInteractionActiveChange = onInteractionActiveChange,
            showPageSwitcher = showPageSwitcher,
            gridColumnsOverride = gridColumnsOverride,
            gridRowsOverride = gridRowsOverride,
        )
        bottomContent?.invoke(this)
    }
}

@Composable
internal fun QuickLauncherEditorAddPicker(
    padding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    apps: List<AppInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredAppPackages: Set<String>,
    configuredShortcutKeys: Set<String>,
    configuredActionKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    onToggleAction: (GestureAction, String, Boolean) -> Unit,
    onToggleApp: (AppInfo, Boolean) -> Unit,
    onToggleShortcut: (AppInfo, TaskSwitcherMenuItem, Boolean) -> Unit,
    onToggleActivityShortcut: (QuickLauncherItem, Boolean) -> Unit,
    onCreatedShortcut: (AppShortcutLoader.CreatedShortcut) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    includeActionsTab: Boolean = true,
) {
    var selectedTab by remember { mutableIntStateOf(if (includeActionsTab) 0 else 1) }
    val tabs = remember(includeActionsTab) {
        if (includeActionsTab) {
            QuickLauncherEditorAddTab.entries.toList()
        } else {
            listOf(QuickLauncherEditorAddTab.APPS, QuickLauncherEditorAddTab.SHORTCUTS)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        val modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .nestedScroll(nestedScrollConnection)
        MiuixTabRowWithContour(
            tabs = tabs.map { tab ->
                stringResource(
                    when (tab) {
                        QuickLauncherEditorAddTab.ACTIONS -> R.string.action_picker_tab_actions
                        QuickLauncherEditorAddTab.APPS -> R.string.action_picker_tab_apps
                        QuickLauncherEditorAddTab.SHORTCUTS -> R.string.action_picker_tab_shortcuts
                    },
                )
            },
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        when (tabs[selectedTab]) {
            QuickLauncherEditorAddTab.ACTIONS -> QuickLauncherEditorActionsTab(
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                configuredActionKeys = configuredActionKeys,
                onToggle = onToggleAction,
                modifier = modifier,
            )
            QuickLauncherEditorAddTab.APPS -> QuickLauncherEditorAppsTab(
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                apps = apps,
                configuredAppPackages = configuredAppPackages,
                onToggle = onToggleApp,
                modifier = modifier,
            )
            QuickLauncherEditorAddTab.SHORTCUTS -> QuickLauncherEditorShortcutsTab(
                apps = apps,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                configuredShortcutKeys = configuredShortcutKeys,
                activityShortcuts = activityShortcuts,
                onToggle = onToggleShortcut,
                onToggleActivityShortcut = onToggleActivityShortcut,
                onCreatedShortcut = onCreatedShortcut,
                onBrowseActivityShortcut = onBrowseActivityShortcut,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun QuickLauncherEditorActionsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredActionKeys: Set<String>,
    onToggle: (GestureAction, String, Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val actionOptions = remember {
        GestureActionCatalog.build(scope = GestureActionCatalogScope.QuickLauncher)
    }
    val filtered = remember(actionOptions, searchQuery, context) {
        GestureActionCatalog.filter(context, actionOptions, searchQuery)
    }
    Column(modifier = modifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
            hintResId = R.string.search_actions_hint,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            if (filtered.isEmpty()) {
                item(key = "actions-empty") {
                    Text(
                        text = stringResource(R.string.search_no_actions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(filtered.size, key = { filtered[it].type.id }) { index ->
                    val action = filtered[index]
                    val label = gestureActionLabel(action)
                    val added = QuickLauncherItemCodec.actionKey(action) in configuredActionKeys
                    QuickLauncherActionRow(
                        action = action,
                        segmentIndex = index,
                        segmentCount = filtered.size,
                        label = label,
                        subtitle = gestureActionDescription(action),
                        added = added,
                        onToggle = {
                            if (!added) {
                                requestPermissionForAdjustAction(context, action)
                            }
                            onToggle(action, label, added)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickLauncherEditorAppsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
    modifier: Modifier,
) {
    val query = searchQuery.trim().lowercase()
    val filtered = remember(apps, query) {
        apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(app.label).contains(query)
        }.sortedBy { PinyinHelper.sortKey(it.label) }
    }
    Column(modifier = modifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            items(filtered.size, key = { filtered[it].packageName }) { index ->
                val app = filtered[index]
                val added = app.packageName in configuredAppPackages
                QuickLauncherToggleRow(
                    entry = AppPackageEntry.Installed(app),
                    segmentIndex = index,
                    segmentCount = filtered.size,
                    added = added,
                    onToggle = { onToggle(app, added) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickLauncherEditorShortcutsTab(
    apps: List<AppInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredShortcutKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    onToggle: (AppInfo, TaskSwitcherMenuItem, Boolean) -> Unit,
    onToggleActivityShortcut: (QuickLauncherItem, Boolean) -> Unit,
    onCreatedShortcut: (AppShortcutLoader.CreatedShortcut) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    modifier: Modifier,
) {
    val loadedCatalog = rememberLoadedShortcutCatalog(apps)
    val filtered = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
            ?: return@rememberLauncherForActivityResult
        onCreatedShortcut(created)
    }

    Column(modifier = modifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            if (searchQuery.isBlank() || activityShortcuts.isNotEmpty()) {
                activityShortcutPickerToggleSection(
                    activityShortcuts = activityShortcuts,
                    configuredShortcutKeys = configuredShortcutKeys,
                    onToggle = onToggleActivityShortcut,
                    onBrowse = onBrowseActivityShortcut,
                    searchQuery = searchQuery,
                )
            }
            systemShortcutCatalogItems(
                filtered = filtered,
                appsByPackage = appsByPackage,
                loading = loadedCatalog.loading,
                scanProgress = loadedCatalog.scanProgress,
                onCreateHostClick = { host ->
                    pendingCreateHost = host
                    runCatching { createLauncher.launch(host.createIntent()) }
                        .onFailure { pendingCreateHost = null }
                },
                shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                    val item = shortcut.toQuickLauncherItem(group.app.packageName)
                    val added = QuickLauncherItemCodec.shortcutItemKey(item) in configuredShortcutKeys
                    ShortcutCatalogRow(
                        shortcut = shortcut,
                        segmentIndex = segmentIndex,
                        segmentCount = segmentCount,
                        added = added,
                        onToggle = { onToggle(group.app, shortcut, added) },
                    )
                },
            )
        }
    }
}

@Composable
private fun ShortcutCatalogRow(
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = added,
        onClick = onToggle,
        modifier = Modifier.padding(start = 12.dp),
        leadingContent = {
            Md3PickerIconLeading(
                icon = Icons.AutoMirrored.Filled.Shortcut,
                selected = added,
            )
        },
        trailingMode = PickerTrailingMode.Toggle,
        onTrailingClick = onToggle,
    )
}
