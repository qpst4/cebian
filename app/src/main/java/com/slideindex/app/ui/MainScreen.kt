package com.slideindex.app.ui



import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.SwipeRight

import androidx.compose.material.icons.filled.TouchApp

import androidx.compose.material.icons.filled.Vibration

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.settings.GestureHintStyle

import com.slideindex.app.settings.CornerGestureSettings

import com.slideindex.app.settings.HomeMainSettings

import com.slideindex.app.ui.animationstyle.GestureAnimationSettingsRows

import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.miuix.MiuixThemeAppearanceSettings

import kotlin.math.roundToInt



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun MainScreen(

    settings: HomeMainSettings,

    cornerGestureSettings: CornerGestureSettings,

    notificationGranted: Boolean,

    shizukuGranted: Boolean,

    accessibilityGranted: Boolean,

    batteryOptimizationExempt: Boolean,

    onRequestNotification: () -> Unit,

    onRequestShizuku: () -> Unit,

    onRequestAccessibility: () -> Unit,

    onRequestBatteryOptimization: () -> Unit = {},

    onGestureEnabledChange: (Boolean) -> Unit,

    onOpenAppKeepAliveSettings: () -> Unit,

    onOpenFloatBallSettings: () -> Unit,

    onHapticEnabledChange: (Boolean) -> Unit,

    onHapticStrengthChange: (Int) -> Unit,

    onOpenFreeWindowSettings: () -> Unit,

    onOpenExcludedAppsSettings: () -> Unit,

    onOpenTriggerCollection: () -> Unit,

    onOpenCornerWheel: () -> Unit,

    onOpenGestureAngle: () -> Unit,

    onOpenAnimationStyleSelect: () -> Unit,

    onGestureHintEnabledChange: (Boolean) -> Unit,

    onHideTriggerInLandscapeChange: (Boolean) -> Unit,

    onHideTriggerOnLockScreenChange: (Boolean) -> Unit,

    onHideTriggerOnLauncherChange: (Boolean) -> Unit,

    bottomContentPadding: Dp = 0.dp,

    bottomNavReselectCount: Int = 0,

    onDynamicColorChange: (Boolean) -> Unit,

    onThemeColorChange: (Int) -> Unit,

    onThemePaletteStyleChange: (com.slideindex.app.settings.ThemePaletteStyle) -> Unit,

    onThemeModeChange: (com.slideindex.app.settings.AppThemeMode) -> Unit,

    onCustomColorChange: (Boolean) -> Unit,

    onThemeColorSpecChange: (com.slideindex.app.settings.AppColorSpec) -> Unit,

    onBottomNavStyleChange: (com.slideindex.app.settings.BottomNavStyle) -> Unit,

    onBottomNavModeChange: (com.slideindex.app.settings.BottomNavMode) -> Unit,

    onBottomNavGlassEnabledChange: (Boolean) -> Unit,

    onBottomNavBlurRadiusChange: (Float) -> Unit,

    onBottomNavBlurPreviewChange: (Float) -> Unit = {},

    onBottomNavBlurPreviewStop: () -> Unit = {},

) {

    val gestureActive = settings.serviceEnabled && accessibilityGranted && notificationGranted

    val gestureSwitchChecked = gestureActive

    val listState = rememberLazyListState()

    BottomNavReselectScrollEffect(

        reselectCount = bottomNavReselectCount,

        listState = listState,

    )

    val pendingPermissions = buildList {

        if (!accessibilityGranted) {

            add(

                PendingPermissionItem(

                    title = stringResource(R.string.permission_accessibility_title),

                    description = stringResource(R.string.permission_accessibility_desc),

                    grantLabel = stringResource(R.string.permission_accessibility_grant),

                    onGrant = onRequestAccessibility,

                ),

            )

        }

        if (!notificationGranted) {

            add(

                PendingPermissionItem(

                    title = stringResource(R.string.permission_notification_title),

                    description = stringResource(R.string.permission_notification_desc),

                    grantLabel = stringResource(R.string.grant_permission),

                    onGrant = onRequestNotification,

                ),

            )

        }

        if (!shizukuGranted) {

            add(

                PendingPermissionItem(

                    title = stringResource(R.string.permission_shizuku_title),

                    description = stringResource(R.string.permission_shizuku_desc),

                    grantLabel = stringResource(R.string.permission_shizuku_grant),

                    onGrant = onRequestShizuku,

                ),

            )

        }

        if (!batteryOptimizationExempt) {

            add(

                PendingPermissionItem(

                    title = stringResource(R.string.battery_optimization_title),

                    description = stringResource(R.string.battery_optimization_desc),

                    grantLabel = stringResource(R.string.permission_battery_optimization_grant),

                    onGrant = onRequestBatteryOptimization,

                ),

            )

        }

    }



    MiuixHubScaffold(

        title = stringResource(R.string.app_name),

        subtitle = stringResource(R.string.main_settings_subtitle),

        modifier = Modifier.fillMaxSize(),

        listState = listState,

        bottomContentPadding = bottomContentPadding,

    ) {

            if (pendingPermissions.isNotEmpty()) {

                PendingPermissionsCard(items = pendingPermissions)

            }

            MiuixSmallTitle(stringResource(R.string.settings_section_service), modifier = Modifier.fillMaxWidth().padding(top = if (pendingPermissions.isNotEmpty()) MiuixSmallTitleSectionTop else 0.dp))
            SettingsCard {

                        SettingSwitchRow(

                            title = stringResource(R.string.gesture_switch),

                            subtitle = stringResource(R.string.gesture_switch_hint),

                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },

                            checked = gestureSwitchChecked,

                            enabled = true,

                            onCheckedChange = { enabled ->

                                if (enabled) {

                                    when {

                                        !accessibilityGranted -> onRequestAccessibility()

                                        !notificationGranted -> onRequestNotification()

                                        else -> onGestureEnabledChange(true)

                                    }

                                } else {

                                    onGestureEnabledChange(false)

                                }

                            },

                        )

                        AppKeepAliveEntryCard(

                            batteryOptimizationExempt = batteryOptimizationExempt,

                            hideFromRecents = settings.hideFromRecents,

                            accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,

                            onClick = onOpenAppKeepAliveSettings,

                        )

            }

            MiuixSmallTitle(stringResource(R.string.settings_section_features), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {

                        FloatBallEntryCard(

                            floatBallEnabled = settings.floatBallEnabled,

                            floatBallSizeDp = settings.floatBallSizeDp,

                            floatBallOpacity = settings.floatBallOpacity,

                            enabled = accessibilityGranted,

                            onClick = onOpenFloatBallSettings,

                        )

                        SettingNavigationRow(

                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },

                            title = stringResource(R.string.corner_wheel_home_title),

                            subtitle = cornerWheelHomeSubtitle(cornerGestureSettings),

                            enabled = accessibilityGranted,

                            onClick = onOpenCornerWheel,

                        )

            }

            MiuixSmallTitle(stringResource(R.string.settings_section_gestures), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {

                        SettingNavigationRow(

                            icon = { label -> Icon(Icons.Default.SwipeRight, contentDescription = label) },

                            title = stringResource(R.string.trigger_collection_title),

                            subtitle = stringResource(R.string.trigger_collection_desc),

                            onClick = onOpenTriggerCollection,

                        )

                        GestureAngleEntryCard(

                            enabled = gestureActive,

                            onClick = onOpenGestureAngle,

                        )

                        GestureAnimationSettingsRows(

                            settings = settings,

                            enabled = gestureActive,

                            onGestureHintEnabledChange = onGestureHintEnabledChange,

                            onOpenAnimationStyleSelect = onOpenAnimationStyleSelect,

                        )

            }

            MiuixSmallTitle(stringResource(R.string.settings_section_apps), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {

                        ExcludedAppsEntryCard(

                            excludedCount = settings.excludedAppScopes.size,

                            onClick = onOpenExcludedAppsSettings,

                        )

                        HideTriggerSettingsRows(

                            settings = settings,

                            enabled = gestureActive,

                            onHideInLandscapeChange = onHideTriggerInLandscapeChange,

                            onHideOnLockScreenChange = onHideTriggerOnLockScreenChange,

                            onHideOnLauncherChange = onHideTriggerOnLauncherChange,

                        )

                        FreeWindowEntryCard(onClick = onOpenFreeWindowSettings)

            }

            MiuixSmallTitle(stringResource(R.string.settings_section_feedback_appearance), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {

                        SettingSwitchRow(

                            title = stringResource(R.string.haptic_enabled),

                            icon = { label -> Icon(Icons.Default.Vibration, contentDescription = label) },

                            checked = settings.hapticEnabled,

                            enabled = true,

                            onCheckedChange = onHapticEnabledChange,

                        )

                        if (settings.hapticEnabled) {

                            val hapticLightLabel = stringResource(R.string.haptic_strength_light)
                            val hapticMediumLabel = stringResource(R.string.haptic_strength_medium)
                            val hapticStrongLabel = stringResource(R.string.haptic_strength_strong)
                            val hapticFormatLabel = remember(
                                hapticLightLabel,
                                hapticMediumLabel,
                                hapticStrongLabel,
                            ) {
                                { level: Float ->
                                    when (level.roundToInt()) {
                                        0 -> hapticLightLabel
                                        2 -> hapticStrongLabel
                                        else -> hapticMediumLabel
                                    }
                                }
                            }

                            SettingsSliderRow(

                                title = stringResource(R.string.haptic_strength),

                                value = settings.hapticStrengthLevel.toFloat(),

                                valueRange = 0f..2f,

                                steps = 1,

                                enabled = true,

                                label = hapticFormatLabel(settings.hapticStrengthLevel.toFloat()),

                                formatLabel = hapticFormatLabel,

                                onValueChange = { onHapticStrengthChange(it.roundToInt()) },

                            )

                        }

            }

            MiuixSmallTitle(stringResource(R.string.theme_appearance_settings), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))

            MiuixThemeAppearanceSettings(
                        themeModeId = settings.themeModeId,
                        customColorEnabled = settings.customColorEnabled,
                        dynamicColorEnabled = settings.dynamicColorEnabled,
                        themeColorArgb = settings.themeColorArgb,
                        paletteStyleId = settings.themePaletteStyleId,
                        themeColorSpecId = settings.themeColorSpecId,
                        bottomNavStyleId = settings.bottomNavStyleId,
                        bottomNavModeId = settings.bottomNavModeId,
                        bottomNavGlassEnabled = settings.bottomNavGlassEnabled,
                        bottomNavBlurRadiusDp = settings.bottomNavBlurRadiusDp,
                        onThemeModeChange = onThemeModeChange,
                        onCustomColorChange = onCustomColorChange,
                        onDynamicColorChange = onDynamicColorChange,
                        onThemeColorChange = onThemeColorChange,
                        onPaletteStyleChange = onThemePaletteStyleChange,
                        onThemeColorSpecChange = onThemeColorSpecChange,
                        onBottomNavStyleChange = onBottomNavStyleChange,
                        onBottomNavModeChange = onBottomNavModeChange,
                        onBottomNavGlassEnabledChange = onBottomNavGlassEnabledChange,
                        onBottomNavBlurRadiusChange = onBottomNavBlurRadiusChange,
                        onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                        onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                    )

    }

}



@Composable
private fun hapticStrengthLabel(level: Int): String {

    return when (level) {

        0 -> stringResource(R.string.haptic_strength_light)

        2 -> stringResource(R.string.haptic_strength_strong)

        else -> stringResource(R.string.haptic_strength_medium)

    }

}

@Composable
private fun cornerWheelHomeSubtitle(corner: CornerGestureSettings): String {
    if (!corner.enabled) {
        return stringResource(R.string.corner_wheel_home_desc)
    }
    val sides = buildList {
        if (corner.leftEnabled) add(stringResource(R.string.corner_gesture_left_enabled))
        if (corner.rightEnabled) add(stringResource(R.string.corner_gesture_right_enabled))
    }
    return if (sides.isEmpty()) {
        stringResource(R.string.corner_wheel_home_desc)
    } else {
        sides.joinToString(" · ")
    }
}

