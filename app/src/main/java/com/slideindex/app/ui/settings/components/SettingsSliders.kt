@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.RegisterSettingsSegment
import com.slideindex.app.ui.pickerSegmentedShapes
import com.slideindex.app.ui.settingsSegmentedColors
import kotlin.math.roundToInt

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
fun SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean,
    label: String,
    formatLabel: ((Float) -> String)? = null,
    commitOnFinish: Boolean = false,
    snapValue: ((Float) -> Float)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onValueChange: (Float) -> Unit,
) {
    var previewActive by remember { mutableStateOf(false) }
    val snap = remember(valueRange, snapValue) {
        snapValue ?: settingsSliderSnapValue(valueRange)
    }
    var localValue by remember(valueRange) {
        mutableFloatStateOf(snap(value).coerceIn(valueRange.start, valueRange.endInclusive))
    }
    var dragging by remember { mutableStateOf(false) }
    val sliderSteps = when {
        steps > 0 -> steps
        commitOnFinish && valueRange.endInclusive - valueRange.start > 1f ->
            (valueRange.endInclusive - valueRange.start).roundToInt()
        else -> 0
    }

    LaunchedEffect(value, valueRange) {
        if (!dragging) {
            localValue = snap(value).coerceIn(valueRange.start, valueRange.endInclusive)
        }
    }

    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = {},
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = settingsSegmentedColors(),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        },
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = formatLabel?.invoke(localValue) ?: label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            },
            supportingContent = {
                Column {
                    if (startLabel != null && endLabel != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = startLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = endLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(
                        value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
                        onValueChange = {
                            dragging = true
                            if (triggersLayoutPreview && !previewActive) {
                                previewActive = true
                                onLayoutPreviewStart()
                            }
                            val snapped = snap(it).coerceIn(valueRange.start, valueRange.endInclusive)
                            localValue = snapped
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
                        valueRange = valueRange,
                        steps = sliderSteps,
                        enabled = enabled,
                    )
                }
            },
        )
    }
}

@Composable
fun SettingsRangeSliderRow(
    title: String,
    values: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    startLabel: String,
    endLabel: String,
    enabled: Boolean,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
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
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = {},
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = settingsSegmentedColors(),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        },
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${(localValues.start * 100).roundToInt()}% – " +
                                "${(localValues.endInclusive * 100).roundToInt()}%",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            },
            supportingContent = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = startLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = endLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RangeSlider(
                        value = localValues,
                        onValueChange = {
                            dragging = true
                            if (triggersLayoutPreview && !previewActive) {
                                previewActive = true
                                onLayoutPreviewStart()
                            }
                            localValues = it
                            onValueChange(it)
                        },
                        onValueChangeFinished = {
                            dragging = false
                            if (triggersLayoutPreview && previewActive) {
                                previewActive = false
                                onLayoutPreviewStop()
                            }
                        },
                        valueRange = valueRange,
                        enabled = enabled,
                    )
                }
            },
        )
    }
}
