package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.activeBubbleStyle
import com.slideindex.app.settings.activeCapsuleStyle
import com.slideindex.app.settings.activeWaveStyle
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
import com.slideindex.app.ui.viewmodel.HomeDetailSettingsViewModel
import com.slideindex.app.ui.viewmodel.HomeViewModel
import com.slideindex.app.ui.viewmodel.KeepAliveSettingsViewModel
import com.slideindex.app.ui.viewmodel.MainNavHomeEffects
import com.slideindex.app.ui.viewmodel.MainNavKeepAliveEffects

fun EntryProviderScope<AppNavKey>.homeNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.HomeMain> {
        val permissions = ctx.collectPermissions()
        val homeEffects = remember(ctx) { MainNavHomeEffects(ctx) }
        val viewModel: HomeViewModel = hiltViewModel<HomeViewModel, HomeViewModel.Factory> { factory ->
            factory.create(homeEffects)
        }
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
        val keepAliveEffects = remember(ctx) { MainNavKeepAliveEffects(ctx) }
        val viewModel: KeepAliveSettingsViewModel =
            hiltViewModel<KeepAliveSettingsViewModel, KeepAliveSettingsViewModel.Factory> { factory ->
                factory.create(keepAliveEffects)
            }
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            onHideFromRecentsChange = viewModel::setHideFromRecents,
            onAccessibilityKeepAliveChange = viewModel::setAccessibilityKeepAliveEnabled,
            onRequestSecureSettingsGrant = { ctx.requestSecureSettingsGrant() },
        )
    }

    entry<AppNavKey.HomeLayout> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        LayoutSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(settings, permissions),
            onBack = {
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                ctx.replaceRoot(AppNavKey.HomeMain)
            },
            onIndexHeightChange = viewModel::setIndexHeightFraction,
            onAppsPerRowChange = viewModel::setAppsPerRow,
            onPanelOpacityChange = viewModel::setPanelOpacity,
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
            onDebugPerformanceMonitorChange = viewModel::setDebugPerformanceMonitorEnabled,
        )
    }

    entry<AppNavKey.HomeHiddenApps> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        HiddenAppsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeLayout) },
            onHideApp = viewModel::addHiddenApp,
            onUnhideApp = viewModel::removeHiddenApp,
        )
    }

    entry<AppNavKey.HomeExcludedApps> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        ExcludedAppsScreen(
            settings = settings,
            usageAccessGranted = permissions.usageAccessGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onRequestUsageAccess = { ctx.openUsageAccessSettings() },
            onExcludeApp = viewModel::addExcludedTriggerApp,
            onRemoveExcludedApp = viewModel::removeExcludedTriggerApp,
        )
    }

    entry<AppNavKey.HomeFreeWindow> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        FreeWindowSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setFreeWindowEnabled,
            onLaunchPolicyChange = viewModel::setAppLaunchPolicyId,
            onLongPressDurationChange = viewModel::setLongPressLaunchDurationMs,
            onModeChange = viewModel::setFreeWindowModeId,
            onOpenPreview = { ctx.navigate(AppNavKey.HomeFreeWindowPreview) },
        )
    }

    entry<AppNavKey.HomeFreeWindowPreview> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        FreeWindowPreviewScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeFreeWindow) },
            onSave = viewModel::setFreeWindowLayout,
        )
    }

    entry<AppNavKey.HomeTriggerCollection> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            onAddTriggerPair = viewModel::addTriggerHandlePair,
            onRemoveTriggerHandle = viewModel::removeTriggerHandle,
        )
    }

    entry<AppNavKey.HomeSideGestures> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
                viewModel.setSlotConfig(side, trigger, action, mode, handleId)
            },
            onDefaultTriggerModeChange = { mode -> viewModel.setDefaultTriggerMode(side, mode) },
        )
    }

    entry<AppNavKey.HomeSideGesturesAppearance> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
                viewModel.setShortSwipeDistanceDp(side, key.handleId, value)
            },
            onLongSwipeDistanceChange = { value ->
                viewModel.setLongSwipeDistanceDp(side, key.handleId, value)
            },
            onEdgeWidthChange = { value -> viewModel.setEdgeTriggerWidthDp(side, value) },
            onTriggerVerticalRangeChange = { handleId, top, bottom ->
                viewModel.setTriggerVerticalRange(side, handleId, top, bottom)
            },
            onAlignHandlesChange = { enabled ->
                viewModel.setTriggerAlignOppositeSide(key.handleId, side, enabled)
            },
            onInterceptBackChange = viewModel::setInterceptSystemBackGesture,
            onLimitInterceptLengthChange = viewModel::setLimitMaxInterceptLength,
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
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            onDesignChange = { design -> viewModel.setTriggerHandleDesign(side, key.handleId, design) },
            onPresetApply = { preset -> viewModel.applyTriggerDesignPreset(side, key.handleId, preset) },
            onResetDefaults = {
                viewModel.setTriggerHandleDesign(side, key.handleId, TriggerHandleDesign())
            },
        )
    }

    entry<AppNavKey.HomeGestureAngle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        GestureAngleSettingsScreen(
            config = settings.gestureAngleConfig,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onSave = viewModel::setGestureAngleConfig,
        )
    }

    entry<AppNavKey.HomeAnimationStyleSelect> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        AnimationStyleSelectScreen(
            settings = settings,
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onStyleSelected = viewModel::setGestureHintStyle,
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
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        WaveStyleSettingsScreen(
            style = settings.activeWaveStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = viewModel::updateWaveStyle,
        )
    }

    entry<AppNavKey.HomeCapsuleAnimationStyle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        CapsuleStyleSettingsScreen(
            style = settings.activeCapsuleStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = viewModel::updateCapsuleStyle,
        )
    }

    entry<AppNavKey.HomeBubbleAnimationStyle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        BubbleStyleSettingsScreen(
            style = settings.activeBubbleStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = viewModel::updateBubbleStyle,
        )
    }
}
