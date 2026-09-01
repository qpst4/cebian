package com.slideindex.app.settings

import com.slideindex.app.floatball.FloatBallGestureCodec
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode

/**
 * [AppSettings] 按域拆出的分片。
 *
 * 拆分原因是硬性约束而非风格偏好：Kotlin 为带默认值的构造器/`copy()` 生成的合成方法，
 * 其调用点走 DEX 的 range 形式 invoke 指令，而该指令的寄存器计数字段只有 8 位（上限 255）。
 * 单个 data class 的参数数 P 必须满足 `1(this) + P + ceil(P/32) + 1(marker) <= 255`，
 * 即 P <= 245，超出后 D8 不报错但会生成非法指令，运行期由 ART 抛 VerifyError。
 *
 * 每个分片的字段名与拆分前 [AppSettings] 的扁平字段名保持一致，
 * [AppSettings] 侧以派生属性原样暴露，因此读取方无需改动。
 */

/** 边缘触钮几何、横竖屏触钮、手势规则与手势提示。 */
data class EdgeTriggerSettings(
    val leftEdgeEnabled: Boolean = true,
    val rightEdgeEnabled: Boolean = true,
    val leftEdgeTriggerWidthDp: Float = 20f,
    val rightEdgeTriggerWidthDp: Float = 20f,
    val bottomEdgeTriggerWidthDp: Float = 20f,
    val topEdgeTriggerWidthDp: Float = 20f,
    val leftTriggerTopFraction: Float = 0.30f,
    val rightTriggerTopFraction: Float = 0.30f,
    val leftTriggerHeightFraction: Float = 0.38f,
    val rightTriggerHeightFraction: Float = 0.38f,
    val leftTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.default(0.30f, 0.38f)),
    val rightTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.default(0.30f, 0.38f)),
    val bottomTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.bottomDefault()),
    val topTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.topDefault()),
    val leftTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    val rightTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    val bottomTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    val topTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    /** 横屏触钮已从竖屏完成一次性复制；为 true 后横屏布局/手势与竖屏完全独立。 */
    val landscapeTriggersInitialized: Boolean = false,
    val gestureRulesLandscape: List<GestureRule> = emptyList(),
    val leftDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val rightDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val bottomDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val topDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val interceptSystemBackGesture: Boolean = false,
    val limitMaxInterceptLength: Boolean = false,
    val leftDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val rightDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val bottomDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val topDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val shortSwipeDistanceDp: Float = 60f,
    val longSwipeDistanceDp: Float = 120f,
    val gestureHintEnabled: Boolean = true,
    val gestureHintStyleId: Int = GestureHintStyle.BUBBLE.id,
    /** 手势动画相对手指的垂直偏移（dp）；0 为贴在手指高度，增大则远离指腹（侧/底向上，顶向下）。 */
    val gestureHintFingerOffsetDp: Float = 0f,
    val swipeHoverDurationMs: Int = 250,
    val inwardHoverCompoundEnabled: Boolean = true,
    val animationStyles: AnimationStyles = AnimationStyles(),
    val gestureAngles: GestureAngles = GestureAngles(),
)

