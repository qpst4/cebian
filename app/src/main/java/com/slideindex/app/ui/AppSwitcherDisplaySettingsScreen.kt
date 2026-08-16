package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.settings.AppSwitcherDisplaySettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_100
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSwitcherDisplaySettingsScreen(
    display: AppSwitcherDisplaySettings,
    onBack: () -> Unit,
    onDisplayChange: (AppSwitcherDisplaySettings) -> Unit,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewDisplayChange: (AppSwitcherDisplaySettings) -> Unit = {},
) {
    var localDisplay by remember { mutableStateOf(display) }
    var previewDragging by remember { mutableStateOf(false) }
    LaunchedEffect(display) { localDisplay = display }
    DisposableEffect(Unit) {
        onDispose {
            if (previewDragging) {
                onLayoutPreviewStop()
            }
        }
    }
    fun update(next: AppSwitcherDisplaySettings) {
        localDisplay = next
        onDisplayChange(next)
        if (previewDragging) {
            onLayoutPreviewDisplayChange(next)
        }
    }
    fun beginLayoutPreview() {
        if (!previewDragging) {
            previewDragging = true
            onLayoutPreviewStart()
            onLayoutPreviewDisplayChange(localDisplay)
        }
    }
    fun endLayoutPreview() {
        if (previewDragging) {
            previewDragging = false
            onLayoutPreviewStop()
        }
    }

    val screenTitle = stringResource(R.string.app_switcher_display_settings_title)
    val layoutSectionTitle = stringResource(R.string.app_switcher_display_section_layout)
    val behaviorSectionTitle = stringResource(R.string.app_switcher_display_section_behavior)
    val backgroundSectionTitle = stringResource(R.string.app_switcher_display_section_background)

    SettingsScreenScaffold(
        title = screenTitle,
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "section-layout",
            title = layoutSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "app_switcher_layout",
            items = buildList {
                add(
                    settingsCardScopeItem("icon-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.app_switcher_icon_size),
                            value = localDisplay.iconSizeDp.toFloat(),
                            valueRange = AppSwitcherDisplaySettings.MIN_ICON_SIZE_DP.toFloat()..
                                AppSwitcherDisplaySettings.MAX_ICON_SIZE_DP.toFloat(),
                            steps = AppSwitcherDisplaySettings.MAX_ICON_SIZE_DP -
                                AppSwitcherDisplaySettings.MIN_ICON_SIZE_DP - 1,
                            enabled = true,
                            label = "${localDisplay.iconSizeDp}dp",
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = ::beginLayoutPreview,
                            onLayoutPreviewStop = ::endLayoutPreview,
                            onLayoutPreviewValueChange = {
                                onLayoutPreviewDisplayChange(localDisplay.copy(iconSizeDp = it.roundToInt()))
                            },
                            onValueChange = { update(localDisplay.copy(iconSizeDp = it.roundToInt())) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("spacing") {
                        SettingsSliderRow(
                            title = stringResource(R.string.app_switcher_spacing),
                            value = localDisplay.spacingDp.toFloat(),
                            valueRange = AppSwitcherDisplaySettings.MIN_SPACING_DP.toFloat()..
                                AppSwitcherDisplaySettings.MAX_SPACING_DP.toFloat(),
                            steps = AppSwitcherDisplaySettings.MAX_SPACING_DP -
                                AppSwitcherDisplaySettings.MIN_SPACING_DP - 1,
                            enabled = true,
                            label = "${localDisplay.spacingDp}dp",
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = ::beginLayoutPreview,
                            onLayoutPreviewStop = ::endLayoutPreview,
                            onLayoutPreviewValueChange = {
                                onLayoutPreviewDisplayChange(localDisplay.copy(spacingDp = it.roundToInt()))
                            },
                            onValueChange = { update(localDisplay.copy(spacingDp = it.roundToInt())) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("selection-scale") {
                        SettingsSliderRow(
                            title = stringResource(R.string.app_switcher_selection_scale),
                            value = localDisplay.selectionScale.toFloat(),
                            valueRange = AppSwitcherDisplaySettings.MIN_SELECTION_SCALE.toFloat()..
                                AppSwitcherDisplaySettings.MAX_SELECTION_SCALE.toFloat(),
                            steps = (AppSwitcherDisplaySettings.MAX_SELECTION_SCALE -
                                AppSwitcherDisplaySettings.MIN_SELECTION_SCALE) / 5 - 1,
                            enabled = true,
                            label = "${localDisplay.selectionScale}%",
                            onValueChange = { update(localDisplay.copy(selectionScale = it.roundToInt())) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("radius-ratio") {
                        SettingsSliderRow(
                            title = stringResource(R.string.app_switcher_initial_radius_ratio),
                            value = localDisplay.initialRadiusRatioPercent.toFloat(),
                            valueRange = AppSwitcherDisplaySettings.MIN_INITIAL_RADIUS_RATIO_PERCENT.toFloat()..
                                AppSwitcherDisplaySettings.MAX_INITIAL_RADIUS_RATIO_PERCENT.toFloat(),
                            steps = AppSwitcherDisplaySettings.MAX_INITIAL_RADIUS_RATIO_PERCENT -
                                AppSwitcherDisplaySettings.MIN_INITIAL_RADIUS_RATIO_PERCENT - 1,
                            enabled = true,
                            label = "${localDisplay.initialRadiusRatioPercent}%",
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = ::beginLayoutPreview,
                            onLayoutPreviewStop = ::endLayoutPreview,
                            onLayoutPreviewValueChange = {
                                onLayoutPreviewDisplayChange(
                                    localDisplay.copy(initialRadiusRatioPercent = it.roundToInt()),
                                )
                            },
                            onValueChange = {
                                update(localDisplay.copy(initialRadiusRatioPercent = it.roundToInt()))
                            },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "section-behavior",
            title = behaviorSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "app_switcher_behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("pin-on-release") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                            title = stringResource(R.string.app_switcher_pin_on_release),
                            subtitle = stringResource(R.string.app_switcher_pin_on_release_desc),
                            checked = localDisplay.pinOnRelease,
                            enabled = true,
                            onCheckedChange = { update(localDisplay.copy(pinOnRelease = it)) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("empty-tap-close") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                            title = stringResource(R.string.app_switcher_empty_tap_close),
                            subtitle = stringResource(R.string.app_switcher_empty_tap_close_desc),
                            checked = localDisplay.emptyTapClose,
                            enabled = true,
                            onCheckedChange = { update(localDisplay.copy(emptyTapClose = it)) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("show-selected-name") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Default.Tune, contentDescription = label) },
                            title = stringResource(R.string.app_switcher_show_selected_name),
                            subtitle = stringResource(R.string.app_switcher_show_selected_name_desc),
                            checked = localDisplay.showSelectedName,
                            enabled = true,
                            onCheckedChange = { update(localDisplay.copy(showSelectedName = it)) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("slot-haptic") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Default.Vibration, contentDescription = label) },
                            title = stringResource(R.string.app_switcher_slot_haptic),
                            subtitle = stringResource(R.string.app_switcher_slot_haptic_desc),
                            checked = localDisplay.slotHaptic,
                            enabled = true,
                            onCheckedChange = { update(localDisplay.copy(slotHaptic = it)) },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "section-background",
            title = backgroundSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "app_switcher_background",
            items = buildList {
                add(
                    settingsCardScopeItem("dim") {
                        SettingsSliderRow(
                            title = stringResource(R.string.app_switcher_dim_percent),
                            value = localDisplay.dimPercent.toFloat(),
                            valueRange = AppSwitcherDisplaySettings.MIN_DIM_PERCENT.toFloat()..
                                AppSwitcherDisplaySettings.MAX_DIM_PERCENT.toFloat(),
                            enabled = true,
                            label = "${localDisplay.dimPercent}%",
                            keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_100,
                            onValueChange = { update(localDisplay.copy(dimPercent = it.roundToInt())) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("blur") {
                        SettingsSliderRow(
                            title = stringResource(R.string.app_switcher_blur_dp),
                            value = localDisplay.blurDp.toFloat(),
                            valueRange = AppSwitcherDisplaySettings.MIN_BLUR_DP.toFloat()..
                                AppSwitcherDisplaySettings.MAX_BLUR_DP.toFloat(),
                            steps = AppSwitcherDisplaySettings.MAX_BLUR_DP - AppSwitcherDisplaySettings.MIN_BLUR_DP,
                            enabled = true,
                            label = "${localDisplay.blurDp}dp",
                            onValueChange = { update(localDisplay.copy(blurDp = it.roundToInt())) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("hint-icon-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.app_switcher_selected_hint_icon_size),
                            value = localDisplay.selectedHintIconSizeDp.toFloat(),
                            valueRange = SelectedHintMetrics.MIN_ICON_SIZE_DP.toFloat()..
                                SelectedHintMetrics.MAX_ICON_SIZE_DP.toFloat(),
                            steps = SelectedHintMetrics.MAX_ICON_SIZE_DP - SelectedHintMetrics.MIN_ICON_SIZE_DP - 1,
                            enabled = localDisplay.showSelectedName,
                            label = "${localDisplay.selectedHintIconSizeDp}dp",
                            onValueChange = {
                                update(
                                    localDisplay.copy(
                                        selectedHintIconSizeDp = SelectedHintMetrics.clampIconSizeDp(it.roundToInt()),
                                    ),
                                )
                            },
                        )
                    },
                )
            },
        )
    }
}
