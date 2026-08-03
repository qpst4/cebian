package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.TriggerCornerMode
import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.TriggerDesignPresets
import com.slideindex.app.gesture.detectPreset
import com.slideindex.app.gesture.TriggerRectanglePresetLogic
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.rectangleSettingsVisibility
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import com.slideindex.app.ui.settings.components.SettingDropdownRow
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
    onAlignOppositeDesignChange: (Boolean) -> Unit,
    onResetDefaults: () -> Unit,
    onPreviewStart: () -> Unit = {},
    onPreviewStop: () -> Unit = {},
    onDesignPreview: (TriggerHandleDesign) -> Unit = {},
    onDesignPreviewStop: () -> Unit = {},
) {
    val pairIndex = settings.triggerCollectionEntries().indexOfFirst { it.handleId == handleId }.let {
        if (it >= 0) it + 1 else 1
    }
    val pairCount = settings.triggerCollectionEntries().size
    val selectedHandle = settings.triggerHandle(side, handleId)
        ?: settings.primaryTriggerHandle(side)
    val persistedDesign = selectedHandle.design
    var draftDesign by remember(side, handleId) { mutableStateOf(persistedDesign) }
    var customizeLayoutEpoch by remember(side, handleId) { mutableIntStateOf(0) }
    var hasLocalEdits by remember(side, handleId) { mutableStateOf(false) }
    LaunchedEffect(side, handleId) {
        hasLocalEdits = false
        draftDesign = persistedDesign
        customizeLayoutEpoch = 0
    }
    LaunchedEffect(persistedDesign) {
        if (!hasLocalEdits) {
            draftDesign = persistedDesign
        }
    }
    val design = draftDesign
    val pairSuffix = if (pairCount > 1) " · $pairIndex" else ""

    TriggerHandlePreviewLifecycle(
        enabled = serviceEnabled,
        side = side,
        handleId = handleId,
        onPreviewStart = { _, _ -> onPreviewStart() },
        onPreviewStop = onPreviewStop,
    )

    fun updateDesign(updated: TriggerHandleDesign) {
        hasLocalEdits = true
        draftDesign = updated
        onDesignChange(updated)
    }

    fun applyKind(kind: TriggerDesignKind) {
        val updated = when (kind) {
            TriggerDesignKind.CONFIGURABLE_RECTANGLE ->
                TriggerRectanglePresetLogic.restoreRectangleDesign(
                    selectedHandle.copy(design = design),
                )
            else -> design.copy(kind = kind)
        }
        hasLocalEdits = true
        customizeLayoutEpoch++
        updateDesign(updated)
    }

    fun applyPreset(preset: TriggerDesignPreset) {
        val updatedHandle = TriggerRectanglePresetLogic.switchPreset(
            handle = selectedHandle.copy(design = design),
            target = preset,
        )
        hasLocalEdits = true
        customizeLayoutEpoch++
        draftDesign = updatedHandle.design
        onPresetApply(preset)
    }

    var colorTarget by remember { mutableStateOf<TriggerDesignColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }
    val kindEntries = TriggerDesignKind.entries
    val presetEntries = TriggerDesignPreset.entries
    val cornerModeEntries = TriggerCornerMode.entries
    val activePreset = selectedHandle.rectanglePresetState.activePreset
        ?: TriggerDesignPresets.detectPreset(design)
        ?: TriggerDesignPreset.BAR

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
                updateDesign(updated)
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
            SettingDropdownRow(
                title = stringResource(R.string.trigger_design_kind),
                items = kindEntries.map { triggerDesignKindLabel(it) },
                selectedIndex = kindEntries.indexOf(design.kind).coerceAtLeast(0),
                enabled = serviceEnabled,
                onSelectedIndexChange = { applyKind(kindEntries[it]) },
            )
            if (design.kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE) {
                SettingDropdownRow(
                    title = stringResource(R.string.trigger_design_preset),
                    items = presetEntries.map { triggerDesignPresetLabel(it) },
                    selectedIndex = presetEntries.indexOf(activePreset).coerceAtLeast(0),
                    enabled = serviceEnabled,
                    onSelectedIndexChange = { applyPreset(presetEntries[it]) },
                )
            }
            if (side.isHorizontalEdge) {
                SettingSwitchRow(
                    title = stringResource(R.string.trigger_design_align_handles),
                    subtitle = stringResource(R.string.trigger_design_align_handles_desc),
                    checked = selectedHandle.alignOppositeDesign,
                    enabled = serviceEnabled,
                    onCheckedChange = onAlignOppositeDesignChange,
                )
            }
        }

        if (design.kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE) {
            key(customizeLayoutEpoch) {
                val visibility = design.rectangleSettingsVisibility(
                    selectedHandle.rectanglePresetState.activePreset,
                )
                if (visibility.hasAny) {
                SettingsSectionTitle(stringResource(R.string.trigger_design_customize))
                SettingsCard {
                    if (visibility.body) {
                        SettingsSliderRow(
                            title = stringResource(R.string.trigger_design_size),
                            value = design.sizeDp,
                            valueRange = 0f..48f,
                            enabled = serviceEnabled,
                            label = "${design.sizeDp.roundToInt()} dp",
                            commitOnFinish = true,
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewValueChange = { value ->
                                onDesignPreview(design.copy(sizeDp = value))
                            },
                            onLayoutPreviewStop = onDesignPreviewStop,
                            onValueChange = { updateDesign(design.copy(sizeDp = it)) },
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.trigger_design_corner_radius),
                            value = design.cornerRadiusDp,
                            valueRange = 0f..32f,
                            enabled = serviceEnabled,
                            label = "${design.cornerRadiusDp.roundToInt()} dp",
                            commitOnFinish = true,
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewValueChange = { value ->
                                onDesignPreview(design.copy(cornerRadiusDp = value))
                            },
                            onLayoutPreviewStop = onDesignPreviewStop,
                            onValueChange = { updateDesign(design.copy(cornerRadiusDp = it)) },
                        )
                        SettingDropdownRow(
                            title = stringResource(R.string.trigger_design_corner_mode),
                            items = cornerModeEntries.map { triggerDesignCornerModeLabel(it) },
                            selectedIndex = cornerModeEntries.indexOf(design.cornerMode).coerceAtLeast(0),
                            enabled = serviceEnabled,
                            onSelectedIndexChange = { updateDesign(design.copy(cornerMode = cornerModeEntries[it])) },
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
                    }
                    if (visibility.border) {
                        SettingsSliderRow(
                            title = stringResource(R.string.trigger_design_border_size),
                            value = design.borderSizeDp,
                            valueRange = 0f..8f,
                            enabled = serviceEnabled,
                            label = "${design.borderSizeDp.roundToInt()} dp",
                            commitOnFinish = true,
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewValueChange = { value ->
                                onDesignPreview(design.copy(borderSizeDp = value))
                            },
                            onLayoutPreviewStop = onDesignPreviewStop,
                            onValueChange = { updateDesign(design.copy(borderSizeDp = it)) },
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
                    }
                    if (visibility.halo) {
                        SettingsSliderRow(
                            title = stringResource(R.string.trigger_design_halo_size),
                            value = design.haloSizeDp,
                            valueRange = 0f..48f,
                            enabled = serviceEnabled,
                            label = "${design.haloSizeDp.roundToInt()} dp",
                            commitOnFinish = true,
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewValueChange = { value ->
                                onDesignPreview(design.copy(haloSizeDp = value))
                            },
                            onLayoutPreviewStop = onDesignPreviewStop,
                            onValueChange = { updateDesign(design.copy(haloSizeDp = it)) },
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
                }
            }
        }

        if (design.kind == TriggerDesignKind.CUSTOM_IMAGE) {
            SettingsHintText(stringResource(R.string.trigger_design_custom_image_hint))
        }

        SettingsCard {
            SettingLinkRow(
                title = stringResource(R.string.trigger_design_reset),
                subtitle = stringResource(R.string.trigger_design_reset_desc),
                enabled = serviceEnabled,
                onClick = {
                    val resetDesign = TriggerRectanglePresetLogic.resetDesign(selectedHandle).design
                    hasLocalEdits = true
                    customizeLayoutEpoch++
                    draftDesign = resetDesign
                    onDesignChange(resetDesign)
                },
            )
        }
    }
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
private fun triggerDesignCornerModeLabel(mode: TriggerCornerMode): String = when (mode) {
    TriggerCornerMode.ALL -> stringResource(R.string.trigger_design_corner_mode_all)
    TriggerCornerMode.OUTER -> stringResource(R.string.trigger_design_corner_mode_outer)
}

@Composable
private fun triggerDesignPresetLabel(preset: TriggerDesignPreset): String = when (preset) {
    TriggerDesignPreset.BAR -> stringResource(R.string.trigger_design_preset_bar)
    TriggerDesignPreset.LINE -> stringResource(R.string.trigger_design_preset_line)
    TriggerDesignPreset.ROUNDED_RECT -> stringResource(R.string.trigger_design_preset_rounded_rect)
    TriggerDesignPreset.HALO -> stringResource(R.string.trigger_design_preset_halo)
    TriggerDesignPreset.LINE_AND_HALO -> stringResource(R.string.trigger_design_preset_line_and_halo)
}
