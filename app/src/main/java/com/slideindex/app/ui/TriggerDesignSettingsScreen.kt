package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.primaryTriggerHandle
import com.slideindex.app.gesture.triggerCollectionEntries
import com.slideindex.app.gesture.triggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import kotlin.math.roundToInt

private enum class TriggerDesignColorTarget {
    Background,
    Border,
    Halo,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TriggerDesignSettingsScreen(
    side: PanelSide,
    handleId: String,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onDesignChange: (TriggerHandleDesign) -> Unit,
    onPresetApply: (TriggerDesignPreset) -> Unit,
) {
    val pairIndex = settings.triggerCollectionEntries().indexOfFirst { it.handleId == handleId }.let {
        if (it >= 0) it + 1 else 1
    }
    val pairCount = settings.triggerCollectionEntries().size
    val selectedHandle = settings.triggerHandle(side, handleId)
        ?: settings.primaryTriggerHandle(side)
    val design = selectedHandle.design
    val pairSuffix = if (pairCount > 1) " · $pairIndex" else ""

    var pickingKind by remember { mutableStateOf(false) }
    var pickingPreset by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<TriggerDesignColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                val updated = when (colorTarget) {
                    TriggerDesignColorTarget.Background -> design.copy(backgroundColor = color)
                    TriggerDesignColorTarget.Border -> design.copy(borderColor = color)
                    TriggerDesignColorTarget.Halo -> design.copy(haloColor = color)
                    null -> design
                }
                colorTarget = null
                onDesignChange(updated)
            },
        )
    }

    if (pickingKind) {
        TriggerDesignKindDialog(
            current = design.kind,
            onDismiss = { pickingKind = false },
            onSelect = { kind ->
                onDesignChange(design.copy(kind = kind))
                pickingKind = false
            },
        )
    }

    if (pickingPreset) {
        TriggerDesignPresetDialog(
            onDismiss = { pickingPreset = false },
            onSelect = { preset ->
                onPresetApply(preset)
                pickingPreset = false
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.trigger_design_title),
        subtitle = stringResource(R.string.trigger_design_desc) + pairSuffix,
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.trigger_design_section))
        SettingsCard {
            TriggerDesignKindRow(
                kind = design.kind,
                enabled = serviceEnabled,
                onClick = { pickingKind = true },
                onPresetClick = { pickingPreset = true },
            )
        }

        if (design.kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE) {
            SettingsSectionTitle(stringResource(R.string.trigger_design_customize))
            SettingsCard {
                SettingsSliderRow(
                    title = stringResource(R.string.trigger_design_size),
                    value = design.sizeDp,
                    valueRange = 0f..48f,
                    enabled = serviceEnabled,
                    label = "${design.sizeDp.roundToInt()} px",
                    onValueChange = { onDesignChange(design.copy(sizeDp = it)) },
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.trigger_design_background_color),
                    color = design.backgroundColor,
                    enabled = serviceEnabled,
                    onClick = {
                        pickerInitialColor = design.backgroundColor
                        colorTarget = TriggerDesignColorTarget.Background
                    },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.trigger_design_border_size),
                    value = design.borderSizeDp,
                    valueRange = 0f..8f,
                    enabled = serviceEnabled,
                    label = "${design.borderSizeDp.roundToInt()} px",
                    onValueChange = { onDesignChange(design.copy(borderSizeDp = it)) },
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.trigger_design_border_color),
                    color = design.borderColor,
                    enabled = serviceEnabled,
                    onClick = {
                        pickerInitialColor = design.borderColor
                        colorTarget = TriggerDesignColorTarget.Border
                    },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.trigger_design_halo_size),
                    value = design.haloSizeDp,
                    valueRange = 0f..48f,
                    enabled = serviceEnabled,
                    label = "${design.haloSizeDp.roundToInt()} px",
                    onValueChange = { onDesignChange(design.copy(haloSizeDp = it)) },
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.trigger_design_halo_color),
                    color = design.haloColor,
                    enabled = serviceEnabled,
                    onClick = {
                        pickerInitialColor = design.haloColor
                        colorTarget = TriggerDesignColorTarget.Halo
                    },
                )
            }
        }

        if (design.kind == TriggerDesignKind.CUSTOM_IMAGE) {
            SettingsHintText(stringResource(R.string.trigger_design_custom_image_hint))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriggerDesignKindRow(
    kind: TriggerDesignKind,
    enabled: Boolean,
    onClick: () -> Unit,
    onPresetClick: () -> Unit,
) {
    val showPresetAction = kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = pickerSegmentedColors(),
            trailingContent = {
                if (showPresetAction) {
                    IconButton(
                        onClick = onPresetClick,
                        enabled = enabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.trigger_design_preset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            content = {
                Text(
                    text = stringResource(R.string.trigger_design_kind),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            },
            supportingContent = {
                Text(
                    text = triggerDesignKindLabel(kind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun TriggerDesignKindDialog(
    current: TriggerDesignKind,
    onDismiss: () -> Unit,
    onSelect: (TriggerDesignKind) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.trigger_design_kind))
        },
        text = {
            Column {
                TriggerDesignKind.entries.forEach { kind ->
                    Text(
                        text = triggerDesignKindLabel(kind),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (kind == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(kind) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun TriggerDesignPresetDialog(
    onDismiss: () -> Unit,
    onSelect: (TriggerDesignPreset) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.trigger_design_preset))
        },
        text = {
            Column {
                TriggerDesignPreset.entries.forEach { preset ->
                    Text(
                        text = triggerDesignPresetLabel(preset),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(preset)
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
internal fun triggerDesignSummary(design: TriggerHandleDesign): String = when (design.kind) {
    TriggerDesignKind.HIDE -> stringResource(R.string.trigger_design_kind_hide)
    TriggerDesignKind.CONFIGURABLE_RECTANGLE -> stringResource(R.string.trigger_design_kind_rectangle)
    TriggerDesignKind.CUSTOM_IMAGE -> stringResource(R.string.trigger_design_kind_custom_image)
}

@Composable
private fun triggerDesignKindLabel(kind: TriggerDesignKind): String = when (kind) {
    TriggerDesignKind.HIDE -> stringResource(R.string.trigger_design_kind_hide)
    TriggerDesignKind.CONFIGURABLE_RECTANGLE -> stringResource(R.string.trigger_design_kind_rectangle)
    TriggerDesignKind.CUSTOM_IMAGE -> stringResource(R.string.trigger_design_kind_custom_image)
}

@Composable
private fun triggerDesignPresetLabel(preset: TriggerDesignPreset): String = when (preset) {
    TriggerDesignPreset.BAR -> stringResource(R.string.trigger_design_preset_bar)
    TriggerDesignPreset.LINE -> stringResource(R.string.trigger_design_preset_line)
    TriggerDesignPreset.ROUNDED_RECT -> stringResource(R.string.trigger_design_preset_rounded_rect)
    TriggerDesignPreset.HALO -> stringResource(R.string.trigger_design_preset_halo)
    TriggerDesignPreset.LINE_AND_HALO -> stringResource(R.string.trigger_design_preset_line_and_halo)
}
