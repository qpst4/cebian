package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.settings.AppSettings

data class GestureRule(
    val id: String,
    val side: PanelSide,
    val trigger: GestureTriggerType,
    val action: GestureAction,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val triggerMode: GestureTriggerMode = GestureTriggerMode.DEFAULT,
    val handleId: String = TriggerHandle.DEFAULT_ID,
) {
    companion object {
        fun slot(
            side: PanelSide,
            trigger: GestureTriggerType,
            action: GestureAction,
            triggerMode: GestureTriggerMode = GestureTriggerMode.DEFAULT,
            handleId: String = TriggerHandle.DEFAULT_ID,
        ): GestureRule = GestureRule(
            id = slotId(side, trigger, handleId),
            side = side,
            trigger = trigger,
            action = action,
            priority = 0,
            enabled = true,
            triggerMode = triggerMode,
            handleId = handleId,
        )

        fun slotId(
            side: PanelSide,
            trigger: GestureTriggerType,
            handleId: String = TriggerHandle.DEFAULT_ID,
        ): String = "slot-${side.name.lowercase()}-$handleId-${trigger.id}"

        fun legacySlotId(side: PanelSide, trigger: GestureTriggerType): String =
            "slot-${side.name.lowercase()}-${trigger.id}"

        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}

object GestureRuleCodec {
    private const val SEP = "\u001F"

    fun encode(rule: GestureRule): String {
        return listOf(
            rule.id,
            rule.side.ordinal.toString(),
            rule.handleId,
            rule.trigger.id.toString(),
            rule.action.type.id.toString(),
            rule.action.payload,
            rule.priority.toString(),
            if (rule.enabled) "1" else "0",
            rule.triggerMode.id.toString(),
        ).joinToString(SEP)
    }

    fun decode(raw: String): GestureRule? {
        val parts = raw.split(SEP)
        return when (parts.size) {
            9 -> decodeNew(parts)
            8 -> decodeLegacy(parts)
            7 -> decodeLegacy(parts, hasTriggerMode = false)
            else -> null
        }
    }

    private fun decodeNew(parts: List<String>): GestureRule? {
        val side = PanelSide.entries.getOrNull(parts[1].toIntOrNull() ?: return null) ?: return null
        val handleId = parts[2]
        val trigger = GestureTriggerType.fromId(parts[3].toIntOrNull() ?: return null) ?: return null
        val actionType = GestureActionType.fromId(parts[4].toIntOrNull() ?: return null)
        val triggerMode = GestureTriggerMode.fromId(parts[8].toIntOrNull() ?: return null)
        return GestureRule(
            id = parts[0],
            side = side,
            trigger = trigger,
            action = GestureAction.from(actionType, parts[5]),
            priority = parts[6].toIntOrNull() ?: 0,
            enabled = parts[7] == "1",
            triggerMode = triggerMode,
            handleId = handleId,
        )
    }

    private fun decodeLegacy(parts: List<String>, hasTriggerMode: Boolean = true): GestureRule? {
        val side = PanelSide.entries.getOrNull(parts[1].toIntOrNull() ?: return null) ?: return null
        val trigger = GestureTriggerType.fromId(parts[2].toIntOrNull() ?: return null) ?: return null
        val actionType = GestureActionType.fromId(parts[3].toIntOrNull() ?: return null)
        val triggerMode = if (hasTriggerMode) {
            GestureTriggerMode.fromId(parts[7].toIntOrNull() ?: return null)
        } else {
            GestureTriggerMode.DEFAULT
        }
        return GestureRule(
            id = parts[0],
            side = side,
            trigger = trigger,
            action = GestureAction.from(actionType, parts[4]),
            priority = parts[5].toIntOrNull() ?: 0,
            enabled = parts[6] == "1",
            triggerMode = triggerMode,
            handleId = TriggerHandle.DEFAULT_ID,
        )
    }

    fun encodeAll(rules: List<GestureRule>): Set<String> = rules.map { encode(it) }.toSet()

    fun decodeAll(raw: Set<String>): List<GestureRule> =
        raw.mapNotNull { decode(it) }.sortedByDescending { it.priority }
}

fun AppSettings.rulesForSide(side: PanelSide): List<GestureRule> =
    gestureRules.filter { it.enabled && it.side == side }.sortedByDescending { it.priority }

fun AppSettings.withSlotAction(
    side: PanelSide,
    trigger: GestureTriggerType,
    action: GestureAction,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val slotId = GestureRule.slotId(side, trigger, handleId)
    val existing = gestureRules.firstOrNull { it.id == slotId }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }
        } else {
            null
        }
    val others = gestureRules.filterNot { it.id == slotId || it.id == existing?.id }
    if (action.type == GestureActionType.NONE) {
        if (existing?.triggerMode == GestureTriggerMode.DEFAULT || existing?.triggerMode == null) {
            return copy(gestureRules = others)
        }
        return copy(
            gestureRules = others + GestureRule(
                id = slotId,
                side = side,
                trigger = trigger,
                action = GestureAction.None,
                triggerMode = existing.triggerMode,
                handleId = handleId,
            ),
        )
    }
    return copy(
        gestureRules = others + GestureRule(
            id = slotId,
            side = side,
            trigger = trigger,
            action = action,
            triggerMode = existing?.triggerMode ?: GestureTriggerMode.DEFAULT,
            handleId = handleId,
        ),
    )
}

fun AppSettings.withSlotTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    triggerMode: GestureTriggerMode,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val slotId = GestureRule.slotId(side, trigger, handleId)
    val existing = gestureRules.firstOrNull { it.id == slotId }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }
        } else {
            null
        }
    val others = gestureRules.filterNot { it.id == slotId || it.id == existing?.id }
    val action = existing?.action ?: actionFor(side, trigger, handleId)
    if (triggerMode == GestureTriggerMode.DEFAULT &&
        (existing == null || existing.action.type == GestureActionType.NONE) &&
        action.type == GestureActionType.NONE
    ) {
        return copy(gestureRules = others)
    }
    if (triggerMode == GestureTriggerMode.DEFAULT && existing != null &&
        existing.action.type != GestureActionType.NONE
    ) {
        return copy(
            gestureRules = others + existing.copy(triggerMode = GestureTriggerMode.DEFAULT),
        )
    }
    if (triggerMode == GestureTriggerMode.DEFAULT && existing == null) {
        return copy(gestureRules = others)
    }
    return copy(
        gestureRules = others + GestureRule(
            id = slotId,
            side = side,
            trigger = trigger,
            action = action,
            triggerMode = triggerMode,
            handleId = handleId,
        ),
    )
}

fun AppSettings.shortcutGesturesConfiguredCount(): Int =
    gestureRules.count {
        it.enabled && it.action.type == GestureActionType.LAUNCH_APP &&
            it.trigger == GestureTriggerType.SHORT_SWIPE_IN
    }
