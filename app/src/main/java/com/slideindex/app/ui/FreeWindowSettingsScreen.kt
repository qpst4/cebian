package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.settings.titleRes
import com.slideindex.app.ui.settings.components.SettingsCardScope
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreeWindowSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLongPressDurationChange: (Int) -> Unit,
    onOpenLaunchPolicy: () -> Unit,
    onOpenMode: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    val selectedMode = settings.resolvedFreeWindowMode()
    val selectedPolicy = settings.resolvedLaunchPolicy()
    val longPressDuration = settings.effectiveLongPressDurationMs()
    val showLongPressDuration = selectedPolicy.usesLongPress()
    val resources = LocalResources.current
    val formatDurationLabel = remember(resources) {
        { ms: Float ->
            resources.getString(R.string.long_press_launch_duration_value, ms.roundToInt())
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.free_window_settings_title),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSectionTitle(stringResource(R.string.settings_section_service))
            SettingsCard {
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.PowerSettingsNew, contentDescription = label) },
                    title = stringResource(R.string.free_window_enabled),
                    subtitle = stringResource(R.string.free_window_enabled_desc),
                    checked = settings.freeWindowEnabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            SettingsHintText(stringResource(R.string.free_window_portrait_only_hint))
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSectionTitle(stringResource(R.string.settings_section_launch))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                    title = stringResource(R.string.launch_policy_title),
                    subtitle = stringResource(selectedPolicy.titleRes),
                    enabled = settings.freeWindowEnabled,
                    onClick = onOpenLaunchPolicy,
                )
            }
            if (showLongPressDuration) {
                SettingsHintText(stringResource(R.string.long_press_launch_duration_desc))
                SettingsCard {
                    SettingsSliderRow(
                        title = stringResource(R.string.long_press_launch_duration),
                        value = longPressDuration.toFloat(),
                        valueRange = 250f..900f,
                        steps = 12,
                        enabled = settings.freeWindowEnabled,
                        label = formatDurationLabel(longPressDuration.toFloat()),
                        snapValue = { value ->
                            ((value / 50f).roundToInt() * 50).toFloat().coerceIn(250f, 900f)
                        },
                        formatLabel = formatDurationLabel,
                        onValueChange = { value -> onLongPressDurationChange(value.roundToInt()) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSectionTitle(stringResource(R.string.settings_section_free_window))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Layers, contentDescription = label) },
                    title = stringResource(R.string.free_window_launch_mode),
                    subtitle = stringResource(selectedMode.titleRes),
                    enabled = settings.freeWindowEnabled,
                    onClick = onOpenMode,
                )
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Tune, contentDescription = label) },
                    title = stringResource(R.string.free_window_adjust_layout),
                    subtitle = stringResource(R.string.free_window_adjust_layout_desc),
                    enabled = settings.freeWindowEnabled,
                    onClick = onOpenPreview,
                )
            }
            SettingsHintText(stringResource(R.string.free_window_mode_hint))
        }
    }
}

@Composable
fun SettingsCardScope.FreeWindowEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.CropFree, contentDescription = label) },
        title = stringResource(R.string.free_window_entry_title),
        subtitle = stringResource(R.string.free_window_entry_desc),
        onClick = onClick,
    )
}
