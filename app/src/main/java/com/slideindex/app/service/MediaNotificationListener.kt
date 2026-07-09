package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.slideindex.app.message.MessageReminderController
import com.slideindex.app.notification.NotificationHider
import com.slideindex.app.util.MediaSessionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Notification listener for media session tracking and notification history recording.
 * Must be enabled in system Settings ù?Notification access.
 *
 * Listener callbacks arrive on the main thread; heavy recording work is offloaded so
 * touch overlays (floating pointer, edge gestures) stay responsive.
 */
@AndroidEntryPoint
class MediaNotificationListener : NotificationListenerService() {
    @Inject lateinit var deps: AppDependencies

    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        mainHandler.post { MediaSessionTracker.onListenerConnected(this) }
        workerScope.launch {
            val notifications = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
            deps.notificationHistoryRecorder.onListenerConnected(
                this@MediaNotificationListener,
                notifications,
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaSessionTracker.onListenerDisconnected()
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        mainHandler.post { MediaSessionTracker.onNotificationsChanged(this) }
        val listener = this
        workerScope.launch {
            MessageReminderController.onNotificationPosted(applicationContext, listener, sbn, deps)
            deps.notificationHistoryRecorder.onPosted(applicationContext, listener, sbn)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int,
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        mainHandler.post { MediaSessionTracker.onNotificationsChanged(this) }
        workerScope.launch {
            deps.notificationHistoryRecorder.onRemoved(applicationContext, sbn, reason)
        }
    }

    fun restoreNotificationToShade(key: String): Boolean = NotificationHider.unsnoozeNotification(key)

    companion object {
        @Volatile
        var instance: MediaNotificationListener? = null
            private set
    }
}
