package com.slideindex.app.overlay.volumepanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.subtitleDetail
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerAppShortcutLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerManagedShortcutLeading
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gesturepicker.ActionPickerAppRow
import com.slideindex.app.ui.gesturepicker.actionPickerActionItems
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredActions
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredApps
import com.slideindex.app.ui.miuix.MiuixSearchField
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.taskSwitcherItemToLaunchShortcut
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ExpandPanelPickerTab {
    ACTIONS,
    APPS,
    SHORTCUTS,
}

private sealed class ExpandPanelShortcutRow {
    data class Managed(val shortcut: ActivityShortcut) : ExpandPanelShortcutRow()
    data class System(val app: AppInfo, val shortcut: TaskSwitcherMenuItem) : ExpandPanelShortcutRow()
}

/** 与列表行 [com.slideindex.app.ui.Md3PickerIconLeading] 一致的槽位选择器可视高度。 */
internal val ExpandPanelPickerListHeight = 440.dp

@Composable
fun ExpandPanelSlotPicker(
    allApps: List<AppInfo>,
    activityShortcuts: List<ActivityShortcut>,
    current: GestureAction?,
    onSelect: (GestureAction) -> Unit,
    onCancel: () -> Unit,
    editingSubtitle: String? = null,
    onClearSlot: (() -> Unit)? = null,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val currentAction = current ?: GestureAction.None

    val filteredActions = rememberActionPickerFilteredActions(
        trigger = GestureTriggerType.SHORT_SINGLE_TAP,
        searchQuery = searchQuery,
        includePointerGestureActions = false,
        includeCornerInnerZoneActions = false,
        pinNoneAtTop = false,
    )
    val filteredApps = rememberActionPickerFilteredApps(allApps, searchQuery)
    val loadedCatalog = rememberLoadedShortcutCatalog(
        apps = allApps,
        enabled = selectedTab == ExpandPanelPickerTab.SHORTCUTS.ordinal,
    )
    val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val managedShortcuts = remember(activityShortcuts, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            activityShortcuts
        } else {
            activityShortcuts.filter { shortcut ->
                shortcut.label.lowercase().contains(query) ||
                    shortcut.packageName.lowercase().contains(query)
            }
        }
    }
    val systemShortcutRows = remember(filteredShortcuts) {
        buildList {
            filteredShortcuts.groups.forEach { group ->
                group.shortcuts.forEach { shortcut ->
                    add(ExpandPanelShortcutRow.System(group.app, shortcut))
                }
            }
        }
    }
    val flatShortcutRows = remember(managedShortcuts, systemShortcutRows) {
        buildList {
            managedShortcuts.forEach { add(ExpandPanelShortcutRow.Managed(it)) }
            addAll(systemShortcutRows)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.expand_panel_shortcut_pick),
                style = MiuixTheme.textStyles.title4,
            )
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
            )
        }
        if (!editingSubtitle.isNullOrBlank()) {
            Text(
                text = editingSubtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        MiuixSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            hintResId = R.string.search_hint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        MiuixTabRowWithContour(
            tabs = listOf(
                stringResource(R.string.action_picker_tab_actions),
                stringResource(R.string.action_picker_tab_apps),
                stringResource(R.string.action_picker_tab_shortcuts),
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            contourHost = MiuixTabRowContourHost.SurfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(ExpandPanelPickerListHeight)
                .padding(top = 4.dp),
        ) {
            when (ExpandPanelPickerTab.entries[selectedTab]) {
                ExpandPanelPickerTab.ACTIONS -> {
                    actionPickerActionItems(
                        filtered = filteredActions,
                        current = currentAction,
                        onSelect = onSelect,
                        onOpenExecuteShellCommand = { onSelect(GestureAction.ExecuteShellCommand()) },
                    )
                }
                ExpandPanelPickerTab.APPS -> {
                    if (filteredApps.isEmpty()) {
                        item(key = "apps-empty") {
                            Text(
                                text = stringResource(R.string.search_no_actions),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    } else {
                        items(filteredApps.size, key = { filteredApps[it].packageName }) { index ->
                            val app = filteredApps[index]
                            ActionPickerAppRow(
                                app = app,
                                segmentIndex = index,
                                segmentCount = filteredApps.size,
                                selected = currentAction is GestureAction.LaunchApp &&
                                    currentAction.packageName == app.packageName,
                                onSelect = { onSelect(GestureAction.LaunchApp(it.packageName)) },
                            )
                        }
                    }
                }
                ExpandPanelPickerTab.SHORTCUTS -> {
                    val showSections = searchQuery.isBlank()
                    if (flatShortcutRows.isEmpty()) {
                        item(key = "shortcuts-empty") {
                            Text(
                                text = stringResource(R.string.search_no_actions),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    } else if (showSections) {
                        if (managedShortcuts.isNotEmpty()) {
                            item(key = "shortcuts-managed-header") {
                                Md3PickerSectionHeader(stringResource(R.string.activity_shortcut_picker_section))
                            }
                            items(managedShortcuts.size, key = { managedShortcuts[it].id }) { index ->
                                val shortcut = managedShortcuts[index]
                                val action = shortcut.toLaunchShortcut()
                                val selected = currentAction is GestureAction.LaunchShortcut &&
                                    currentAction.payloadKey == action.payloadKey
                                Md3PickerListRow(
                                    segmentIndex = index,
                                    segmentCount = managedShortcuts.size,
                                    title = shortcut.label,
                                    subtitle = shortcut.subtitleDetail(),
                                    selected = selected,
                                    onClick = { onSelect(action) },
                                    leadingContent = {
                                        Md3PickerManagedShortcutLeading(
                                            shortcut = shortcut,
                                            selected = selected,
                                        )
                                    },
                                    trailingMode = PickerTrailingMode.Radio,
                                )
                            }
                        }
                        if (systemShortcutRows.isNotEmpty()) {
                            item(key = "shortcuts-system-header") {
                                Md3PickerSectionHeader(stringResource(R.string.launch_shortcut))
                            }
                            items(systemShortcutRows.size, key = { index ->
                                val row = systemShortcutRows[index]
                                "system-${row.app.packageName}-${row.shortcut.label}-${row.shortcut.targetComponent}"
                            }) { index ->
                                val row = systemShortcutRows[index]
                                val action = taskSwitcherItemToLaunchShortcut(row.shortcut, row.app.packageName)
                                val selected = currentAction is GestureAction.LaunchShortcut &&
                                    currentAction.payloadKey == action.payloadKey
                                Md3PickerListRow(
                                    segmentIndex = index,
                                    segmentCount = systemShortcutRows.size,
                                    title = row.shortcut.label,
                                    subtitle = row.app.label,
                                    selected = selected,
                                    onClick = { onSelect(action) },
                                    leadingContent = {
                                        Md3PickerAppShortcutLeading(
                                            packageName = row.app.packageName,
                                            contentDescription = row.shortcut.label,
                                            selected = selected,
                                        )
                                    },
                                    trailingMode = PickerTrailingMode.Radio,
                                )
                            }
                        }
                    } else {
                        items(flatShortcutRows.size, key = { index ->
                            when (val row = flatShortcutRows[index]) {
                                is ExpandPanelShortcutRow.Managed -> "managed-${row.shortcut.id}"
                                is ExpandPanelShortcutRow.System ->
                                    "system-${row.app.packageName}-${row.shortcut.label}-${row.shortcut.targetComponent}"
                            }
                        }) { index ->
                            when (val row = flatShortcutRows[index]) {
                                is ExpandPanelShortcutRow.Managed -> {
                                    val shortcut = row.shortcut
                                    val action = shortcut.toLaunchShortcut()
                                    val selected = currentAction is GestureAction.LaunchShortcut &&
                                        currentAction.payloadKey == action.payloadKey
                                    Md3PickerListRow(
                                        segmentIndex = index,
                                        segmentCount = flatShortcutRows.size,
                                        title = shortcut.label,
                                        subtitle = shortcut.subtitleDetail(),
                                        selected = selected,
                                        onClick = { onSelect(action) },
                                        leadingContent = {
                                            Md3PickerManagedShortcutLeading(
                                                shortcut = shortcut,
                                                selected = selected,
                                            )
                                        },
                                        trailingMode = PickerTrailingMode.Radio,
                                    )
                                }
                                is ExpandPanelShortcutRow.System -> {
                                    val action = taskSwitcherItemToLaunchShortcut(row.shortcut, row.app.packageName)
                                    val selected = currentAction is GestureAction.LaunchShortcut &&
                                        currentAction.payloadKey == action.payloadKey
                                    Md3PickerListRow(
                                        segmentIndex = index,
                                        segmentCount = flatShortcutRows.size,
                                        title = row.shortcut.label,
                                        subtitle = row.app.label,
                                        selected = selected,
                                        onClick = { onSelect(action) },
                                        leadingContent = {
                                            Md3PickerAppShortcutLeading(
                                                packageName = row.app.packageName,
                                                contentDescription = row.shortcut.label,
                                                selected = selected,
                                            )
                                        },
                                        trailingMode = PickerTrailingMode.Radio,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (onClearSlot != null) {
            TextButton(
                text = stringResource(R.string.expand_panel_clear_slot),
                onClick = onClearSlot,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
