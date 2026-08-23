package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Hive
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixListScaffold
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.MiuixSwitchRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import kotlin.math.roundToInt

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
    onIndexHeightPreviewChange: (Float) -> Unit = {},
    onDebugPerformanceMonitorChange: (Boolean) -> Unit = {},
) {
    DisposableEffect(Unit) {
        onDispose {
            onLayoutPreviewStop()
        }
    }

    val panelSliderCount = 3
    val debugSwitchCount = if (BuildConfig.DEBUG) 1 else 0

    MiuixListScaffold(
        title = stringResource(R.string.layout_settings_title),
        navigationIcon = { MiuixBackNavigationIcon(onBack) },
    ) {
        item(key = "hint") {
            MiuixHintText(stringResource(R.string.live_preview_hint))
        }

        item(key = "panel_section") {
            MiuixSmallTitle(stringResource(R.string.settings_section_panel), modifier = Modifier.fillMaxWidth())
        }

        item(key = "panel_sliders_card") {
            top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                MiuixSliderRow(
                    title = stringResource(R.string.index_height),
                    value = settings.indexHeightFraction,
                    valueRange = 0.25f..0.65f,
                    enabled = serviceEnabled,
                    label = "",
                    formatLabel = { "${(it * 100).roundToInt()}%" },
                    triggersLayoutPreview = true,
                    onLayoutPreviewStart = onLayoutPreviewStart,
                    onLayoutPreviewStop = onLayoutPreviewStop,
                    onLayoutPreviewValueChange = onIndexHeightPreviewChange,
                    onValueChange = onIndexHeightChange,
                )
                MiuixSliderRow(
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
                MiuixSliderRow(
                    title = stringResource(R.string.panel_opacity),
                    value = settings.panelOpacity,
                    valueRange = 0.75f..1f,
                    enabled = serviceEnabled,
                    label = "",
                    formatLabel = { "${(it * 100).roundToInt()}%" },
                    onValueChange = onPanelOpacityChange,
                )
            }
        }

        item(key = "hidden_section") {
            MiuixSmallTitle(stringResource(R.string.hidden_apps_section_in_index), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        }

        item(key = "hidden_apps") {
            val hiddenCount = settings.hiddenAppPackages.size
            val hiddenSubtitle = if (hiddenCount > 0) {
                stringResource(R.string.hidden_apps_entry_count, hiddenCount)
            } else {
                stringResource(R.string.hidden_apps_entry_desc)
            }
            top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                MiuixArrowRow(
                    title = stringResource(R.string.hidden_apps_entry_title),
                    summary = hiddenSubtitle,
                    onClick = onOpenHiddenAppsSettings,
                )
            }
        }

        if (BuildConfig.DEBUG) {
            item(key = "debug_section") {
                MiuixSmallTitle(stringResource(R.string.debug_section_title), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
            }
            item(key = "debug_perf") {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    MiuixSwitchRow(
                        title = stringResource(R.string.debug_performance_monitor),
                        summary = stringResource(R.string.debug_performance_monitor_desc),
                        checked = settings.debugPerformanceMonitorEnabled,
                        onCheckedChange = onDebugPerformanceMonitorChange,
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun SettingsCardScope.LayoutSettingsEntryCard(
    settings: ExtensionHubSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
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
        icon = { label ->
            Icon(HubLeadingIcons.layoutSettings(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.layout_settings_entry_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.QuickLauncherEntryCard(
    settings: ExtensionHubSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = if (enabled) {
        pluralStringResource(
            R.plurals.quick_launcher_entry_summary_panels,
            settings.quickLauncherPanelCount,
            settings.quickLauncherPanelCount,
        )
    } else {
        stringResource(R.string.quick_launcher_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.quickLauncher(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.quick_launcher_editor_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.HoneycombLauncherEntryCard(
    settings: ExtensionHubSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = if (enabled) {
        pluralStringResource(
            R.plurals.honeycomb_launcher_entry_summary,
            settings.honeycombLauncherCount,
            settings.honeycombLauncherCount,
        )
    } else {
        stringResource(R.string.honeycomb_launcher_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.honeycombLauncher(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.honeycomb_launcher_editor_title),
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
    val layoutCard = settingsCardItems {
        SettingsSliderRow(
            title = stringResource(R.string.quick_launcher_grid_columns),
            value = settings.quickLauncherColumnsPerPage.toFloat(),
            valueRange = 2f..6f,
            steps = 3,
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
            valueRange = 2f..9f,
            steps = 6,
            enabled = enabled,
            label = stringResource(
                R.string.quick_launcher_grid_rows_label,
                settings.quickLauncherRowsPerPage,
            ),
            onValueChange = { onRowsChange(it.roundToInt()) },
        )
    }
    layoutCard.RenderRows()
}
