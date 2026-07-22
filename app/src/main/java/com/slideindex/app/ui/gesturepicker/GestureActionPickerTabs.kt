package com.slideindex.app.ui.gesturepicker

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.launchShortcutFromCreated
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.Md3PickerSupportingHints
import com.slideindex.app.ui.PickerListGroupSpacing
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerSearchListHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.ShortcutScanProgressContent
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanProgress

internal enum class ActionPickerTab { ACTIONS, APPS, SHORTCUTS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ActionPickerActionsTab(
    trigger: GestureTriggerType,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier,
    includePointerGestureActions: Boolean = false,
) {
    val context = LocalContext.current
    val actionOptions = remember(trigger, includePointerGestureActions) {
        buildList {
            add(GestureAction.None)
            add(GestureAction.OpenIndex)
            add(GestureAction.QuickLauncher)
            add(GestureAction.TaskSwitcher)
            add(GestureAction.ShellCommandPanel)
            add(GestureAction.ExecuteShellCommand())
            add(GestureAction.QuickToolsOverlay)
            add(GestureAction.WidgetPopupOverlay)
            add(GestureAction.StashPanel)
            add(GestureAction.ClipboardPanel)
            add(GestureAction.FloatingPointer)
            add(GestureAction.SimulatePointerSwipe())
            if (includePointerGestureActions) {
                add(GestureAction.PointerGestureRecorder)
                add(GestureAction.PointerRealtimeGesture)
                add(GestureAction.OpenFloatingPointerRadialMenu)
            }
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
            add(GestureAction.LockScreenAndSilenceRing)
            add(GestureAction.LockScreenAndMuteAll)
            add(GestureAction.Screenshot)
            add(GestureAction.FullscreenScreenshotPick)
            add(GestureAction.SearchPanel)
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
    Column(modifier = modifier) {
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
                        selected = when {
                            action.type == GestureActionType.EXECUTE_SHELL_COMMAND &&
                                current.type == GestureActionType.EXECUTE_SHELL_COMMAND -> true
                            action.type == current.type &&
                                action.type != GestureActionType.LAUNCH_APP &&
                                action.type != GestureActionType.LAUNCH_SHORTCUT -> true
                            else -> false
                        },
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
    val enabled = isGestureActionEnabledOnDevice(action)
    val description = gestureActionDescription(action)
    val ancillaryHint = listOfNotNull(
        gestureActionPermissionHint(action, context),
        gestureActionRequirementHint(action),
    ).joinToString("\n").takeIf { it.isNotBlank() }
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = gestureActionLabel(action),
        subtitle = null,
        selected = selected,
        enabled = enabled,
        onClick = {
            if (enabled) onClick()
        },
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action),
                selected = selected,
            )
        },
        supportingContent = if (description != null || ancillaryHint != null) {
            { Md3PickerSupportingHints(description, ancillaryHint) }
        } else {
            null
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ActionPickerAppsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    current: GestureAction,
    onSelect: (AppInfo) -> Unit,
    modifier: Modifier,
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
    Column(modifier = modifier) {
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
internal fun ActionPickerShortcutsTab(
    apps: List<AppInfo>,
    catalog: AppShortcutLoader.ShortcutCatalog?,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    modifier: Modifier,
) {
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

    Column(modifier = modifier) {
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
