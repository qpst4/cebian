package com.slideindex.app.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log

object NotificationChannelSupport {
    private const val TAG = "NotifChannelSupport"

    fun channelIdFrom(notification: Notification?): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return notification?.channelId?.takeIf { it.isNotBlank() }
    }

    fun channelIdFrom(sbn: StatusBarNotification): String? = channelIdFrom(sbn.notification)

    fun resolveChannelId(item: NotificationHistoryItem): String? {
        item.channelId?.takeIf { it.isNotBlank() }?.let { return it }
        val key = item.notificationKey ?: return null
        val cached = NotificationSbnCache.find(key, item.postedAtMs) ?: return null
        return channelIdFrom(cached)
    }

    fun openSettings(context: Context, packageName: String, channelId: String? = null): Boolean {
        if (packageName.isBlank()) return false
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelId.isNullOrBlank()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
        }.onFailure { error ->
            Log.w(TAG, "openSettings failed for $packageName channel=$channelId", error)
        }.isSuccess
    }
}
