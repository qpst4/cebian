package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
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
import com.slideindex.app.ui.FloatBallAppearanceSettingsScreen
import com.slideindex.app.ui.FloatBallGestureSettingsScreen
import com.slideindex.app.ui.FloatBallPickSettingsScreen
import com.slideindex.app.ui.ShareImageOcrHistoryScreen
import com.slideindex.app.ui.FloatBallSettingsScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.PrivacyPolicyScreen
import com.slideindex.app.ui.SettingsBackupScreen
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.WidgetPanelSettingsScreen
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.FloatBallTranslationSettingsScreen
import com.slideindex.app.ui.SearchEngineSettingsScreen
import com.slideindex.app.ui.ImageSearchEngineSettingsScreen
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel
import com.slideindex.app.ui.TranslateModelSettingsScreen
import com.slideindex.app.ui.OcrModelSettingsScreen
import com.slideindex.app.ui.viewmodel.OcrModelSettingsViewModel
import com.slideindex.app.ui.viewmodel.TranslateSettingsViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel
import com.slideindex.app.ui.viewmodel.ShellCommandViewModel
import com.slideindex.app.ui.viewmodel.ShareImageOcrHistoryViewModel

fun EntryProviderScope<AppNavKey>.extensionNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.ExtensionHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: ExtensionHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ExtensionHubScreen(
            settings = settings,
            gestureActive = ctx.gestureActive(settings, permissions),
            accessibilityGranted = permissions.accessibilityGranted,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onOpenLayoutSettings = { ctx.navigate(AppNavKey.HomeLayout) },
            onOpenQuickLauncher = { ctx.navigate(AppNavKey.QuickLauncher) },
            onOpenShellCommands = { ctx.navigate(AppNavKey.ShellCommands) },
            onOpenWidgetPanel = { ctx.navigate(AppNavKey.WidgetPanel) },
            onOpenFloatingPointer = { ctx.navigate(AppNavKey.FloatingPointer) },
            onOpenFloatBall = { ctx.navigate(AppNavKey.FloatBall) },
            onOpenSettingsBackup = { ctx.navigate(AppNavKey.ExtensionBackup) },
            onOpenAbout = { ctx.navigate(AppNavKey.ExtensionAbout) },
        )
    }

    entry<AppNavKey.ExtensionAbout> {
        ExtensionAboutScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPrivacyPolicy = { ctx.navigate(AppNavKey.ExtensionPrivacy) },
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
        SettingsBackupScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onExport = viewModel::exportSettings,
            onImport = viewModel::previewImport,
            importPreviewState = importPreviewState,
            onDismissPreview = viewModel::dismissPreview,
            onConfirmImport = viewModel::confirmImport,
        )
    }

    entry<AppNavKey.QuickLauncher> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        ShellCommandPanelScreen(
            settings = settings,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveCommands = viewModel::setShellCommands,
            onRequestShizuku = { ctx.requestShizuku() },
            shellViewModel = shellViewModel,
        )
    }

    entry<AppNavKey.WidgetPanel> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        WidgetPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSavePages = viewModel::setWidgetPanelPages,
            onBlurEnabledChange = viewModel::setWidgetPanelBlurEnabled,
            onWidthFractionChange = viewModel::setWidgetPanelWidthFraction,
        )
    }

    entry<AppNavKey.FloatBall> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        FloatBallSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        )
    }

    entry<AppNavKey.FloatBallAppearance> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        FloatBallAppearanceSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onSizeChange = viewModel::setFloatBallSizeDp,
            onOpacityChange = viewModel::setFloatBallOpacity,
            onPositionModeChange = viewModel::setFloatBallPositionMode,
            onPositionYChange = viewModel::setFloatBallPositionYFraction,
            onPositionXChange = viewModel::setFloatBallPositionXFraction,
            onLineHeightChange = viewModel::setFloatBallLineHeightFraction,
            onLineWidthChange = viewModel::setFloatBallLineWidthFraction,
            onLineOpacityChange = viewModel::setFloatBallLineOpacity,
            onStyleTypeChange = viewModel::setFloatBallStyleType,
            onCustomImageUriChange = viewModel::setFloatBallCustomImageUri,
            onSlideshowUrisChange = viewModel::setFloatBallSlideshowUris,
            onGifUriChange = viewModel::setFloatBallGifUri,
        )
    }

    entry<AppNavKey.FloatBallGesture> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        FloatBallGestureSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onGestureActionChange = viewModel::setFloatBallGestureAction,
            onDownSwipeShortPercentChange = viewModel::setFloatBallDownSwipeShortPercent,
            onSideSwipeShortPercentChange = viewModel::setFloatBallSideSwipeShortPercent,
            onUpSwipeShortPercentChange = viewModel::setFloatBallUpSwipeShortPercent,
        )
    }

    entry<AppNavKey.FloatBallImageSearchEngine> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ImageSearchEngineSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onUpsertEngine = viewModel::upsertEngine,
            onDeleteEngine = viewModel::deleteEngine,
            onMoveEngine = viewModel::moveImageShareEngine,
        )
    }

    entry<AppNavKey.FloatBallPick> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val historyViewModel: ShareImageOcrHistoryViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            onPointerSlopChange = viewModel::setFloatBallPointerSlopDp,
            onOcrFallbackChange = viewModel::setFloatBallOcrFallbackEnabled,
            onShareImageOcrHistoryEnabledChange = viewModel::setShareImageOcrHistoryEnabled,
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        FloatBallTranslationSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onInstantTranslateChange = viewModel::setInstantTranslate,
            onEngineChange = viewModel::setTranslateEngine,
            onTargetLangChange = viewModel::setTranslateTargetLang,
            onPickPanelTransparencyChange = viewModel::setTranslatePickPanelTransparency,
            onOpenMlKitModels = { ctx.navigate(AppNavKey.TranslateModels) },
        )
    }

    entry<AppNavKey.TranslateModels> {
        val viewModel: TranslateSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        FloatingPointerRadialMenuSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onAlwaysVisibleChange = viewModel::setFloatingPointerRadialAlwaysVisible,
            onLongPressMsChange = viewModel::setFloatingPointerRadialLongPressMs,
            onLongPressActionChange = viewModel::setFloatingPointerJoystickLongPressAction,
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

    entry<AppNavKey.FloatingPointerEdgeActions> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        FloatingPointerEdgeSideSettingsScreen(
            side = side,
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeActions) },
            onEnabledChange = { enabled -> viewModel.setFloatingPointerEdgeBarEnabled(side, enabled) },
            onSlotActionChange = { slotIndex, action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, slotIndex, action)
            },
            onAddSlot = { viewModel.addFloatingPointerEdgeBarSlot(side) },
            onRemoveSlot = { slotIndex -> viewModel.removeFloatingPointerEdgeBarSlot(side, slotIndex) },
        )
    }
}
