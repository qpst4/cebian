package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.ui.compose.rememberAppRepository
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
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

private sealed class HoneycombEditorMode {
    data object Main : HoneycombEditorMode()
    data object AddPicker : HoneycombEditorMode()
    data object PickApp : HoneycombEditorMode()
    data class PickActivity(val packageName: String) : HoneycombEditorMode()
    data class ShellCommandConfig(val initialCommand: String = "") : HoneycombEditorMode()
}

private fun HoneycombEditorMode.navDepth(): Int = when (this) {
    HoneycombEditorMode.Main -> 0
    HoneycombEditorMode.AddPicker -> 1
    HoneycombEditorMode.PickApp -> 2
    is HoneycombEditorMode.PickActivity -> 3
    is HoneycombEditorMode.ShellCommandConfig -> 2
}

private val HoneycombAddPickerTabs = listOf(
    QuickLauncherEditorAddTab.ACTIONS,
    QuickLauncherEditorAddTab.APPS,
    QuickLauncherEditorAddTab.SHORTCUTS,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
    onOpenDisplaySettings: () -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var mode by remember { mutableStateOf<HoneycombEditorMode>(HoneycombEditorMode.Main) }
    var searchQuery by remember { mutableStateOf("") }
    var layoutEditing by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(settings.honeycombLauncher.honeycombRuntimeItems()) }

    LaunchedEffect(settings.honeycombLauncher) {
        if (!layoutEditing) {
            items = settings.honeycombLauncher.honeycombRuntimeItems()
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
        val normalized = next.honeycombRuntimeItems()
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

    AnimatedContent(
        targetState = mode,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(HoneycombEditorMode::navDepth) },
        label = "honeycombLauncherEditorSubNav",
    ) { currentMode ->
        when (currentMode) {
            is HoneycombEditorMode.ShellCommandConfig -> {
                GestureExecuteShellCommandScreen(
                    initialCommand = currentMode.initialCommand,
                    shellCommands = settings.shellCommands,
                    onBack = { mode = HoneycombEditorMode.AddPicker },
                    onConfirm = { command ->
                        val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                        addItem(
                            QuickLauncherItem.action(
                                GestureAction.ExecuteShellCommand(command),
                                label,
                            ),
                        )
                        mode = HoneycombEditorMode.AddPicker
                    },
                )
            }
            HoneycombEditorMode.PickApp -> {
                ActivityShortcutPickAppScreen(
                    onBack = { mode = HoneycombEditorMode.AddPicker },
                    onSelectApp = { app -> mode = HoneycombEditorMode.PickActivity(app.packageName) },
                )
            }
            is HoneycombEditorMode.PickActivity -> {
                ActivityShortcutPickActivityScreen(
                    packageName = currentMode.packageName,
                    onBack = { mode = HoneycombEditorMode.PickApp },
                    onSelectActivity = { activity ->
                        addItem(
                            QuickLauncherItem.shortcut(
                                "${activity.packageName}/${activity.className}",
                                activity.label,
                            ),
                        )
                        mode = HoneycombEditorMode.AddPicker
                    },
                )
            }
            HoneycombEditorMode.Main -> {
                SettingsScreenScaffold(
                    title = stringResource(R.string.honeycomb_launcher_editor_title),
                    onBack = { saveAndBack() },
                    scrollContent = false,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SettingsCard(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(Icons.Outlined.Tune, contentDescription = label)
                                },
                                title = stringResource(R.string.honeycomb_display_settings_entry),
                                subtitle = stringResource(R.string.honeycomb_display_settings_entry_desc),
                                onClick = onOpenDisplaySettings,
                            )
                        }
                        HoneycombLauncherItemsSection(
                            modifier = Modifier.weight(1f),
                            items = items,
                            display = settings.honeycombDisplay,
                            appsByPackage = appsByPackage,
                            onItemsChange = ::persistItems,
                            onAdd = {
                                searchQuery = ""
                                mode = HoneycombEditorMode.AddPicker
                            },
                            onInteractionActiveChange = { layoutEditing = it },
                            activityShortcuts = settings.activityShortcuts,
                        )
                    }
                }
            }
            HoneycombEditorMode.AddPicker -> {
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
                        mode = HoneycombEditorMode.Main
                        searchQuery = ""
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
                                    toggleItem(item, added)
                                },
                                onOpenExecuteShellCommand = {
                                    mode = HoneycombEditorMode.ShellCommandConfig()
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
                                onBrowseActivityShortcut = { mode = HoneycombEditorMode.PickApp },
                            )
                        }
                    }
                }
            }
        }
    }
}
