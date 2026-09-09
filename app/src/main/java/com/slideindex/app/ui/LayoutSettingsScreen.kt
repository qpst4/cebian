package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixListScaffold
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
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
    onHideEmptyIndexLettersChange: (Boolean) -> Unit,
    onOpenHiddenAppsSettings: () -> Unit,
    onLayoutPreviewStart: () -> Unit,
    onLayoutPreviewStop: () -> Unit,
    onIndexHeightPreviewChange: (Float) -> Unit = {},
) {
    DisposableEffect(Unit) {
        onDispose {
            onLayoutPreviewStop()
        }
    }

    val layoutDesc = stringResource(R.string.layout_settings_entry_desc)
    val previewHint = stringResource(R.string.live_preview_hint)

    MiuixListScaffold(
        title = stringResource(R.string.layout_settings_title),
        pageHint = "$layoutDesc\n$previewHint",
        showPageHintCard = true,
        navigationIcon = { MiuixBackNavigationIcon(onBack) },
    ) {
        item(key = "panel_section") {
            SmallTitle(stringResource(R.string.settings_section_panel), modifier = Modifier.fillMaxWidth())
        }

        MiuixListSettingsCard(
            keyPrefix = "layout-panel",
            items = listOf(
                CardItem("hide-empty-letters") {
                    MiuixSwitchRow(
                        title = stringResource(R.string.hide_empty_index_letters),
                        summary = stringResource(R.string.hide_empty_index_letters_desc),
                        checked = settings.hideEmptyIndexLetters,
                        enabled = serviceEnabled,
                        onCheckedChange = onHideEmptyIndexLettersChange,
                    )
                },
                CardItem("index-height") {
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
                },
                CardItem("apps-per-row") {
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
                },
                CardItem("panel-opacity") {
                    MiuixSliderRow(
                        title = stringResource(R.string.panel_opacity),
                        value = settings.panelOpacity,
                        valueRange = 0.75f..1f,
                        enabled = serviceEnabled,
                        label = "",
                        formatLabel = { "${(it * 100).roundToInt()}%" },
                        onValueChange = onPanelOpacityChange,
                    )
                },
            ),
        )

        item(key = "hidden_section") {
            SmallTitle(stringResource(R.string.hidden_apps_section_in_index), modifier = Modifier.fillMaxWidth())
        }

        MiuixListSettingsCard(
            keyPrefix = "layout-hidden-apps",
            items = listOf(
                CardItem("hidden-apps-entry") {
                    val hiddenCount = settings.hiddenAppPackages.size
                    val hiddenSubtitle = if (hiddenCount > 0) {
                        stringResource(R.string.hidden_apps_entry_count, hiddenCount)
                    } else {
                        stringResource(R.string.hidden_apps_entry_desc)
                    }
                    MiuixArrowRow(
                        title = stringResource(R.string.hidden_apps_entry_title),
                        summary = hiddenSubtitle,
                        onClick = onOpenHiddenAppsSettings,
                    )
                },
            ),
        )

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
