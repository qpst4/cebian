package com.slideindex.app.message

object MessageSettingsCodec {
    private const val SEP = "\u001E"

    fun encodeGestureAction(slot: String, action: MessageAction): String =
        "$slot$SEP${action.id}"

    fun decodeGestureActions(raw: Set<String>): Map<String, MessageAction> =
        raw.mapNotNull { entry ->
            val index = entry.indexOf(SEP)
            if (index <= 0) return@mapNotNull null
            val slot = entry.substring(0, index)
            val actionId = entry.substring(index + 1).toIntOrNull() ?: return@mapNotNull null
            slot to MessageAction.fromId(actionId)
        }.toMap()

    const val SLOT_TAP = "tap"
    const val SLOT_UP = "up"
    const val SLOT_DOWN = "down"
    const val SLOT_LEFT = "left"
    const val SLOT_RIGHT = "right"
    const val SLOT_LONG_PRESS = "long_press"
}
