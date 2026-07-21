package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec

data class FaceDownGestureSettings(
    val enabled: Boolean = false,
    val action: GestureAction = GestureAction.LockScreenAndSilenceRing,
    val holdDurationMs: Long = 800L,
    val requireProximity: Boolean = false,
    val cooldownMs: Long = 4_000L,
    val disableInLandscape: Boolean = false,
    val vibrationFeedbackEnabled: Boolean = true,
) {
    companion object {
        fun clampHoldDurationMs(value: Long): Long = value.coerceIn(500L, 1_500L)
        fun clampCooldownMs(value: Long): Long = value.coerceIn(2_000L, 10_000L)
    }
}

object FaceDownGestureCodec {
    fun encodeAction(action: GestureAction): String =
        QuickLauncherItemCodec.encodeActionPayload(action)

    fun decodeAction(raw: String?): GestureAction =
        raw?.let { QuickLauncherItemCodec.parseActionPayload(it) }
            ?: GestureAction.LockScreenAndSilenceRing
}
