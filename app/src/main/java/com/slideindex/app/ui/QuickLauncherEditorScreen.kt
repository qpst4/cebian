package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherDefaults
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerActionItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerAppItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerShortcutItems
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredActions
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredApps
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorAddTab
import com.slideindex.app.ui.compose.rememberAppRepository

private sealed class EditorMode {
    data object Main : EditorMode()
    data object AddPicker : EditorMode()
    data object PickApp : EditorMode()
    data class PickActivity(val packageName: String) : EditorMode()
}

private fun EditorMode.navDepth(): Int = when (this) {
    EditorMode.Main -> 0
    EditorMode.AddPicker -> 1
    EditorMode.PickApp -> 2
    is EditorMode.PickActivity -> 3
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onRowsChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var mode by remember { mutableStateOf<EditorMode>(EditorMode.Main) }
    var searchQuery by remember { mutableStateOf("") }
    val currentItems = settings.quickLauncher
    var items by remember(currentItems) { mutableStateOf(currentItems) }
    var gridInteractionActive by remember { mutableStateOf(false) }

    LaunchedEffect(allApps, currentItems) {
        if (allApps.isNotEmpty() && items.isEmpty()) {
            val effective = QuickLauncherDefaults.effectiveItems(currentItems, allApps)
            if (effective.isNotEmpty()) {
                items = effective
                if (currentItems.isEmpty()) {
                    onSaveItems(effective)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val configuredAppPackages = remember(items) {
        items.filter { it.type == QuickLauncherItemType.APP }.map { it.payload }.toSet()
    }
    val configuredShortcutKeys = remember(items) {
        items.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
            QuickLauncherItemCodec.shortcutItemKey(item)
        }.toSet()
    }
    val configuredActionKeys = remember(items) {
        items.filter { it.type == QuickLauncherItemType.ACTION }.mapNotNull { item ->
            QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(QuickLauncherItemCodec::actionKey)
        }.toSet()
    }

    fun saveAndBack() {
        onSaveItems(items)
        onBack()
    }

    fun addItem(item: QuickLauncherItem) {
        items = items + item
    }

    fun removeItem(item: QuickLauncherItem) {
        items = when (item.type) {
            QuickLauncherItemType.APP ->
                items.filterNot { it.type == QuickLauncherItemType.APP && it.payload == item.payload }
            QuickLauncherItemType.SHORTCUT -> {
                val key = QuickLauncherItemCodec.shortcutItemKey(item) ?: return
                items.filterNot {
                    it.type == QuickLauncherItemType.SHORTCUT &&
                        QuickLauncherItemCodec.shortcutItemKey(it) == key
                }
            }
            QuickLauncherItemType.ACTION -> {
                val actionKey = QuickLauncherItemCodec.parseActionPayload(item.payload)
                    ?.let(QuickLauncherItemCodec::actionKey) ?: return
                items.filterNot {
                    it.type == QuickLauncherItemType.ACTION &&
                        QuickLauncherItemCodec.parseActionPayload(it.payload)
                            ?.let(QuickLauncherItemCodec::actionKey) == actionKey
                }
            }
            QuickLauncherItemType.WIDGET ->
                items.filterNot { it.type == QuickLauncherItemType.WIDGET && it.payload == item.payload }
        }
    }

    fun toggleItem(item: QuickLauncherItem, added: Boolean) {
        if (added) removeItem(item) else addItem(item)
    }

    AnimatedContent(
        targetState = mode,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(EditorMode::navDepth) },
        label = "quickLauncherEditorSubNav",
    ) { currentMode ->
        when (currentMode) {
            EditorMode.PickApp -> {
                ActivityShortcutPickAppScreen(
                    onBack = { mode = EditorMode.AddPicker },
                    onSelectApp = { app -> mode = EditorMode.PickActivity(app.packageName) },
                )
            }
            is EditorMode.PickActivity -> {
                ActivityShortcutPickActivityScreen(
                    packageName = currentMode.packageName,
                    onBack = { mode = EditorMode.PickApp },
                    onSelectActivity = { activity ->
                        addItem(
                            QuickLauncherItem.shortcut(
                                "${activity.packageName}/${activity.className}",
                                activity.label,
                            ),
                        )
                        mode = EditorMode.AddPicker
                    },
                )
            }
            EditorMode.Main -> {
                SettingsLazyScreenScaffold(
                    title = stringResource(R.string.quick_launcher_editor_title),
                    onBack = { saveAndBack() },
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !gridInteractionActive,
                ) {
                    item(key = "desc") {
                        MiuixHintText(stringResource(R.string.quick_launcher_editor_desc))
                    }
                    item(key = "layout_section") {
                        MiuixSmallTitle(stringResource(R.string.quick_launcher_layout_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
                    }
                    item(key = "layout_settings") {
                        QuickLauncherLayoutSettings(
                            settings = settings,
                            enabled = true,
                            onColumnsChange = onColumnsChange,
                            onRowsChange = onRowsChange,
                        )
                    }
                    item(key = "items_section") {
                        MiuixSmallTitle(stringResource(R.string.quick_launcher_page_switch), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
                    }
                    item(key = "grid_editor") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp),
                        ) {
                            QuickLauncherGridEditor(
                                settings = settings,
                                items = items,
                                appsByPackage = appsByPackage,
                                onItemsChange = { items = it },
                                onAdd = {
                                    searchQuery = ""
                                    mode = EditorMode.AddPicker
                                },
                                onInteractionActiveChange = { gridInteractionActive = it },
                            )
                        }
                    }
                }
            }
            EditorMode.AddPicker -> {
                var selectedTab by remember { mutableIntStateOf(0) }
                var searchExpanded by remember { mutableStateOf(false) }
                val searchFocusRequester = remember { FocusRequester() }
                val tabs = remember { QuickLauncherEditorAddTab.entries }
                var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
                val createLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val host = pendingCreateHost
                    pendingCreateHost = null
                    if (result.resultCode != android.app.Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
                    val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
                        ?: return@rememberLauncherForActivityResult
                    addItem(created.toQuickLauncherItem())
                }
                val filteredActions = rememberQuickLauncherFilteredActions(searchQuery)
                val filteredApps = rememberQuickLauncherFilteredApps(allApps, searchQuery)
                val loadedCatalog = rememberLoadedShortcutCatalog(allApps)
                val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
                    filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
                }
                val searchHintResId = when (tabs[selectedTab]) {
                    QuickLauncherEditorAddTab.ACTIONS -> R.string.search_actions_hint
                    QuickLauncherEditorAddTab.APPS, QuickLauncherEditorAddTab.SHORTCUTS -> R.string.search_hint
                }
                val addPickerBack: () -> Unit = {
                    if (
                        !consumeExpandableSearchBack(
                            expanded = searchExpanded,
                            query = searchQuery,
                            onExpandedChange = { searchExpanded = it },
                            onQueryChange = { searchQuery = it },
                        )
                    ) {
                        mode = EditorMode.Main
                        searchQuery = ""
                    }
                }

                SettingsLazyScreenScaffold(
                    title = stringResource(R.string.quick_launcher_add),
                    onBack = addPickerBack,
                    modifier = Modifier.fillMaxSize(),
                    actions = {
                        MiuixExpandableSearchIconAction(
                            expanded = searchExpanded,
                            query = searchQuery,
                            onExpandedChange = { searchExpanded = it },
                            onQueryChange = { searchQuery = it },
                        )
                    },
                    bottomContent = {
                        MiuixScaffoldSearchTabBottomContent(
                            searchExpanded = searchExpanded,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            focusRequester = searchFocusRequester,
                            hintResId = searchHintResId,
                            tabContent = {
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
                            },
                        )
                    },
                ) {
                    when (tabs[selectedTab]) {
                        QuickLauncherEditorAddTab.ACTIONS -> {
                            quickLauncherAddPickerActionItems(
                                filtered = filteredActions,
                                configuredActionKeys = configuredActionKeys,
                                onToggle = { action, label, added ->
                                    val item = QuickLauncherItem.action(action, label)
                                    if (!added) {
                                        requestPermissionForAdjustAction(context, action)
                                    }
                                    toggleItem(item, added)
                                },
                            )
                        }
                        QuickLauncherEditorAddTab.APPS -> {
                            quickLauncherAddPickerAppItems(
                                filtered = filteredApps,
                                configuredAppPackages = configuredAppPackages,
                                onToggle = { app, added ->
                                    toggleItem(QuickLauncherItem.app(app.packageName, app.label), added)
                                },
                            )
                        }
                        QuickLauncherEditorAddTab.SHORTCUTS -> {
                            quickLauncherAddPickerShortcutItems(
                                searchQuery = searchQuery,
                                activityShortcuts = settings.activityShortcuts,
                                configuredShortcutKeys = configuredShortcutKeys,
                                filtered = filteredShortcuts,
                                appsByPackage = appsByPackage,
                                loading = loadedCatalog.loading,
                                scanProgress = loadedCatalog.scanProgress,
                                onCreateHostClick = { host ->
                                    pendingCreateHost = host
                                    runCatching { createLauncher.launch(host.createIntent()) }
                                        .onFailure { pendingCreateHost = null }
                                },
                                onToggle = { app, shortcut, added ->
                                    toggleItem(shortcut.toQuickLauncherItem(app.packageName), added)
                                },
                                onToggleActivityShortcut = { item, added -> toggleItem(item, added) },
                                onBrowseActivityShortcut = { mode = EditorMode.PickApp },
                            )
                        }
                    }
                }
            }
        }
    }
}
