@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shake.FaceDownGestureSettings
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import com.slideindex.app.ui.miuix.MiuixHubScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeGesturesScreen(
    settings: ShakeGestureSettings,
    faceDownSettings: FaceDownGestureSettings,
    bottomContentPadding: Dp = 0.dp,
    bottomNavReselectCount: Int = 0,
    onEnabledChange: (Boolean) -> Unit,
    onLockScreenShakeEnabledChange: (Boolean) -> Unit,
    onIndependentAppShakeEnabledChange: (Boolean) -> Unit,
    onGlobalSensitivityChange: (Float) -> Unit,
    onIndependentSensitivityEnabledChange: (Boolean) -> Unit,
    onOpenIndependentSensitivity: () -> Unit = {},
    onAnimationFeedbackEnabledChange: (Boolean) -> Unit,
    onVibrationFeedbackEnabledChange: (Boolean) -> Unit,
    onAnimationColorChange: (Int) -> Unit,
    onDisableInLandscapeChange: (Boolean) -> Unit,
    onFaceDownEnabledChange: (Boolean) -> Unit,
    onFaceDownHoldDurationChange: (Long) -> Unit,
    onFaceDownRequireProximityChange: (Boolean) -> Unit,
    onFaceDownDisableInLandscapeChange: (Boolean) -> Unit,
    onFaceDownVibrationFeedbackChange: (Boolean) -> Unit,
    onFaceDownAudioFeedbackChange: (Boolean) -> Unit,
    onOpenLockScreenShakeSettings: () -> Unit = {},
    onOpenIndependentAppShakeSettings: () -> Unit = {},
    onOpenAppBlacklist: () -> Unit = {},
    onOpenBasicActionPick: (ShakeGestureType) -> Unit = {},
    onOpenFaceDownActionPick: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState,
    )
    var showColorPicker by remember { mutableStateOf(false) }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val formatFaceDownHoldDuration: (Float) -> String = remember(resources) {
        { seconds -> resources.getString(R.string.face_down_gestures_hold_duration_value, seconds) }
    }

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

    MiuixHubScaffold(
        title = stringResource(R.string.shake_gestures_title),
        subtitle = stringResource(R.string.shake_gestures_subtitle),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding,
    ) {
            SettingsCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.shake_gestures_title),
                        subtitle = stringResource(R.string.shake_gestures_subtitle),
                        icon = { label -> Icon(Icons.Default.ScreenRotation, contentDescription = label) },
                        checked = settings.enabled,
                        enabled = true,
                        onCheckedChange = onEnabledChange,
                    )

            }

            MiuixSmallTitle(
                text = stringResource(R.string.shake_gestures_section_basic),
                modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
            )
            SettingsCard {
                        ShakeGestureType.entries.forEach { type ->
                            ShakeActionRow(
                                icon = shakeGestureIcon(type),
                                iconTint = shakeGestureIconTint(type),
                                title = shakeGestureLabel(type),
                                action = settings.actionFor(type),
                                enabled = true,
                                onClick = { onOpenBasicActionPick(type) },
                            )
                        }
            }

            MiuixSmallTitle(stringResource(R.string.face_down_gestures_title), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_title),
                            subtitle = stringResource(R.string.face_down_gestures_subtitle),
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.PhoneAndroid,
                                    background = Color(0xFF78909C),
                                    contentDescription = label,
                                )
                            },
                            checked = faceDownSettings.enabled,
                            enabled = true,
                            onCheckedChange = onFaceDownEnabledChange,
                        )
                        ShakeActionRow(
                            icon = Icons.Default.Lock,
                            iconTint = Color(0xFF5C6BC0),
                            title = stringResource(R.string.face_down_gestures_action),
                            action = faceDownSettings.action,
                            enabled = true,
                            onClick = onOpenFaceDownActionPick,
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.face_down_gestures_hold_duration),
                            value = faceDownSettings.holdDurationMs / 1000f,
                            valueRange = 0.5f..1.5f,
                            steps = 9,
                            enabled = faceDownSettings.enabled,
                            label = formatFaceDownHoldDuration(faceDownSettings.holdDurationMs / 1000f),
                            formatLabel = formatFaceDownHoldDuration,
                            onValueChange = { seconds ->
                                onFaceDownHoldDurationChange((seconds * 1000f).toLong())
                            },
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_require_proximity),
                            subtitle = stringResource(R.string.face_down_gestures_require_proximity_desc),
                            checked = faceDownSettings.requireProximity,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onFaceDownRequireProximityChange,
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_disable_landscape),
                            checked = faceDownSettings.disableInLandscape,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onFaceDownDisableInLandscapeChange,
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_vibration_feedback),
                            checked = faceDownSettings.vibrationFeedbackEnabled,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onFaceDownVibrationFeedbackChange,
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_audio_feedback),
                            subtitle = stringResource(R.string.face_down_gestures_audio_feedback_desc),
                            checked = faceDownSettings.audioFeedbackEnabled,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onFaceDownAudioFeedbackChange,
                        )
            }
            SettingsHintText(stringResource(R.string.face_down_gestures_blacklist_hint))

            MiuixSmallTitle(stringResource(R.string.shake_gestures_section_advanced), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                        SettingSwitchNavigationRow(
                            title = stringResource(R.string.shake_gestures_lock_screen),
                            subtitle = stringResource(R.string.shake_gestures_lock_screen_desc),
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.Lock,
                                    background = Color(0xFF5C6BC0),
                                    contentDescription = label,
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
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.Apps,
                                    background = Color(0xFFEF5350),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.independentAppShakeEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = onIndependentAppShakeEnabledChange,
                            onNavigate = onOpenIndependentAppShakeSettings,
                        )
            }

            MiuixSmallTitle(stringResource(R.string.shake_gestures_section_sensitivity), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
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
                        SettingSwitchNavigationRow(
                            title = stringResource(R.string.shake_gestures_independent_sensitivity),
                            subtitle = stringResource(R.string.shake_gestures_independent_sensitivity_desc),
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.ScreenRotation,
                                    background = Color(0xFF26A69A),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.independentSensitivityEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = onIndependentSensitivityEnabledChange,
                            onNavigate = onOpenIndependentSensitivity,
                        )
            }
            SettingsHintText(stringResource(R.string.shake_gestures_sensitivity_hint))

            MiuixSmallTitle(stringResource(R.string.shake_gestures_section_feedback), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
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

            MiuixSmallTitle(stringResource(R.string.shake_gestures_section_advanced_features), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                        SettingSwitchRow(
                            title = stringResource(R.string.shake_gestures_disable_landscape),
                            checked = settings.disableInLandscape,
                            enabled = settings.enabled,
                            onCheckedChange = onDisableInLandscapeChange,
                        )
                        SettingNavigationRow(
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.Block,
                                    background = Color(0xFFFF9800),
                                    contentDescription = label,
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
                                        contentDescription = stringResource(R.string.cd_navigate_forward), modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
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
        icon = { label ->
            ColoredSettingIcon(icon = icon, background = iconTint, contentDescription = label)
        },
        title = title,
        subtitle = gestureActionSettingSubtitle(action),
        enabled = enabled,
        onClick = onClick,
        trailingContent = {
            GestureActionSettingTrailing(
                action = action,
                enabled = enabled,
                onClick = onClick,
            )
        },
    )
}

@Composable
private fun ColoredSettingIcon(
    icon: ImageVector,
    background: Color,
    contentColor: Color = Color.White,
    contentDescription: String,
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
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