/** 启动策略、应用过滤、快捷启动 / 蜂窝 / 圆环启动器槽位与命令。 */
data class LauncherSettings(
    val appLaunchPolicyId: Int = AppLaunchPolicy.ALWAYS_FULLSCREEN.id,
    val longPressLaunchDurationMs: Int = 450,
    val hiddenAppPackages: Set<String> = emptySet(),
    val freezerAppPackages: Set<String> = emptySet(),
    val freezerShowInLauncher: Boolean = false,
    val expandPanelSlotActions: List<com.slideindex.app.gesture.GestureAction?> = List(8) { null },
    /** “切换上一应用”动作忽略的包名黑名单。 */
    val previousAppExcludedPackages: Set<String> = emptySet(),
    val excludedAppScopes: Map<String, ExcludedAppScopes> = emptyMap(),
    val excludedAppDefaultScopes: ExcludedAppScopes = ExcludedAppScopes.ALL,
    val gestureRules: List<GestureRule> = emptyList(),
    val quickLauncherPanels: List<com.slideindex.app.launcher.QuickLauncherPanel> = emptyList(),
    val quickLauncherDisplay: QuickLauncherDisplaySettings = QuickLauncherDisplaySettings(),
    val honeycombLauncher: List<com.slideindex.app.launcher.QuickLauncherItem> = emptyList(),
    val honeycombDisplay: HoneycombDisplaySettings = HoneycombDisplaySettings(),
    val fvAppSwitcherVertical: FvAppSwitcherSettings = FvAppSwitcherSettings(),
    val fvAppSwitcherHorizontal: FvAppSwitcherSettings = FvAppSwitcherSettings(),
    /** 为 true 时顶/底与左/右共用同一套外观（圈数、尺寸、半径等）。 */
    val fvAppSwitcherLinkAppearanceAxes: Boolean = FvAppSwitcherLinkFlags.DEFAULT_LINK_APPEARANCE_AXES,
    /** 为 true 时顶/底与左/右共用同一套槽位。 */
    val fvAppSwitcherLinkSlotAxes: Boolean = FvAppSwitcherLinkFlags.DEFAULT_LINK_SLOT_AXES,
    val holographicLauncher: HolographicLauncherSettings = HolographicLauncherSettings(),
    val shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    val activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
)

/** 边缘手势接力指针：指针、摇杆、轮盘与边缘动作。 */
data class FloatingPointerSettings(
    /**
     * Pointer speed shown in settings (0.2–0.75 ≈ 20%–75%). Higher = faster pointer.
     */
    val floatingPointerSensitivityFraction: Float = 0.52f,
    /** Virtual joystick diameter in screen pixels (QC default 275). */
    val floatingPointerJoystickDiameterPx: Float = 275f,
    /** Ring pointer outer diameter in screen pixels. */
    val floatingPointerPointerDiameterPx: Float = 100f,
    /** Pointer design id; ring style by default for backward compatibility. */
    val floatingPointerDesignId: String = FloatingPointerDesignIds.RING,
    /** Ring pointer band thickness in screen pixels. */
    val floatingPointerRingThicknessPx: Float = 12f,
    /** Ring pointer center dot diameter in screen pixels. */
    val floatingPointerDotDiameterPx: Float = 15f,
    val floatingPointerRingColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerFillColorArgb: Int = 0x19000000,
    val floatingPointerDotColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerClickVisualFeedbackEnabled: Boolean = true,
    val floatingPointerClickHapticEnabled: Boolean = true,
    val floatingPointerRippleColorArgb: Int = 0xFFFD746C.toInt(),
    /** Click ripple diameter in dp (QC default 80dp). */
    val floatingPointerRippleSizeDp: Float = 80f,
    /** Click ripple animation duration in ms (QC default 500). */
    val floatingPointerRippleDurationMs: Int = 500,
    val floatingPointerTrailTypeId: Int = FloatingPointerTrailType.HIGH_DETAIL.id,
    val floatingPointerTrailDurationMs: Int = 150,
    val floatingPointerTrailColorArgb: Int = 0x66FF5252,
    val floatingPointerHideWhenJoystickReleased: Boolean = false,
    /** QC `clickDistanceThreshold`: max tracker travel to still count as click/long-press (dp). */
    val floatingPointerClickDistanceThresholdDp: Float = 6f,
    val floatingPointerJoystickInnerColorArgb: Int = 0x80FFFFFF.toInt(),
    val floatingPointerJoystickOuterColorArgb: Int = 0x80C0C0C0.toInt(),
    val floatingPointerJoystickGradientRadiusFraction: Float = 1f,
    val floatingPointerHideOnOutsideClick: Boolean = true,
    val floatingPointerHideOnQuickSwipe: Boolean = true,
    val floatingPointerHideWhenIdle: Boolean = true,
    val floatingPointerIdleHideDelayMs: Int = 3000,
    /**
     * Continued edge handoff: on finger-up, click at pointer then dismiss the overlay.
     * Does not affect resident joystick tap-to-click.
     */
    val floatingPointerReleaseClickAndDismiss: Boolean = true,
    /**
     * Continued edge handoff: dwell ~280ms on the pointer to enter element/region pick mode.
     */
    val floatingPointerHoverEnterSelect: Boolean = false,
    /** Action executed when the joystick is long-pressed. Defaults to opening the radial action ring. */
    val floatingPointerJoystickLongPressAction: com.slideindex.app.gesture.GestureAction = GestureAction.OpenFloatingPointerRadialMenu,
    /** Keep the radial action ring visible around the joystick without long-press. */
    val floatingPointerRadialAlwaysVisible: Boolean = false,
    val floatingPointerRadialLongPressMs: Int = 500,
    val floatingPointerRadialOuterDiameterPx: Float = 440f,
    val floatingPointerRadialInnerDiameterPx: Float = 192f,
    val floatingPointerRadialOuterColorArgb: Int = 0xE62B3D4F.toInt(),
    val floatingPointerRadialInnerColorArgb: Int = 0xE61A1A28.toInt(),
    val floatingPointerRadialDividerThicknessPx: Float = 4f,
    val floatingPointerRadialDividerColorArgb: Int = 0x22FFFFFF,
    val floatingPointerRadialIconSizeFraction: Float = 0.85f,
    val floatingPointerRadialIconColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerRadialSlotActions: List<com.slideindex.app.gesture.GestureAction> =
        FloatingPointerRadialMenuCodec.defaultSlots(),
    /** QC edge actions: pointer pushed past screen edge triggers configured shortcuts. */
    val floatingPointerEdgeThresholdDp: Float = 30f,
    val floatingPointerEdgePreviewSensitivity: Int = 3,
    val floatingPointerEdgePreviewGlowSize: Int = 4,
    val floatingPointerEdgePreviewShowIcon: Boolean = true,
    val floatingPointerEdgeVisualSizeDp: Float = 0f,
    val floatingPointerEdgeVisualOpacity: Int = 75,
    val floatingPointerEdgeVisualColorArgb: Int = 0xFFFD746C.toInt(),
    val floatingPointerEdgeActionsConfig: FloatingPointerEdgeActionsConfig =
        FloatingPointerEdgeActionsCodec.defaultConfig(),
)

