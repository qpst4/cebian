package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.settings.ClipboardHistoryCapacity
import com.slideindex.app.settings.ClipboardMonitoringPath
import com.slideindex.app.ui.settings.SettingsSection
import com.slideindex.app.ui.settings.clipboard.rememberClipboardMonitoringUiState
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
    onClipboardScreenshotMonitoringChange: (Boolean) -> Unit,
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
    val monitoringUi = rememberClipboardMonitoringUiState(settings)
    val readLogsGranted = monitoringUi.state.readLogsGranted
    val lsposedServiceConnected = monitoringUi.state.lsposedServiceConnected
    val selfHookReady = monitoringUi.state.selfHookReady
    val lsposedWhitelistSynced = monitoringUi.state.lsposedWhitelistSynced
    val adbCommand = remember { ClipboardPermissionHelper.adbGrantReadLogsCommand(context) }
    var mediaReadGranted by remember {
        mutableStateOf(ClipboardPermissionHelper.hasMediaReadPermission(context))
    }
    var pendingScreenshotEnable by remember { mutableStateOf(false) }
    val mediaReadPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        mediaReadGranted = ClipboardPermissionHelper.hasMediaReadPermission(context)
        if (mediaReadGranted) {
            if (pendingScreenshotEnable) {
                pendingScreenshotEnable = false
                onClipboardScreenshotMonitoringChange(true)
            } else if (settings.clipboardScreenshotMonitoring) {
                SlideIndexAccessibilityService.accessibilityInstance()?.syncScreenshotMonitoring()
            }
        } else {
            pendingScreenshotEnable = false
        }
    }

    fun requestMediaReadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaReadPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            mediaReadGranted = true
            if (pendingScreenshotEnable) {
                pendingScreenshotEnable = false
                onClipboardScreenshotMonitoringChange(true)
            } else if (settings.clipboardScreenshotMonitoring) {
                SlideIndexAccessibilityService.accessibilityInstance()?.syncScreenshotMonitoring()
            }
        }
    }

    LaunchedEffect(settings.clipboardScreenshotMonitoring) {
        mediaReadGranted = ClipboardPermissionHelper.hasMediaReadPermission(context)
    }

    fun promptShizukuReadLogsGrant() {
        showShizukuGrantReminderDialog = true
    }

    fun performShizukuReadLogsGrant() {
        showShizukuGrantReminderDialog = false
        if (onRequestReadLogsGrant()) {
            monitoringUi.refreshReadLogsGranted()
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

        ClipboardScreenshotMonitoringSection(
            monitoringEnabled = settings.clipboardScreenshotMonitoring,
            mediaReadGranted = mediaReadGranted,
            onMonitoringChange = { enabled ->
                if (!enabled) {
                    pendingScreenshotEnable = false
                    onClipboardScreenshotMonitoringChange(false)
                    return@ClipboardScreenshotMonitoringSection
                }
                if (mediaReadGranted) {
                    onClipboardScreenshotMonitoringChange(true)
                } else {
                    pendingScreenshotEnable = true
                    requestMediaReadPermission()
                }
            },
            onRequestMediaReadPermission = ::requestMediaReadPermission,
        )

        ClipboardBackgroundMonitoringSection(
            monitoringEnabled = settings.clipboardBackgroundMonitoring,
            monitoringPath = settings.clipboardBackgroundMonitoringPath,
            readLogsGranted = readLogsGranted,
            lsposedServiceConnected = lsposedServiceConnected,
            selfHookReady = selfHookReady,
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
            val whitelistStatusReady = lsposedWhitelistSynced
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
private fun ClipboardScreenshotMonitoringSection(
    monitoringEnabled: Boolean,
    mediaReadGranted: Boolean,
    onMonitoringChange: (Boolean) -> Unit,
    onRequestMediaReadPermission: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.clipboard_screenshot_monitoring_section)) {
        SettingSwitchRow(
            title = stringResource(R.string.clipboard_screenshot_monitoring_title),
            subtitle = stringResource(R.string.clipboard_screenshot_monitoring_desc),
            checked = monitoringEnabled,
            enabled = true,
            onCheckedChange = onMonitoringChange,
        )
    }
    if (monitoringEnabled || !mediaReadGranted) {
        SettingsCard {
            SettingLinkRow(
                title = stringResource(R.string.clipboard_media_read_status_title),
                subtitle = if (mediaReadGranted) {
                    stringResource(R.string.clipboard_media_read_status_granted)
                } else {
                    stringResource(R.string.clipboard_media_read_status_denied)
                },
                onClick = {
                    if (!mediaReadGranted) {
                        onRequestMediaReadPermission()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipboardBackgroundMonitoringSection(
    monitoringEnabled: Boolean,
    monitoringPath: ClipboardMonitoringPath,
    readLogsGranted: Boolean,
    lsposedServiceConnected: Boolean,
    selfHookReady: Boolean,
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
        SettingsRadioGroup {
            ClipboardMonitoringPath.entries.forEach { path ->
                SettingRadioRow(
                    title = clipboardMonitoringPathLabel(path),
                    subtitle = clipboardMonitoringPathDescription(path),
                    selected = monitoringPath == path,
                    segmentKey = path,
                    onClick = { onPathSelected(path) },
                )
            }
        }
        key(monitoringPath) {
            SettingsCard {
                when (monitoringPath) {
                    ClipboardMonitoringPath.LOGCAT -> {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_read_logs_status_title),
                            subtitle = if (readLogsGranted) {
                                stringResource(R.string.clipboard_read_logs_status_granted)
                            } else {
                                stringResource(R.string.clipboard_read_logs_status_denied)
                            },
                            onClick = {
                                if (!readLogsGranted) {
                                    onRequestReadLogsGrant()
                                }
                            },
                        )
                    }
                    ClipboardMonitoringPath.LSPOSED -> {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_self_hook_status_title),
                            subtitle = if (selfHookReady) {
                                stringResource(R.string.clipboard_self_hook_status_ready)
                            } else {
                                stringResource(R.string.clipboard_self_hook_status_not_ready)
                            },
                            onClick = {
                                if (!selfHookReady) {
                                    onShowLsposedTroubleshoot()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCardScope.StashClipboardEntryCard(
    settings: ExtensionHubSettings,
    stashEntryCount: Int,
    onClick: () -> Unit,
) {
    val monitoringUi = rememberClipboardMonitoringUiState(settings.toMinimalAppSettings())
    val clipboardMonitoringEnabled = settings.clipboardBackgroundMonitoring
    val clipboardMonitoringPath = settings.clipboardBackgroundMonitoringPath
    val readLogsGranted = monitoringUi.state.readLogsGranted
    val selfHookReady = monitoringUi.state.selfHookReady
    val stashPart = pluralStringResource(
        R.plurals.stash_clipboard_entry_summary_stash,
        stashEntryCount,
        stashEntryCount,
    )
    val clipboardPart = when {
        !clipboardMonitoringEnabled ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_off)
        clipboardMonitoringPath == ClipboardMonitoringPath.LSPOSED && selfHookReady ->
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
