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

@Composable
fun HideTriggerSettingsRows(
    settings: AppSettings,
    enabled: Boolean,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onHideOnLockScreenChange: (Boolean) -> Unit,
    onHideOnLauncherChange: (Boolean) -> Unit,
) {
    // "横屏模式" enables edge triggers in landscape; stored as hideTriggerInLandscape (hide when false).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_landscape),
        icon = { Icon(Icons.Default.ScreenRotation, contentDescription = null) },
        checked = !settings.hideTriggerInLandscape,
        enabled = enabled,
        onCheckedChange = { landscapeModeEnabled -> onHideInLandscapeChange(!landscapeModeEnabled) },
    )
    // "锁屏界面" enables edge triggers on lock screen; stored as hideTriggerOnLockScreen (hide when true).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_lock_screen),
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        checked = !settings.hideTriggerOnLockScreen,
        enabled = enabled,
        onCheckedChange = { lockScreenEnabled -> onHideOnLockScreenChange(!lockScreenEnabled) },
    )
    // "系统桌面" enables edge triggers on home launcher; stored as hideTriggerOnLauncher (hide when true).
    SettingSwitchRow(
        title = stringResource(R.string.hide_trigger_launcher),
        icon = { Icon(Icons.Default.Home, contentDescription = null) },
        checked = !settings.hideTriggerOnLauncher,
        enabled = enabled,
        onCheckedChange = { launcherEnabled -> onHideOnLauncherChange(!launcherEnabled) },
    )
}
