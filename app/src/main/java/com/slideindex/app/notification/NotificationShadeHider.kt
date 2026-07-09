package com.slideindex.app.notification

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.slideindex.app.notification.NotificationListenerPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationShadeHider @Inject constructor(
    private val listenerPort: NotificationListenerPort,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True for ongoing / non-dismissible shade notifications (e.g. overlay, FGS). */
    fun isOngoing(sbn: StatusBarNotification): Boolean = NotificationShadePolicy.isOngoing(sbn)

    /**
     * Hide a notification from the shade. Uses long-duration snooze as primary path
     * (works for persistent system notifications); falls back to cancel for dismissible ones.
     */
    fun hideFromShade(listener: NotificationListenerService, sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == listener.packageName) return false
        return hideFromShade(listener, sbn.key, sbn)
    }

    fun hideFromShadeOnMain(listener: NotificationListenerService, sbn: StatusBarNotification) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideFromShade(listener, sbn)
        } else {
            mainHandler.post { hideFromShade(listener, sbn) }
        }
    }

    fun hideFromShade(
        listener: NotificationListenerService,
        key: String,
        sbn: StatusBarNotification? = null,
    ): Boolean {
        if (isHiddenInShade(listener, key)) {
            Log.d(TAG, "notification already hidden: key=$key")
            return true
        }

        val snoozeAttempted = runCatching {
            listener.snoozeNotification(key, SNOOZE_DURATION_MS)
        }.onFailure { error ->
            Log.w(TAG, "snoozeNotification failed for key=$key", error)
        }.isSuccess

        if (snoozeAttempted && isHiddenInShade(listener, key)) {
            Log.d(TAG, "snoozeNotification hid notification: key=$key")
            return true
        }

        val canCancel = sbn?.let { !isOngoing(it) && it.isClearable } ?: true
        if (!canCancel) {
            val ongoing = sbn?.let(::isOngoing)
            Log.w(
                TAG,
                "snooze did not hide and notification is not cancelable: key=$key " +
                    "ongoing=$ongoing clearable=${sbn?.isClearable}",
            )
            return false
        }

        val cancelAttempted = runCatching {
            listener.cancelNotification(key)
        }.onFailure { error ->
            Log.w(TAG, "cancelNotification fallback failed for key=$key", error)
        }.isSuccess

        if (cancelAttempted && isHiddenInShade(listener, key)) {
            Log.d(TAG, "cancelNotification hid notification: key=$key")
            return true
        }

        Log.w(TAG, "hideFromShade failed for key=$key snoozeAttempted=$snoozeAttempted cancelAttempted=$cancelAttempted")
        return false
    }

    private fun isHiddenInShade(listener: NotificationListenerService, key: String): Boolean {
        val stillActive = runCatching {
            listener.activeNotifications?.any { it.key == key } == true
        }.getOrDefault(false)
        if (!stillActive) return true
        return runCatching {
            listener.snoozedNotifications?.any { it.key == key } == true
        }.getOrDefault(false)
    }

    fun hideNotification(key: String): Boolean {
        val listener = listenerPort.listenerOrNull() ?: return false
        val sbn = runCatching {
            listener.activeNotifications?.firstOrNull { it.key == key }
        }.getOrNull()
        return hideFromShade(listener, key, sbn)
    }

    fun hideNotification(sbn: StatusBarNotification): Boolean {
        val listener = listenerPort.listenerOrNull() ?: return false
        return hideFromShade(listener, sbn)
    }

    /**
     * Restore a snoozed notification to the shade.
     * Public [NotificationListenerService] has no unsnooze API; a very short re-snooze
     * causes the system to repost the notification (see Android 11+ behavior).
     */
    fun unsnoozeNotification(key: String): Boolean {
        val listener = listenerPort.listenerOrNull() ?: return false
        val isSnoozed = runCatching {
            listener.snoozedNotifications?.any { it.key == key } == true
        }.getOrDefault(false)
        if (!isSnoozed) return false
        return runCatching {
            listener.snoozeNotification(key, UNSNOOZE_DURATION_MS)
            true
        }.onFailure { error ->
            Log.w(TAG, "short snooze restore failed for key=$key", error)
        }.getOrDefault(false)
    }

    /** Restores every notification snoozed by this listener back to the shade. */
    fun restoreAllSnoozed(selfPackage: String): List<String> {
        val listener = listenerPort.listenerOrNull() ?: return emptyList()
        val snoozed = runCatching {
            listener.snoozedNotifications?.toList()
        }.getOrNull().orEmpty()
        val restoredKeys = mutableListOf<String>()
        snoozed.forEach { sbn ->
            if (sbn.packageName == selfPackage) return@forEach
            if (unsnoozeNotification(sbn.key)) {
                restoredKeys += sbn.key
            }
        }
        return restoredKeys
    }

    companion object {
        /**
         * Effectively permanent snooze (~50 years). Well below [Long.MAX_VALUE] to avoid overflow.
         * [NotificationListenerService.snoozeNotification] is available from API 26; minSdk is 30.
         */
        val SNOOZE_DURATION_MS: Long = run {
            val msPerYear = 365L * 24 * 60 * 60 * 1000
            50L * msPerYear
        }

        private const val TAG = "NotificationShadeHider"

        /** Short snooze used to trigger system repost of a long-snoozed notification. */
        private const val UNSNOOZE_DURATION_MS = 100L
    }
}
