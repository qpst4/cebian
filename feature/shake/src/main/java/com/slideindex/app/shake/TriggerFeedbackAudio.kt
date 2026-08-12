package com.slideindex.app.shake

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** 扣桌手势触发成功时的短促音频反馈（走闹钟音量通道）。 */
object TriggerFeedbackAudio {
    const val TONE_DURATION_MS = 150
    private const val TONE_RELEASE_BUFFER_MS = 30L
    private const val EXECUTE_DELAY_BUFFER_MS = 40L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 提示音播放时长 + 缓冲，供静音类动作延迟执行。 */
    fun tonePlayBlockMs(): Long = TONE_DURATION_MS + EXECUTE_DELAY_BUFFER_MS

    /**
     * @param forceAudible 扣桌等场景：响铃静音时也播放（固定走 [AudioManager.STREAM_ALARM]）。
     * @param volume App 内反馈音强度（0–100），仅作用于 [ToneGenerator] 音量参数。
     */
    fun playActionAck(
        context: Context,
        forceAudible: Boolean = false,
        volume: Int = FaceDownGestureSettings.DEFAULT_AUDIO_FEEDBACK_VOLUME,
    ) {
        val toneVolume = FaceDownGestureSettings.clampAudioFeedbackVolume(volume)
        if (toneVolume <= 0) return

        val audioManager = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (!forceAudible && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return

        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, toneVolume)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_DURATION_MS)
            mainHandler.postDelayed({ tone.release() }, TONE_DURATION_MS + TONE_RELEASE_BUFFER_MS)
        }
    }
}
