package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.slideindex.app.clipboard.ClipboardWhitelistBridge
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardHistoryCapacity
import com.slideindex.app.settings.ClipboardMonitoringPath
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.pickerSegmentedColors
import com.slideindex.app.ui.pickerSegmentedShapes
import com.slideindex.app.ui.settingsSegmentedColors
import com.slideindex.app.ui.settings.SettingsSection
import com.slideindex.app.ui.settings.clipboard.isClipboardSelfHookReady
import com.slideindex.app.ui.settings.components.SettingsCardScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashClipboardSettingsScreen(
    settings: AppSettings,
    clipboardEntryCount: Int,
    stashEntryCount: Int,
    shizukuGranted: Boolean,
    onBack: () -> Unit,
    onClipboardMonitoringChange: (Boolean) -> Unit,
    onClipboardMonitoringPathChange: (ClipboardMonitoringPath) -> Unit,
    onOpenLsposedWhitelist: () -> Unit,
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
    var showLsposedTroubleshootDialog by remember { mutableStateOf(false) }
    var lsposedServiceConnected by remember { mutableStateOf(ClipboardWhitelistBridge.isServiceConnected()) }
    var lsposedReady by remember { mutableStateOf(ClipboardWhitelistBridge.isReady(settings)) }
    val adbCommand = remember { ClipboardPermissionHelper.adbGrantReadLogsCommand(context) }
    var readLogsGranted by remember {
        mutableStateOf(ClipboardPermissionHelper.hasReadLogsPermission(context))
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readLogsGranted = ClipboardPermissionHelper.hasReadLogsPermission(context)
                lsposedServiceConnected = ClipboardWhitelistBridge.isServiceConnected()
                lsposedReady = ClipboardWhitelistBridge.isReady(settings)
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

    fun onMonitoringPathSelected(path: ClipboardMonitoringPath) {
        onClipboardMonitoringPathChange(path)
        if (settings.clipboardBackgroundMonitoring &&
            path == ClipboardMonitoringPath.LOGCAT &&
            !readLogsGranted
        ) {
            requestReadLogsGrant()
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.stash_clipboard_settings_title),
        subtitle = stringResource(R.string.stash_clipboard_settings_desc),
        onBack = onBack,
    ) {
        SettingsSection(title = stringResource(R.string.stash_clipboard_section_stash)) {
            SettingsHintText(stringResource(R.string.stash_clipboard_stash_desc))
            SettingLinkRow(
                title = stringResource(R.string.stash_clear_all),
                subtitle = pluralStringResource(
                    R.plurals.stash_entry_count,
                    stashEntryCount,
                    stashEntryCount,
                ),
                enabled = stashEntryCount > 0,
                onClick = { showClearStashDialog = true },
            )
        }

        SettingsSection(title = stringResource(R.string.stash_clipboard_section_history)) {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.History, contentDescription = label) },
                title = stringResource(R.string.clipboard_history_capacity_title),
                subtitle = clipboardCapacityLabel(settings.clipboardHistoryMaxEntries),
                onClick = { showCapacityDialog = true },
            )
            SettingLinkRow(
                title = stringResource(R.string.clipboard_clear_history),
                subtitle = pluralStringResource(
                    R.plurals.clipboard_history_count,
                    clipboardEntryCount,
                    clipboardEntryCount,
                ),
                enabled = clipboardEntryCount > 0,
                onClick = { showClearClipboardDialog = true },
            )
        }

        ClipboardBackgroundMonitoringSection(
            monitoringEnabled = settings.clipboardBackgroundMonitoring,
            monitoringPath = settings.clipboardBackgroundMonitoringPath,
            readLogsGranted = readLogsGranted,
            lsposedServiceConnected = lsposedServiceConnected,
            lsposedReady = lsposedReady,
            onMonitoringChange = { enabled ->
                if (!enabled) {
                    onClipboardMonitoringChange(false)
                    return@ClipboardBackgroundMonitoringSection
                }
                onClipboardMonitoringChange(true)
                if (settings.clipboardBackgroundMonitoringPath == ClipboardMonitoringPath.LOGCAT &&
                    !readLogsGranted
                ) {
                    requestReadLogsGrant()
                }
            },
            onPathSelected = ::onMonitoringPathSelected,
            onRequestReadLogsGrant = ::requestReadLogsGrant,
            onShowLsposedTroubleshoot = { showLsposedTroubleshootDialog = true },
        )

        SettingsSection(title = stringResource(R.string.clipboard_lsposed_section_title)) {
            SettingsHintText(stringResource(R.string.clipboard_lsposed_section_desc))
            val whitelistStatusReady = lsposedReady
            SettingLinkRow(
                title = stringResource(R.string.clipboard_lsposed_status_title),
                subtitle = when {
                    whitelistStatusReady ->
                        stringResource(R.string.clipboard_lsposed_status_ready)
                    lsposedServiceConnected ->
                        stringResource(R.string.clipboard_lsposed_status_whitelist_empty)
                    else -> stringResource(R.string.clipboard_lsposed_status_service_missing)
                },
                onClick = {
                    if (!whitelistStatusReady) {
                        showLsposedTroubleshootDialog = true
                    }
                },
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Apps, contentDescription = label) },
                title = stringResource(R.string.clipboard_lsposed_whitelist_entry_title),
                subtitle = pluralStringResource(
                    R.plurals.clipboard_lsposed_whitelist_count,
                    settings.clipboardLsposedExtraWhitelist.size,
                    settings.clipboardLsposedExtraWhitelist.size,
                ),
                onClick = onOpenLsposedWhitelist,
            )
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

    if (showLsposedTroubleshootDialog) {
        AlertDialog(
            onDismissRequest = { showLsposedTroubleshootDialog = false },
            title = { Text(stringResource(R.string.clipboard_lsposed_troubleshoot_title)) },
            text = { Text(stringResource(R.string.clipboard_lsposed_troubleshoot_message)) },
            confirmButton = {
                TextButton(onClick = { showLsposedTroubleshootDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipboardBackgroundMonitoringSection(
    monitoringEnabled: Boolean,
    monitoringPath: ClipboardMonitoringPath,
    readLogsGranted: Boolean,
    lsposedServiceConnected: Boolean,
    lsposedReady: Boolean,
    onMonitoringChange: (Boolean) -> Unit,
    onPathSelected: (ClipboardMonitoringPath) -> Unit,
    onRequestReadLogsGrant: () -> Unit,
    onShowLsposedTroubleshoot: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.clipboard_background_monitoring_section)) {
        SettingSwitchRow(
            title = stringResource(R.string.clipboard_background_monitoring_title),
            subtitle = stringResource(R.string.clipboard_background_monitoring_desc),
            checked = monitoringEnabled,
            enabled = true,
            onCheckedChange = onMonitoringChange,
        )
    }
    if (monitoringEnabled) {
        ClipboardMonitoringPathPicker(
            selectedPath = monitoringPath,
            onPathSelected = onPathSelected,
        )
        ClipboardMonitoringStatusRow(
            monitoringPath = monitoringPath,
            readLogsGranted = readLogsGranted,
            lsposedServiceConnected = lsposedServiceConnected,
            lsposedReady = lsposedReady,
            onRequestReadLogsGrant = onRequestReadLogsGrant,
            onShowLsposedTroubleshoot = onShowLsposedTroubleshoot,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipboardMonitoringStatusRow(
    monitoringPath: ClipboardMonitoringPath,
    readLogsGranted: Boolean,
    lsposedServiceConnected: Boolean,
    lsposedReady: Boolean,
    onRequestReadLogsGrant: () -> Unit,
    onShowLsposedTroubleshoot: () -> Unit,
) {
    key(monitoringPath) {
        when (monitoringPath) {
            ClipboardMonitoringPath.LOGCAT -> {
                val title = stringResource(R.string.clipboard_read_logs_status_title)
                val subtitle = if (readLogsGranted) {
                    stringResource(R.string.clipboard_read_logs_status_granted)
                } else {
                    stringResource(R.string.clipboard_read_logs_status_denied)
                }
                SegmentedListItem(
                    onClick = {
                        if (!readLogsGranted) {
                            onRequestReadLogsGrant()
                        }
                    },
                    enabled = true,
                    shapes = pickerSegmentedShapes(0, 1),
                    colors = settingsSegmentedColors(),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_navigate_forward),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    content = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    },
                )
            }
            ClipboardMonitoringPath.LSPOSED -> {
                val selfHookReady = isClipboardSelfHookReady(
                    lsposedServiceConnected = lsposedServiceConnected,
                    lsposedReady = lsposedReady,
                )
                val title = stringResource(R.string.clipboard_self_hook_status_title)
                val subtitle = if (selfHookReady) {
                    stringResource(R.string.clipboard_self_hook_status_ready)
                } else {
                    stringResource(R.string.clipboard_self_hook_status_not_ready)
                }
                SegmentedListItem(
                    onClick = {
                        if (!selfHookReady) {
                            onShowLsposedTroubleshoot()
                        }
                    },
                    enabled = true,
                    shapes = pickerSegmentedShapes(0, 1),
                    colors = settingsSegmentedColors(),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_navigate_forward),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    content = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipboardMonitoringPathPicker(
    selectedPath: ClipboardMonitoringPath,
    onPathSelected: (ClipboardMonitoringPath) -> Unit,
) {
    val paths = ClipboardMonitoringPath.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        paths.forEachIndexed { index, path ->
            val selected = selectedPath == path
            val title = clipboardMonitoringPathLabel(path)
            val subtitle = clipboardMonitoringPathDescription(path)
            key(path) {
                SegmentedListItem(
                    selected = selected,
                    onClick = { onPathSelected(path) },
                    enabled = true,
                    shapes = pickerSegmentedShapes(index, paths.size),
                    colors = pickerSegmentedColors(),
                    trailingContent = {
                        RadioButton(
                            selected = selected,
                            onClick = { onPathSelected(path) },
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    content = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun SettingsCardScope.StashClipboardEntryCard(
    clipboardMonitoringEnabled: Boolean,
    clipboardMonitoringPath: ClipboardMonitoringPath,
    stashEntryCount: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val readLogsGranted = remember { ClipboardPermissionHelper.hasReadLogsPermission(context) }
    val lsposedReady = remember { ClipboardWhitelistBridge.isServiceConnected() }
    val stashPart = pluralStringResource(
        R.plurals.stash_clipboard_entry_summary_stash,
        stashEntryCount,
        stashEntryCount,
    )
    val clipboardPart = when {
        !clipboardMonitoringEnabled ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_off)
        clipboardMonitoringPath == ClipboardMonitoringPath.LSPOSED && lsposedReady ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_lsposed)
        clipboardMonitoringPath == ClipboardMonitoringPath.LSPOSED ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_lsposed_not_ready)
        readLogsGranted ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_log)
        else ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_log_no_perm)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.ContentPaste, contentDescription = label) },
        title = stringResource(R.string.stash_clipboard_entry_title),
        subtitle = stringResource(R.string.stash_clipboard_entry_summary, stashPart, clipboardPart),
        onClick = onClick,
    )
}

@Composable
private fun clipboardMonitoringPathLabel(path: ClipboardMonitoringPath): String = when (path) {
    ClipboardMonitoringPath.LOGCAT -> stringResource(R.string.clipboard_monitoring_path_logcat)
    ClipboardMonitoringPath.LSPOSED -> stringResource(R.string.clipboard_monitoring_path_lsposed)
}

@Composable
private fun clipboardMonitoringPathDescription(path: ClipboardMonitoringPath): String = when (path) {
    ClipboardMonitoringPath.LOGCAT -> stringResource(R.string.clipboard_monitoring_path_logcat_desc)
    ClipboardMonitoringPath.LSPOSED -> stringResource(R.string.clipboard_monitoring_path_lsposed_desc)
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
