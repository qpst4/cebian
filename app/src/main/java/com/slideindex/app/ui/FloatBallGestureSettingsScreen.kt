package com.slideindex.app.ui



import androidx.compose.foundation.layout.size

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.floatball.FloatBallGestureType

import com.slideindex.app.gesture.GestureAction

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.ui.miuix.groupedCardItems

import com.slideindex.app.ui.settings.components.SettingNavigationRow

import com.slideindex.app.ui.settings.components.SettingsCardScope

import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

import com.slideindex.app.ui.settings.components.settingsCardScopeItem

import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

import kotlin.math.roundToInt



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun FloatBallGestureSettingsScreen(

    settings: AppSettings,

    accessibilityGranted: Boolean,

    onBack: () -> Unit,

    onOpenActionPick: (FloatBallGestureType) -> Unit,

    onOpenShellCommand: (FloatBallGestureType, String) -> Unit,

    onDownSwipeShortPercentChange: (Float) -> Unit,

    onSideSwipeShortPercentChange: (Float) -> Unit,

    onUpSwipeShortPercentChange: (Float) -> Unit,

) {

    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted



    val distanceSectionTitle = stringResource(R.string.float_ball_gesture_distance_section)

    val actionsSectionTitle = stringResource(R.string.float_ball_gesture_actions_section)



    SettingsScreenScaffold(

        title = stringResource(R.string.float_ball_gesture_settings_title),

        onBack = onBack,

    ) {

        settingsLazySmallTitle(

            key = "section-distance",

            title = distanceSectionTitle,

            sectionTop = true,

        )

        groupedCardItems(

            keyPrefix = "float-ball-gesture-distance",

            items = buildList {

                add(

                    settingsCardScopeItem("down-swipe-distance") {

                        SettingsSliderRow(

                            title = stringResource(R.string.float_ball_gesture_down_swipe_distance),

                            value = settings.floatBallDownSwipeShortPercent,

                            valueRange = 50f..500f,

                            steps = 18,

                            enabled = controlsEnabled,

                            label = stringResource(

                                R.string.floating_pointer_percent_value,

                                settings.floatBallDownSwipeShortPercent.roundToInt(),

                            ),

                            onValueChange = onDownSwipeShortPercentChange,

                        )

                    },

                )

                add(

                    settingsCardScopeItem("side-swipe-distance") {

                        SettingsSliderRow(

                            title = stringResource(R.string.float_ball_gesture_side_swipe_distance),

                            value = settings.floatBallSideSwipeShortPercent,

                            valueRange = 50f..500f,

                            steps = 18,

                            enabled = controlsEnabled,

                            label = stringResource(

                                R.string.floating_pointer_percent_value,

                                settings.floatBallSideSwipeShortPercent.roundToInt(),

                            ),

                            onValueChange = onSideSwipeShortPercentChange,

                        )

                    },

                )

                add(

                    settingsCardScopeItem("up-swipe-distance") {

                        SettingsSliderRow(

                            title = stringResource(R.string.float_ball_gesture_up_swipe_distance),

                            value = settings.floatBallUpSwipeShortPercent,

                            valueRange = 50f..500f,

                            steps = 18,

                            enabled = controlsEnabled,

                            label = stringResource(

                                R.string.floating_pointer_percent_value,

                                settings.floatBallUpSwipeShortPercent.roundToInt(),

                            ),

                            onValueChange = onUpSwipeShortPercentChange,

                        )

                    },

                )

            },

        )

        settingsLazySmallTitle(

            key = "section-actions",

            title = actionsSectionTitle,

            sectionTop = true,

        )

        groupedCardItems(

            keyPrefix = "float-ball-gesture-actions",

            items = buildList {

                FloatBallGestureType.settingsDisplayOrder().forEach { type ->

                    val action = settings.floatBallGestureActions[type] ?: GestureAction.None

                    add(

                        settingsCardScopeItem("action-${type.name}") {

                            FloatBallGestureActionRow(

                                type = type,

                                settings = settings,

                                title = floatBallGestureLabel(type),

                                action = action,

                                enabled = controlsEnabled,

                                showSettings = action is GestureAction.LaunchApp ||

                                    action is GestureAction.LaunchShortcut ||

                                    action is GestureAction.SimulatePointerSwipe ||

                                    action is GestureAction.ExecuteShellCommand,

                                onClick = { onOpenActionPick(type) },

                                onSettingsClick = if (action is GestureAction.ExecuteShellCommand) {

                                    { onOpenShellCommand(type, action.command) }

                                } else {

                                    null

                                },

                            )

                        },

                    )

                }

            },

        )

    }

}



@Composable

private fun SettingsCardScope.FloatBallGestureActionRow(

    type: FloatBallGestureType,

    settings: AppSettings,

    title: String,

    action: GestureAction,

    enabled: Boolean,

    showSettings: Boolean,

    onClick: () -> Unit,

    onSettingsClick: (() -> Unit)? = null,

) {

    SettingNavigationRow(

        icon = { label ->

            FloatBallGestureIcon(

                type = type,

                settings = settings,

                contentDescription = label,

                modifier = Modifier.size(22.dp),

            )

        },

        title = title,

        subtitle = gestureActionSettingSubtitle(action),

        enabled = enabled,

        onClick = onClick,

        trailingContent = {

            GestureActionSettingTrailing(

                action = action,

                enabled = enabled,

                showSettings = showSettings,

                onSettingsClick = onSettingsClick,

                onClick = onClick,

            )

        },

    )

}



@Composable

fun floatBallGestureLabel(type: FloatBallGestureType): String = when (type) {

    FloatBallGestureType.SWIPE_UP_SHORT -> stringResource(R.string.float_ball_gesture_swipe_up_short)

    FloatBallGestureType.SWIPE_UP_LONG -> stringResource(R.string.float_ball_gesture_swipe_up_long)

    FloatBallGestureType.SWIPE_UP_RETURN -> stringResource(R.string.float_ball_gesture_swipe_up_return)

    FloatBallGestureType.SWIPE_DOWN_SHORT -> stringResource(R.string.float_ball_gesture_swipe_down_short)

    FloatBallGestureType.SWIPE_DOWN_LONG -> stringResource(R.string.float_ball_gesture_swipe_down_long)

    FloatBallGestureType.SWIPE_DOWN_RETURN -> stringResource(R.string.float_ball_gesture_swipe_down_return)

    FloatBallGestureType.SWIPE_SIDE_SHORT -> stringResource(R.string.float_ball_gesture_swipe_side_short)

    FloatBallGestureType.SWIPE_SIDE_LONG -> stringResource(R.string.float_ball_gesture_swipe_side_long)

    FloatBallGestureType.SWIPE_SIDE_RETURN -> stringResource(R.string.float_ball_gesture_swipe_side_return)

    FloatBallGestureType.SINGLE_TAP -> stringResource(R.string.float_ball_gesture_single_tap)

    FloatBallGestureType.DOUBLE_TAP -> stringResource(R.string.float_ball_gesture_double_tap)

    FloatBallGestureType.LONG_PRESS -> stringResource(R.string.float_ball_gesture_long_press)

}


