package com.slideindex.app.ui

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
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
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

    val appearanceSectionTitle = stringResource(R.string.float_ball_section_appearance)
    val positionSectionTitle = stringResource(R.string.float_ball_position)
    val positionXyHint = stringResource(R.string.float_ball_position_xy_hint)
    val lineSectionTitle = stringResource(R.string.float_ball_section_line)
    val lineWidthPreviewHint = stringResource(R.string.float_ball_line_width_preview_hint)

    val positionMode = settings.floatBallPositionMode
    val positionModeEntries = FloatBallPositionMode.selectable
    val positionModeIndex = positionModeEntries.indexOf(positionMode).coerceAtLeast(0)
    val showEdgeLineSettings = positionMode == FloatBallPositionMode.BOTH_EDGES

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_appearance_settings_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "appearance_section",
            title = appearanceSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "fb-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("style-picker") {
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
                    },
                )
                add(
                    settingsCardScopeItem("size") {
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
                    },
                )
                add(
                    settingsCardScopeItem("opacity") {
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
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "position_section",
            title = positionSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(key = "position_xy_hint", text = positionXyHint)
        groupedCardItems(
            keyPrefix = "fb-position",
            items = buildList {
                add(
                    settingsCardScopeItem("position-mode") {
                        SettingDropdownRow(
                            title = stringResource(R.string.float_ball_position),
                            subtitle = floatBallPositionModeLabel(positionMode),
                            items = positionModeEntries.map { floatBallPositionModeLabel(it) },
                            selectedIndex = positionModeIndex,
                            enabled = controlsEnabled,
                            onSelectedIndexChange = { onPositionModeChange(positionModeEntries[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("visible-fraction") {
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
                    },
                )
                add(
                    settingsCardScopeItem("position-y") {
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
                    },
                )
            },
        )

        if (showEdgeLineSettings) {
            settingsLazySmallTitle(
                key = "line_section",
                title = lineSectionTitle,
                sectionTop = true,
            )
            settingsLazyHint(key = "line_width_preview_hint", text = lineWidthPreviewHint)
            groupedCardItems(
                keyPrefix = "fb-line",
                items = buildList {
                    add(
                        settingsCardScopeItem("line-height") {
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
                        },
                    )
                    add(
                        settingsCardScopeItem("line-width") {
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
                        },
                    )
                    add(
                        settingsCardScopeItem("line-opacity") {
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
                        },
                    )
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
