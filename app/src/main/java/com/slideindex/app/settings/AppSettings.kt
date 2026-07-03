package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
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
    val alignHandlesEnabled: Boolean = true,
    val interceptSystemBackGesture: Boolean = false,
    val limitMaxInterceptLength: Boolean = false,
    val leftDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val rightDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val shortSwipeDistanceDp: Float = 60f,
    val longSwipeDistanceDp: Float = 120f,
    val gestureHintEnabled: Boolean = true,
    val gestureHintStyleId: Int = GestureHintStyle.BUBBLE.id,
    val gestureAngleConfig: GestureAngleConfig = GestureAngleConfig.DEFAULT,
    val indexHeightFraction: Float = 0.42f,
    val appsPerRow: Int = 3,
    val panelOpacity: Float = 0.95f,
    val hapticEnabled: Boolean = true,
    val hapticStrengthLevel: Int = HapticStrength.MEDIUM.level,
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
)

fun AppSettings.edgeTriggerWidthDp(side: PanelSide): Float = when (side) {
    PanelSide.LEFT -> leftEdgeTriggerWidthDp
    PanelSide.RIGHT -> rightEdgeTriggerWidthDp
}

fun AppSettings.triggerTopFraction(side: PanelSide): Float = when (side) {
    PanelSide.LEFT -> leftTriggerTopFraction
    PanelSide.RIGHT -> rightTriggerTopFraction
}

fun AppSettings.triggerHeightFraction(side: PanelSide): Float = when (side) {
    PanelSide.LEFT -> leftTriggerHeightFraction
    PanelSide.RIGHT -> rightTriggerHeightFraction
}

fun AppSettings.triggerBottomFraction(side: PanelSide): Float =
    triggerTopFraction(side) + triggerHeightFraction(side)

fun AppSettings.interceptWindowWidthDp(side: PanelSide): Float {
    if (!interceptSystemBackGesture) return edgeTriggerWidthDp(side)
    val triggerWidth = edgeTriggerWidthDp(side)
    val interceptWidth = if (limitMaxInterceptLength) 200f else 320f
    return maxOf(triggerWidth, interceptWidth)
}
