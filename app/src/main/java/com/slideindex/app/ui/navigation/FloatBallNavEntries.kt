package com.slideindex.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.FloatBallAppearanceSettingsScreen
import com.slideindex.app.ui.FloatBallGestureSettingsScreen
import com.slideindex.app.ui.FloatBallPickSettingsScreen
import com.slideindex.app.ui.FloatBallSettingsScreen
import com.slideindex.app.ui.FloatBallStyleSettingsScreen
import com.slideindex.app.ui.FloatBallTranslationSettingsScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.ImageSearchEngineDetailScreen
import com.slideindex.app.ui.ImageSearchEngineSettingsScreen
import com.slideindex.app.ui.NativeEnginePackSettingsScreen
import com.slideindex.app.ui.OcrModelSettingsScreen
import com.slideindex.app.ui.SearchEngineEditorCategory
import com.slideindex.app.ui.SearchEngineEditorScreen
import com.slideindex.app.ui.SearchEnginePreviewSortScreen
import com.slideindex.app.ui.SearchEngineSettingsScreen
import com.slideindex.app.ui.ShareImageOcrHistoryScreen
import com.slideindex.app.ui.TranslateModelSettingsScreen
import com.slideindex.app.ui.resolveImageSearchEngine
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.FloatBallPickSettingsViewModel
import com.slideindex.app.ui.viewmodel.NativeEnginePackSettingsViewModel
import com.slideindex.app.ui.viewmodel.OcrModelSettingsViewModel
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel
import com.slideindex.app.ui.viewmodel.ShareImageOcrHistoryViewModel
import com.slideindex.app.ui.viewmodel.TranslateSettingsViewModel