/** FV 风格常驻悬浮球：外观、取词、滑动阈值与翻译。 */
data class FloatBallSettings(
    /** FV-style persistent float ball; independent from edge-gesture floating pointer. */
    val floatBallEnabled: Boolean = false,
    val floatBallSizeDp: Float = 48f,
    /** Pick cross arm length from center to tip in dp. */
    val floatBallPickCrossArmDp: Float = 7.5f,
    val floatBallOpacity: Float = 0.88f,
    /** Fraction of ball width visible on screen when docked to an edge (0.5–1). */
    val floatBallVisibleFraction: Float = 1f,
    /** CUSTOM mode only: ball center X as fraction of screen width. */
    val floatBallCustomCenterXFraction: Float = 0.92f,
    /** Ball center Y as fraction of screen height (0–1). */
    val floatBallPositionYFraction: Float = 0.55f,
    /** When a11y text pick fails, capture screen region and run on-device OCR. */
    val floatBallOcrFallbackEnabled: Boolean = true,
    /** Selected downloadable OCR model id; empty means OCR fallback is unavailable until a model is installed. */
    val floatBallOcrModelId: String = "",
    /** Download OCR models on Wi-Fi only. */
    val ocrDownloadWifiOnly: Boolean = true,
    /**
     * Float-ball pick pointer horizontal speed (0.2–0.75, higher = faster).
     * Independent from [FloatingPointerSettings.floatingPointerSensitivityFraction].
     */
    val floatBallPointerSpeedFraction: Float = 0.35f,
    /**
     * Float-ball pick pointer vertical speed (0.2–0.75, higher = faster).
     * Independent from [floatBallPointerSpeedFraction].
     */
    val floatBallPointerSpeedVerticalFraction: Float = 0.35f,
    val floatBallPositionMode: FloatBallPositionMode = FloatBallPositionMode.RIGHT,
    /** Which side shows the ball when [floatBallPositionMode] is [FloatBallPositionMode.BOTH_EDGES]. */
    val floatBallActiveSide: FloatBallSide = FloatBallSide.RIGHT,
    /** Edge line / capture strip height as fraction of screen height. */
    val floatBallLineHeightFraction: Float = 0.08f,
    /** Edge line / capture strip width as fraction of screen width (1%–50%). */
    val floatBallLineWidthFraction: Float = 0.04f,
    val floatBallLineOpacity: Float = 0.9f,
    /** 悬浮球手势 → 动作绑定。 */
    val floatBallGestureActions: Map<FloatBallGestureType, GestureAction> =
        FloatBallGestureCodec.defaultActions(),
    /** 悬浮球外观样式。 */
    val floatBallStyleType: FloatBallStyleType = FloatBallStyleType.DEFAULT,
    /** 自定义图片 URI（[floatBallStyleType] 为 CUSTOM_IMAGE 时使用）。 */
    val floatBallCustomImageUri: String = "",
    /** 幻灯片图片 URI 列表（[floatBallStyleType] 为 SLIDESHOW 时使用）。 */
    val floatBallSlideshowUris: List<String> = emptyList(),
    /** GIF 图片 URI（[floatBallStyleType] 为 GIF 时使用）。 */
    val floatBallGifUri: String = "",
    /** Gap between ball edge and pick crosshair in dp (above / below). */
    val floatBallPickOffsetDp: Float = 48f,
    /** Body text size for pick-result panel in sp. */
    val floatBallPickTextSizeSp: Float = 15f,
    /** Screen-height fraction for smooth above→below pick transition near bottom. */
    val floatBallPickBottomTransitionFraction: Float = 0.22f,
    /** Pick panel: show text + search by default; image section stays collapsed until the image row is tapped. */
    val floatBallPickTextFirstPanel: Boolean = false,
    /** Bottom pick panel slide-in duration in ms (0 = no animation). */
    val floatBallPickPanelEnterAnimationMs: Int = 64,
    /** Bottom pick panel slide-out duration in ms (0 = no animation). */
    val floatBallPickPanelExitAnimationMs: Int = 64,
    /** Finger travel before full-screen pointer mode activates. */
    val floatBallPointerSlopDp: Float = 4f,
    /** Hover dwell time before cursor locks start position for text pick or screenshot (ms). */
    val floatBallHoverPauseDelayMs: Int = 400,
    /** Deadzone distance from pause origin to cancel screenshot / enter drag (dp). */
    val floatBallRegionalCancelSlopDp: Float = 16f,
    /** FV down_swipe_short_distance_2：短滑阈值 = percent × 40dp / 100。 */
    val floatBallDownSwipeShortPercent: Float = 200f,
    /** FV side_swipe_short_distance_2：短滑阈值 = percent × 40dp / 100。 */
    val floatBallSideSwipeShortPercent: Float = 320f,
    /** 上滑短滑阈值 = percent × 40dp / 100；超过即为长滑。 */
    val floatBallUpSwipeShortPercent: Float = 256f,
    /** When false, translate opens Google Translate in browser; when true, shows in-app overlay. */
    val floatBallInstantTranslate: Boolean = false,
    val floatBallTranslateEngine: FloatBallTranslateEngine = FloatBallTranslateEngine.GOOGLE,
    /** BCP-47 style target language code for translation, e.g. zh-CN. */
    val floatBallTranslateTargetLang: String = "zh-CN",
    /** Pick-result card transparency while the in-app translate overlay is open (0=opaque, 1=transparent). */
    val floatBallImageSearchPickPanelTransparency: Float = 0.65f,
    /** Save shared long-image OCR results for later re-open from pick settings. */
    val shareImageOcrHistoryEnabled: Boolean = true,
)

