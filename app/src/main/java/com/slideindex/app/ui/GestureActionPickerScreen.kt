package com.slideindex.app.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.slideindex.app.ui.compose.rememberAppRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.launchShortcutFromCreated
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ActionPickerTab { ACTIONS, APPS, SHORTCUTS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureActionPickerScreen(
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var shortcutCatalog by remember { mutableStateOf<AppShortcutLoader.ShortcutCatalog?>(null) }
    var shortcutCatalogLoading by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableStateOf<ShortcutScanProgress?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
    }

    LaunchedEffect(allApps) {
        if (allApps.isEmpty()) {
            shortcutCatalogLoading = false
            scanProgress = null
            return@LaunchedEffect
        }
        shortcutCatalogLoading = true
        scanProgress = ShortcutScanProgress(ShortcutScanPhase.DUMPSYS, 0, 0)
        try {
            shortcutCatalog = withContext(Dispatchers.IO) {
                AppShortcutLoader.loadShortcutCatalog(
                    context = context,
                    apps = allApps,
                    includeShell = true,
                    onProgress = { progress ->
                        mainHandler.post { scanProgress = progress }
                    },
                )
            }
        } catch (_: Exception) {
            shortcutCatalog = AppShortcutLoader.ShortcutCatalog(createHosts = emptyList())
        } finally {
            shortcutCatalogLoading = false
            scanProgress = null
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { SettingsAppBarTitle(stringResource(R.string.slot_pick_action)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val listModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.action_picker_tab_actions)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.action_picker_tab_apps)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.action_picker_tab_shortcuts)) },
                )
            }
            when (ActionPickerTab.entries[selectedTab]) {
                ActionPickerTab.ACTIONS -> ActionPickerActionsTab(
                    trigger = trigger,
                    current = current,
                    onSelect = onSelect,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    listModifier = listModifier,
                )
                ActionPickerTab.APPS -> ActionPickerAppsTab(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    apps = allApps,
                    current = current,
                    onSelect = { app -> onSelect(GestureAction.LaunchApp(app.packageName)) },
                    listModifier = listModifier,
                )
                ActionPickerTab.SHORTCUTS -> ActionPickerShortcutsTab(
                    apps = allApps,
                    catalog = shortcutCatalog,
                    loading = shortcutCatalogLoading,
                    scanProgress = scanProgress,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    current = current,
                    onSelect = onSelect,
                    listModifier = listModifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ActionPickerActionsTab(
    trigger: GestureTriggerType,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    listModifier: Modifier,
) {
    val context = LocalContext.current
    val actionOptions = remember(trigger) {
        buildList {
            add(GestureAction.None)
            add(GestureAction.OpenIndex)
            add(GestureAction.QuickLauncher)
            add(GestureAction.TaskSwitcher)
            add(GestureAction.ShellCommandPanel)
            add(GestureAction.QuickToolsOverlay)
            add(GestureAction.WidgetPopupOverlay)
            add(GestureAction.FloatingPointer)
            add(GestureAction.SimulatePointerSwipe())
            add(GestureAction.Back)
            add(GestureAction.Home)
            add(GestureAction.Recents)
            add(GestureAction.CloseCurrentApp)
            add(GestureAction.FreeWindowCurrentApp)
            add(GestureAction.Flashlight)
            add(GestureAction.ToggleDnd)
            add(GestureAction.ScreenRecord)
            add(GestureAction.ToggleWifi)
            add(GestureAction.ToggleMobileData)
            add(GestureAction.SwitchInputMethod)
            add(GestureAction.ToggleMute)
            add(GestureAction.MediaPlayPause)
            add(GestureAction.MediaPrevious)
            add(GestureAction.MediaNext)
            add(GestureAction.PreviousApp)
            add(GestureAction.OpenNotifications)
            add(GestureAction.OpenQuickSettings)
            add(GestureAction.LockScreen)
            add(GestureAction.Screenshot)
            add(GestureAction.PowerMenu)
            add(GestureAction.KeepScreenOn)
            add(GestureAction.ScrollToTop)
            add(GestureAction.ScrollToBottom)
            add(GestureAction.AdjustVolume)
            add(GestureAction.AdjustBrightness)
            add(GestureAction.LaunchAssistant)
            if (trigger == GestureTriggerType.SHORT_SINGLE_TAP) add(GestureAction.ClickPassthrough)
        }
    }
    val filtered = remember(actionOptions, searchQuery, context) {
        filterGestureActions(context, actionOptions, searchQuery)
    }
    Column(modifier = listModifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
            hintResId = R.string.search_actions_hint,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .selectableGroup(),
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
                    ActionPickerActionRow(
                        action = action,
                        segmentIndex = index,
                        segmentCount = filtered.size,
                        selected = action.type == current.type &&
                            action.type != GestureActionType.LAUNCH_APP &&
                            action.type != GestureActionType.LAUNCH_SHORTCUT,
                        onClick = {
                            requestPermissionForAdjustAction(context, action)
                            onSelect(action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPickerActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val description = gestureActionDescription(action)
    val permissionHint = gestureActionPermissionHint(action, context)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = gestureActionLabel(action),
        subtitle = null,
        selected = selected,
        onClick = onClick,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action),
                selected = selected,
            )
        },
        supportingContent = if (description != null || permissionHint != null) {
            { Md3PickerSupportingHints(description, permissionHint) }
        } else {
            null
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ActionPickerAppsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    current: GestureAction,
    onSelect: (AppInfo) -> Unit,
    listModifier: Modifier,
) {
    val query = searchQuery.trim().lowercase()
    val filtered = remember(apps, query) {
        apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(app.label).contains(query)
        }
    }
    Column(modifier = listModifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .selectableGroup(),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            items(filtered.size, key = { filtered[it].packageName }) { index ->
                val app = filtered[index]
                val selected = current is GestureAction.LaunchApp && current.packageName == app.packageName
                Md3PickerListRow(
                    segmentIndex = index,
                    segmentCount = filtered.size,
                    title = app.label,
                    subtitle = app.packageName,
                    selected = selected,
                    onClick = { onSelect(app) },
                    leadingContent = { Md3PickerAppLeading(app) },
                    trailingMode = PickerTrailingMode.Radio,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ActionPickerShortcutsTab(
    apps: List<AppInfo>,
    catalog: AppShortcutLoader.ShortcutCatalog?,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    listModifier: Modifier,
) {
    val context = LocalContext.current
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

    val createHosts = catalog?.createHosts.orEmpty()
    val launchGroups = catalog?.groups.orEmpty()

    val query = searchQuery.trim().lowercase()
    val filteredCreateHosts = remember(createHosts, query) {
        createHosts.filter { host ->
            query.isEmpty() ||
                host.label.lowercase().contains(query) ||
                host.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(host.label).contains(query)
        }
    }
    val filteredGroups = remember(launchGroups, query) {
        launchGroups.mapNotNull { group ->
            val appMatches = query.isEmpty() ||
                group.app.label.lowercase().contains(query) ||
                group.app.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(group.app.label).contains(query)
            val matchedShortcuts = if (query.isEmpty()) {
                group.shortcuts
            } else if (appMatches) {
                group.shortcuts
            } else {
                group.shortcuts.filter { shortcut ->
                    shortcut.label.lowercase().contains(query) ||
                        (shortcut.shortcutId?.lowercase()?.contains(query) == true)
                }
            }
            if (matchedShortcuts.isEmpty()) null else group.copy(shortcuts = matchedShortcuts)
        }
    }

    Column(modifier = listModifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .selectableGroup(),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            if (loading) {
                item(key = "loading") {
                    ShortcutScanProgressContent(
                        progress = scanProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (filteredCreateHosts.isEmpty() && filteredGroups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.shortcut_kind_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                if (filteredCreateHosts.isNotEmpty()) {
                    item(key = "header-create") {
                        Md3PickerSectionHeader(stringResource(R.string.create_shortcut))
                    }
                    items(filteredCreateHosts.size, key = { filteredCreateHosts[it].qualifiedName }) { index ->
                        val host = filteredCreateHosts[index]
                        val app = apps.firstOrNull { it.packageName == host.packageName }
                        Md3PickerListRow(
                            segmentIndex = index,
                            segmentCount = filteredCreateHosts.size,
                            title = host.label,
                            subtitle = stringResource(R.string.create_shortcut_tap_hint),
                            selected = false,
                            onClick = {
                                pendingCreateHost = host
                                runCatching { createLauncher.launch(host.createIntent()) }
                                    .onFailure { pendingCreateHost = null }
                            },
                            leadingContent = {
                                if (app != null) {
                                    Md3PickerAppLeading(app)
                                } else {
                                    Md3PickerIconLeading(
                                        icon = Icons.AutoMirrored.Filled.Shortcut,
                                        selected = false,
                                    )
                                }
                            },
                            trailingMode = PickerTrailingMode.Icon,
                            trailingIcon = Icons.AutoMirrored.Filled.Shortcut,
                            trailingIconDescription = stringResource(R.string.create_shortcut),
                        )
                    }
                    item(key = "gap-create") { Spacer(modifier = Modifier.height(PickerListGroupSpacing)) }
                }
                if (filteredGroups.isNotEmpty()) {
                    item(key = "header-launch") {
                        Md3PickerSectionHeader(stringResource(R.string.launch_shortcut))
                    }
                    filteredGroups.forEach { group ->
                        item(key = "header-${group.app.packageName}") {
                            Md3PickerListRow(
                                segmentIndex = 0,
                                segmentCount = 1,
                                title = group.app.label,
                                subtitle = group.app.packageName,
                                selected = false,
                                onClick = null,
                                enabled = false,
                                leadingContent = { Md3PickerAppLeading(group.app) },
                            )
                        }
                        items(
                            count = group.shortcuts.size,
                            key = { idx ->
                                val shortcut = group.shortcuts[idx]
                                "${group.app.packageName}:${shortcut.shortcutId ?: shortcut.label}"
                            },
                        ) { index ->
                            val shortcut = group.shortcuts[index]
                            ActionPickerShortcutRow(
                                shortcut = shortcut,
                                packageName = group.app.packageName,
                                segmentIndex = index,
                                segmentCount = group.shortcuts.size,
                                current = current,
                                onSelect = onSelect,
                            )
                        }
                        item(key = "gap-${group.app.packageName}") {
                            Spacer(modifier = Modifier.height(PickerListGroupSpacing))
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ActionPickerShortcutRow(
    shortcut: TaskSwitcherMenuItem,
    packageName: String,
    segmentIndex: Int,
    segmentCount: Int,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
) {
    val shortcutId = shortcut.shortcutId ?: shortcut.label
    val action = when {
        !shortcut.intentUris.isNullOrEmpty() && shortcut.intentUris.size == 1 ->
            GestureAction.LaunchShortcut.intent(shortcut.intentUris[0], shortcut.label)
        !shortcut.intentUris.isNullOrEmpty() ->
            GestureAction.LaunchShortcut.intents(shortcut.intentUris, shortcut.label)
        else ->
            GestureAction.LaunchShortcut.dynamic(
                packageName = packageName,
                shortcutId = shortcutId,
                label = shortcut.label,
            )
    }
    val selected = current is GestureAction.LaunchShortcut &&
        current.payloadKey == action.payloadKey
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = when {
            selected -> stringResource(R.string.action_picker_selected)
            !shortcut.targetComponent.isNullOrBlank() -> shortcut.targetComponent
            else -> null
        },
        selected = selected,
        onClick = { onSelect(action) },
        leadingContent = {
            Md3PickerIconLeading(
                icon = Icons.AutoMirrored.Filled.Shortcut,
                selected = selected,
            )
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

internal fun filterGestureActions(
    context: Context,
    actions: List<GestureAction>,
    query: String,
): List<GestureAction> {
    val sorted = actions.sortedBy { gestureActionSortKey(context, it) }
    val q = query.trim().lowercase()
    if (q.isEmpty()) return sorted
    return sorted.filter { action ->
        val label = gestureActionLabelText(context, action)
        label.lowercase().contains(q) ||
            PinyinHelper.sortKey(label).contains(q) ||
            gestureActionDescriptionText(context, action)?.lowercase()?.contains(q) == true
    }
}

private fun gestureActionDescriptionText(context: Context, action: GestureAction): String? =
    when (action.type) {
        GestureActionType.ADJUST_VOLUME -> context.getString(R.string.gesture_action_adjust_volume_desc)
        GestureActionType.ADJUST_BRIGHTNESS -> context.getString(R.string.gesture_action_adjust_brightness_desc)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top_desc)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom_desc)
        else -> null
    }

internal fun gestureActionLabelText(context: Context, action: GestureAction): String = when (action) {
    is GestureAction.LaunchApp -> {
        if (action.packageName.isBlank()) {
            context.getString(R.string.gesture_action_launch_app)
        } else {
            context.getString(R.string.gesture_action_launch_app_named, action.packageName)
        }
    }
    is GestureAction.LaunchShortcut -> {
        val shortcutLabel = action.label.ifBlank {
            GestureShortcutPayload.decode(action.payloadKey)?.label.orEmpty()
        }
        if (shortcutLabel.isBlank()) {
            context.getString(R.string.gesture_action_launch_shortcut)
        } else {
            context.getString(R.string.gesture_action_launch_shortcut_named, shortcutLabel)
        }
    }
    else -> when (action.type) {
        GestureActionType.NONE -> context.getString(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> context.getString(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> context.getString(R.string.gesture_action_quick_launcher)
        GestureActionType.TASK_SWITCHER -> context.getString(R.string.gesture_action_task_switcher)
        GestureActionType.BACK -> context.getString(R.string.gesture_action_back)
        GestureActionType.HOME -> context.getString(R.string.gesture_action_home)
        GestureActionType.RECENTS -> context.getString(R.string.gesture_action_recents)
        GestureActionType.CLOSE_CURRENT_APP -> context.getString(R.string.gesture_action_close_current_app)
        GestureActionType.FREE_WINDOW_CURRENT_APP -> context.getString(R.string.gesture_action_free_window_current_app)
        GestureActionType.CLICK_PASSTHROUGH -> context.getString(R.string.gesture_action_click_passthrough)
        GestureActionType.FLASHLIGHT -> context.getString(R.string.gesture_action_flashlight)
        GestureActionType.ADJUST_VOLUME -> context.getString(R.string.gesture_action_adjust_volume)
        GestureActionType.ADJUST_BRIGHTNESS -> context.getString(R.string.gesture_action_adjust_brightness)
        GestureActionType.LAUNCH_ASSISTANT -> context.getString(R.string.gesture_action_launch_assistant)
        GestureActionType.TOGGLE_MUTE -> context.getString(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> context.getString(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> context.getString(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> context.getString(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> context.getString(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> context.getString(R.string.gesture_action_open_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> context.getString(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> context.getString(R.string.gesture_action_lock_screen)
        GestureActionType.SCREENSHOT -> context.getString(R.string.gesture_action_screenshot)
        GestureActionType.POWER_MENU -> context.getString(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> context.getString(R.string.gesture_action_keep_screen_on)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> context.getString(R.string.gesture_action_shell_command_panel)
        GestureActionType.QUICK_TOOLS_OVERLAY -> context.getString(R.string.gesture_action_quick_tools_overlay)
        GestureActionType.WIDGET_POPUP_OVERLAY -> context.getString(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.FLOATING_POINTER -> context.getString(R.string.gesture_action_floating_pointer)
        GestureActionType.SIMULATE_POINTER_SWIPE -> context.getString(R.string.gesture_action_pointer_swipe)
        GestureActionType.TOGGLE_DND -> context.getString(R.string.gesture_action_toggle_dnd)
        GestureActionType.SCREEN_RECORD -> context.getString(R.string.gesture_action_screen_record)
        GestureActionType.TOGGLE_WIFI -> context.getString(R.string.gesture_action_toggle_wifi)
        GestureActionType.TOGGLE_MOBILE_DATA -> context.getString(R.string.gesture_action_toggle_mobile_data)
        GestureActionType.SWITCH_INPUT_METHOD -> context.getString(R.string.gesture_action_switch_input_method)
        GestureActionType.LAUNCH_APP -> context.getString(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> context.getString(R.string.gesture_action_launch_shortcut)
    }
}

internal fun gestureActionSortKey(context: Context, action: GestureAction): String =
    PinyinHelper.sortKey(gestureActionLabelText(context, action))

@Composable
fun gestureActionLabel(action: GestureAction): String = when (action) {
    is GestureAction.LaunchApp -> {
        if (action.packageName.isBlank()) {
            stringResource(R.string.gesture_action_launch_app)
        } else {
            stringResource(R.string.gesture_action_launch_app_named, action.packageName)
        }
    }
    is GestureAction.LaunchShortcut -> {
        val label = action.label.ifBlank {
            GestureShortcutPayload.decode(action.payloadKey)?.label.orEmpty()
        }
        if (label.isBlank()) {
            stringResource(R.string.gesture_action_launch_shortcut)
        } else {
            stringResource(R.string.gesture_action_launch_shortcut_named, label)
        }
    }
    is GestureAction.SimulatePointerSwipe -> stringResource(R.string.gesture_action_pointer_swipe)
    else -> when (action.type) {
        GestureActionType.NONE -> stringResource(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> stringResource(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> stringResource(R.string.gesture_action_quick_launcher)
        GestureActionType.TASK_SWITCHER -> stringResource(R.string.gesture_action_task_switcher)
        GestureActionType.BACK -> stringResource(R.string.gesture_action_back)
        GestureActionType.HOME -> stringResource(R.string.gesture_action_home)
        GestureActionType.RECENTS -> stringResource(R.string.gesture_action_recents)
        GestureActionType.CLOSE_CURRENT_APP -> stringResource(R.string.gesture_action_close_current_app)
        GestureActionType.FREE_WINDOW_CURRENT_APP -> stringResource(R.string.gesture_action_free_window_current_app)
        GestureActionType.CLICK_PASSTHROUGH -> stringResource(R.string.gesture_action_click_passthrough)
        GestureActionType.FLASHLIGHT -> stringResource(R.string.gesture_action_flashlight)
        GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume)
        GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness)
        GestureActionType.LAUNCH_ASSISTANT -> stringResource(R.string.gesture_action_launch_assistant)
        GestureActionType.TOGGLE_MUTE -> stringResource(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> stringResource(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> stringResource(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> stringResource(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> stringResource(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> stringResource(R.string.gesture_action_open_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> stringResource(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> stringResource(R.string.gesture_action_lock_screen)
        GestureActionType.SCREENSHOT -> stringResource(R.string.gesture_action_screenshot)
        GestureActionType.POWER_MENU -> stringResource(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> stringResource(R.string.gesture_action_keep_screen_on)
        GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> stringResource(R.string.gesture_action_shell_command_panel)
        GestureActionType.QUICK_TOOLS_OVERLAY -> stringResource(R.string.gesture_action_quick_tools_overlay)
        GestureActionType.WIDGET_POPUP_OVERLAY -> stringResource(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.FLOATING_POINTER -> stringResource(R.string.gesture_action_floating_pointer)
        GestureActionType.SIMULATE_POINTER_SWIPE -> stringResource(R.string.gesture_action_pointer_swipe)
        GestureActionType.TOGGLE_DND -> stringResource(R.string.gesture_action_toggle_dnd)
        GestureActionType.SCREEN_RECORD -> stringResource(R.string.gesture_action_screen_record)
        GestureActionType.TOGGLE_WIFI -> stringResource(R.string.gesture_action_toggle_wifi)
        GestureActionType.TOGGLE_MOBILE_DATA -> stringResource(R.string.gesture_action_toggle_mobile_data)
        GestureActionType.SWITCH_INPUT_METHOD -> stringResource(R.string.gesture_action_switch_input_method)
        GestureActionType.LAUNCH_APP -> stringResource(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> stringResource(R.string.gesture_action_launch_shortcut)
    }
}

@Composable
fun gestureActionDescription(action: GestureAction): String? = when (action.type) {
    GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume_desc)
    GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness_desc)
    GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top_desc)
    GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom_desc)
    GestureActionType.SIMULATE_POINTER_SWIPE -> stringResource(R.string.gesture_action_pointer_swipe_desc)
    else -> null
}

@Composable
fun gestureActionPermissionHint(action: GestureAction, context: Context): String? =
    when (action.type) {
        GestureActionType.ADJUST_VOLUME -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            stringResource(R.string.gesture_action_adjust_volume_permission)
        }
        GestureActionType.ADJUST_BRIGHTNESS -> {
            if (PermissionHelper.canWriteSettings(context)) return null
            stringResource(R.string.gesture_action_adjust_brightness_permission)
        }
        GestureActionType.TOGGLE_MUTE -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            stringResource(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TOGGLE_DND -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            stringResource(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TOGGLE_WIFI, GestureActionType.TOGGLE_MOBILE_DATA -> {
            if (TaskManagerUtil.hasPermission()) return null
            stringResource(R.string.gesture_action_toggle_shell_permission)
        }
        GestureActionType.SCREEN_RECORD -> {
            if (PermissionHelper.canDrawOverlays(context)) return null
            stringResource(R.string.gesture_action_screen_record_permission)
        }
        GestureActionType.LOCK_SCREEN, GestureActionType.SCREENSHOT -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) return null
            stringResource(R.string.gesture_action_requires_android_p)
        }
        GestureActionType.SCROLL_TO_TOP, GestureActionType.SCROLL_TO_BOTTOM -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) return null
            stringResource(R.string.gesture_action_requires_android_n)
        }
        GestureActionType.QUICK_TOOLS_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_quick_tools_overlay_permission)
        }
        GestureActionType.WIDGET_POPUP_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_widget_popup_overlay_permission)
        }
        GestureActionType.FLOATING_POINTER -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_floating_pointer_permission)
        }
        GestureActionType.SIMULATE_POINTER_SWIPE -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_pointer_swipe_permission)
        }
        else -> null
    }

internal fun requestPermissionForAdjustAction(context: Context, action: GestureAction) {
    when (action) {
        GestureAction.AdjustVolume, GestureAction.ToggleMute, GestureAction.ToggleDnd ->
            PermissionHelper.requestNotificationPolicyAccess(context)
        GestureAction.AdjustBrightness -> PermissionHelper.requestWriteSettingsAccess(context)
        GestureAction.QuickToolsOverlay -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.WidgetPopupOverlay -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.FloatingPointer -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        is GestureAction.SimulatePointerSwipe -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ScreenRecord -> {
            if (!PermissionHelper.canDrawOverlays(context)) {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            }
        }
        else -> Unit
    }
}
