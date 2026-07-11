package com.slideindex.app.ui.animationstyle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AnimationStyleLimits
import com.slideindex.app.settings.WaveStyle
import com.slideindex.app.ui.SettingSwitchRow
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsScreenScaffold
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.SettingsSliderRow
import kotlin.math.roundToInt

private enum class WaveColorTarget { Background, Stroke, Icon }

@Composable
fun WaveStyleSettingsScreen(
    style: WaveStyle,
    enabled: Boolean,
    onBack: () -> Unit,
    onStyleChange: (WaveStyle) -> Unit,
) {
    val density = LocalDensity.current.density
    var draft by remember(style) { mutableStateOf(style) }
    var colorTarget by remember { mutableStateOf<WaveColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                draft = when (colorTarget) {
                    WaveColorTarget.Background -> draft.copy(backgroundColor = color)
                    WaveColorTarget.Stroke -> draft.copy(strokeColor = color)
                    WaveColorTarget.Icon -> draft.copy(iconColor = color)
                    null -> draft
                }
                colorTarget = null
                onStyleChange(draft)
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_hint_style_wave),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.animation_style_color_outline))
        SettingsCard {
            AnimationStyleColorRow(
                title = stringResource(R.string.animation_style_background_color),
                color = draft.backgroundColor,
                enabled = enabled,
                onClick = {
                    pickerInitialColor = draft.backgroundColor
                    colorTarget = WaveColorTarget.Background
                },
            )
            AnimationStyleColorRow(
                title = stringResource(R.string.animation_style_stroke_color),
                color = draft.strokeColor,
                enabled = enabled,
                onClick = {
                    pickerInitialColor = draft.strokeColor
                    colorTarget = WaveColorTarget.Stroke
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_stroke_width),
                value = draft.strokeWidth.toFloat(),
                valueRange = AnimationStyleLimits.minStrokeWidthPx(density).toFloat()
                    ..AnimationStyleLimits.maxStrokeWidthPx(density).toFloat(),
                enabled = enabled,
                label = "${(draft.strokeWidth / density).roundToInt()} dp",
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_small),
                endLabel = stringResource(R.string.animation_style_large),
                onValueChange = {
                    draft = draft.copy(strokeWidth = it.roundToInt())
                    onStyleChange(draft)
                },
            )
        }

        SettingsSectionTitle(stringResource(R.string.animation_style_shape_size))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_width),
                value = draft.width.toFloat(),
                valueRange = AnimationStyleLimits.minWaveWidthPx(density).toFloat()
                    ..AnimationStyleLimits.maxWaveWidthPx(density).toFloat(),
                enabled = enabled,
                label = "${(draft.width / density).roundToInt()} dp",
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_small),
                endLabel = stringResource(R.string.animation_style_large),
                onValueChange = {
                    draft = draft.copy(width = it.roundToInt())
                    onStyleChange(draft)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_length),
                value = draft.bezierLengthHalfRatio,
                valueRange = AnimationStyleLimits.MIN_BEZIER_LENGTH..AnimationStyleLimits.MAX_BEZIER_LENGTH,
                enabled = enabled,
                label = String.format(java.util.Locale.US, "%.1f", draft.bezierLengthHalfRatio),
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_short),
                endLabel = stringResource(R.string.animation_style_long),
                onValueChange = {
                    draft = draft.copy(bezierLengthHalfRatio = it)
                    onStyleChange(draft)
                },
            )
            SettingSwitchRow(
                title = stringResource(R.string.animation_style_reserved_bounds),
                subtitle = stringResource(R.string.animation_style_reserved_bounds_hint),
                checked = draft.safeBounds,
                enabled = enabled,
                onCheckedChange = {
                    draft = draft.copy(safeBounds = it)
                    onStyleChange(draft)
                },
            )
            SettingSwitchRow(
                title = stringResource(R.string.animation_style_bezier_transform),
                subtitle = stringResource(R.string.animation_style_bezier_transform_hint),
                checked = draft.transformEnabled,
                enabled = enabled,
                onCheckedChange = {
                    draft = draft.copy(transformEnabled = it)
                    onStyleChange(draft)
                },
            )
        }

        SettingsSectionTitle(stringResource(R.string.animation_style_icon))
        SettingsCard {
            AnimationStyleColorRow(
                title = stringResource(R.string.animation_style_tint),
                color = draft.iconColor,
                enabled = enabled,
                onClick = {
                    pickerInitialColor = draft.iconColor
                    colorTarget = WaveColorTarget.Icon
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_scaling),
                value = draft.iconScale,
                valueRange = AnimationStyleLimits.MIN_ICON_SCALE..AnimationStyleLimits.MAX_ICON_SCALE,
                enabled = enabled,
                label = String.format(java.util.Locale.US, "%.2f", draft.iconScale),
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_small),
                endLabel = stringResource(R.string.animation_style_large),
                onValueChange = {
                    draft = draft.copy(iconScale = it)
                    onStyleChange(draft)
                },
            )
        }
        SettingsSectionTitle(stringResource(R.string.animation_style_custom_icon))
        AnimationStyleIconTypePicker(
            selectedType = draft.iconType,
            enabled = enabled,
            onTypeSelected = {
                draft = draft.copy(iconType = it)
                onStyleChange(draft)
            },
        )
    }
}
