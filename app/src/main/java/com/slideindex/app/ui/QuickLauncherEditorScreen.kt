package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.QuickLauncherPanelMutator
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
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import top.yukonga.miuix.kmp.basic.TextButton
import com.slideindex.app.ui.miuix.AdaptiveTopAppBar
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixBlurredTopBar
import com.slideindex.app.ui.miuix.miuixAppBarColor
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.ui.quicklauncher.QuickLauncherPanelManagementSection
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorAddTab
import com.slideindex.app.ui.quicklauncher.quickLauncherAppearanceCardItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAppearanceSettingsSection
import com.slideindex.app.ui.settings.components.SettingsDeferredLoadingIndicator
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

import com.slideindex.app.launcher.QuickLauncherDefaults
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.navigation.rememberContentReady

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.slideindex.app.ui.quicklauncher.QuickLauncherCreateFolderScreen

private sealed class EditorMode {
    data object Main : EditorMode()
    data object AddPicker : EditorMode()
    data object MyShortcuts : EditorMode()
    data object PresetShortcuts : EditorMode()
    data object PickApp : EditorMode()
    data class PickActivity(val packageName: String) : EditorMode()
    data class ShellCommandConfig(val initialCommand: String = "") : EditorMode()
    data object CreateFolder : EditorMode()
}

