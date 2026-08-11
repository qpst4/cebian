package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.ui.ExtensionHubScreen
import com.slideindex.app.ui.FloatingPointerEdgeActionsSettingsScreen
import com.slideindex.app.ui.FloatingPointerEdgeSideSettingsScreen
import com.slideindex.app.ui.FloatingPointerJoystickSettingsScreen
import com.slideindex.app.ui.FloatingPointerPointerSettingsScreen
import com.slideindex.app.ui.FloatingPointerRadialMenuSettingsScreen
import com.slideindex.app.ui.FloatingPointerSettingsScreen
import com.slideindex.app.ui.ExtensionAboutScreen
import com.slideindex.app.ui.ThirdPartyNoticesScreen
import com.slideindex.app.ui.LicenseTextScreen
import com.slideindex.app.ui.FloatBallAppearanceSettingsScreen
import com.slideindex.app.ui.FloatBallStyleSettingsScreen
import com.slideindex.app.ui.FloatBallGestureSettingsScreen
import com.slideindex.app.ui.FloatBallPickSettingsScreen
import com.slideindex.app.ui.ShareImageOcrHistoryScreen
import com.slideindex.app.ui.StashClipboardSettingsScreen
import com.slideindex.app.ui.SearchPanelAppSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelContactSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelFileSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelSettingsScreen
import com.slideindex.app.ui.SearchPanelSystemSettingsSearchSettingsScreen
import com.slideindex.app.ui.FloatBallSettingsScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.HoneycombDisplaySettingsScreen
import com.slideindex.app.ui.HoneycombLauncherEditorScreen
import com.slideindex.app.ui.PrivacyPolicyScreen
import com.slideindex.app.ui.SettingsBackupScreen
import com.slideindex.app.ui.MissingGesturePermissionsScreen
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.PointerSwipeConfigScreen
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.SearchEngineEditorCategory
import com.slideindex.app.ui.SearchEngineEditorScreen
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.ui.ActivityShortcutScreen
import com.slideindex.app.ui.ActivityShortcutPresetsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppShortcutScreen
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.ShellCommandEditorScreen
import com.slideindex.app.ui.ShellOutputHistoryScreen
import com.slideindex.app.ui.ShellResultScreen
import com.slideindex.app.util.ShellCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.slideindex.app.ui.WidgetPanelSettingsScreen
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.FloatBallTranslationSettingsScreen
import com.slideindex.app.ui.SearchEnginePreviewSortScreen
import com.slideindex.app.ui.SearchEngineSettingsScreen
import com.slideindex.app.ui.ImageSearchEngineDetailScreen
import com.slideindex.app.ui.ImageSearchEngineSettingsScreen
import com.slideindex.app.ui.resolveImageSearchEngine
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel
import com.slideindex.app.ui.TranslateModelSettingsScreen
import com.slideindex.app.ui.NativeEnginePackSettingsScreen
import com.slideindex.app.ui.OcrModelSettingsScreen
import com.slideindex.app.ui.viewmodel.NativeEnginePackSettingsViewModel
import com.slideindex.app.ui.viewmodel.OcrModelSettingsViewModel
import com.slideindex.app.ui.viewmodel.TranslateSettingsViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel
import com.slideindex.app.ui.viewmodel.ShellCommandViewModel
import com.slideindex.app.ui.viewmodel.ShareImageOcrHistoryViewModel
import com.slideindex.app.ui.viewmodel.StashClipboardSettingsViewModel

