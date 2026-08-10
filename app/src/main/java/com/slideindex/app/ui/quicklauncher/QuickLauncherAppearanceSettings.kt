package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.QuickLauncherDisplaySettings
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsRadioGroup
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.settings.components.SettingsCardScope
import kotlin.math.roundToInt

@Composable
fun SettingsCardScope.QuickLauncherAppearanceSettings(
    display: QuickLauncherDisplaySettings,
    enabled: Boolean,
    onDisplayChange: (QuickLauncherDisplaySettings) -> Unit,
) {
    SettingsSliderRow(
        title = stringResource(R.string.quick_launcher_icon_size),
        value = display.iconSizeDp.toFloat(),
        valueRange = QuickLauncherDisplaySettings.MIN_ICON_SIZE_DP.toFloat()..
            QuickLauncherDisplaySettings.MAX_ICON_SIZE_DP.toFloat(),
        steps = QuickLauncherDisplaySettings.MAX_ICON_SIZE_DP -
            QuickLauncherDisplaySettings.MIN_ICON_SIZE_DP - 1,
        enabled = enabled,
        label = stringResource(
            R.string.quick_launcher_icon_size_value,
            display.iconSizeDp,
        ),
        formatLabel = { "${it.roundToInt()} dp" },
        onValueChange = { value ->
            onDisplayChange(display.copy(iconSizeDp = value.roundToInt()))
        },
    )
    SettingsSliderRow(
        title = stringResource(R.string.quick_launcher_background_opacity),
        value = display.backgroundOpacityPercent.toFloat(),
        valueRange = QuickLauncherDisplaySettings.MIN_BACKGROUND_OPACITY_PERCENT.toFloat()..
            QuickLauncherDisplaySettings.MAX_BACKGROUND_OPACITY_PERCENT.toFloat(),
        steps = QuickLauncherDisplaySettings.MAX_BACKGROUND_OPACITY_PERCENT -
            QuickLauncherDisplaySettings.MIN_BACKGROUND_OPACITY_PERCENT - 1,
        enabled = enabled,
        label = stringResource(
            R.string.quick_launcher_background_opacity_value,
            display.backgroundOpacityPercent,
        ),
        formatLabel = { "${it.roundToInt()}%" },
        onValueChange = { value ->
            onDisplayChange(
                display.copy(backgroundOpacityPercent = value.roundToInt()),
            )
        },
    )
}

@Composable
fun QuickLauncherAppearanceSettingsCard(
    display: QuickLauncherDisplaySettings,
    enabled: Boolean,
    onDisplayChange: (QuickLauncherDisplaySettings) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsCard {
            QuickLauncherAppearanceSettings(
                display = display,
                enabled = enabled,
                onDisplayChange = onDisplayChange,
            )
        }
        MiuixSmallTitle(
            stringResource(R.string.quick_launcher_icon_shape_section),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingsRadioGroup {
                IconShapeRadio(
                    title = stringResource(R.string.quick_launcher_icon_shape_default),
                    selected = display.iconShape == QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT,
                    enabled = enabled,
                    onClick = {
                        onDisplayChange(
                            display.copy(iconShape = QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT),
                        )
                    },
                )
                IconShapeRadio(
                    title = stringResource(R.string.quick_launcher_icon_shape_circle),
                    selected = display.iconShape == QuickLauncherDisplaySettings.ICON_SHAPE_CIRCLE,
                    enabled = enabled,
                    onClick = {
                        onDisplayChange(
                            display.copy(iconShape = QuickLauncherDisplaySettings.ICON_SHAPE_CIRCLE),
                        )
                    },
                )
                IconShapeRadio(
                    title = stringResource(R.string.quick_launcher_icon_shape_adaptive),
                    selected = display.iconShape == QuickLauncherDisplaySettings.ICON_SHAPE_ADAPTIVE,
                    enabled = enabled,
                    onClick = {
                        onDisplayChange(
                            display.copy(iconShape = QuickLauncherDisplaySettings.ICON_SHAPE_ADAPTIVE),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsCardScope.IconShapeRadio(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingRadioRow(
        title = title,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
    )
}
