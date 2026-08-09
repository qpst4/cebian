package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.HoneycombDisplaySettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombDisplaySettingsScreen(
    display: HoneycombDisplaySettings,
    onBack: () -> Unit,
    onDisplayChange: (HoneycombDisplaySettings) -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.honeycomb_display_settings_title),
        onBack = onBack,
    ) {
            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_mode), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                SettingsRadioGroup {
                    SettingRadioRow(
                        title = stringResource(R.string.honeycomb_mode_hold),
                        subtitle = stringResource(R.string.honeycomb_mode_hold_desc),
                        selected = display.mode == HoneycombDisplaySettings.MODE_HOLD,
                        onClick = {
                            onDisplayChange(display.copy(mode = HoneycombDisplaySettings.MODE_HOLD))
                        },
                    )
                    SettingRadioRow(
                        title = stringResource(R.string.honeycomb_mode_browse),
                        subtitle = stringResource(R.string.honeycomb_mode_browse_desc),
                        selected = display.mode == HoneycombDisplaySettings.MODE_BROWSE,
                        onClick = {
                            onDisplayChange(display.copy(mode = HoneycombDisplaySettings.MODE_BROWSE))
                        },
                    )
                }
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                    title = stringResource(R.string.honeycomb_empty_tap_close),
                    subtitle = stringResource(R.string.honeycomb_empty_tap_close_desc),
                    checked = display.emptyTapClose,
                    onCheckedChange = { onDisplayChange(display.copy(emptyTapClose = it)) },
                )
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.Tune, contentDescription = label) },
                    title = stringResource(R.string.honeycomb_show_selected_name),
                    subtitle = stringResource(R.string.honeycomb_show_selected_name_desc),
                    checked = display.showSelectedName,
                    onCheckedChange = { onDisplayChange(display.copy(showSelectedName = it)) },
                )
            }

            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_position), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                    title = stringResource(R.string.honeycomb_follow_finger),
                    subtitle = stringResource(R.string.honeycomb_follow_finger_desc),
                    checked = display.followFinger,
                    onCheckedChange = { onDisplayChange(display.copy(followFinger = it)) },
                )
                if (!display.followFinger) {
                    SettingsSliderRow(
                        title = stringResource(R.string.honeycomb_fixed_x),
                        value = display.fixedXPercent.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = true,
                        label = stringResource(R.string.floating_pointer_percent_value, display.fixedXPercent),
                        onValueChange = {
                            onDisplayChange(display.copy(fixedXPercent = it.roundToInt()))
                        },
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.honeycomb_fixed_y),
                        value = display.fixedYPercent.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = true,
                        label = stringResource(R.string.floating_pointer_percent_value, display.fixedYPercent),
                        onValueChange = {
                            onDisplayChange(display.copy(fixedYPercent = it.roundToInt()))
                        },
                    )
                }
            }

            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_layout), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_icon_size),
                    value = display.iconSizeDp.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_ICON_SIZE_DP.toFloat()..
                        HoneycombDisplaySettings.MAX_ICON_SIZE_DP.toFloat(),
                    steps = 16,
                    enabled = true,
                    label = stringResource(R.string.corner_gesture_zone_dp_value, display.iconSizeDp),
                    onValueChange = { onDisplayChange(display.copy(iconSizeDp = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_spacing),
                    value = display.spacingDp.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_SPACING_DP.toFloat()..
                        HoneycombDisplaySettings.MAX_SPACING_DP.toFloat(),
                    steps = 24,
                    enabled = true,
                    label = stringResource(R.string.corner_gesture_zone_dp_value, display.spacingDp),
                    onValueChange = { onDisplayChange(display.copy(spacingDp = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_disc_size),
                    value = display.discSizePercent.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_DISC_SIZE_PERCENT.toFloat()..
                        HoneycombDisplaySettings.MAX_DISC_SIZE_PERCENT.toFloat(),
                    steps = 10,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, display.discSizePercent),
                    onValueChange = { onDisplayChange(display.copy(discSizePercent = it.roundToInt())) },
                )
            }

            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_background), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                SettingsRadioGroup {
                    SettingRadioRow(
                        title = stringResource(R.string.honeycomb_background_blur),
                        selected = display.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_BLUR,
                        onClick = {
                            onDisplayChange(
                                display.copy(backgroundStyle = HoneycombDisplaySettings.BACKGROUND_BLUR),
                            )
                        },
                    )
                    SettingRadioRow(
                        title = stringResource(R.string.honeycomb_background_wallpaper_blur),
                        subtitle = stringResource(R.string.honeycomb_background_wallpaper_blur_desc),
                        selected = display.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR,
                        onClick = {
                            onDisplayChange(
                                display.copy(
                                    backgroundStyle = HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR,
                                ),
                            )
                        },
                    )
                    SettingRadioRow(
                        title = stringResource(R.string.honeycomb_background_black),
                        selected = display.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_BLACK,
                        onClick = {
                            onDisplayChange(
                                display.copy(backgroundStyle = HoneycombDisplaySettings.BACKGROUND_BLACK),
                            )
                        },
                    )
                }
                if (display.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_BLUR
                    || display.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR
                ) {
                    SettingsSliderRow(
                        title = stringResource(R.string.honeycomb_blur_strength),
                        value = display.blurDp.toFloat(),
                        valueRange = HoneycombDisplaySettings.MIN_BLUR_DP.toFloat()..
                            HoneycombDisplaySettings.MAX_BLUR_DP.toFloat(),
                        steps = 16,
                        enabled = true,
                        label = stringResource(R.string.corner_gesture_zone_dp_value, display.blurDp),
                        onValueChange = { onDisplayChange(display.copy(blurDp = it.roundToInt())) },
                    )
                }
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_dim_percent),
                    value = display.dimPercent.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_DIM_PERCENT.toFloat()..
                        HoneycombDisplaySettings.MAX_DIM_PERCENT.toFloat(),
                    steps = 12,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, display.dimPercent),
                    onValueChange = { onDisplayChange(display.copy(dimPercent = it.roundToInt())) },
                )
            }

            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_animation), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                val speedLabels = listOf(
                    stringResource(R.string.honeycomb_speed_very_fast),
                    stringResource(R.string.honeycomb_speed_fast),
                    stringResource(R.string.honeycomb_speed_normal),
                    stringResource(R.string.honeycomb_speed_soft),
                    stringResource(R.string.honeycomb_speed_slow),
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_animation_speed),
                    value = display.animationSpeed.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_ANIMATION_SPEED.toFloat()..
                        HoneycombDisplaySettings.MAX_ANIMATION_SPEED.toFloat(),
                    steps = 4,
                    enabled = true,
                    label = speedLabels[display.animationSpeed.coerceIn(0, speedLabels.lastIndex)],
                    onValueChange = { onDisplayChange(display.copy(animationSpeed = it.roundToInt())) },
                )
                val inertiaLabels = listOf(
                    stringResource(R.string.honeycomb_inertia_low),
                    stringResource(R.string.honeycomb_inertia_medium),
                    stringResource(R.string.honeycomb_inertia_high),
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_inertia),
                    value = display.inertia.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_INERTIA.toFloat()..
                        HoneycombDisplaySettings.MAX_INERTIA.toFloat(),
                    steps = 2,
                    enabled = true,
                    label = inertiaLabels[display.inertia.coerceIn(0, inertiaLabels.lastIndex)],
                    onValueChange = { onDisplayChange(display.copy(inertia = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_center_scale),
                    value = display.centerScale.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_CENTER_SCALE.toFloat()..
                        HoneycombDisplaySettings.MAX_CENTER_SCALE.toFloat(),
                    steps = 11,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, display.centerScale),
                    onValueChange = { onDisplayChange(display.copy(centerScale = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_edge_scale),
                    value = display.edgeScale.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_EDGE_SCALE.toFloat()..
                        HoneycombDisplaySettings.MAX_EDGE_SCALE.toFloat(),
                    steps = 10,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, display.edgeScale),
                    onValueChange = { onDisplayChange(display.copy(edgeScale = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_selection_scale),
                    value = display.selectionScale.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_SELECTION_SCALE.toFloat()..
                        HoneycombDisplaySettings.MAX_SELECTION_SCALE.toFloat(),
                    steps = 11,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, display.selectionScale),
                    onValueChange = { onDisplayChange(display.copy(selectionScale = it.roundToInt())) },
                )
            }
    }
}
