package com.slideindex.app.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import com.slideindex.app.ui.DiagnosticLogScreen
import com.slideindex.app.ui.ExtensionAboutScreen
import com.slideindex.app.ui.ExtensionHubScreen
import com.slideindex.app.ui.ExternalInvocationHelpScreen
import com.slideindex.app.ui.FreezerAppsPickerScreen
import com.slideindex.app.ui.FreezerHomeScreen
import com.slideindex.app.ui.LicenseTextScreen
import com.slideindex.app.ui.MissingGesturePermissionsScreen
import com.slideindex.app.ui.PrivacyPolicyScreen
import com.slideindex.app.ui.SettingsBackupScreen
import com.slideindex.app.ui.ThirdPartyNoticesScreen
import com.slideindex.app.ui.viewmodel.DiagnosticLogViewModel
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel

fun NavEntryBuilder.extensionHubNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.ExtensionHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: ExtensionHubViewModel = hiltViewModel()
        val hubSettings by viewModel.extensionHubSettings.collectAsStateWithLifecycle()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val stashEntryCount by viewModel.stashEntryCount.collectAsStateWithLifecycle()
        ExtensionHubScreen(
            settings = hubSettings,
            gestureActive = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            stashEntryCount = stashEntryCount,
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
            onOpenLayoutSettings = { ctx.navigate(AppNavKey.HomeLayout) },
            onOpenQuickLauncher = { ctx.navigate(AppNavKey.QuickLauncher) },
            onOpenHoneycombLauncher = { ctx.navigate(AppNavKey.HoneycombLauncher) },
            onOpenHolographicLauncher = { ctx.navigate(AppNavKey.HolographicLauncherSettings) },
            onOpenActivityShortcuts = { ctx.navigate(AppNavKey.ActivityShortcuts) },
            onOpenExternalInvocations = { ctx.navigate(AppNavKey.ExtensionExternalInvocations) },
            onOpenShellCommands = { ctx.navigate(AppNavKey.ShellCommands) },
            onOpenWidgetPanel = { ctx.navigate(AppNavKey.WidgetPanel) },
            onOpenFloatingPointer = { ctx.navigate(AppNavKey.FloatingPointer) },
            onOpenStashClipboard = { ctx.navigate(AppNavKey.StashClipboard) },
            onOpenSearchPanel = { ctx.navigate(AppNavKey.SearchPanel) },
            onOpenFreezer = { ctx.navigate(AppNavKey.ExtensionFreezer) },
            onOpenSettingsBackup = { ctx.navigate(AppNavKey.ExtensionBackup) },
            onOpenNativeEnginePacks = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onOpenDiagnosticLogs = { ctx.navigate(AppNavKey.ExtensionDiagnosticLogs) },
            onOpenAbout = { ctx.navigate(AppNavKey.ExtensionAbout) },
        )
    }

    hiltEntry<AppNavKey.ExtensionFreezer> {
        FreezerHomeScreen(
            settingsRepository = ctx.deps.settingsRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenManageApps = { ctx.navigate(AppNavKey.ExtensionFreezerApps) },
        )
    }

    hiltEntry<AppNavKey.ExtensionFreezerApps> {
        FreezerAppsPickerScreen(
            settingsRepository = ctx.deps.settingsRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionFreezer) },
        )
    }

    hiltEntry<AppNavKey.ExtensionExternalInvocations> {
        ExternalInvocationHelpScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    hiltEntry<AppNavKey.ExtensionAbout> {
        val updateViewModel: com.slideindex.app.update.UpdateViewModel = hiltViewModel(ctx.activity)
        val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
        ExtensionAboutScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPrivacyPolicy = { ctx.navigate(AppNavKey.ExtensionPrivacy) },
            onOpenThirdPartyNotices = { ctx.navigate(AppNavKey.ExtensionThirdPartyNotices) },
            onCheckUpdate = updateViewModel::checkManually,
            autoCheckUpdate = updateUiState.autoCheckUpdate,
            onAutoCheckUpdateChange = updateViewModel::setAutoCheckUpdate,
        )
    }

    hiltEntry<AppNavKey.ExtensionDiagnosticLogs> {
        val viewModel: DiagnosticLogViewModel = hiltViewModel()
        DiagnosticLogScreen(
            viewModel = viewModel,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    hiltEntry<AppNavKey.ExtensionThirdPartyNotices> {
        ThirdPartyNoticesScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionAbout) },
            onOpenLicenseText = { fileName ->
                ctx.navigate(AppNavKey.ExtensionLicenseText(fileName))
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionLicenseText> { key ->
        LicenseTextScreen(
            assetFileName = key.assetFileName,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionThirdPartyNotices) },
        )
    }

    hiltEntry<AppNavKey.ExtensionPrivacy> {
        PrivacyPolicyScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackup> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val importPreviewState by viewModel.importPreviewState.collectAsStateWithLifecycle()
        val navigateToMissingPermissions by viewModel.navigateToMissingPermissions.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var missingCount by remember {
            mutableIntStateOf(GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, settings) {
            missingCount = GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    missingCount = GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        LaunchedEffect(navigateToMissingPermissions) {
            if (navigateToMissingPermissions) {
                ctx.navigate(AppNavKey.ExtensionMissingPermissions)
                viewModel.consumeNavigateToMissingPermissions()
            }
        }
        SettingsBackupScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onExport = viewModel::exportSettings,
            onImport = viewModel::previewImport,
            importPreviewState = importPreviewState,
            onDismissPreview = viewModel::dismissPreview,
            onConfirmImport = viewModel::confirmImport,
            missingPermissionCount = missingCount,
            onOpenMissingPermissions = { ctx.navigate(AppNavKey.ExtensionMissingPermissions) },
        )
    }

    hiltEntry<AppNavKey.ExtensionMissingPermissions> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MissingGesturePermissionsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionBackup) },
        )
    }
}
