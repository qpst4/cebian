package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
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
import android.os.Build
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
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeColorChange: (Int) -> Unit,
) {
    val gestureActive = settings.serviceEnabled && accessibilityGranted && notificationGranted
    val gestureSwitchChecked = gestureActive
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PendingPermissionsCard(items = pendingPermissions)

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

            SettingsSectionTitle(stringResource(R.string.settings_section_features))
            SettingsCard {
                FloatBallEntryCard(
                    settings = settings,
                    enabled = accessibilityGranted,
                    onClick = onOpenFloatBallSettings,
                )
            }

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
            }

            Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
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
