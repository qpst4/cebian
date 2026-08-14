package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.ui.animationstyle.GestureAnimationSettingsRows
import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.themeAppearanceSettingsCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScopeContent
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.HapticHelper
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

    val serviceSectionTitle = stringResource(R.string.settings_section_service)
    val featuresSectionTitle = stringResource(R.string.settings_section_features)
    val gesturesSectionTitle = stringResource(R.string.settings_section_gestures)
    val appsSectionTitle = stringResource(R.string.settings_section_apps)
    val feedbackSectionTitle = stringResource(R.string.settings_section_feedback_appearance)
    val hideTriggerItems = hideTriggerSettingsCardItems(
        hideTriggerInLandscape = settings.hideTriggerInLandscape,
        hideTriggerOnLockScreen = settings.hideTriggerOnLockScreen,
        hideTriggerOnLauncher = settings.hideTriggerOnLauncher,
        enabled = gestureActive,
        outlinedLeadingIcons = true,
        onHideInLandscapeChange = onHideTriggerInLandscapeChange,
        onHideOnLockScreenChange = onHideTriggerOnLockScreenChange,
        onHideOnLauncherChange = onHideTriggerOnLauncherChange,
    )
    val themeAppearanceItems = themeAppearanceSettingsCardItems(
        outlinedPreferenceIcons = true,
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

    MiuixHubScaffold(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.main_settings_subtitle),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding,
    ) {
        LazySettingsItem(key = "pending-permissions") {
            if (pendingPermissions.isNotEmpty()) {
                PendingPermissionsCardContent(items = pendingPermissions)
            }
        }

        settingsLazySmallTitle(
            key = "service_section",
            title = serviceSectionTitle,
            sectionTop = pendingPermissions.isNotEmpty(),
        )
        groupedCardItems(
            keyPrefix = "main_service",
            items = buildList {
                add(
                    settingsCardItem("gesture-enabled") {
                        SettingsCardScopeContent {
                            SettingSwitchRow(
                            title = stringResource(R.string.gesture_switch),
                            subtitle = stringResource(R.string.gesture_switch_hint),
                            icon = { label ->
                                Icon(HomeLeadingIcons.gesture(true), contentDescription = label)
                            },
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
                        }
                    },
                )
                add(
                    settingsCardItem("app-keep-alive") {
                        SettingsCardScopeContent {
                            AppKeepAliveEntryCard(
                                batteryOptimizationExempt = batteryOptimizationExempt,
                                hideFromRecents = settings.hideFromRecents,
                                accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,
                                outlinedLeadingIcons = true,
                                onClick = onOpenAppKeepAliveSettings,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "features_section", title = featuresSectionTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "main_features",
            items = buildList {
                add(
                    settingsCardItem("float-ball") {
                        SettingsCardScopeContent {
                            FloatBallEntryCard(
                                floatBallEnabled = settings.floatBallEnabled,
                                floatBallSizeDp = settings.floatBallSizeDp,
                                floatBallOpacity = settings.floatBallOpacity,
                                enabled = accessibilityGranted,
                                outlinedLeadingIcons = true,
                                onClick = onOpenFloatBallSettings,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("corner-wheel") {
                        SettingsCardScopeContent {
                            SettingNavigationRow(
                            icon = { label ->
                                Icon(HomeLeadingIcons.cornerWheel(true), contentDescription = label)
                            },
                            title = stringResource(R.string.corner_wheel_home_title),
                            subtitle = cornerWheelHomeSubtitle(cornerGestureSettings),
                            enabled = accessibilityGranted,
                            onClick = onOpenCornerWheel,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "gestures_section", title = gesturesSectionTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "main_gestures",
            items = buildList {
                add(
                    settingsCardItem("trigger-collection") {
                        SettingsCardScopeContent {
                            SettingNavigationRow(
                            icon = { label ->
                                Icon(HomeLeadingIcons.triggerCollection(true), contentDescription = label)
                            },
                            title = stringResource(R.string.trigger_collection_title),
                            subtitle = stringResource(R.string.trigger_collection_desc),
                            onClick = onOpenTriggerCollection,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("gesture-angle") {
                        SettingsCardScopeContent {
                            GestureAngleEntryCard(
                                enabled = gestureActive,
                                outlinedLeadingIcons = true,
                                onClick = onOpenGestureAngle,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("gesture-animation") {
                        SettingsCardScopeContent {
                            GestureAnimationSettingsRows(
                                settings = settings,
                                enabled = gestureActive,
                                outlinedLeadingIcons = true,
                                onGestureHintEnabledChange = onGestureHintEnabledChange,
                                onOpenAnimationStyleSelect = onOpenAnimationStyleSelect,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "apps_section", title = appsSectionTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "main_apps",
            items = buildList {
                add(
                    settingsCardItem("excluded-apps") {
                        SettingsCardScopeContent {
                            ExcludedAppsEntryCard(
                                excludedCount = settings.excludedAppScopes.size,
                                outlinedLeadingIcons = true,
                                onClick = onOpenExcludedAppsSettings,
                            )
                        }
                    },
                )
                addAll(hideTriggerItems)
                add(
                    settingsCardItem("free-window") {
                        SettingsCardScopeContent {
                            FreeWindowEntryCard(
                                outlinedLeadingIcons = true,
                                onClick = onOpenFreeWindowSettings,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "feedback_section", title = feedbackSectionTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "main_feedback",
            items = buildList {
                add(
                    settingsCardItem("haptic-enabled") {
                        val view = LocalView.current
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
                        SettingsCardScopeContent {
                            SettingExpandableSwitchRow(
                                title = stringResource(R.string.haptic_enabled),
                                checked = settings.hapticEnabled,
                                enabled = true,
                                onCheckedChange = onHapticEnabledChange,
                            ) {
                                SettingsSliderRow(
                                    title = stringResource(R.string.haptic_strength),
                                    value = settings.hapticStrengthLevel.toFloat(),
                                    valueRange = 0f..2f,
                                    steps = 1,
                                    enabled = true,
                                    label = hapticFormatLabel(settings.hapticStrengthLevel.toFloat()),
                                    formatLabel = hapticFormatLabel,
                                    commitOnFinish = true,
                                    triggersLayoutPreview = true,
                                    onLayoutPreviewValueChange = { level ->
                                        HapticHelper.preview(
                                            view,
                                            AppSettings(
                                                hapticEnabled = true,
                                                hapticStrengthLevel = level.roundToInt(),
                                            ),
                                        )
                                    },
                                    onValueChange = { onHapticStrengthChange(it.roundToInt()) },
                                )
                            }
                        }
                    },
                )
                addAll(themeAppearanceItems)
            },
        )
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
