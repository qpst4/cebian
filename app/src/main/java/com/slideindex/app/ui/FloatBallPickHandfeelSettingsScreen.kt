package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

/**
 * 「取词 → 拾取与手感」页：拾取十字几何与指针手感参数。
 *
 * 这些参数作用于多个取词入口（悬浮球拖动取词、触钮区域截图取词、悬浮指针悬停取词），
 * 属于「取词」而非悬浮球，因此归入取词设置；悬浮球页保留跳转入口以便老用户找到。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallPickHandfeelSettingsScreen(
    settings: AppSettings,
    onPickCrossArmChange: (Float) -> Unit,
    onPickOffsetChange: (Float) -> Unit,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPointerSpeedVerticalChange: (Float) -> Unit,
    onPointerSlopChange: (Float) -> Unit,
    onHoverPauseDelayMsChange: (Int) -> Unit,
    onRegionalCancelSlopDpChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    val advancedPickSectionTitle = stringResource(R.string.float_ball_pick_section_advanced)

    SettingsScreenScaffold(
        title = stringResource(R.string.pick_handfeel_settings_title),
        pageHint = stringResource(R.string.pick_handfeel_settings_page_hint),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "pick-handfeel-cross",
            items = buildList {
                add(
                    settingsCardScopeItem("cross-arm") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_pick_cross_arm),
                            value = settings.floatBallPickCrossArmDp,
                            valueRange = 4f..16f,
                            steps = 23,
                            enabled = true,
                            label = stringResource(
                                R.string.float_ball_pick_cross_arm_value,
                                settings.floatBallPickCrossArmDp,
                            ),
                            onValueChange = onPickCrossArmChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("offset") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_pick_offset),
                            value = settings.floatBallPickOffsetDp,
                            valueRange = 4f..48f,
                            steps = 10,
                            enabled = true,
                            label = stringResource(
                                R.string.float_ball_size_value,
                                settings.floatBallPickOffsetDp,
                            ),
                            onValueChange = onPickOffsetChange,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "pick-handfeel-advanced-section",
            title = advancedPickSectionTitle,
        )

        floatBallPickAdvancedSettingsGroup(
            keyPrefix = "pick-handfeel-advanced",
            settings = settings,
            onPickBottomTransitionChange = onPickBottomTransitionChange,
            onPointerSpeedChange = onPointerSpeedChange,
            onPointerSpeedVerticalChange = onPointerSpeedVerticalChange,
            onPointerSlopChange = onPointerSlopChange,
            onHoverPauseDelayMsChange = onHoverPauseDelayMsChange,
            onRegionalCancelSlopDpChange = onRegionalCancelSlopDpChange,
        )
    }
}
