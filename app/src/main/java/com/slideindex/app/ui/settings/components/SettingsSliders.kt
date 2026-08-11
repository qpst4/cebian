@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.slideindex.app.ui.miuix.MiuixRangeSliderRow
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import java.util.Locale
import kotlin.math.roundToInt

/** MIUIX "Steps with Key Points"：仅少量离散档位画点，避免细调滑条铺满标记。 */
internal const val SETTINGS_SLIDER_MAX_STEPS_WITH_KEY_POINTS = 8

/** MIUIX "Custom Key Points" for 0f..1f percent-like sliders. */
internal val SETTINGS_SLIDER_PERCENT_KEY_POINTS_01 = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

/** MIUIX "Custom Key Points" for 0f..100f percent sliders. */
internal val SETTINGS_SLIDER_PERCENT_KEY_POINTS_100 = listOf(0f, 25f, 50f, 75f, 100f)

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

/**
 * Continuous-range label/commit snap. When [steps] > 0, matches MIUIX
 * [top.yukonga.miuix.kmp.basic.Slider] discrete ticks (do not use 0.1 stepping).
 */
internal fun settingsSliderSnapValue(
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
): (Float) -> Float {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val span = end - start
    if (steps > 0 && span > 0f) {
        val stepCount = steps + 1
        return { value ->
            val clamped = value.coerceIn(start, end)
            val fraction = (clamped - start) / span
            val stepIndex = (fraction * stepCount).roundToInt().coerceIn(0, stepCount)
            start + span * stepIndex / stepCount
        }
    }
    return when {
        end <= 1f && start >= 0f ->
            { value ->
                val clamped = value.coerceIn(start, end)
                (clamped * 100f).roundToInt() / 100f
            }
        span <= 10f && end <= 10f ->
            { value -> (value * 10f).roundToInt() / 10f }
        else ->
            { value -> value.roundToInt().toFloat().coerceIn(start, end) }
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
    keyPoints: List<Float>? = null,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (Float) -> Unit = {},
    onValueChange: (Float) -> Unit,
) {
    val useCustomKeyPoints = !keyPoints.isNullOrEmpty()
    val sliderSteps = when {
        useCustomKeyPoints -> 0
        steps > 0 -> steps
        commitOnFinish && valueRange.endInclusive - valueRange.start > 1f ->
            (valueRange.endInclusive - valueRange.start).roundToInt()
        else -> 0
    }
    val snap = remember(valueRange, snapValue, sliderSteps) {
        snapValue ?: settingsSliderSnapValue(valueRange, sliderSteps)
    }

    SettingsCardRow(key = title) { position ->
        MiuixSliderRow(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            value = snap(value).coerceIn(valueRange.start, valueRange.endInclusive),
            valueRange = valueRange,
            enabled = enabled,
            steps = sliderSteps,
            // 少量显式档位才画点；Custom Key Points 由 keyPoints 控制。
            showKeyPoints = if (useCustomKeyPoints) {
                true
            } else {
                steps in 1..SETTINGS_SLIDER_MAX_STEPS_WITH_KEY_POINTS
            },
            keyPoints = keyPoints,
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
    SettingsCardRow(key = title) { position ->
        MiuixRangeSliderRow(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            values = values,
            valueRange = valueRange,
            startLabel = startLabel,
            endLabel = endLabel,
            enabled = enabled,
            triggersLayoutPreview = triggersLayoutPreview,
            onLayoutPreviewStart = onLayoutPreviewStart,
            onLayoutPreviewStop = onLayoutPreviewStop,
            onLayoutPreviewValueChange = onLayoutPreviewValueChange,
            onValueChange = onValueChange,
        )
    }
}
