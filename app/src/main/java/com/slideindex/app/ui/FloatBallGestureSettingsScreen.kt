package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.FloatBallStyleType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallGestureSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: () -> Unit,
    onGestureActionChange: (FloatBallGestureType, GestureAction) -> Unit,
    onDownSwipeShortPercentChange: (Float) -> Unit,
    onSideSwipeShortPercentChange: (Float) -> Unit,
    onUpSwipeShortPercentChange: (Float) -> Unit,
) {
    var pickingGesture by remember { mutableStateOf<FloatBallGestureType?>(null) }
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_gesture_settings_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.float_ball_gesture_distance_section))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_gesture_down_swipe_distance),
                value = settings.floatBallDownSwipeShortPercent,
                valueRange = 50f..500f,
                steps = 18,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    settings.floatBallDownSwipeShortPercent.roundToInt(),
                ),
                onValueChange = onDownSwipeShortPercentChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_gesture_side_swipe_distance),
                value = settings.floatBallSideSwipeShortPercent,
                valueRange = 50f..500f,
                steps = 18,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    settings.floatBallSideSwipeShortPercent.roundToInt(),
                ),
                onValueChange = onSideSwipeShortPercentChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_gesture_up_swipe_distance),
                value = settings.floatBallUpSwipeShortPercent,
                valueRange = 50f..500f,
                steps = 18,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    settings.floatBallUpSwipeShortPercent.roundToInt(),
                ),
                onValueChange = onUpSwipeShortPercentChange,
            )
        }
        SettingsSectionTitle(stringResource(R.string.float_ball_gesture_actions_section))
        SettingsCard {
            FloatBallGestureType.entries.forEach { type ->
                val action = settings.floatBallGestureActions[type] ?: GestureAction.None
                FloatBallGestureActionRow(
                    title = floatBallGestureLabel(type),
                    action = action,
                    enabled = controlsEnabled,
                    showSettings = action is GestureAction.LaunchApp ||
                        action is GestureAction.LaunchShortcut ||
                        action is GestureAction.SimulatePointerSwipe,
                    onClick = { pickingGesture = type },
                )
            }
        }
    }

    AnimatedFullScreenOverlay(visible = pickingGesture != null) {
        pickingGesture?.let { type ->
            GestureActionPickerScreen(
                trigger = GestureTriggerType.SHORT_SINGLE_TAP,
                current = settings.floatBallGestureActions[type] ?: GestureAction.None,
                onDismiss = { pickingGesture = null },
                onSelect = { action ->
                    onGestureActionChange(type, action)
                    pickingGesture = null
                },
            )
        }
    }
}

@Composable
private fun FloatBallGestureActionRow(
    title: String,
    action: GestureAction,
    enabled: Boolean,
    showSettings: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
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
                    contentDescription = gestureActionLabel(action),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = gestureActionLabel(action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showSettings) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = stringResource(R.string.float_ball_gesture_action_settings),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_navigate_forward),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
fun floatBallGestureLabel(type: FloatBallGestureType): String = when (type) {
    FloatBallGestureType.SWIPE_UP_SHORT -> stringResource(R.string.float_ball_gesture_swipe_up_short)
    FloatBallGestureType.SWIPE_UP_LONG -> stringResource(R.string.float_ball_gesture_swipe_up_long)
    FloatBallGestureType.SWIPE_DOWN_SHORT -> stringResource(R.string.float_ball_gesture_swipe_down_short)
    FloatBallGestureType.SWIPE_DOWN_LONG -> stringResource(R.string.float_ball_gesture_swipe_down_long)
    FloatBallGestureType.SWIPE_SIDE_SHORT -> stringResource(R.string.float_ball_gesture_swipe_side_short)
    FloatBallGestureType.SWIPE_SIDE_LONG -> stringResource(R.string.float_ball_gesture_swipe_side_long)
    FloatBallGestureType.SINGLE_TAP -> stringResource(R.string.float_ball_gesture_single_tap)
    FloatBallGestureType.DOUBLE_TAP -> stringResource(R.string.float_ball_gesture_double_tap)
    FloatBallGestureType.LONG_PRESS -> stringResource(R.string.float_ball_gesture_long_press)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallStyleSection(
    settings: AppSettings,
    enabled: Boolean,
    onStyleTypeChange: (FloatBallStyleType) -> Unit,
    onCustomImageUriChange: (String) -> Unit,
    onSlideshowUrisChange: (List<String>) -> Unit,
    onGifUriChange: (String) -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { onCustomImageUriChange(it.toString()) }
    }
    val slideshowPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSlideshowUrisChange(uris.map { it.toString() })
        }
    }
    val gifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { onGifUriChange(it.toString()) }
    }

    SettingsSectionTitle(stringResource(R.string.float_ball_section_style))
    SettingsCard {
        FloatBallStyleType.entries.forEach { style ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onStyleTypeChange(style) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = settings.floatBallStyleType == style,
                    onClick = { if (enabled) onStyleTypeChange(style) },
                    enabled = enabled,
                )
                Text(
                    text = floatBallStyleLabel(style),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                when (style) {
                    FloatBallStyleType.CUSTOM_IMAGE -> {
                        if (settings.floatBallStyleType == style) {
                            IconButton(
                                onClick = { imagePicker.launch("image/*") },
                                enabled = enabled,
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                            }
                        }
                    }
                    FloatBallStyleType.SLIDESHOW -> {
                        if (settings.floatBallStyleType == style) {
                            IconButton(
                                onClick = { slideshowPicker.launch(arrayOf("image/*")) },
                                enabled = enabled,
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                            }
                        }
                    }
                    FloatBallStyleType.GIF -> {
                        if (settings.floatBallStyleType == style) {
                            IconButton(
                                onClick = { gifPicker.launch("image/*") },
                                enabled = enabled,
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    when (settings.floatBallStyleType) {
        FloatBallStyleType.CUSTOM_IMAGE -> {
            if (settings.floatBallCustomImageUri.isNotBlank()) {
                SettingsHintText(stringResource(R.string.float_ball_style_image_selected))
            } else {
                SettingsHintText(stringResource(R.string.float_ball_style_custom_image_hint))
            }
        }
        FloatBallStyleType.SLIDESHOW -> {
            SettingsHintText(
                pluralStringResource(
                    R.plurals.float_ball_style_slideshow_hint,
                    settings.floatBallSlideshowUris.size,
                    settings.floatBallSlideshowUris.size,
                ),
            )
        }
        FloatBallStyleType.GIF -> {
            if (settings.floatBallGifUri.isBlank()) {
                SettingsHintText(stringResource(R.string.float_ball_style_gif_hint))
            }
        }
        else -> Unit
    }
}

@Composable
fun floatBallStyleLabel(style: FloatBallStyleType): String = when (style) {
    FloatBallStyleType.DEFAULT -> stringResource(R.string.float_ball_style_default)
    FloatBallStyleType.PRESET_1 -> stringResource(R.string.float_ball_style_preset_1)
    FloatBallStyleType.PRESET_2 -> stringResource(R.string.float_ball_style_preset_2)
    FloatBallStyleType.PRESET_3 -> stringResource(R.string.float_ball_style_preset_3)
    FloatBallStyleType.PRESET_4 -> stringResource(R.string.float_ball_style_preset_4)
    FloatBallStyleType.CUSTOM_IMAGE -> stringResource(R.string.float_ball_style_custom_image)
    FloatBallStyleType.SLIDESHOW -> stringResource(R.string.float_ball_style_slideshow)
    FloatBallStyleType.GIF -> stringResource(R.string.float_ball_style_gif)
}
