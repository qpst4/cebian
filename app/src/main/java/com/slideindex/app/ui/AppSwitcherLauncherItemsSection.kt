package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSwitcherDisplaySettings
import com.slideindex.app.settings.HoneycombDisplaySettings

@Composable
fun AppSwitcherLauncherItemsSection(
    items: List<QuickLauncherItem>,
    display: AppSwitcherDisplaySettings,
    appsByPackage: Map<String, AppInfo>,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onAdd: () -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
) {
    HoneycombLauncherItemsSection(
        modifier = modifier,
        items = items,
        display = display.toHoneycombPreview(),
        appsByPackage = appsByPackage,
        onItemsChange = onItemsChange,
        onAdd = onAdd,
        onInteractionActiveChange = onInteractionActiveChange,
        descriptionResId = R.string.app_switcher_editor_desc,
        activityShortcuts = activityShortcuts,
        shellCommands = shellCommands,
    )
}

private fun AppSwitcherDisplaySettings.toHoneycombPreview(): HoneycombDisplaySettings =
    HoneycombDisplaySettings(
        iconSizeDp = iconSizeDp,
        spacingDp = spacingDp,
        selectionScale = selectionScale,
        dimPercent = dimPercent,
        blurDp = blurDp,
    )
