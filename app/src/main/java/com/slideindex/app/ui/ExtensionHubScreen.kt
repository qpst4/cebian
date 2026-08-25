@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun ExtensionHubScreen(
    settings: ExtensionHubSettings,
    gestureActive: Boolean,
    stashEntryCount: Int,
    bottomContentPadding: Dp = 0.dp,
    bottomNavReselectCount: Int = 0,
    onOpenLayoutSettings: () -> Unit,
    onOpenQuickLauncher: () -> Unit,
    onOpenHoneycombLauncher: () -> Unit,
    onOpenHolographicLauncher: () -> Unit,
    onOpenActivityShortcuts: () -> Unit,
    onOpenShellCommands: () -> Unit,
    onOpenWidgetPanel: () -> Unit,
    onOpenFloatingPointer: () -> Unit,
    onOpenStashClipboard: () -> Unit,
    onOpenSearchPanel: () -> Unit,
    onOpenSettingsBackup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState,
    )

    val launchPanelTitle = stringResource(R.string.extension_section_launch_panel)
    val shortcutsTitle = stringResource(R.string.extension_section_shortcuts)
    val toolsTitle = stringResource(R.string.extension_section_tools)
    val otherTitle = stringResource(R.string.extension_section_other)
    val aboutTitle = stringResource(R.string.about_section_title)

    MiuixHubScaffold(
        title = stringResource(R.string.main_nav_extension),
        subtitle = stringResource(R.string.extension_hub_subtitle),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding,
    ) {
        settingsLazySmallTitle(
            key = "launch_panel_section",
            title = launchPanelTitle,
        )
        groupedCardItems(
            keyPrefix = "extension_launch_panel",
            items = buildList {
                add(
                    settingsCardScopeItem("layout-settings") {
                        LayoutSettingsEntryCard(
                            settings = settings,
                            enabled = gestureActive,
                            outlinedLeadingIcons = true,
                            onClick = onOpenLayoutSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("quick-launcher") {
                        QuickLauncherEntryCard(
                            settings = settings,
                            enabled = gestureActive,
                            outlinedLeadingIcons = true,
                            onClick = onOpenQuickLauncher,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("honeycomb-launcher") {
                        HoneycombLauncherEntryCard(
                            settings = settings,
                            enabled = gestureActive,
                            outlinedLeadingIcons = true,
                            onClick = onOpenHoneycombLauncher,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("holographic-launcher") {
                        HolographicLauncherEntryCard(
                            hiddenAppCount = settings.holographicHiddenAppCount,
                            enabled = gestureActive,
                            outlinedLeadingIcons = true,
                            onClick = onOpenHolographicLauncher,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("widget-panel") {
                        WidgetPanelEntryCard(
                            settings = settings,
                            enabled = gestureActive,
                            outlinedLeadingIcons = true,
                            onClick = onOpenWidgetPanel,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "shortcuts_section",
            title = shortcutsTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "extension_shortcuts",
            items = buildList {
                add(
                    settingsCardScopeItem("shell-command") {
                        ShellCommandEntryCard(
                            commandCount = settings.shellCommandCount,
                            outlinedLeadingIcons = true,
                            onClick = onOpenShellCommands,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("activity-shortcut") {
                        ActivityShortcutEntryCard(
                            shortcutCount = settings.activityShortcutCount,
                            outlinedLeadingIcons = true,
                            onClick = onOpenActivityShortcuts,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "tools_section",
            title = toolsTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "extension_tools",
            items = buildList {
                add(
                    settingsCardScopeItem("floating-pointer") {
                        FloatingPointerEntryCard(
                            settings = settings,
                            enabled = gestureActive,
                            outlinedLeadingIcons = true,
                            onClick = onOpenFloatingPointer,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("stash-clipboard") {
                        StashClipboardEntryCard(
                            settings = settings,
                            stashEntryCount = stashEntryCount,
                            outlinedLeadingIcons = true,
                            onClick = onOpenStashClipboard,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("search-panel") {
                        SearchPanelEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenSearchPanel,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "other_section",
            title = otherTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "extension_other",
            items = buildList {
                add(
                    settingsCardScopeItem("settings-backup") {
                        SettingsBackupEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenSettingsBackup,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "about_section", title = aboutTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "extension_about",
            items = buildList {
                add(
                    settingsCardScopeItem("about") {
                        AboutEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenAbout,
                        )
                    },
                )
            },
        )
    }
}

@Composable
fun SettingsCardScope.NativeEnginePacksEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Outlined.Memory, contentDescription = label) },
        title = stringResource(R.string.extension_native_engine_packs_entry_title),
        subtitle = stringResource(R.string.extension_native_engine_packs_entry_desc),
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.AboutEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(HubLeadingIcons.about(outlinedLeadingIcons), contentDescription = label) },
        title = stringResource(R.string.about_section_title),
        subtitle = "版本、更新与隐私协议",
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.PrivacyPolicyEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = label) },
        title = stringResource(R.string.privacy_policy_entry_title),
        subtitle = stringResource(R.string.privacy_policy_entry_desc),
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.OpenSourceLicenseEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Outlined.Gavel, contentDescription = label) },
        title = stringResource(R.string.about_open_source_license_title),
        subtitle = stringResource(R.string.about_open_source_license_desc),
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.ThirdPartyNoticesEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Outlined.Favorite, contentDescription = label) },
        title = stringResource(R.string.about_third_party_notices_title),
        subtitle = stringResource(R.string.about_third_party_notices_subtitle),
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.SettingsBackupEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(HubLeadingIcons.settingsBackup(outlinedLeadingIcons), contentDescription = label) },
        title = stringResource(R.string.settings_backup_entry_title),
        subtitle = stringResource(R.string.settings_backup_entry_desc),
        onClick = onClick,
    )
}
