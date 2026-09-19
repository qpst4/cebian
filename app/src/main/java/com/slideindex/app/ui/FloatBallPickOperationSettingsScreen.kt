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



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun FloatBallPickOperationSettingsScreen(

    settings: AppSettings,

    accessibilityGranted: Boolean,

    onPickOffsetChange: (Float) -> Unit,

    onPickCrossArmChange: (Float) -> Unit,

    onDragPasteEnabledChange: (Boolean) -> Unit,

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

        title = stringResource(R.string.float_ball_pick_word_config_title),

        pageHint = stringResource(R.string.float_ball_pick_operation_page_hint),

        onBack = onBack,

    ) {

        groupedCardItems(

            keyPrefix = "fb-pick-operation",

            items = buildList {

                add(

                    settingsCardScopeItem("drag-paste") {

                        SettingSwitchRow(

                            title = stringResource(R.string.float_ball_drag_paste_enabled),

                            subtitle = stringResource(R.string.float_ball_drag_paste_enabled_desc),

                            checked = settings.floatBallDragPasteEnabled,

                            enabled = accessibilityGranted,

                            onCheckedChange = onDragPasteEnabledChange,

                        )

                    },

                )

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

                            label = stringResource(R.string.float_ball_size_value, settings.floatBallPickOffsetDp),

                            onValueChange = onPickOffsetChange,

                        )

                    },

                )

            },

        )

        settingsLazySmallTitle(

            key = "pick-advanced-section",

            title = advancedPickSectionTitle,

        )

        floatBallPickAdvancedSettingsGroup(

            keyPrefix = "fb-pick-advanced",

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


