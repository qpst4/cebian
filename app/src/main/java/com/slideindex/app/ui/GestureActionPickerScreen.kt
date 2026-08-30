package com.slideindex.app.ui

import android.app.Activity
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.launchShortcutFromCreated
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.gesturepicker.ActionPickerTab
import com.slideindex.app.ui.gesturepicker.actionPickerActionItems
import com.slideindex.app.ui.gesturepicker.actionPickerAppItems
import com.slideindex.app.ui.gesturepicker.ActionPickerShortcutRow
import com.slideindex.app.ui.gesturepicker.actionPickerShortcutItems
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredActions
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredApps
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.util.AppShortcutLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureActionPickerScreen(
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
    onOpenMyShortcuts: () -> Unit,
    onOpenPresetShortcuts: () -> Unit,
    onOpenPickApp: () -> Unit,
    onOpenExecuteShellCommand: (String) -> Unit,
    onOpenSimulateKeyEvent: (GestureAction.SimulateKeyEvent) -> Unit = {},
    includePointerGestureActions: Boolean = false,
    includeCornerInnerZoneActions: Boolean = false,
    pinNoneAtTop: Boolean = true,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
    val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
    val activityShortcuts = appSettings.activityShortcuts
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val handleBack: () -> Unit = {
        if (
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it },
            )
        ) {
            onDismiss()
        }
    }

    val shellConfigInitialCommand = remember(current) {
        (current as? GestureAction.ExecuteShellCommand)?.command.orEmpty()
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        allApps = withContext(Dispatchers.IO) {
            appRepository.loadApps(force = true)
        }
    }

    val filteredActions = rememberActionPickerFilteredActions(
        trigger = trigger,
        searchQuery = searchQuery,
        includePointerGestureActions = includePointerGestureActions,
        includeCornerInnerZoneActions = includeCornerInnerZoneActions,
        pinNoneAtTop = pinNoneAtTop,
    )
    val filteredApps = rememberActionPickerFilteredApps(allApps, searchQuery)
    val loadedCatalog = rememberLoadedShortcutCatalog(allApps)
    val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
            ?: return@rememberLauncherForActivityResult
        onSelect(launchShortcutFromCreated(created))
    }

    val searchHintResId = when (ActionPickerTab.entries[selectedTab]) {
        ActionPickerTab.ACTIONS -> R.string.search_actions_hint
        ActionPickerTab.APPS, ActionPickerTab.SHORTCUTS -> R.string.search_hint
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        SettingsLazyScreenScaffold(
            title = stringResource(R.string.slot_pick_action),
            onBack = handleBack,
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
                            tabs = listOf(
                                stringResource(R.string.action_picker_tab_actions),
                                stringResource(R.string.action_picker_tab_apps),
                                stringResource(R.string.action_picker_tab_shortcuts),
                            ),
                            selectedTabIndex = selectedTab,
                            onTabSelected = { selectedTab = it },
                        )
                    },
                )
            },
        ) {
            when (ActionPickerTab.entries[selectedTab]) {
                ActionPickerTab.ACTIONS -> {
                    actionPickerActionItems(
                        filtered = filteredActions,
                        current = current,
                        onSelect = onSelect,
                        onOpenExecuteShellCommand = {
                            onOpenExecuteShellCommand(shellConfigInitialCommand)
                        },
                        onOpenSimulateKeyEvent = {
                            onOpenSimulateKeyEvent(
                                current as? GestureAction.SimulateKeyEvent ?: GestureAction.SimulateKeyEvent()
                            )
                        },
                    )
                }
                ActionPickerTab.APPS -> {
                    actionPickerAppItems(
                        filtered = filteredApps,
                        current = current,
                        onSelect = { app -> onSelect(GestureAction.LaunchApp(app.packageName)) },
                    )
                }
                ActionPickerTab.SHORTCUTS -> {
                    actionPickerShortcutItems(
                        appsByPackage = appsByPackage,
                        searchQuery = searchQuery,
                        current = current,
                        onSelect = onSelect,
                        activityShortcuts = activityShortcuts,
                        onOpenMyShortcuts = onOpenMyShortcuts,
                        onOpenPresetShortcuts = onOpenPresetShortcuts,
                        onBrowseActivityShortcut = onOpenPickApp,
                        filtered = filteredShortcuts,
                        loading = loadedCatalog.loading,
                        scanProgress = loadedCatalog.scanProgress,
                        onCreateHostClick = { host ->
                            pendingCreateHost = host
                            runCatching { createLauncher.launch(host.createIntent()) }
                                .onFailure { pendingCreateHost = null }
                        },
                        shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                            ActionPickerShortcutRow(
                                shortcut = shortcut,
                                packageName = group.app.packageName,
                                segmentIndex = segmentIndex,
                                segmentCount = segmentCount,
                                current = current,
                                onSelect = onSelect,
                            )
                        },
                    )
                }
            }
        }
    }
}