/** 剪贴板监听、历史浮标、输入浮窗与暂存面板。 */
data class ClipboardSettings(
    /** Background clipboard monitoring via Shizuku/Root privileged listener. */
    val clipboardBackgroundMonitoring: Boolean = true,
    val clipboardBackgroundMonitoringMode: ClipboardMonitoringMode = ClipboardMonitoringMode.FOLLOW_PRIVILEGE,
    /** Monitor MediaStore for system/third-party screenshots and add to clipboard history. */
    val clipboardScreenshotMonitoring: Boolean = false,
    /** Max clipboard history entries; [ClipboardHistoryCapacity.UNLIMITED] means no limit. */
    val clipboardHistoryMaxEntries: Int = 100,
    /** Persistent edge handle showing clipboard history overlay. */
    val clipboardHistoryFloatEnabled: Boolean = false,
    /** When true, the history float handle is shown in landscape orientation. */
    val clipboardHistoryFloatEnabledLandscape: Boolean = false,
    /** When true, the history float handle cannot be dragged vertically. */
    val clipboardHistoryFloatLockPosition: Boolean = true,
    /** Width of the collapsed history float handle in dp (24–50). */
    val clipboardHistoryFloatHandleWidthDp: Int = 32,
    /** Floating clipboard window for input scenarios (grid, resize, paste on tap). */
    val clipboardFloatEnabled: Boolean = false,
    /** When true, show a chip above the keyboard before expanding the full window. */
    val clipboardFloatShowChip: Boolean = true,
    /** When true, the chip follows the keyboard top edge instead of a saved position. */
    val clipboardFloatChipFollowIme: Boolean = true,
    val clipboardFloatChipX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatChipY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    /** When true, the expanded panel uses saved x/y instead of IME-adaptive placement. */
    val clipboardFloatPanelPinPosition: Boolean = false,
    val clipboardFloatEntryClickAction: ClipboardFloatEntryClickAction = ClipboardFloatEntryClickAction.PASTE,
    val clipboardFloatListStyleId: Int = ClipboardFloatListStyle.SINGLE_LINE.id,
    val clipboardFloatPortraitGeometry: ClipboardFloatOrientationGeometry = ClipboardFloatOrientationGeometry(),
    val clipboardFloatLandscapeGeometry: ClipboardFloatOrientationGeometry = ClipboardFloatOrientationGeometry(),
    /** @deprecated Use [clipboardFloatPortraitGeometry]; kept for legacy readers. */
    val clipboardFloatPanelWidthDp: Int = ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP,
    /** @deprecated Use [clipboardFloatPortraitGeometry]. */
    val clipboardFloatPanelHeightDp: Int = ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP,
    /** @deprecated Use [clipboardFloatPortraitGeometry]. */
    val clipboardFloatPanelX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    /** @deprecated Use [clipboardFloatPortraitGeometry]. */
    val clipboardFloatPanelY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatBlockedPackages: Set<String> = emptySet(),
    val clipboardFloatPasteHapticEnabled: Boolean = false,
    val clipboardFloatPasteSuccessCount: Int = 0,
    val clipboardFloatPasteFailCount: Int = 0,
    val clipboardFloatAlpha: Float = 1.0f,
    val clipboardFloatAutoDimWhenUnfocused: Boolean = false,
    val clipboardFloatAutoCloseSeconds: Int = 0,
    /** Cross-window blur behind stash/history side panel (API 31+). */
    val stashPanelBackgroundBlurEnabled: Boolean = false,
    val stashPanelBackgroundBlurRadiusDp: Int = AppSettings.STASH_PANEL_BLUR_RADIUS_DEFAULT_DP,
)

