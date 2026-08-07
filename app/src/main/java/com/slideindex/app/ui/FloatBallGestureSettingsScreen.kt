package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
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

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_gesture_settings_title),
        onBack = onBack,
    ) {
        MiuixSmallTitle(stringResource(R.string.float_ball_gesture_distance_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
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
        }
        MiuixSmallTitle(stringResource(R.string.float_ball_gesture_actions_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            FloatBallGestureType.settingsDisplayOrder().forEach { type ->
                val action = settings.floatBallGestureActions[type] ?: GestureAction.None
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
            }
        }
    }
}

@Composable
private fun FloatBallGestureActionRow(
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
    FloatBallGestureType.SWIPE_DOWN_SHORT -> stringResource(R.string.float_ball_gesture_swipe_down_short)
    FloatBallGestureType.SWIPE_DOWN_LONG -> stringResource(R.string.float_ball_gesture_swipe_down_long)
    FloatBallGestureType.SWIPE_SIDE_SHORT -> stringResource(R.string.float_ball_gesture_swipe_side_short)
    FloatBallGestureType.SWIPE_SIDE_LONG -> stringResource(R.string.float_ball_gesture_swipe_side_long)
    FloatBallGestureType.SINGLE_TAP -> stringResource(R.string.float_ball_gesture_single_tap)
    FloatBallGestureType.DOUBLE_TAP -> stringResource(R.string.float_ball_gesture_double_tap)
    FloatBallGestureType.LONG_PRESS -> stringResource(R.string.float_ball_gesture_long_press)
}
