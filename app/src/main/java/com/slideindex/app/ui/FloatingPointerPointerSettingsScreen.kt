package com.slideindex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerDesignPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import com.slideindex.app.settings.FloatingPointerTrailType
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import kotlin.math.roundToInt

private enum class PointerColorTarget {
    Ring,
    Fill,
    Dot,
    Trail,
    Ripple,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerPointerSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onPointerDiameterChange: (Float) -> Unit,
    onRingThicknessChange: (Float) -> Unit,
    onDotDiameterChange: (Float) -> Unit,
    onRingColorChange: (Int) -> Unit,
    onFillColorChange: (Int) -> Unit,
    onDotColorChange: (Int) -> Unit,
    onClickVisualFeedbackChange: (Boolean) -> Unit,
    onClickHapticChange: (Boolean) -> Unit,
    onRippleColorChange: (Int) -> Unit,
    onRippleSizeChange: (Float) -> Unit,
    onRippleDurationChange: (Int) -> Unit,
    onTrailTypeChange: (FloatingPointerTrailType) -> Unit,
    onTrailDurationChange: (Int) -> Unit,
    onTrailColorChange: (Int) -> Unit,
    onHideWhenReleasedChange: (Boolean) -> Unit,
    onPointerDesignChange: (FloatingPointerDesign) -> Unit,
    onResetVisualDefaults: () -> Unit,
) {
    var colorTarget by remember { mutableStateOf<PointerColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }
    var pickingDesign by remember { mutableStateOf(false) }
    var pointerSizeDragging by remember { mutableStateOf(false) }
    var previewPointerDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerPointerDiameterPx)
    }
    var previewRingThicknessPx by remember {
        mutableFloatStateOf(settings.floatingPointerRingThicknessPx)
    }
    var previewDotDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerDotDiameterPx)
    }
    val density = LocalDensity.current.density
    val resources = LocalResources.current
    val formatPxDpLabel = remember(density, resources) {
        { px: Float ->
            resources.getString(
                R.string.floating_pointer_size_px_dp_value,
                px.roundToInt(),
                px / density,
            )
        }
    }
    val selectedDesign = FloatingPointerDesign.fromId(settings.floatingPointerDesignId)

    LaunchedEffect(
        settings.floatingPointerPointerDiameterPx,
        settings.floatingPointerRingThicknessPx,
        settings.floatingPointerDotDiameterPx,
    ) {
        if (!pointerSizeDragging) {
            previewPointerDiameterPx = settings.floatingPointerPointerDiameterPx
            previewRingThicknessPx = settings.floatingPointerRingThicknessPx
            previewDotDiameterPx = settings.floatingPointerDotDiameterPx
        }
    }

    val previewSettings = settings.copy(
        floatingPointerPointerDiameterPx = previewPointerDiameterPx,
        floatingPointerRingThicknessPx = previewRingThicknessPx,
        floatingPointerDotDiameterPx = previewDotDiameterPx,
    )

    @Composable
    fun pxDpLabel(px: Float): String = formatPxDpLabel(px)

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                when (colorTarget) {
                    PointerColorTarget.Ring -> onRingColorChange(color)
                    PointerColorTarget.Fill -> onFillColorChange(color)
                    PointerColorTarget.Dot -> onDotColorChange(color)
                    PointerColorTarget.Trail -> onTrailColorChange(color)
                    PointerColorTarget.Ripple -> onRippleColorChange(color)
                    null -> Unit
                }
                colorTarget = null
            },
        )
    }

    if (pickingDesign) {
        PointerDesignPickerDialog(
            settings = settings,
            selected = selectedDesign,
            onDismiss = { pickingDesign = false },
            onSelect = { design ->
                onPointerDesignChange(design)
                pickingDesign = false
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.floating_pointer_pointer_settings_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.floating_pointer_preview_section))
        Surface(
            modifier = Modifier.padding(bottom = 4.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            FloatingPointerDesignPreview(settings = previewSettings)
        }

        SettingsSectionTitle(stringResource(R.string.floating_pointer_design_section))
        SettingsCard {
            SettingLinkRow(
                title = stringResource(R.string.floating_pointer_design_section),
                subtitle = stringResource(selectedDesign.labelResId),
                onClick = { pickingDesign = true },
            )
        }

        SettingsSectionTitle(stringResource(R.string.floating_pointer_settings_section_pointer))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_pointer_size),
                value = settings.floatingPointerPointerDiameterPx,
                valueRange = 48f..120f,
                steps = 17,
                enabled = true,
                label = stringResource(
                    R.string.floating_pointer_size_px_value,
                    settings.floatingPointerPointerDiameterPx.roundToInt(),
                ),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = { pointerSizeDragging = true },
                onLayoutPreviewStop = { pointerSizeDragging = false },
                onLayoutPreviewValueChange = { previewPointerDiameterPx = it },
                onValueChange = onPointerDiameterChange,
            )
            if (selectedDesign.isRing) {
                SettingsSliderRow(
                    title = stringResource(R.string.floating_pointer_ring_thickness),
                    value = settings.floatingPointerRingThicknessPx,
                    valueRange = 4f..24f,
                    steps = 19,
                    enabled = true,
                    label = pxDpLabel(settings.floatingPointerRingThicknessPx),
                    formatLabel = formatPxDpLabel,
                    triggersLayoutPreview = true,
                    onLayoutPreviewStart = { pointerSizeDragging = true },
                    onLayoutPreviewStop = { pointerSizeDragging = false },
                    onLayoutPreviewValueChange = { previewRingThicknessPx = it },
                    onValueChange = onRingThicknessChange,
                )
                SettingsSliderRow(
                    title = stringResource(R.string.floating_pointer_dot_diameter),
                    value = settings.floatingPointerDotDiameterPx,
                    valueRange = 2f..24f,
                    steps = 21,
                    enabled = true,
                    label = pxDpLabel(settings.floatingPointerDotDiameterPx),
                    formatLabel = formatPxDpLabel,
                    triggersLayoutPreview = true,
                    onLayoutPreviewStart = { pointerSizeDragging = true },
                    onLayoutPreviewStop = { pointerSizeDragging = false },
                    onLayoutPreviewValueChange = { previewDotDiameterPx = it },
                    onValueChange = onDotDiameterChange,
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.floating_pointer_ring_color),
                    color = settings.floatingPointerRingColorArgb,
                    enabled = true,
                    onClick = {
                        pickerInitialColor = settings.floatingPointerRingColorArgb
                        colorTarget = PointerColorTarget.Ring
                    },
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.floating_pointer_fill_color),
                    subtitle = stringResource(R.string.floating_pointer_fill_color_desc),
                    color = settings.floatingPointerFillColorArgb,
                    enabled = true,
                    onClick = {
                        pickerInitialColor = settings.floatingPointerFillColorArgb
                        colorTarget = PointerColorTarget.Fill
                    },
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.floating_pointer_dot_color),
                    color = settings.floatingPointerDotColorArgb,
                    enabled = true,
                    onClick = {
                        pickerInitialColor = settings.floatingPointerDotColorArgb
                        colorTarget = PointerColorTarget.Dot
                    },
                )
            }
        }

        SettingsSectionTitle(stringResource(R.string.floating_pointer_visual_feedback_section))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_click_haptic),
                subtitle = stringResource(R.string.floating_pointer_click_haptic_desc),
                checked = settings.floatingPointerClickHapticEnabled,
                enabled = true,
                onCheckedChange = onClickHapticChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_click_visual_feedback),
                subtitle = stringResource(R.string.floating_pointer_click_visual_feedback_desc),
                checked = settings.floatingPointerClickVisualFeedbackEnabled,
                enabled = true,
                onCheckedChange = onClickVisualFeedbackChange,
            )
            if (settings.floatingPointerClickVisualFeedbackEnabled) {
                SettingsSliderRow(
                    title = stringResource(R.string.floating_pointer_ripple_size),
                    value = settings.floatingPointerRippleSizeDp,
                    valueRange = 40f..200f,
                    steps = 15,
                    enabled = true,
                    label = stringResource(
                        R.string.floating_pointer_ripple_size_value,
                        settings.floatingPointerRippleSizeDp,
                    ),
                    onValueChange = onRippleSizeChange,
                )
                SettingsSliderRow(
                    title = stringResource(R.string.floating_pointer_ripple_duration),
                    value = settings.floatingPointerRippleDurationMs.toFloat(),
                    valueRange = 100f..1500f,
                    steps = 55,
                    enabled = true,
                    label = stringResource(
                        R.string.floating_pointer_ripple_duration_value,
                        settings.floatingPointerRippleDurationMs,
                    ),
                    onValueChange = { onRippleDurationChange(it.roundToInt()) },
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.floating_pointer_ripple_color),
                    subtitle = stringResource(R.string.floating_pointer_ripple_color_desc),
                    color = settings.floatingPointerRippleColorArgb,
                    enabled = true,
                    onClick = {
                        pickerInitialColor = settings.floatingPointerRippleColorArgb
                        colorTarget = PointerColorTarget.Ripple
                    },
                )
            }
        }

        SettingsSectionTitle(stringResource(R.string.floating_pointer_trail_section))
        SettingsCard {
            val trailType = FloatingPointerTrailType.fromId(settings.floatingPointerTrailTypeId)
            SettingRadioRow(
                title = stringResource(R.string.floating_pointer_trail_off),
                selected = trailType == FloatingPointerTrailType.OFF,
                onClick = { onTrailTypeChange(FloatingPointerTrailType.OFF) },
            )
            SettingRadioRow(
                title = stringResource(R.string.floating_pointer_trail_simple),
                selected = trailType == FloatingPointerTrailType.SIMPLE,
                onClick = { onTrailTypeChange(FloatingPointerTrailType.SIMPLE) },
            )
            SettingRadioRow(
                title = stringResource(R.string.floating_pointer_trail_high_detail),
                selected = trailType == FloatingPointerTrailType.HIGH_DETAIL,
                onClick = { onTrailTypeChange(FloatingPointerTrailType.HIGH_DETAIL) },
            )
            if (trailType != FloatingPointerTrailType.OFF) {
                SettingsSliderRow(
                    title = stringResource(R.string.floating_pointer_trail_duration),
                    value = settings.floatingPointerTrailDurationMs.toFloat(),
                    valueRange = 50f..500f,
                    steps = 8,
                    enabled = true,
                    label = stringResource(
                        R.string.floating_pointer_trail_duration_value,
                        settings.floatingPointerTrailDurationMs,
                    ),
                    onValueChange = { onTrailDurationChange(it.roundToInt()) },
                )
                AnimationStyleColorRow(
                    title = stringResource(R.string.floating_pointer_trail_color),
                    color = settings.floatingPointerTrailColorArgb,
                    enabled = true,
                    onClick = {
                        pickerInitialColor = settings.floatingPointerTrailColorArgb
                        colorTarget = PointerColorTarget.Trail
                    },
                )
            }
        }

        SettingsSectionTitle(stringResource(R.string.floating_pointer_settings_section_other))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_hide_on_release),
                subtitle = stringResource(R.string.floating_pointer_hide_on_release_desc),
                checked = settings.floatingPointerHideWhenJoystickReleased,
                enabled = true,
                onCheckedChange = onHideWhenReleasedChange,
            )
        }

        SettingLinkRow(
            title = stringResource(R.string.floating_pointer_reset_visual),
            onClick = onResetVisualDefaults,
        )
    }
}
