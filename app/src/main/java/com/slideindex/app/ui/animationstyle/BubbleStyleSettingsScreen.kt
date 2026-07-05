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
import com.slideindex.app.settings.BubbleStyle
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsScreenScaffold
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.SettingsSliderRow
import kotlin.math.roundToInt

private enum class BubbleColorTarget { Background, Stroke, Icon }

@Composable
fun BubbleStyleSettingsScreen(
    style: BubbleStyle,
    enabled: Boolean,
    onBack: () -> Unit,
    onStyleChange: (BubbleStyle) -> Unit,
) {
    val density = LocalDensity.current.density
    var draft by remember(style) { mutableStateOf(style) }
    var colorTarget by remember { mutableStateOf<BubbleColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                draft = when (colorTarget) {
                    BubbleColorTarget.Background -> draft.copy(backgroundColor = color)
                    BubbleColorTarget.Stroke -> draft.copy(strokeColor = color)
                    BubbleColorTarget.Icon -> draft.copy(iconColor = color)
                    null -> draft
                }
                colorTarget = null
                onStyleChange(draft)
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_hint_style_bubble),
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
                    colorTarget = BubbleColorTarget.Background
                },
            )
            AnimationStyleColorRow(
                title = stringResource(R.string.animation_style_stroke_color),
                color = draft.strokeColor,
                enabled = enabled,
                onClick = {
                    pickerInitialColor = draft.strokeColor
                    colorTarget = BubbleColorTarget.Stroke
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
                title = stringResource(R.string.animation_style_diameter),
                value = draft.diameter.toFloat(),
                valueRange = AnimationStyleLimits.minBubbleDiameterPx(density).toFloat()
                    ..AnimationStyleLimits.maxBubbleDiameterPx(density).toFloat(),
                enabled = enabled,
                label = "${(draft.diameter / density).roundToInt()} dp",
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_small),
                endLabel = stringResource(R.string.animation_style_large),
                onValueChange = {
                    draft = draft.copy(diameter = it.roundToInt())
                    onStyleChange(draft)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_pop_offset),
                value = draft.maxOffset.toFloat(),
                valueRange = AnimationStyleLimits.minBubbleOffsetPx(density).toFloat()
                    ..AnimationStyleLimits.maxBubbleOffsetPx(density).toFloat(),
                enabled = enabled,
                label = "${(draft.maxOffset / density).roundToInt()} dp",
                commitOnFinish = true,
                startLabel = stringResource(R.string.animation_style_short),
                endLabel = stringResource(R.string.animation_style_long),
                onValueChange = {
                    draft = draft.copy(maxOffset = it.roundToInt())
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
                    colorTarget = BubbleColorTarget.Icon
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.animation_style_scaling),
                value = draft.iconScale,
                valueRange = AnimationStyleLimits.MIN_ICON_SCALE..AnimationStyleLimits.MAX_ICON_SCALE,
                enabled = enabled,
                label = String.format("%.2f", draft.iconScale),
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
