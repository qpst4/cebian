package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.slideindex.app.service.MediaNotificationListener

/**
 * Resolves the foreground media session (package + play/pause) for the quick-tools media strip.
 *
 * **Reliable path:** enable notification listener access for [MediaNotificationListener]
 * (Settings → Notification access). [MediaSessionTracker] then receives live callbacks.
 *
 * **Fallback:** Shizuku `dumpsys media_session` / `audio` / `notification` when shell is available.
 */
object MediaSessionHelper {
    data class Info(
        val packageName: String,
        val isPlaying: Boolean,
    )

    fun query(context: Context): Info? {
        MediaSessionTracker.currentSnapshot().packageName?.let { pkg ->
            val snap = MediaSessionTracker.currentSnapshot()
            return Info(pkg, snap.isPlaying)
        }
        queryViaMediaControllers(context)?.let { return it }
        if (TaskManagerUtil.hasPermission()) {
            parseMediaSessionDump(
                output = TaskManagerUtil.runShellCommandOutput("dumpsys", "media_session").output,
                selfPackage = context.packageName,
            )?.let { return it }
            parseNotificationDump(
                output = TaskManagerUtil.runShellCommandOutput("dumpsys", "notification", "--noredact").output,
                selfPackage = context.packageName,
            )?.let { return it }
            parseAudioDump(
                output = TaskManagerUtil.runShellCommandOutput("dumpsys", "audio").output,
                selfPackage = context.packageName,
            )?.let { return it }
        }
        return null
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val component = ComponentName(context, MediaNotificationListener::class.java)
        return enabled.contains(component.flattenToString(), ignoreCase = true)
    }

    fun ensureNotificationListenerConnected(context: Context) {
        if (!isNotificationListenerEnabled(context)) return
        if (MediaNotificationListener.instance != null) return
        val component = ComponentName(context, MediaNotificationListener::class.java)
        runCatching {
            NotificationListenerService.requestRebind(component)
        }
        runCatching {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    fun notificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isIgnoredMediaPackage(pkg: String, selfPackage: String): Boolean =
        pkg == selfPackage ||
            pkg == "com.android.systemui" ||
            pkg == "android" ||
            pkg.startsWith("com.android.")

    fun playbackScore(state: Int): Int = when (state) {
        PlaybackState.STATE_PLAYING -> 300
        PlaybackState.STATE_BUFFERING -> 280
        PlaybackState.STATE_PAUSED -> 200
        PlaybackState.STATE_CONNECTING -> 150
        PlaybackState.STATE_STOPPED -> 80
        else -> 0
    }

    fun isPlayingState(state: Int): Boolean =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    private fun queryViaMediaControllers(context: Context): Info? {
        if (!isNotificationListenerEnabled(context)) return null
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val component = ComponentName(context, MediaNotificationListener::class.java)
        val controllers = runCatching { manager.getActiveSessions(component) }.getOrNull()
            ?: return null
        var best: Info? = null
        var bestScore = Int.MIN_VALUE
        for (controller in controllers) {
            val pkg = controller.packageName ?: continue
            if (isIgnoredMediaPackage(pkg, context.packageName)) continue
            val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
            val score = playbackScore(state)
            if (score > bestScore) {
                bestScore = score
                best = Info(packageName = pkg, isPlaying = isPlayingState(state))
            }
        }
        return best?.takeIf { bestScore > 0 }
    }

    private fun parseMediaSessionDump(output: String, selfPackage: String): Info? {
        val sessionStates = linkedMapOf<String, Int>()
        var currentPackage: String? = null
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            PACKAGE_PATTERN.find(line)?.let { currentPackage = it.groupValues[1] }
            OWNER_PACKAGE_PATTERN.find(line)?.let { currentPackage = it.groupValues[1] }
            val pkg = currentPackage ?: return@forEach
            if (isIgnoredMediaPackage(pkg, selfPackage)) return@forEach
            val state = extractPlaybackState(line) ?: return@forEach
            val prev = sessionStates[pkg]
            if (prev == null || playbackScore(state) > playbackScore(prev)) {
                sessionStates[pkg] = state
            }
        }
        val best = sessionStates.maxByOrNull { playbackScore(it.value) } ?: return null
        if (playbackScore(best.value) <= 0) return null
        return Info(best.key, isPlayingState(best.value))
    }

    private fun parseAudioDump(output: String, selfPackage: String): Info? {
        var best: Info? = null
        var bestScore = Int.MIN_VALUE
        output.lineSequence().forEach { line ->
            val pkg = AUDIO_PACKAGE_PATTERN.find(line)?.groupValues?.getOrNull(1) ?: return@forEach
            if (isIgnoredMediaPackage(pkg, selfPackage)) return@forEach
            val playing = line.contains("state:started", ignoreCase = true)
            val score = if (playing) 260 else 120
            if (score > bestScore) {
                bestScore = score
                best = Info(packageName = pkg, isPlaying = playing)
            }
        }
        return best?.takeIf { bestScore > 0 }
    }

    private fun parseNotificationDump(output: String, selfPackage: String): Info? {
        var currentPackage: String? = null
        var inMediaNotification = false
        var best: Info? = null
        var bestScore = Int.MIN_VALUE
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.contains("MediaStyle", ignoreCase = true) ||
                line.contains("android.mediaSession", ignoreCase = true) ||
                line.contains("template=media", ignoreCase = true)
            ) {
                inMediaNotification = true
            }
            NOTIFICATION_PKG_PATTERN.find(line)?.let { match ->
                currentPackage = match.groupValues[1]
            }
            if (line.startsWith("NotificationRecord(")) {
                inMediaNotification = false
            }
            val pkg = currentPackage
            if (inMediaNotification && pkg != null && !isIgnoredMediaPackage(pkg, selfPackage)) {
                val playing = !line.contains("isPlaying=false", ignoreCase = true)
                val score = if (playing) 260 else 180
                if (score > bestScore) {
                    bestScore = score
                    best = Info(packageName = pkg, isPlaying = playing)
                }
            }
        }
        return best?.takeIf { bestScore > 0 }
    }

    private fun extractPlaybackState(line: String): Int? {
        PLAYBACK_STATE_PATTERN.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        STATE_FIELD_PATTERN.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        if (line.contains("playing=true", ignoreCase = true)) return PlaybackState.STATE_PLAYING
        if (line.contains("playing=false", ignoreCase = true)) return PlaybackState.STATE_PAUSED
        return null
    }

    private val PACKAGE_PATTERN = Regex("""package(?:Name)?=([a-zA-Z][\w.]*)""")
    private val OWNER_PACKAGE_PATTERN = Regex("""ownerPackage=([a-zA-Z][\w.]*)""")
    private val STATE_FIELD_PATTERN = Regex("""\bstate=(\d+)\b""")
    private val PLAYBACK_STATE_PATTERN = Regex("""PlaybackState\s*\{[^}]*state=(\d+)""")
    private val AUDIO_PACKAGE_PATTERN = Regex("""\bpackage:([a-zA-Z][\w.]+)""")
    private val NOTIFICATION_PKG_PATTERN = Regex("""pkg=([a-zA-Z][\w.]+)""")
}
