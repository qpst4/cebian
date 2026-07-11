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
import com.slideindex.app.settings.CapsuleStyle
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsScreenScaffold
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.SettingsSliderRow
import kotlin.math.roundToInt

private enum class CapsuleColorTarget { Background, Stroke, Icon }

@Composable
fun CapsuleStyleSettingsScreen(
    style: CapsuleStyle,
    enabled: Boolean,
    onBack: () -> Unit,
    onStyleChange: (CapsuleStyle) -> Unit,
) {
    val density = LocalDensity.current.density
    var draft by remember(style) { mutableStateOf(style) }
    var colorTarget by remember { mutableStateOf<CapsuleColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                draft = when (colorTarget) {
                    CapsuleColorTarget.Background -> draft.copy(backgroundColor = color)
                    CapsuleColorTarget.Stroke -> draft.copy(strokeColor = color)
                    CapsuleColorTarget.Icon -> draft.copy(iconColor = color)
                    null -> draft
                }
                colorTarget = null
                onStyleChange(draft)
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_hint_style_capsule),
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
                    colorTarget = CapsuleColorTarget.Background
                },
            )
            AnimationStyleColorRow(
                title = stringResource(R.string.animation_style_stroke_color),
                color = draft.strokeColor,
                enabled = enabled,
                onClick = {
                    pickerInitialColor = draft.strokeColor
                    colorTarget = CapsuleColorTarget.Stroke
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
                value = draft.thickness.toFloat(),
                valueRange = AnimationStyleLimits.minCapsuleThicknessPx(density).toFloat()
                    ..AnimationStyleLimits.maxCapsuleThicknessPx(density).toFloat(),
                enabled = enabled,
                label = "${(draft.thickness / density).roundToInt()} dp",
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_small),
                endLabel = stringResource(R.string.animation_style_large),
                onValueChange = {
                    draft = draft.copy(thickness = it.roundToInt())
                    onStyleChange(draft)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_length),
                value = draft.maxLength.toFloat(),
                valueRange = AnimationStyleLimits.minCapsuleLengthPx(density).toFloat()
                    ..AnimationStyleLimits.maxCapsuleLengthPx(density).toFloat(),
                enabled = enabled,
                label = "${(draft.maxLength / density).roundToInt()} dp",
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_short),
                endLabel = stringResource(R.string.animation_style_long),
                onValueChange = {
                    draft = draft.copy(maxLength = it.roundToInt())
                    onStyleChange(draft)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_corner_radius),
                value = draft.cornerRadius.toFloat(),
                valueRange = AnimationStyleLimits.minCapsuleCornerRadiusPx(density).toFloat()
                    ..AnimationStyleLimits.maxCapsuleCornerRadiusPx(density).toFloat(),
                enabled = enabled,
                label = "${(draft.cornerRadius / density).roundToInt()} dp",
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_small),
                endLabel = stringResource(R.string.animation_style_large),
                onValueChange = {
                    draft = draft.copy(cornerRadius = it.roundToInt())
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
                    colorTarget = CapsuleColorTarget.Icon
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
