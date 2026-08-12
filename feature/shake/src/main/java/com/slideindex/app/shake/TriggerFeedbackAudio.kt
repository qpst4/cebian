package com.slideindex.app.shake

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/** 手势触发成功时的短促音频反馈（扣桌等场景下比振动更易察觉）。 */
object TriggerFeedbackAudio {
    private const val TONE_DURATION_MS = 120

    fun playActionAck(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 72)
            try {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_DURATION_MS)
            } finally {
                tone.release()
            }
        }
    }
}
