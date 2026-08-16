package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.appswitcher.appSwitcherRuntimeItems
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.navigation.rememberContentReady
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorAddTab
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerActionItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerAppItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerShortcutItems
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredActions
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredApps
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsDeferredLoadingIndicator
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsCardItems

private sealed class AppSwitcherEditorMode {
    data object Main : AppSwitcherEditorMode()
    data object AddPicker : AppSwitcherEditorMode()
    data object PickApp : AppSwitcherEditorMode()
    data class PickActivity(val packageName: String) : AppSwitcherEditorMode()
    data class ShellCommandConfig(val initialCommand: String = "") : AppSwitcherEditorMode()
}

private fun AppSwitcherEditorMode.navDepth(): Int = when (this) {
    AppSwitcherEditorMode.Main -> 0
    AppSwitcherEditorMode.AddPicker -> 1
    AppSwitcherEditorMode.PickApp -> 2
    is AppSwitcherEditorMode.PickActivity -> 3
    is AppSwitcherEditorMode.ShellCommandConfig -> 2
}

private val AppSwitcherAddPickerTabs = listOf(
    QuickLauncherEditorAddTab.ACTIONS,
    QuickLauncherEditorAddTab.APPS,
    QuickLauncherEditorAddTab.SHORTCUTS,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSwitcherLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
    onOpenDisplaySettings: () -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var mode by remember { mutableStateOf<AppSwitcherEditorMode>(AppSwitcherEditorMode.Main) }
    var searchQuery by remember { mutableStateOf("") }
    var layoutEditing by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(settings.appSwitcherItems.appSwitcherRuntimeItems()) }

    LaunchedEffect(settings.appSwitcherItems) {
        if (!layoutEditing) {
            items = settings.appSwitcherItems.appSwitcherRuntimeItems()
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
        onBack()
    }

    fun persistItems(next: List<QuickLauncherItem>) {
        val normalized = next.appSwitcherRuntimeItems()
        items = normalized
        onSaveItems(normalized)
    }

    fun addItem(item: QuickLauncherItem) {
        when (item.type) {
            QuickLauncherItemType.APP, QuickLauncherItemType.SHORTCUT, QuickLauncherItemType.ACTION ->
                persistItems(items + item)
            else -> Unit
        }
    }

    fun removeItem(item: QuickLauncherItem) {
        val next = when (item.type) {
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
            else -> return
        }
        persistItems(next)
    }

    fun toggleItem(item: QuickLauncherItem, added: Boolean) {
        if (added) removeItem(item) else addItem(item)
    }

    val displaySettingsCard = settingsCardItems {
        SettingNavigationRow(
            icon = { label ->
                Icon(Icons.Outlined.Tune, contentDescription = label)
            },
            title = stringResource(R.string.app_switcher_display_settings_entry),
            subtitle = stringResource(R.string.app_switcher_display_settings_entry_desc),
            onClick = onOpenDisplaySettings,
        )
    }

    AnimatedContent(
        targetState = mode,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(AppSwitcherEditorMode::navDepth) },
        label = "appSwitcherLauncherEditorSubNav",
    ) { currentMode ->
        when (currentMode) {
            is AppSwitcherEditorMode.ShellCommandConfig -> {
                GestureExecuteShellCommandScreen(
                    initialCommand = currentMode.initialCommand,
                    shellCommands = settings.shellCommands,
                    onBack = { mode = AppSwitcherEditorMode.AddPicker },
                    onConfirm = { command ->
                        val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                        addItem(
                            QuickLauncherItem.action(
                                GestureAction.ExecuteShellCommand(command),
                                label,
                            ),
                        )
                        mode = AppSwitcherEditorMode.AddPicker
                    },
                )
            }
            AppSwitcherEditorMode.PickApp -> {
                ActivityShortcutPickAppScreen(
                    onBack = { mode = AppSwitcherEditorMode.AddPicker },
                    onSelectApp = { app -> mode = AppSwitcherEditorMode.PickActivity(app.packageName) },
                )
            }
            is AppSwitcherEditorMode.PickActivity -> {
                ActivityShortcutPickActivityScreen(
                    packageName = currentMode.packageName,
                    onBack = { mode = AppSwitcherEditorMode.PickApp },
                    onSelectActivity = { activity ->
                        addItem(
                            QuickLauncherItem.shortcut(
                                "${activity.packageName}/${activity.className}",
                                activity.label,
                            ),
                        )
                        mode = AppSwitcherEditorMode.AddPicker
                    },
                )
            }
            AppSwitcherEditorMode.Main -> {
                val contentReady = rememberContentReady()
                SettingsScreenScaffold(
                    title = stringResource(R.string.app_switcher_editor_title),
                    onBack = { saveAndBack() },
                    scrollContent = false,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!contentReady) {
                        LazySettingsItem(key = "honeycomb-launcher-loading", fillParentMaxSize = true) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                SettingsDeferredLoadingIndicator()
                            }
                        }
                    } else {
                    LazySettingsItem(key = "honeycomb-launcher-main", fillParentMaxSize = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        displaySettingsCard.RenderRows()
                        AppSwitcherLauncherItemsSection(
                            modifier = Modifier.weight(1f),
                            items = items,
                            display = settings.appSwitcherDisplay,
                            appsByPackage = appsByPackage,
                            onItemsChange = ::persistItems,
                            onAdd = {
                                searchQuery = ""
                                mode = AppSwitcherEditorMode.AddPicker
                            },
                            onInteractionActiveChange = { layoutEditing = it },
                            activityShortcuts = settings.activityShortcuts,
                            shellCommands = settings.shellCommands,
                        )
                    }
                    }
                    }
                }
            }
            AppSwitcherEditorMode.AddPicker -> {
                val context = LocalContext.current
                var selectedTab by remember { mutableIntStateOf(0) }
                var searchExpanded by remember { mutableStateOf(false) }
                val searchFocusRequester = remember { FocusRequester() }
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
                val isAppsTabActive = AppSwitcherAddPickerTabs.getOrNull(selectedTab) == QuickLauncherEditorAddTab.APPS
                val isShortcutsTabActive = AppSwitcherAddPickerTabs.getOrNull(selectedTab) == QuickLauncherEditorAddTab.SHORTCUTS
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
                        mode = AppSwitcherEditorMode.Main
                        searchQuery = ""
                    }
                }

                SettingsLazyScreenScaffold(
                    title = stringResource(R.string.app_switcher_add),
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
                                    tabs = AppSwitcherAddPickerTabs.map { tab ->
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
                    when (AppSwitcherAddPickerTabs[selectedTab]) {
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
                                    mode = AppSwitcherEditorMode.ShellCommandConfig()
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
                                onBrowseActivityShortcut = { mode = AppSwitcherEditorMode.PickApp },
                            )
                        }
                    }
                }
            }
        }
    }
}
