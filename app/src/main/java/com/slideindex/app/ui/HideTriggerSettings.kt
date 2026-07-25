package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingsCardScope

@Composable
fun SettingsCardScope.HideTriggerSettingsRows(
    settings: AppSettings,
    enabled: Boolean,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onHideOnLockScreenChange: (Boolean) -> Unit,
    onHideOnLauncherChange: (Boolean) -> Unit,
) {
    // "妯睆妯″紡" enables edge triggers in landscape; stored as hideTriggerInLandscape (hide when false).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_landscape),
        icon = { label -> Icon(Icons.Default.ScreenRotation, contentDescription = label) },
        checked = !settings.hideTriggerInLandscape,
        enabled = enabled,
        onCheckedChange = { landscapeModeEnabled -> onHideInLandscapeChange(!landscapeModeEnabled) },
    )
    // "閿佸睆鐣岄潰" enables edge triggers on lock screen; stored as hideTriggerOnLockScreen (hide when true).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_lock_screen),
        icon = { label -> Icon(Icons.Default.Lock, contentDescription = label) },
        checked = !settings.hideTriggerOnLockScreen,
        enabled = enabled,
        onCheckedChange = { lockScreenEnabled -> onHideOnLockScreenChange(!lockScreenEnabled) },
    )
    // "绯荤粺妗岄潰" enables edge triggers on home launcher; stored as hideTriggerOnLauncher (hide when true).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_launcher),
        icon = { label -> Icon(Icons.Default.Home, contentDescription = label) },
        checked = !settings.hideTriggerOnLauncher,
        enabled = enabled,
        onCheckedChange = { launcherEnabled -> onHideOnLauncherChange(!launcherEnabled) },
    )
}
