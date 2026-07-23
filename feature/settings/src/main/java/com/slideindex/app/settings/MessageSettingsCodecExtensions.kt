package com.slideindex.app.settings

import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettingsCodec

fun MessageSettingsCodec.applyGestureActions(
    settings: MessageSettings,
    raw: Set<String>,
): MessageSettings {
    val decoded = decodeGestureActions(raw)
    return settings.copy(
        singleTapAction = decoded[MessageSettingsCodec.SLOT_TAP] ?: settings.singleTapAction,
        swipeUpAction = decoded[MessageSettingsCodec.SLOT_UP] ?: settings.swipeUpAction,
        swipeDownAction = decoded[MessageSettingsCodec.SLOT_DOWN] ?: settings.swipeDownAction,
        swipeLeftAction = decoded[MessageSettingsCodec.SLOT_LEFT] ?: settings.swipeLeftAction,
        swipeRightAction = decoded[MessageSettingsCodec.SLOT_RIGHT] ?: settings.swipeRightAction,
        longPressAction = decoded[MessageSettingsCodec.SLOT_LONG_PRESS] ?: settings.longPressAction,
    )
}

fun MessageSettingsCodec.encodeAllGestureActions(settings: MessageSettings): Set<String> = setOf(
    encodeGestureAction(MessageSettingsCodec.SLOT_TAP, settings.singleTapAction),
    encodeGestureAction(MessageSettingsCodec.SLOT_UP, settings.swipeUpAction),
    encodeGestureAction(MessageSettingsCodec.SLOT_DOWN, settings.swipeDownAction),
    encodeGestureAction(MessageSettingsCodec.SLOT_LEFT, settings.swipeLeftAction),
    encodeGestureAction(MessageSettingsCodec.SLOT_RIGHT, settings.swipeRightAction),
    encodeGestureAction(MessageSettingsCodec.SLOT_LONG_PRESS, settings.longPressAction),
)
