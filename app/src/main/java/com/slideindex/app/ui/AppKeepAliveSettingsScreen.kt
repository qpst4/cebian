package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.util.SecureSettingsHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppKeepAliveSettingsScreen(
    hideFromRecents: Boolean,
    batteryOptimizationExempt: Boolean,
    accessibilityKeepAliveEnabled: Boolean,
    writeSecureSettingsGranted: Boolean,
    shizukuGranted: Boolean,
    onBack: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAutoStart: () -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onAccessibilityKeepAliveChange: (Boolean) -> Unit,
    onRequestSecureSettingsGrant: () -> Boolean,
) {
    val context = LocalContext.current
    var showAdbDialog by remember { mutableStateOf(false) }
    val adbCommand = remember { SecureSettingsHelper.adbGrantCommand(context) }

    SettingsScreenScaffold(
        title = stringResource(R.string.app_keep_alive_title),
        subtitle = stringResource(R.string.app_keep_alive_desc),
        onBack = onBack,
    ) {
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.battery_optimization_title),
                subtitle = stringResource(R.string.battery_optimization_desc),
                checked = batteryOptimizationExempt,
                enabled = true,
                onCheckedChange = { onRequestBatteryOptimization() },
            )
            SettingLinkRow(
                title = stringResource(R.string.auto_start_title),
                subtitle = stringResource(R.string.auto_start_desc),
                onClick = onRequestAutoStart,
            )
            SettingSwitchRow(
                title = stringResource(R.string.hide_from_recents_title),
                subtitle = stringResource(R.string.hide_from_recents_desc),
                checked = hideFromRecents,
                enabled = true,
                onCheckedChange = onHideFromRecentsChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.secure_settings_title),
                subtitle = stringResource(R.string.secure_settings_desc),
                checked = accessibilityKeepAliveEnabled,
                enabled = true,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onAccessibilityKeepAliveChange(false)
                        return@SettingSwitchRow
                    }
                    if (writeSecureSettingsGranted) {
                        onAccessibilityKeepAliveChange(true)
                        return@SettingSwitchRow
                    }
                    if (shizukuGranted) {
                        val granted = onRequestSecureSettingsGrant()
                        if (granted) {
                            onAccessibilityKeepAliveChange(true)
                        } else {
                            showAdbDialog = true
                        }
                    } else {
                        showAdbDialog = true
                    }
                },
            )
        }
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            title = { Text(stringResource(R.string.secure_settings_adb_dialog_title)) },
            text = {
                Text(stringResource(R.string.secure_settings_adb_dialog_message, adbCommand))
            },
            confirmButton = {
                val copiedMessage = stringResource(R.string.secure_settings_adb_copied)
                TextButton(
                    onClick = {
                        copyToClipboard(context, adbCommand)
                        Toast.makeText(
                            context,
                            copiedMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.secure_settings_adb_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdbDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }
}

@Composable
fun AppKeepAliveEntryCard(
    batteryOptimizationExempt: Boolean,
    hideFromRecents: Boolean,
    accessibilityKeepAliveEnabled: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = when {
        batteryOptimizationExempt && hideFromRecents && accessibilityKeepAliveEnabled ->
            stringResource(R.string.app_keep_alive_entry_summary_all)
        batteryOptimizationExempt && hideFromRecents ->
            stringResource(R.string.app_keep_alive_entry_summary_all)
        batteryOptimizationExempt ->
            stringResource(R.string.app_keep_alive_entry_summary_battery)
        hideFromRecents ->
            stringResource(R.string.app_keep_alive_entry_summary_recents)
        accessibilityKeepAliveEnabled ->
            stringResource(R.string.secure_settings_enabled_summary)
        else -> stringResource(R.string.app_keep_alive_entry_desc)
    }
    SettingNavigationRow(
        icon = { Icon(Icons.Default.BatteryChargingFull, contentDescription = null) },
        title = stringResource(R.string.app_keep_alive_title),
        subtitle = subtitle,
        onClick = onClick,
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("adb_command", text))
}
