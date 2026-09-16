package com.slideindex.app.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.slideindex.app.privilege.PrivilegeGateway
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

    /**
     * Toggles system Do Not Disturb.
     *
     * Uses `cmd notification set_dnd` when privileged shell access is available (Flyme/OEM
     * often ignores [NotificationManager.setInterruptionFilter]), otherwise falls back to the API.
     *
     * @return the current interruption filter after the attempt when the enabled state changed,
     *         or null when access is missing, the call failed, or the system state did not change.
     */
    fun toggleDnd(context: Context): Int? {
        if (!hasAccess(context)) return null
        val appContext = context.applicationContext
        val beforeEnabled = isDndEnabled(appContext)
        val targetFilter = if (beforeEnabled) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        val after = setInterruptionFilter(appContext, targetFilter) ?: return null
        val afterEnabled = isDndFilter(after)
        if (beforeEnabled == afterEnabled) {
            Log.w(TAG, "toggle dnd had no effect after=$after")
            return null
        }
        Log.i(TAG, "toggle dnd ok after=$after")
        return after
    }

    /**
     * Applies the requested interruption filter via shell when available, otherwise via API.
     *
     * @return the filter read back after the attempt when it matches the request or DND state,
     *         or null when access is missing or the call had no effect.
     */
    fun setInterruptionFilter(context: Context, filter: Int): Int? {
        if (!hasAccess(context)) return null
        val appContext = context.applicationContext
        if (TaskManagerUtil.hasPermission()) {
            setInterruptionFilterViaShell(appContext, filter)?.let { return it }
        }
        return setInterruptionFilterViaNotificationManager(appContext, filter)
    }

    fun ensureDndEnabled(context: Context): Boolean {
        if (isDndEnabled(context)) return true
        return setInterruptionFilter(
            context,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        ) != null
    }

    private fun setInterruptionFilterViaShell(context: Context, filter: Int): Int? {
        val beforeEnabled = isDndEnabled(context)
        val targetEnabled = isDndFilter(filter)
        val mode = shellModeForInterruptionFilter(filter)
        val result = TaskManagerUtil.runShellCommandLine(
            command = "cmd notification set_dnd $mode",
            useRoot = PrivilegeGateway.isRootMode(),
        )
        if (!result.success) {
            Log.w(
                TAG,
                "set dnd shell failed: mode=$mode filter=$filter exit=${result.exitCode} output=${result.output}",
            )
            return null
        }
        return readInterruptionFilterIfChanged(context, beforeEnabled, "shell:$mode")
            ?: readInterruptionFilter(context).takeIf { isDndFilter(it) == targetEnabled }
    }

    private fun setInterruptionFilterViaNotificationManager(context: Context, filter: Int): Int? {
        val manager = notificationManager(context) ?: return null
        return runCatching {
            val beforeEnabled = isDndEnabled(context)
            manager.setInterruptionFilter(filter)
            readInterruptionFilterIfChanged(context, beforeEnabled, "api:$filter")
                ?: readInterruptionFilter(context).takeIf { it == filter }
        }.getOrElse { error ->
            Log.w(TAG, "set dnd api failed filter=$filter", error)
            null
        }
    }

    private fun shellModeForInterruptionFilter(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "silent"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
        else -> if (isDndFilter(filter)) "priority" else "off"
    }

    private fun readInterruptionFilterIfChanged(
        context: Context,
        beforeEnabled: Boolean,
        via: String,
    ): Int? {
        val after = readInterruptionFilter(context)
        val afterEnabled = isDndFilter(after)
        if (beforeEnabled == afterEnabled) {
            Log.w(TAG, "toggle dnd had no effect via $via after=$after")
            return null
        }
        Log.i(TAG, "toggle dnd ok via $via after=$after")
        return after
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
