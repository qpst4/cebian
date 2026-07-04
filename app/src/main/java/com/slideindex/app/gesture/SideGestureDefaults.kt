package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings

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

fun AppSettings.effectiveRule(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureRule? {
    val newSlotId = GestureRule.slotId(side, trigger, handleId)
    val custom = gestureRules
        .filter { it.enabled && it.side == side && it.trigger == trigger && it.handleId == handleId }
        .maxByOrNull { it.priority }
        ?: gestureRules.firstOrNull {
            it.enabled &&
                it.side == side &&
                it.trigger == trigger &&
                it.id == newSlotId
        }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull {
                it.enabled &&
                    it.side == side &&
                    it.trigger == trigger &&
                    it.id == GestureRule.legacySlotId(side, trigger)
            }
        } else {
            null
        }
    if (custom != null) return custom
    if (handleId != TriggerHandle.DEFAULT_ID) {
        return effectiveRule(side, trigger, TriggerHandle.DEFAULT_ID)
    }
    return SideGestureDefaults.rulesFor(side)
        .firstOrNull { it.trigger == trigger && it.action.isEffective() }
}

fun AppSettings.actionFor(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureAction {
    return effectiveRule(side, trigger, handleId)?.action ?: GestureAction.None
}

fun AppSettings.slotTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureTriggerMode {
    val newSlotId = GestureRule.slotId(side, trigger, handleId)
    return gestureRules.firstOrNull { it.id == newSlotId }?.triggerMode
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }?.triggerMode
        } else {
            null
        }
        ?: GestureTriggerMode.DEFAULT
}

fun AppSettings.resolvedTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureTriggerMode {
    val customMode = slotTriggerMode(side, trigger, handleId)
    if (customMode != GestureTriggerMode.DEFAULT) return customMode
    val ruleMode = effectiveRule(side, trigger, handleId)?.triggerMode
    if (ruleMode != null && ruleMode != GestureTriggerMode.DEFAULT) return ruleMode
    return defaultTriggerModeFor(side)
}

fun AppSettings.defaultTriggerModeFor(side: PanelSide): GestureTriggerMode =
    when (side) {
        PanelSide.LEFT -> leftDefaultTriggerMode
        PanelSide.RIGHT -> rightDefaultTriggerMode
    }
