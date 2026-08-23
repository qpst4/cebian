package com.slideindex.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.util.MediaSessionHelper

/**
 * On device boot or package replace, request the notification listener to rebind so persisted filter rules
 * can be re-applied via [com.slideindex.app.service.MediaNotificationListener.onListenerConnected].
 */
class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            MediaSessionHelper.ensureNotificationListenerConnected(context)
            Log.d(TAG, "Requested notification listener rebind after $action")
        }.onFailure { error ->
            Log.w(TAG, "Failed to request notification listener rebind after $action", error)
        }
    }

    private companion object {
        const val TAG = "NotificationBootReceiver"
    }
}
