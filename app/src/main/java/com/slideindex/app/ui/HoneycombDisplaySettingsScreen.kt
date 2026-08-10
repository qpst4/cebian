package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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
    // 下拉切换背景时立刻驱动条件滑条重组，避免等 DataStore 回流才出现「模糊强度」。
    var localDisplay by remember { mutableStateOf(display) }
    LaunchedEffect(display) { localDisplay = display }
    fun updateDisplay(next: HoneycombDisplaySettings) {
        localDisplay = next
        onDisplayChange(next)
    }

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
                        selected = localDisplay.mode == HoneycombDisplaySettings.MODE_HOLD,
                        onClick = {
                            updateDisplay(localDisplay.copy(mode = HoneycombDisplaySettings.MODE_HOLD))
                        },
                    )
                    SettingRadioRow(
                        title = stringResource(R.string.honeycomb_mode_browse),
                        subtitle = stringResource(R.string.honeycomb_mode_browse_desc),
                        selected = localDisplay.mode == HoneycombDisplaySettings.MODE_BROWSE,
                        onClick = {
                            updateDisplay(localDisplay.copy(mode = HoneycombDisplaySettings.MODE_BROWSE))
                        },
                    )
                }
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                    title = stringResource(R.string.honeycomb_empty_tap_close),
                    subtitle = stringResource(R.string.honeycomb_empty_tap_close_desc),
                    checked = localDisplay.emptyTapClose,
                    onCheckedChange = { updateDisplay(localDisplay.copy(emptyTapClose = it)) },
                )
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.Tune, contentDescription = label) },
                    title = stringResource(R.string.honeycomb_show_selected_name),
                    subtitle = stringResource(R.string.honeycomb_show_selected_name_desc),
                    checked = localDisplay.showSelectedName,
                    onCheckedChange = { updateDisplay(localDisplay.copy(showSelectedName = it)) },
                )
            }

            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_position), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                    title = stringResource(R.string.honeycomb_follow_finger),
                    subtitle = stringResource(R.string.honeycomb_follow_finger_desc),
                    checked = localDisplay.followFinger,
                    onCheckedChange = { updateDisplay(localDisplay.copy(followFinger = it)) },
                )
                if (!localDisplay.followFinger) {
                    SettingsSliderRow(
                        title = stringResource(R.string.honeycomb_fixed_x),
                        value = localDisplay.fixedXPercent.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = true,
                        label = stringResource(R.string.floating_pointer_percent_value, localDisplay.fixedXPercent),
                        onValueChange = {
                            updateDisplay(localDisplay.copy(fixedXPercent = it.roundToInt()))
                        },
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.honeycomb_fixed_y),
                        value = localDisplay.fixedYPercent.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = true,
                        label = stringResource(R.string.floating_pointer_percent_value, localDisplay.fixedYPercent),
                        onValueChange = {
                            updateDisplay(localDisplay.copy(fixedYPercent = it.roundToInt()))
                        },
                    )
                }
            }

            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_layout), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard {
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_icon_size),
                    value = localDisplay.iconSizeDp.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_ICON_SIZE_DP.toFloat()..
                        HoneycombDisplaySettings.MAX_ICON_SIZE_DP.toFloat(),
                    steps = 16,
                    enabled = true,
                    label = stringResource(R.string.corner_gesture_zone_dp_value, localDisplay.iconSizeDp),
                    onValueChange = { updateDisplay(localDisplay.copy(iconSizeDp = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_spacing),
                    value = localDisplay.spacingDp.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_SPACING_DP.toFloat()..
                        HoneycombDisplaySettings.MAX_SPACING_DP.toFloat(),
                    steps = 24,
                    enabled = true,
                    label = stringResource(R.string.corner_gesture_zone_dp_value, localDisplay.spacingDp),
                    onValueChange = { updateDisplay(localDisplay.copy(spacingDp = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_disc_size),
                    value = localDisplay.discSizePercent.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_DISC_SIZE_PERCENT.toFloat()..
                        HoneycombDisplaySettings.MAX_DISC_SIZE_PERCENT.toFloat(),
                    steps = 10,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, localDisplay.discSizePercent),
                    onValueChange = { updateDisplay(localDisplay.copy(discSizePercent = it.roundToInt())) },
                )
            }

            MiuixSmallTitle(stringResource(R.string.honeycomb_display_section_background), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            SettingsCard(keyPrefix = "honeycomb-background") {
                val backgroundStyles = listOf(
                    HoneycombDisplaySettings.BACKGROUND_BLUR,
                    HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR,
                    HoneycombDisplaySettings.BACKGROUND_BLACK,
                )
                val blurEnabled = localDisplay.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_BLUR
                    || localDisplay.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR
                SettingDropdownRow(
                    title = stringResource(R.string.honeycomb_display_section_background),
                    items = listOf(
                        stringResource(R.string.honeycomb_background_blur),
                        stringResource(R.string.honeycomb_background_wallpaper_blur),
                        stringResource(R.string.honeycomb_background_black),
                    ),
                    selectedIndex = backgroundStyles.indexOf(localDisplay.backgroundStyle).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        updateDisplay(localDisplay.copy(backgroundStyle = backgroundStyles[index]))
                    },
                )
                // 始终注册行，避免 Lazy 条件增减导致切背景后滑条不出现。
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_blur_strength),
                    value = localDisplay.blurDp.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_BLUR_DP.toFloat()..
                        HoneycombDisplaySettings.MAX_BLUR_DP.toFloat(),
                    steps = 16,
                    enabled = blurEnabled,
                    label = stringResource(R.string.corner_gesture_zone_dp_value, localDisplay.blurDp),
                    onValueChange = { updateDisplay(localDisplay.copy(blurDp = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_dim_percent),
                    value = localDisplay.dimPercent.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_DIM_PERCENT.toFloat()..
                        HoneycombDisplaySettings.MAX_DIM_PERCENT.toFloat(),
                    steps = 12,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, localDisplay.dimPercent),
                    onValueChange = { updateDisplay(localDisplay.copy(dimPercent = it.roundToInt())) },
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
                    value = localDisplay.animationSpeed.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_ANIMATION_SPEED.toFloat()..
                        HoneycombDisplaySettings.MAX_ANIMATION_SPEED.toFloat(),
                    steps = 4,
                    enabled = true,
                    label = speedLabels[localDisplay.animationSpeed.coerceIn(0, speedLabels.lastIndex)],
                    onValueChange = { updateDisplay(localDisplay.copy(animationSpeed = it.roundToInt())) },
                )
                val inertiaLabels = listOf(
                    stringResource(R.string.honeycomb_inertia_low),
                    stringResource(R.string.honeycomb_inertia_medium),
                    stringResource(R.string.honeycomb_inertia_high),
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_inertia),
                    value = localDisplay.inertia.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_INERTIA.toFloat()..
                        HoneycombDisplaySettings.MAX_INERTIA.toFloat(),
                    steps = 2,
                    enabled = true,
                    label = inertiaLabels[localDisplay.inertia.coerceIn(0, inertiaLabels.lastIndex)],
                    onValueChange = { updateDisplay(localDisplay.copy(inertia = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_center_scale),
                    value = localDisplay.centerScale.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_CENTER_SCALE.toFloat()..
                        HoneycombDisplaySettings.MAX_CENTER_SCALE.toFloat(),
                    steps = 11,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, localDisplay.centerScale),
                    onValueChange = { updateDisplay(localDisplay.copy(centerScale = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_edge_scale),
                    value = localDisplay.edgeScale.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_EDGE_SCALE.toFloat()..
                        HoneycombDisplaySettings.MAX_EDGE_SCALE.toFloat(),
                    steps = 10,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, localDisplay.edgeScale),
                    onValueChange = { updateDisplay(localDisplay.copy(edgeScale = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.honeycomb_selection_scale),
                    value = localDisplay.selectionScale.toFloat(),
                    valueRange = HoneycombDisplaySettings.MIN_SELECTION_SCALE.toFloat()..
                        HoneycombDisplaySettings.MAX_SELECTION_SCALE.toFloat(),
                    steps = 11,
                    enabled = true,
                    label = stringResource(R.string.floating_pointer_percent_value, localDisplay.selectionScale),
                    onValueChange = { updateDisplay(localDisplay.copy(selectionScale = it.roundToInt())) },
                )
            }
    }
}
