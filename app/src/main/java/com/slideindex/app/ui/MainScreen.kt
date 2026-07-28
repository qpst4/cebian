package com.slideindex.app.ui



import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Palette

import androidx.compose.material.icons.filled.SwipeRight

import androidx.compose.material.icons.filled.TouchApp

import androidx.compose.material.icons.filled.Vibration

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.settings.GestureHintStyle

import com.slideindex.app.settings.HomeMainSettings

import com.slideindex.app.settings.BottomNavBlurDefaults

import com.slideindex.app.ui.animationstyle.GestureAnimationSettingsRows

import com.slideindex.app.ui.settings.components.HubScrollColumn
import com.slideindex.app.ui.settings.components.HubTopAppBar
import com.slideindex.app.ui.settings.components.ThemeAppearanceSettings

import kotlin.math.roundToInt



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun MainScreen(

    settings: HomeMainSettings,

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

    onBottomNavGlassEnabledChange: (Boolean) -> Unit,

    onBottomNavBlurRadiusChange: (Float) -> Unit,

) {

    val gestureActive = settings.serviceEnabled && accessibilityGranted && notificationGranted

    val gestureSwitchChecked = gestureActive

    val scrollState = rememberScrollState()

    BottomNavReselectScrollEffect(

        reselectCount = bottomNavReselectCount,

        scrollState = scrollState,

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



    Scaffold(

        topBar = {

            HubTopAppBar(

                title = stringResource(R.string.app_name),

                subtitle = stringResource(R.string.main_settings_subtitle),

            )

        },

    ) { padding ->

        HubScrollColumn(

            scrollState = scrollState,

            modifier = Modifier

                .fillMaxSize()

                .padding(padding),

            bottomContentPadding = bottomContentPadding,

        ) {

            if (pendingPermissions.isNotEmpty()) {

                PendingPermissionsCard(items = pendingPermissions)

            }



            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                SettingsSectionTitle(stringResource(R.string.settings_section_service))

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

            }



            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                SettingsSectionTitle(stringResource(R.string.settings_section_features))

                    SettingsCard {

                        FloatBallEntryCard(

                            floatBallEnabled = settings.floatBallEnabled,

                            floatBallSizeDp = settings.floatBallSizeDp,

                            floatBallOpacity = settings.floatBallOpacity,

                            enabled = accessibilityGranted,

                            onClick = onOpenFloatBallSettings,

                        )

                    }

            }



            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                SettingsSectionTitle(stringResource(R.string.settings_section_gestures))

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

            }



            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                SettingsSectionTitle(stringResource(R.string.settings_section_apps))

                    SettingsCard {

                        ExcludedAppsEntryCard(

                            excludedCount = settings.excludedTriggerAppPackages.size,

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

            }



            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                SettingsSectionTitle(stringResource(R.string.settings_section_feedback_appearance))

                    SettingsCard {

                        SettingSwitchRow(

                            title = stringResource(R.string.haptic_enabled),

                            icon = { label -> Icon(Icons.Default.Vibration, contentDescription = label) },

                            checked = settings.hapticEnabled,

                            enabled = true,

                            onCheckedChange = onHapticEnabledChange,

                        )

                        if (settings.hapticEnabled) {

                            SettingsSliderRow(

                                title = stringResource(R.string.haptic_strength),

                                value = settings.hapticStrengthLevel.toFloat(),

                                valueRange = 0f..2f,

                                steps = 1,

                                enabled = true,

                                label = hapticStrengthLabel(settings.hapticStrengthLevel),

                                onValueChange = { onHapticStrengthChange(it.roundToInt()) },

                            )

                        }

                        SettingSwitchRow(

                            title = stringResource(R.string.bottom_nav_glass_enabled),

                            subtitle = stringResource(R.string.bottom_nav_glass_enabled_desc),

                            icon = { label -> Icon(Icons.Default.Palette, contentDescription = label) },

                            checked = settings.bottomNavGlassEnabled,

                            enabled = true,

                            onCheckedChange = onBottomNavGlassEnabledChange,

                        )

                        SettingsSliderRow(

                            title = stringResource(R.string.bottom_nav_blur_radius),

                            value = settings.bottomNavBlurRadiusDp,

                            valueRange = BottomNavBlurDefaults.MIN_RADIUS_DP..BottomNavBlurDefaults.MAX_RADIUS_DP,

                            steps = (BottomNavBlurDefaults.MAX_RADIUS_DP - BottomNavBlurDefaults.MIN_RADIUS_DP).roundToInt(),

                            enabled = settings.bottomNavGlassEnabled,

                            label = "${settings.bottomNavBlurRadiusDp.roundToInt()} dp",

                            formatLabel = { value -> "${value.roundToInt()} dp" },

                            onValueChange = onBottomNavBlurRadiusChange,

                        )

                    }

                    SettingsCard {

                        ThemeAppearanceSettings(

                            themeColorArgb = settings.themeColorArgb,

                            dynamicColorEnabled = settings.dynamicColorEnabled,

                            paletteStyleId = settings.themePaletteStyleId,

                            onDynamicColorChange = onDynamicColorChange,

                            onThemeColorChange = onThemeColorChange,

                            onPaletteStyleChange = onThemePaletteStyleChange,

                        )

                    }

            }

        }

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

