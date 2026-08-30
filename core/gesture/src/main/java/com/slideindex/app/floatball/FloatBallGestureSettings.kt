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
    SWIPE_SIDE_RETURN(9),
    SWIPE_UP_RETURN(10),
    SWIPE_DOWN_RETURN(11),
    ;

    val isReturnGesture: Boolean
        get() = this == SWIPE_SIDE_RETURN || this == SWIPE_UP_RETURN || this == SWIPE_DOWN_RETURN

    companion object {
        fun fromId(id: Int): FloatBallGestureType? = entries.firstOrNull { it.id == id }

        /** 设置页展示顺序：方向（下→上→侧）短/长/返回，再点击类。 */
        fun settingsDisplayOrder(): List<FloatBallGestureType> = listOf(
            SWIPE_DOWN_SHORT,
            SWIPE_DOWN_LONG,
            SWIPE_DOWN_RETURN,
            SWIPE_UP_SHORT,
            SWIPE_UP_LONG,
            SWIPE_UP_RETURN,
            SWIPE_SIDE_SHORT,
            SWIPE_SIDE_LONG,
            SWIPE_SIDE_RETURN,
            SINGLE_TAP,
            DOUBLE_TAP,
            LONG_PRESS,
        )
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
        FloatBallGestureType.SWIPE_DOWN_SHORT to GestureAction.Recents,
        FloatBallGestureType.SWIPE_DOWN_LONG to GestureAction.OpenNotifications,
        FloatBallGestureType.SWIPE_DOWN_RETURN to GestureAction.None,
        FloatBallGestureType.SWIPE_UP_LONG to GestureAction.StashPanel,
        FloatBallGestureType.SWIPE_UP_RETURN to GestureAction.None,
        FloatBallGestureType.SWIPE_SIDE_SHORT to GestureAction.Back,
        FloatBallGestureType.SWIPE_SIDE_LONG to GestureAction.Back,
        FloatBallGestureType.SWIPE_SIDE_RETURN to GestureAction.None,
        FloatBallGestureType.SINGLE_TAP to GestureAction.ClickPassthrough,
        FloatBallGestureType.DOUBLE_TAP to GestureAction.None,
        FloatBallGestureType.LONG_PRESS to GestureAction.AppSwitcher,
    )
}
