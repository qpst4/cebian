package com.slideindex.app.ui.quicklauncher

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.QuickLauncherDisplaySettings
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import kotlin.math.roundToInt

@Composable
fun SettingsCardScope.QuickLauncherAppearanceSettings(
    display: QuickLauncherDisplaySettings,
    enabled: Boolean,
    onDisplayChange: (QuickLauncherDisplaySettings) -> Unit,
) {
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
    SettingsCard {
        QuickLauncherAppearanceSettings(
            display = display,
            enabled = enabled,
            onDisplayChange = onDisplayChange,
        )
    }
}
