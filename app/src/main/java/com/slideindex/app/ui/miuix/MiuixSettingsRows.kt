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
import com.slideindex.app.ui.settings.components.settingsSliderInferFormatLabel
import com.slideindex.app.ui.settings.components.settingsSliderSnapValue
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
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
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
    summary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
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
    enabled: Boolean = true,
    steps: Int = 0,
    label: String = "",
    formatLabel: ((Float) -> String)? = null,
    commitOnFinish: Boolean = false,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    val snap = remember(valueRange) { settingsSliderSnapValue(valueRange) }
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
    SliderPreference(
        modifier = modifier,
        title = title,
        value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        valueText = displayValue,
        onValueChange = { raw ->
            dragging = true
            if (triggersLayoutPreview) {
                if (!previewActive) {
                    previewActive = true
                }
                onLayoutPreviewStart()
            }
            val snapped = snap(raw).coerceIn(valueRange.start, valueRange.endInclusive)
            localValue = snapped
            if (triggersLayoutPreview) {
                onLayoutPreviewValueChange(snapped)
            }
            if (!commitOnFinish) {
                onValueChange(snapped)
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
    enabled: Boolean = true,
    steps: Int = 0,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (ClosedFloatingPointRange<Float>) -> Unit = {},
    modifier: Modifier = Modifier,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    val snap = remember(valueRange) { settingsSliderSnapValue(valueRange) }
    var localValues by remember { mutableStateOf(values) }
    var dragging by remember { mutableStateOf(false) }
    var previewActive by remember { mutableStateOf(false) }
    LaunchedEffect(values) {
        if (!dragging) {
            localValues = values
        }
    }
    val snappedValues = remember(localValues, valueRange) {
        val start = snap(localValues.start).coerceIn(valueRange.start, valueRange.endInclusive)
        val end = snap(localValues.endInclusive).coerceIn(valueRange.start, valueRange.endInclusive)
        if (start <= end) start..end else end..start
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
            if (triggersLayoutPreview) {
                if (!previewActive) {
                    previewActive = true
                }
                onLayoutPreviewStart()
            }
            val start = snap(raw.start).coerceIn(valueRange.start, valueRange.endInclusive)
            val end = snap(raw.endInclusive).coerceIn(valueRange.start, valueRange.endInclusive)
            localValues = if (start <= end) start..end else end..start
            if (triggersLayoutPreview) {
                onLayoutPreviewValueChange(localValues)
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
