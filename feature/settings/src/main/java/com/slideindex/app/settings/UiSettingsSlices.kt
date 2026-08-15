package com.slideindex.app.settings

import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.shake.FaceDownGestureSettings
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.widget.WidgetPanelPage

/** 首页主屏展示所需设置子集。 */
data class HomeMainSettings(
    val serviceEnabled: Boolean = false,
    val hideFromRecents: Boolean = false,
    val accessibilityKeepAliveEnabled: Boolean = false,
    val floatBallEnabled: Boolean = false,
    val floatBallSizeDp: Float = 48f,
    val floatBallOpacity: Float = 0.88f,
    val gestureHintEnabled: Boolean = true,
    val gestureHintStyleId: Int = GestureHintStyle.BUBBLE.id,
    val excludedAppScopes: Map<String, ExcludedAppScopes> = emptyMap(),
    val hideTriggerInLandscape: Boolean = false,
    val hideTriggerOnLockScreen: Boolean = false,
    val hideTriggerOnLauncher: Boolean = false,
    val hapticEnabled: Boolean = true,
    val hapticStrengthLevel: Int = HapticStrength.MEDIUM.level,
    val dynamicColorEnabled: Boolean = true,
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val themePaletteStyleId: Int = ThemePaletteStyle.TONAL_SPOT.id,
    val themeModeId: Int = AppThemeMode.SYSTEM.id,
    val customColorEnabled: Boolean = false,
    val themeColorSpecId: Int = AppColorSpec.SPEC_2025.id,
    val bottomNavStyleId: Int = BottomNavStyle.FLOATING_NAV.id,
    val bottomNavModeId: Int = BottomNavMode.ICON_AND_TEXT.id,
    val bottomNavGlassEnabled: Boolean = true,
    val topAppBarBlurStyleId: Int = TopAppBarBlurStyle.GAUSSIAN.id,
    val bottomNavClassicBlurRadiusDp: Float = BottomNavBlurDefaults.DEFAULT_RADIUS_DP,
    val bottomNavLiquidGlassBlurRadiusDp: Float = BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP,
    val bottomNavFloatingNavBlurRadiusDp: Float = BottomNavBlurDefaults.FLOATING_NAV_DEFAULT_RADIUS_DP,
    val predictiveBackEnabled: Boolean = false,
) {
    val bottomNavBlurRadiusDp: Float
        get() = when (BottomNavStyle.fromId(bottomNavStyleId)) {
            BottomNavStyle.CLASSIC -> bottomNavClassicBlurRadiusDp
            BottomNavStyle.LIQUID_GLASS -> bottomNavLiquidGlassBlurRadiusDp
            BottomNavStyle.FLOATING_NAV -> bottomNavFloatingNavBlurRadiusDp
        }

    companion object {
        fun from(settings: AppSettings): HomeMainSettings = HomeMainSettings(
            serviceEnabled = settings.serviceEnabled,
            hideFromRecents = settings.hideFromRecents,
            accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,
            floatBallEnabled = settings.floatBallEnabled,
            floatBallSizeDp = settings.floatBallSizeDp,
            floatBallOpacity = settings.floatBallOpacity,
            gestureHintEnabled = settings.gestureHintEnabled,
            gestureHintStyleId = settings.gestureHintStyleId,
            excludedAppScopes = settings.excludedAppScopes,
            hideTriggerInLandscape = settings.hideTriggerInLandscape,
            hideTriggerOnLockScreen = settings.hideTriggerOnLockScreen,
            hideTriggerOnLauncher = settings.hideTriggerOnLauncher,
            hapticEnabled = settings.hapticEnabled,
            hapticStrengthLevel = settings.hapticStrengthLevel,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            themeColorArgb = settings.themeColorArgb,
            themePaletteStyleId = settings.themePaletteStyleId,
            themeModeId = settings.themeModeId,
            customColorEnabled = settings.customColorEnabled,
            themeColorSpecId = settings.themeColorSpecId,
            bottomNavStyleId = settings.bottomNavStyleId,
            bottomNavModeId = settings.bottomNavModeId,
            bottomNavGlassEnabled = settings.bottomNavGlassEnabled,
            topAppBarBlurStyleId = settings.topAppBarBlurStyleId,
            bottomNavClassicBlurRadiusDp = settings.bottomNavClassicBlurRadiusDp,
            bottomNavLiquidGlassBlurRadiusDp = settings.bottomNavLiquidGlassBlurRadiusDp,
            bottomNavFloatingNavBlurRadiusDp = settings.bottomNavFloatingNavBlurRadiusDp,
            predictiveBackEnabled = settings.predictiveBackEnabled,
        )
    }
}

