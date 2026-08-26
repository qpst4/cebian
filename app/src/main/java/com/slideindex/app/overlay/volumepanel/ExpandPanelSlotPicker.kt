package com.slideindex.app.overlay.volumepanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gesturepicker.ActionPickerActionRow
import com.slideindex.app.ui.gesturepicker.ActionPickerAppRow
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredActions
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredApps
import com.slideindex.app.ui.miuix.MiuixSearchField
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.taskSwitcherItemToLaunchShortcut
import com.slideindex.app.util.AppShortcutLoader
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
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

@Composable
fun ExpandPanelSlotPicker(
    allApps: List<AppInfo>,
    activityShortcuts: List<ActivityShortcut>,
    current: GestureAction?,
    onSelect: (GestureAction) -> Unit,
    onCancel: () -> Unit,
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
    val loadedCatalog = rememberLoadedShortcutCatalog(apps = allApps, enabled = selectedTab == ExpandPanelPickerTab.SHORTCUTS.ordinal)
    val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val shortcutRows = remember(activityShortcuts, searchQuery, filteredShortcuts) {
        val query = searchQuery.trim().lowercase()
        val managed = if (query.isEmpty()) {
            activityShortcuts
        } else {
            activityShortcuts.filter { shortcut ->
                shortcut.label.lowercase().contains(query) ||
                    shortcut.packageName.lowercase().contains(query)
            }
        }
        buildList {
            managed.forEach { add(ExpandPanelShortcutRow.Managed(it)) }
            filteredShortcuts.groups.forEach { group ->
                group.shortcuts.forEach { shortcut ->
                    add(ExpandPanelShortcutRow.System(group.app, shortcut))
                }
            }
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
                .height(320.dp)
                .padding(top = 4.dp),
        ) {
            when (ExpandPanelPickerTab.entries[selectedTab]) {
                ExpandPanelPickerTab.ACTIONS -> {
                    if (filteredActions.isEmpty()) {
                        item(key = "actions-empty") {
                            Text(
                                text = stringResource(R.string.search_no_actions),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    } else {
                        items(filteredActions.size, key = { index ->
                            "action-${filteredActions[index].type.id}-${filteredActions[index].payload}"
                        }) { index ->
                            val action = filteredActions[index]
                            ActionPickerActionRow(
                                action = action,
                                segmentIndex = index,
                                segmentCount = filteredActions.size,
                                selected = currentAction.type == action.type &&
                                    currentAction.type != com.slideindex.app.gesture.GestureActionType.LAUNCH_APP &&
                                    currentAction.type != com.slideindex.app.gesture.GestureActionType.LAUNCH_SHORTCUT,
                                onClick = { onSelect(action) },
                            )
                        }
                    }
                }
                ExpandPanelPickerTab.APPS -> {
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
                ExpandPanelPickerTab.SHORTCUTS -> {
                    if (shortcutRows.isEmpty()) {
                        item(key = "shortcuts-empty") {
                            Text(
                                text = stringResource(R.string.search_no_actions),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    } else {
                        items(shortcutRows.size, key = { index ->
                            when (val row = shortcutRows[index]) {
                                is ExpandPanelShortcutRow.Managed -> "managed-${row.shortcut.id}"
                                is ExpandPanelShortcutRow.System -> "system-${row.app.packageName}-${row.shortcut.label}-${row.shortcut.targetComponent}"
                            }
                        }) { index ->
                            when (val row = shortcutRows[index]) {
                                is ExpandPanelShortcutRow.Managed -> {
                                    val app = allApps.firstOrNull { it.packageName == row.shortcut.packageName }
                                    Md3PickerListRow(
                                        segmentIndex = index,
                                        segmentCount = shortcutRows.size,
                                        title = row.shortcut.label,
                                        subtitle = row.shortcut.packageName,
                                        selected = false,
                                        onClick = { onSelect(row.shortcut.toLaunchShortcut()) },
                                        leadingContent = {
                                            if (app != null) {
                                                Md3PickerAppLeading(app)
                                            } else {
                                                com.slideindex.app.ui.Md3PickerIconLeading(
                                                    icon = com.slideindex.app.ui.gestureActionIcon(
                                                        row.shortcut.toLaunchShortcut(),
                                                    ),
                                                    selected = false,
                                                )
                                            }
                                        },
                                    )
                                }
                                is ExpandPanelShortcutRow.System -> {
                                    val action = taskSwitcherItemToLaunchShortcut(row.shortcut, row.app.packageName)
                                    Md3PickerListRow(
                                        segmentIndex = index,
                                        segmentCount = shortcutRows.size,
                                        title = row.shortcut.label,
                                        subtitle = row.app.label,
                                        selected = false,
                                        onClick = { onSelect(action) },
                                        leadingContent = { Md3PickerAppLeading(row.app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
