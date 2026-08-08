package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
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
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gestureActionDescription
import com.slideindex.app.ui.gestureActionLabel
import com.slideindex.app.ui.picker.FilteredShortcutCatalog
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.picker.activityShortcutPickerToggleSection
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
) {
    if (filtered.isEmpty()) {
        item(key = "actions_empty") {
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
            val context = LocalContext.current
            val label = gestureActionLabel(action)
            if (action.type == GestureActionType.EXECUTE_SHELL_COMMAND) {
                QuickLauncherShellCommandActionRow(
                    action = action,
                    segmentIndex = index,
                    segmentCount = filtered.size,
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
                    segmentCount = filtered.size,
                    label = label,
                    subtitle = gestureActionDescription(action),
                    added = added,
                    onToggle = {
                        if (!added) {
                            requestPermissionForAdjustAction(context, action)
                        }
                        onToggleItem(QuickLauncherItem.action(action, label), added)
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
) {
    items(filtered.size, key = { filtered[it].packageName }) { index ->
        val app = filtered[index]
        val added = app.packageName in configuredAppPackages
        QuickLauncherToggleRow(
            entry = AppPackageEntry.Installed(app),
            segmentIndex = index,
            segmentCount = filtered.size,
            added = added,
            onToggle = { onToggle(app, added) },
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
) {
    if (searchQuery.isBlank() || activityShortcuts.isNotEmpty()) {
        activityShortcutPickerToggleSection(
            activityShortcuts = activityShortcuts,
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = onToggleActivityShortcut,
            onBrowse = onBrowseActivityShortcut,
            searchQuery = searchQuery,
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
                onToggle = { onToggle(group.app, shortcut, added) },
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
        val appsWithKey = apps.map { app ->
            app to PinyinHelper.sortKey(app.label)
        }
        val filtered = appsWithKey.filter { (app, pinyin) ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                pinyin.contains(query)
        }
        filtered.sortedBy { (_, pinyin) -> pinyin }.map { it.first }
    }
}
