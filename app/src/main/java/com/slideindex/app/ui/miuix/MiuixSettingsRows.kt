package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
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
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = displayValue,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Slider(
            value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
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
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