private fun EditorMode.navDepth(): Int = when (this) {
    EditorMode.Main -> 0
    EditorMode.AddPicker -> 1
    EditorMode.MyShortcuts -> 2
    EditorMode.PresetShortcuts -> 2
    EditorMode.PickApp -> 2
    is EditorMode.PickActivity -> 3
    is EditorMode.ShellCommandConfig -> 2
    EditorMode.CreateFolder -> 2
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSavePanels: (List<QuickLauncherPanel>) -> Unit,
    onDisplayChange: (com.slideindex.app.settings.QuickLauncherDisplaySettings) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var mode by remember { mutableStateOf<EditorMode>(EditorMode.Main) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var panels by remember {
        mutableStateOf(QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels))
    }
    var selectedPanelIndex by remember { mutableIntStateOf(0) }
    var gridInteractionActive by remember { mutableStateOf(false) }

    LaunchedEffect(settings.quickLauncherPanels) {
        panels = QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
        selectedPanelIndex = selectedPanelIndex.coerceIn(0, (panels.size - 1).coerceAtLeast(0))
    }

    val currentPanel = panels.getOrElse(selectedPanelIndex) {
        QuickLauncherPanelDefaults.defaultPanel()
    }
    var items by remember(currentPanel.id) { mutableStateOf(currentPanel.items) }

    val (appearanceItems, shapeItems) = quickLauncherAppearanceCardItems(
        display = settings.quickLauncherDisplay,
        enabled = true,
        onDisplayChange = onDisplayChange,
    )
    val appearanceSectionTitle = stringResource(R.string.quick_launcher_appearance_section)
    val iconShapeSectionTitle = stringResource(R.string.quick_launcher_icon_shape_section)

    var defaultsSeeded by remember { mutableStateOf(false) }

    LaunchedEffect(allApps, panels.size, settings.quickLauncherPanels) {
        if (defaultsSeeded || allApps.isEmpty()) return@LaunchedEffect
        if (panels.size != 1) {
            defaultsSeeded = true
            return@LaunchedEffect
        }
        val onlyPanel = panels.first()
        if (onlyPanel.items.isNotEmpty()) {
            defaultsSeeded = true
            return@LaunchedEffect
        }
        val effective = QuickLauncherDefaults.effectiveItems(emptyList(), allApps)
        if (effective.isNotEmpty()) {
            val updated = QuickLauncherPanelMutator.updatePanelItems(panels, onlyPanel.id, effective)
            panels = updated
            items = effective
            onSavePanels(updated)
        }
        defaultsSeeded = true
    }

    LaunchedEffect(selectedPanelIndex, panels) {
        items = panels.getOrElse(selectedPanelIndex) {
            QuickLauncherPanelDefaults.defaultPanel()
        }.items
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

    fun persistCurrentPanelItems(updatedItems: List<QuickLauncherItem> = items) {
        val updated = QuickLauncherPanelMutator.updatePanelItems(panels, currentPanel.id, updatedItems)
        panels = updated
        onSavePanels(updated)
    }

    fun saveAndBack() {
        persistCurrentPanelItems()
        onBack()
    }

    fun addItem(item: QuickLauncherItem) {
        items = items + item
        persistCurrentPanelItems(items)
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
            QuickLauncherItemType.FOLDER ->
                items.filterNot { it.type == QuickLauncherItemType.FOLDER && it.payload == item.payload && it.label == item.label }
        }
        persistCurrentPanelItems(items)
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
            is EditorMode.ShellCommandConfig -> {
                GestureExecuteShellCommandScreen(
                    initialCommand = currentMode.initialCommand,
                    shellCommands = settings.shellCommands,
                    onBack = { mode = EditorMode.AddPicker },
                    onConfirm = { command ->
                        val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                        addItem(
                            QuickLauncherItem.action(
                                GestureAction.ExecuteShellCommand(command),
                                label,
                            ),
                        )
                        mode = EditorMode.AddPicker
                    },
                )
            }
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
            EditorMode.MyShortcuts -> {
                MyShortcutsFolderScreen(
                    activityShortcuts = settings.activityShortcuts,
                    onBack = { mode = EditorMode.AddPicker },
                    onBrowseNewShortcut = { mode = EditorMode.PickApp },
                    configuredShortcutKeys = configuredShortcutKeys,
                    onToggle = { item, added -> toggleItem(item, added) },
                )
            }
            EditorMode.PresetShortcuts -> {
                PresetShortcutsFolderScreen(
                    onBack = { mode = EditorMode.AddPicker },
                    configuredShortcutKeys = configuredShortcutKeys,
                    onToggle = { item, added -> toggleItem(item, added) },
                )
            }
            EditorMode.Main -> {
                val contentReady = rememberContentReady()
                SettingsLazyScreenScaffold(
                    title = stringResource(R.string.quick_launcher_editor_title),
                    onBack = { saveAndBack() },
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !gridInteractionActive,
                ) {
                    if (!contentReady) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                SettingsDeferredLoadingIndicator()
                            }
                        }
                    } else {
                    item(key = "desc") {
                        MiuixHintText(stringResource(R.string.quick_launcher_editor_desc))
                    }
                    settingsLazySmallTitle(
                        key = "quick-launcher-appearance",
                        title = appearanceSectionTitle,
                    )
                    quickLauncherAppearanceSettingsSection(
                        appearanceItems = appearanceItems,
                        shapeItems = shapeItems,
                        iconShapeSectionTitle = iconShapeSectionTitle,
                    )
                    item(key = "panel_and_grid") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            QuickLauncherPanelManagementSection(
                                panels = panels,
                                selectedIndex = selectedPanelIndex,
                                defaultColumns = settings.quickLauncherColumnsPerPage,
                                defaultRows = settings.quickLauncherRowsPerPage,
                                onPanelsChange = { updated ->
                                    panels = updated
                                    onSavePanels(updated)
                                },
                                onSelectedIndexChange = { selectedPanelIndex = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MiuixSmallTitle(
                                stringResource(R.string.quick_launcher_items_section),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = MiuixSmallTitleSectionTop),
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                key(currentPanel.id) {
                                    QuickLauncherGridEditor(
                                        settings = settings,
                                        items = items,
                                        appsByPackage = appsByPackage,
                                        onItemsChange = {
                                            items = it
                                            persistCurrentPanelItems(it)
                                        },
                                        onAdd = {
                                            searchQuery = ""
                                            mode = EditorMode.AddPicker
                                        },
                                        onInteractionActiveChange = { gridInteractionActive = it },
                                        gridColumnsOverride = currentPanel.columnsPerPage,
                                        gridRowsOverride = currentPanel.rowsPerPage,
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            EditorMode.AddPicker -> {
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
                val isAppsTabActive = tabs[selectedTab] == QuickLauncherEditorAddTab.APPS
                val isShortcutsTabActive = tabs[selectedTab] == QuickLauncherEditorAddTab.SHORTCUTS
                val filteredActions = rememberQuickLauncherFilteredActions(searchQuery)
                val filteredApps = rememberQuickLauncherFilteredApps(
                    apps = allApps,
                    searchQuery = searchQuery,
                    enabled = isAppsTabActive || searchQuery.isNotBlank(),
                )
                val loadedCatalog = rememberLoadedShortcutCatalog(
                    apps = allApps,
                    enabled = isShortcutsTabActive || searchQuery.isNotBlank(),
                )
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
                        IconButton(onClick = { mode = EditorMode.CreateFolder }) {
                            Icon(
                                Icons.Outlined.CreateNewFolder,
                                contentDescription = stringResource(R.string.quick_launcher_new_folder),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
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
                                onToggleItem = { item, added ->
                                    if (!added) {
                                        QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                                            requestPermissionForAdjustAction(context, action)
                                        }
                                    }
                                    toggleItem(item, added)
                                },
                                onOpenExecuteShellCommand = {
                                    mode = EditorMode.ShellCommandConfig()
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
                                onOpenMyShortcuts = { mode = EditorMode.MyShortcuts },
                                onOpenPresetShortcuts = { mode = EditorMode.PresetShortcuts },
                            )
                        }
                    }
                }
            }
            EditorMode.CreateFolder -> {
                var folderName by remember { mutableStateOf("") }
                var selectedFolderItems by remember { mutableStateOf<List<QuickLauncherItem>>(emptyList()) }
                var searchExpanded by remember { mutableStateOf(false) }
                val searchFocusRequester = remember { FocusRequester() }
                val tabs = remember { QuickLauncherEditorAddTab.entries }
                var folderSelectedTab by remember { mutableIntStateOf(0) }
                var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
                val createLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val host = pendingCreateHost
                    pendingCreateHost = null
                    if (result.resultCode != android.app.Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
                    val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
                        ?: return@rememberLauncherForActivityResult
                    selectedFolderItems = selectedFolderItems + created.toQuickLauncherItem()
                }

                val configuredFolderActionKeys = remember(selectedFolderItems) {
                    selectedFolderItems.filter { it.type == QuickLauncherItemType.ACTION }
                        .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) }
                        .toSet()
                }
                val configuredFolderAppPackages = remember(selectedFolderItems) {
                    selectedFolderItems.filter { it.type == QuickLauncherItemType.APP }
                        .map { it.payload }
                        .toSet()
                }
                val configuredFolderShortcutKeys = remember(selectedFolderItems) {
                    selectedFolderItems.filter { it.type == QuickLauncherItemType.SHORTCUT }
                        .mapNotNull { QuickLauncherItemCodec.shortcutItemKey(it) }
                        .toSet()
                }

                fun toggleFolderItem(item: QuickLauncherItem, added: Boolean) {
                    selectedFolderItems = if (added) {
                        selectedFolderItems.filterNot { it.type == item.type && it.payload == item.payload }
                    } else {
                        selectedFolderItems + item
                    }
                }

                val isAppsTabActive = tabs[folderSelectedTab] == QuickLauncherEditorAddTab.APPS
                val isShortcutsTabActive = tabs[folderSelectedTab] == QuickLauncherEditorAddTab.SHORTCUTS
                val filteredActions = rememberQuickLauncherFilteredActions(searchQuery)
                val filteredApps = rememberQuickLauncherFilteredApps(
                    apps = allApps,
                    searchQuery = searchQuery,
                    enabled = isAppsTabActive || searchQuery.isNotBlank(),
                )
                val loadedCatalog = rememberLoadedShortcutCatalog(
                    apps = allApps,
                    enabled = isShortcutsTabActive || searchQuery.isNotBlank(),
                )
                val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
                    filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
                }
                val searchHintResId = when (tabs[folderSelectedTab]) {
                    QuickLauncherEditorAddTab.ACTIONS -> R.string.search_actions_hint
                    QuickLauncherEditorAddTab.APPS, QuickLauncherEditorAddTab.SHORTCUTS -> R.string.search_hint
                }

                val createFolderBack: () -> Unit = {
                    if (
                        !consumeExpandableSearchBack(
                            expanded = searchExpanded,
                            query = searchQuery,
                            onExpandedChange = { searchExpanded = it },
                            onQueryChange = { searchQuery = it },
                        )
                    ) {
                        mode = EditorMode.AddPicker
                        searchQuery = ""
                    }
                }

                SettingsLazyScreenScaffold(
                    title = stringResource(R.string.quick_launcher_create_folder),
                    onBack = createFolderBack,
                    modifier = Modifier.fillMaxSize(),
                    actions = {
                        MiuixExpandableSearchIconAction(
                            expanded = searchExpanded,
                            query = searchQuery,
                            onExpandedChange = { searchExpanded = it },
                            onQueryChange = { searchQuery = it },
                        )
                        TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                val defaultName = "文件夹"
                                val finalName = folderName.trim().ifBlank { defaultName }
                                addItem(QuickLauncherItem.folder(finalName, selectedFolderItems))
                                mode = EditorMode.Main
                            },
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
                                    selectedTabIndex = folderSelectedTab,
                                    onTabSelected = { folderSelectedTab = it },
                                )
                            },
                        )
                    },
                ) {
                    item(key = "folder_name_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            MiuixLabeledTextField(
                                value = folderName,
                                onValueChange = { folderName = it },
                                label = stringResource(R.string.quick_launcher_folder_name),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (selectedFolderItems.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.quick_launcher_folder_items_count, selectedFolderItems.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                )
                            }
                        }
                    }
                    when (tabs[folderSelectedTab]) {
                        QuickLauncherEditorAddTab.ACTIONS -> {
                            quickLauncherAddPickerActionItems(
                                filtered = filteredActions,
                                configuredActionKeys = configuredFolderActionKeys,
                                onToggleItem = { item, added ->
                                    if (!added) {
                                        QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                                            requestPermissionForAdjustAction(context, action)
                                        }
                                    }
                                    toggleFolderItem(item, added)
                                },
                                onOpenExecuteShellCommand = {
                                    mode = EditorMode.ShellCommandConfig()
                                },
                            )
                        }
                        QuickLauncherEditorAddTab.APPS -> {
                            quickLauncherAddPickerAppItems(
                                filtered = filteredApps,
                                configuredAppPackages = configuredFolderAppPackages,
                                onToggle = { app, added ->
                                    toggleFolderItem(QuickLauncherItem.app(app.packageName, app.label), added)
                                },
                            )
                        }
                        QuickLauncherEditorAddTab.SHORTCUTS -> {
                            quickLauncherAddPickerShortcutItems(
                                searchQuery = searchQuery,
                                activityShortcuts = settings.activityShortcuts,
                                configuredShortcutKeys = configuredFolderShortcutKeys,
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
                                    toggleFolderItem(shortcut.toQuickLauncherItem(app.packageName), added)
                                },
                                onToggleActivityShortcut = { item, added -> toggleFolderItem(item, added) },
                                onBrowseActivityShortcut = { mode = EditorMode.PickApp },
                                onOpenMyShortcuts = { mode = EditorMode.MyShortcuts },
                                onOpenPresetShortcuts = { mode = EditorMode.PresetShortcuts },
                            )
                        }
                    }
                }
            }
        }
    }
}
