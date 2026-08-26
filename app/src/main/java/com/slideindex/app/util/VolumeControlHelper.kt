package com.slideindex.app.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

object VolumeControlHelper {
    enum class Stream {
        MEDIA,
        RING,
        NOTIFICATION,
        ALARM,
    }

    fun hasAccess(context: Context): Boolean =
        PermissionHelper.hasNotificationPolicyAccess(context.applicationContext)

    fun readRingerMode(context: Context): Int {
        val manager = audioManager(context) ?: return AudioManager.RINGER_MODE_NORMAL
        return manager.ringerMode
    }

    fun cycleRingerMode(context: Context): Int? {
        if (!hasAccess(context)) return null
        val manager = audioManager(context) ?: return null
        val nextMode = when (manager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return runCatching {
            manager.ringerMode = nextMode
            nextMode
        }.getOrElse { error ->
            Log.w(TAG, "cycle ringer mode failed", error)
            null
        }
    }

    fun readInterruptionFilter(context: Context): Int {
        if (!hasAccess(context)) return NotificationManager.INTERRUPTION_FILTER_ALL
        val manager = notificationManager(context) ?: return NotificationManager.INTERRUPTION_FILTER_ALL
        return manager.currentInterruptionFilter
    }

    fun isDndEnabled(context: Context): Boolean = isDndFilter(readInterruptionFilter(context))

    fun isDndFilter(filter: Int): Boolean = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL,
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
        -> false
        else -> true
    }

    fun toggleDnd(context: Context): Int? {
        if (!hasAccess(context)) return null
        val manager = notificationManager(context) ?: return null
        return runCatching {
            val next = if (isDndEnabled(context)) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            }
            manager.setInterruptionFilter(next)
            next
        }.getOrElse { error ->
            Log.w(TAG, "toggle dnd failed", error)
            null
        }
    }

    fun readFraction(context: Context, stream: Stream): Float {
        val manager = audioManager(context) ?: return 0f
        val audioStream = toAudioStream(stream)
        val max = manager.getStreamMaxVolume(audioStream)
        if (max <= 0) return 0f
        return manager.getStreamVolume(audioStream).toFloat() / max
    }

    fun setFraction(context: Context, stream: Stream, fraction: Float) {
        if (stream.requiresPolicyAccess() && !hasAccess(context)) return
        val manager = audioManager(context) ?: return
        val audioStream = toAudioStream(stream)
        val max = manager.getStreamMaxVolume(audioStream)
        if (max <= 0) return
        val level = (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
        if (level == manager.getStreamVolume(audioStream)) return
        manager.setStreamVolume(audioStream, level, 0)
    }

    fun toAudioStream(stream: Stream): Int = when (stream) {
        Stream.MEDIA -> AudioManager.STREAM_MUSIC
        Stream.RING -> AudioManager.STREAM_RING
        Stream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        Stream.ALARM -> AudioManager.STREAM_ALARM
    }

    private fun Stream.requiresPolicyAccess(): Boolean =
        this == Stream.RING || this == Stream.NOTIFICATION

    private fun audioManager(context: Context): AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private fun notificationManager(context: Context): NotificationManager? =
        context.applicationContext.getSystemService(NotificationManager::class.java)

    private const val TAG = "VolumeControlHelper"
}
