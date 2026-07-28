package com.slideindex.app.floatball

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec

/** 悬浮球可配置手势类型（参考 FooView）。 */
enum class FloatBallGestureType(val id: Int) {
    SWIPE_UP_SHORT(0),
    SWIPE_DOWN_SHORT(1),
    SWIPE_DOWN_LONG(2),
    SWIPE_SIDE_SHORT(3),
    SWIPE_SIDE_LONG(4),
    SINGLE_TAP(5),
    DOUBLE_TAP(6),
    LONG_PRESS(7),
    SWIPE_UP_LONG(8),
    ;

    companion object {
        fun fromId(id: Int): FloatBallGestureType? = entries.firstOrNull { it.id == id }
    }
}

object FloatBallGestureCodec {
    private const val SEP = "\u001E"

    fun encode(type: FloatBallGestureType, action: GestureAction): String =
        "${type.id}$SEP${QuickLauncherItemCodec.encodeActionPayload(action)}"

    fun decode(raw: String): Pair<FloatBallGestureType, GestureAction>? {
        val index = raw.indexOf(SEP)
        if (index <= 0) return null
        val type = FloatBallGestureType.fromId(raw.substring(0, index).toIntOrNull() ?: return null)
            ?: return null
        val action = QuickLauncherItemCodec.parseActionPayload(raw.substring(index + 1))
            ?: return null
        return type to action
    }

    fun encodeAll(actions: Map<FloatBallGestureType, GestureAction>): Set<String> =
        actions.map { (type, action) -> encode(type, action) }.toSet()

    fun decodeAll(raw: Set<String>): Map<FloatBallGestureType, GestureAction> =
        raw.mapNotNull { decode(it) }.toMap()

    fun defaultActions(): Map<FloatBallGestureType, GestureAction> = mapOf(
        FloatBallGestureType.SWIPE_UP_SHORT to GestureAction.None,
        FloatBallGestureType.SWIPE_DOWN_SHORT to GestureAction.None,
        FloatBallGestureType.SWIPE_DOWN_LONG to GestureAction.None,
        FloatBallGestureType.SWIPE_SIDE_SHORT to GestureAction.Back,
        FloatBallGestureType.SWIPE_SIDE_LONG to GestureAction.Back,
        FloatBallGestureType.SINGLE_TAP to GestureAction.ClickPassthrough,
        FloatBallGestureType.DOUBLE_TAP to GestureAction.None,
        FloatBallGestureType.LONG_PRESS to GestureAction.None,
        FloatBallGestureType.SWIPE_UP_LONG to GestureAction.None,
    )
}
