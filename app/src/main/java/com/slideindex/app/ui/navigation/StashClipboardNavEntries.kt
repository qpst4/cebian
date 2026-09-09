package com.slideindex.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.ClipboardFloatSettingsScreen
import com.slideindex.app.ui.ClipboardHistorySettingsScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.StashClipboardSettingsScreen
import com.slideindex.app.ui.StashPanelSettingsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.viewmodel.StashClipboardSettingsViewModel

fun NavEntryBuilder.stashClipboardNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.StashClipboard> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val clipboardEntryCount by viewModel.clipboardHistoryRepository.entryCount.collectAsStateWithLifecycle()
        val stashEntries by viewModel.stashRepository.entries.collectAsStateWithLifecycle()
        StashClipboardSettingsScreen(
            settings = settings,
            clipboardEntryCount = clipboardEntryCount,
            stashEntryCount = stashEntries.size,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenClipboardHistory = { ctx.navigate(AppNavKey.ClipboardHistorySettings) },
            onOpenStashPanel = { ctx.navigate(AppNavKey.StashPanelSettings) },
            onOpenClipboardFloat = { ctx.navigate(AppNavKey.ClipboardFloatSettings) },
            onClearStash = viewModel::clearStash,
        )
        LaunchedEffect(permissions.overlayGranted) {
            if (permissions.overlayGranted) {
                viewModel.syncHistoryFloatFromSettings()
            }
        }
        LaunchedEffect(permissions.accessibilityGranted, settings.clipboardFloatEnabled) {
            if (permissions.accessibilityGranted) {
                viewModel.syncClipboardFloatFromSettings()
            }
        }
    }

    hiltEntry<AppNavKey.ClipboardHistorySettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val context = LocalContext.current
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val clipboardEntryCount by viewModel.clipboardHistoryRepository.entryCount.collectAsStateWithLifecycle()
        ClipboardHistorySettingsScreen(
            settings = settings,
            clipboardEntryCount = clipboardEntryCount,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onClipboardHistoryMaxEntriesChange = viewModel::setClipboardHistoryMaxEntries,
            onClearClipboardHistory = viewModel::clearClipboardHistory,
            onClipboardScreenshotMonitoringChange = viewModel::setClipboardScreenshotMonitoring,
            onClipboardMonitoringChange = viewModel::setClipboardBackgroundMonitoring,
            onClipboardMonitoringModeChange = viewModel::setClipboardBackgroundMonitoringMode,
            onOpenOverlayPermission = {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            },
        )
    }

    hiltEntry<AppNavKey.StashPanelSettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val context = LocalContext.current
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        StashPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onStashPanelBackgroundBlurEnabledChange = viewModel::setStashPanelBackgroundBlurEnabled,
            onStashPanelBackgroundBlurRadiusDpChange = viewModel::setStashPanelBackgroundBlurRadiusDp,
            onClipboardHistoryFloatEnabledChange = viewModel::setClipboardHistoryFloatEnabled,
            onClipboardHistoryFloatEnabledLandscapeChange = viewModel::setClipboardHistoryFloatEnabledLandscape,
            onClipboardHistoryFloatLockPositionChange = viewModel::setClipboardHistoryFloatLockPosition,
            onClipboardHistoryFloatHandleWidthChange = viewModel::setClipboardHistoryFloatHandleWidthDp,
            onOpenOverlayPermission = {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            },
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatSettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ClipboardFloatSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onRequestAccessibility = { ctx.openAccessibilitySettings() },
            onClipboardFloatEnabledChange = viewModel::setClipboardFloatEnabled,
            onClipboardFloatShowChipChange = viewModel::setClipboardFloatShowChip,
            onClipboardFloatPinPositionChange = viewModel::setClipboardFloatPinPosition,
            onClipboardFloatEntryClickActionChange = viewModel::setClipboardFloatEntryClickAction,
            onClipboardFloatListStyleChange = viewModel::setClipboardFloatListStyle,
            onClipboardFloatPasteHapticEnabledChange = viewModel::setClipboardFloatPasteHapticEnabled,
            onClipboardFloatAlphaChange = viewModel::setClipboardFloatAlpha,
            onClipboardFloatAutoDimWhenUnfocusedChange = viewModel::setClipboardFloatAutoDimWhenUnfocused,
            onClipboardFloatAutoCloseSecondsChange = viewModel::setClipboardFloatAutoCloseSeconds,
            onOpenClipboardFloatBlacklist = { ctx.navigate(AppNavKey.ClipboardFloatBlacklist) },
            onResetClipboardFloatLayout = viewModel::resetClipboardFloatLayout,
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatBlacklist> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = settings.clipboardFloatBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardFloatSettings) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ClipboardFloatBlacklistPick) },
            onRemoveBlacklistedApp = viewModel::removeClipboardFloatBlockedPackage,
            titleRes = R.string.clipboard_float_app_blacklist,
            descriptionRes = R.string.clipboard_float_app_blacklist_page_desc,
            blockedSectionTitleRes = R.string.clipboard_float_blacklist_section_blocked,
            emptyRes = R.string.clipboard_float_blacklist_empty,
            removeActionDescriptionRes = R.string.clipboard_float_blacklist_remove,
            addSectionTitleRes = R.string.clipboard_float_blacklist_section_add,
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatBlacklistPick> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.clipboard_float_blacklist_section_add,
            excludePackageNames = settings.clipboardFloatBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardFloatBlacklist) },
            onSelectApp = { app ->
                viewModel.addClipboardFloatBlockedPackage(app.packageName)
                ctx.navigateBackTo(AppNavKey.ClipboardFloatBlacklist)
            },
        )
    }
}
