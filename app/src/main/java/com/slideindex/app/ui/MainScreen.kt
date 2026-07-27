package com.slideindex.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.BottomNavBlurDefaults
import com.slideindex.app.ui.animationstyle.GestureAnimationSettingsRows
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    settings: AppSettings,
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
    onBottomNavBlurRadiusChange: (Float) -> Unit,
) {
    val gestureActive = settings.serviceEnabled && accessibilityGranted && notificationGranted
    val gestureSwitchChecked = gestureActive
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState,
        scrollBehavior = scrollBehavior,
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                subtitle = {
                    Text(stringResource(R.string.main_settings_subtitle))
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp + bottomContentPadding),
        ) {
            if (pendingPermissions.isNotEmpty()) {
                item(key = "pending_permissions") {
                    PendingPermissionsCard(items = pendingPermissions)
                }
            }

            item(key = "section_service_title") {
                SettingsSectionTitle(stringResource(R.string.settings_section_service))
            }
            item(key = "section_service_card") {
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

            item(key = "section_features_title") {
                SettingsSectionTitle(stringResource(R.string.settings_section_features))
            }
            item(key = "section_features_card") {
                SettingsCard {
                    FloatBallEntryCard(
                        settings = settings,
                        enabled = accessibilityGranted,
                        onClick = onOpenFloatBallSettings,
                    )
                }
            }

            item(key = "section_gestures_title") {
                SettingsSectionTitle(stringResource(R.string.settings_section_gestures))
            }
            item(key = "section_gestures_card") {
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

            item(key = "section_apps_title") {
                SettingsSectionTitle(stringResource(R.string.settings_section_apps))
            }
            item(key = "section_apps_card") {
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

            item(key = "section_appearance_title") {
                SettingsSectionTitle(stringResource(R.string.settings_section_feedback_appearance))
            }
            item(key = "section_appearance_card") {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingSwitchRow(
                            title = stringResource(R.string.dynamic_color),
                            subtitle = stringResource(R.string.dynamic_color_desc),
                            icon = { label -> Icon(Icons.Default.Palette, contentDescription = label) },
                            checked = settings.dynamicColorEnabled,
                            enabled = true,
                            onCheckedChange = onDynamicColorChange,
                        )
                    }
                    ThemeColorPicker(
                        selected = settings.themeColorArgb,
                        enabled = !settings.dynamicColorEnabled,
                        onColorSelected = onThemeColorChange,
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.bottom_nav_blur_radius),
                        value = settings.bottomNavBlurRadiusDp,
                        valueRange = BottomNavBlurDefaults.MIN_RADIUS_DP..BottomNavBlurDefaults.MAX_RADIUS_DP,
                        steps = (BottomNavBlurDefaults.MAX_RADIUS_DP - BottomNavBlurDefaults.MIN_RADIUS_DP).roundToInt(),
                        enabled = true,
                        label = "${settings.bottomNavBlurRadiusDp.roundToInt()} dp",
                        formatLabel = { value -> "${value.roundToInt()} dp" },
                        onValueChange = onBottomNavBlurRadiusChange,
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
