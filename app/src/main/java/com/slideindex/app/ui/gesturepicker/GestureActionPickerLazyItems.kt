package com.slideindex.app.ui.gesturepicker

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
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.ui.picker.FilteredShortcutCatalog
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.picker.activityShortcutPickerRadioSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.ShortcutScanProgress

@Composable
fun rememberActionPickerFilteredActions(
    trigger: GestureTriggerType,
    searchQuery: String,
    includePointerGestureActions: Boolean,
    includeCornerInnerZoneActions: Boolean,
    pinNoneAtTop: Boolean,
): List<GestureAction> {
    val context = LocalContext.current
    val actionOptions = remember(trigger, includePointerGestureActions, includeCornerInnerZoneActions) {
        GestureActionCatalog.build(
            scope = GestureActionCatalogScope.GesturePicker,
            trigger = trigger,
            includePointerGestureActions = includePointerGestureActions,
            includeCornerInnerZoneActions = includeCornerInnerZoneActions,
        )
    }
    return remember(
        actionOptions,
        searchQuery,
        context,
        includeCornerInnerZoneActions,
        pinNoneAtTop,
    ) {
        GestureActionCatalog.filter(
            context = context,
            actions = actionOptions,
            query = searchQuery,
            pinNoneAtTop = pinNoneAtTop,
            includeCornerInnerZoneActions = includeCornerInnerZoneActions,
        )
    }
}

@Composable
fun rememberActionPickerFilteredApps(
    apps: List<AppInfo>,
    searchQuery: String,
): List<AppInfo> {
    val query = searchQuery.trim().lowercase()
    return remember(apps, query) {
        apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                app.pinyinKey.contains(query)
        }.sortedBy { it.pinyinKey }
    }
}

fun LazyListScope.actionPickerActionItems(
    filtered: List<GestureAction>,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
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
            val context = LocalContext.current
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

fun LazyListScope.actionPickerAppItems(
    filtered: List<AppInfo>,
    current: GestureAction,
    onSelect: (AppInfo) -> Unit,
) {
    items(filtered.size, key = { filtered[it].packageName }) { index ->
        val app = filtered[index]
        val selected = current is GestureAction.LaunchApp && current.packageName == app.packageName
        ActionPickerAppRow(
            app = app,
            segmentIndex = index,
            segmentCount = filtered.size,
            selected = selected,
            onSelect = onSelect,
        )
    }
}

fun LazyListScope.actionPickerShortcutItems(
    appsByPackage: Map<String, AppInfo>,
    searchQuery: String,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    activityShortcuts: List<ActivityShortcut>,
    onBrowseActivityShortcut: () -> Unit,
    filtered: FilteredShortcutCatalog,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    onCreateHostClick: (AppShortcutLoader.CreateShortcutHost) -> Unit,
) {
    if (searchQuery.isBlank() || activityShortcuts.isNotEmpty()) {
        activityShortcutPickerRadioSection(
            activityShortcuts = activityShortcuts,
            current = current,
            onSelect = onSelect,
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
