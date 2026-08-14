package com.slideindex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.preferredTriggerMode
import com.slideindex.app.gesture.supportsAction
import com.slideindex.app.settings.AppLaunchPolicy
import com.slideindex.app.settings.descRes
import com.slideindex.app.settings.titleRes
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.defaultTriggerModeFor
import com.slideindex.app.settings.displayTriggerMode
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.QuickLauncherPanelPickScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.SideGestureSlotConfigScreen
import com.slideindex.app.ui.SideGestureTriggerModePickerScreen
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.settings.FreeWindowUiSettings
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.settings.forLandscapeEditing
import com.slideindex.app.ui.trigger.TriggerLandscapeOrientationEffect
import com.slideindex.app.ui.trigger.TriggerSettingsLandscapeSession
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsRadioPickerScreen
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.activeBubbleStyle
import com.slideindex.app.settings.activeCapsuleStyle
import com.slideindex.app.settings.activeWaveStyle
import com.slideindex.app.ui.AppKeepAliveSettingsScreen
import com.slideindex.app.ui.ExcludedAppsScreen
import com.slideindex.app.ui.ExcludedAppPickScreen
import com.slideindex.app.ui.CornerGestureInteractionScreen
import com.slideindex.app.ui.CornerGestureSettingsScreen
import com.slideindex.app.ui.CornerGestureSlotsSettingsScreen
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
        val settings by viewModel.homeMainSettings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        MainScreen(
            settings = settings,
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
            notificationGranted = permissions.notificationGranted,
            shizukuGranted = permissions.shizukuGranted,
            accessibilityGranted = permissions.accessibilityGranted,
            batteryOptimizationExempt = permissions.batteryOptimizationExempt,
            onRequestNotification = { viewModel.requestNotificationPermission() },
            onRequestShizuku = { viewModel.requestShizuku() },
            onRequestAccessibility = { viewModel.openAccessibilitySettings() },
            onRequestBatteryOptimization = { ctx.requestBatteryOptimization() },
            onGestureEnabledChange = { enabled -> viewModel.setServiceEnabled(enabled) },
            onOpenAppKeepAliveSettings = { ctx.navigate(AppNavKey.HomeAppKeepAlive) },
            onOpenFloatBallSettings = { ctx.navigate(AppNavKey.FloatBall) },
            onHapticEnabledChange = { enabled -> viewModel.setHapticEnabled(enabled) },
            onHapticStrengthChange = { level -> viewModel.setHapticStrength(level) },
            onOpenFreeWindowSettings = { ctx.navigate(AppNavKey.HomeFreeWindow) },
            onOpenExcludedAppsSettings = { ctx.navigate(AppNavKey.HomeExcludedApps) },
            onOpenTriggerCollection = { ctx.navigate(AppNavKey.HomeTriggerCollection) },
            onOpenCornerWheel = { ctx.navigate(AppNavKey.HomeCornerGesture) },
            onOpenGestureAngle = { ctx.navigate(AppNavKey.HomeGestureAngle) },
            onOpenAnimationStyleSelect = { ctx.navigate(AppNavKey.HomeAnimationStyleSelect) },
            onGestureHintEnabledChange = { enabled -> viewModel.setGestureHintEnabled(enabled) },
            onHideTriggerInLandscapeChange = { enabled -> viewModel.setHideTriggerInLandscape(enabled) },
            onHideTriggerOnLockScreenChange = { enabled -> viewModel.setHideTriggerOnLockScreen(enabled) },
            onHideTriggerOnLauncherChange = { enabled -> viewModel.setHideTriggerOnLauncher(enabled) },
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
            onDynamicColorChange = { enabled -> viewModel.setDynamicColorEnabled(enabled) },
            onThemeColorChange = { color -> viewModel.setThemeColor(color) },
            onThemePaletteStyleChange = { style -> viewModel.setThemePaletteStyle(style) },
            onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
            onCustomColorChange = { enabled -> viewModel.setCustomColorEnabled(enabled) },
            onThemeColorSpecChange = { spec -> viewModel.setThemeColorSpec(spec) },
            onBottomNavStyleChange = { style -> viewModel.setBottomNavStyle(style) },
            onBottomNavModeChange = { mode -> viewModel.setBottomNavMode(mode) },
            onBottomNavGlassEnabledChange = { enabled -> viewModel.setBottomNavGlassEnabled(enabled) },
            onBottomNavBlurRadiusChange = { value -> viewModel.setBottomNavBlurRadiusDp(value) },
            onBottomNavBlurPreviewChange = ctx.onBottomNavBlurPreviewChange,
            onBottomNavBlurPreviewStop = ctx.onBottomNavBlurPreviewStop,
        )
    }

    entry<AppNavKey.HomeAppKeepAlive> {
        val keepAliveEffects = remember(ctx) { MainNavKeepAliveEffects(ctx) }
        val viewModel: KeepAliveSettingsViewModel =
            hiltViewModel<KeepAliveSettingsViewModel, KeepAliveSettingsViewModel.Factory> { factory ->
                factory.create(keepAliveEffects)
            }
        val keepAliveSettings by viewModel.keepAliveUiSettings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        AppKeepAliveSettingsScreen(
            hideFromRecents = keepAliveSettings.hideFromRecents,
            batteryOptimizationExempt = permissions.batteryOptimizationExempt,
            accessibilityKeepAliveEnabled = keepAliveSettings.accessibilityKeepAliveEnabled,
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

    entry<AppNavKey.HomeExcludedApps> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ExcludedAppsScreen(
            settings = settings,
            usageAccessGranted = permissions.usageAccessGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onRequestUsageAccess = { ctx.openUsageAccessSettings() },
            onOpenAddApp = { ctx.navigate(AppNavKey.HomeExcludedAppsPick) },
            onRemoveExcludedApp = { packageName -> viewModel.removeExcludedTriggerApp(packageName) },
            onExcludedAppScopesChange = { packageName, scopes ->
                viewModel.setExcludedAppScopes(packageName, scopes)
            },
        )
    }

    entry<AppNavKey.HomeExcludedAppsPick> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ExcludedAppPickScreen(
            excludedPackages = settings.excludedAppScopes.keys,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeExcludedApps) },
            onConfirmAdd = { packageName, scopes ->
                viewModel.addExcludedTriggerApp(packageName)
                viewModel.setExcludedAppScopes(packageName, scopes)
            },
        )
    }

    entry<AppNavKey.HomeFreeWindow> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val freeWindowSettings by viewModel.freeWindowUiSettings.collectAsStateWithLifecycle()
        val settings = freeWindowSettings.toMinimalAppSettings()
        FreeWindowSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setFreeWindowEnabled,
            onLongPressDurationChange = viewModel::setLongPressLaunchDurationMs,
            onLaunchPolicyChange = viewModel::setAppLaunchPolicyId,
            onOpenMode = { ctx.navigate(AppNavKey.HomeFreeWindowMode) },
            onOpenPreview = { ctx.navigate(AppNavKey.HomeFreeWindowPreview) },
        )
    }

    entry<AppNavKey.HomeFreeWindowMode> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val freeWindowSettings by viewModel.freeWindowUiSettings.collectAsStateWithLifecycle()
        val settings = freeWindowSettings.toMinimalAppSettings()
        val selectedMode = settings.resolvedFreeWindowMode()
        val freeWindowModeItems = buildList {
            FreeWindowMode.entries.forEach { mode ->
                add(
                    settingsCardScopeItem("mode-${mode.id}") {
                        SettingRadioRow(
                            title = ctx.activity.getString(mode.titleRes),
                            subtitle = ctx.activity.getString(mode.descRes),
                            selected = mode.id == selectedMode.id,
                            onClick = {
                                viewModel.setFreeWindowModeId(mode.id)
                                ctx.navigateBackTo(AppNavKey.HomeFreeWindow)
                            },
                        )
                    },
                )
            }
        }
        SettingsRadioPickerScreen(
            title = ctx.activity.getString(com.slideindex.app.R.string.free_window_mode_dialog_title),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeFreeWindow) },
            items = freeWindowModeItems,
        )
    }

    entry<AppNavKey.HomeFreeWindowPreview> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val freeWindowSettings by viewModel.freeWindowUiSettings.collectAsStateWithLifecycle()
        val settings = freeWindowSettings.toMinimalAppSettings()
        FreeWindowPreviewScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeFreeWindow) },
            onSave = viewModel::setFreeWindowLayout,
        )
    }

    entry<AppNavKey.HomeTriggerCollection> {
        HomeTriggerCollectionRoute(ctx)
    }

    entry<AppNavKey.HomeTriggerCollectionLandscape> {
        HomeTriggerCollectionRoute(ctx, initialManualLandscapeOverride = true)
    }

    entry<AppNavKey.HomeCornerGesture> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings().copy(
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
        )
        val permissions = ctx.collectPermissions()
        CornerGestureSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setCornerGestureEnabled,
            onLeftEnabledChange = viewModel::setCornerGestureLeftEnabled,
            onRightEnabledChange = viewModel::setCornerGestureRightEnabled,
            onOpenInteractionAppearance = { ctx.navigate(AppNavKey.HomeCornerGestureInteraction) },
            onOpenSlots = { ctx.navigate(AppNavKey.HomeCornerGestureSlots) },
        )
    }

    entry<AppNavKey.HomeCornerGestureInteraction> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings().copy(
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
        )
        val permissions = ctx.collectPermissions()
        CornerGestureInteractionScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeCornerGesture) },
            onVerticalEdgeWidthChange = viewModel::setCornerGestureVerticalEdgeWidthDp,
            onVerticalEdgeHeightChange = viewModel::setCornerGestureVerticalEdgeHeightDp,
            onHorizontalEdgeWidthChange = viewModel::setCornerGestureHorizontalEdgeWidthDp,
            onHorizontalEdgeHeightChange = viewModel::setCornerGestureHorizontalEdgeHeightDp,
            onZonePreviewStart = { ctx.startCornerZonePreview() },
            onZonePreviewStop = { ctx.stopCornerZonePreview() },
            onZonePreviewDimensionsChange = { vWidth, vHeight, hWidth, hHeight ->
                ctx.updateCornerZonePreview(vWidth, vHeight, hWidth, hHeight)
            },
            onTriggerSlopChange = viewModel::setCornerGestureTriggerSlopDp,
            onHideInLandscapeChange = viewModel::setCornerGestureHideInLandscape,
            onLandscapePreventFalseTouchChange = viewModel::setCornerGestureLandscapePreventFalseTouch,
            onOverrideSystemNavChange = viewModel::setCornerGestureOverrideSystemNav,
            onOuterDiameterChange = viewModel::setCornerGestureOuterDiameterDp,
            onInnerDiameterChange = viewModel::setCornerGestureInnerDiameterDp,
            onBubbleSizeChange = viewModel::setCornerGestureBubbleSizeDp,
            onCancelOutsideWheelChange = viewModel::setCornerGestureCancelOutsideWheel,
            onProgressiveLayersChange = viewModel::setCornerGestureProgressiveLayers,
            onSlotHapticChange = viewModel::setCornerGestureSlotHaptic,
            onShowSelectedNameChange = viewModel::setCornerGestureShowSelectedName,
            onSelectedHintIconSizeChange = viewModel::setCornerGestureSelectedHintIconSizeDp,
            onBackgroundStyleChange = viewModel::setCornerGestureBackgroundStyle,
            onBlurDpChange = viewModel::setCornerGestureBlurDp,
            onDimPercentChange = viewModel::setCornerGestureDimPercent,
        )
    }

    entry<AppNavKey.HomeCornerGestureSlots> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings().copy(
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
        )
        val permissions = ctx.collectPermissions()
        CornerGestureSlotsSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeCornerGesture) },
            onUnifiedSlotsChange = viewModel::setCornerGestureUnifiedSlots,
            onOpenInnerZoneActionPick = { ctx.navigate(AppNavKey.HomeCornerGestureInnerZoneActionPick) },
            onOpenLeftSlotActionPick = { slotIndex ->
                ctx.navigate(AppNavKey.HomeCornerGestureSlotActionPick("left", slotIndex))
            },
            onOpenRightSlotActionPick = { slotIndex ->
                ctx.navigate(AppNavKey.HomeCornerGestureSlotActionPick("right", slotIndex))
            },
        )
    }

    entry<AppNavKey.HomeCornerGestureInnerZoneActionPick> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val corner = overlaySettings.cornerGestureSettings
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = corner.innerZoneAction,
            onDismiss = { ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots) },
            onSelect = { action ->
                if (action is GestureAction.FloatingPointer) return@GestureActionPickerScreen
                viewModel.setCornerGestureInnerZoneAction(action)
                ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots)
            },
            includeCornerInnerZoneActions = true,
        )
    }

    entry<AppNavKey.HomeCornerGestureSlotActionPick> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val corner = overlaySettings.cornerGestureSettings
        val current = when (key.corner) {
            "right" -> {
                if (corner.unifiedSlots) {
                    corner.leftSlots.getOrElse(key.slotIndex) { GestureAction.None }
                } else {
                    corner.rightSlots.getOrElse(key.slotIndex) { GestureAction.None }
                }
            }
            else -> corner.leftSlots.getOrElse(key.slotIndex) { GestureAction.None }
        }
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            onDismiss = { ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots) },
            onSelect = { action ->
                if (action is GestureAction.FloatingPointer) return@GestureActionPickerScreen
                if (corner.unifiedSlots || key.corner != "right") {
                    viewModel.setCornerGestureLeftSlotAction(key.slotIndex, action)
                } else {
                    viewModel.setCornerGestureRightSlotAction(key.slotIndex, action)
                }
                ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots)
            },
        )
    }

    entry<AppNavKey.HomeSideGestures> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        val collectionBackKey = AppNavKey.HomeTriggerCollection
        SideGestureSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = true,
            onBack = {
                ctx.stopTriggerPreview()
                ctx.navigateBackTo(collectionBackKey)
            },
            onPreviewStart = {
                ctx.startFocusedTriggerPreview(side, key.handleId)
            },
            onPreviewStop = {
                ctx.releaseFocusedTriggerPreview()
            },
            onOpenAppearanceSettings = {
                ctx.navigate(AppNavKey.HomeSideGesturesAppearance(key.side, key.handleId))
            },
            onOpenDesignSettings = {
                ctx.navigate(AppNavKey.HomeSideGesturesDesign(key.side, key.handleId))
            },
            onOpenDefaultModePick = {
                ctx.navigate(AppNavKey.HomeSideGesturesDefaultMode(key.side, key.handleId))
            },
            onOpenSlotConfig = { trigger ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotConfig(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = trigger.id,
                    ),
                )
            },
            onAlignOppositeGesturesChange = { enabled, mirrorSourceSide ->
                if (enabled && mirrorSourceSide != null) {
                    viewModel.setTriggerAlignOppositeGestures(key.handleId, mirrorSourceSide, true)
                } else if (!enabled) {
                    viewModel.setTriggerAlignOppositeGestures(key.handleId, side, false)
                }
            },
        )
    }

    entry<AppNavKey.HomeSideGesturesDefaultMode> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        SideGestureTriggerModePickerScreen(
            title = ctx.activity.getString(com.slideindex.app.R.string.default_trigger_mode),
            current = settings.defaultTriggerModeFor(configSide),
            action = GestureAction.None,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            includeDefaultOption = false,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeSideGestures(key.side, key.handleId)) },
            onSelect = { mode ->
                viewModel.setDefaultTriggerMode(side, mode, key.handleId)
                ctx.navigateBackTo(AppNavKey.HomeSideGestures(key.side, key.handleId))
            },
        )
    }

    entry<AppNavKey.HomeSideGestureSlotConfig> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val returnKey = AppNavKey.HomeSideGestures(key.side, key.handleId)
        SideGestureSlotConfigScreen(
            side = side,
            handleId = key.handleId,
            trigger = trigger,
            settings = settings,
            onBack = { ctx.navigateBackTo(returnKey) },
            onOpenActionPick = {
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotActionPick(key.side, key.handleId, key.triggerId),
                )
            },
            onOpenModePick = {
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotModePick(key.side, key.handleId, key.triggerId),
                )
            },
            onOpenShellCommand = { command ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotShellCommand(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = key.triggerId,
                        initialCommand = command,
                    ),
                )
            },
            onOpenQuickLauncherPanel = { panelId ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotQuickLauncherPanel(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = key.triggerId,
                        panelId = panelId,
                    ),
                )
            },
        )
    }

    entry<AppNavKey.HomeSideGestureSlotQuickLauncherPanel> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(key.side, key.handleId, key.triggerId)
        QuickLauncherPanelPickScreen(
            settings = settings,
            currentPanelId = key.panelId,
            onBack = { ctx.navigateBackTo(slotConfigKey) },
            onSelect = { panel ->
                viewModel.setSlotConfig(
                    side,
                    trigger,
                    GestureAction.QuickLauncher(panel.id),
                    settings.displayTriggerMode(configSide, trigger, key.handleId),
                    key.handleId,
                )
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    entry<AppNavKey.HomeSideGestureSlotActionPick> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val context = LocalContext.current
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(key.side, key.handleId, key.triggerId)
        val currentAction = settings.actionFor(configSide, trigger, key.handleId)
        val currentMode = settings.displayTriggerMode(configSide, trigger, key.handleId)
        GestureActionPickerScreen(
            trigger = trigger,
            current = currentAction,
            onDismiss = { ctx.navigateBackTo(slotConfigKey) },
            onSelect = { action ->
                requestPermissionForAdjustAction(context, action)
                val resolved = when (action) {
                    is GestureAction.QuickLauncher -> {
                        val panelId = action.panelId.ifBlank {
                            QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels).first().id
                        }
                        action.copy(panelId = panelId)
                    }
                    else -> action
                }
                val mode = if (!currentMode.supportsAction(resolved, trigger)) {
                    resolved.preferredTriggerMode(trigger) ?: GestureTriggerMode.ON_RELEASE
                } else {
                    currentMode
                }
                viewModel.setSlotConfig(side, trigger, resolved, mode, key.handleId)
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    entry<AppNavKey.HomeSideGestureSlotModePick> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(key.side, key.handleId, key.triggerId)
        val currentAction = settings.actionFor(configSide, trigger, key.handleId)
        SideGestureTriggerModePickerScreen(
            title = ctx.activity.getString(com.slideindex.app.R.string.slot_pick_trigger_mode),
            current = settings.displayTriggerMode(configSide, trigger, key.handleId),
            action = currentAction,
            trigger = trigger,
            sideDefaultMode = settings.defaultTriggerModeFor(configSide),
            includeDefaultOption = true,
            onBack = { ctx.navigateBackTo(slotConfigKey) },
            onSelect = { mode ->
                viewModel.setSlotConfig(
                    side,
                    trigger,
                    currentAction,
                    mode,
                    key.handleId,
                )
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    entry<AppNavKey.HomeSideGestureSlotShellCommand> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(key.side, key.handleId, key.triggerId)
        val currentMode = settings.displayTriggerMode(configSide, trigger, key.handleId)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setSlotConfig(
                    side,
                    trigger,
                    GestureAction.ExecuteShellCommand(command),
                    currentMode,
                    key.handleId,
                )
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    entry<AppNavKey.HomeSideGesturesAppearance> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        TriggerAppearanceSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = true,
            onBack = {
                ctx.navigateBackTo(AppNavKey.HomeSideGestures(key.side, key.handleId))
            },
            onShortSwipeDistanceChange = { value ->
                viewModel.setShortSwipeDistanceDp(side, key.handleId, value)
            },
            onLongSwipeDistanceChange = { value ->
                viewModel.setLongSwipeDistanceDp(side, key.handleId, value)
            },
            onEdgeWidthChange = { value ->
                viewModel.setTriggerEdgeWidthDp(side, key.handleId, value)
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
            onTriggerVerticalRangeChange = { handleId, top, bottom ->
                viewModel.setTriggerVerticalRange(side, handleId, top, bottom)
            },
            onEdgeWidthPreviewChange = { value ->
                ctx.previewTriggerHandleEdgeWidth(side, key.handleId, value)
            },
            onTriggerVerticalRangePreviewChange = { top, bottom ->
                ctx.previewTriggerHandleVerticalRange(side, key.handleId, top, bottom)
            },
            onShortSwipeDistancePreviewChange = { value ->
                ctx.previewTriggerHandleSwipeDistances(
                    side,
                    key.handleId,
                    shortSwipeDistanceDp = value,
                )
            },
            onLongSwipeDistancePreviewChange = { value ->
                ctx.previewTriggerHandleSwipeDistances(
                    side,
                    key.handleId,
                    longSwipeDistanceDp = value,
                )
            },
            onTriggerLayoutPreviewStop = { ctx.clearTriggerHandleLayoutPreview() },
            onAlignHandlesChange = { enabled ->
                viewModel.setTriggerAlignOppositeSide(key.handleId, side, enabled)
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
            onInterceptBackChange = viewModel::setInterceptSystemBackGesture,
            onLimitInterceptLengthChange = viewModel::setLimitMaxInterceptLength,
            onApplyBackGestureRecommendation = {
                viewModel.setInterceptSystemBackGesture(true)
                viewModel.setLimitMaxInterceptLength(true)
            },
            onPreviewStart = {
                ctx.startFocusedTriggerPreview(side, key.handleId)
            },
            onPreviewStop = {
                ctx.clearTriggerHandleLayoutPreview()
                ctx.releaseFocusedTriggerPreview()
            },
            onLayoutPreviewStart = {
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
            onSwipeDistancePreviewStart = {
                ctx.refreshSwipeDistancePreview(side, key.handleId)
            },
            onSwipeDistancePreviewStop = {
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
        )
    }

    entry<AppNavKey.HomeSideGesturesDesign> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val landscapeEditing = TriggerSettingsLandscapeSession.active
        val settings = gestureSettings.toMinimalAppSettings().let { base ->
            if (landscapeEditing) base.forLandscapeEditing() else base
        }
        TriggerLandscapeOrientationEffect(landscapeEditing)
        val side = key.side.toPanelSide()
        TriggerDesignSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = true,
            onBack = {
                ctx.navigateBackTo(AppNavKey.HomeSideGestures(key.side, key.handleId))
            },
            onDesignChange = { design -> viewModel.setTriggerHandleDesign(side, key.handleId, design) },
            onPresetApply = { preset -> viewModel.applyTriggerDesignPreset(side, key.handleId, preset) },
            onAlignOppositeDesignChange = { enabled ->
                viewModel.setTriggerAlignOppositeDesign(key.handleId, side, enabled)
            },
            onResetDefaults = {
                viewModel.setTriggerHandleDesign(side, key.handleId, TriggerHandleDesign())
            },
            onPreviewStart = {
                ctx.startTriggerDesignPreview(side, key.handleId)
            },
            onPreviewStop = {
                ctx.clearTriggerHandleLayoutPreview()
                ctx.releaseFocusedTriggerPreview()
            },
            onDesignPreview = { design ->
                ctx.previewTriggerHandleDesign(side, key.handleId, design)
            },
            onDesignPreviewStop = { ctx.clearTriggerHandleLayoutPreview() },
        )
    }

    entry<AppNavKey.HomeGestureAngle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        GestureAngleSettingsScreen(
            angles = settings.gestureAngles,
            livePreviewEnabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onSave = { angles -> viewModel.saveGestureAngles(angles) },
            onPreviewStart = { ctx.startGestureAnglesPreview(it) },
            onPreviewAnglesChange = { ctx.updateGestureAnglesPreview(it) },
            onPreviewStop = { ctx.stopGestureAnglesPreview() },
        )
    }

    entry<AppNavKey.HomeAnimationStyleSelect> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        AnimationStyleSelectScreen(
            settings = settings,
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onStyleSelected = viewModel::setGestureHintStyle,
            onGestureHintFingerOffsetDpChange = viewModel::setGestureHintFingerOffsetDp,
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
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
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
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
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
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        BubbleStyleSettingsScreen(
            style = settings.activeBubbleStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = viewModel::updateBubbleStyle,
        )
    }
}

@Composable
private fun HomeTriggerCollectionRoute(
    ctx: MainNavContext,
    initialManualLandscapeOverride: Boolean? = null,
) {
    val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
    val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
    val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
    val settings = gestureSettings.toMinimalAppSettings().copy(
        cornerGestureSettings = overlaySettings.cornerGestureSettings,
    )
    TriggerCollectionScreen(
        settings = settings,
        serviceEnabled = true,
        initialManualLandscapeOverride = initialManualLandscapeOverride,
        onEnsureLandscapeInitialized = viewModel::ensureLandscapeTriggerHandlesInitialized,
        onBack = {
            TriggerSettingsLandscapeSession.releaseForExit(ctx.activity)
            ctx.navigateBackTo(AppNavKey.HomeMain)
        },
        onOpenLeftTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.LEFT.toNavSide(), handleId))
        },
        onOpenRightTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.RIGHT.toNavSide(), handleId))
        },
        onOpenBottomTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.BOTTOM.toNavSide(), handleId))
        },
        onOpenTopTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.TOP.toNavSide(), handleId))
        },
        onAddTriggerPair = viewModel::addTriggerHandlePair,
        onAddBottomTrigger = viewModel::addBottomTriggerHandle,
        onAddTopTrigger = viewModel::addTopTriggerHandle,
        onRemoveTriggerHandle = viewModel::removeTriggerHandle,
        onTriggerHandleEnabledChange = viewModel::setTriggerHandleEnabled,
    )
}

fun EntryProviderScope<AppNavKey>.layoutSettingsNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.HomeLayout> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        LayoutSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = {
                ctx.clearOverlayLayoutPreview()
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                ctx.navigateBackTo(AppNavKey.ExtensionHub)
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
                ctx.clearIndexHeightPreview()
                ctx.sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
            },
            onIndexHeightPreviewChange = { fraction ->
                ctx.previewIndexHeightFraction(fraction)
            },
            onDebugPerformanceMonitorChange = viewModel::setDebugPerformanceMonitorEnabled,
        )
    }

    entry<AppNavKey.HomeHiddenApps> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HiddenAppsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeLayout) },
            onHideApp = viewModel::addHiddenApp,
            onUnhideApp = viewModel::removeHiddenApp,
        )
    }
}
