@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

internal fun settingsSliderDefaultFormatLabel(
    valueRange: ClosedFloatingPointRange<Float>,
): (Float) -> String {
    val snap = settingsSliderSnapValue(valueRange)
    return when {
        valueRange.endInclusive <= 1f && valueRange.start >= 0f ->
            { value -> "${(snap(value) * 100f).roundToInt()}%" }
        valueRange.endInclusive - valueRange.start <= 10f && valueRange.endInclusive <= 10f ->
            { value -> String.format(Locale.US, "%.1f", snap(value)) }
        else ->
            { value -> snap(value).roundToInt().toString() }
    }
}

internal fun settingsSliderInferFormatLabel(
    label: String,
    valueRange: ClosedFloatingPointRange<Float>,
): (Float) -> String {
    val snap = settingsSliderSnapValue(valueRange)
    val trimmed = label.trim()
    if (trimmed.isEmpty()) {
        return settingsSliderDefaultFormatLabel(valueRange)
    }
    if (trimmed.contains(" px") && trimmed.contains("(")) {
        return settingsSliderDefaultFormatLabel(valueRange)
    }
    when {
        trimmed.endsWith("%") -> {
            val treatAsFraction = valueRange.endInclusive <= 1f
            return if (treatAsFraction) {
                { value -> "${(snap(value) * 100f).roundToInt()}%" }
            } else {
                { value -> "${snap(value).roundToInt()}%" }
            }
        }
        trimmed.endsWith(" dp") -> {
            val hasDecimal = Regex("\\d+\\.\\d").containsMatchIn(trimmed)
            return if (hasDecimal) {
                { value -> String.format(Locale.US, "%.1f dp", snap(value)) }
            } else {
                { value -> String.format(Locale.US, "%.0f dp", snap(value)) }
            }
        }
        trimmed.endsWith("dp") ->
            return { value -> "${snap(value).roundToInt()}dp" }
        trimmed.endsWith(" sp") ->
            return { value -> String.format(Locale.US, "%.0f sp", snap(value)) }
        trimmed.endsWith(" ms") ->
            return { value -> "${snap(value).roundToInt()} ms" }
        trimmed.endsWith(" 毫秒") ->
            return { value -> "${snap(value).roundToInt()} 毫秒" }
        trimmed.endsWith(" px") ->
            return { value -> "${snap(value).roundToInt()} px" }
        else -> {
            val decimalMatch = Regex("^\\d+\\.(\\d+)").find(trimmed)
            if (decimalMatch != null) {
                val decimals = decimalMatch.groupValues[1].length
                val format = "%.${decimals}f"
                return { value -> String.format(Locale.US, format, snap(value)) }
            }
            return settingsSliderDefaultFormatLabel(valueRange)
        }
    }
}

internal fun settingsSliderSnapValue(valueRange: ClosedFloatingPointRange<Float>): (Float) -> Float {
    val span = valueRange.endInclusive - valueRange.start
    return when {
        valueRange.endInclusive <= 1f && valueRange.start >= 0f ->
            { value ->
                val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
                (clamped * 100f).roundToInt() / 100f
            }
        span <= 10f && valueRange.endInclusive <= 10f ->
            { value -> (value * 10f).roundToInt() / 10f }
        else ->
            { value -> value.roundToInt().toFloat().coerceIn(valueRange.start, valueRange.endInclusive) }
    }
}

@Composable
fun SettingsCardScope.SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean,
    label: String,
    formatLabel: ((Float) -> String)? = null,
    commitOnFinish: Boolean = true,
    snapValue: ((Float) -> Float)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (Float) -> Unit = {},
    onValueChange: (Float) -> Unit,
) {
    val snap = remember(valueRange, snapValue) {
        snapValue ?: settingsSliderSnapValue(valueRange)
    }
    val sliderSteps = when {
        steps > 0 -> steps
        commitOnFinish && valueRange.endInclusive - valueRange.start > 1f ->
            (valueRange.endInclusive - valueRange.start).roundToInt()
        else -> 0
    }

    SettingsCardRow(key = title) { position ->
        MiuixSliderRow(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            value = snap(value).coerceIn(valueRange.start, valueRange.endInclusive),
            valueRange = valueRange,
            enabled = enabled,
            steps = sliderSteps,
            label = label,
            formatLabel = formatLabel,
            commitOnFinish = commitOnFinish,
            triggersLayoutPreview = triggersLayoutPreview,
            onLayoutPreviewStart = onLayoutPreviewStart,
            onLayoutPreviewStop = onLayoutPreviewStop,
            onLayoutPreviewValueChange = onLayoutPreviewValueChange,
            onValueChange = onValueChange,
        )
    }
}

@Composable
fun SettingsCardScope.SettingsRangeSliderRow(
    title: String,
    values: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    startLabel: String,
    endLabel: String,
    enabled: Boolean,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (ClosedFloatingPointRange<Float>) -> Unit = {},
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    var previewActive by remember { mutableStateOf(false) }
    var localValues by remember { mutableStateOf(values) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(values) {
        if (!dragging) {
            localValues = values
        }
    }
    SettingsCardRow(key = title) { position ->
        Column(
            modifier = Modifier
                .miuixGroupedCardItem(position.index, position.count)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${(localValues.start * 100).roundToInt()}% – " +
                        "${(localValues.endInclusive * 100).roundToInt()}%",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
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
            RangeSlider(
                value = localValues,
                onValueChange = {
                    dragging = true
                    if (triggersLayoutPreview) {
                        if (!previewActive) {
                            previewActive = true
                        }
                        onLayoutPreviewStart()
                    }
                    localValues = it
                    if (triggersLayoutPreview) {
                        onLayoutPreviewValueChange(it)
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
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
