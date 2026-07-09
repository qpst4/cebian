package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide

object SideGestureDefaults {
    fun rulesFor(side: PanelSide): List<GestureRule> = listOf(
        slotRule(side, GestureTriggerType.SHORT_SWIPE_UP, GestureAction.OpenIndex, "default-index-up-short"),
        slotRule(side, GestureTriggerType.SHORT_SWIPE_DOWN, GestureAction.OpenIndex, "default-index-down-short"),
        slotRule(side, GestureTriggerType.LONG_SWIPE_UP, GestureAction.OpenIndex, "default-index-up-long"),
        slotRule(
            side,
            GestureTriggerType.LONG_SWIPE_DOWN,
            GestureAction.QuickLauncher,
            "default-quick-down-long",
            triggerMode = GestureTriggerMode.CONTINUOUS,
        ),
        slotRule(side, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT, GestureAction.TaskSwitcher, "default-task-down-right-long"),
    )

    private fun slotRule(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        id: String,
        priority: Int = 0,
        triggerMode: GestureTriggerMode = GestureTriggerMode.DEFAULT,
    ): GestureRule = GestureRule(
        id = "$id-${side.name.lowercase()}",
        side = side,
        trigger = trigger,
        action = action,
        priority = priority,
        enabled = action.isEffective(),
        triggerMode = triggerMode,
        handleId = TriggerHandle.DEFAULT_ID,
    )
}
