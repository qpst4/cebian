package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings

object SideGestureDefaults {
    fun rulesFor(side: PanelSide): List<GestureRule> = listOf(
        slotRule(side, GestureTriggerType.SHORT_SWIPE_UP, GestureAction.OpenIndex, "default-index-up-short"),
        slotRule(side, GestureTriggerType.SHORT_SWIPE_DOWN, GestureAction.OpenIndex, "default-index-down-short"),
        slotRule(side, GestureTriggerType.LONG_SWIPE_UP, GestureAction.OpenIndex, "default-index-up-long"),
        slotRule(side, GestureTriggerType.LONG_SWIPE_DOWN, GestureAction.QuickLauncher, "default-quick-down-long"),
        slotRule(side, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT, GestureAction.TaskSwitcher, "default-task-down-right-long"),
    )

    private fun slotRule(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        id: String,
        priority: Int = 0,
    ): GestureRule = GestureRule(
        id = "$id-${side.name.lowercase()}",
        side = side,
        trigger = trigger,
        action = action,
        priority = priority,
        enabled = action.isEffective(),
    )
}

fun AppSettings.effectiveRule(side: PanelSide, trigger: GestureTriggerType): GestureRule? {
    val custom = gestureRules
        .filter { it.enabled && it.side == side && it.trigger == trigger }
        .maxByOrNull { it.priority }
    if (custom != null) return custom
    return SideGestureDefaults.rulesFor(side)
        .firstOrNull { it.trigger == trigger && it.action.isEffective() }
}

fun AppSettings.actionFor(side: PanelSide, trigger: GestureTriggerType): GestureAction {
    return effectiveRule(side, trigger)?.action ?: GestureAction.None
}

fun AppSettings.slotTriggerMode(side: PanelSide, trigger: GestureTriggerType): GestureTriggerMode {
    return gestureRules.firstOrNull { it.id == GestureRule.slotId(side, trigger) }?.triggerMode
        ?: GestureTriggerMode.DEFAULT
}

fun AppSettings.resolvedTriggerMode(side: PanelSide, trigger: GestureTriggerType): GestureTriggerMode {
    val customMode = slotTriggerMode(side, trigger)
    if (customMode != GestureTriggerMode.DEFAULT) return customMode
    val action = actionFor(side, trigger)
    if (trigger.supportsIndex && action is GestureAction.OpenIndex) {
        return GestureTriggerMode.CONTINUOUS
    }
    return defaultTriggerModeFor(side)
}

fun AppSettings.defaultTriggerModeFor(side: PanelSide): GestureTriggerMode =
    when (side) {
        PanelSide.LEFT -> leftDefaultTriggerMode
        PanelSide.RIGHT -> rightDefaultTriggerMode
    }
