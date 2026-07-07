@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.toShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import kotlin.math.roundToInt

private fun settingsSliderSnapValue(valueRange: ClosedFloatingPointRange<Float>): (Float) -> Float {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                subtitle = subtitle?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Md3PickerSectionHeader(title = title, modifier = modifier)
}

@Composable
fun SettingsHintText(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = { if (enabled) onCheckedChange(!checked) },
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = settingsSegmentedColors(),
            leadingContent = icon?.let {
                {
                    SettingIconContainer { it() }
                }
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { if (enabled) onCheckedChange(it) },
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
        )
    }
}

@Composable
fun SettingSwitchNavigationRow(
    title: String,
    subtitle: String,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: () -> Unit,
) {
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = { if (enabled) onNavigate() },
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = settingsSegmentedColors(),
            leadingContent = icon?.let {
                {
                    SettingIconContainer { it() }
                }
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    VerticalDivider(
                        modifier = Modifier.height(32.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides 0.dp,
                    ) {
                        Switch(
                            checked = checked,
                            enabled = enabled,
                            onCheckedChange = { if (enabled) onCheckedChange(it) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
        )
    }
}

@Composable
fun SettingLinkRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = settingsSegmentedColors(),
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
        )
    }
}

@Composable
fun SettingToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = { if (enabled) onCheckedChange(!checked) },
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = settingsSegmentedColors(),
            leadingContent = {
                SettingIconContainer { icon() }
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { if (enabled) onCheckedChange(it) },
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
        )
    }
}

@Composable
fun SettingNavigationRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = settingsSegmentedColors(),
            leadingContent = {
                SettingIconContainer { icon() }
            },
            trailingContent = {
                if (trailingContent != null) {
                    trailingContent()
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
        )
    }
}

@Composable
fun SettingRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = pickerSegmentedColors(),
            trailingContent = {
                androidx.compose.material3.RadioButton(
                    selected = selected,
                    onClick = null,
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
        )
    }
}

@Composable
fun SettingsRadioGroup(content: @Composable () -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        SettingsCard(content = content)
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
        mutableStateOf(snap(value).coerceIn(valueRange.start, valueRange.endInclusive))
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

@Composable
fun PermissionCard(
    title: String,
    description: String,
    onGrant: () -> Unit,
    grantLabel: String = stringResource(R.string.grant_permission),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Spacer(modifier = Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onGrant) {
                Text(grantLabel)
            }
        }
    }
}

@Composable
fun ThemeColorPicker(
    selected: Int,
    enabled: Boolean,
    onColorSelected: (Int) -> Unit,
) {
    val colors = listOf(
        0xFF6750A4.toInt(),
        0xFF0061A4.toInt(),
        0xFF386A20.toInt(),
        0xFF984061.toInt(),
        0xFF7D5260.toInt(),
        0xFF006874.toInt(),
    )
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        RegisterSettingsSegment { segmentIndex, segmentCount ->
            SegmentedListItem(
                onClick = {},
                enabled = enabled,
                shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
                colors = settingsSegmentedColors(),
                content = {
                    Text(
                        stringResource(R.string.theme_color),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colors.forEach { color ->
                            val isSelected = color == selected
                            val swatchShape = if (isSelected) {
                                MaterialShapes.Cookie9Sided.toShape()
                            } else {
                                CircleShape
                            }
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(swatchShape)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, swatchShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable(enabled = enabled) { onColorSelected(color) },
                                shape = swatchShape,
                                color = Color(color),
                            ) {}
                        }
                    }
                },
            )
        }
    }
}
