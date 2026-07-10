package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.ui.ExtensionHubScreen
import com.slideindex.app.ui.FloatingPointerJoystickSettingsScreen
import com.slideindex.app.ui.FloatingPointerPointerSettingsScreen
import com.slideindex.app.ui.FloatingPointerRadialMenuSettingsScreen
import com.slideindex.app.ui.FloatingPointerSettingsScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.WidgetPanelSettingsScreen
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun EntryProviderScope<AppNavKey>.extensionNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.ExtensionHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: ExtensionHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ExtensionHubScreen(
            settings = settings,
            gestureActive = ctx.gestureActive(settings, permissions),
            bottomContentPadding = ctx.rootBottomContentPadding,
            onOpenLayoutSettings = { ctx.navigate(AppNavKey.HomeLayout) },
            onOpenQuickLauncher = { ctx.navigate(AppNavKey.QuickLauncher) },
            onOpenShellCommands = { ctx.navigate(AppNavKey.ShellCommands) },
            onOpenWidgetPanel = { ctx.navigate(AppNavKey.WidgetPanel) },
            onOpenFloatingPointer = { ctx.navigate(AppNavKey.FloatingPointer) },
        )
    }

    entry<AppNavKey.QuickLauncher> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        QuickLauncherEditorScreen(
            settings = settings,
            onBack = { ctx.replaceRoot(AppNavKey.HomeMain) },
            onSaveItems = viewModel::setQuickLauncherItems,
            onColumnsChange = viewModel::setQuickLauncherColumnsPerPage,
            onRowsChange = viewModel::setQuickLauncherRowsPerPage,
        )
    }

    entry<AppNavKey.ShellCommands> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        ShellCommandPanelScreen(
            settings = settings,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.replaceRoot(AppNavKey.HomeMain) },
            onSaveCommands = viewModel::setShellCommands,
            onRequestShizuku = { ctx.requestShizuku() },
        )
    }

    entry<AppNavKey.WidgetPanel> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        WidgetPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.replaceRoot(AppNavKey.HomeMain) },
            onSavePages = viewModel::setWidgetPanelPages,
            onBlurEnabledChange = viewModel::setWidgetPanelBlurEnabled,
            onWidthFractionChange = viewModel::setWidgetPanelWidthFraction,
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
            onBack = { ctx.replaceRoot(AppNavKey.HomeMain) },
            onOpenPointerSettings = { ctx.navigate(AppNavKey.FloatingPointerPointer) },
            onOpenJoystickSettings = { ctx.navigate(AppNavKey.FloatingPointerJoystick) },
            onOpenRadialMenuSettings = { ctx.navigate(AppNavKey.FloatingPointerRadialMenu) },
            onJoystickAreaZoomChange = viewModel::setFloatingPointerJoystickAreaZoomFraction,
            onJoystickAreaWidthChange = viewModel::setFloatingPointerJoystickAreaWidthPx,
            onJoystickAreaHeightChange = viewModel::setFloatingPointerJoystickAreaHeightPx,
            onMatchJoystickToScreenAspectChange = viewModel::setFloatingPointerMatchJoystickToScreenAspect,
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
            onEnabledChange = viewModel::setFloatingPointerRadialMenuEnabled,
            onAlwaysVisibleChange = viewModel::setFloatingPointerRadialAlwaysVisible,
            onLongPressMsChange = viewModel::setFloatingPointerRadialLongPressMs,
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
}
