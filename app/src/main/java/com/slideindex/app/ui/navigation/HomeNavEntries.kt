package com.slideindex.app.ui.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.service.OverlayService
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.activeBubbleStyle
import com.slideindex.app.settings.activeCapsuleStyle
import com.slideindex.app.settings.activeWaveStyle
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.ui.AppKeepAliveSettingsScreen
import com.slideindex.app.ui.ExcludedAppsScreen
import com.slideindex.app.ui.FreeWindowPreviewScreen
import com.slideindex.app.ui.FreeWindowSettingsScreen
import com.slideindex.app.ui.GestureAngleSettingsScreen
import com.slideindex.app.ui.HiddenAppsScreen
import com.slideindex.app.ui.LayoutSettingsScreen
import com.slideindex.app.ui.MainScreen
import com.slideindex.app.ui.SideGestureSettingsScreen
import com.slideindex.app.ui.TriggerAppearanceSettingsScreen
import com.slideindex.app.ui.TriggerCollectionScreen
import com.slideindex.app.ui.TriggerDesignSettingsScreen
import com.slideindex.app.ui.animationstyle.AnimationStyleSelectScreen
import com.slideindex.app.ui.animationstyle.BubbleStyleSettingsScreen
import com.slideindex.app.ui.animationstyle.CapsuleStyleSettingsScreen
import com.slideindex.app.ui.animationstyle.WaveStyleSettingsScreen
import com.slideindex.app.ui.viewmodel.HomeViewModel
import com.slideindex.app.ui.viewmodel.MainNavHomeEffects
import com.slideindex.app.ui.viewmodel.homeViewModelFactory