/** 搜索面板与搜索引擎。 */
data class SearchPanelSettings(
    /** Configured text/image search engines for pick panel. */
    val searchEngines: List<SearchEngineConfig> = SearchEngineCatalog.defaultEngines(),
    val searchEngineGridColumns: Int = 5,
    val searchEngineGridRows: Int = 2,
    val searchEngineShowLabels: Boolean = true,
    val searchPanelDefaultEngineId: String? = null,
    val searchPanelInputBehavior: SearchPanelInputBehavior = SearchPanelInputBehavior.KEEP,
    val searchPanelContactSearchEnabled: Boolean = true,
    val searchPanelFileSearchEnabled: Boolean = true,
    val searchPanelAppSearchEnabled: Boolean = true,
    val searchPanelSettingsSearchEnabled: Boolean = true,
    /** Enum names of enabled file types; empty means all. */
    val searchPanelFileTypesEnabled: Set<String> = emptySet(),
    val searchPanelFileShowFolders: Boolean = false,
    val searchPanelFileShowSystemFiles: Boolean = false,
    val searchPanelFilePreviewsEnabled: Boolean = true,
    val searchPanelFileFolderWhitelist: Set<String> = emptySet(),
    val searchPanelFileFolderBlacklist: Set<String> = emptySet(),
    val searchPanelPresentationMode: SearchPanelPresentationMode = SearchPanelPresentationMode.BOTTOM_SHEET,
    val searchPanelBarPosition: SearchPanelBarPosition = SearchPanelBarPosition.TOP,
    val searchPanelListOrder: SearchPanelListOrder = SearchPanelListOrder.TOP_DOWN,
    val searchPanelAppDisplayStyle: SearchPanelAppDisplayStyle = SearchPanelAppDisplayStyle.ICONS,
    val searchPanelCalculatorEnabled: Boolean = true,
    val searchPanelBackgroundStyle: Int = SearchPanelBackgroundStyle.DEFAULT,
    val searchPanelBlurRadiusDp: Int = AppSettings.SEARCH_PANEL_BLUR_RADIUS_DEFAULT_DP,
    val searchPanelDimPercent: Int = AppSettings.SEARCH_PANEL_DIM_DEFAULT_PERCENT,
    val searchPanelWebSuggestionsEnabled: Boolean = true,
    val searchPanelWebSuggestionsCount: Int = 5,
    val searchPanelHistoryMaxEntries: Int = SearchPanelHistoryCapacity.DEFAULT,
    val searchPanelSectionAliases: SearchPanelSectionAliasSettings = SearchPanelSectionAliasSettings(),
    val aggregatedImageSearchEngines: List<AggregatedImageSearchEngineConfig> =
        AppSettings.defaultAggregatedImageSearchEngines(),
)

