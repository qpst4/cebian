package com.slideindex.app.overlay.appswitcher

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.FvAppSwitcherSettings

fun Map<Int, QuickLauncherItem>.fvAppSwitcherRuntimeItems(): List<QuickLauncherItem> =
    entries.sortedBy { it.key }
        .map { it.value }
        .filter {
            it.type == QuickLauncherItemType.APP ||
                it.type == QuickLauncherItemType.SHORTCUT ||
                it.type == QuickLauncherItemType.ACTION
        }

fun FvAppSwitcherSettings.runtimeItems(): List<QuickLauncherItem?> {
    val count = slotCount()
    return List(count) { index -> itemAt(index)?.takeIf { it.payload.isNotBlank() } }
}
