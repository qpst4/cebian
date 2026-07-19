package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerBounds
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerSettingsScreen(
    settings: AppSettings,
    areaPreviewEnabled: Boolean,
    previewAccessibilityGranted: Boolean,
    onAreaPreviewEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenPointerSettings: () -> Unit,
    onOpenJoystickSettings: () -> Unit,
    onOpenRadialMenuSettings: () -> Unit,
    onOpenEdgeActionsSettings: () -> Unit,
    onPointerSensitivityChange: (Float) -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.floating_pointer_settings_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.floating_pointer_joystick_area_section))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.floating_pointer_area_preview_title),
                subtitle = if (previewAccessibilityGranted) {
                    stringResource(R.string.floating_pointer_area_preview_desc)
                } else {
                    stringResource(R.string.gesture_action_floating_pointer_permission)
                },
                checked = areaPreviewEnabled,
                enabled = previewAccessibilityGranted,
                onCheckedChange = onAreaPreviewEnabledChange,
            )
        }
        if (areaPreviewEnabled) {
            SettingsHintText(stringResource(R.string.floating_pointer_preview_drag_hint))
        }

        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.floating_pointer_sensitivity),
                value = settings.floatingPointerSensitivityFraction,
                valueRange = FloatingPointerBounds.SENSITIVITY_MIN..FloatingPointerBounds.SENSITIVITY_MAX,
                steps = 10,
                enabled = true,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatingPointerSensitivityFraction * 100).roundToInt(),
                ),
                onValueChange = onPointerSensitivityChange,
            )
        }

        SettingsSectionTitle(stringResource(R.string.floating_pointer_settings_section_appearance))
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.MyLocation, contentDescription = label) },
                title = stringResource(R.string.floating_pointer_pointer_settings_title),
                subtitle = stringResource(
                    R.string.floating_pointer_pointer_settings_summary,
                    settings.floatingPointerRingThicknessPx.roundToInt(),
                    settings.floatingPointerDotDiameterPx.roundToInt(),
                ),
                onClick = onOpenPointerSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                title = stringResource(R.string.floating_pointer_joystick_settings_title),
                subtitle = stringResource(
                    R.string.floating_pointer_joystick_settings_summary,
                    settings.floatingPointerJoystickDiameterPx.roundToInt(),
                ),
                onClick = onOpenJoystickSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.RadioButtonChecked, contentDescription = label) },
                title = stringResource(R.string.floating_pointer_radial_settings_title),
                subtitle = gestureActionLabel(settings.floatingPointerJoystickLongPressAction),
                onClick = onOpenRadialMenuSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.KeyboardArrowUp, contentDescription = label) },
                title = stringResource(R.string.floating_pointer_edge_settings_title),
                subtitle = stringResource(R.string.floating_pointer_edge_settings_summary),
                onClick = onOpenEdgeActionsSettings,
            )
        }
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
            (settings.floatingPointerSensitivityFraction * 100).roundToInt(),
        )
    } else {
        stringResource(R.string.floating_pointer_entry_desc)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.MyLocation, contentDescription = label) },
        title = stringResource(R.string.floating_pointer_settings_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}
