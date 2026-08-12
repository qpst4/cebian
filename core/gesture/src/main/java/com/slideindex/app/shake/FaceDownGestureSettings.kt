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
    val audioFeedbackEnabled: Boolean = true,
    /** App 内反馈音强度（0–100），仅影响 [ToneGenerator] 音量，不改系统闹钟音量。 */
    val audioFeedbackVolume: Int = DEFAULT_AUDIO_FEEDBACK_VOLUME,
) {
    companion object {
        const val DEFAULT_AUDIO_FEEDBACK_VOLUME = 100
        const val MIN_AUDIO_FEEDBACK_VOLUME = 0
        const val MAX_AUDIO_FEEDBACK_VOLUME = 100

        fun clampHoldDurationMs(value: Long): Long = value.coerceIn(500L, 1_500L)
        fun clampCooldownMs(value: Long): Long = value.coerceIn(2_000L, 10_000L)
        fun clampAudioFeedbackVolume(value: Int): Int =
            value.coerceIn(MIN_AUDIO_FEEDBACK_VOLUME, MAX_AUDIO_FEEDBACK_VOLUME)
    }
}

object FaceDownGestureCodec {
    fun encodeAction(action: GestureAction): String =
        QuickLauncherItemCodec.encodeActionPayload(action)

    fun decodeAction(raw: String?): GestureAction =
        raw?.let { QuickLauncherItemCodec.parseActionPayload(it) }
            ?: GestureAction.LockScreenAndSilenceRing
}
