package com.slideindex.app.ui

import com.slideindex.app.ui.HomeLeadingIcons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsCardScopeContent
import com.slideindex.app.ui.settings.components.settingsCardItem

@Composable
fun hideTriggerSettingsCardItems(
    hideTriggerInLandscape: Boolean,
    hideTriggerOnLockScreen: Boolean,
    hideTriggerOnLauncher: Boolean,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onHideOnLockScreenChange: (Boolean) -> Unit,
    onHideOnLauncherChange: (Boolean) -> Unit,
): List<CardItem> = listOf(
    settingsCardItem("hide-trigger-landscape") {
        SettingsCardScopeContent {
            SettingSwitchRow(
            title = stringResource(R.string.hide_trigger_landscape),
            icon = { label ->
                Icon(HomeLeadingIcons.hideTriggerLandscape(outlinedLeadingIcons), contentDescription = label)
            },
            checked = !hideTriggerInLandscape,
            enabled = enabled,
            onCheckedChange = { landscapeModeEnabled -> onHideInLandscapeChange(!landscapeModeEnabled) },
            )
        }
    },
    settingsCardItem("hide-trigger-lock-screen") {
        SettingsCardScopeContent {
            SettingSwitchRow(
            title = stringResource(R.string.hide_trigger_lock_screen),
            icon = { label ->
                Icon(HomeLeadingIcons.hideTriggerLock(outlinedLeadingIcons), contentDescription = label)
            },
            checked = !hideTriggerOnLockScreen,
            enabled = enabled,
            onCheckedChange = { lockScreenEnabled -> onHideOnLockScreenChange(!lockScreenEnabled) },
            )
        }
    },
    settingsCardItem("hide-trigger-launcher") {
        SettingsCardScopeContent {
            SettingSwitchRow(
            title = stringResource(R.string.hide_trigger_launcher),
            icon = { label ->
                Icon(HomeLeadingIcons.hideTriggerLauncher(outlinedLeadingIcons), contentDescription = label)
            },
            checked = !hideTriggerOnLauncher,
            enabled = enabled,
            onCheckedChange = { launcherEnabled -> onHideOnLauncherChange(!launcherEnabled) },
            )
        }
    },
)

@Composable
fun SettingsCardScope.HideTriggerSettingsRows(
    settings: HomeMainSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onHideOnLockScreenChange: (Boolean) -> Unit,
    onHideOnLauncherChange: (Boolean) -> Unit,
) {
    HideTriggerSettingsRows(
        hideTriggerInLandscape = settings.hideTriggerInLandscape,
        hideTriggerOnLockScreen = settings.hideTriggerOnLockScreen,
        hideTriggerOnLauncher = settings.hideTriggerOnLauncher,
        enabled = enabled,
        outlinedLeadingIcons = outlinedLeadingIcons,
        onHideInLandscapeChange = onHideInLandscapeChange,
        onHideOnLockScreenChange = onHideOnLockScreenChange,
        onHideOnLauncherChange = onHideOnLauncherChange,
    )
}

@Composable
fun SettingsCardScope.HideTriggerSettingsRows(
    hideTriggerInLandscape: Boolean,
    hideTriggerOnLockScreen: Boolean,
    hideTriggerOnLauncher: Boolean,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onHideOnLockScreenChange: (Boolean) -> Unit,
    onHideOnLauncherChange: (Boolean) -> Unit,
) {
    // "妯睆妯″紡" enables edge triggers in landscape; stored as hideTriggerInLandscape (hide when false).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_landscape),
        icon = { label ->
            Icon(HomeLeadingIcons.hideTriggerLandscape(outlinedLeadingIcons), contentDescription = label)
        },
        checked = !hideTriggerInLandscape,
        enabled = enabled,
        onCheckedChange = { landscapeModeEnabled -> onHideInLandscapeChange(!landscapeModeEnabled) },
    )
    // "閿佸睆鐣岄潰" enables edge triggers on lock screen; stored as hideTriggerOnLockScreen (hide when true).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_lock_screen),
        icon = { label ->
            Icon(HomeLeadingIcons.hideTriggerLock(outlinedLeadingIcons), contentDescription = label)
        },
        checked = !hideTriggerOnLockScreen,
        enabled = enabled,
        onCheckedChange = { lockScreenEnabled -> onHideOnLockScreenChange(!lockScreenEnabled) },
    )
    // "绯荤粺妗岄潰" enables edge triggers on home launcher; stored as hideTriggerOnLauncher (hide when true).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_launcher),
        icon = { label ->
            Icon(HomeLeadingIcons.hideTriggerLauncher(outlinedLeadingIcons), contentDescription = label)
        },
        checked = !hideTriggerOnLauncher,
        enabled = enabled,
        onCheckedChange = { launcherEnabled -> onHideOnLauncherChange(!launcherEnabled) },
    )
}
