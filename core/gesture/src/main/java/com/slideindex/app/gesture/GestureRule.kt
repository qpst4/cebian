package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide

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
