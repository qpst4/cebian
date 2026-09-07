package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide

object SideGestureDefaults {
    fun rulesFor(side: PanelSide): List<GestureRule> = when (side) {
        PanelSide.BOTTOM -> listOf(
            slotRule(side, GestureTriggerType.SHORT_SWIPE_IN, GestureAction.Home),
            slotRule(side, GestureTriggerType.LONG_SWIPE_IN, GestureAction.Recents),
            slotRule(side, GestureTriggerType.SHORT_SINGLE_TAP, GestureAction.ClickPassthrough),
        )
        PanelSide.TOP -> listOf(
            slotRule(side, GestureTriggerType.SHORT_SINGLE_TAP, GestureAction.ClickPassthrough),
        )
        else -> listOf(
            slotRule(side, GestureTriggerType.SHORT_SINGLE_TAP, GestureAction.ClickPassthrough),
            slotRule(side, GestureTriggerType.SHORT_SWIPE_IN, GestureAction.Back),
            slotRule(side, GestureTriggerType.LONG_SWIPE_IN, GestureAction.Back),
            slotRule(
                side,
                GestureTriggerType.SHORT_SWIPE_UP,
                GestureAction.OpenIndex,
                triggerMode = GestureTriggerMode.CONTINUOUS,
            ),
            slotRule(
                side,
                GestureTriggerType.SHORT_SWIPE_DOWN,
                GestureAction.OpenIndex,
                triggerMode = GestureTriggerMode.CONTINUOUS,
            ),
            slotRule(
                side,
                GestureTriggerType.LONG_SWIPE_UP,
                GestureAction.OpenIndex,
                triggerMode = GestureTriggerMode.CONTINUOUS,
            ),
            slotRule(
                side,
                GestureTriggerType.LONG_SWIPE_DOWN,
                GestureAction.QuickLauncher(),
            ),
            slotRule(side, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT, GestureAction.TaskSwitcher),
        )
    }

    fun defaultRules(): List<GestureRule> = listOf(
        PanelSide.LEFT,
        PanelSide.RIGHT,
        PanelSide.BOTTOM,
        PanelSide.TOP,
    ).flatMap { rulesFor(it) }

    private fun slotRule(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        priority: Int = 0,
        triggerMode: GestureTriggerMode = GestureTriggerMode.DEFAULT,
    ): GestureRule = GestureRule(
        id = GestureRule.slotId(side, trigger, TriggerHandle.DEFAULT_ID),
        side = side,
        trigger = trigger,
        action = action,
        priority = priority,
        enabled = action.isEffective(),
        triggerMode = triggerMode,
        handleId = TriggerHandle.DEFAULT_ID,
    )
}
