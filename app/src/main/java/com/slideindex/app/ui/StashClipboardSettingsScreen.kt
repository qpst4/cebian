@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardFloatEntryClickAction
import com.slideindex.app.settings.ClipboardHistoryCapacity
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.settings.clipboard.isClipboardMonitoringBackendReady
import com.slideindex.app.ui.settings.clipboard.rememberClipboardMonitoringUiState
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

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
    onClipboardHistoryFloatEnabledChange: (Boolean) -> Unit,
    onClipboardHistoryFloatEnabledLandscapeChange: (Boolean) -> Unit,
    onClipboardHistoryFloatLockPositionChange: (Boolean) -> Unit,
    onClipboardHistoryFloatHandleWidthChange: (Int) -> Unit,
    accessibilityGranted: Boolean,
    onRequestAccessibility: () -> Unit,
    onClipboardFloatEnabledChange: (Boolean) -> Unit,
    onClipboardFloatShowChipChange: (Boolean) -> Unit,
    onClipboardFloatPinPositionChange: (Boolean) -> Unit,
    onClipboardFloatEntryClickActionChange: (ClipboardFloatEntryClickAction) -> Unit,
    onClipboardFloatPasteHapticEnabledChange: (Boolean) -> Unit,
    onOpenClipboardFloatBlacklist: () -> Unit,
    onResetClipboardFloatLayout: () -> Unit,
    onStashPanelBackgroundBlurEnabledChange: (Boolean) -> Unit,
    onStashPanelBackgroundBlurRadiusDpChange: (Int) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onClearClipboardHistory: () -> Unit,
    onClearStash: () -> Unit,
) {
    val context = LocalContext.current
    var showClearClipboardDialog by remember { mutableStateOf(false) }
    var showClearStashDialog by remember { mutableStateOf(false) }
    val capacityPresets = ClipboardHistoryCapacity.presets
    val capacityIndex = capacityPresets.indexOf(settings.clipboardHistoryMaxEntries).let {
        if (it >= 0) it else capacityPresets.indexOf(100).coerceAtLeast(0)
    }
    val handleWidthPresets = HistoryFloatHandleWidth.presets
    val handleWidthIndex = handleWidthPresets.indexOf(settings.clipboardHistoryFloatHandleWidthDp).let {
        if (it >= 0) it else handleWidthPresets.indexOf(HistoryFloatHandleWidth.DEFAULT_DP).coerceAtLeast(0)
    }
    val overlayPermissionGranted = PermissionHelper.canDrawOverlays(context)
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

    var stashBlurExpanded by remember { mutableStateOf(settings.stashPanelBackgroundBlurEnabled) }
    LaunchedEffect(settings.stashPanelBackgroundBlurEnabled) {
        stashBlurExpanded = settings.stashPanelBackgroundBlurEnabled
    }

    var showShizukuReadLogsDialog by remember { mutableStateOf(false) }

    val appearanceSectionTitle = stringResource(R.string.stash_panel_section_appearance)
    val stashSectionTitle = stringResource(R.string.stash_clipboard_section_stash)
    val stashDesc = stringResource(R.string.stash_clipboard_stash_desc)
    val historySectionTitle = stringResource(R.string.stash_clipboard_section_history)
    val screenshotSectionTitle = stringResource(R.string.clipboard_screenshot_monitoring_section)
    val backgroundSectionTitle = stringResource(R.string.clipboard_background_monitoring_section)
    val floatSectionTitle = stringResource(R.string.clipboard_history_float_section)
    val clipboardFloatSectionTitle = stringResource(R.string.clipboard_float_section)
    val floatOverlayHint = stringResource(R.string.clipboard_history_float_overlay_permission_hint)
    val clipboardFloatA11yHint = stringResource(R.string.clipboard_float_a11y_hint)
    val clickActionEntries = ClipboardFloatEntryClickAction.entries
    val clickActionIndex = clickActionEntries.indexOf(settings.clipboardFloatEntryClickAction).let {
        if (it >= 0) it else 0
    }
    val modeEntries = ClipboardMonitoringMode.entries

    SettingsScreenScaffold(
        title = stringResource(R.string.stash_clipboard_settings_title),
        subtitle = stringResource(R.string.stash_clipboard_settings_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "stash-appearance-section",
            title = appearanceSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "stash-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("stash-blur-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.stash_panel_background_blur),
                            subtitle = stringResource(R.string.stash_panel_background_blur_desc),
                            checked = settings.stashPanelBackgroundBlurEnabled,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                stashBlurExpanded = enabled
                                onStashPanelBackgroundBlurEnabledChange(enabled)
                            },
                        )
                    },
                )
                if (stashBlurExpanded) {
                    add(
                        settingsCardScopeItem("stash-blur-radius") {
                            SettingsSliderRow(
                                title = stringResource(R.string.honeycomb_blur_strength),
                                value = settings.stashPanelBackgroundBlurRadiusDp.toFloat(),
                                valueRange = AppSettings.STASH_PANEL_BLUR_RADIUS_MIN_DP.toFloat()..
                                    AppSettings.STASH_PANEL_BLUR_RADIUS_MAX_DP.toFloat(),
                                steps = 16,
                                enabled = true,
                                label = stringResource(
                                    R.string.corner_gesture_zone_dp_value,
                                    settings.stashPanelBackgroundBlurRadiusDp,
                                ),
                                onValueChange = { onStashPanelBackgroundBlurRadiusDpChange(it.roundToInt()) },
                            )
                        },
                    )
                }
            },
        )
        settingsLazySmallTitle(
            key = "stash-section",
            title = stashSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(
            key = "stash-desc",
            text = stashDesc,
        )
        groupedCardItems(
            keyPrefix = "stash-clear",
            items = buildList {
                add(
                    settingsCardScopeItem("stash-clear-all") {
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
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "clipboard-history-section",
            title = historySectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "clipboard-history",
            items = buildList {
                add(
                    settingsCardScopeItem("history-capacity") {
                        SettingDropdownRow(
                            icon = { label -> Icon(Icons.Outlined.History, contentDescription = label) },
                            title = stringResource(R.string.clipboard_history_capacity_title),
                            items = capacityPresets.map { clipboardCapacityLabel(it) },
                            selectedIndex = capacityIndex,
                            onSelectedIndexChange = { onClipboardHistoryMaxEntriesChange(capacityPresets[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("clear-history") {
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
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "clipboard-screenshot-section",
            title = screenshotSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "clipboard-screenshot",
            items = buildList {
                add(
                    settingsCardScopeItem("screenshot-media-read-status") {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_media_read_status_title),
                            subtitle = stringResource(
                                if (mediaReadGranted) {
                                    R.string.clipboard_media_read_status_granted
                                } else {
                                    R.string.clipboard_media_read_status_denied
                                },
                            ),
                            onClick = {
                                if (!mediaReadGranted) {
                                    requestMediaReadPermission()
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("screenshot-monitoring") {
                        SettingSwitchRow(
                            title = stringResource(R.string.clipboard_screenshot_monitoring_title),
                            subtitle = stringResource(R.string.clipboard_screenshot_monitoring_desc),
                            checked = settings.clipboardScreenshotMonitoring,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    pendingScreenshotEnable = false
                                    onClipboardScreenshotMonitoringChange(false)
                                    return@SettingSwitchRow
                                }
                                if (mediaReadGranted) {
                                    onClipboardScreenshotMonitoringChange(true)
                                } else {
                                    pendingScreenshotEnable = true
                                    requestMediaReadPermission()
                                }
                            },
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "clipboard-background-section",
            title = backgroundSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "clipboard-background",
            items = buildList {
                add(
                    settingsCardScopeItem("background-monitoring") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.clipboard_background_monitoring_title),
                            subtitle = stringResource(R.string.clipboard_background_monitoring_desc),
                            checked = settings.clipboardBackgroundMonitoring,
                            enabled = true,
                            onCheckedChange = onClipboardMonitoringChange,
                        ) {
                            SettingDropdownRow(
                                title = stringResource(R.string.clipboard_background_monitoring_section),
                                subtitle = clipboardMonitoringModeDescription(settings.clipboardBackgroundMonitoringMode),
                                items = modeEntries.map { clipboardMonitoringModeLabel(it) },
                                selectedIndex = modeEntries.indexOf(settings.clipboardBackgroundMonitoringMode)
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { onClipboardMonitoringModeChange(modeEntries[it]) },
                            )
                        }
                    },
                )
            },
        )
        if (settings.clipboardBackgroundMonitoring) {
            groupedCardItems(
                keyPrefix = "clipboard-background-status",
                items = buildList {
                    val monitoringMode = settings.clipboardBackgroundMonitoringMode
                    val backendReady = isClipboardMonitoringBackendReady(monitoringMode, monitoringUi)
                    val readLogsGranted = ClipboardPermissionHelper.hasReadLogsPermission(context)
                    add(
                        settingsCardScopeItem("backend-status") {
                            SettingLinkRow(
                                title = stringResource(R.string.clipboard_monitor_backend_status_title),
                                subtitle = when {
                                    monitoringMode.usesRoot && monitoringUi.rootAvailable ->
                                        stringResource(R.string.clipboard_monitor_backend_root_ready)
                                    monitoringMode.usesRoot ->
                                        stringResource(R.string.clipboard_monitor_backend_root_missing)
                                    monitoringUi.shizukuGranted ->
                                        stringResource(R.string.clipboard_monitor_backend_shizuku_ready)
                                    else ->
                                        stringResource(R.string.clipboard_monitor_backend_shizuku_missing)
                                },
                                onClick = {},
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("overlay-status") {
                            SettingLinkRow(
                                title = stringResource(R.string.clipboard_monitor_overlay_status_title),
                                subtitle = if (monitoringUi.overlayGranted) {
                                    stringResource(R.string.clipboard_monitor_overlay_ready)
                                } else {
                                    stringResource(R.string.clipboard_monitor_overlay_missing)
                                },
                                onClick = if (!monitoringUi.overlayGranted) onOpenOverlayPermission else ({}),
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("service-status") {
                            SettingLinkRow(
                                title = stringResource(R.string.clipboard_monitor_service_status_title),
                                subtitle = if (monitoringUi.monitorRunning && backendReady) {
                                    stringResource(R.string.clipboard_monitor_service_running)
                                } else {
                                    stringResource(R.string.clipboard_monitor_service_stopped)
                                },
                                onClick = {},
                            )
                        },
                    )
                    if (monitoringMode == ClipboardMonitoringMode.SHIZUKU_LOGS && !readLogsGranted) {
                        add(
                            settingsCardScopeItem("read-logs-grant") {
                                SettingLinkRow(
                                    title = stringResource(R.string.clipboard_read_logs_shizuku_grant),
                                    subtitle = null,
                                    onClick = { showShizukuReadLogsDialog = true },
                                )
                            },
                        )
                    }
                },
            )
        }
        settingsLazySmallTitle(
            key = "clipboard-float-section",
            title = floatSectionTitle,
            sectionTop = true,
        )
        if (!overlayPermissionGranted) {
            settingsLazyHint(
                key = "clipboard-float-overlay-hint",
                text = floatOverlayHint,
            )
            groupedCardItems(
                keyPrefix = "clipboard-float-overlay",
                items = buildList {
                    add(
                        settingsCardScopeItem("overlay-permission") {
                            SettingLinkRow(
                                title = stringResource(R.string.clipboard_history_float_open_overlay_permission),
                                subtitle = null,
                                onClick = onOpenOverlayPermission,
                            )
                        },
                    )
                },
            )
        }
        groupedCardItems(
            keyPrefix = "clipboard-float",
            items = buildList {
                add(
                    settingsCardScopeItem("float-enabled") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.clipboard_history_float_enabled_title),
                            subtitle = stringResource(R.string.clipboard_history_float_enabled_desc),
                            checked = settings.clipboardHistoryFloatEnabled,
                            enabled = overlayPermissionGranted,
                            onCheckedChange = onClipboardHistoryFloatEnabledChange,
                        ) {
                            SettingSwitchRow(
                                title = stringResource(R.string.clipboard_history_float_enabled_landscape_title),
                                subtitle = stringResource(R.string.clipboard_history_float_enabled_landscape_desc),
                                checked = settings.clipboardHistoryFloatEnabledLandscape,
                                enabled = true,
                                onCheckedChange = onClipboardHistoryFloatEnabledLandscapeChange,
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.clipboard_history_float_lock_position_title),
                                subtitle = stringResource(R.string.clipboard_history_float_lock_position_desc),
                                checked = settings.clipboardHistoryFloatLockPosition,
                                enabled = true,
                                onCheckedChange = onClipboardHistoryFloatLockPositionChange,
                            )
                            SettingDropdownRow(
                                title = stringResource(R.string.clipboard_history_float_handle_width_title),
                                items = handleWidthPresets.map { "${it}dp" },
                                selectedIndex = handleWidthIndex,
                                onSelectedIndexChange = {
                                    onClipboardHistoryFloatHandleWidthChange(handleWidthPresets[it])
                                },
                            )
                        }
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "clipboard-floating-section",
            title = clipboardFloatSectionTitle,
            sectionTop = true,
        )
        if (!accessibilityGranted) {
            settingsLazyHint(
                key = "clipboard-float-a11y-hint",
                text = clipboardFloatA11yHint,
            )
        }
        groupedCardItems(
            keyPrefix = "clipboard-floating",
            items = buildList {
                add(
                    settingsCardScopeItem("float-enabled") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.clipboard_float_enabled_title),
                            subtitle = stringResource(R.string.clipboard_float_enabled_desc),
                            checked = settings.clipboardFloatEnabled,
                            enabled = accessibilityGranted,
                            onCheckedChange = { enabled ->
                                if (!accessibilityGranted) {
                                    onRequestAccessibility()
                                } else {
                                    onClipboardFloatEnabledChange(enabled)
                                }
                            },
                        ) {
                            SettingSwitchRow(
                                title = stringResource(R.string.clipboard_float_show_chip_title),
                                subtitle = stringResource(R.string.clipboard_float_show_chip_desc),
                                checked = settings.clipboardFloatShowChip,
                                enabled = true,
                                onCheckedChange = onClipboardFloatShowChipChange,
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.clipboard_float_pin_title),
                                subtitle = stringResource(R.string.clipboard_float_pin_desc),
                                checked = settings.clipboardFloatPanelPinPosition,
                                enabled = true,
                                onCheckedChange = onClipboardFloatPinPositionChange,
                            )
                            SettingDropdownRow(
                                title = stringResource(R.string.clipboard_float_click_action_title),
                                items = clickActionEntries.map { clipboardFloatClickActionLabel(it) },
                                selectedIndex = clickActionIndex,
                                onSelectedIndexChange = {
                                    onClipboardFloatEntryClickActionChange(clickActionEntries[it])
                                },
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.clipboard_float_paste_haptic_title),
                                subtitle = stringResource(R.string.clipboard_float_paste_haptic_desc),
                                checked = settings.clipboardFloatPasteHapticEnabled,
                                enabled = true,
                                onCheckedChange = onClipboardFloatPasteHapticEnabledChange,
                            )
                            SettingLinkRow(
                                title = stringResource(R.string.clipboard_float_app_blacklist),
                                subtitle = pluralStringResource(
                                    R.plurals.clipboard_float_app_blacklist_desc,
                                    settings.clipboardFloatBlockedPackages.size,
                                    settings.clipboardFloatBlockedPackages.size,
                                    settings.clipboardFloatPasteSuccessCount,
                                    settings.clipboardFloatPasteFailCount,
                                ),
                                onClick = onOpenClipboardFloatBlacklist,
                            )
                            SettingLinkRow(
                                title = stringResource(R.string.clipboard_float_reset_layout),
                                subtitle = null,
                                onClick = onResetClipboardFloatLayout,
                            )
                        }
                    },
                )
            },
        )
    }

    MiuixConfirmDialog(
        show = showClearClipboardDialog,
        onDismissRequest = { showClearClipboardDialog = false },
        title = stringResource(R.string.clipboard_clear_history_confirm_title),
        message = stringResource(R.string.clipboard_clear_history_confirm_message),
        onConfirm = onClearClipboardHistory,
    )

    MiuixConfirmDialog(
        show = showClearStashDialog,
        onDismissRequest = { showClearStashDialog = false },
        title = stringResource(R.string.stash_clear_all_confirm_title),
        message = stringResource(R.string.stash_clear_all_confirm_message),
        onConfirm = onClearStash,
    )

    ClipboardBackgroundReadLogsDialog(
        show = showShizukuReadLogsDialog,
        onDismiss = { showShizukuReadLogsDialog = false },
    )
}

@Composable
private fun ClipboardBackgroundReadLogsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    MiuixConfirmDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.clipboard_read_logs_shizuku_reminder_title),
        message = stringResource(R.string.clipboard_read_logs_shizuku_reminder_message),
        confirmText = stringResource(R.string.clipboard_read_logs_shizuku_reminder_continue),
        onConfirm = {
            ClipboardPermissionHelper.grantViaShizuku(context)
            onDismiss()
        },
    )
}

@Composable
fun SettingsCardScope.StashClipboardEntryCard(
    settings: ExtensionHubSettings,
    stashEntryCount: Int,
    outlinedLeadingIcons: Boolean = false,
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
        icon = { label ->
            Icon(HubLeadingIcons.stashClipboard(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.stash_clipboard_entry_title),
        subtitle = stringResource(R.string.stash_clipboard_entry_summary, stashPart, clipboardPart),
        onClick = onClick,
    )
}

@Composable
private fun clipboardFloatClickActionLabel(action: ClipboardFloatEntryClickAction): String = when (action) {
    ClipboardFloatEntryClickAction.PASTE -> stringResource(R.string.clipboard_float_click_action_paste)
    ClipboardFloatEntryClickAction.COPY -> stringResource(R.string.clipboard_float_click_action_copy)
    ClipboardFloatEntryClickAction.COPY_AND_PASTE -> stringResource(R.string.clipboard_float_click_action_copy_and_paste)
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
private fun clipboardCapacityLabel(capacity: Int): String =
    if (capacity == ClipboardHistoryCapacity.UNLIMITED) {
        stringResource(R.string.clipboard_history_capacity_unlimited)
    } else {
        capacity.toString()
    }
