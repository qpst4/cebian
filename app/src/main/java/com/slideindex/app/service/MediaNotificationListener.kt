package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.slideindex.app.message.MessageReminderOrchestrator
import com.slideindex.app.notification.ActiveNotificationSnapshot
import com.slideindex.app.notification.NotificationChannelSupport
import com.slideindex.app.notification.NotificationShadeHider
import com.slideindex.app.overlay.OverlayStatePort
import com.slideindex.app.util.MediaSessionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import javax.inject.Inject

/**
 * Notification listener for media session tracking and notification history recording.
 * Must be enabled in system Settings ??Notification access.
 *
 * Listener callbacks arrive on the main thread; heavy recording work is offloaded so
 * touch overlays (floating pointer, edge gestures) stay responsive.
 */
@AndroidEntryPoint
class MediaNotificationListener : NotificationListenerService() {
    @Inject lateinit var deps: AppDependencies
    @Inject lateinit var shadeHider: NotificationShadeHider
    @Inject lateinit var messageReminderOrchestrator: MessageReminderOrchestrator

    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var unlockReceiverRegistered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                messageReminderOrchestrator.onUserPresent(applicationContext)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        mainHandler.post { MediaSessionTracker.onListenerConnected(this) }
        registerUnlockReceiver()
        scheduleActiveNotificationPublish()
        workerScope.launch {
            val notifications = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
            deps.notificationHistoryRecorder.onListenerConnected(
                this@MediaNotificationListener,
                notifications
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaSessionTracker.onListenerDisconnected()
        unregisterUnlockReceiver()
        if (instance === this) instance = null
        // 监听断开：通知栏快照作废，主进程的「实时」tab 不能继续显示旧数据。
        OverlayStatePort.publishActiveNotifications(applicationContext, emptyList())
    }

    private fun registerUnlockReceiver() {
        if (unlockReceiverRegistered) return
        runCatching {
            registerReceiver(
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT)
            )
            unlockReceiverRegistered = true
        }
    }

    private fun unregisterUnlockReceiver() {
        if (!unlockReceiverRegistered) return
        runCatching { unregisterReceiver(unlockReceiver) }
        unlockReceiverRegistered = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        mainHandler.post { MediaSessionTracker.onNotificationsChanged(this) }
        scheduleActiveNotificationPublish()
        val listener = this
        workerScope.launch {
            messageReminderOrchestrator.onNotificationPosted(applicationContext, listener, sbn)
            deps.notificationHistoryRecorder.onPosted(applicationContext, listener, sbn)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        mainHandler.post { MediaSessionTracker.onNotificationsChanged(this) }
        scheduleActiveNotificationPublish()
        workerScope.launch {
            messageReminderOrchestrator.onNotificationRemoved(
                applicationContext,
                this@MediaNotificationListener,
                sbn,
                reason,
            )
            deps.notificationHistoryRecorder.onRemoved(applicationContext, sbn, reason)
        }
    }

    fun restoreNotificationToShade(key: String): Boolean = shadeHider.unsnoozeNotification(key)

    private fun scheduleActiveNotificationPublish() {
        if (snapshotPublishScheduled) return
        snapshotPublishScheduled = true
        mainHandler.postDelayed(
            {
                snapshotPublishScheduled = false
                workerScope.launch { publishActiveNotificationsSnapshot(applicationContext) }
            },
            ACTIVE_SNAPSHOT_DEBOUNCE_MS,
        )
    }

    /** 本进程（`:overlay`）读通知栏，映射成可跨进程传输的快照。 */
    private fun currentActiveNotificationSnapshots(): List<ActiveNotificationSnapshot> =
        runCatching { activeNotifications?.toList() }.getOrNull().orEmpty().mapNotNull { sbn ->
            val notification = sbn.notification ?: return@mapNotNull null
            val extras = notification.extras ?: return@mapNotNull null
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString()
                ?: ""
            if (title.isBlank() && text.isBlank()) return@mapNotNull null
            ActiveNotificationSnapshot(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = text,
                postedAtMs = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
                channelId = NotificationChannelSupport.channelIdFrom(notification),
            )
        }

    private var snapshotPublishScheduled = false

    companion object {
        @Volatile
        var instance: MediaNotificationListener? = null
            private set

        private const val ACTIVE_SNAPSHOT_DEBOUNCE_MS = 120L

        /** 把通知栏当前内容广播出去，供主进程的「实时」tab 读取。 */
        fun publishActiveNotificationsSnapshot(context: Context) {
            val listener = instance
            OverlayStatePort.publishActiveNotifications(
                context = context,
                snapshots = listener?.currentActiveNotificationSnapshots().orEmpty(),
            )
        }

        /** 供端口读取：本进程（overlay）的通知栏快照；实例不在时返回空列表（＝此刻没有）。 */
        fun snapshotOf(listener: MediaNotificationListener?): List<ActiveNotificationSnapshot> =
            listener?.currentActiveNotificationSnapshots().orEmpty()
    }
}
