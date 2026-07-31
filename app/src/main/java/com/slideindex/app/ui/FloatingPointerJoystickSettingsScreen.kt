package com.slideindex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerJoystickPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import kotlin.math.roundToInt

private enum class JoystickColorTarget {
    Inner,
    Outer,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerJoystickSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onJoystickDiameterChange: (Float) -> Unit,
    onInnerColorChange: (Int) -> Unit,
    onOuterColorChange: (Int) -> Unit,
    onGradientRadiusChange: (Float) -> Unit,
    onHideOnOutsideClickChange: (Boolean) -> Unit,
    onHideOnQuickSwipeChange: (Boolean) -> Unit,
    onHideWhenIdleChange: (Boolean) -> Unit,
    onIdleDelayChange: (Int) -> Unit,
    onClickDistanceThresholdChange: (Float) -> Unit,
    onResetVisualDefaults: () -> Unit,
    onResetBehaviorDefaults: () -> Unit,
) {
    var colorTarget by remember { mutableStateOf<JoystickColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }
    var joystickPreviewDragging by remember { mutableStateOf(false) }
    var previewJoystickDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerJoystickDiameterPx)
    }
    var previewGradientRadiusFraction by remember {
        mutableFloatStateOf(settings.floatingPointerJoystickGradientRadiusFraction)
    }
    var previewClickDistanceThresholdDp by remember {
        mutableFloatStateOf(settings.floatingPointerClickDistanceThresholdDp)
    }
    var previewIdleHideDelayMs by remember {
        mutableIntStateOf(settings.floatingPointerIdleHideDelayMs)
    }
    val density = LocalDensity.current.density

    LaunchedEffect(
        settings.floatingPointerJoystickDiameterPx,
        settings.floatingPointerJoystickGradientRadiusFraction,
        settings.floatingPointerClickDistanceThresholdDp,
        settings.floatingPointerIdleHideDelayMs,
    ) {
        if (!joystickPreviewDragging) {
            previewJoystickDiameterPx = settings.floatingPointerJoystickDiameterPx
            previewGradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction
            previewClickDistanceThresholdDp = settings.floatingPointerClickDistanceThresholdDp
            previewIdleHideDelayMs = settings.floatingPointerIdleHideDelayMs
        }
    }

    val previewSettings = settings.copy(
        floatingPointerJoystickDiameterPx = previewJoystickDiameterPx,
        floatingPointerJoystickGradientRadiusFraction = previewGradientRadiusFraction,
        floatingPointerClickDistanceThresholdDp = previewClickDistanceThresholdDp,
        floatingPointerIdleHideDelayMs = previewIdleHideDelayMs,
    )

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                when (colorTarget) {
                    JoystickColorTarget.Inner -> onInnerColorChange(color)
                    JoystickColorTarget.Outer -> onOuterColorChange(color)
                    null -> Unit
                }
                colorTarget = null
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.floating_pointer_joystick_settings_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.floating_pointer_preview_section))
        Surface(
            modifier = Modifier.padding(bottom = 4.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            FloatingPointerJoystickPreview(settings = previewSettings)
        }

        SettingsSectionTitle(stringResource(R.string.floating_pointer_joystick_visual_section))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_joystick_size),
                value = settings.floatingPointerJoystickDiameterPx,
                valueRange = 180f..360f,
                steps = 17,
                enabled = true,
                label = stringResource(
                    R.string.floating_pointer_size_px_value,
                    settings.floatingPointerJoystickDiameterPx.roundToInt(),
                ),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = { joystickPreviewDragging = true },
                onLayoutPreviewStop = { joystickPreviewDragging = false },
                onLayoutPreviewValueChange = { previewJoystickDiameterPx = it },
                onValueChange = onJoystickDiameterChange,
            )
            AnimationStyleColorRow(
                title = stringResource(R.string.floating_pointer_joystick_inner_color),
                color = settings.floatingPointerJoystickInnerColorArgb,
                enabled = true,
                onClick = {
                    pickerInitialColor = settings.floatingPointerJoystickInnerColorArgb
                    colorTarget = JoystickColorTarget.Inner
                },
            )
            AnimationStyleColorRow(
                title = stringResource(R.string.floating_pointer_joystick_outer_color),
                color = settings.floatingPointerJoystickOuterColorArgb,
                enabled = true,
                onClick = {
                    pickerInitialColor = settings.floatingPointerJoystickOuterColorArgb
                    colorTarget = JoystickColorTarget.Outer
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_joystick_gradient_radius),
                value = settings.floatingPointerJoystickGradientRadiusFraction,
                valueRange = 0.5f..1f,
                steps = 9,
                enabled = true,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatingPointerJoystickGradientRadiusFraction * 100).roundToInt(),
                ),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = { joystickPreviewDragging = true },
                onLayoutPreviewStop = { joystickPreviewDragging = false },
                onLayoutPreviewValueChange = { previewGradientRadiusFraction = it },
                onValueChange = onGradientRadiusChange,
            )
        }
        SettingLinkRow(
            title = stringResource(R.string.floating_pointer_reset_joystick_visual),
            onClick = onResetVisualDefaults,
        )

        SettingsSectionTitle(stringResource(R.string.floating_pointer_joystick_behavior_section))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_click_distance_threshold),
                value = settings.floatingPointerClickDistanceThresholdDp,
                valueRange = 1f..30f,
                steps = 28,
                enabled = true,
                label = stringResource(
                    R.string.floating_pointer_size_px_dp_value,
                    (settings.floatingPointerClickDistanceThresholdDp * density).roundToInt(),
                    settings.floatingPointerClickDistanceThresholdDp,
                ),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = { joystickPreviewDragging = true },
                onLayoutPreviewStop = { joystickPreviewDragging = false },
                onLayoutPreviewValueChange = { previewClickDistanceThresholdDp = it },
                onValueChange = onClickDistanceThresholdChange,
            )
            Text(
                text = stringResource(R.string.floating_pointer_click_distance_threshold_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_hide_outside_click),
                subtitle = stringResource(R.string.floating_pointer_hide_outside_click_desc),
                checked = settings.floatingPointerHideOnOutsideClick,
                enabled = true,
                onCheckedChange = onHideOnOutsideClickChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_hide_quick_swipe),
                subtitle = stringResource(R.string.floating_pointer_hide_quick_swipe_desc),
                checked = settings.floatingPointerHideOnQuickSwipe,
                enabled = true,
                onCheckedChange = onHideOnQuickSwipeChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_hide_idle),
                subtitle = stringResource(R.string.floating_pointer_hide_idle_desc),
                checked = settings.floatingPointerHideWhenIdle,
                enabled = true,
                onCheckedChange = onHideWhenIdleChange,
            )
            if (settings.floatingPointerHideWhenIdle) {
                SettingsSliderRow(
                    title = stringResource(R.string.floating_pointer_hide_idle_delay),
                    value = settings.floatingPointerIdleHideDelayMs.toFloat(),
                    valueRange = 1000f..10000f,
                    steps = 8,
                    enabled = true,
                    label = stringResource(
                        R.string.floating_pointer_hide_idle_delay_value,
                        settings.floatingPointerIdleHideDelayMs / 1000,
                    ),
                    triggersLayoutPreview = true,
                    onLayoutPreviewStart = { joystickPreviewDragging = true },
                    onLayoutPreviewStop = { joystickPreviewDragging = false },
                    onLayoutPreviewValueChange = { previewIdleHideDelayMs = it.roundToInt() },
                    onValueChange = { onIdleDelayChange(it.roundToInt()) },
                )
            }
        }
        SettingLinkRow(
            title = stringResource(R.string.floating_pointer_reset_joystick_behavior),
            onClick = onResetBehaviorDefaults,
        )
    }
}
