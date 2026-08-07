@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Memory
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
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope

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
    onOpenActivityShortcuts: () -> Unit,
    onOpenShellCommands: () -> Unit,
    onOpenWidgetPanel: () -> Unit,
    onOpenFloatingPointer: () -> Unit,
    onOpenStashClipboard: () -> Unit,
    onOpenSettingsBackup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState,
    )

    MiuixHubScaffold(
        title = stringResource(R.string.main_nav_extension),
        subtitle = stringResource(R.string.extension_hub_subtitle),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding,
    ) {
        MiuixSmallTitle(stringResource(R.string.settings_section_features), modifier = Modifier.fillMaxWidth())
        SettingsCard {
            LayoutSettingsEntryCard(
                settings = settings,
                enabled = gestureActive,
                outlinedLeadingIcons = true,
                onClick = onOpenLayoutSettings,
            )
            QuickLauncherEntryCard(
                settings = settings,
                enabled = gestureActive,
                outlinedLeadingIcons = true,
                onClick = onOpenQuickLauncher,
            )
            HoneycombLauncherEntryCard(
                settings = settings,
                enabled = gestureActive,
                outlinedLeadingIcons = true,
                onClick = onOpenHoneycombLauncher,
            )
            ActivityShortcutEntryCard(
                shortcutCount = settings.activityShortcutCount,
                outlinedLeadingIcons = true,
                onClick = onOpenActivityShortcuts,
            )
            ShellCommandEntryCard(
                commandCount = settings.shellCommandCount,
                outlinedLeadingIcons = true,
                onClick = onOpenShellCommands,
            )
            WidgetPanelEntryCard(
                settings = settings,
                enabled = gestureActive,
                outlinedLeadingIcons = true,
                onClick = onOpenWidgetPanel,
            )
            FloatingPointerEntryCard(
                settings = settings,
                enabled = gestureActive,
                outlinedLeadingIcons = true,
                onClick = onOpenFloatingPointer,
            )
            StashClipboardEntryCard(
                settings = settings,
                stashEntryCount = stashEntryCount,
                outlinedLeadingIcons = true,
                onClick = onOpenStashClipboard,
            )
            SettingsBackupEntryCard(outlinedLeadingIcons = true, onClick = onOpenSettingsBackup)
        }

        MiuixSmallTitle(stringResource(R.string.about_section_title), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            AboutEntryCard(outlinedLeadingIcons = true, onClick = onOpenAbout)
        }
    }
}

@Composable
fun SettingsCardScope.NativeEnginePacksEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Memory, contentDescription = label) },
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
        icon = { label -> Icon(Icons.AutoMirrored.Filled.Article, contentDescription = label) },
        title = stringResource(R.string.privacy_policy_entry_title),
        subtitle = stringResource(R.string.privacy_policy_entry_desc),
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.OpenSourceLicenseEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Gavel, contentDescription = label) },
        title = stringResource(R.string.about_open_source_license_title),
        subtitle = stringResource(R.string.about_open_source_license_desc),
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.ThirdPartyNoticesEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Favorite, contentDescription = label) },
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
