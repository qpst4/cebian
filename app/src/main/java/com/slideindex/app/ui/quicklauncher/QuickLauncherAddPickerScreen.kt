package com.slideindex.app.ui.quicklauncher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButton
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherAddPickerScreen(
    panelId: String,
    settings: AppSettings,
    onBack: () -> Unit,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onAddItem: (QuickLauncherItem) -> Unit,
    onPickApp: () -> Unit,
    onMyShortcuts: () -> Unit,
    onPresetShortcuts: () -> Unit,
    onOpenExecuteShellCommand: (String) -> Unit,
    onOpenCreateFolder: () -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val tabs = remember { QuickLauncherEditorAddTab.entries }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

    val panel = remember(settings.quickLauncherPanels, panelId) {
        QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels).find { it.id == panelId }
            ?: QuickLauncherPanelDefaults.defaultPanel()
    }
    val items = panel.items

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

    var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != android.app.Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
            ?: return@rememberLauncherForActivityResult
        onAddItem(created.toQuickLauncherItem())
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
            onBack()
        }
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.quick_launcher_add),
        onBack = addPickerBack,
        modifier = Modifier.fillMaxSize(),
        actions = {
            IconButton(onClick = onOpenCreateFolder) {
                Icon(
                    MiuixIcons.AddFolder,
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
                        onToggleItem(item, added)
                    },
                    onOpenExecuteShellCommand = { onOpenExecuteShellCommand("") },
                )
            }
            QuickLauncherEditorAddTab.APPS -> {
                quickLauncherAddPickerAppItems(
                    filtered = filteredApps,
                    configuredAppPackages = configuredAppPackages,
                    onToggle = { app, added ->
                        onToggleItem(QuickLauncherItem.app(app.packageName, app.label), added)
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
                        onToggleItem(shortcut.toQuickLauncherItem(app.packageName), added)
                    },
                    onToggleActivityShortcut = { item, added -> onToggleItem(item, added) },
                    onBrowseActivityShortcut = onPickApp,
                    onOpenMyShortcuts = onMyShortcuts,
                    onOpenPresetShortcuts = onPresetShortcuts,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherCreateFolderScreen(
    settings: AppSettings,
    draft: com.slideindex.app.ui.viewmodel.QuickLauncherFolderDraft?,
    onBack: () -> Unit,
    onConfirmCreateFolder: (String, List<QuickLauncherItem>) -> Unit,
    onOpenExecuteShellCommand: (String) -> Unit,
    onPickApp: () -> Unit,
    onMyShortcuts: () -> Unit,
    onPresetShortcuts: () -> Unit,
    onFolderNameChange: (String) -> Unit,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onAddItem: (QuickLauncherItem) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    val folderName = draft?.name.orEmpty()
    val folderItems = draft?.items.orEmpty()
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val tabs = remember { QuickLauncherEditorAddTab.entries }
    var folderSelectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

    var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != android.app.Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
            ?: return@rememberLauncherForActivityResult
        onAddItem(created.toQuickLauncherItem())
    }

    val configuredFolderActionKeys = remember(folderItems) {
        folderItems.filter { it.type == QuickLauncherItemType.ACTION }
            .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) }
            .toSet()
    }
    val configuredFolderAppPackages = remember(folderItems) {
        folderItems.filter { it.type == QuickLauncherItemType.APP }
            .map { it.payload }
            .toSet()
    }
    val configuredFolderShortcutKeys = remember(folderItems) {
        folderItems.filter { it.type == QuickLauncherItemType.SHORTCUT }
            .mapNotNull { QuickLauncherItemCodec.shortcutItemKey(it) }
            .toSet()
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
            onBack()
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
                    onConfirmCreateFolder(finalName, folderItems)
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
                    onValueChange = onFolderNameChange,
                    label = stringResource(R.string.quick_launcher_folder_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (folderItems.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.quick_launcher_folder_items_count, folderItems.size),
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
                        onToggleItem(item, added)
                    },
                    onOpenExecuteShellCommand = { onOpenExecuteShellCommand("") },
                )
            }
            QuickLauncherEditorAddTab.APPS -> {
                quickLauncherAddPickerAppItems(
                    filtered = filteredApps,
                    configuredAppPackages = configuredFolderAppPackages,
                    onToggle = { app, added ->
                        onToggleItem(QuickLauncherItem.app(app.packageName, app.label), added)
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
                        onToggleItem(shortcut.toQuickLauncherItem(app.packageName), added)
                    },
                    onToggleActivityShortcut = { item, added -> onToggleItem(item, added) },
                    onBrowseActivityShortcut = onPickApp,
                    onOpenMyShortcuts = onMyShortcuts,
                    onOpenPresetShortcuts = onPresetShortcuts,
                )
            }
        }
    }
}
