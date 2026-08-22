package com.slideindex.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import com.slideindex.app.ui.gesturepicker.actionPickerShortcutItems
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredActions
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredApps
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import androidx.activity.compose.BackHandler
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.slideindex.app.util.AppShortcutLoader

private sealed interface GesturePickerSubScreen {
    data object Main : GesturePickerSubScreen
    data object PickApp : GesturePickerSubScreen
    data class PickActivity(val packageName: String) : GesturePickerSubScreen
    data class ShellCommandConfig(val initialCommand: String = "") : GesturePickerSubScreen
    data object MyShortcuts : GesturePickerSubScreen
    data object PresetShortcuts : GesturePickerSubScreen
}

private fun GesturePickerSubScreen.navDepth(): Int = when (this) {
    GesturePickerSubScreen.Main -> 0
    GesturePickerSubScreen.PickApp -> 1
    is GesturePickerSubScreen.PickActivity -> 2
    is GesturePickerSubScreen.ShellCommandConfig -> 1
    GesturePickerSubScreen.MyShortcuts -> 1
    GesturePickerSubScreen.PresetShortcuts -> 1
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureActionPickerScreen(
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
    includePointerGestureActions: Boolean = false,
    includeCornerInnerZoneActions: Boolean = false,
    pinNoneAtTop: Boolean = true,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var subScreen by remember { mutableStateOf<GesturePickerSubScreen>(GesturePickerSubScreen.Main) }
    val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
    val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
    val shellCommands = appSettings.shellCommands
    val activityShortcuts = appSettings.activityShortcuts
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val handleBack: () -> Unit = {
        when (subScreen) {
            GesturePickerSubScreen.Main -> {
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
            GesturePickerSubScreen.PickApp -> subScreen = GesturePickerSubScreen.Main
            is GesturePickerSubScreen.PickActivity -> subScreen = GesturePickerSubScreen.PickApp
            is GesturePickerSubScreen.ShellCommandConfig -> subScreen = GesturePickerSubScreen.Main
            GesturePickerSubScreen.MyShortcuts -> subScreen = GesturePickerSubScreen.Main
            GesturePickerSubScreen.PresetShortcuts -> subScreen = GesturePickerSubScreen.Main
        }
    }
    BackHandler(
        enabled = subScreen != GesturePickerSubScreen.Main || searchExpanded,
        onBack = handleBack,
    )

    val shellConfigInitialCommand = remember(current) {
        (current as? GestureAction.ExecuteShellCommand)?.command.orEmpty()
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        allApps = withContext(Dispatchers.IO) {
            appRepository.loadApps(force = true)
        }
    }

    AnimatedContent(
        targetState = subScreen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(GesturePickerSubScreen::navDepth) },
        label = "gesturePickerSubNav",
    ) { screen ->
        when (screen) {
        GesturePickerSubScreen.MyShortcuts -> {
            com.slideindex.app.ui.picker.MyShortcutsFolderScreen(
                activityShortcuts = activityShortcuts,
                onBack = { subScreen = GesturePickerSubScreen.Main },
                onBrowseNewShortcut = { subScreen = GesturePickerSubScreen.PickApp },
                currentAction = current,
                onSelectRadio = {
                    onSelect(it)
                    onDismiss()
                },
            )
        }
        GesturePickerSubScreen.PresetShortcuts -> {
            com.slideindex.app.ui.picker.PresetShortcutsFolderScreen(
                onBack = { subScreen = GesturePickerSubScreen.Main },
                currentAction = current,
                onSelectRadio = {
                    onSelect(it)
                    onDismiss()
                },
            )
        }
        is GesturePickerSubScreen.ShellCommandConfig -> {
            GestureExecuteShellCommandScreen(
                initialCommand = screen.initialCommand,
                shellCommands = shellCommands,
                onBack = { subScreen = GesturePickerSubScreen.Main },
                onConfirm = { command ->
                    onSelect(GestureAction.ExecuteShellCommand(command))
                    subScreen = GesturePickerSubScreen.Main
                },
            )
        }
        GesturePickerSubScreen.PickApp -> {
            ActivityShortcutPickAppScreen(
                onBack = { subScreen = GesturePickerSubScreen.Main },
                onSelectApp = { app -> subScreen = GesturePickerSubScreen.PickActivity(app.packageName) },
            )
        }
        is GesturePickerSubScreen.PickActivity -> {
            ActivityShortcutPickActivityScreen(
                packageName = screen.packageName,
                onBack = { subScreen = GesturePickerSubScreen.PickApp },
                onSelectActivity = { activity ->
                    onSelect(
                        GestureAction.LaunchShortcut.component(
                            "${activity.packageName}/${activity.className}",
                            activity.label,
                        ),
                    )
                },
            )
        }
        GesturePickerSubScreen.Main -> {
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
                                subScreen = GesturePickerSubScreen.ShellCommandConfig(
                                    initialCommand = shellConfigInitialCommand,
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
                            onOpenMyShortcuts = { subScreen = GesturePickerSubScreen.MyShortcuts },
                            onOpenPresetShortcuts = { subScreen = GesturePickerSubScreen.PresetShortcuts },
                            onBrowseActivityShortcut = { subScreen = GesturePickerSubScreen.PickApp },
                            filtered = filteredShortcuts,
                            loading = loadedCatalog.loading,
                            scanProgress = loadedCatalog.scanProgress,
                            onCreateHostClick = { host ->
                                pendingCreateHost = host
                                runCatching { createLauncher.launch(host.createIntent()) }
                                    .onFailure { pendingCreateHost = null }
                            },
                        )
                    }
                }
            }
        }
        }
    }
}
