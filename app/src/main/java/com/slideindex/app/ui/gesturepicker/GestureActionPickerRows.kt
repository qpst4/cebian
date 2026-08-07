package com.slideindex.app.ui.gesturepicker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.runtime.Composable
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gestureActionIcon

internal enum class ActionPickerTab {
    ACTIONS,
    APPS,
    SHORTCUTS,
}

@Composable
internal fun ActionPickerActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = gestureActionDescription(action),
        selected = selected,
        onClick = onClick,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = selected,
            )
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

@Composable
internal fun ActionPickerAppRow(
    app: AppInfo,
    segmentIndex: Int,
    segmentCount: Int,
    selected: Boolean,
    onSelect: (AppInfo) -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = app.label,
        subtitle = app.packageName,
        selected = selected,
        onClick = { onSelect(app) },
        leadingContent = { Md3PickerAppLeading(app) },
        trailingMode = PickerTrailingMode.Radio,
    )
}

@Composable
internal fun ActionPickerShortcutRow(
    shortcut: TaskSwitcherMenuItem,
    packageName: String,
    segmentIndex: Int,
    segmentCount: Int,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
) {
    val action = shortcutToLaunchShortcut(shortcut, packageName)
    val selected = current is GestureAction.LaunchShortcut && current.payloadKey == action.payloadKey
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = selected,
        onClick = { onSelect(action) },
        leadingContent = {
            Md3PickerIconLeading(
                icon = Icons.AutoMirrored.Outlined.Shortcut,
                selected = selected,
            )
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

private fun shortcutToLaunchShortcut(
    shortcut: TaskSwitcherMenuItem,
    packageName: String,
): GestureAction.LaunchShortcut {
    val uris = shortcut.intentUris
    if (!uris.isNullOrEmpty()) {
        return if (uris.size == 1) {
            GestureAction.LaunchShortcut.intent(uris[0], shortcut.label)
        } else {
            GestureAction.LaunchShortcut.intents(uris, shortcut.label)
        }
    }
    val component = shortcut.targetComponent?.takeIf { it.isNotBlank() }
    if (component != null) {
        return GestureAction.LaunchShortcut.component(component, shortcut.label)
    }
    val shortcutId = shortcut.shortcutId?.takeIf { it.isNotBlank() } ?: shortcut.label
    return GestureAction.LaunchShortcut.dynamic(packageName, shortcutId, shortcut.label)
}
