@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchPanelAppDisplayStyle
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.settings.SearchPanelBarPosition
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
) {
    val context = LocalContext.current
    val presentationModes = SearchPanelPresentationMode.entries
    val barPositions = SearchPanelBarPosition.entries
    val listOrders = SearchPanelListOrder.entries
    val appDisplayStyles = SearchPanelAppDisplayStyle.entries
    val backgroundStyles = listOf(
        SearchPanelBackgroundStyle.BLUR,
        SearchPanelBackgroundStyle.WALLPAPER_BLUR,
        SearchPanelBackgroundStyle.BLACK,
    )
    val backgroundSectionTitle = stringResource(R.string.honeycomb_display_section_background)

    fun ensureWallpaperPermission() {
        WallpaperPermissionTrampolineActivity.ensurePermission(context) { }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_settings_section_layout),
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
            sectionTop = true,
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
    }
}
