package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.slideindex.app.ui.viewmodel.extensionHubViewModelFactory

fun EntryProviderScope<AppNavKey>.extensionNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.ExtensionHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: ExtensionHubViewModel = viewModel(
            factory = extensionHubViewModelFactory(ctx.app),
        )
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
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        QuickLauncherEditorScreen(
            settings = settings,
            onBack = { ctx.replaceRoot(AppNavKey.HomeMain) },
            onSaveItems = { items ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setQuickLauncherItems(items)
                }
            },
            onColumnsChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setQuickLauncherColumnsPerPage(value)
                }
            },
            onRowsChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setQuickLauncherRowsPerPage(value)
                }
            },
        )
    }

    entry<AppNavKey.ShellCommands> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        ShellCommandPanelScreen(
            settings = settings,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.replaceRoot(AppNavKey.HomeMain) },
            onSaveCommands = { items ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setShellCommands(items)
                }
            },
            onRequestShizuku = { ctx.requestShizuku() },
        )
    }

    entry<AppNavKey.WidgetPanel> {
        val settings = ctx.collectAppSettings()
        WidgetPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.replaceRoot(AppNavKey.HomeMain) },
            onSavePages = { pages ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setWidgetPanelPages(pages)
                }
            },
            onBlurEnabledChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setWidgetPanelBlurEnabled(enabled)
                }
            },
            onWidthFractionChange = { fraction ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setWidgetPanelWidthFraction(fraction)
                }
            },
        )
    }

    entry<AppNavKey.FloatingPointer> {
        val settings = ctx.collectAppSettings()
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
            onJoystickAreaZoomChange = { zoom ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerJoystickAreaZoomFraction(zoom)
                }
            },
            onJoystickAreaWidthChange = { width ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerJoystickAreaWidthPx(width)
                }
            },
            onJoystickAreaHeightChange = { height ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerJoystickAreaHeightPx(height)
                }
            },
            onMatchJoystickToScreenAspectChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerMatchJoystickToScreenAspect(enabled)
                }
            },
        )
    }

    entry<AppNavKey.FloatingPointerPointer> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        FloatingPointerPointerSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onPointerDiameterChange = { size ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerPointerDiameterPx(size)
                }
            },
            onRingThicknessChange = { thickness ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRingThicknessPx(thickness)
                }
            },
            onDotDiameterChange = { diameter ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerDotDiameterPx(diameter)
                }
            },
            onRingColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRingColor(color)
                }
            },
            onFillColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerFillColor(color)
                }
            },
            onDotColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerDotColor(color)
                }
            },
            onClickVisualFeedbackChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerClickVisualFeedbackEnabled(enabled)
                }
            },
            onClickHapticChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerClickHapticEnabled(enabled)
                }
            },
            onRippleColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRippleColor(color)
                }
            },
            onRippleSizeChange = { size ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRippleSizeDp(size)
                }
            },
            onRippleDurationChange = { duration ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRippleDurationMs(duration)
                }
            },
            onTrailTypeChange = { type ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerTrailType(type)
                }
            },
            onTrailDurationChange = { duration ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerTrailDurationMs(duration)
                }
            },
            onTrailColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerTrailColor(color)
                }
            },
            onHideWhenReleasedChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerHideWhenJoystickReleased(enabled)
                }
            },
            onPointerDesignChange = { design ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerDesignId(design.id)
                }
            },
            onResetVisualDefaults = {
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.resetFloatingPointerVisualDefaults()
                }
            },
        )
    }

    entry<AppNavKey.FloatingPointerJoystick> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        FloatingPointerJoystickSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onJoystickDiameterChange = { size ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerJoystickDiameterPx(size)
                }
            },
            onInnerColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerJoystickInnerColor(color)
                }
            },
            onOuterColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerJoystickOuterColor(color)
                }
            },
            onGradientRadiusChange = { fraction ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerJoystickGradientRadiusFraction(fraction)
                }
            },
            onHideOnOutsideClickChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerHideOnOutsideClick(enabled)
                }
            },
            onHideOnQuickSwipeChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerHideOnQuickSwipe(enabled)
                }
            },
            onHideWhenIdleChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerHideWhenIdle(enabled)
                }
            },
            onIdleDelayChange = { delayMs ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerIdleHideDelayMs(delayMs)
                }
            },
            onResetVisualDefaults = {
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.resetFloatingPointerJoystickVisualDefaults()
                }
            },
            onResetBehaviorDefaults = {
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.resetFloatingPointerJoystickBehaviorDefaults()
                }
            },
        )
    }

    entry<AppNavKey.FloatingPointerRadialMenu> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        FloatingPointerRadialMenuSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onEnabledChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialMenuEnabled(enabled)
                }
            },
            onAlwaysVisibleChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialAlwaysVisible(enabled)
                }
            },
            onLongPressMsChange = { ms ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialLongPressMs(ms)
                }
            },
            onSlotActionChange = { index, action ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialSlotAction(index, action)
                }
            },
            onOuterDiameterChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialOuterDiameterPx(value)
                }
            },
            onInnerDiameterChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialInnerDiameterPx(value)
                }
            },
            onOuterColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialOuterColor(color)
                }
            },
            onInnerColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialInnerColor(color)
                }
            },
            onDividerThicknessChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialDividerThicknessPx(value)
                }
            },
            onDividerColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialDividerColor(color)
                }
            },
            onIconSizeFractionChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialIconSizeFraction(value)
                }
            },
            onIconColorChange = { color ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFloatingPointerRadialIconColor(color)
                }
            },
            onResetDesignDefaults = {
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.resetFloatingPointerRadialDesignDefaults()
                }
            },
        )
    }
}
