package com.slideindex.app.ui.quicklauncher

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType

internal const val QUICK_LAUNCHER_SHEET_ENTER_MS = 240
internal const val QUICK_LAUNCHER_SHEET_EXIT_MS = 200

internal fun Modifier.pickerTabPageVisible(visible: Boolean): Modifier =
    then(
        if (visible) {
            Modifier.zIndex(1f)
        } else {
            Modifier.zIndex(0f).graphicsLayer { alpha = 0f }
        },
    )

internal fun addQuickLauncherItem(
    item: QuickLauncherItem,
    addedAppPackages: Set<String>,
    addedShortcutKeys: Set<String>,
    addedActionKeys: Set<String>,
    onAdd: (QuickLauncherItem) -> Unit,
): Triple<Set<String>, Set<String>, Set<String>> {
    onAdd(item)
    return when (item.type) {
        QuickLauncherItemType.APP -> Triple(addedAppPackages + item.payload, addedShortcutKeys, addedActionKeys)
        QuickLauncherItemType.SHORTCUT -> {
            val key = QuickLauncherItemCodec.shortcutItemKey(item)
            Triple(
                addedAppPackages,
                if (key != null) addedShortcutKeys + key else addedShortcutKeys,
                addedActionKeys,
            )
        }
        QuickLauncherItemType.ACTION -> {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload)
            Triple(
                addedAppPackages,
                addedShortcutKeys,
                if (action != null) addedActionKeys + QuickLauncherItemCodec.actionKey(action) else addedActionKeys,
            )
        }
        QuickLauncherItemType.WIDGET -> Triple(addedAppPackages, addedShortcutKeys, addedActionKeys)
    }
}

internal fun removeQuickLauncherItem(
    item: QuickLauncherItem,
    addedAppPackages: Set<String>,
    addedShortcutKeys: Set<String>,
    addedActionKeys: Set<String>,
    onRemove: (QuickLauncherItem) -> Unit,
): Triple<Set<String>, Set<String>, Set<String>> {
    onRemove(item)
    return when (item.type) {
        QuickLauncherItemType.APP -> Triple(addedAppPackages - item.payload, addedShortcutKeys, addedActionKeys)
        QuickLauncherItemType.SHORTCUT -> {
            val key = QuickLauncherItemCodec.shortcutItemKey(item)
            Triple(
                addedAppPackages,
                if (key != null) addedShortcutKeys - key else addedShortcutKeys,
                addedActionKeys,
            )
        }
        QuickLauncherItemType.ACTION -> {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload)
            Triple(
                addedAppPackages,
                addedShortcutKeys,
                if (action != null) addedActionKeys - QuickLauncherItemCodec.actionKey(action) else addedActionKeys,
            )
        }
        QuickLauncherItemType.WIDGET -> Triple(addedAppPackages, addedShortcutKeys, addedActionKeys)
    }
}
