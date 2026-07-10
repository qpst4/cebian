package com.slideindex.app.notification

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.slideindex.app.message.NotificationData
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.FreeWindowLauncher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationIntentLaunchPort @Inject constructor(
    private val listenerPort: NotificationListenerPort,
) : NotificationIntentLaunchPort {
    override fun open(context: Context, data: NotificationData): Boolean {
        val appContext = context.applicationContext
        findSbn(data)?.let { sbn ->
            if (replayFromSbn(appContext, sbn)) return true
        }
        if (sendPendingIntent(appContext, data.contentIntent)) return true
        return NotificationAppLauncher.open(appContext, data.packageName)
    }

    override fun openInSmallWindow(context: Context, data: NotificationData, settings: AppSettings): Boolean {
        val appContext = context.applicationContext
        if (settings.freeWindowEnabled) {
            val launchOptions = FreeWindowLauncher.launchOptionsBundle(appContext, settings)
            findSbn(data)?.let { sbn ->
                if (replayFromSbn(appContext, sbn, launchOptions)) return true
            }
            if (sendPendingIntent(appContext, data.contentIntent, launchOptions)) return true
            return openAppInSmallWindow(appContext, data.packageName, settings)
        }
        return open(context, data)
    }

    private fun findSbn(data: NotificationData): StatusBarNotification? {
        NotificationSbnCache.find(data.key, data.postTime)?.let { return it }
        val listener = listenerPort.listenerOrNull() ?: return null
        return runCatching {
            listener.activeNotifications?.firstOrNull { it.key == data.key }
        }.getOrNull()
    }

    private fun replayFromSbn(
        context: Context,
        sbn: StatusBarNotification,
        launchOptions: Bundle? = null,
    ): Boolean {
        val notification = sbn.notification ?: return false
        if (sendPendingIntent(context, notification.contentIntent, launchOptions)) return true
        if (sendPendingIntent(context, notification.fullScreenIntent, launchOptions)) return true
        if (sendPendingIntent(context, notification.publicVersion?.contentIntent, launchOptions)) return true
        notification.actions?.forEach { action ->
            if (sendPendingIntent(context, action.actionIntent, launchOptions)) return true
        }
        return false
    }

    private fun sendPendingIntent(
        context: Context,
        pendingIntent: PendingIntent?,
        launchOptions: Bundle? = null,
    ): Boolean {
        if (pendingIntent == null) return false
        val options = launchOptions ?: createPendingIntentSendOptions()
        val sentWithContext = runCatching {
            pendingIntent.send(context, 0, null, null, null, null, options)
        }.onFailure { error ->
            Log.w(TAG, "PendingIntent.send(context) failed", error)
        }.isSuccess
        if (sentWithContext) return true
        return runCatching {
            pendingIntent.send()
        }.onFailure { error ->
            Log.w(TAG, "PendingIntent.send() failed", error)
        }.isSuccess
    }

    private fun openAppInSmallWindow(
        context: Context,
        packageName: String,
        settings: AppSettings,
    ): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        FreeWindowLauncher.launch(context, launchIntent, settings, fullscreen = false)
        return true
    }

    private fun createPendingIntentSendOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        val mode = when {
            Build.VERSION.SDK_INT >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            else -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        options.pendingIntentBackgroundActivityStartMode = mode
        return options.toBundle()
    }

    private companion object {
        const val TAG = "NotificationIntentLauncher"
    }
}
