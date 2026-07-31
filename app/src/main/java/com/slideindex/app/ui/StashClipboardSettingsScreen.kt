package com.slideindex.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.slideindex.app.settings.ClipboardHistoryCapacity
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.settings.SettingsSection
import com.slideindex.app.ui.settings.clipboard.isClipboardMonitoringBackendReady
import com.slideindex.app.ui.settings.clipboard.rememberClipboardMonitoringUiState
import com.slideindex.app.ui.settings.components.SettingsCardScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashClipboardSettingsScreen(
    settings: AppSettings,
    clipboardEntryCount: Int,
    stashEntryCount: Int,
    onBack: () -> Unit,
    onClipboardMonitoringChange: (Boolean) -> Unit,
    onClipboardMonitoringModeChange: (ClipboardMonitoringMode) -> Unit,
    onClipboardScreenshotMonitoringChange: (Boolean) -> Unit,
    onClipboardHistoryMaxEntriesChange: (Int) -> Unit,
    onClearClipboardHistory: () -> Unit,
    onClearStash: () -> Unit,
) {
    val context = LocalContext.current
    var showClearClipboardDialog by remember { mutableStateOf(false) }
    var showClearStashDialog by remember { mutableStateOf(false) }
    var showCapacityDialog by remember { mutableStateOf(false) }
    val monitoringUi = rememberClipboardMonitoringUiState(settings)
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
            monitoringMode = settings.clipboardBackgroundMonitoringMode,
            monitoringState = monitoringUi,
            onMonitoringChange = onClipboardMonitoringChange,
            onModeSelected = onClipboardMonitoringModeChange,
        )
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
    monitoringMode: ClipboardMonitoringMode,
    monitoringState: com.slideindex.app.ui.settings.clipboard.ClipboardMonitoringUiState,
    onMonitoringChange: (Boolean) -> Unit,
    onModeSelected: (ClipboardMonitoringMode) -> Unit,
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
            ClipboardMonitoringMode.entries.forEach { mode ->
                SettingRadioRow(
                    title = clipboardMonitoringModeLabel(mode),
                    subtitle = clipboardMonitoringModeDescription(mode),
                    selected = monitoringMode == mode,
                    segmentKey = mode,
                    onClick = { onModeSelected(mode) },
                )
            }
        }
        key(monitoringMode) {
            SettingsCard {
                val backendReady = isClipboardMonitoringBackendReady(monitoringMode, monitoringState)
                SettingLinkRow(
                    title = stringResource(R.string.clipboard_monitor_backend_status_title),
                    subtitle = when {
                        monitoringMode.usesRoot && monitoringState.rootAvailable ->
                            stringResource(R.string.clipboard_monitor_backend_root_ready)
                        monitoringMode.usesRoot ->
                            stringResource(R.string.clipboard_monitor_backend_root_missing)
                        monitoringState.shizukuGranted ->
                            stringResource(R.string.clipboard_monitor_backend_shizuku_ready)
                        else ->
                            stringResource(R.string.clipboard_monitor_backend_shizuku_missing)
                    },
                    onClick = {},
                )
                SettingLinkRow(
                    title = stringResource(R.string.clipboard_monitor_overlay_status_title),
                    subtitle = if (monitoringState.overlayGranted) {
                        stringResource(R.string.clipboard_monitor_overlay_ready)
                    } else {
                        stringResource(R.string.clipboard_monitor_overlay_missing)
                    },
                    onClick = {},
                )
                SettingLinkRow(
                    title = stringResource(R.string.clipboard_monitor_service_status_title),
                    subtitle = if (monitoringState.monitorRunning && backendReady) {
                        stringResource(R.string.clipboard_monitor_service_running)
                    } else {
                        stringResource(R.string.clipboard_monitor_service_stopped)
                    },
                    onClick = {},
                )
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
    val stashPart = pluralStringResource(
        R.plurals.stash_clipboard_entry_summary_stash,
        stashEntryCount,
        stashEntryCount,
    )
    val mode = settings.clipboardBackgroundMonitoringMode
    val clipboardPart = when {
        !settings.clipboardBackgroundMonitoring ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_off)
        isClipboardMonitoringBackendReady(mode, monitoringUi) && mode.usesRoot ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_root)
        isClipboardMonitoringBackendReady(mode, monitoringUi) ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_shizuku)
        else ->
            stringResource(R.string.stash_clipboard_entry_summary_clipboard_not_ready)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.ContentPaste, contentDescription = label) },
        title = stringResource(R.string.stash_clipboard_entry_title),
        subtitle = stringResource(R.string.stash_clipboard_entry_summary, stashPart, clipboardPart),
        onClick = onClick,
    )
}

@Composable
private fun clipboardMonitoringModeLabel(mode: ClipboardMonitoringMode): String = when (mode) {
    ClipboardMonitoringMode.SHIZUKU_LOGS -> stringResource(R.string.clipboard_monitoring_mode_shizuku_logs)
    ClipboardMonitoringMode.SHIZUKU_HIDDEN_API -> stringResource(R.string.clipboard_monitoring_mode_shizuku_hidden_api)
    ClipboardMonitoringMode.ROOT_LOGS -> stringResource(R.string.clipboard_monitoring_mode_root_logs)
    ClipboardMonitoringMode.ROOT_HIDDEN_API -> stringResource(R.string.clipboard_monitoring_mode_root_hidden_api)
}

@Composable
private fun clipboardMonitoringModeDescription(mode: ClipboardMonitoringMode): String = when (mode) {
    ClipboardMonitoringMode.SHIZUKU_LOGS -> stringResource(R.string.clipboard_monitoring_mode_shizuku_logs_desc)
    ClipboardMonitoringMode.SHIZUKU_HIDDEN_API -> stringResource(R.string.clipboard_monitoring_mode_shizuku_hidden_api_desc)
    ClipboardMonitoringMode.ROOT_LOGS -> stringResource(R.string.clipboard_monitoring_mode_root_logs_desc)
    ClipboardMonitoringMode.ROOT_HIDDEN_API -> stringResource(R.string.clipboard_monitoring_mode_root_hidden_api_desc)
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
