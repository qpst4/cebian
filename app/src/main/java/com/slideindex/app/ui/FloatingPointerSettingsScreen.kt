package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenPointerSettings: () -> Unit,
    onOpenJoystickSettings: () -> Unit,
    onOpenRadialMenuSettings: () -> Unit,
    onJoystickAreaZoomChange: (Float) -> Unit,
    onJoystickAreaWidthChange: (Float) -> Unit,
    onJoystickAreaHeightChange: (Float) -> Unit,
    onMatchJoystickToScreenAspectChange: (Boolean) -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.floating_pointer_settings_title),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.floating_pointer_settings_desc))
        SettingsHintText(stringResource(R.string.floating_pointer_preview_drag_hint))

        SettingsSectionTitle(stringResource(R.string.floating_pointer_joystick_area_section))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_joystick_area_zoom),
                value = settings.floatingPointerJoystickAreaZoomFraction,
                valueRange = 0.1f..1f,
                steps = 8,
                enabled = true,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatingPointerJoystickAreaZoomFraction * 100).roundToInt(),
                ),
                onValueChange = onJoystickAreaZoomChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_joystick_area_width),
                value = settings.floatingPointerJoystickAreaWidthPx,
                valueRange = 120f..800f,
                steps = 13,
                enabled = true,
                label = stringResource(
                    R.string.floating_pointer_size_px_value,
                    settings.floatingPointerJoystickAreaWidthPx.roundToInt(),
                ),
                onValueChange = onJoystickAreaWidthChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_joystick_area_height),
                value = settings.floatingPointerJoystickAreaHeightPx,
                valueRange = 120f..1400f,
                steps = 12,
                enabled = !settings.floatingPointerMatchJoystickToScreenAspect,
                label = stringResource(
                    R.string.floating_pointer_size_px_value,
                    settings.floatingPointerJoystickAreaHeightPx.roundToInt(),
                ),
                onValueChange = onJoystickAreaHeightChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_match_joystick_aspect),
                subtitle = stringResource(R.string.floating_pointer_match_joystick_aspect_desc),
                checked = settings.floatingPointerMatchJoystickToScreenAspect,
                enabled = true,
                onCheckedChange = onMatchJoystickToScreenAspectChange,
            )
        }
        SettingsHintText(stringResource(R.string.floating_pointer_joystick_area_hint))

        SettingsSectionTitle(stringResource(R.string.floating_pointer_settings_section_appearance))
        SettingsCard {
            SettingNavigationRow(
                icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                title = stringResource(R.string.floating_pointer_pointer_settings_title),
                subtitle = stringResource(
                    R.string.floating_pointer_pointer_settings_summary,
                    settings.floatingPointerRingThicknessPx.roundToInt(),
                    settings.floatingPointerDotDiameterPx.roundToInt(),
                ),
                onClick = onOpenPointerSettings,
            )
            SettingNavigationRow(
                icon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                title = stringResource(R.string.floating_pointer_joystick_settings_title),
                subtitle = stringResource(
                    R.string.floating_pointer_joystick_settings_summary,
                    settings.floatingPointerJoystickDiameterPx.roundToInt(),
                ),
                onClick = onOpenJoystickSettings,
            )
            SettingNavigationRow(
                icon = { Icon(Icons.Default.RadioButtonChecked, contentDescription = null) },
                title = stringResource(R.string.floating_pointer_radial_settings_title),
                subtitle = if (settings.floatingPointerRadialMenuEnabled) {
                    stringResource(R.string.floating_pointer_radial_settings_summary_enabled)
                } else {
                    stringResource(R.string.floating_pointer_radial_settings_summary_disabled)
                },
                onClick = onOpenRadialMenuSettings,
            )
        }

        SettingsHintText(stringResource(R.string.floating_pointer_usage_hint))
    }
}

@Composable
fun FloatingPointerEntryCard(
    settings: AppSettings,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = if (enabled) {
        stringResource(
            R.string.floating_pointer_entry_summary,
            settings.floatingPointerJoystickDiameterPx.roundToInt(),
            settings.floatingPointerPointerDiameterPx.roundToInt(),
            settings.floatingPointerJoystickAreaWidthPx.roundToInt(),
            settings.floatingPointerJoystickAreaHeightPx.roundToInt(),
        )
    } else {
        stringResource(R.string.floating_pointer_entry_desc)
    }
    SettingNavigationRow(
        icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
        title = stringResource(R.string.floating_pointer_settings_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}
