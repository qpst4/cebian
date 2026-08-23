package com.slideindex.app.ui.quicklauncher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
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
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem

private val HoneycombAddPickerTabs = listOf(
    QuickLauncherEditorAddTab.ACTIONS,
    QuickLauncherEditorAddTab.APPS,
    QuickLauncherEditorAddTab.SHORTCUTS,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherAddPickerScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onAddItem: (QuickLauncherItem) -> Unit,
    onPickApp: () -> Unit,
    onMyShortcuts: () -> Unit,
    onPresetShortcuts: () -> Unit,
    onOpenExecuteShellCommand: (String) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val items = remember(settings.honeycombLauncher) {
        settings.honeycombLauncher.honeycombRuntimeItems()
    }

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

    val isAppsTabActive = HoneycombAddPickerTabs.getOrNull(selectedTab) == QuickLauncherEditorAddTab.APPS
    val isShortcutsTabActive = HoneycombAddPickerTabs.getOrNull(selectedTab) == QuickLauncherEditorAddTab.SHORTCUTS
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
        title = stringResource(R.string.honeycomb_launcher_add),
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
                tabContent = {
                    MiuixTabRowWithContour(
                        tabs = HoneycombAddPickerTabs.map { tab ->
                            stringResource(
                                when (tab) {
                                    QuickLauncherEditorAddTab.APPS -> R.string.action_picker_tab_apps
                                    QuickLauncherEditorAddTab.SHORTCUTS -> R.string.action_picker_tab_shortcuts
                                    QuickLauncherEditorAddTab.ACTIONS -> R.string.action_picker_tab_actions
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
        when (HoneycombAddPickerTabs[selectedTab]) {
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
