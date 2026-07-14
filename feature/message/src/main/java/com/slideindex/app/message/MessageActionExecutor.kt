package com.slideindex.app.message

import android.content.Context
import android.util.Log
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageActionExecutor @Inject constructor(
    private val listenerPort: NotificationListenerPort,
    private val foregroundPort: MessageForegroundPort,
) {
    fun execute(
        context: Context,
        data: NotificationData,
        action: MessageAction,
        settings: AppSettings,
        launchPort: NotificationIntentLaunchPort,
    ) {
        when (action) {
            MessageAction.Read -> openNotification(context, data, launchPort)
            MessageAction.ReadInSmallWindow -> {
                val foregroundPackage = foregroundPort.foregroundPackage()
                if (foregroundPackage != null && foregroundPackage == data.packageName) {
                    openNotification(context, data, launchPort)
                } else {
                    openNotificationInSmallWindow(context, data, settings, launchPort)
                }
            }
            MessageAction.Ignore -> Unit
            MessageAction.IgnoreAndRemove -> cancelNotification(data.key)
            MessageAction.Dnd5Min -> MessageNotificationFilter.applyDnd(data.packageName, DND_DURATION_MS)
        }
    }

    fun cancelNotification(key: String): Boolean {
        val listener = listenerPort.listenerOrNull() ?: return false
        return runCatching {
            listener.cancelNotification(key)
            true
        }.getOrDefault(false)
    }

    private fun openNotification(
        context: Context,
        data: NotificationData,
        launchPort: NotificationIntentLaunchPort,
    ) {
        val opened = launchPort.open(context, data)
        if (!opened) {
            Log.w(TAG, "Failed to open notification for ${data.packageName}")
        }
    }

    private fun openNotificationInSmallWindow(
        context: Context,
        data: NotificationData,
        settings: AppSettings,
        launchPort: NotificationIntentLaunchPort,
    ) {
        val opened = launchPort.openInSmallWindow(context, data, settings)
        if (!opened) {
            Log.w(TAG, "Failed to open notification in small window for ${data.packageName}")
        }
    }

    companion object {
        private const val TAG = "MessageActionExecutor"
        private const val DND_DURATION_MS = 5 * 60 * 1000L
    }
}
