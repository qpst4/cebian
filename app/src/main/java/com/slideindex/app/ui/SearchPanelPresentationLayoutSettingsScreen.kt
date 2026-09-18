@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchPanelAppDisplayStyle
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.settings.SearchPanelBarPosition
import com.slideindex.app.settings.SearchPanelEnterAction
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchPanelListOrder
import com.slideindex.app.settings.SearchPanelPresentationMode
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@Composable
fun SearchPanelPresentationLayoutSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSetSearchPanelPresentationMode: (SearchPanelPresentationMode) -> Unit,
    onSetSearchPanelBarPosition: (SearchPanelBarPosition) -> Unit,
    onSetSearchPanelListOrder: (SearchPanelListOrder) -> Unit,
    onSetSearchPanelAppDisplayStyle: (SearchPanelAppDisplayStyle) -> Unit,
    onSetSearchPanelBackgroundStyle: (Int) -> Unit,
    onSetSearchPanelBlurRadiusDp: (Int) -> Unit,
    onSetSearchPanelDimPercent: (Int) -> Unit,
    onSetDefaultEngineId: (String?) -> Unit,
    onSetSearchPanelInputBehavior: (SearchPanelInputBehavior) -> Unit,
    onSetSearchPanelEnterAction: (SearchPanelEnterAction) -> Unit,
) {
    val context = LocalContext.current
    val presentationModes = SearchPanelPresentationMode.entries
    val barPositions = SearchPanelBarPosition.entries
    val listOrders = SearchPanelListOrder.entries
    val appDisplayStyles = SearchPanelAppDisplayStyle.entries
    val inputBehaviorEntries = SearchPanelInputBehavior.entries
    val enterActionEntries = SearchPanelEnterAction.entries
    val backgroundStyles = listOf(
        SearchPanelBackgroundStyle.BLUR,
        SearchPanelBackgroundStyle.WALLPAPER_BLUR,
        SearchPanelBackgroundStyle.BLACK,
    )
    val backgroundSectionTitle = stringResource(R.string.honeycomb_display_section_background)
    val behaviorSectionTitle = stringResource(R.string.search_panel_settings_section_behavior)

    val engines = remember(settings.searchEngines) {
        SearchEngineStore.textSettingsEngines(settings.searchEngines)
    }
    val noneEngineLabel = stringResource(R.string.search_panel_default_engine_none)
    val defaultEngineItems = listOf(noneEngineLabel) + engines.map { it.name }
    val defaultEngineIndex = if (settings.searchPanelDefaultEngineId == null) {
        0
    } else {
        engines.indexOfFirst { it.id == settings.searchPanelDefaultEngineId }.let { idx ->
            if (idx >= 0) idx + 1 else 0
        }
    }

    fun ensureWallpaperPermission() {
        SystemWallpaperBlurHelper.requestWallpaperPermission(context)
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_settings_section_layout),
        pageHint = stringResource(R.string.search_panel_presentation_layout_entry_desc),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "search_panel_layout",
            items = buildList {
                add(
                    settingsCardScopeItem("presentation") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_presentation_title),
                            items = presentationModes.map { searchPanelPresentationLabel(it) },
                            selectedIndex = presentationModes.indexOf(settings.searchPanelPresentationMode).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelPresentationMode(presentationModes[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("bar-position") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_bar_position_title),
                            items = barPositions.map { searchPanelBarPositionLabel(it) },
                            selectedIndex = barPositions.indexOf(settings.searchPanelBarPosition).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelBarPosition(barPositions[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("list-order") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_list_order_title),
                            items = listOrders.map { searchPanelListOrderLabel(it) },
                            selectedIndex = listOrders.indexOf(settings.searchPanelListOrder).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelListOrder(listOrders[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("app-display-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_app_display_style_title),
                            items = appDisplayStyles.map { searchPanelAppDisplayStyleLabel(it) },
                            selectedIndex = appDisplayStyles.indexOf(settings.searchPanelAppDisplayStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelAppDisplayStyle(appDisplayStyles[it]) },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "background_section",
            title = backgroundSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "search_panel_appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("background-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.honeycomb_display_section_background),
                            items = listOf(
                                stringResource(R.string.honeycomb_background_blur),
                                stringResource(R.string.honeycomb_background_wallpaper_blur),
                                stringResource(R.string.honeycomb_background_black),
                            ),
                            selectedIndex = backgroundStyles.indexOf(settings.searchPanelBackgroundStyle).coerceAtLeast(0),
                            onSelectedIndexChange = {
                                val style = backgroundStyles[it]
                                onSetSearchPanelBackgroundStyle(style)
                                if (style == SearchPanelBackgroundStyle.WALLPAPER_BLUR &&
                                    !SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)
                                ) {
                                    ensureWallpaperPermission()
                                }
                            },
                        )
                    },
                )
                if (settings.searchPanelBackgroundStyle == SearchPanelBackgroundStyle.BLUR ||
                    settings.searchPanelBackgroundStyle == SearchPanelBackgroundStyle.WALLPAPER_BLUR
                ) {
                    add(
                        settingsCardScopeItem("blur-strength") {
                            SettingsSliderRow(
                                title = stringResource(R.string.honeycomb_blur_strength),
                                value = settings.searchPanelBlurRadiusDp.toFloat(),
                                valueRange = AppSettings.SEARCH_PANEL_BLUR_RADIUS_MIN_DP.toFloat()..
                                    AppSettings.SEARCH_PANEL_BLUR_RADIUS_MAX_DP.toFloat(),
                                steps = 16,
                                enabled = true,
                                label = stringResource(
                                    R.string.corner_gesture_zone_dp_value,
                                    settings.searchPanelBlurRadiusDp,
                                ),
                                onValueChange = { onSetSearchPanelBlurRadiusDp(it.roundToInt()) },
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("dim-percent") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_dim_percent),
                            value = settings.searchPanelDimPercent.toFloat(),
                            valueRange = AppSettings.SEARCH_PANEL_DIM_MIN_PERCENT.toFloat()..
                                AppSettings.SEARCH_PANEL_DIM_MAX_PERCENT.toFloat(),
                            steps = 12,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                settings.searchPanelDimPercent,
                            ),
                            onValueChange = { onSetSearchPanelDimPercent(it.roundToInt()) },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "behavior_section",
            title = behaviorSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "search_panel_behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("default-engine") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_default_engine_title),
                            items = defaultEngineItems,
                            selectedIndex = defaultEngineIndex,
                            enabled = engines.isNotEmpty(),
                            onSelectedIndexChange = { index ->
                                onSetDefaultEngineId(if (index == 0) null else engines[index - 1].id)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("input-behavior") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_input_behavior_title),
                            items = inputBehaviorEntries.map { searchPanelInputBehaviorLabel(it) },
                            selectedIndex = inputBehaviorEntries.indexOf(settings.searchPanelInputBehavior)
                                .coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelInputBehavior(inputBehaviorEntries[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("enter-action") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_enter_action_title),
                            items = enterActionEntries.map { searchPanelEnterActionLabel(it) },
                            selectedIndex = enterActionEntries.indexOf(settings.searchPanelEnterAction)
                                .coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelEnterAction(enterActionEntries[it]) },
                        )
                    },
                )
            },
        )
    }
}
