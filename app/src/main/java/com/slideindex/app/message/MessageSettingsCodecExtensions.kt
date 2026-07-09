package com.slideindex.app.message

fun MessageSettingsCodec.applyGestureActions(
    settings: MessageSettings,
    raw: Set<String>,
): MessageSettings {
    val decoded = decodeGestureActions(raw)
    return settings.copy(
        singleTapAction = decoded[SLOT_TAP] ?: settings.singleTapAction,
        swipeUpAction = decoded[SLOT_UP] ?: settings.swipeUpAction,
        swipeDownAction = decoded[SLOT_DOWN] ?: settings.swipeDownAction,
        swipeLeftAction = decoded[SLOT_LEFT] ?: settings.swipeLeftAction,
        swipeRightAction = decoded[SLOT_RIGHT] ?: settings.swipeRightAction,
    )
}

fun MessageSettingsCodec.encodeAllGestureActions(settings: MessageSettings): Set<String> = setOf(
    encodeGestureAction(SLOT_TAP, settings.singleTapAction),
    encodeGestureAction(SLOT_UP, settings.swipeUpAction),
    encodeGestureAction(SLOT_DOWN, settings.swipeDownAction),
    encodeGestureAction(SLOT_LEFT, settings.swipeLeftAction),
    encodeGestureAction(SLOT_RIGHT, settings.swipeRightAction),
)
