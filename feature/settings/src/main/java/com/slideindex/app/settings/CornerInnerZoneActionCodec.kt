package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec

object CornerInnerZoneActionCodec {
    fun decode(payload: String?, legacyId: Int?): GestureAction {
        if (!payload.isNullOrBlank()) {
            return QuickLauncherItemCodec.parseActionPayload(payload)
                ?.let(::sanitize)
                ?: GestureAction.CornerInnerCancel
        }
        return when (legacyId) {
            1 -> GestureAction.CornerInnerPinWheel
            2 -> GestureAction.LaunchAssistant
            else -> GestureAction.CornerInnerCancel
        }
    }

    fun encode(action: GestureAction): String =
        QuickLauncherItemCodec.encodeActionPayload(sanitize(action))

    fun sanitize(action: GestureAction): GestureAction = when (action) {
        is GestureAction.FloatingPointer,
        is GestureAction.OpenFloatingPointerRadialMenu,
        is GestureAction.None,
        -> GestureAction.CornerInnerCancel
        else -> action
    }
}
