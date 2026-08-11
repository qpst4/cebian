@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionTrampolineActivity
import com.slideindex.app.overlay.searchpanel.FilePermissionTrampolineActivity
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.settings.SearchPanelAppDisplayStyle
import com.slideindex.app.settings.SearchPanelBarPosition
import com.slideindex.app.settings.SearchPanelHistoryCapacity
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchPanelListOrder
import com.slideindex.app.settings.SearchPanelPresentationMode
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsHintText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SearchPanelSettingsScreen(
    settings: AppSettings,
    searchHistoryEntryCount: Int,
    onBack: () -> Unit,
    onSetDefaultEngineId: (String?) -> Unit,
    onSetSearchPanelInputBehavior: (SearchPanelInputBehavior) -> Unit,
    onSetSearchPanelContactSearchEnabled: (Boolean) -> Unit,
    onSetSearchPanelFileSearchEnabled: (Boolean) -> Unit,
    onOpenFileSearchSettings: () -> Unit,
    onSetSearchPanelPresentationMode: (SearchPanelPresentationMode) -> Unit,
    onSetSearchPanelBarPosition: (SearchPanelBarPosition) -> Unit,
    onSetSearchPanelListOrder: (SearchPanelListOrder) -> Unit,
    onSetSearchPanelAppDisplayStyle: (SearchPanelAppDisplayStyle) -> Unit,
    onSetSearchPanelCalculatorEnabled: (Boolean) -> Unit,
    onSetSearchPanelWebSuggestionsEnabled: (Boolean) -> Unit,
    onSetSearchPanelWebSuggestionsCount: (Int) -> Unit,
    onSetSearchPanelHistoryMaxEntries: (Int) -> Unit,
    onSetSearchPanelSectionAliases: (com.slideindex.app.settings.SearchPanelSectionAliasSettings) -> Unit,
    onClearSearchHistory: () -> Unit,
    onSetSearchPanelBackgroundStyle: (Int) -> Unit,
    onSetSearchPanelBlurRadiusDp: (Int) -> Unit,
    onSetSearchPanelDimPercent: (Int) -> Unit,
    onOpenPreviewSort: () -> Unit,
    onOpenTextSearchEngines: () -> Unit,
    onOpenImageSearchEngines: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var wallpaperPermissionGranted by remember {
        mutableStateOf(SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wallpaperPermissionGranted =
                    SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun ensureWallpaperPermission() {
        WallpaperPermissionTrampolineActivity.ensurePermission(context) { granted ->
            wallpaperPermissionGranted = granted
        }
    }
    val engines = remember(settings.searchEngines) {
        SearchEngineStore.textSettingsEngines(settings.searchEngines)
    }
    val presentationModes = SearchPanelPresentationMode.entries
    val inputBehaviorEntries = SearchPanelInputBehavior.entries
    val barPositions = SearchPanelBarPosition.entries
    val listOrders = SearchPanelListOrder.entries
    val appDisplayStyles = SearchPanelAppDisplayStyle.entries
    val historyCapacityPresets = SearchPanelHistoryCapacity.presets
    val historyCapacityIndex = historyCapacityPresets
        .indexOf(settings.searchPanelHistoryMaxEntries)
        .let { if (it >= 0) it else historyCapacityPresets.indexOf(SearchPanelHistoryCapacity.DEFAULT).coerceAtLeast(0) }
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
            SettingDropdownRow(
                title = stringResource(R.string.search_panel_app_display_style_title),
                items = appDisplayStyles.map { searchPanelAppDisplayStyleLabel(it) },
                selectedIndex = appDisplayStyles.indexOf(settings.searchPanelAppDisplayStyle)
                    .coerceAtLeast(0),
                onSelectedIndexChange = { onSetSearchPanelAppDisplayStyle(appDisplayStyles[it]) },
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
            SettingDropdownRow(
                icon = { label -> Icon(Icons.Outlined.History, contentDescription = label) },
                title = stringResource(R.string.search_panel_history_capacity_title),
                items = historyCapacityPresets.map {
                    stringResource(R.string.search_panel_history_capacity_value, it)
                },
                selectedIndex = historyCapacityIndex,
                onSelectedIndexChange = { onSetSearchPanelHistoryMaxEntries(historyCapacityPresets[it]) },
            )
            SettingsHintText(stringResource(R.string.search_panel_history_hint))
            SettingLinkRow(
                title = stringResource(R.string.search_panel_history_clear),
                subtitle = pluralStringResource(
                    R.plurals.search_panel_history_count,
                    searchHistoryEntryCount,
                    searchHistoryEntryCount,
                ),
                enabled = searchHistoryEntryCount > 0,
                onClick = { showClearHistoryDialog = true },
            )
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_aliases),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingsHintText(stringResource(R.string.search_panel_section_aliases_hint))
            val aliases = settings.searchPanelSectionAliases
            SectionAliasField(
                label = stringResource(R.string.search_panel_section_apps),
                value = aliases.apps,
                onValueChange = {
                    onSetSearchPanelSectionAliases(aliases.copy(apps = it))
                },
            )
            SectionAliasField(
                label = stringResource(R.string.search_panel_section_contacts),
                value = aliases.contacts,
                onValueChange = {
                    onSetSearchPanelSectionAliases(aliases.copy(contacts = it))
                },
            )
            SectionAliasField(
                label = stringResource(R.string.search_panel_section_files),
                value = aliases.files,
                onValueChange = {
                    onSetSearchPanelSectionAliases(aliases.copy(files = it))
                },
            )
            SectionAliasField(
                label = stringResource(R.string.search_panel_section_settings),
                value = aliases.settings,
                onValueChange = {
                    onSetSearchPanelSectionAliases(aliases.copy(settings = it))
                },
            )
            SettingLinkRow(
                title = stringResource(R.string.search_engine_alias_hint),
                subtitle = stringResource(R.string.search_panel_engine_alias_nav_desc),
                onClick = onOpenTextSearchEngines,
            )
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_appearance),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            val backgroundStyles = listOf(
                SearchPanelBackgroundStyle.BLUR,
                SearchPanelBackgroundStyle.WALLPAPER_BLUR,
                SearchPanelBackgroundStyle.BLACK,
            )
            SettingDropdownRow(
                title = stringResource(R.string.honeycomb_display_section_background),
                items = listOf(
                    stringResource(R.string.honeycomb_background_blur),
                    stringResource(R.string.honeycomb_background_wallpaper_blur),
                    stringResource(R.string.honeycomb_background_black),
                ),
                selectedIndex = backgroundStyles.indexOf(settings.searchPanelBackgroundStyle)
                    .coerceAtLeast(0),
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
            SettingLinkRow(
                title = stringResource(R.string.wallpaper_blur_permission_title),
                subtitle = stringResource(
                    if (wallpaperPermissionGranted) {
                        R.string.wallpaper_blur_permission_granted
                    } else {
                        R.string.wallpaper_blur_permission_missing
                    },
                ),
                enabled = settings.searchPanelBackgroundStyle ==
                    SearchPanelBackgroundStyle.WALLPAPER_BLUR &&
                    !wallpaperPermissionGranted,
                onClick = { ensureWallpaperPermission() },
            )
            if (settings.searchPanelBackgroundStyle == SearchPanelBackgroundStyle.BLUR
                || settings.searchPanelBackgroundStyle == SearchPanelBackgroundStyle.WALLPAPER_BLUR
            ) {
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
            }
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

    MiuixConfirmDialog(
        show = showClearHistoryDialog,
        onDismissRequest = { showClearHistoryDialog = false },
        title = stringResource(R.string.search_panel_history_clear_confirm_title),
        message = stringResource(R.string.search_panel_history_clear_confirm_message),
        onConfirm = onClearSearchHistory,
    )
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

@Composable
private fun searchPanelAppDisplayStyleLabel(style: SearchPanelAppDisplayStyle): String = when (style) {
    SearchPanelAppDisplayStyle.ICONS -> stringResource(R.string.search_panel_app_display_style_icons)
    SearchPanelAppDisplayStyle.LIST -> stringResource(R.string.search_panel_app_display_style_list)
}

@Composable
private fun SectionAliasField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    LaunchedEffect(local) {
        if (local == value) return@LaunchedEffect
        delay(450)
        if (local != value) {
            onValueChange(local)
        }
    }
    MiuixLabeledTextField(
        value = local,
        onValueChange = { local = it },
        label = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