/** 自由小窗模式与尺寸。 */
data class FreeWindowSettings(
    val freeWindowEnabled: Boolean = false,
    val freeWindowModeId: Int = FreeWindowMode.detectDefault().id,
    val freeWindowWidthFraction: Float = 0.8f,
    val freeWindowHeightFraction: Float = 0.55f,
    val freeWindowLeftFraction: Float = 0.1f,
    val freeWindowTopFraction: Float = 0.15f,
)

/** 小部件面板尺寸、模糊与页面。 */
data class WidgetPanelSettings(
    val widgetPanelPages: List<com.slideindex.app.widget.WidgetPanelPage> = emptyList(),
    val widgetPanelWidthFraction: Float = 0.8f,
    val widgetPanelHeightFraction: Float = 0.55f,
    val widgetPanelTopFraction: Float = 0.15f,
    val widgetPanelBlurEnabled: Boolean = true,
    val widgetPanelBlurRadiusDp: Int = AppSettings.WIDGET_PANEL_BLUR_RADIUS_DEFAULT_DP,
)

/** 验证码提取与自动填充规则。 */
data class OtpSettings(
    val otpCopyToClipboard: Boolean = false,
    val otpKeywordsRegex: String = com.slideindex.app.otp.OtpKeywords.DEFAULT_KEYWORDS_REGEX,
    val otpUserMatchRules: List<com.slideindex.app.otp.OtpMatchRule> = emptyList(),
    val otpDisabledOfficialRuleIds: Set<String> = emptySet(),
    val otpAutoInputEnabled: Boolean = false,
    val otpAutoConfirmEnabled: Boolean = false,
    val otpAutoInputDelayMs: Int = 0,
    val otpAutoInputIntervalMs: Int = 0,
    val otpLsposedSmsCaptureEnabled: Boolean = false,
    val otpLsposedSystemInjectEnabled: Boolean = true,
)

