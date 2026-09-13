package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.KeyboardTriggerBehavior

@Composable
fun keyboardTriggerBehaviorLabel(behavior: KeyboardTriggerBehavior): String =
    when (behavior) {
        KeyboardTriggerBehavior.OVERLAY -> stringResource(R.string.keyboard_trigger_behavior_overlay)
        KeyboardTriggerBehavior.MOVE_UP -> stringResource(R.string.keyboard_trigger_behavior_move_up)
        KeyboardTriggerBehavior.NARROW -> stringResource(R.string.keyboard_trigger_behavior_narrow)
        KeyboardTriggerBehavior.DISABLE -> stringResource(R.string.keyboard_trigger_behavior_disable)
    }

@Composable
fun keyboardTriggerBehaviorDescription(behavior: KeyboardTriggerBehavior): String =
    when (behavior) {
        KeyboardTriggerBehavior.OVERLAY -> stringResource(R.string.keyboard_trigger_behavior_overlay_desc)
        KeyboardTriggerBehavior.MOVE_UP -> stringResource(R.string.keyboard_trigger_behavior_move_up_desc)
        KeyboardTriggerBehavior.NARROW -> stringResource(R.string.keyboard_trigger_behavior_narrow_desc)
        KeyboardTriggerBehavior.DISABLE -> stringResource(R.string.keyboard_trigger_behavior_disable_desc)
    }
