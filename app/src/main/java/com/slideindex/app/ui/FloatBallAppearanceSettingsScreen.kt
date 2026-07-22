package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionFractions
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallAppearanceSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onPositionModeChange: (FloatBallPositionMode) -> Unit,
    onVisibleFractionChange: (Float) -> Unit,
    onPositionYChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onLineWidthChange: (Float) -> Unit,
    onLineOpacityChange: (Float) -> Unit,
    onOpenStyleSettings: () -> Unit,
    onStripZonePreviewStart: () -> Unit = {},
    onStripZonePreviewStop: () -> Unit = {},
) {
    var showPositionDialog by remember { mutableStateOf(false) }
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted

    DisposableEffect(Unit) {
        onDispose { onStripZonePreviewStop() }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_appearance_settings_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.float_ball_section_appearance))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_size),
                value = settings.floatBallSizeDp,
                valueRange = 36f..72f,
                steps = 8,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.float_ball_size_value,
                    settings.floatBallSizeDp,
                ),
                onValueChange = onSizeChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_opacity),
                value = settings.floatBallOpacity,
                valueRange = 0.3f..1f,
                steps = 6,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallOpacity * 100).roundToInt(),
                ),
                onValueChange = onOpacityChange,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Place, contentDescription = label) },
                title = stringResource(R.string.float_ball_position),
                subtitle = floatBallPositionModeLabel(settings.floatBallPositionMode),
                enabled = controlsEnabled,
                onClick = { showPositionDialog = true },
            )
            SettingsHintText(stringResource(R.string.float_ball_position_xy_hint))
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_visible_fraction),
                value = settings.floatBallVisibleFraction,
                valueRange = FloatBallPositionFractions.MIN_VISIBLE..FloatBallPositionFractions.MAX_VISIBLE,
                steps = 9,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallVisibleFraction * 100).roundToInt(),
                ),
                onValueChange = onVisibleFractionChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_position_y),
                value = settings.floatBallPositionYFraction,
                valueRange = FloatBallPositionFractions.MIN_Y..FloatBallPositionFractions.MAX_Y,
                enabled = controlsEnabled,
                snapValue = { value ->
                    value.coerceIn(
                        FloatBallPositionFractions.MIN_Y,
                        FloatBallPositionFractions.MAX_Y,
                    )
                },
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallPositionYFraction * 100).roundToInt(),
                ),
                onValueChange = onPositionYChange,
            )
        }

        SettingsSectionTitle(stringResource(R.string.float_ball_section_style))
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Palette, contentDescription = label) },
                title = stringResource(R.string.float_ball_style_picker_title),
                subtitle = floatBallStyleLabel(settings.floatBallStyleType),
                enabled = controlsEnabled,
                onClick = onOpenStyleSettings,
            )
        }

        SettingsSectionTitle(stringResource(R.string.float_ball_section_line))
        SettingsHintText(stringResource(R.string.float_ball_line_width_preview_hint))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_line_height),
                value = settings.floatBallLineHeightFraction,
                valueRange = 0.04f..0.4f,
                steps = 8,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallLineHeightFraction * 100).roundToInt(),
                ),
                onValueChange = onLineHeightChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_line_width),
                value = settings.floatBallLineWidthFraction,
                valueRange = 0.04f..0.5f,
                steps = 9,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallLineWidthFraction * 100).roundToInt(),
                ),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onStripZonePreviewStart,
                onLayoutPreviewStop = onStripZonePreviewStop,
                onValueChange = onLineWidthChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_line_opacity),
                value = settings.floatBallLineOpacity,
                valueRange = 0.1f..1f,
                steps = 8,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallLineOpacity * 100).roundToInt(),
                ),
                onValueChange = onLineOpacityChange,
            )
        }
    }

    if (showPositionDialog) {
        FloatBallPositionModeDialog(
            selected = settings.floatBallPositionMode,
            onDismiss = { showPositionDialog = false },
            onSelect = {
                onPositionModeChange(it)
                showPositionDialog = false
            },
        )
    }
}

@Composable
internal fun floatBallPositionModeLabel(mode: FloatBallPositionMode): String =
    when (mode) {
        FloatBallPositionMode.LEFT -> stringResource(R.string.float_ball_position_left)
        FloatBallPositionMode.RIGHT -> stringResource(R.string.float_ball_position_right)
        FloatBallPositionMode.BOTH_EDGES -> stringResource(R.string.float_ball_position_both_edges)
        FloatBallPositionMode.CUSTOM -> stringResource(R.string.float_ball_position_right)
    }

@Composable
internal fun FloatBallPositionModeDialog(
    selected: FloatBallPositionMode,
    onDismiss: () -> Unit,
    onSelect: (FloatBallPositionMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.float_ball_position)) },
        text = {
            Column {
                FloatBallPositionMode.selectable.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == selected,
                            onClick = { onSelect(mode) },
                        )
                        Text(
                            text = floatBallPositionModeLabel(mode),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