fun EntryProviderScope<AppNavKey>.extensionNavEntries(ctx: MainNavContext) {
    layoutSettingsNavEntries(ctx)

    entry<AppNavKey.ExtensionHub> {
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
            onOpenActivityShortcuts = { ctx.navigate(AppNavKey.ActivityShortcuts) },
            onOpenShellCommands = { ctx.navigate(AppNavKey.ShellCommands) },
            onOpenWidgetPanel = { ctx.navigate(AppNavKey.WidgetPanel) },
            onOpenFloatingPointer = { ctx.navigate(AppNavKey.FloatingPointer) },
            onOpenStashClipboard = { ctx.navigate(AppNavKey.StashClipboard) },
            onOpenSearchPanel = { ctx.navigate(AppNavKey.SearchPanel) },
            onOpenSettingsBackup = { ctx.navigate(AppNavKey.ExtensionBackup) },
            onOpenAbout = { ctx.navigate(AppNavKey.ExtensionAbout) },
        )
    }

    entry<AppNavKey.ExtensionAbout> {
        val updateViewModel: com.slideindex.app.update.UpdateViewModel = hiltViewModel(ctx.activity)
        val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
        ExtensionAboutScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPrivacyPolicy = { ctx.navigate(AppNavKey.ExtensionPrivacy) },
            onOpenThirdPartyNotices = { ctx.navigate(AppNavKey.ExtensionThirdPartyNotices) },
            onOpenNativeEnginePacks = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onCheckUpdate = updateViewModel::checkManually,
            autoCheckUpdate = updateUiState.autoCheckUpdate,
            onAutoCheckUpdateChange = updateViewModel::setAutoCheckUpdate,
        )
    }

    entry<AppNavKey.ExtensionThirdPartyNotices> {
        ThirdPartyNoticesScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionAbout) },
            onOpenLicenseText = { fileName ->
                ctx.navigate(AppNavKey.ExtensionLicenseText(fileName))
            },
        )
    }

    entry<AppNavKey.ExtensionLicenseText> { key ->
        LicenseTextScreen(
            assetFileName = key.assetFileName,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionThirdPartyNotices) },
        )
    }

    entry<AppNavKey.ExtensionPrivacy> {
        PrivacyPolicyScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    entry<AppNavKey.ExtensionBackup> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val importPreviewState by viewModel.importPreviewState.collectAsStateWithLifecycle()
        val navigateToMissingPermissions by viewModel.navigateToMissingPermissions.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val missingCount = remember(settings) {
            GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size
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

    entry<AppNavKey.ExtensionMissingPermissions> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MissingGesturePermissionsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionBackup) },
        )
    }

    entry<AppNavKey.QuickLauncher> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        QuickLauncherEditorScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSavePanels = viewModel::setQuickLauncherPanels,
            onDisplayChange = viewModel::setQuickLauncherDisplaySettings,
        )
    }

    entry<AppNavKey.HoneycombLauncher> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HoneycombLauncherEditorScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveItems = viewModel::setHoneycombLauncherItems,
            onOpenDisplaySettings = { ctx.navigate(AppNavKey.HoneycombDisplaySettings) },
        )
    }

    entry<AppNavKey.HoneycombDisplaySettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HoneycombDisplaySettingsScreen(
            display = settings.honeycombDisplay,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncher) },
            onDisplayChange = viewModel::setHoneycombDisplaySettings,
        )
    }

    entry<AppNavKey.ActivityShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveShortcuts = viewModel::setActivityShortcuts,
            onAdd = { ctx.navigate(AppNavKey.ActivityShortcutPickApp) },
            onAddAppShortcut = { ctx.navigate(AppNavKey.ActivityShortcutPickAppShortcut) },
            onOpenPresets = { ctx.navigate(AppNavKey.ActivityShortcutPresets) },
        )
    }

    entry<AppNavKey.ActivityShortcutPresets> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPresetsScreen(
            settings = settings,
            shortcuts = settings.activityShortcuts,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSaveShortcuts = viewModel::setActivityShortcuts,
        )
    }

    entry<AppNavKey.ActivityShortcutPickApp> {
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.ActivityShortcutPickActivity(app.packageName))
            },
        )
    }

    entry<AppNavKey.ActivityShortcutPickAppShortcut> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickAppShortcutScreen(
            existingIdentityKeys = settings.activityShortcuts.map { it.identityKey() }.toSet(),
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onAddShortcut = { shortcut ->
                if (settings.activityShortcuts.none { it.identityKey() == shortcut.identityKey() }) {
                    viewModel.setActivityShortcuts(settings.activityShortcuts + shortcut)
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    entry<AppNavKey.ActivityShortcutPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val candidate = ActivityShortcut.component(
                    label = activity.label,
                    packageName = activity.packageName,
                    activityClassName = activity.className,
                )
                val duplicate = settings.activityShortcuts.any {
                    it.identityKey() == candidate.identityKey()
                }
                if (!duplicate) {
                    viewModel.setActivityShortcuts(
                        settings.activityShortcuts + candidate,
                    )
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    entry<AppNavKey.ShellCommands> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ShellCommandPanelScreen(
            settings = settings,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveCommands = viewModel::setShellCommands,
            onRequestShizuku = { ctx.requestShizuku() },
            shellViewModel = shellViewModel,
            onOpenHistory = { ctx.navigate(AppNavKey.ShellCommandHistory) },
            onOpenEditor = { commandId ->
                ctx.navigate(AppNavKey.ShellCommandEditor(commandId.orEmpty()))
            },
            onOpenResult = { ctx.navigate(AppNavKey.ShellCommandResult) },
        )
    }

    entry<AppNavKey.ShellCommandHistory> {
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        ShellOutputHistoryScreen(
            repository = shellViewModel.historyRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ShellCommands) },
            onClear = shellViewModel::clearHistory,
        )
    }

    entry<AppNavKey.ShellCommandEditor> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val initial = key.commandId.takeIf { it.isNotEmpty() }
            ?.let { id -> settings.shellCommands.find { it.id == id } }
        ShellCommandEditorScreen(
            initial = initial,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ShellCommands) },
            onSave = { draft ->
                val current = settings.shellCommands
                val updated = if (current.any { it.id == draft.id }) {
                    current.map { if (it.id == draft.id) draft else it }
                } else {
                    current + draft
                }
                viewModel.setShellCommands(updated)
                ctx.navigateBackTo(AppNavKey.ShellCommands)
            },
            onDelete = initial?.let { existing ->
                {
                    viewModel.setShellCommands(
                        settings.shellCommands.filter { it.id != existing.id },
                    )
                    ctx.navigateBackTo(AppNavKey.ShellCommands)
                }
            },
            onTest = { command, callback ->
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        ShellCommandRunner.execute(context, command)
                    }
                    callback(outcome.exitCode, outcome.output)
                }
            },
        )
    }

    entry<AppNavKey.ShellCommandResult> {
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        val context = LocalContext.current
        val pending = shellViewModel.pendingResult
        if (pending == null) {
            LaunchedEffect(Unit) {
                ctx.navigateBackTo(AppNavKey.ShellCommands)
            }
            return@entry
        }
        ShellResultScreen(
            label = pending.label,
            command = pending.command,
            exitCode = pending.exitCode,
            output = pending.output,
            onBack = {
                shellViewModel.clearPendingResult()
                ctx.navigateBackTo(AppNavKey.ShellCommands)
            },
            onCopy = { copyShellOutputToClipboard(context, pending.output) },
        )
    }

    entry<AppNavKey.WidgetPanel> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        WidgetPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSavePages = viewModel::setWidgetPanelPages,
            onBlurEnabledChange = viewModel::setWidgetPanelBlurEnabled,
            onWidthFractionChange = viewModel::setWidgetPanelWidthFraction,
        )
    }

    entry<AppNavKey.StashClipboard> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val clipboardEntries by viewModel.clipboardHistoryRepository.entries.collectAsStateWithLifecycle()
        val stashEntries by viewModel.stashRepository.entries.collectAsStateWithLifecycle()
        StashClipboardSettingsScreen(
            settings = settings,
            clipboardEntryCount = clipboardEntries.size,
            stashEntryCount = stashEntries.size,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onClipboardMonitoringChange = viewModel::setClipboardBackgroundMonitoring,
            onClipboardMonitoringModeChange = viewModel::setClipboardBackgroundMonitoringMode,
            onClipboardScreenshotMonitoringChange = viewModel::setClipboardScreenshotMonitoring,
            onClipboardHistoryMaxEntriesChange = viewModel::setClipboardHistoryMaxEntries,
            onClearClipboardHistory = viewModel::clearClipboardHistory,
            onClearStash = viewModel::clearStash,
        )
    }

    entry<AppNavKey.SearchPanel> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val searchHistoryEntryCount by viewModel.searchHistoryEntryCount.collectAsStateWithLifecycle()
        SearchPanelSettingsScreen(
            settings = settings,
            searchHistoryEntryCount = searchHistoryEntryCount,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSetDefaultEngineId = viewModel::setDefaultEngineId,
            onSetSearchPanelInputBehavior = viewModel::setSearchPanelInputBehavior,
            onSetSearchPanelContactSearchEnabled = viewModel::setSearchPanelContactSearchEnabled,
            onSetSearchPanelFileSearchEnabled = viewModel::setSearchPanelFileSearchEnabled,
            onSetSearchPanelAppSearchEnabled = viewModel::setSearchPanelAppSearchEnabled,
            onSetSearchPanelSettingsSearchEnabled = viewModel::setSearchPanelSettingsSearchEnabled,
            onSetSearchPanelSectionAliases = viewModel::setSearchPanelSectionAliases,
            onOpenAppSearchSettings = { ctx.navigate(AppNavKey.SearchPanelAppSearch) },
            onOpenContactSearchSettings = { ctx.navigate(AppNavKey.SearchPanelContactSearch) },
            onOpenFileSearchSettings = { ctx.navigate(AppNavKey.SearchPanelFileSearch) },
            onOpenSystemSettingsSearchSettings = { ctx.navigate(AppNavKey.SearchPanelSystemSettingsSearch) },
            onSetSearchPanelPresentationMode = viewModel::setSearchPanelPresentationMode,
            onSetSearchPanelBarPosition = viewModel::setSearchPanelBarPosition,
            onSetSearchPanelListOrder = viewModel::setSearchPanelListOrder,
            onSetSearchPanelAppDisplayStyle = viewModel::setSearchPanelAppDisplayStyle,
            onSetSearchPanelCalculatorEnabled = viewModel::setSearchPanelCalculatorEnabled,
            onSetSearchPanelWebSuggestionsEnabled = viewModel::setSearchPanelWebSuggestionsEnabled,
            onSetSearchPanelWebSuggestionsCount = viewModel::setSearchPanelWebSuggestionsCount,
            onSetSearchPanelHistoryMaxEntries = viewModel::setSearchPanelHistoryMaxEntries,
            onClearSearchHistory = viewModel::clearSearchHistory,
            onSetSearchPanelBackgroundStyle = viewModel::setSearchPanelBackgroundStyle,
            onSetSearchPanelBlurRadiusDp = viewModel::setSearchPanelBlurRadiusDp,
            onSetSearchPanelDimPercent = viewModel::setSearchPanelDimPercent,
            onOpenPreviewSort = { ctx.navigate(AppNavKey.FloatBallSearchEnginePreviewSort) },
            onOpenTextSearchEngines = { ctx.navigate(AppNavKey.FloatBallSearchEngine) },
            onOpenImageSearchEngines = { ctx.navigate(AppNavKey.FloatBallImageSearchEngine) },
        )
    }

    entry<AppNavKey.SearchPanelAppSearch> {
        SearchPanelAppSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    entry<AppNavKey.SearchPanelContactSearch> {
        SearchPanelContactSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    entry<AppNavKey.SearchPanelSystemSettingsSearch> {
        SearchPanelSystemSettingsSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    entry<AppNavKey.SearchPanelFileSearch> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        SearchPanelFileSearchSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
            onSetFileTypesEnabled = viewModel::setSearchPanelFileTypesEnabled,
            onSetShowFolders = viewModel::setSearchPanelFileShowFolders,
            onSetShowSystemFiles = viewModel::setSearchPanelFileShowSystemFiles,
            onSetFilePreviewsEnabled = viewModel::setSearchPanelFilePreviewsEnabled,
            onSetFolderWhitelist = viewModel::setSearchPanelFileFolderWhitelist,
            onSetFolderBlacklist = viewModel::setSearchPanelFileFolderBlacklist,
        )
    }

    floatBallNavEntries(ctx)


    entry<AppNavKey.FloatingPointer> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val areaPreviewEnabled = ctx.collectAreaPreviewEnabled()
        FloatingPointerSettingsScreen(
            settings = settings,
            areaPreviewEnabled = areaPreviewEnabled,
            previewAccessibilityGranted = permissions.accessibilityGranted,
            onAreaPreviewEnabledChange = { ctx.setFloatingPointerAreaPreviewEnabled(it) },
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPointerSettings = { ctx.navigate(AppNavKey.FloatingPointerPointer) },
            onOpenJoystickSettings = { ctx.navigate(AppNavKey.FloatingPointerJoystick) },
            onOpenRadialMenuSettings = { ctx.navigate(AppNavKey.FloatingPointerRadialMenu) },
            onOpenEdgeActionsSettings = { ctx.navigate(AppNavKey.FloatingPointerEdgeActions) },
            onPointerSensitivityChange = viewModel::setFloatingPointerSensitivityFraction,
        )
    }

    entry<AppNavKey.FloatingPointerPointer> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerPointerSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onPointerDiameterChange = viewModel::setFloatingPointerPointerDiameterPx,
            onRingThicknessChange = viewModel::setFloatingPointerRingThicknessPx,
            onDotDiameterChange = viewModel::setFloatingPointerDotDiameterPx,
            onRingColorChange = viewModel::setFloatingPointerRingColor,
            onFillColorChange = viewModel::setFloatingPointerFillColor,
            onDotColorChange = viewModel::setFloatingPointerDotColor,
            onClickVisualFeedbackChange = viewModel::setFloatingPointerClickVisualFeedbackEnabled,
            onClickHapticChange = viewModel::setFloatingPointerClickHapticEnabled,
            onRippleColorChange = viewModel::setFloatingPointerRippleColor,
            onRippleSizeChange = viewModel::setFloatingPointerRippleSizeDp,
            onRippleDurationChange = viewModel::setFloatingPointerRippleDurationMs,
            onTrailTypeChange = viewModel::setFloatingPointerTrailType,
            onTrailDurationChange = viewModel::setFloatingPointerTrailDurationMs,
            onTrailColorChange = viewModel::setFloatingPointerTrailColor,
            onHideWhenReleasedChange = viewModel::setFloatingPointerHideWhenJoystickReleased,
            onPointerDesignChange = { design -> viewModel.setFloatingPointerDesignId(design.id) },
            onResetVisualDefaults = viewModel::resetFloatingPointerVisualDefaults,
        )
    }

    entry<AppNavKey.FloatingPointerJoystick> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerJoystickSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onJoystickDiameterChange = viewModel::setFloatingPointerJoystickDiameterPx,
            onInnerColorChange = viewModel::setFloatingPointerJoystickInnerColor,
            onOuterColorChange = viewModel::setFloatingPointerJoystickOuterColor,
            onGradientRadiusChange = viewModel::setFloatingPointerJoystickGradientRadiusFraction,
            onHideOnOutsideClickChange = viewModel::setFloatingPointerHideOnOutsideClick,
            onHideOnQuickSwipeChange = viewModel::setFloatingPointerHideOnQuickSwipe,
            onHideWhenIdleChange = viewModel::setFloatingPointerHideWhenIdle,
            onIdleDelayChange = viewModel::setFloatingPointerIdleHideDelayMs,
            onClickDistanceThresholdChange = viewModel::setFloatingPointerClickDistanceThresholdDp,
            onResetVisualDefaults = viewModel::resetFloatingPointerJoystickVisualDefaults,
            onResetBehaviorDefaults = viewModel::resetFloatingPointerJoystickBehaviorDefaults,
        )
    }

    entry<AppNavKey.FloatingPointerRadialMenu> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerRadialMenuSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onAlwaysVisibleChange = viewModel::setFloatingPointerRadialAlwaysVisible,
            onLongPressMsChange = viewModel::setFloatingPointerRadialLongPressMs,
            onOpenLongPressActionPick = {
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialActionPick(FloatingPointerRadialActionTarget.LONG_PRESS),
                )
            },
            onOpenSlotActionPick = { slotIndex ->
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialActionPick(
                        target = FloatingPointerRadialActionTarget.SLOT,
                        slotIndex = slotIndex,
                    ),
                )
            },
            onOpenShellCommand = { slotIndex, command ->
                ctx.navigate(AppNavKey.FloatingPointerRadialShellCommand(slotIndex, command))
            },
            onOpenSwipeConfig = { slotIndex ->
                ctx.navigate(AppNavKey.FloatingPointerRadialSwipeConfig(slotIndex))
            },
            onSlotActionChange = viewModel::setFloatingPointerRadialSlotAction,
            onOuterDiameterChange = viewModel::setFloatingPointerRadialOuterDiameterPx,
            onInnerDiameterChange = viewModel::setFloatingPointerRadialInnerDiameterPx,
            onOuterColorChange = viewModel::setFloatingPointerRadialOuterColor,
            onInnerColorChange = viewModel::setFloatingPointerRadialInnerColor,
            onDividerThicknessChange = viewModel::setFloatingPointerRadialDividerThicknessPx,
            onDividerColorChange = viewModel::setFloatingPointerRadialDividerColor,
            onIconSizeFractionChange = viewModel::setFloatingPointerRadialIconSizeFraction,
            onIconColorChange = viewModel::setFloatingPointerRadialIconColor,
            onResetDesignDefaults = viewModel::resetFloatingPointerRadialDesignDefaults,
        )
    }

    entry<AppNavKey.FloatingPointerRadialActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        val current = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> settings.floatingPointerJoystickLongPressAction
            FloatingPointerRadialActionTarget.SLOT ->
                settings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        }
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            includePointerGestureActions = true,
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                if (action is GestureAction.FloatingPointer) return@GestureActionPickerScreen
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> {
                        viewModel.setFloatingPointerJoystickLongPressAction(action)
                        ctx.navigateBackTo(returnKey)
                    }
                    FloatingPointerRadialActionTarget.SLOT -> {
                        if (action is GestureAction.SimulatePointerSwipe) {
                            ctx.navigate(AppNavKey.FloatingPointerRadialSwipeConfig(key.slotIndex))
                        } else {
                            viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                            ctx.navigateBackTo(returnKey)
                        }
                    }
                }
            },
        )
    }

    entry<AppNavKey.FloatingPointerRadialShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = overlaySettings.toMinimalAppSettings().shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    entry<AppNavKey.FloatingPointerRadialSwipeConfig> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        val current = settings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        val initialConfig = (current as? GestureAction.SimulatePointerSwipe)?.config
            ?: PointerSwipeConfig.DEFAULT
        PointerSwipeConfigScreen(
            initialConfig = initialConfig,
            onBack = { ctx.navigateBackTo(returnKey) },
            onConfirm = { config ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    GestureAction.SimulatePointerSwipe(config),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    entry<AppNavKey.FloatingPointerEdgeActions> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerEdgeActionsSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onThresholdChange = viewModel::setFloatingPointerEdgeThresholdDp,
            onPreviewSensitivityChange = viewModel::setFloatingPointerEdgePreviewSensitivity,
            onPreviewGlowSizeChange = viewModel::setFloatingPointerEdgePreviewGlowSize,
            onPreviewShowIconChange = viewModel::setFloatingPointerEdgePreviewShowIcon,
            onVisualColorChange = viewModel::setFloatingPointerEdgeVisualColor,
            onOpenSideSettings = { side ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeSideSettings(side.toNavSide()))
            },
            onResetDefaults = viewModel::resetFloatingPointerEdgeDefaults,
        )
    }

    entry<AppNavKey.FloatingPointerEdgeSideSettings> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val side = key.side.toFloatingPointerEdgeSide()
        FloatingPointerEdgeSideSettingsScreen(
            side = side,
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeActions) },
            onEnabledChange = { enabled -> viewModel.setFloatingPointerEdgeBarEnabled(side, enabled) },
            onOpenActionPick = { slotIndex ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeActionPick(key.side, slotIndex))
            },
            onOpenShellCommand = { slotIndex, command ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeShellCommand(key.side, slotIndex, command))
            },
            onAddSlot = { viewModel.addFloatingPointerEdgeBarSlot(side) },
            onRemoveSlot = { slotIndex -> viewModel.removeFloatingPointerEdgeBarSlot(side, slotIndex) },
        )
    }

    entry<AppNavKey.FloatingPointerEdgeActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeSideSettings(key.side)
        val current = settings.floatingPointerEdgeActionsConfig
            .bar(side)
            .layoutSlots()
            .getOrNull(key.slotIndex)
            ?.action
            ?: GestureAction.None
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            includePointerGestureActions = false,
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    entry<AppNavKey.FloatingPointerEdgeShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeSideSettings(key.side)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = overlaySettings.toMinimalAppSettings().shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatingPointerEdgeBarSlotAction(
                    side,
                    key.slotIndex,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }
}

private fun copyShellOutputToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))
}
