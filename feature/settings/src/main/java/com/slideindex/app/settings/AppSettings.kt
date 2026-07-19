package com.slideindex.app.settings

import com.slideindex.app.floatball.FloatBallGestureCodec
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.overlay.PanelSide

data class AppSettings(
    val serviceEnabled: Boolean = false,
    val leftEdgeEnabled: Boolean = true,
    val rightEdgeEnabled: Boolean = true,
    val leftEdgeTriggerWidthDp: Float = 20f,
    val rightEdgeTriggerWidthDp: Float = 20f,
    val leftTriggerTopFraction: Float = 0.30f,
    val rightTriggerTopFraction: Float = 0.30f,
    val leftTriggerHeightFraction: Float = 0.38f,
    val rightTriggerHeightFraction: Float = 0.38f,
    val leftTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.default(0.30f, 0.38f)),
    val rightTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.default(0.30f, 0.38f)),
    val interceptSystemBackGesture: Boolean = false,
    val limitMaxInterceptLength: Boolean = false,
    val leftDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val rightDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val shortSwipeDistanceDp: Float = 60f,
    val longSwipeDistanceDp: Float = 120f,
    val gestureHintEnabled: Boolean = true,
    val gestureHintStyleId: Int = GestureHintStyle.BUBBLE.id,
    val animationStyles: AnimationStyles = AnimationStyles(),
    val gestureAngleConfig: GestureAngleConfig = GestureAngleConfig.DEFAULT,
    val indexHeightFraction: Float = 0.42f,
    val appsPerRow: Int = 3,
    /** Fixed grid columns per quick-launcher page. */
    val quickLauncherColumnsPerPage: Int = 3,
    /** Fixed grid rows per quick-launcher page (panel height). */
    val quickLauncherRowsPerPage: Int = 4,
    val panelOpacity: Float = 0.95f,
    val hapticEnabled: Boolean = true,
    val hapticStrengthLevel: Int = HapticStrength.MEDIUM.level,
    val hideFromRecents: Boolean = false,
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
    val appLaunchPolicyId: Int = AppLaunchPolicy.ALWAYS_FULLSCREEN.id,
    val longPressLaunchDurationMs: Int = 450,
    val hiddenAppPackages: Set<String> = emptySet(),
    val excludedTriggerAppPackages: Set<String> = emptySet(),
    val gestureRules: List<GestureRule> = emptyList(),
    val quickLauncher: List<com.slideindex.app.launcher.QuickLauncherItem> = emptyList(),
    val shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val widgetPanelPages: List<com.slideindex.app.widget.WidgetPanelPage> = emptyList(),
    val widgetPanelWidthFraction: Float = 0.8f,
    val widgetPanelHeightFraction: Float = 0.55f,
    val widgetPanelTopFraction: Float = 0.15f,
    val widgetPanelBlurEnabled: Boolean = true,
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
    val shakeGestureSettings: ShakeGestureSettings = ShakeGestureSettings(),
    val messageReminderSettings: MessageSettings = MessageSettings(),
    val debugPerformanceMonitorEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    /** FV-style persistent float ball; independent from edge-gesture floating pointer. */
    val floatBallEnabled: Boolean = false,
    val floatBallSizeDp: Float = 48f,
    val floatBallOpacity: Float = 0.88f,
    /** Ball center X as fraction of screen width (0–1). */
    val floatBallPositionXFraction: Float = 0.92f,
    /** Ball center Y as fraction of screen height (0–1). */
    val floatBallPositionYFraction: Float = 0.55f,
    /** When a11y text pick fails, capture screen region and run on-device OCR. */
    val floatBallOcrFallbackEnabled: Boolean = true,
    /** Selected downloadable OCR model id; empty means OCR fallback is unavailable until a model is installed. */
    val floatBallOcrModelId: String = "",
    /** Download OCR models on Wi-Fi only. */
    val ocrDownloadWifiOnly: Boolean = true,
    /**
     * Float-ball pick pointer speed (0.2–0.75, higher = faster).
     * Independent from [floatingPointerSensitivityFraction].
     */
    val floatBallPointerSpeedFraction: Float = 0.35f,
    val floatBallPositionMode: FloatBallPositionMode = FloatBallPositionMode.RIGHT,
    /** Which side shows the ball when [floatBallPositionMode] is [FloatBallPositionMode.BOTH_EDGES]. */
    val floatBallActiveSide: FloatBallSide = FloatBallSide.RIGHT,
    /** Edge line / capture strip height as fraction of screen height. */
    val floatBallLineHeightFraction: Float = 0.08f,
    /** Edge line / capture strip width as fraction of screen width. */
    val floatBallLineWidthFraction: Float = 0.30f,
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
    /** Finger travel before full-screen pointer mode activates. */
    val floatBallPointerSlopDp: Float = 8f,
    /** FV down_swipe_short_distance_2：短滑阈值 = percent × 40dp / 100。 */
    val floatBallDownSwipeShortPercent: Float = 200f,
    /** FV side_swipe_short_distance_2：短滑阈值 = percent × 40dp / 100。 */
    val floatBallSideSwipeShortPercent: Float = 320f,
    /** 上滑短滑阈值 = percent × 40dp / 100；超过即为长滑。 */
    val floatBallUpSwipeShortPercent: Float = 200f,
    /** When false, translate opens Google Translate in browser; when true, shows in-app overlay. */
    val floatBallInstantTranslate: Boolean = false,
    val floatBallTranslateEngine: FloatBallTranslateEngine = FloatBallTranslateEngine.GOOGLE,
    /** BCP-47 style target language code for translation, e.g. zh-CN. */
    val floatBallTranslateTargetLang: String = "zh-CN",
    /** Pick-result card transparency while the in-app translate overlay is open (0=opaque, 1=transparent). */
    val floatBallTranslatePickPanelTransparency: Float = 0.65f,
    /** Save shared long-image OCR results for later re-open from pick settings. */
    val shareImageOcrHistoryEnabled: Boolean = true,
    /** Configured text/image search engines for pick panel. */
    val searchEngines: List<SearchEngineConfig> = SearchEngineCatalog.defaultEngines(),
    val searchEngineGridColumns: Int = 5,
    val searchEngineGridRows: Int = 2,
    val searchEngineShowLabels: Boolean = true,
    val aggregatedImageSearchEngines: List<AggregatedImageSearchEngineConfig> =
        AggregatedImageSearchEngineCatalog.defaultConfigs(),
)
