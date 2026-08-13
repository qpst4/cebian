package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_MAX_STEPS_WITH_KEY_POINTS
import com.slideindex.app.ui.settings.components.settingsSliderInferFormatLabel
import com.slideindex.app.ui.settings.components.settingsSliderSnapValue
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RangeSliderPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun MiuixHintText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
fun MiuixSwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        modifier = modifier,
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
fun MiuixArrowRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ArrowPreference(
        modifier = modifier,
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun MiuixSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    /** When true with [steps] > 0, matches MIUIX demo "Steps with Key Points". */
    showKeyPoints: Boolean = steps in 1..SETTINGS_SLIDER_MAX_STEPS_WITH_KEY_POINTS,
    /** MIUIX "Custom Key Points"; when set, [steps] is forced to 0 so mid values stay selectable. */
    keyPoints: List<Float>? = null,
    label: String = "",
    formatLabel: ((Float) -> String)? = null,
    commitOnFinish: Boolean = false,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (Float) -> Unit = {},
    onValueChange: (Float) -> Unit,
) {
    val useCustomKeyPoints = !keyPoints.isNullOrEmpty()
    val effectiveSteps = if (useCustomKeyPoints) 0 else steps
    // steps > 0：信 MIUIX 离散回调；Custom Key Points / 连续滑条才用 settingsSliderSnapValue。
    val snap = remember(valueRange, effectiveSteps) { settingsSliderSnapValue(valueRange, effectiveSteps) }
    val resolvedFormat = remember(label, valueRange, formatLabel) {
        formatLabel ?: settingsSliderInferFormatLabel(label, valueRange)
    }
    var localValue by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var previewActive by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!dragging) {
            localValue = value
        }
    }
    val displayValue = resolvedFormat(localValue)
    val discrete = effectiveSteps > 0
    SliderPreference(
        modifier = modifier,
        title = title,
        value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
        valueRange = valueRange,
        steps = effectiveSteps,
        enabled = enabled,
        valueText = displayValue,
        hapticEffect = if (discrete || useCustomKeyPoints) {
            SliderDefaults.SliderHapticEffect.Step
        } else {
            SliderDefaults.DefaultHapticEffect
        },
        showKeyPoints = when {
            useCustomKeyPoints -> true
            else -> showKeyPoints && discrete
        },
        keyPoints = keyPoints,
        onValueChange = { raw ->
            dragging = true
            val next = if (discrete) {
                raw.coerceIn(valueRange.start, valueRange.endInclusive)
            } else if (useCustomKeyPoints) {
                // 信 MIUIX 磁吸 + 连续中间值，不再套 0.1 吸附。
                raw.coerceIn(valueRange.start, valueRange.endInclusive)
            } else {
                snap(raw).coerceIn(valueRange.start, valueRange.endInclusive)
            }
            val changed = next != localValue
            localValue = next
            if (triggersLayoutPreview) {
                if (!previewActive) {
                    previewActive = true
                    onLayoutPreviewStart()
                }
                if (changed) {
                    onLayoutPreviewValueChange(next)
                }
            }
            if (!commitOnFinish) {
                onValueChange(next)
            }
        },
        onValueChangeFinished = {
            if (commitOnFinish) {
                onValueChange(localValue)
            }
            dragging = false
            if (triggersLayoutPreview && previewActive) {
                previewActive = false
                onLayoutPreviewStop()
            }
        },
    )
}

@Composable
fun MiuixRangeSliderRow(
    title: String,
    values: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    showKeyPoints: Boolean = steps in 1..SETTINGS_SLIDER_MAX_STEPS_WITH_KEY_POINTS,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (ClosedFloatingPointRange<Float>) -> Unit = {},
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    val snap = remember(valueRange, steps) { settingsSliderSnapValue(valueRange, steps) }
    var localValues by remember { mutableStateOf(values) }
    var dragging by remember { mutableStateOf(false) }
    var previewActive by remember { mutableStateOf(false) }
    LaunchedEffect(values) {
        if (!dragging) {
            localValues = values
        }
    }
    val discrete = steps > 0
    val snappedValues = remember(localValues, valueRange, discrete) {
        if (discrete) {
            val start = localValues.start.coerceIn(valueRange.start, valueRange.endInclusive)
            val end = localValues.endInclusive.coerceIn(valueRange.start, valueRange.endInclusive)
            if (start <= end) start..end else end..start
        } else {
            val start = snap(localValues.start).coerceIn(valueRange.start, valueRange.endInclusive)
            val end = snap(localValues.endInclusive).coerceIn(valueRange.start, valueRange.endInclusive)
            if (start <= end) start..end else end..start
        }
    }
    val valueText = "${(snappedValues.start * 100f).roundToInt()}% – " +
        "${(snappedValues.endInclusive * 100f).roundToInt()}%"
    RangeSliderPreference(
        modifier = modifier,
        title = title,
        value = snappedValues,
        valueText = valueText,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        hapticEffect = if (discrete) {
            SliderDefaults.SliderHapticEffect.Step
        } else {
            SliderDefaults.DefaultHapticEffect
        },
        showKeyPoints = showKeyPoints && discrete,
        bottomAction = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = startLabel,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = endLabel,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        onValueChange = { raw ->
            dragging = true
            val next = if (discrete) {
                val start = raw.start.coerceIn(valueRange.start, valueRange.endInclusive)
                val end = raw.endInclusive.coerceIn(valueRange.start, valueRange.endInclusive)
                if (start <= end) start..end else end..start
            } else {
                val start = snap(raw.start).coerceIn(valueRange.start, valueRange.endInclusive)
                val end = snap(raw.endInclusive).coerceIn(valueRange.start, valueRange.endInclusive)
                if (start <= end) start..end else end..start
            }
            val changed = next != localValues
            localValues = next
            if (triggersLayoutPreview) {
                if (!previewActive) {
                    previewActive = true
                    onLayoutPreviewStart()
                }
                if (changed) {
                    onLayoutPreviewValueChange(localValues)
                }
            }
        },
        onValueChangeFinished = {
            onValueChange(localValues)
            dragging = false
            if (triggersLayoutPreview && previewActive) {
                previewActive = false
                onLayoutPreviewStop()
            }
        },
    )
}

@Composable
fun MiuixGroupedCard(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.miuixGroupedCardItem(index, count)) {
        content()
    }
}
