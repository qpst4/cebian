package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionFractions
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import java.util.Locale
import kotlin.math.roundToInt

private val floatBallOpacityRange = 0f..1f
private val floatBallLineHeightRange = 0.04f..0.4f
private val floatBallLineWidthRange = 0.01f..0.50f
private val floatBallVisibleFractionRange =
    FloatBallPositionFractions.MIN_VISIBLE..FloatBallPositionFractions.MAX_VISIBLE

private fun fractionPercentSnap(
    range: ClosedFloatingPointRange<Float>,
): (Float) -> Float = { value ->
    (value.coerceIn(range.start, range.endInclusive) * 1000f).roundToInt() / 1000f
}

private fun fractionPercentLabel(value: Float): String = "${(value * 100f).roundToInt()}%"

private fun lineHeightPercentLabel(value: Float): String =
    String.format(Locale.US, "%.1f%%", value * 100f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallAppearanceSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onPickCrossArmChange: (Float) -> Unit,
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
    onPositionYPreviewStart: () -> Unit = {},
    onPositionYPreviewChange: (Float) -> Unit = {},
    onPositionYPreviewStop: (restoreIfNeeded: Boolean) -> Unit = {},
    onPreviewAppearance: (
        sizeDp: Float?,
        opacity: Float?,
        visibleFraction: Float?,
        lineHeightFraction: Float?,
        lineWidthFraction: Float?,
        lineOpacity: Float?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onAppearancePreviewCommit: () -> Unit = {},
    onAppearancePreviewRestore: () -> Unit = {},
) {
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted

    DisposableEffect(Unit) {
        onDispose {
            onPositionYPreviewStop(true)
            onStripZonePreviewStop()
            onAppearancePreviewRestore()
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_appearance_settings_title),
        onBack = onBack,
    ) {
        MiuixSmallTitle(stringResource(R.string.float_ball_section_appearance), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
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
                triggersLayoutPreview = true,
                onLayoutPreviewValueChange = { value ->
                    onPreviewAppearance(value, null, null, null, null, null)
                },
                onValueChange = { value ->
                    onAppearancePreviewCommit()
                    onSizeChange(value)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pick_cross_arm),
                value = settings.floatBallPickCrossArmDp,
                valueRange = 4f..16f,
                steps = 23,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.float_ball_pick_cross_arm_value,
                    settings.floatBallPickCrossArmDp,
                ),
                onValueChange = onPickCrossArmChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_opacity),
                value = settings.floatBallOpacity,
                valueRange = floatBallOpacityRange,
                enabled = controlsEnabled,
                label = fractionPercentLabel(settings.floatBallOpacity),
                formatLabel = ::fractionPercentLabel,
                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                triggersLayoutPreview = true,
                onLayoutPreviewValueChange = { value ->
                    onPreviewAppearance(null, value, null, null, null, null)
                },
                onValueChange = { value ->
                    onAppearancePreviewCommit()
                    onOpacityChange(value)
                },
            )
        }

        MiuixSmallTitle(stringResource(R.string.float_ball_position), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsRadioGroup {
            FloatBallPositionMode.selectable.forEach { mode ->
                SettingRadioRow(
                    title = floatBallPositionModeLabel(mode),
                    selected = settings.floatBallPositionMode == mode,
                    enabled = controlsEnabled,
                    segmentKey = mode,
                    onClick = { onPositionModeChange(mode) },
                )
            }
        }
        SettingsHintText(stringResource(R.string.float_ball_position_xy_hint))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_visible_fraction),
                value = settings.floatBallVisibleFraction,
                valueRange = floatBallVisibleFractionRange,
                enabled = controlsEnabled,
                label = fractionPercentLabel(settings.floatBallVisibleFraction),
                formatLabel = ::fractionPercentLabel,
                snapValue = fractionPercentSnap(floatBallVisibleFractionRange),
                triggersLayoutPreview = true,
                onLayoutPreviewValueChange = { value ->
                    onPreviewAppearance(null, null, value, null, null, null)
                },
                onValueChange = { value ->
                    onAppearancePreviewCommit()
                    onVisibleFractionChange(value)
                },
            )
            SettingsHintText(stringResource(R.string.float_ball_position_y_preview_hint))
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_position_y),
                value = settings.floatBallPositionYFraction,
                valueRange = FloatBallPositionFractions.MIN_Y..FloatBallPositionFractions.MAX_Y,
                enabled = controlsEnabled,
                label = "",
                formatLabel = { "${(it * 100).roundToInt()}%" },
                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onPositionYPreviewStart,
                onLayoutPreviewStop = { onPositionYPreviewStop(true) },
                onLayoutPreviewValueChange = onPositionYPreviewChange,
                onValueChange = { fraction ->
                    onPositionYPreviewStop(false)
                    onPositionYChange(fraction)
                },
            )
        }

        MiuixSmallTitle(stringResource(R.string.float_ball_section_style), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            SettingNavigationRow(
                icon = { label ->
                    Icon(
                        Icons.Outlined.Palette,
                        contentDescription = label,
                        tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                title = stringResource(R.string.float_ball_style_picker_title),
                subtitle = floatBallStyleLabel(settings.floatBallStyleType),
                enabled = controlsEnabled,
                onClick = onOpenStyleSettings,
            )
        }

        MiuixSmallTitle(stringResource(R.string.float_ball_section_line), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsHintText(stringResource(R.string.float_ball_line_width_preview_hint))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_line_height),
                value = settings.floatBallLineHeightFraction,
                valueRange = floatBallLineHeightRange,
                enabled = controlsEnabled,
                label = lineHeightPercentLabel(settings.floatBallLineHeightFraction),
                formatLabel = ::lineHeightPercentLabel,
                snapValue = { value ->
                    (value.coerceIn(floatBallLineHeightRange.start, floatBallLineHeightRange.endInclusive) * 1000f).roundToInt() / 1000f
                },
                triggersLayoutPreview = true,
                onLayoutPreviewValueChange = { value ->
                    onPreviewAppearance(null, null, null, value, null, null)
                },
                onValueChange = { value ->
                    onAppearancePreviewCommit()
                    onLineHeightChange(value)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_line_width),
                value = settings.floatBallLineWidthFraction,
                valueRange = floatBallLineWidthRange,
                enabled = controlsEnabled,
                label = fractionPercentLabel(settings.floatBallLineWidthFraction),
                formatLabel = ::fractionPercentLabel,
                snapValue = fractionPercentSnap(floatBallLineWidthRange),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onStripZonePreviewStart,
                onLayoutPreviewStop = onStripZonePreviewStop,
                onLayoutPreviewValueChange = { value ->
                    onPreviewAppearance(null, null, null, null, value, null)
                },
                onValueChange = { value ->
                    onAppearancePreviewCommit()
                    onLineWidthChange(value)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_line_opacity),
                value = settings.floatBallLineOpacity,
                valueRange = floatBallOpacityRange,
                enabled = controlsEnabled,
                label = fractionPercentLabel(settings.floatBallLineOpacity),
                formatLabel = ::fractionPercentLabel,
                snapValue = fractionPercentSnap(floatBallOpacityRange),
                triggersLayoutPreview = true,
                onLayoutPreviewValueChange = { value ->
                    onPreviewAppearance(null, null, null, null, null, value)
                },
                onValueChange = { value ->
                    onAppearancePreviewCommit()
                    onLineOpacityChange(value)
                },
            )
        }
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