/** 扩展 Hub 入口卡片所需设置子集。 */
data class ExtensionHubSettings(
    val appsPerRow: Int = 3,
    val quickLauncherPanelCount: Int = 0,
    val shellCommandCount: Int = 0,
    val activityShortcutCount: Int = 0,
    val honeycombLauncherCount: Int = 0,
    val widgetPanelPages: List<WidgetPanelPage> = emptyList(),
    val floatingPointerJoystickDiameterPx: Float = 275f,
    val floatingPointerPointerDiameterPx: Float = 100f,
    val floatingPointerSensitivityFraction: Float = 0.52f,
    val clipboardBackgroundMonitoring: Boolean = true,
    val clipboardBackgroundMonitoringMode: ClipboardMonitoringMode = ClipboardMonitoringMode.SHIZUKU_LOGS,
) {
    companion object {
        fun from(settings: AppSettings): ExtensionHubSettings = ExtensionHubSettings(
            appsPerRow = settings.appsPerRow,
            quickLauncherPanelCount = settings.quickLauncherPanels.size.coerceAtLeast(1),
            shellCommandCount = settings.shellCommands.size,
            activityShortcutCount = settings.activityShortcuts.size,
            honeycombLauncherCount = settings.honeycombLauncher.size,
            widgetPanelPages = settings.widgetPanelPages,
            floatingPointerJoystickDiameterPx = settings.floatingPointerJoystickDiameterPx,
            floatingPointerPointerDiameterPx = settings.floatingPointerPointerDiameterPx,
            floatingPointerSensitivityFraction = settings.floatingPointerSensitivityFraction,
            clipboardBackgroundMonitoring = settings.clipboardBackgroundMonitoring,
            clipboardBackgroundMonitoringMode = settings.clipboardBackgroundMonitoringMode,
        )
    }
}

data class KeepAliveUiSettings(
    val hideFromRecents: Boolean = false,
    val accessibilityKeepAliveEnabled: Boolean = false,
) {
    companion object {
        fun from(settings: AppSettings): KeepAliveUiSettings = KeepAliveUiSettings(
            hideFromRecents = settings.hideFromRecents,
            accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,
        )
    }
}

data class ShakeUiSettings(
    val shakeGestureSettings: ShakeGestureSettings = ShakeGestureSettings(),
    val faceDownGestureSettings: FaceDownGestureSettings = FaceDownGestureSettings(),
) {
    companion object {
        fun from(settings: AppSettings): ShakeUiSettings = ShakeUiSettings(
            shakeGestureSettings = settings.shakeGestureSettings,
            faceDownGestureSettings = settings.faceDownGestureSettings,
        )
    }
}

data class FreeWindowUiSettings(
    val freeWindowEnabled: Boolean = false,
    val freeWindowModeId: Int = FreeWindowMode.detectDefault().id,
    val freeWindowWidthFraction: Float = 0.8f,
    val freeWindowHeightFraction: Float = 0.55f,
    val freeWindowLeftFraction: Float = 0.1f,
    val freeWindowTopFraction: Float = 0.15f,
    val appLaunchPolicyId: Int = AppLaunchPolicy.ALWAYS_FULLSCREEN.id,
    val longPressLaunchDurationMs: Int = 450,
) {
    companion object {
        fun from(settings: AppSettings): FreeWindowUiSettings = FreeWindowUiSettings(
            freeWindowEnabled = settings.freeWindowEnabled,
            freeWindowModeId = settings.freeWindowModeId,
            freeWindowWidthFraction = settings.freeWindowWidthFraction,
            freeWindowHeightFraction = settings.freeWindowHeightFraction,
            freeWindowLeftFraction = settings.freeWindowLeftFraction,
            freeWindowTopFraction = settings.freeWindowTopFraction,
            appLaunchPolicyId = settings.appLaunchPolicyId,
            longPressLaunchDurationMs = settings.longPressLaunchDurationMs,
        )
    }
}

data class OtpUiSettings(
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
) {
    companion object {
        fun from(settings: AppSettings): OtpUiSettings = OtpUiSettings(
            otpCopyToClipboard = settings.otpCopyToClipboard,
            otpKeywordsRegex = settings.otpKeywordsRegex,
            otpUserMatchRules = settings.otpUserMatchRules,
            otpDisabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
            otpAutoInputEnabled = settings.otpAutoInputEnabled,
            otpAutoConfirmEnabled = settings.otpAutoConfirmEnabled,
            otpAutoInputDelayMs = settings.otpAutoInputDelayMs,
            otpAutoInputIntervalMs = settings.otpAutoInputIntervalMs,
            otpLsposedSmsCaptureEnabled = settings.otpLsposedSmsCaptureEnabled,
            otpLsposedSystemInjectEnabled = settings.otpLsposedSystemInjectEnabled,
        )
    }
}

fun HomeMainSettings.gestureHintStyle(): GestureHintStyle = GestureHintStyle.fromId(gestureHintStyleId)
