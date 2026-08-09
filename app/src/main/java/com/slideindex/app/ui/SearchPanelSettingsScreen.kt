@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.searchpanel.FilePermissionTrampolineActivity
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchPanelBarPosition
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchPanelListOrder
import com.slideindex.app.settings.SearchPanelPresentationMode
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import kotlin.math.roundToInt

@Composable
fun SearchPanelSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSetDefaultEngineId: (String?) -> Unit,
    onSetSearchPanelInputBehavior: (SearchPanelInputBehavior) -> Unit,
    onSetSearchPanelContactSearchEnabled: (Boolean) -> Unit,
    onSetSearchPanelFileSearchEnabled: (Boolean) -> Unit,
    onOpenFileSearchSettings: () -> Unit,
    onSetSearchPanelPresentationMode: (SearchPanelPresentationMode) -> Unit,
    onSetSearchPanelBarPosition: (SearchPanelBarPosition) -> Unit,
    onSetSearchPanelListOrder: (SearchPanelListOrder) -> Unit,
    onSetSearchPanelCalculatorEnabled: (Boolean) -> Unit,
    onSetSearchPanelWebSuggestionsEnabled: (Boolean) -> Unit,
    onSetSearchPanelWebSuggestionsCount: (Int) -> Unit,
    onSetSearchPanelWallpaperBlurEnabled: (Boolean) -> Unit,
    onSetSearchPanelBlurRadiusDp: (Int) -> Unit,
    onOpenPreviewSort: () -> Unit,
    onOpenTextSearchEngines: () -> Unit,
    onOpenImageSearchEngines: () -> Unit,
) {
    val context = LocalContext.current
    val engines = remember(settings.searchEngines) {
        SearchEngineStore.textSettingsEngines(settings.searchEngines)
    }
    val presentationModes = SearchPanelPresentationMode.entries
    val inputBehaviorEntries = SearchPanelInputBehavior.entries
    val barPositions = SearchPanelBarPosition.entries
    val listOrders = SearchPanelListOrder.entries
    val noneEngineLabel = stringResource(R.string.search_panel_default_engine_none)
    val defaultEngineItems = listOf(noneEngineLabel) + engines.map { it.name }
    val defaultEngineIndex = if (settings.searchPanelDefaultEngineId == null) {
        0
    } else {
        engines.indexOfFirst { it.id == settings.searchPanelDefaultEngineId }.let { idx ->
            if (idx >= 0) idx + 1 else 0
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            ContactSearchIndex.invalidateCache()
        }
    }
    val requestFilePermission: () -> Unit = {
        if (!FileSearchIndex.hasPermission(context)) {
            FilePermissionTrampolineActivity.launch(context) { /* result handled on next search */ }
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_settings_title),
        subtitle = stringResource(R.string.search_panel_settings_subtitle),
        onBack = onBack,
    ) {
        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_layout),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingDropdownRow(
                title = stringResource(R.string.search_panel_presentation_title),
                items = presentationModes.map { searchPanelPresentationLabel(it) },
                selectedIndex = presentationModes.indexOf(settings.searchPanelPresentationMode)
                    .coerceAtLeast(0),
                onSelectedIndexChange = { onSetSearchPanelPresentationMode(presentationModes[it]) },
            )
            SettingDropdownRow(
                title = stringResource(R.string.search_panel_bar_position_title),
                items = barPositions.map { searchPanelBarPositionLabel(it) },
                selectedIndex = barPositions.indexOf(settings.searchPanelBarPosition).coerceAtLeast(0),
                onSelectedIndexChange = { onSetSearchPanelBarPosition(barPositions[it]) },
            )
            SettingDropdownRow(
                title = stringResource(R.string.search_panel_list_order_title),
                items = listOrders.map { searchPanelListOrderLabel(it) },
                selectedIndex = listOrders.indexOf(settings.searchPanelListOrder).coerceAtLeast(0),
                onSelectedIndexChange = { onSetSearchPanelListOrder(listOrders[it]) },
            )
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_behavior),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingDropdownRow(
                title = stringResource(R.string.search_panel_default_engine_title),
                items = defaultEngineItems,
                selectedIndex = defaultEngineIndex,
                enabled = engines.isNotEmpty(),
                onSelectedIndexChange = { index ->
                    onSetDefaultEngineId(if (index == 0) null else engines[index - 1].id)
                },
            )
            SettingDropdownRow(
                title = stringResource(R.string.search_panel_input_behavior_title),
                items = inputBehaviorEntries.map { searchPanelInputBehaviorLabel(it) },
                selectedIndex = inputBehaviorEntries.indexOf(settings.searchPanelInputBehavior)
                    .coerceAtLeast(0),
                onSelectedIndexChange = { onSetSearchPanelInputBehavior(inputBehaviorEntries[it]) },
            )
            SettingSwitchRow(
                title = stringResource(R.string.search_panel_calculator_title),
                subtitle = stringResource(R.string.search_panel_calculator_desc),
                checked = settings.searchPanelCalculatorEnabled,
                enabled = true,
                onCheckedChange = onSetSearchPanelCalculatorEnabled,
            )
            SettingSwitchRow(
                title = stringResource(R.string.search_panel_web_suggestions_title),
                subtitle = stringResource(R.string.search_panel_web_suggestions_desc),
                checked = settings.searchPanelWebSuggestionsEnabled,
                enabled = true,
                onCheckedChange = onSetSearchPanelWebSuggestionsEnabled,
            )
            if (settings.searchPanelWebSuggestionsEnabled) {
                SettingsSliderRow(
                    title = stringResource(R.string.search_panel_web_suggestions_count_title),
                    value = settings.searchPanelWebSuggestionsCount.toFloat(),
                    valueRange = AppSettings.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MIN.toFloat()..
                        AppSettings.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MAX.toFloat(),
                    steps = 3,
                    enabled = true,
                    label = settings.searchPanelWebSuggestionsCount.toString(),
                    onValueChange = { onSetSearchPanelWebSuggestionsCount(it.roundToInt()) },
                )
            }
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_appearance),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.search_panel_wallpaper_blur_title),
                subtitle = stringResource(R.string.search_panel_wallpaper_blur_desc),
                checked = settings.searchPanelWallpaperBlurEnabled,
                enabled = true,
                onCheckedChange = onSetSearchPanelWallpaperBlurEnabled,
            )
            if (settings.searchPanelWallpaperBlurEnabled) {
                SettingsSliderRow(
                    title = stringResource(R.string.search_panel_blur_radius_title),
                    value = settings.searchPanelBlurRadiusDp.toFloat(),
                    valueRange = AppSettings.SEARCH_PANEL_BLUR_RADIUS_MIN_DP.toFloat()..
                        AppSettings.SEARCH_PANEL_BLUR_RADIUS_MAX_DP.toFloat(),
                    steps = 15,
                    enabled = true,
                    label = stringResource(
                        R.string.corner_gesture_zone_dp_value,
                        settings.searchPanelBlurRadiusDp,
                    ),
                    onValueChange = { onSetSearchPanelBlurRadiusDp(it.roundToInt()) },
                )
            }
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_candidates),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.search_panel_contact_search_title),
                subtitle = stringResource(R.string.search_panel_contact_search_desc),
                checked = settings.searchPanelContactSearchEnabled,
                enabled = true,
                onCheckedChange = { enabled ->
                    if (enabled && !ContactSearchIndex.hasPermission(context)) {
                        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                    onSetSearchPanelContactSearchEnabled(enabled)
                },
            )
            SettingSwitchRow(
                title = stringResource(R.string.search_panel_file_search_title),
                subtitle = stringResource(R.string.search_panel_file_search_desc),
                checked = settings.searchPanelFileSearchEnabled,
                enabled = true,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        requestFilePermission()
                    }
                    onSetSearchPanelFileSearchEnabled(enabled)
                },
            )
            if (settings.searchPanelFileSearchEnabled) {
                SettingNavigationRow(
                    icon = { label ->
                        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = label)
                    },
                    title = stringResource(R.string.search_panel_file_search_manage_title),
                    subtitle = stringResource(R.string.search_panel_file_search_manage_desc),
                    onClick = onOpenFileSearchSettings,
                )
            }
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_engines),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Reorder, contentDescription = label) },
                title = stringResource(R.string.search_engine_settings_preview_mode),
                subtitle = stringResource(R.string.search_engine_settings_preview_mode_summary),
                enabled = engines.isNotEmpty(),
                onClick = onOpenPreviewSort,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Search, contentDescription = label) },
                title = stringResource(R.string.search_engine_settings_title),
                subtitle = stringResource(R.string.search_panel_text_engines_entry_desc),
                onClick = onOpenTextSearchEngines,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Image, contentDescription = label) },
                title = stringResource(R.string.image_search_engine_settings_title),
                subtitle = stringResource(R.string.search_panel_image_engines_entry_desc),
                onClick = onOpenImageSearchEngines,
            )
        }
    }
}