fun EntryProviderScope<AppNavKey>.homeNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.HomeMain> {
        val permissions = ctx.collectPermissions()
        val homeEffects = remember(ctx) { MainNavHomeEffects(ctx) }
        val viewModel: HomeViewModel = viewModel(
            factory = homeViewModelFactory(ctx.app, homeEffects),
        )
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MainScreen(
            settings = settings,
            notificationGranted = permissions.notificationGranted,
            shizukuGranted = permissions.shizukuGranted,
            accessibilityGranted = permissions.accessibilityGranted,
            batteryOptimizationExempt = permissions.batteryOptimizationExempt,
            onRequestNotification = { viewModel.requestNotificationPermission() },
            onRequestShizuku = { viewModel.requestShizuku() },
            onRequestAccessibility = { viewModel.openAccessibilitySettings() },
            onGestureEnabledChange = { enabled -> viewModel.setServiceEnabled(enabled) },
            onOpenAppKeepAliveSettings = { ctx.navigate(AppNavKey.HomeAppKeepAlive) },
            onHapticEnabledChange = { enabled -> viewModel.setHapticEnabled(enabled) },
            onHapticStrengthChange = { level -> viewModel.setHapticStrength(level) },
            onOpenFreeWindowSettings = { ctx.navigate(AppNavKey.HomeFreeWindow) },
            onOpenExcludedAppsSettings = { ctx.navigate(AppNavKey.HomeExcludedApps) },
            onOpenTriggerCollection = { ctx.navigate(AppNavKey.HomeTriggerCollection) },
            onOpenGestureAngle = { ctx.navigate(AppNavKey.HomeGestureAngle) },
            onOpenAnimationStyleSelect = { ctx.navigate(AppNavKey.HomeAnimationStyleSelect) },
            onGestureHintEnabledChange = { enabled -> viewModel.setGestureHintEnabled(enabled) },
            onHideTriggerInLandscapeChange = { enabled -> viewModel.setHideTriggerInLandscape(enabled) },
            onHideTriggerOnLockScreenChange = { enabled -> viewModel.setHideTriggerOnLockScreen(enabled) },
            onHideTriggerOnLauncherChange = { enabled -> viewModel.setHideTriggerOnLauncher(enabled) },
            bottomContentPadding = ctx.rootBottomContentPadding,
            onDynamicColorChange = { enabled -> viewModel.setDynamicColorEnabled(enabled) },
            onThemeColorChange = { color -> viewModel.setThemeColor(color) },
        )
    }

    entry<AppNavKey.HomeAppKeepAlive> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        AppKeepAliveSettingsScreen(
            hideFromRecents = settings.hideFromRecents,
            batteryOptimizationExempt = permissions.batteryOptimizationExempt,
            accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,
            writeSecureSettingsGranted = permissions.writeSecureSettingsGranted,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onRequestBatteryOptimization = { ctx.requestBatteryOptimization() },
            onRequestAutoStart = { ctx.openAutoStartSettings() },
            onHideFromRecentsChange = { enabled -> ctx.setHideFromRecents(enabled) },
            onAccessibilityKeepAliveChange = { enabled -> ctx.setAccessibilityKeepAlive(enabled) },
            onRequestSecureSettingsGrant = { ctx.requestSecureSettingsGrant() },
        )
    }

    entry<AppNavKey.HomeLayout> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        LayoutSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(settings, permissions),
            onBack = {
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                ctx.replaceRoot(AppNavKey.HomeMain)
            },
            onIndexHeightChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setIndexHeightFraction(value)
                }
            },
            onAppsPerRowChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setAppsPerRow(value)
                }
            },
            onPanelOpacityChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setPanelOpacity(value)
                }
            },
            onOpenHiddenAppsSettings = { ctx.navigate(AppNavKey.HomeHiddenApps) },
            onLayoutPreviewStart = {
                ctx.sendOverlayPreviewIntent(
                    OverlayService.ACTION_PREVIEW_START,
                    LayoutPreviewContent.INDEX_ONLY,
                )
            },
            onLayoutPreviewStop = {
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
            },
        )
    }

    entry<AppNavKey.HomeHiddenApps> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        HiddenAppsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeLayout) },
            onHideApp = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.addHiddenApp(packageName)
                }
            },
            onUnhideApp = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.removeHiddenApp(packageName)
                }
            },
        )
    }

    entry<AppNavKey.HomeExcludedApps> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        ExcludedAppsScreen(
            settings = settings,
            usageAccessGranted = permissions.usageAccessGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onRequestUsageAccess = { ctx.openUsageAccessSettings() },
            onExcludeApp = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.addExcludedTriggerApp(packageName)
                }
            },
            onRemoveExcludedApp = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.removeExcludedTriggerApp(packageName)
                }
            },
        )
    }

    entry<AppNavKey.HomeFreeWindow> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        FreeWindowSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFreeWindowEnabled(enabled)
                }
            },
            onLaunchPolicyChange = { policyId ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setAppLaunchPolicyId(policyId)
                }
            },
            onLongPressDurationChange = { durationMs ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setLongPressLaunchDurationMs(durationMs)
                }
            },
            onModeChange = { modeId ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFreeWindowModeId(modeId)
                }
            },
            onOpenPreview = { ctx.navigate(AppNavKey.HomeFreeWindowPreview) },
        )
    }

    entry<AppNavKey.HomeFreeWindowPreview> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        FreeWindowPreviewScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeFreeWindow) },
            onSave = { width, height, left, top ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setFreeWindowLayout(width, height, left, top)
                }
            },
        )
    }

    entry<AppNavKey.HomeTriggerCollection> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        TriggerCollectionScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onOpenLeftTrigger = { handleId ->
                ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.LEFT.toNavSide(), handleId))
            },
            onOpenRightTrigger = { handleId ->
                ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.RIGHT.toNavSide(), handleId))
            },
            onAddTriggerPair = {
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.addTriggerHandlePair()
                }
            },
            onRemoveTriggerHandle = { side, handleId ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.removeTriggerHandle(side, handleId)
                }
            },
        )
    }

    entry<AppNavKey.HomeSideGestures> { key ->
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        val side = key.side.toPanelSide()
        SideGestureSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = ctx.gestureActive(settings, permissions),
            onBack = {
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                ctx.navigateBackTo(AppNavKey.HomeTriggerCollection)
            },
            onOpenAppearanceSettings = {
                ctx.navigate(AppNavKey.HomeSideGesturesAppearance(key.side, key.handleId))
            },
            onOpenDesignSettings = {
                ctx.navigate(AppNavKey.HomeSideGesturesDesign(key.side, key.handleId))
            },
            onSlotConfigChange = { handleId, trigger, action, mode ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setSlotConfig(side, trigger, action, mode, handleId)
                }
            },
            onDefaultTriggerModeChange = { mode ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setDefaultTriggerMode(side, mode)
                }
            },
        )
    }

    entry<AppNavKey.HomeSideGesturesAppearance> { key ->
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        val side = key.side.toPanelSide()
        TriggerAppearanceSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = ctx.gestureActive(settings, permissions),
            onBack = {
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                ctx.navigateBackTo(AppNavKey.HomeSideGestures(key.side, key.handleId))
            },
            onShortSwipeDistanceChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setShortSwipeDistanceDp(side, key.handleId, value)
                }
            },
            onLongSwipeDistanceChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setLongSwipeDistanceDp(side, key.handleId, value)
                }
            },
            onEdgeWidthChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setEdgeTriggerWidthDp(side, value)
                }
            },
            onTriggerVerticalRangeChange = { handleId, top, bottom ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setTriggerVerticalRange(side, handleId, top, bottom)
                }
            },
            onAlignHandlesChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setTriggerAlignOppositeSide(
                        handleId = key.handleId,
                        sourceSide = side,
                        enabled = enabled,
                    )
                }
            },
            onInterceptBackChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setInterceptSystemBackGesture(enabled)
                }
            },
            onLimitInterceptLengthChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setLimitMaxInterceptLength(enabled)
                }
            },
            onLayoutPreviewStart = {
                ctx.sendOverlayPreviewIntent(
                    OverlayService.ACTION_PREVIEW_START,
                    LayoutPreviewContent.TRIGGER_ONLY,
                )
            },
            onLayoutPreviewStop = {
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
            },
        )
    }

    entry<AppNavKey.HomeSideGesturesDesign> { key ->
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        val side = key.side.toPanelSide()
        TriggerDesignSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = ctx.gestureActive(settings, permissions),
            onBack = {
                ctx.navigateBackTo(AppNavKey.HomeSideGestures(key.side, key.handleId))
            },
            onDesignChange = { design ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setTriggerHandleDesign(side, key.handleId, design)
                }
            },
            onPresetApply = { preset ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.applyTriggerDesignPreset(side, key.handleId, preset)
                }
            },
            onResetDefaults = {
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setTriggerHandleDesign(
                        side,
                        key.handleId,
                        TriggerHandleDesign(),
                    )
                }
            },
        )
    }

    entry<AppNavKey.HomeGestureAngle> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        GestureAngleSettingsScreen(
            config = settings.gestureAngleConfig,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onSave = { config ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setGestureAngleConfig(config)
                }
            },
        )
    }

    entry<AppNavKey.HomeAnimationStyleSelect> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        AnimationStyleSelectScreen(
            settings = settings,
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onStyleSelected = { style ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setGestureHintStyle(style)
                }
            },
            onOpenStyleConfig = { style ->
                ctx.navigate(
                    when (style) {
                        GestureHintStyle.WAVE -> AppNavKey.HomeWaveAnimationStyle
                        GestureHintStyle.CAPSULE -> AppNavKey.HomeCapsuleAnimationStyle
                        GestureHintStyle.BUBBLE -> AppNavKey.HomeBubbleAnimationStyle
                    },
                )
            },
        )
    }

    entry<AppNavKey.HomeWaveAnimationStyle> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        WaveStyleSettingsScreen(
            style = settings.activeWaveStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = { style ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.updateWaveStyle(style)
                }
            },
        )
    }

    entry<AppNavKey.HomeCapsuleAnimationStyle> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        CapsuleStyleSettingsScreen(
            style = settings.activeCapsuleStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = { style ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.updateCapsuleStyle(style)
                }
            },
        )
    }

    entry<AppNavKey.HomeBubbleAnimationStyle> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        BubbleStyleSettingsScreen(
            style = settings.activeBubbleStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = { style ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.updateBubbleStyle(style)
                }
            },
        )
    }
}
