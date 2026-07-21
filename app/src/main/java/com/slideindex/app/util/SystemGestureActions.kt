package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent

object SystemGestureActions {
    fun isMuted(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    fun toggleMute(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val nextMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_SILENT
        }
        return runCatching {
            audioManager.ringerMode = nextMode
            true
        }.getOrDefault(false)
    }

    /** Sets ringer to silent without affecting media volume. */
    fun silenceRinger(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return true
        return runCatching {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            true
        }.getOrDefault(false)
    }

    /** Silences ringer/notification alerts and sets media volume to 0. Alarm volume is unchanged. */
    fun muteAllVolumes(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        silenceRinger(context)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.MEDIA, 0f)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.RING, 0f)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.NOTIFICATION, 0f)
        return true
    }

    fun dispatchMediaKey(context: Context, keyCode: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        }.getOrDefault(false)
    }

    fun openNotificationPolicySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
