package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.slideindex.app.ui.ClipboardLsposedWhitelistScreen
import com.slideindex.app.ui.StashClipboardSettingsScreen
import com.slideindex.app.ui.FloatBallSettingsScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
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
import com.slideindex.app.shell.ShellTemplateContextFactory
import com.slideindex.app.ui.SearchEngineEditorCategory
import com.slideindex.app.ui.SearchEngineEditorScreen
import com.slideindex.app.ui.ShellCommandEditorScreen
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.ShellOutputHistoryScreen
import com.slideindex.app.ui.ShellResultScreen
import com.slideindex.app.util.ShellCommandExecutor
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
import com.slideindex.app.ui.OcrModelSettingsScreen
import com.slideindex.app.ui.viewmodel.OcrModelSettingsViewModel
import com.slideindex.app.ui.viewmodel.TranslateSettingsViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel
import com.slideindex.app.ui.viewmodel.ShellCommandViewModel
import com.slideindex.app.ui.viewmodel.ShareImageOcrHistoryViewModel
import com.slideindex.app.ui.viewmodel.StashClipboardSettingsViewModel

fun EntryProviderScope<AppNavKey>.extensionNavEntries(ctx: MainNavContext) {
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
            onOpenShellCommands = { ctx.navigate(AppNavKey.ShellCommands) },
            onOpenWidgetPanel = { ctx.navigate(AppNavKey.WidgetPanel) },
            onOpenFloatingPointer = { ctx.navigate(AppNavKey.FloatingPointer) },
            onOpenStashClipboard = { ctx.navigate(AppNavKey.StashClipboard) },
            onOpenSettingsBackup = { ctx.navigate(AppNavKey.ExtensionBackup) },
            onOpenAbout = { ctx.navigate(AppNavKey.ExtensionAbout) },
        )
    }

    entry<AppNavKey.ExtensionAbout> {
        ExtensionAboutScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPrivacyPolicy = { ctx.navigate(AppNavKey.ExtensionPrivacy) },
            onOpenThirdPartyNotices = { ctx.navigate(AppNavKey.ExtensionThirdPartyNotices) },
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
            onSaveItems = viewModel::setQuickLauncherItems,
            onColumnsChange = viewModel::setQuickLauncherColumnsPerPage,
            onRowsChange = viewModel::setQuickLauncherRowsPerPage,
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
                    val result = withContext(Dispatchers.IO) {
                        ShellCommandExecutor.execute(command, ShellTemplateContextFactory.current())
                    }
                    callback(result.exitCode, result.output)
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
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onClipboardMonitoringChange = viewModel::setClipboardBackgroundMonitoring,
            onClipboardMonitoringPathChange = viewModel::setClipboardBackgroundMonitoringPath,
            onClipboardScreenshotMonitoringChange = viewModel::setClipboardScreenshotMonitoring,
            onOpenLsposedWhitelist = { ctx.navigate(AppNavKey.ClipboardLsposedWhitelist) },
            onClipboardHistoryMaxEntriesChange = viewModel::setClipboardHistoryMaxEntries,
            onRequestReadLogsGrant = { ctx.requestReadLogsGrant() },
            onClearClipboardHistory = viewModel::clearClipboardHistory,
            onClearStash = viewModel::clearStash,
        )
    }

    entry<AppNavKey.ClipboardLsposedWhitelist> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ClipboardLsposedWhitelistScreen(
            whitelistedPackages = settings.clipboardLsposedExtraWhitelist,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onAddPackage = { packageName ->
                val updated = settings.clipboardLsposedExtraWhitelist + packageName
                viewModel.setClipboardLsposedExtraWhitelist(updated)
            },
            onRemovePackage = { packageName ->
                val updated = settings.clipboardLsposedExtraWhitelist - packageName
                viewModel.setClipboardLsposedExtraWhitelist(updated)
            },
        )
    }

    entry<AppNavKey.FloatBall> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setFloatBallEnabled,
            onOpenAppearanceSettings = { ctx.navigate(AppNavKey.FloatBallAppearance) },
            onOpenGestureSettings = { ctx.navigate(AppNavKey.FloatBallGesture) },
            onOpenPickSettings = { ctx.navigate(AppNavKey.FloatBallPick) },
            onOpenTranslationSettings = { ctx.navigate(AppNavKey.FloatBallTranslation) },
            onOpenSearchEngineSettings = { ctx.navigate(AppNavKey.FloatBallSearchEngine) },
            onOpenImageSearchEngineSettings = { ctx.navigate(AppNavKey.FloatBallImageSearchEngine) },
        )
    }

    entry<AppNavKey.FloatBallSearchEngine> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val importPreviewState by viewModel.importPreviewState.collectAsStateWithLifecycle()
        SearchEngineSettingsScreen(
            settings = settings,
            importPreviewState = importPreviewState,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onImport = viewModel::previewImport,
            onDismissImportPreview = viewModel::dismissImportPreview,
            onConfirmImport = viewModel::confirmImport,
            onUpsertEngine = viewModel::upsertEngine,
            onDeleteEngine = viewModel::deleteEngine,
            onMoveEngine = viewModel::moveEngine,
            onGridColumnsChange = viewModel::setGridColumns,
            onGridRowsChange = viewModel::setGridRows,
            onShowLabelsChange = viewModel::setShowLabels,
            onSetDefaultEngineId = viewModel::setDefaultEngineId,
            onSetSearchPanelInputBehavior = viewModel::setSearchPanelInputBehavior,
            onOpenPreviewSort = { ctx.navigate(AppNavKey.FloatBallSearchEnginePreviewSort) },
            onOpenEditor = { engineId ->
                ctx.navigate(AppNavKey.FloatBallSearchEngineEditor(engineId.orEmpty()))
            },
        )
    }

    entry<AppNavKey.FloatBallSearchEngineEditor> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val initialEngine = key.engineId.takeIf { it.isNotEmpty() }
            ?.let { id -> settings.searchEngines.find { it.id == id } }
        SearchEngineEditorScreen(
            initialEngine = initialEngine,
            editorCategory = SearchEngineEditorCategory.TEXT,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallSearchEngine) },
            onSave = { result ->
                viewModel.upsertEngine(result)
                ctx.navigateBackTo(AppNavKey.FloatBallSearchEngine)
            },
        )
    }

    entry<AppNavKey.FloatBallSearchEnginePreviewSort> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        SearchEnginePreviewSortScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallSearchEngine) },
            onReorder = viewModel::reorderPickPanelEngines,
        )
    }

    entry<AppNavKey.FloatBallAppearance> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallAppearanceSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onSizeChange = viewModel::setFloatBallSizeDp,
            onPickCrossArmChange = viewModel::setFloatBallPickCrossArmDp,
            onOpacityChange = viewModel::setFloatBallOpacity,
            onPositionModeChange = viewModel::setFloatBallPositionMode,
            onVisibleFractionChange = viewModel::setFloatBallVisibleFraction,
            onPositionYChange = viewModel::setFloatBallPositionYFraction,
            onLineHeightChange = viewModel::setFloatBallLineHeightFraction,
            onLineWidthChange = viewModel::setFloatBallLineWidthFraction,
            onLineOpacityChange = viewModel::setFloatBallLineOpacity,
            onOpenStyleSettings = { ctx.navigate(AppNavKey.FloatBallStyle) },
            onStripZonePreviewStart = { ctx.startFloatBallStripZonePreview() },
            onStripZonePreviewStop = { ctx.stopFloatBallStripZonePreview() },
        )
    }

    entry<AppNavKey.FloatBallStyle> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallStyleSettingsScreen(
            settings = settings,
            enabled = settings.floatBallEnabled && permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallAppearance) },
            onStyleTypeChange = viewModel::setFloatBallStyleType,
            onCustomImageUriChange = viewModel::setFloatBallCustomImageUri,
            onSlideshowUrisChange = viewModel::setFloatBallSlideshowUris,
            onGifUriChange = viewModel::setFloatBallGifUri,
        )
    }

    entry<AppNavKey.FloatBallGesture> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallGestureSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onOpenActionPick = { type ->
                ctx.navigate(AppNavKey.FloatBallGestureActionPick(type.id))
            },
            onOpenShellCommand = { type, command ->
                ctx.navigate(AppNavKey.FloatBallGestureShellCommand(type.id, command))
            },
            onDownSwipeShortPercentChange = viewModel::setFloatBallDownSwipeShortPercent,
            onSideSwipeShortPercentChange = viewModel::setFloatBallSideSwipeShortPercent,
            onUpSwipeShortPercentChange = viewModel::setFloatBallUpSwipeShortPercent,
        )
    }

    entry<AppNavKey.FloatBallGestureActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val returnKey = AppNavKey.FloatBallGesture
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            current = settings.floatBallGestureActions[gestureType] ?: GestureAction.None,
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                if (action is GestureAction.ExecuteShellCommand) {
                    ctx.navigate(
                        AppNavKey.FloatBallGestureShellCommand(
                            gestureTypeId = key.gestureTypeId,
                            initialCommand = action.command,
                        ),
                    )
                } else {
                    viewModel.setFloatBallGestureAction(gestureType, action)
                    ctx.navigateBackTo(returnKey)
                }
            },
        )
    }

    entry<AppNavKey.FloatBallGestureShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val returnKey = AppNavKey.FloatBallGesture
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatBallGestureAction(
                    gestureType,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    entry<AppNavKey.FloatBallImageSearchEngine> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ImageSearchEngineSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onUpsertEngine = viewModel::upsertEngine,
            onDeleteEngine = viewModel::deleteEngine,
            onReorderShareEngines = viewModel::reorderImageShareEngines,
            onReorderAggregatedEngines = viewModel::reorderAggregatedImageSearchEngines,
            onOpenAggregatedEngine = { engineId ->
                ctx.navigate(AppNavKey.FloatBallImageSearchEngineDetail(engineId))
            },
            onImageSearchPickPanelTransparencyChange = viewModel::setImageSearchPickPanelTransparency,
            onOpenEditor = { engineId ->
                ctx.navigate(AppNavKey.FloatBallImageSearchEngineEditor(engineId.orEmpty()))
            },
        )
    }

    entry<AppNavKey.FloatBallImageSearchEngineEditor> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val initialEngine = key.engineId.takeIf { it.isNotEmpty() }
            ?.let { id -> settings.searchEngines.find { it.id == id } }
        SearchEngineEditorScreen(
            initialEngine = initialEngine,
            editorCategory = SearchEngineEditorCategory.IMAGE_SHARE,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallImageSearchEngine) },
            onSave = { result ->
                viewModel.upsertEngine(result)
                ctx.navigateBackTo(AppNavKey.FloatBallImageSearchEngine)
            },
        )
    }

    entry<AppNavKey.FloatBallImageSearchEngineDetail> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val config = settings.aggregatedImageSearchEngines.find { it.engineId == key.engineId }
        val engine = resolveImageSearchEngine(key.engineId)
        if (config == null || engine == null) {
            ctx.navigateBackTo(AppNavKey.FloatBallImageSearchEngine)
            return@entry
        }
        ImageSearchEngineDetailScreen(
            engine = engine,
            config = config,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallImageSearchEngine) },
            onShowInPanelChange = { enabled ->
                viewModel.setAggregatedImageSearchEngineShowInPanel(key.engineId, enabled)
            },
            onPreloadChange = { enabled ->
                viewModel.setAggregatedImageSearchEnginePreload(key.engineId, enabled)
            },
        )
    }

    entry<AppNavKey.FloatBallPick> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val historyViewModel: ShareImageOcrHistoryViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val historyEntries by historyViewModel.historyRepository.entries.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        FloatBallPickSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            historyCount = historyEntries.size,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onPointerSpeedChange = viewModel::setFloatBallPointerSpeedFraction,
            onPickOffsetChange = viewModel::setFloatBallPickOffsetDp,
            onPickTextSizeChange = viewModel::setFloatBallPickTextSizeSp,
            onPickBottomTransitionChange = viewModel::setFloatBallPickBottomTransitionFraction,
            onPickTextFirstPanelChange = viewModel::setFloatBallPickTextFirstPanel,
            onPickPanelEnterAnimationMsChange = viewModel::setFloatBallPickPanelEnterAnimationMs,
            onPickPanelExitAnimationMsChange = viewModel::setFloatBallPickPanelExitAnimationMs,
            onPointerSlopChange = viewModel::setFloatBallPointerSlopDp,
            onOcrFallbackChange = viewModel::setFloatBallOcrFallbackEnabled,
            onShareImageOcrHistoryEnabledChange = viewModel::setShareImageOcrHistoryEnabled,
            onDefaultImageViewerPackageChange = viewModel::setDefaultImageViewerPackage,
            onOpenOcrModels = { ctx.navigate(AppNavKey.OcrModels) },
            onOpenShareImageOcrHistory = { ctx.navigate(AppNavKey.ShareImageOcrHistory) },
        )
    }

    entry<AppNavKey.ShareImageOcrHistory> {
        val viewModel: ShareImageOcrHistoryViewModel = hiltViewModel()
        ShareImageOcrHistoryScreen(
            repository = viewModel.historyRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onClear = viewModel::clearHistory,
        )
    }

    entry<AppNavKey.FloatBallTranslation> {
        val viewModel: TranslateSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatBallTranslationSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onInstantTranslateChange = viewModel::setInstantTranslate,
            onEngineChange = viewModel::setTranslateEngine,
            onTargetLangChange = viewModel::setTranslateTargetLang,
            onOpenMlKitModels = { ctx.navigate(AppNavKey.TranslateModels) },
        )
    }

    entry<AppNavKey.TranslateModels> {
        val viewModel: TranslateSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val installedLanguageCodes by viewModel.installedLanguageCodes.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        TranslateModelSettingsScreen(
            settings = settings,
            installedLanguageCodes = installedLanguageCodes,
            downloadState = downloadState,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallTranslation) },
            onDownloadLanguage = viewModel::downloadLanguage,
            onDeleteLanguage = viewModel::deleteLanguage,
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
        )
    }

    entry<AppNavKey.OcrModels> {
        val viewModel: OcrModelSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val installedModelIds by viewModel.installedModelIds.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        OcrModelSettingsScreen(
            settings = settings,
            catalogModels = viewModel.catalogModels,
            installedModelIds = installedModelIds,
            downloadState = downloadState,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onSelectModel = viewModel::selectModel,
            onClearSelectedModel = viewModel::clearSelectedModel,
            onDownloadModel = viewModel::downloadModel,
            onDeleteModel = viewModel::deleteModel,
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
        )
    }

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
                        } else if (action is GestureAction.ExecuteShellCommand) {
                            ctx.navigate(
                                AppNavKey.FloatingPointerRadialShellCommand(
                                    key.slotIndex,
                                    action.command,
                                ),
                            )
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
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
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
                if (action is GestureAction.ExecuteShellCommand) {
                    ctx.navigate(
                        AppNavKey.FloatingPointerEdgeShellCommand(
                            side = key.side,
                            slotIndex = key.slotIndex,
                            initialCommand = action.command,
                        ),
                    )
                } else {
                    viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                    ctx.navigateBackTo(returnKey)
                }
            },
        )
    }

    entry<AppNavKey.FloatingPointerEdgeShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeSideSettings(key.side)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
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
