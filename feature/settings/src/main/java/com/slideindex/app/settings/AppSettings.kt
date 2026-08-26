package com.slideindex.app.settings

import com.slideindex.app.message.MessageSettings
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.shake.FaceDownGestureSettings
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.backtap.BackTapSettings

/**
 * 全量设置快照。
 *
 * 主构造参数数量受 DEX 硬性约束：带默认值的构造器/`copy()` 调用点走 range 形式 invoke 指令，
 * 其寄存器计数字段仅 8 位，要求 `1 + P + ceil(P/32) + 1 <= 255`（即 P <= 245）。
 * 超限时 D8 不报错，运行期才由 ART 抛 VerifyError。因此高基数的域一律拆进
 * [AppSettingsSlices] 中的分片，此处仅以派生属性保持扁平读取 API。
 * 新增字段请放进对应分片，不要直接加在主构造上。
 */
data class AppSettings(
    val serviceEnabled: Boolean = false,
    val edgeTrigger: EdgeTriggerSettings = EdgeTriggerSettings(),
    val indexHeightFraction: Float = 0.42f,
    /** 为 true 时字母索引栏仅显示有应用的分组字母。 */
    val hideEmptyIndexLetters: Boolean = true,
    val appsPerRow: Int = 3,
    /** Fixed grid columns per quick-launcher page. */
    val quickLauncherColumnsPerPage: Int = 3,
    /** Fixed grid rows per quick-launcher page (panel height). */
    val quickLauncherRowsPerPage: Int = 4,
    val panelOpacity: Float = 0.95f,
    val hapticEnabled: Boolean = true,
    val hapticStrengthLevel: Int = HapticStrength.MEDIUM.level,
    val hideFromRecents: Boolean = false,
    /** Android 14+ 侧滑返回跟手动画（需系统预测性返回可用）。 */
    val predictiveBackEnabled: Boolean = false,
    /** 页内横移返回上一页（miuix-nav swipeDismiss）。 */
    val swipeDismissEnabled: Boolean = true,
    val accessibilityKeepAliveEnabled: Boolean = false,
    val hideTriggerInLandscape: Boolean = false,
    val hideTriggerOnLockScreen: Boolean = false,
    val hideTriggerOnLauncher: Boolean = false,
    val dynamicColorEnabled: Boolean = true,
    val freeWindowEnabled: Boolean = false,
    val freeWindowModeId: Int = FreeWindowMode.detectDefault().id,
    val freeWindowWidthFraction: Float = 0.8f,
    val freeWindowHeightFraction: Float = 0.55f,
    val freeWindowLeftFraction: Float = 0.1f,
    val freeWindowTopFraction: Float = 0.15f,
    val launcher: LauncherSettings = LauncherSettings(),
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val themePaletteStyleId: Int = ThemePaletteStyle.TONAL_SPOT.id,
    val themeModeId: Int = AppThemeMode.SYSTEM.id,
    /** 开启后使用种子色/壁纸 Monet 配色；关闭则使用 Miuix 默认蓝。 */
    val customColorEnabled: Boolean = false,
    val darkBackgroundStyleId: Int = DarkBackgroundStyle.QUIET_BLUE.id,
    val themeColorSpecId: Int = AppColorSpec.SPEC_2025.id,
    /** 底栏内容模式（图标+文字 / 仅图标）。 */
    val bottomNavStyleId: Int = BottomNavStyle.FLOATING_NAV.id,
    val bottomNavModeId: Int = BottomNavMode.ICON_AND_TEXT.id,
    /** 是否启用底部导航液态玻璃（backdrop 采样）；关闭时底栏为纯色。 */
    val bottomNavGlassEnabled: Boolean = true,
    val topAppBarBlurStyleId: Int = TopAppBarBlurStyle.PROGRESSIVE.id,
    val bottomNavClassicBlurRadiusDp: Float = BottomNavBlurDefaults.DEFAULT_RADIUS_DP,
    /** 液态玻璃底栏模糊强度（dp，用于是否启用 haze 阈值）。 */
    val bottomNavLiquidGlassBlurRadiusDp: Float = BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP,
    val bottomNavFloatingNavBlurRadiusDp: Float = BottomNavBlurDefaults.FLOATING_NAV_DEFAULT_RADIUS_DP,
    val widgetPanelPages: List<com.slideindex.app.widget.WidgetPanelPage> = emptyList(),
    val widgetPanelWidthFraction: Float = 0.8f,
    val widgetPanelHeightFraction: Float = 0.55f,
    val widgetPanelTopFraction: Float = 0.15f,
    val widgetPanelBlurEnabled: Boolean = true,
    val widgetPanelBlurRadiusDp: Int = WIDGET_PANEL_BLUR_RADIUS_DEFAULT_DP,
    val floatingPointer: FloatingPointerSettings = FloatingPointerSettings(),
    val otpCopyToClipboard: Boolean = false,
    val otpKeywordsRegex: String = OtpKeywords.DEFAULT_KEYWORDS_REGEX,
    val otpUserMatchRules: List<com.slideindex.app.otp.OtpMatchRule> = emptyList(),
    val otpDisabledOfficialRuleIds: Set<String> = emptySet(),
    val otpAutoInputEnabled: Boolean = false,
    val otpAutoConfirmEnabled: Boolean = false,
    val otpAutoInputDelayMs: Int = 0,
    val otpAutoInputIntervalMs: Int = 0,
    val otpLsposedSmsCaptureEnabled: Boolean = false,
    val otpLsposedSystemInjectEnabled: Boolean = true,
    val cornerGestureSettings: CornerGestureSettings = CornerGestureSettings(),
    val shakeGestureSettings: ShakeGestureSettings = ShakeGestureSettings(),
    val backTapSettings: BackTapSettings = BackTapSettings(),
    val faceDownGestureSettings: FaceDownGestureSettings = FaceDownGestureSettings(),
    val messageReminderSettings: MessageSettings = MessageSettings(),
    val debugPerformanceMonitorEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val floatBall: FloatBallSettings = FloatBallSettings(),
    val clipboard: ClipboardSettings = ClipboardSettings(),
    val defaultImageViewerPackage: String? = null,
    val searchPanel: SearchPanelSettings = SearchPanelSettings(),
) {
    /** 当前底栏样式的生效模糊半径（派生，不落盘）。 */
    val bottomNavBlurRadiusDp: Float
        get() = when (BottomNavStyle.fromId(bottomNavStyleId)) {
            BottomNavStyle.CLASSIC -> bottomNavClassicBlurRadiusDp
            BottomNavStyle.LIQUID_GLASS -> bottomNavLiquidGlassBlurRadiusDp
            BottomNavStyle.FLOATING_NAV -> bottomNavFloatingNavBlurRadiusDp
        }

    // region 分片字段的扁平别名：保持拆分前的读取 API 不变。

    val leftEdgeEnabled get() = edgeTrigger.leftEdgeEnabled
    val rightEdgeEnabled get() = edgeTrigger.rightEdgeEnabled
    val leftEdgeTriggerWidthDp get() = edgeTrigger.leftEdgeTriggerWidthDp
    val rightEdgeTriggerWidthDp get() = edgeTrigger.rightEdgeTriggerWidthDp
    val bottomEdgeTriggerWidthDp get() = edgeTrigger.bottomEdgeTriggerWidthDp
    val topEdgeTriggerWidthDp get() = edgeTrigger.topEdgeTriggerWidthDp
    val leftTriggerTopFraction get() = edgeTrigger.leftTriggerTopFraction
    val rightTriggerTopFraction get() = edgeTrigger.rightTriggerTopFraction
    val leftTriggerHeightFraction get() = edgeTrigger.leftTriggerHeightFraction
    val rightTriggerHeightFraction get() = edgeTrigger.rightTriggerHeightFraction
    val leftTriggerHandles get() = edgeTrigger.leftTriggerHandles
    val rightTriggerHandles get() = edgeTrigger.rightTriggerHandles
    val bottomTriggerHandles get() = edgeTrigger.bottomTriggerHandles
    val topTriggerHandles get() = edgeTrigger.topTriggerHandles
    val leftTriggerHandlesLandscape get() = edgeTrigger.leftTriggerHandlesLandscape
    val rightTriggerHandlesLandscape get() = edgeTrigger.rightTriggerHandlesLandscape
    val bottomTriggerHandlesLandscape get() = edgeTrigger.bottomTriggerHandlesLandscape
    val topTriggerHandlesLandscape get() = edgeTrigger.topTriggerHandlesLandscape
    val landscapeTriggersInitialized get() = edgeTrigger.landscapeTriggersInitialized
    val gestureRulesLandscape get() = edgeTrigger.gestureRulesLandscape
    val leftDefaultTriggerModeLandscape get() = edgeTrigger.leftDefaultTriggerModeLandscape
    val rightDefaultTriggerModeLandscape get() = edgeTrigger.rightDefaultTriggerModeLandscape
    val bottomDefaultTriggerModeLandscape get() = edgeTrigger.bottomDefaultTriggerModeLandscape
    val topDefaultTriggerModeLandscape get() = edgeTrigger.topDefaultTriggerModeLandscape
    val interceptSystemBackGesture get() = edgeTrigger.interceptSystemBackGesture
    val limitMaxInterceptLength get() = edgeTrigger.limitMaxInterceptLength
    val leftDefaultTriggerMode get() = edgeTrigger.leftDefaultTriggerMode
    val rightDefaultTriggerMode get() = edgeTrigger.rightDefaultTriggerMode
    val bottomDefaultTriggerMode get() = edgeTrigger.bottomDefaultTriggerMode
    val topDefaultTriggerMode get() = edgeTrigger.topDefaultTriggerMode
    val shortSwipeDistanceDp get() = edgeTrigger.shortSwipeDistanceDp
    val longSwipeDistanceDp get() = edgeTrigger.longSwipeDistanceDp
    val gestureHintEnabled get() = edgeTrigger.gestureHintEnabled
    val gestureHintStyleId get() = edgeTrigger.gestureHintStyleId
    val gestureHintFingerOffsetDp get() = edgeTrigger.gestureHintFingerOffsetDp
    val animationStyles get() = edgeTrigger.animationStyles
    val gestureAngles get() = edgeTrigger.gestureAngles

    val appLaunchPolicyId get() = launcher.appLaunchPolicyId
    val longPressLaunchDurationMs get() = launcher.longPressLaunchDurationMs
    val hiddenAppPackages get() = launcher.hiddenAppPackages
    val freezerAppPackages get() = launcher.freezerAppPackages
    val hideRecentTaskPackages get() = launcher.hideRecentTaskPackages
    val hideRecentPreviewPackages get() = launcher.hideRecentPreviewPackages
    val expandPanelSlotActions get() = launcher.expandPanelSlotActions
    val previousAppExcludedPackages get() = launcher.previousAppExcludedPackages
    val excludedAppScopes get() = launcher.excludedAppScopes
    val excludedAppDefaultScopes get() = launcher.excludedAppDefaultScopes
    val gestureRules get() = launcher.gestureRules
    val quickLauncherPanels get() = launcher.quickLauncherPanels
    val quickLauncherDisplay get() = launcher.quickLauncherDisplay
    val honeycombLauncher get() = launcher.honeycombLauncher
    val honeycombDisplay get() = launcher.honeycombDisplay
    val fvAppSwitcher get() = launcher.fvAppSwitcherVertical
    val fvAppSwitcherVertical get() = launcher.fvAppSwitcherVertical
    val fvAppSwitcherHorizontal get() = launcher.fvAppSwitcherHorizontal
    val fvAppSwitcherLinkAppearanceAxes get() = launcher.fvAppSwitcherLinkAppearanceAxes
    val fvAppSwitcherLinkSlotAxes get() = launcher.fvAppSwitcherLinkSlotAxes
    val holographicLauncher get() = launcher.holographicLauncher
    val shellCommands get() = launcher.shellCommands
    val activityShortcuts get() = launcher.activityShortcuts

    val floatingPointerSensitivityFraction get() = floatingPointer.floatingPointerSensitivityFraction
    val floatingPointerJoystickDiameterPx get() = floatingPointer.floatingPointerJoystickDiameterPx
    val floatingPointerPointerDiameterPx get() = floatingPointer.floatingPointerPointerDiameterPx
    val floatingPointerDesignId get() = floatingPointer.floatingPointerDesignId
    val floatingPointerRingThicknessPx get() = floatingPointer.floatingPointerRingThicknessPx
    val floatingPointerDotDiameterPx get() = floatingPointer.floatingPointerDotDiameterPx
    val floatingPointerRingColorArgb get() = floatingPointer.floatingPointerRingColorArgb
    val floatingPointerFillColorArgb get() = floatingPointer.floatingPointerFillColorArgb
    val floatingPointerDotColorArgb get() = floatingPointer.floatingPointerDotColorArgb
    val floatingPointerClickVisualFeedbackEnabled get() = floatingPointer.floatingPointerClickVisualFeedbackEnabled
    val floatingPointerClickHapticEnabled get() = floatingPointer.floatingPointerClickHapticEnabled
    val floatingPointerRippleColorArgb get() = floatingPointer.floatingPointerRippleColorArgb
    val floatingPointerRippleSizeDp get() = floatingPointer.floatingPointerRippleSizeDp
    val floatingPointerRippleDurationMs get() = floatingPointer.floatingPointerRippleDurationMs
    val floatingPointerTrailTypeId get() = floatingPointer.floatingPointerTrailTypeId
    val floatingPointerTrailDurationMs get() = floatingPointer.floatingPointerTrailDurationMs
    val floatingPointerTrailColorArgb get() = floatingPointer.floatingPointerTrailColorArgb
    val floatingPointerHideWhenJoystickReleased get() = floatingPointer.floatingPointerHideWhenJoystickReleased
    val floatingPointerClickDistanceThresholdDp get() = floatingPointer.floatingPointerClickDistanceThresholdDp
    val floatingPointerJoystickInnerColorArgb get() = floatingPointer.floatingPointerJoystickInnerColorArgb
    val floatingPointerJoystickOuterColorArgb get() = floatingPointer.floatingPointerJoystickOuterColorArgb
    val floatingPointerJoystickGradientRadiusFraction
        get() = floatingPointer.floatingPointerJoystickGradientRadiusFraction
    val floatingPointerHideOnOutsideClick get() = floatingPointer.floatingPointerHideOnOutsideClick
    val floatingPointerHideOnQuickSwipe get() = floatingPointer.floatingPointerHideOnQuickSwipe
    val floatingPointerHideWhenIdle get() = floatingPointer.floatingPointerHideWhenIdle
    val floatingPointerIdleHideDelayMs get() = floatingPointer.floatingPointerIdleHideDelayMs
    val floatingPointerReleaseClickAndDismiss get() = floatingPointer.floatingPointerReleaseClickAndDismiss
    val floatingPointerHoverEnterSelect get() = floatingPointer.floatingPointerHoverEnterSelect
    val floatingPointerJoystickLongPressAction get() = floatingPointer.floatingPointerJoystickLongPressAction
    val floatingPointerRadialAlwaysVisible get() = floatingPointer.floatingPointerRadialAlwaysVisible
    val floatingPointerRadialLongPressMs get() = floatingPointer.floatingPointerRadialLongPressMs
    val floatingPointerRadialOuterDiameterPx get() = floatingPointer.floatingPointerRadialOuterDiameterPx
    val floatingPointerRadialInnerDiameterPx get() = floatingPointer.floatingPointerRadialInnerDiameterPx
    val floatingPointerRadialOuterColorArgb get() = floatingPointer.floatingPointerRadialOuterColorArgb
    val floatingPointerRadialInnerColorArgb get() = floatingPointer.floatingPointerRadialInnerColorArgb
    val floatingPointerRadialDividerThicknessPx get() = floatingPointer.floatingPointerRadialDividerThicknessPx
    val floatingPointerRadialDividerColorArgb get() = floatingPointer.floatingPointerRadialDividerColorArgb
    val floatingPointerRadialIconSizeFraction get() = floatingPointer.floatingPointerRadialIconSizeFraction
    val floatingPointerRadialIconColorArgb get() = floatingPointer.floatingPointerRadialIconColorArgb
    val floatingPointerRadialSlotActions get() = floatingPointer.floatingPointerRadialSlotActions
    val floatingPointerEdgeThresholdDp get() = floatingPointer.floatingPointerEdgeThresholdDp
    val floatingPointerEdgePreviewSensitivity get() = floatingPointer.floatingPointerEdgePreviewSensitivity
    val floatingPointerEdgePreviewGlowSize get() = floatingPointer.floatingPointerEdgePreviewGlowSize
    val floatingPointerEdgePreviewShowIcon get() = floatingPointer.floatingPointerEdgePreviewShowIcon
    val floatingPointerEdgeVisualSizeDp get() = floatingPointer.floatingPointerEdgeVisualSizeDp
    val floatingPointerEdgeVisualOpacity get() = floatingPointer.floatingPointerEdgeVisualOpacity
    val floatingPointerEdgeVisualColorArgb get() = floatingPointer.floatingPointerEdgeVisualColorArgb
    val floatingPointerEdgeActionsConfig get() = floatingPointer.floatingPointerEdgeActionsConfig

    val floatBallEnabled get() = floatBall.floatBallEnabled
    val floatBallSizeDp get() = floatBall.floatBallSizeDp
    val floatBallPickCrossArmDp get() = floatBall.floatBallPickCrossArmDp
    val floatBallOpacity get() = floatBall.floatBallOpacity
    val floatBallVisibleFraction get() = floatBall.floatBallVisibleFraction
    val floatBallCustomCenterXFraction get() = floatBall.floatBallCustomCenterXFraction
    val floatBallPositionYFraction get() = floatBall.floatBallPositionYFraction
    val floatBallOcrFallbackEnabled get() = floatBall.floatBallOcrFallbackEnabled
    val floatBallOcrModelId get() = floatBall.floatBallOcrModelId
    val ocrDownloadWifiOnly get() = floatBall.ocrDownloadWifiOnly
    val floatBallPointerSpeedFraction get() = floatBall.floatBallPointerSpeedFraction
    val floatBallPointerSpeedVerticalFraction get() = floatBall.floatBallPointerSpeedVerticalFraction
    val floatBallPositionMode get() = floatBall.floatBallPositionMode
    val floatBallActiveSide get() = floatBall.floatBallActiveSide
    val floatBallLineHeightFraction get() = floatBall.floatBallLineHeightFraction
    val floatBallLineWidthFraction get() = floatBall.floatBallLineWidthFraction
    val floatBallLineOpacity get() = floatBall.floatBallLineOpacity
    val floatBallGestureActions get() = floatBall.floatBallGestureActions
    val floatBallStyleType get() = floatBall.floatBallStyleType
    val floatBallCustomImageUri get() = floatBall.floatBallCustomImageUri
    val floatBallSlideshowUris get() = floatBall.floatBallSlideshowUris
    val floatBallGifUri get() = floatBall.floatBallGifUri
    val floatBallPickOffsetDp get() = floatBall.floatBallPickOffsetDp
    val floatBallPickTextSizeSp get() = floatBall.floatBallPickTextSizeSp
    val floatBallPickBottomTransitionFraction get() = floatBall.floatBallPickBottomTransitionFraction
    val floatBallPickTextFirstPanel get() = floatBall.floatBallPickTextFirstPanel
    val floatBallPickPanelEnterAnimationMs get() = floatBall.floatBallPickPanelEnterAnimationMs
    val floatBallPickPanelExitAnimationMs get() = floatBall.floatBallPickPanelExitAnimationMs
    val floatBallPointerSlopDp get() = floatBall.floatBallPointerSlopDp
    val floatBallHoverPauseDelayMs get() = floatBall.floatBallHoverPauseDelayMs
    val floatBallRegionalCancelSlopDp get() = floatBall.floatBallRegionalCancelSlopDp
    val floatBallDownSwipeShortPercent get() = floatBall.floatBallDownSwipeShortPercent
    val floatBallSideSwipeShortPercent get() = floatBall.floatBallSideSwipeShortPercent
    val floatBallUpSwipeShortPercent get() = floatBall.floatBallUpSwipeShortPercent
    val floatBallInstantTranslate get() = floatBall.floatBallInstantTranslate
    val floatBallTranslateEngine get() = floatBall.floatBallTranslateEngine
    val floatBallTranslateTargetLang get() = floatBall.floatBallTranslateTargetLang
    val floatBallImageSearchPickPanelTransparency get() = floatBall.floatBallImageSearchPickPanelTransparency
    val shareImageOcrHistoryEnabled get() = floatBall.shareImageOcrHistoryEnabled

    val clipboardBackgroundMonitoring get() = clipboard.clipboardBackgroundMonitoring
    val clipboardBackgroundMonitoringMode get() = clipboard.clipboardBackgroundMonitoringMode
    val clipboardScreenshotMonitoring get() = clipboard.clipboardScreenshotMonitoring
    val clipboardHistoryMaxEntries get() = clipboard.clipboardHistoryMaxEntries
    val clipboardHistoryFloatEnabled get() = clipboard.clipboardHistoryFloatEnabled
    val clipboardHistoryFloatEnabledLandscape get() = clipboard.clipboardHistoryFloatEnabledLandscape
    val clipboardHistoryFloatLockPosition get() = clipboard.clipboardHistoryFloatLockPosition
    val clipboardHistoryFloatHandleWidthDp get() = clipboard.clipboardHistoryFloatHandleWidthDp
    val clipboardFloatEnabled get() = clipboard.clipboardFloatEnabled
    val clipboardFloatShowChip get() = clipboard.clipboardFloatShowChip
    val clipboardFloatChipFollowIme get() = clipboard.clipboardFloatChipFollowIme
    val clipboardFloatChipX get() = clipboard.clipboardFloatChipX
    val clipboardFloatChipY get() = clipboard.clipboardFloatChipY
    val clipboardFloatPanelPinPosition get() = clipboard.clipboardFloatPanelPinPosition
    val clipboardFloatEntryClickAction get() = clipboard.clipboardFloatEntryClickAction
    val clipboardFloatListStyleId get() = clipboard.clipboardFloatListStyleId
    val clipboardFloatListStyle get() = ClipboardFloatListStyle.fromId(clipboardFloatListStyleId)
    val clipboardFloatPortraitGeometry get() = clipboard.clipboardFloatPortraitGeometry
    val clipboardFloatLandscapeGeometry get() = clipboard.clipboardFloatLandscapeGeometry
    val clipboardFloatPanelWidthDp get() = clipboard.clipboardFloatPanelWidthDp
    val clipboardFloatPanelHeightDp get() = clipboard.clipboardFloatPanelHeightDp
    val clipboardFloatPanelX get() = clipboard.clipboardFloatPanelX
    val clipboardFloatPanelY get() = clipboard.clipboardFloatPanelY
    val clipboardFloatBlockedPackages get() = clipboard.clipboardFloatBlockedPackages
    val clipboardFloatPasteHapticEnabled get() = clipboard.clipboardFloatPasteHapticEnabled
    val clipboardFloatPasteSuccessCount get() = clipboard.clipboardFloatPasteSuccessCount
    val clipboardFloatPasteFailCount get() = clipboard.clipboardFloatPasteFailCount
    val clipboardFloatAlpha get() = clipboard.clipboardFloatAlpha
    val clipboardFloatAutoDimWhenUnfocused get() = clipboard.clipboardFloatAutoDimWhenUnfocused
    val clipboardFloatAutoCloseSeconds get() = clipboard.clipboardFloatAutoCloseSeconds
    val stashPanelBackgroundBlurEnabled get() = clipboard.stashPanelBackgroundBlurEnabled
    val stashPanelBackgroundBlurRadiusDp get() = clipboard.stashPanelBackgroundBlurRadiusDp

    val searchEngines get() = searchPanel.searchEngines
    val searchEngineGridColumns get() = searchPanel.searchEngineGridColumns
    val searchEngineGridRows get() = searchPanel.searchEngineGridRows
    val searchEngineShowLabels get() = searchPanel.searchEngineShowLabels
    val searchPanelDefaultEngineId get() = searchPanel.searchPanelDefaultEngineId
    val searchPanelInputBehavior get() = searchPanel.searchPanelInputBehavior
    val searchPanelContactSearchEnabled get() = searchPanel.searchPanelContactSearchEnabled
    val searchPanelFileSearchEnabled get() = searchPanel.searchPanelFileSearchEnabled
    val searchPanelAppSearchEnabled get() = searchPanel.searchPanelAppSearchEnabled
    val searchPanelSettingsSearchEnabled get() = searchPanel.searchPanelSettingsSearchEnabled
    val searchPanelFileTypesEnabled get() = searchPanel.searchPanelFileTypesEnabled
    val searchPanelFileShowFolders get() = searchPanel.searchPanelFileShowFolders
    val searchPanelFileShowSystemFiles get() = searchPanel.searchPanelFileShowSystemFiles
    val searchPanelFilePreviewsEnabled get() = searchPanel.searchPanelFilePreviewsEnabled
    val searchPanelFileFolderWhitelist get() = searchPanel.searchPanelFileFolderWhitelist
    val searchPanelFileFolderBlacklist get() = searchPanel.searchPanelFileFolderBlacklist
    val searchPanelPresentationMode get() = searchPanel.searchPanelPresentationMode
    val searchPanelBarPosition get() = searchPanel.searchPanelBarPosition
    val searchPanelListOrder get() = searchPanel.searchPanelListOrder
    val searchPanelAppDisplayStyle get() = searchPanel.searchPanelAppDisplayStyle
    val searchPanelCalculatorEnabled get() = searchPanel.searchPanelCalculatorEnabled
    val searchPanelBackgroundStyle get() = searchPanel.searchPanelBackgroundStyle
    val searchPanelBlurRadiusDp get() = searchPanel.searchPanelBlurRadiusDp
    val searchPanelDimPercent get() = searchPanel.searchPanelDimPercent
    val searchPanelWebSuggestionsEnabled get() = searchPanel.searchPanelWebSuggestionsEnabled
    val searchPanelWebSuggestionsCount get() = searchPanel.searchPanelWebSuggestionsCount
    val searchPanelHistoryMaxEntries get() = searchPanel.searchPanelHistoryMaxEntries
    val searchPanelSectionAliases get() = searchPanel.searchPanelSectionAliases
    val aggregatedImageSearchEngines get() = searchPanel.aggregatedImageSearchEngines

    // endregion

    companion object {
        const val SEARCH_PANEL_BLUR_RADIUS_MIN_DP = HoneycombDisplaySettings.MIN_BLUR_DP
        const val SEARCH_PANEL_BLUR_RADIUS_MAX_DP = HoneycombDisplaySettings.MAX_BLUR_DP
        const val SEARCH_PANEL_BLUR_RADIUS_DEFAULT_DP = HoneycombDisplaySettings.DEFAULT_BLUR_DP
        const val WIDGET_PANEL_BLUR_RADIUS_MIN_DP = 10
        const val WIDGET_PANEL_BLUR_RADIUS_MAX_DP = 150
        const val WIDGET_PANEL_BLUR_RADIUS_DEFAULT_DP = 19
        const val STASH_PANEL_BLUR_RADIUS_MIN_DP = HoneycombDisplaySettings.MIN_BLUR_DP
        const val STASH_PANEL_BLUR_RADIUS_MAX_DP = HoneycombDisplaySettings.MAX_BLUR_DP
        const val STASH_PANEL_BLUR_RADIUS_DEFAULT_DP = HoneycombDisplaySettings.DEFAULT_BLUR_DP
        const val SEARCH_PANEL_DIM_MIN_PERCENT = HoneycombDisplaySettings.MIN_DIM_PERCENT
        const val SEARCH_PANEL_DIM_MAX_PERCENT = HoneycombDisplaySettings.MAX_DIM_PERCENT
        const val SEARCH_PANEL_DIM_DEFAULT_PERCENT = HoneycombDisplaySettings.DEFAULT_DIM_PERCENT
        const val SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MIN = 1
        const val SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MAX = 5

        val knownAggregatedImageSearchEngineIds: List<String> = listOf(
            "Google",
            "Yandex",
            "TinEye",
            "Iqdb",
            "SauceNao",
            "Iqdb3D",
            "Ascii2d",
            "TraceMoe",
            "AnimeTrace",
            "Copyseeker",
        )

        fun defaultAggregatedImageSearchEngines(): List<AggregatedImageSearchEngineConfig> =
            knownAggregatedImageSearchEngineIds.mapIndexed { index, engineId ->
                AggregatedImageSearchEngineConfig(
                    engineId = engineId,
                    sortOrder = index,
                    showInPanel = true,
                    preloadOnOpen = true,
                )
            }
    }
}
