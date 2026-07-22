package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardHistoryCapacity

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashClipboardSettingsScreen(
    settings: AppSettings,
    clipboardEntryCount: Int,
    stashEntryCount: Int,
    shizukuGranted: Boolean,
    onBack: () -> Unit,
    onClipboardMonitoringChange: (Boolean) -> Unit,
    onClipboardHistoryMaxEntriesChange: (Int) -> Unit,
    onRequestReadLogsGrant: () -> Boolean,
    onClearClipboardHistory: () -> Unit,
    onClearStash: () -> Unit,
) {
    val context = LocalContext.current
    var showAdbDialog by remember { mutableStateOf(false) }
    var showClearClipboardDialog by remember { mutableStateOf(false) }
    var showClearStashDialog by remember { mutableStateOf(false) }
    var showCapacityDialog by remember { mutableStateOf(false) }
    var showShizukuGrantReminderDialog by remember { mutableStateOf(false) }
    val adbCommand = remember { ClipboardPermissionHelper.adbGrantReadLogsCommand(context) }
    var readLogsGranted by remember {
        mutableStateOf(ClipboardPermissionHelper.hasReadLogsPermission(context))
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readLogsGranted = ClipboardPermissionHelper.hasReadLogsPermission(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun promptShizukuReadLogsGrant() {
        showShizukuGrantReminderDialog = true
    }

    fun performShizukuReadLogsGrant() {
        showShizukuGrantReminderDialog = false
        if (onRequestReadLogsGrant()) {
            readLogsGranted = ClipboardPermissionHelper.hasReadLogsPermission(context)
        } else {
            showAdbDialog = true
        }
    }

    fun requestReadLogsGrant() {
        if (shizukuGranted) {
            promptShizukuReadLogsGrant()
            return
        }
        showAdbDialog = true
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.stash_clipboard_settings_title),
        subtitle = stringResource(R.string.stash_clipboard_settings_desc),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.stash_clipboard_section_clipboard))
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.History, contentDescription = label) },
                title = stringResource(R.string.clipboard_history_capacity_title),
                subtitle = clipboardCapacityLabel(settings.clipboardHistoryMaxEntries),
                onClick = { showCapacityDialog = true },
            )
            SettingSwitchRow(
                title = stringResource(R.string.clipboard_background_monitoring_title),
                subtitle = if (readLogsGranted) {
                    stringResource(R.string.clipboard_background_monitoring_desc)
                } else {
                    stringResource(R.string.clipboard_background_monitoring_desc_no_logs)
                },
                checked = settings.clipboardBackgroundMonitoring,
                enabled = true,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onClipboardMonitoringChange(false)
                        return@SettingSwitchRow
                    }
                    onClipboardMonitoringChange(true)
                    if (!readLogsGranted) {
                        requestReadLogsGrant()
                    }
                },
            )
            SettingLinkRow(
                title = stringResource(R.string.clipboard_read_logs_status_title),
                subtitle = if (readLogsGranted) {
                    stringResource(R.string.clipboard_read_logs_status_granted)
                } else {
                    stringResource(R.string.clipboard_read_logs_status_denied)
                },
                onClick = {
                    if (!readLogsGranted) {
                        requestReadLogsGrant()
                    }
                },
            )
            if (clipboardEntryCount > 0) {
                SettingLinkRow(
                    title = stringResource(R.string.clipboard_clear_history),
                    subtitle = pluralStringResource(
                        R.plurals.clipboard_history_count,
                        clipboardEntryCount,
                        clipboardEntryCount,
                    ),
                    onClick = { showClearClipboardDialog = true },
                )
            }
        }

        SettingsSectionTitle(stringResource(R.string.stash_clipboard_section_stash))
        SettingsCard {
            SettingsHintText(stringResource(R.string.stash_clipboard_stash_desc))
            if (stashEntryCount > 0) {
                SettingLinkRow(
                    title = stringResource(R.string.stash_clear_all),
                    subtitle = pluralStringResource(
                        R.plurals.stash_entry_count,
                        stashEntryCount,
                        stashEntryCount,
                    ),
                    onClick = { showClearStashDialog = true },
                )
            }
        }
    }

    if (showCapacityDialog) {
        ClipboardHistoryCapacityDialog(
            selected = settings.clipboardHistoryMaxEntries,
            onDismiss = { showCapacityDialog = false },
            onSelect = {
                onClipboardHistoryMaxEntriesChange(it)
                showCapacityDialog = false
            },
        )
    }

    if (showShizukuGrantReminderDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuGrantReminderDialog = false },
            title = { Text(stringResource(R.string.clipboard_read_logs_shizuku_reminder_title)) },
            text = { Text(stringResource(R.string.clipboard_read_logs_shizuku_reminder_message)) },
            confirmButton = {
                TextButton(onClick = { performShizukuReadLogsGrant() }) {
                    Text(stringResource(R.string.clipboard_read_logs_shizuku_reminder_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShizukuGrantReminderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            title = { Text(stringResource(R.string.clipboard_read_logs_adb_dialog_title)) },
            text = {
                Text(stringResource(R.string.clipboard_read_logs_adb_dialog_message, adbCommand))
            },
            confirmButton = {
                if (shizukuGranted) {
                    TextButton(
                        onClick = {
                            showAdbDialog = false
                            promptShizukuReadLogsGrant()
                        },
                    ) {
                        Text(stringResource(R.string.clipboard_read_logs_shizuku_grant))
                    }
                } else {
                    val copiedMessage = stringResource(R.string.secure_settings_adb_copied)
                    TextButton(
                        onClick = {
                            copyAdbCommandToClipboard(context, adbCommand)
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(R.string.secure_settings_adb_copy))
                    }
                }
            },
            dismissButton = {
                val copiedMessage = stringResource(R.string.secure_settings_adb_copied)
                TextButton(
                    onClick = {
                        if (shizukuGranted) {
                            copyAdbCommandToClipboard(context, adbCommand)
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                        showAdbDialog = false
                    },
                ) {
                    Text(
                        if (shizukuGranted) {
                            stringResource(R.string.secure_settings_adb_copy)
                        } else {
                            stringResource(R.string.confirm)
                        },
                    )
                }
            },
        )
    }

    if (showClearClipboardDialog) {
        AlertDialog(
            onDismissRequest = { showClearClipboardDialog = false },
            title = { Text(stringResource(R.string.clipboard_clear_history_confirm_title)) },
            text = { Text(stringResource(R.string.clipboard_clear_history_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearClipboardDialog = false
                        onClearClipboardHistory()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearClipboardDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearStashDialog) {
        AlertDialog(
            onDismissRequest = { showClearStashDialog = false },
            title = { Text(stringResource(R.string.stash_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.stash_clear_all_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearStashDialog = false
                        onClearStash()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStashDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun StashClipboardEntryCard(
    clipboardMonitoringEnabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val readLogsGranted = remember { ClipboardPermissionHelper.hasReadLogsPermission(context) }
    val subtitle = when {
        clipboardMonitoringEnabled && readLogsGranted ->
            stringResource(R.string.stash_clipboard_entry_summary_monitoring_on)
        clipboardMonitoringEnabled && !readLogsGranted ->
            stringResource(R.string.stash_clipboard_entry_summary_monitoring_fallback)
        else -> stringResource(R.string.stash_clipboard_entry_desc)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.ContentPaste, contentDescription = label) },
        title = stringResource(R.string.stash_clipboard_entry_title),
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun ClipboardHistoryCapacityDialog(
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clipboard_history_capacity_title)) },
        text = {
            Column {
                ClipboardHistoryCapacity.presets.forEach { capacity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(capacity) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == capacity,
                            onClick = { onSelect(capacity) },
                        )
                        Text(
                            text = clipboardCapacityLabel(capacity),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun clipboardCapacityLabel(capacity: Int): String =
    if (capacity == ClipboardHistoryCapacity.UNLIMITED) {
        stringResource(R.string.clipboard_history_capacity_unlimited)
    } else {
        capacity.toString()
    }

private fun copyAdbCommandToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("adb_command", text))
}
