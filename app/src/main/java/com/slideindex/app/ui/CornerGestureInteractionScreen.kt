package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CornerGestureInteractionScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onVerticalEdgeWidthChange: (Float) -> Unit,
    onVerticalEdgeHeightChange: (Float) -> Unit,
    onHorizontalEdgeWidthChange: (Float) -> Unit,
    onHorizontalEdgeHeightChange: (Float) -> Unit,
    onZonePreviewStart: () -> Unit = {},
    onZonePreviewStop: () -> Unit = {},
    onZonePreviewDimensionsChange: (
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) -> Unit = { _, _, _, _ -> },
    onTriggerSlopChange: (Float) -> Unit,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onLandscapePreventFalseTouchChange: (Boolean) -> Unit,
    onOverrideSystemNavChange: (Boolean) -> Unit,
    onOuterDiameterChange: (Float) -> Unit,
    onInnerDiameterChange: (Float) -> Unit,
    onBubbleSizeChange: (Float) -> Unit,
    onCancelOutsideWheelChange: (Boolean) -> Unit,
    onProgressiveLayersChange: (Boolean) -> Unit,
    onSlotHapticChange: (Boolean) -> Unit,
    onShowSelectedNameChange: (Boolean) -> Unit,
    onSelectedHintIconSizeChange: (Int) -> Unit,
    onBackgroundStyleChange: (Int) -> Unit,
    onBlurDpChange: (Int) -> Unit,
    onDimPercentChange: (Int) -> Unit,
) {
    val corner = settings.cornerGestureSettings

    // 下拉切换背景时立刻驱动条件滑条，避免等 DataStore 回流。
    var localBackgroundStyle by remember(corner.backgroundStyle) {
        mutableIntStateOf(corner.backgroundStyle)
    }
    var localBlurDp by remember(corner.blurDp) { mutableIntStateOf(corner.blurDp) }
    var localDimPercent by remember(corner.dimPercent) { mutableIntStateOf(corner.dimPercent) }

    val zoneControlsEnabled = serviceEnabled && corner.enabled

    var previewVerticalWidthDp by remember { mutableFloatStateOf(corner.verticalEdgeWidthDp) }
    var previewVerticalHeightDp by remember { mutableFloatStateOf(corner.verticalEdgeHeightDp) }
    var previewHorizontalWidthDp by remember { mutableFloatStateOf(corner.horizontalEdgeWidthDp) }
    var previewHorizontalHeightDp by remember { mutableFloatStateOf(corner.horizontalEdgeHeightDp) }

    LaunchedEffect(
        corner.verticalEdgeWidthDp,
        corner.verticalEdgeHeightDp,
        corner.horizontalEdgeWidthDp,
        corner.horizontalEdgeHeightDp,
    ) {
        previewVerticalWidthDp = corner.verticalEdgeWidthDp
        previewVerticalHeightDp = corner.verticalEdgeHeightDp
        previewHorizontalWidthDp = corner.horizontalEdgeWidthDp
        previewHorizontalHeightDp = corner.horizontalEdgeHeightDp
    }

    fun pushZonePreview() {
        onZonePreviewDimensionsChange(
            previewVerticalWidthDp,
            previewVerticalHeightDp,
            previewHorizontalWidthDp,
            previewHorizontalHeightDp,
        )
    }

    DisposableEffect(Unit) {
        onDispose { onZonePreviewStop() }
    }

    val triggerSectionTitle = stringResource(R.string.corner_gesture_trigger_section)
    val zonePreviewHint = stringResource(R.string.corner_gesture_zone_preview_hint)
    val behaviorSectionTitle = stringResource(R.string.corner_gesture_behavior_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.corner_gesture_interaction_appearance_title),
        subtitle = stringResource(R.string.corner_gesture_interaction_appearance_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "corner-trigger-section",
            title = triggerSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(
            key = "corner-zone-preview-hint",
            text = zonePreviewHint,
        )
        groupedCardItems(
            keyPrefix = "corner-trigger",
            items = buildList {
                add(
                    settingsCardScopeItem("vertical-edge-height") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_vertical_edge_height),
                            value = corner.verticalEdgeHeightDp,
                            valueRange = 0f..200f,
                            steps = 31,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewVerticalHeightDp = value
                                pushZonePreview()
                            },
                            onValueChange = onVerticalEdgeHeightChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("vertical-edge-width") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_vertical_edge_width),
                            value = corner.verticalEdgeWidthDp,
                            valueRange = 0f..120f,
                            steps = 21,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewVerticalWidthDp = value
                                pushZonePreview()
                            },
                            onValueChange = onVerticalEdgeWidthChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("horizontal-edge-height") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_horizontal_edge_height),
                            value = corner.horizontalEdgeHeightDp,
                            valueRange = 0f..160f,
                            steps = 23,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewHorizontalHeightDp = value
                                pushZonePreview()
                            },
                            onValueChange = onHorizontalEdgeHeightChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("horizontal-edge-width") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_horizontal_edge_width),
                            value = corner.horizontalEdgeWidthDp,
                            valueRange = 0f..160f,
                            steps = 31,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewHorizontalWidthDp = value
                                pushZonePreview()
                            },
                            onValueChange = onHorizontalEdgeWidthChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("trigger-slop") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_trigger_slop),
                            value = corner.triggerSlopDp,
                            valueRange = 24f..96f,
                            steps = 17,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(
                                R.string.corner_gesture_zone_dp_value,
                                corner.triggerSlopDp.roundToInt(),
                            ),
                            onValueChange = onTriggerSlopChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("outer-diameter") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_outer_diameter),
                            value = corner.outerDiameterDp,
                            valueRange = 180f..400f,
                            steps = 21,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(
                                R.string.corner_gesture_zone_dp_value,
                                corner.outerDiameterDp.roundToInt(),
                            ),
                            onValueChange = onOuterDiameterChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("inner-diameter") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_inner_diameter),
                            value = corner.innerDiameterDp,
                            valueRange = 40f..(corner.outerDiameterDp - 24f).coerceAtLeast(48f),
                            steps = 15,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(
                                R.string.corner_gesture_zone_dp_value,
                                corner.innerDiameterDp.roundToInt(),
                            ),
                            onValueChange = onInnerDiameterChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("bubble-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_bubble_size),
                            value = corner.bubbleSizeDp,
                            valueRange = 12f..28f,
                            steps = 15,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(
                                R.string.corner_gesture_zone_dp_value,
                                corner.bubbleSizeDp.roundToInt(),
                            ),
                            onValueChange = onBubbleSizeChange,
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "corner-behavior-section",
            title = behaviorSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "corner-behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("hide-landscape") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_hide_landscape),
                            subtitle = stringResource(R.string.corner_gesture_hide_landscape_desc),
                            checked = corner.hideInLandscape,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onHideInLandscapeChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("landscape-prevent-false-touch") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_landscape_prevent_false_touch),
                            subtitle = stringResource(R.string.corner_gesture_landscape_prevent_false_touch_desc),
                            checked = corner.landscapePreventFalseTouch,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onLandscapePreventFalseTouchChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("override-system-nav") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_override_system_nav),
                            subtitle = stringResource(R.string.corner_gesture_override_system_nav_desc),
                            checked = corner.overrideSystemNav,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onOverrideSystemNavChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("cancel-outside-wheel") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_cancel_outside_wheel),
                            subtitle = stringResource(R.string.corner_gesture_cancel_outside_wheel_desc),
                            checked = corner.cancelOutsideWheel,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onCancelOutsideWheelChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("progressive-layers") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_progressive_layers),
                            subtitle = stringResource(R.string.corner_gesture_progressive_layers_desc),
                            checked = corner.progressiveLayers,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onProgressiveLayersChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("slot-haptic") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_slot_haptic),
                            subtitle = stringResource(R.string.corner_gesture_slot_haptic_desc),
                            checked = corner.slotHapticEnabled,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onSlotHapticChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("show-selected-name") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.corner_gesture_show_selected_name),
                            subtitle = stringResource(R.string.corner_gesture_show_selected_name_desc),
                            checked = corner.showSelectedName,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onShowSelectedNameChange,
                            expandedContent = {
                                SettingsSliderRow(
                                    title = stringResource(R.string.corner_gesture_selected_hint_icon_size),
                                    value = corner.selectedHintIconSizeDp.toFloat(),
                                    valueRange = SelectedHintMetrics.MIN_ICON_SIZE_DP.toFloat()..
                                        SelectedHintMetrics.MAX_ICON_SIZE_DP.toFloat(),
                                    steps = SelectedHintMetrics.MAX_ICON_SIZE_DP - SelectedHintMetrics.MIN_ICON_SIZE_DP - 1,
                                    enabled = serviceEnabled && corner.enabled && corner.showSelectedName,
                                    label = stringResource(
                                        R.string.selected_hint_icon_size_value,
                                        corner.selectedHintIconSizeDp,
                                    ),
                                    onValueChange = { onSelectedHintIconSizeChange(it.roundToInt()) },
                                )
                            },
                        )
                    },
                )
                val backgroundStyles = listOf(
                    CornerGestureSettings.BACKGROUND_NONE,
                    CornerGestureSettings.BACKGROUND_BLUR,
                    CornerGestureSettings.BACKGROUND_BLACK,
                )
                add(
                    settingsCardScopeItem("background-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.corner_gesture_background_style),
                            items = listOf(
                                stringResource(R.string.corner_gesture_background_none),
                                stringResource(R.string.honeycomb_background_blur),
                                stringResource(R.string.honeycomb_background_black),
                            ),
                            selectedIndex = backgroundStyles.indexOf(localBackgroundStyle).coerceAtLeast(0),
                            enabled = serviceEnabled && corner.enabled,
                            onSelectedIndexChange = { index ->
                                val style = backgroundStyles[index]
                                localBackgroundStyle = style
                                onBackgroundStyleChange(style)
                            },
                        )
                    },
                )
                // 始终注册行，避免 Lazy 条件增减导致切背景后滑条不出现。
                add(
                    settingsCardScopeItem("blur-strength") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_blur_strength),
                            value = localBlurDp.toFloat(),
                            valueRange = CornerGestureSettings.MIN_BLUR_DP.toFloat()..
                                CornerGestureSettings.MAX_BLUR_DP.toFloat(),
                            steps = 16,
                            enabled = serviceEnabled && corner.enabled
                                && localBackgroundStyle == CornerGestureSettings.BACKGROUND_BLUR,
                            label = stringResource(R.string.corner_gesture_zone_dp_value, localBlurDp),
                            onValueChange = {
                                val v = it.roundToInt()
                                localBlurDp = v
                                onBlurDpChange(v)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("dim-percent") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_dim_percent),
                            value = localDimPercent.toFloat(),
                            valueRange = CornerGestureSettings.MIN_DIM_PERCENT.toFloat()..
                                CornerGestureSettings.MAX_DIM_PERCENT.toFloat(),
                            steps = 12,
                            enabled = serviceEnabled && corner.enabled
                                && localBackgroundStyle != CornerGestureSettings.BACKGROUND_NONE,
                            label = stringResource(R.string.floating_pointer_percent_value, localDimPercent),
                            onValueChange = {
                                val v = it.roundToInt()
                                localDimPercent = v
                                onDimPercentChange(v)
                            },
                        )
                    },
                )
            },
        )
    }
}
