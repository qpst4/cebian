package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.AppPackageEntry
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.gesturepicker.gestureActionDescription
import com.slideindex.app.ui.gesturepicker.gestureActionLabel
import com.slideindex.app.ui.picker.FilteredShortcutCatalog
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.picker.activityShortcutPickerToggleSection
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanProgress

fun LazyListScope.quickLauncherAddPickerActionItems(
    filtered: List<GestureAction>,
    configuredActionKeys: Set<String>,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onOpenExecuteShellCommand: () -> Unit,
    singleSelect: Boolean = false,
    pinNoneAtTop: Boolean = false,
    noneSelected: Boolean = false,
    onSelectNone: () -> Unit = {},
    searchQuery: String = "",
) {
    if (pinNoneAtTop) {
        item(key = "ql-action-none") {
            val noneLabel = stringResource(R.string.gesture_action_none)
            val showNone = searchQuery.isBlank() || noneLabel.contains(searchQuery, ignoreCase = true)
            if (showNone) {
                com.slideindex.app.ui.Md3PickerListRow(
                    segmentIndex = 0,
                    segmentCount = 1,
                    title = noneLabel,
                    subtitle = null,
                    selected = noneSelected,
                    onClick = onSelectNone,
                    leadingContent = {
                        com.slideindex.app.ui.Md3PickerIconLeading(
                            icon = gestureActionIcon(GestureAction.None, outlined = true),
                            selected = noneSelected,
                        )
                    },
                    trailingMode = com.slideindex.app.ui.PickerTrailingMode.Radio,
                )
            }
        }
    }

    if (filtered.isEmpty()) {
        if (!pinNoneAtTop || !searchQuery.isBlank()) {
            item(key = "actions_empty") {
                Text(
                    text = stringResource(R.string.search_no_actions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                )
            }
        }
        return
    }

    val sections = GestureActionCatalog.groupIntoSections(filtered)
    sections.forEach { section ->
        item(key = "ql-action-cat-${section.category.name}") {
            com.slideindex.app.ui.Md3PickerSectionHeader(stringResource(section.category.titleRes))
        }
        items(
            count = section.actions.size,
            key = { index -> "ql-action-${section.category.name}-${section.actions[index].type.id}" },
        ) { index ->
            val action = section.actions[index]
            val context = LocalContext.current
            val label = gestureActionLabel(action)
            if (action.type == GestureActionType.EXECUTE_SHELL_COMMAND) {
                QuickLauncherShellCommandActionRow(
                    action = action,
                    segmentIndex = index,
                    segmentCount = section.actions.size,
                    label = label,
                    subtitle = gestureActionDescription(action),
                    onOpenConfig = {
                        requestPermissionForAdjustAction(context, action)
                        onOpenExecuteShellCommand()
                    },
                )
            } else {
                val added = QuickLauncherItemCodec.actionKey(action) in configuredActionKeys
                QuickLauncherActionRow(
                    action = action,
                    segmentIndex = index,
                    segmentCount = section.actions.size,
                    label = label,
                    subtitle = gestureActionDescription(action),
                    added = added,
                    singleSelect = singleSelect,
                    onToggle = {
                        if (!added) {
                            requestPermissionForAdjustAction(context, action)
                        }
                        onToggleItem(QuickLauncherItem.action(action, label), if (singleSelect) false else added)
                    },
                )
            }
        }
    }
}

fun LazyListScope.quickLauncherAddPickerAppItems(
    filtered: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
    singleSelect: Boolean = false,
) {
    items(filtered.size, key = { filtered[it].packageName }) { index ->
        val app = filtered[index]
        val added = app.packageName in configuredAppPackages
        QuickLauncherToggleRow(
            entry = AppPackageEntry.Installed(app),
            segmentIndex = index,
            segmentCount = filtered.size,
            added = added,
            singleSelect = singleSelect,
            onToggle = { onToggle(app, if (singleSelect) false else added) },
        )
    }
}

fun LazyListScope.quickLauncherAddPickerShortcutItems(
    searchQuery: String,
    activityShortcuts: List<ActivityShortcut>,
    configuredShortcutKeys: Set<String>,
    filtered: FilteredShortcutCatalog,
    appsByPackage: Map<String, AppInfo>,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    onCreateHostClick: (AppShortcutLoader.CreateShortcutHost) -> Unit,
    onToggle: (AppInfo, TaskSwitcherMenuItem, Boolean) -> Unit,
    onToggleActivityShortcut: (QuickLauncherItem, Boolean) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    onOpenMyShortcuts: () -> Unit = {},
    onOpenPresetShortcuts: () -> Unit = {},
    singleSelect: Boolean = false,
) {
    if (searchQuery.isBlank()) {
        shortcutFolderCardsSection(
            activityShortcutsCount = activityShortcuts.size,
            onOpenMyShortcuts = onOpenMyShortcuts,
            onOpenPresetShortcuts = onOpenPresetShortcuts,
        )
    } else if (activityShortcuts.isNotEmpty()) {
        activityShortcutPickerToggleSection(
            activityShortcuts = activityShortcuts,
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = onToggleActivityShortcut,
            onBrowse = onBrowseActivityShortcut,
            searchQuery = searchQuery,
            singleSelect = singleSelect,
        )
    }
    systemShortcutCatalogItems(
        filtered = filtered,
        appsByPackage = appsByPackage,
        loading = loading,
        scanProgress = scanProgress,
        onCreateHostClick = onCreateHostClick,
        shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
            val item = shortcut.toQuickLauncherItem(group.app.packageName)
            val added = QuickLauncherItemCodec.shortcutItemKey(item) in configuredShortcutKeys
            ShortcutCatalogRow(
                shortcut = shortcut,
                segmentIndex = segmentIndex,
                segmentCount = segmentCount,
                added = added,
                onToggle = { onToggle(group.app, shortcut, if (singleSelect) false else added) },
            )
        },
    )
}

@Composable
fun rememberQuickLauncherFilteredActions(searchQuery: String): List<GestureAction> {
    val context = LocalContext.current
    val actionOptions = remember {
        GestureActionCatalog.build(scope = GestureActionCatalogScope.QuickLauncher)
    }
    return remember(actionOptions, searchQuery, context) {
        GestureActionCatalog.filter(context, actionOptions, searchQuery)
    }
}

@Composable
fun rememberQuickLauncherFilteredApps(
    apps: List<AppInfo>,
    searchQuery: String,
    enabled: Boolean = true,
): List<AppInfo> {
    val query = searchQuery.trim().lowercase()
    return remember(apps, query, enabled) {
        if (!enabled || apps.isEmpty()) return@remember emptyList()
        val filtered = apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                app.pinyinKey.contains(query)
        }
        filtered.sortedBy { it.pinyinKey }
    }
}