@Composable
fun SettingsCardScope.SearchPanelEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.searchPanel(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.search_panel_entry_title),
        subtitle = stringResource(R.string.search_panel_entry_desc),
        onClick = onClick,
    )
}

@Composable
private fun searchPanelPresentationLabel(mode: SearchPanelPresentationMode): String = when (mode) {
    SearchPanelPresentationMode.BOTTOM_SHEET ->
        stringResource(R.string.search_panel_presentation_bottom_sheet)
    SearchPanelPresentationMode.FULLSCREEN ->
        stringResource(R.string.search_panel_presentation_fullscreen)
}

@Composable
private fun searchPanelInputBehaviorLabel(behavior: SearchPanelInputBehavior): String = when (behavior) {
    SearchPanelInputBehavior.SELECT_ALL -> stringResource(R.string.search_panel_input_behavior_select_all)
    SearchPanelInputBehavior.CLEAR -> stringResource(R.string.search_panel_input_behavior_clear)
    SearchPanelInputBehavior.KEEP -> stringResource(R.string.search_panel_input_behavior_keep)
}

@Composable
private fun searchPanelListOrderLabel(order: SearchPanelListOrder): String = when (order) {
    SearchPanelListOrder.TOP_DOWN -> stringResource(R.string.search_panel_list_order_top_down)
    SearchPanelListOrder.BOTTOM_UP -> stringResource(R.string.search_panel_list_order_bottom_up)
}

@Composable
private fun searchPanelBarPositionLabel(position: SearchPanelBarPosition): String = when (position) {
    SearchPanelBarPosition.TOP -> stringResource(R.string.search_panel_bar_position_top)
    SearchPanelBarPosition.BOTTOM -> stringResource(R.string.search_panel_bar_position_bottom)
}
