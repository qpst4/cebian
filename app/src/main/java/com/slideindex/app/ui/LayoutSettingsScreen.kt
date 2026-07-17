package com.slideindex.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutSettingsScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onIndexHeightChange: (Float) -> Unit,
    onAppsPerRowChange: (Int) -> Unit,
    onPanelOpacityChange: (Float) -> Unit,
    onOpenHiddenAppsSettings: () -> Unit,
    onLayoutPreviewStart: () -> Unit,
    onLayoutPreviewStop: () -> Unit,
    onDebugPerformanceMonitorChange: (Boolean) -> Unit = {},
) {
    DisposableEffect(Unit) {
        onDispose { onLayoutPreviewStop() }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.layout_settings_title),
        subtitle = stringResource(R.string.layout_settings_entry_desc),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.live_preview_hint))

        SettingsSectionTitle(stringResource(R.string.settings_section_panel))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.index_height),
                value = settings.indexHeightFraction,
                valueRange = 0.25f..0.65f,
                enabled = serviceEnabled,
                label = "",
                formatLabel = { "${(it * 100).roundToInt()}%" },
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onLayoutPreviewStart,
                onLayoutPreviewStop = onLayoutPreviewStop,
                onValueChange = onIndexHeightChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.apps_per_row),
                value = settings.appsPerRow.toFloat(),
                valueRange = 2f..5f,
                steps = 2,
                enabled = serviceEnabled,
                label = pluralStringResource(
                    R.plurals.apps_per_row_value_label,
                    settings.appsPerRow,
                    settings.appsPerRow,
                ),
                onValueChange = { onAppsPerRowChange(it.roundToInt()) },
            )
            SettingsSliderRow(
                title = stringResource(R.string.panel_opacity),
                value = settings.panelOpacity,
                valueRange = 0.75f..1f,
                enabled = serviceEnabled,
                label = "",
                formatLabel = { "${(it * 100).roundToInt()}%" },
                onValueChange = onPanelOpacityChange,
            )
        }

        SettingsSectionTitle(stringResource(R.string.hidden_apps_section_in_index))
        SettingsCard {
            HiddenAppsEntryCard(
                hiddenCount = settings.hiddenAppPackages.size,
                onClick = onOpenHiddenAppsSettings,
            )
        }

        if (BuildConfig.DEBUG) {
            SettingsSectionTitle(stringResource(R.string.debug_section_title))
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.debug_performance_monitor),
                    subtitle = stringResource(R.string.debug_performance_monitor_desc),
                    checked = settings.debugPerformanceMonitorEnabled,
                    enabled = true,
                    onCheckedChange = onDebugPerformanceMonitorChange,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun LayoutSettingsEntryCard(
    settings: AppSettings,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = if (enabled) {
        stringResource(
            R.string.layout_settings_entry_summary,
            settings.appsPerRow,
        )
    } else {
        stringResource(R.string.layout_settings_entry_desc)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.SortByAlpha, contentDescription = label) },
        title = stringResource(R.string.layout_settings_entry_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun QuickLauncherEntryCard(
    settings: AppSettings,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = if (enabled) {
        stringResource(
            R.string.quick_launcher_entry_summary,
            settings.quickLauncherColumnsPerPage,
            settings.quickLauncherRowsPerPage,
        )
    } else {
        stringResource(R.string.quick_launcher_entry_desc)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Apps, contentDescription = label) },
        title = stringResource(R.string.quick_launcher_editor_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun QuickLauncherLayoutSettings(
    settings: AppSettings,
    enabled: Boolean,
    onColumnsChange: (Int) -> Unit,
    onRowsChange: (Int) -> Unit,
) {
    SettingsCard {
        SettingsSliderRow(
            title = stringResource(R.string.quick_launcher_grid_columns),
            value = settings.quickLauncherColumnsPerPage.toFloat(),
            valueRange = 2f..5f,
            steps = 2,
            enabled = enabled,
            label = stringResource(
                R.string.quick_launcher_grid_columns_label,
                settings.quickLauncherColumnsPerPage,
            ),
            onValueChange = { onColumnsChange(it.roundToInt()) },
        )
        SettingsSliderRow(
            title = stringResource(R.string.quick_launcher_grid_rows),
            value = settings.quickLauncherRowsPerPage.toFloat(),
            valueRange = 2f..6f,
            steps = 3,
            enabled = enabled,
            label = stringResource(
                R.string.quick_launcher_grid_rows_label,
                settings.quickLauncherRowsPerPage,
            ),
            onValueChange = { onRowsChange(it.roundToInt()) },
        )
    }
}
