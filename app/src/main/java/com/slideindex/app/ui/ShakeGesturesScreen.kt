@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeGesturesScreen(
    settings: ShakeGestureSettings,
    bottomContentPadding: Dp = 0.dp,
    onEnabledChange: (Boolean) -> Unit,
    onBasicActionChange: (ShakeGestureType, GestureAction) -> Unit,
    onLockScreenShakeEnabledChange: (Boolean) -> Unit,
    onIndependentAppShakeEnabledChange: (Boolean) -> Unit,
    onGlobalSensitivityChange: (Float) -> Unit,
    onIndependentSensitivityEnabledChange: (Boolean) -> Unit,
    onOpenIndependentSensitivity: () -> Unit = {},
    onAnimationFeedbackEnabledChange: (Boolean) -> Unit,
    onVibrationFeedbackEnabledChange: (Boolean) -> Unit,
    onAnimationColorChange: (Int) -> Unit,
    onDisableInLandscapeChange: (Boolean) -> Unit,
    onOpenLockScreenShakeSettings: () -> Unit = {},
    onOpenIndependentAppShakeSettings: () -> Unit = {},
    onOpenAppBlacklist: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pickingGesture by remember { mutableStateOf<ShakeGestureType?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        AnimationStyleColorPickerDialog(
            initialColor = settings.animationColorArgb,
            onDismissRequest = { showColorPicker = false },
            onColorPicked = { color ->
                onAnimationColorChange(color)
                showColorPicker = false
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shake_gestures_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                subtitle = {
                    Text(stringResource(R.string.shake_gestures_subtitle))
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
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.shake_gestures_title),
                    subtitle = stringResource(R.string.shake_gestures_subtitle),
                    icon = { Icon(Icons.Default.ScreenRotation, contentDescription = null) },
                    checked = settings.enabled,
                    enabled = true,
                    onCheckedChange = onEnabledChange,
                )
            }

            SettingsSectionTitle(stringResource(R.string.shake_gestures_section_basic))
            SettingsCard {
                ShakeGestureType.entries.forEach { type ->
                    ShakeActionRow(
                        icon = shakeGestureIcon(type),
                        iconTint = shakeGestureIconTint(type),
                        title = shakeGestureLabel(type),
                        action = settings.actionFor(type),
                        enabled = true,
                        onClick = { pickingGesture = type },
                    )
                }
            }

            SettingsSectionTitle(stringResource(R.string.shake_gestures_section_advanced))
            SettingsCard {
                SettingSwitchNavigationRow(
                    title = stringResource(R.string.shake_gestures_lock_screen),
                    subtitle = stringResource(R.string.shake_gestures_lock_screen_desc),
                    icon = {
                        ColoredSettingIcon(
                            icon = Icons.Default.Lock,
                            background = Color(0xFF5C6BC0),
                        )
                    },
                    checked = settings.lockScreenShakeEnabled,
                    enabled = settings.enabled,
                    onCheckedChange = onLockScreenShakeEnabledChange,
                    onNavigate = onOpenLockScreenShakeSettings,
                )
                SettingSwitchNavigationRow(
                    title = stringResource(R.string.shake_gestures_independent_app),
                    subtitle = stringResource(R.string.shake_gestures_independent_app_desc),
                    icon = {
                        ColoredSettingIcon(
                            icon = Icons.Default.Apps,
                            background = Color(0xFFEF5350),
                        )
                    },
                    checked = settings.independentAppShakeEnabled,
                    enabled = settings.enabled,
                    onCheckedChange = onIndependentAppShakeEnabledChange,
                    onNavigate = onOpenIndependentAppShakeSettings,
                )
            }

            SettingsSectionTitle(stringResource(R.string.shake_gestures_section_sensitivity))
            SettingsCard {
                SettingsSliderRow(
                    title = stringResource(R.string.shake_gestures_global_sensitivity),
                    value = settings.globalSensitivity,
                    valueRange = 1f..10f,
                    steps = 8,
                    enabled = settings.enabled,
                    label = String.format(java.util.Locale.US, "%.1f", settings.globalSensitivity),
                    formatLabel = { String.format(java.util.Locale.US, "%.1f", it) },
                    startLabel = stringResource(R.string.shake_gestures_sensitivity_easy),
                    endLabel = stringResource(R.string.shake_gestures_sensitivity_hard),
                    onValueChange = onGlobalSensitivityChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.shake_gestures_independent_sensitivity),
                    subtitle = stringResource(R.string.shake_gestures_independent_sensitivity_desc),
                    checked = settings.independentSensitivityEnabled,
                    enabled = settings.enabled,
                    onCheckedChange = onIndependentSensitivityEnabledChange,
                )
                if (settings.independentSensitivityEnabled) {
                    SettingNavigationRow(
                        icon = {
                            ShakeGestureColoredIcon(
                                icon = Icons.Default.ScreenRotation,
                                background = Color(0xFF26A69A),
                            )
                        },
                        title = stringResource(R.string.shake_gestures_independent_sensitivity),
                        subtitle = stringResource(R.string.shake_gestures_sensitivity_hint),
                        enabled = settings.enabled,
                        onClick = onOpenIndependentSensitivity,
                    )
                }
            }
            SettingsHintText(stringResource(R.string.shake_gestures_sensitivity_hint))

            SettingsSectionTitle(stringResource(R.string.shake_gestures_section_feedback))
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.shake_gestures_vibration_feedback),
                    checked = settings.vibrationFeedbackEnabled,
                    enabled = settings.enabled,
                    onCheckedChange = onVibrationFeedbackEnabledChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.shake_gestures_animation_feedback),
                    checked = settings.animationFeedbackEnabled,
                    enabled = settings.enabled,
                    onCheckedChange = onAnimationFeedbackEnabledChange,
                )
                if (settings.animationFeedbackEnabled) {
                    AnimationStyleColorRow(
                        title = stringResource(R.string.shake_gestures_animation_color),
                        color = settings.animationColorArgb,
                        enabled = settings.enabled,
                        onClick = { showColorPicker = true },
                    )
                }
            }

            SettingsSectionTitle(stringResource(R.string.shake_gestures_section_advanced_features))
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.shake_gestures_disable_landscape),
                    checked = settings.disableInLandscape,
                    enabled = settings.enabled,
                    onCheckedChange = onDisableInLandscapeChange,
                )
                SettingNavigationRow(
                    icon = {
                        ColoredSettingIcon(
                            icon = Icons.Default.Block,
                            background = Color(0xFFFF9800),
                        )
                    },
                    title = stringResource(R.string.shake_gestures_app_blacklist),
                    subtitle = stringResource(R.string.shake_gestures_app_blacklist_desc),
                    enabled = settings.enabled,
                    onClick = onOpenAppBlacklist,
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (settings.blacklistedPackages.isNotEmpty()) {
                                Text(
                                    text = settings.blacklistedPackages.size.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
        }
    }

    val pickingType = pickingGesture
    if (pickingType != null) {
        Dialog(
            onDismissRequest = { pickingGesture = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                GestureActionPickerScreen(
                    trigger = GestureTriggerType.SHORT_SINGLE_TAP,
                    current = settings.actionFor(pickingType),
                    onDismiss = { pickingGesture = null },
                    onSelect = { action ->
                        onBasicActionChange(pickingType, action)
                        pickingGesture = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ShakeActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    action: GestureAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = {
            ColoredSettingIcon(icon = icon, background = iconTint)
        },
        title = title,
        subtitle = gestureActionLabel(action),
        enabled = enabled,
        onClick = onClick,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = gestureActionIcon(action),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = gestureActionLabel(action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun ColoredSettingIcon(
    icon: ImageVector,
    background: Color,
    contentColor: Color = Color.White,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