fun NavEntryBuilder.floatBallNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.FloatBall> {
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

    hiltEntry<AppNavKey.FloatBallSearchEngine> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val importPreviewState by viewModel.importPreviewState.collectAsStateWithLifecycle()
        SearchEngineSettingsScreen(
            settings = settings,
            importPreviewState = importPreviewState,
            onBack = { ctx.backStack.removeLastOrNull() },
            onImport = viewModel::previewImport,
            onDismissImportPreview = viewModel::dismissImportPreview,
            onConfirmImport = viewModel::confirmImport,
            onUpsertEngine = viewModel::upsertEngine,
            onDeleteEngine = viewModel::deleteEngine,
            onMoveEngine = viewModel::moveEngine,
            onGridColumnsChange = viewModel::setGridColumns,
            onGridRowsChange = viewModel::setGridRows,
            onShowLabelsChange = viewModel::setShowLabels,
            onOpenPreviewSort = { ctx.navigate(AppNavKey.FloatBallSearchEnginePreviewSort) },
            onOpenEditor = { engineId ->
                ctx.navigate(AppNavKey.FloatBallSearchEngineEditor(engineId.orEmpty()))
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallSearchEngineEditor> { key ->
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

    hiltEntry<AppNavKey.FloatBallSearchEnginePreviewSort> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        SearchEnginePreviewSortScreen(
            settings = settings,
            onBack = { ctx.backStack.removeLastOrNull() },
            onReorder = viewModel::reorderPickPanelEngines,
        )
    }

    hiltEntry<AppNavKey.FloatBallAppearance> {
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
            onPositionYPreviewStart = { /* preview starts on first drag tick */ },
            onPositionYPreviewChange = { ctx.previewFloatBallPositionY(it) },
            onPositionYPreviewStop = { restore -> ctx.endFloatBallPositionYPreview(restore) },
            onPreviewAppearance = { size, opacity, visible, lineHeight, lineWidth, lineOpacity ->
                ctx.previewFloatBallAppearance(
                    sizeDp = size,
                    opacity = opacity,
                    visibleFraction = visible,
                    lineHeightFraction = lineHeight,
                    lineWidthFraction = lineWidth,
                    lineOpacity = lineOpacity,
                )
            },
            onAppearancePreviewCommit = { ctx.clearFloatBallAppearancePreviewRestore() },
            onAppearancePreviewRestore = { ctx.endFloatBallAppearancePreview(true) },
        )
    }

    hiltEntry<AppNavKey.FloatBallStyle> {
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

    hiltEntry<AppNavKey.FloatBallGesture> {
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

    hiltEntry<AppNavKey.FloatBallGestureActionPick> { key ->
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
                viewModel.setFloatBallGestureAction(gestureType, action)
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallGestureShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val returnKey = AppNavKey.FloatBallGesture
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = overlaySettings.toMinimalAppSettings().shellCommands,
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

    hiltEntry<AppNavKey.FloatBallImageSearchEngine> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ImageSearchEngineSettingsScreen(
            settings = settings,
            onBack = { ctx.backStack.removeLastOrNull() },
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

    hiltEntry<AppNavKey.FloatBallImageSearchEngineEditor> { key ->
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

    hiltEntry<AppNavKey.FloatBallImageSearchEngineDetail> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val config = settings.aggregatedImageSearchEngines.find { it.engineId == key.engineId }
        val engine = resolveImageSearchEngine(key.engineId)
        if (config == null || engine == null) {
            LaunchedEffect(Unit) {
                ctx.navigateBackTo(AppNavKey.FloatBallImageSearchEngine)
            }
        } else {
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
    }

    hiltEntry<AppNavKey.FloatBallPick> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val pickViewModel: FloatBallPickSettingsViewModel = hiltViewModel()
        val historyViewModel: ShareImageOcrHistoryViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val imageViewerOptions by pickViewModel.imageViewerOptions.collectAsStateWithLifecycle()
        val historyEntries by historyViewModel.historyRepository.entries.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        FloatBallPickSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            historyCount = historyEntries.size,
            imageViewerOptions = imageViewerOptions,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onPointerSpeedChange = viewModel::setFloatBallPointerSpeedFraction,
            onPointerSpeedVerticalChange = viewModel::setFloatBallPointerSpeedVerticalFraction,
            onPickOffsetChange = viewModel::setFloatBallPickOffsetDp,
            onPickTextSizeChange = viewModel::setFloatBallPickTextSizeSp,
            onPickBottomTransitionChange = viewModel::setFloatBallPickBottomTransitionFraction,
            onPickTextFirstPanelChange = viewModel::setFloatBallPickTextFirstPanel,
            onPickPanelEnterAnimationMsChange = viewModel::setFloatBallPickPanelEnterAnimationMs,
            onPickPanelExitAnimationMsChange = viewModel::setFloatBallPickPanelExitAnimationMs,
            onPointerSlopChange = viewModel::setFloatBallPointerSlopDp,
            onHoverPauseDelayMsChange = viewModel::setFloatBallHoverPauseDelayMs,
            onRegionalCancelSlopDpChange = viewModel::setFloatBallRegionalCancelSlopDp,
            onOcrFallbackChange = viewModel::setFloatBallOcrFallbackEnabled,
            onShareImageOcrHistoryEnabledChange = viewModel::setShareImageOcrHistoryEnabled,
            onDefaultImageViewerPackageChange = viewModel::setDefaultImageViewerPackage,
            onOpenOcrModels = { ctx.navigate(AppNavKey.OcrModels) },
            onOpenShareImageOcrHistory = { ctx.navigate(AppNavKey.ShareImageOcrHistory) },
        )
    }

    hiltEntry<AppNavKey.ShareImageOcrHistory> {
        val viewModel: ShareImageOcrHistoryViewModel = hiltViewModel()
        ShareImageOcrHistoryScreen(
            repository = viewModel.historyRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onClear = viewModel::clearHistory,
        )
    }

    hiltEntry<AppNavKey.FloatBallTranslation> {
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

    hiltEntry<AppNavKey.TranslateModels> {
        val viewModel: TranslateSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val installedLanguageCodes by viewModel.installedLanguageCodes.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        TranslateModelSettingsScreen(
            settings = settings,
            installedLanguageCodes = installedLanguageCodes,
            downloadState = downloadState,
            translateEngineInstalled = viewModel.translateEngineInstalled,
            translateEngineSizeBytes = viewModel.translateEngineSizeBytes,
            translateEngineVersionState = viewModel.translateEngineVersionState,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallTranslation) },
            onDownloadLanguage = viewModel::downloadLanguage,
            onDeleteLanguage = viewModel::deleteLanguage,
            onDeleteTranslateEngine = viewModel::deleteTranslateEngine,
            onOpenEngineManagement = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
        )
    }

    hiltEntry<AppNavKey.NativeEnginePacks> {
        val viewModel: NativeEnginePackSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val packRows by viewModel.packRows.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        NativeEnginePackSettingsScreen(
            settings = settings,
            packRows = packRows,
            downloadState = downloadState,
            onBack = { ctx.backStack.removeLastOrNull() },
            onDownloadPack = viewModel::downloadPack,
            onDeletePack = viewModel::deletePack,
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
        )
    }

    hiltEntry<AppNavKey.OcrModels> {
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
            ocrEngineInstalled = viewModel.ocrEngineInstalled,
            ocrEngineSizeBytes = viewModel.ocrEngineSizeBytes,
            ocrEngineVersionState = viewModel.ocrEngineVersionState,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onSelectModel = viewModel::selectModel,
            onClearSelectedModel = viewModel::clearSelectedModel,
            onDownloadModel = viewModel::downloadModel,
            onDeleteModel = viewModel::deleteModel,
            onDeleteOcrEngine = viewModel::deleteOcrEngine,
            onOpenEngineManagement = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
        )
    }
}
