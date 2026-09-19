package com.slideindex.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerBounds
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import kotlin.math.roundToInt

fun floatBallPickAdvancedCardItems(
    settings: AppSettings,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPointerSpeedVerticalChange: (Float) -> Unit,
    onPointerSlopChange: (Float) -> Unit,
    onHoverPauseDelayMsChange: (Int) -> Unit,
    onRegionalCancelSlopDpChange: (Float) -> Unit,
): List<CardItem> = listOf(
    settingsCardScopeItem("bottom-transition") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pick_bottom_transition),
            value = settings.floatBallPickBottomTransitionFraction,
            valueRange = 0.05f..0.22f,
            steps = 8,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatBallPickBottomTransitionFraction * 100).roundToInt(),
            ),
            onValueChange = onPickBottomTransitionChange,
        )
    },
    settingsCardScopeItem("pointer-speed") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pointer_speed),
            value = settings.floatBallPointerSpeedFraction,
            valueRange = FloatingPointerBounds.SENSITIVITY_MIN..FloatingPointerBounds.SENSITIVITY_MAX,
            steps = 10,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatBallPointerSpeedFraction * 100).roundToInt(),
            ),
            onValueChange = onPointerSpeedChange,
        )
    },
    settingsCardScopeItem("pointer-speed-vertical") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pointer_speed_vertical),
            value = settings.floatBallPointerSpeedVerticalFraction,
            valueRange = FloatingPointerBounds.SENSITIVITY_MIN..FloatingPointerBounds.SENSITIVITY_MAX,
            steps = 10,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatBallPointerSpeedVerticalFraction * 100).roundToInt(),
            ),
            onValueChange = onPointerSpeedVerticalChange,
        )
    },
    settingsCardScopeItem("pointer-slop") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pointer_slop),
            value = settings.floatBallPointerSlopDp,
            valueRange = 4f..32f,
            steps = 6,
            enabled = true,
            label = stringResource(R.string.float_ball_size_value, settings.floatBallPointerSlopDp),
            onValueChange = onPointerSlopChange,
        )
    },
    settingsCardScopeItem("hover-pause-delay") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_hover_pause_delay),
            value = settings.floatBallHoverPauseDelayMs.toFloat(),
            valueRange = 200f..1000f,
            steps = 15,
            enabled = true,
            label = stringResource(
                R.string.float_ball_pick_panel_animation_ms_value,
                settings.floatBallHoverPauseDelayMs,
            ),
            onValueChange = { onHoverPauseDelayMsChange(it.roundToInt()) },
        )
    },
    settingsCardScopeItem("regional-cancel-slop") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_regional_cancel_slop),
            value = settings.floatBallRegionalCancelSlopDp,
            valueRange = 3f..30f,
            steps = 26,
            enabled = true,
            label = stringResource(
                R.string.float_ball_size_value,
                settings.floatBallRegionalCancelSlopDp,
            ),
            onValueChange = onRegionalCancelSlopDpChange,
        )
    },
)

fun LazyListScope.floatBallPickAdvancedSettingsGroup(
    keyPrefix: String,
    settings: AppSettings,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPointerSpeedVerticalChange: (Float) -> Unit,
    onPointerSlopChange: (Float) -> Unit,
    onHoverPauseDelayMsChange: (Int) -> Unit,
    onRegionalCancelSlopDpChange: (Float) -> Unit,
) {
    groupedCardItems(
        keyPrefix = keyPrefix,
        items = floatBallPickAdvancedCardItems(
            settings = settings,
            onPickBottomTransitionChange = onPickBottomTransitionChange,
            onPointerSpeedChange = onPointerSpeedChange,
            onPointerSpeedVerticalChange = onPointerSpeedVerticalChange,
            onPointerSlopChange = onPointerSlopChange,
            onHoverPauseDelayMsChange = onHoverPauseDelayMsChange,
            onRegionalCancelSlopDpChange = onRegionalCancelSlopDpChange,
        ),
    )
}
