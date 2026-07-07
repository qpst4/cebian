package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.primaryTriggerHandle
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
    val dynamicColorEnabled: Boolean = false,
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
    /** Joystick effective movement area width in px; maps to horizontal pointer travel (full screen). */
    val floatingPointerJoystickAreaWidthPx: Float = 703f,
    /** Joystick effective movement area height in px; maps to vertical pointer travel (full screen). */
    val floatingPointerJoystickAreaHeightPx: Float = 711f,
    /** Scales the joystick movement area (0.1–1). */
    val floatingPointerJoystickAreaZoomFraction: Float = 0.8f,
    /** When true, joystick area height is derived from width to match screen aspect ratio. */
    val floatingPointerMatchJoystickToScreenAspect: Boolean = false,
    /** Virtual joystick diameter in screen pixels (QC default 275). */
    val floatingPointerJoystickDiameterPx: Float = 275f,
    /** Ring pointer outer diameter in screen pixels. */
    val floatingPointerPointerDiameterPx: Float = 110f,
    /** Ring pointer band thickness in screen pixels. */
    val floatingPointerRingThicknessPx: Float = 12f,
    /** Ring pointer center dot diameter in screen pixels. */
    val floatingPointerDotDiameterPx: Float = 24f,
    val floatingPointerRingColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerFillColorArgb: Int = 0x19000000,
    val floatingPointerDotColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerTrailTypeId: Int = FloatingPointerTrailType.HIGH_DETAIL.id,
    val floatingPointerTrailDurationMs: Int = 150,
    val floatingPointerTrailColorArgb: Int = 0x66FF5252,
    val floatingPointerHideWhenJoystickReleased: Boolean = false,
    val floatingPointerJoystickInnerColorArgb: Int = 0x80FFFFFF.toInt(),
    val floatingPointerJoystickOuterColorArgb: Int = 0x80C0C0C0.toInt(),
    val floatingPointerJoystickGradientRadiusFraction: Float = 1f,
    val floatingPointerHideOnOutsideClick: Boolean = true,
    val floatingPointerHideOnQuickSwipe: Boolean = true,
    val floatingPointerHideWhenIdle: Boolean = true,
    val floatingPointerIdleHideDelayMs: Int = 3000,
    /** Long-press on joystick opens the radial action ring. */
    val floatingPointerRadialMenuEnabled: Boolean = true,
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
    val otpCopyToClipboard: Boolean = false,
    val otpKeywordsRegex: String = com.slideindex.app.otp.VerificationCodeExtractor.DEFAULT_KEYWORDS_REGEX,
    val otpUserMatchRules: List<com.slideindex.app.otp.OtpMatchRule> = emptyList(),
    val otpDisabledOfficialRuleIds: Set<String> = emptySet(),
    val otpAccessibilityAssistEnabled: Boolean = false,
    val otpAutoInputEnabled: Boolean = false,
    val otpAutoConfirmEnabled: Boolean = false,
    val otpAutoInputDelayMs: Int = 0,
    val otpAutoInputIntervalMs: Int = 0,
)

fun AppSettings.edgeTriggerWidthDp(side: PanelSide): Float = when (side) {
    PanelSide.LEFT -> leftEdgeTriggerWidthDp
    PanelSide.RIGHT -> rightEdgeTriggerWidthDp
}

fun AppSettings.triggerTopFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).topFraction

fun AppSettings.triggerHeightFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).heightFraction

fun AppSettings.triggerBottomFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).bottomFraction

fun AppSettings.interceptWindowWidthDp(side: PanelSide): Float {
    if (!interceptSystemBackGesture) return edgeTriggerWidthDp(side)
    val triggerWidth = edgeTriggerWidthDp(side)
    val interceptWidth = if (limitMaxInterceptLength) 200f else 320f
    return maxOf(triggerWidth, interceptWidth)
}
