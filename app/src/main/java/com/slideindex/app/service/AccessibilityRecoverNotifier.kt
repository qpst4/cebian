package com.slideindex.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper

/**
 * 「无障碍掉线」的用户提示。
 *
 * 应用启动时的静默重绑失败走 Toast（[maybeShowReopenHint]）；后台看门狗发现的掉线走可点通知
 * （[notifyOffline]），点一下直达系统无障碍设置。两者都按「一次掉线只提示一次」去重，
 * 重连后由 [clearOffline] 复位，方便下一轮掉线还能提示。
 */
object AccessibilityRecoverNotifier {
    @Volatile
    private var reopenHintShownThisProcess = false

    @Volatile
    private var offlineNotificationShown = false

    private val mainHandler = Handler(Looper.getMainLooper())

    fun maybeShowReopenHint(
        context: Context,
        outcome: AccessibilityRecoverOutcome,
        settings: AppSettings,
    ) {
        if (outcome != AccessibilityRecoverOutcome.Failed) return
        if (!settings.serviceEnabled) return
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return
        if (reopenHintShownThisProcess) return
        reopenHintShownThisProcess = true
        val appContext = context.applicationContext
        mainHandler.post {
            Toast.makeText(
                appContext,
                R.string.accessibility_recover_reopen_app_hint,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * 后台看门狗检测到「系统设置里已开启、实际进程没连上」时调用。
     *
     * 同一次掉线只发一条；[clearOffline] 之后才允许再发。
     */
    fun notifyOffline(context: Context) {
        val appContext = context.applicationContext
        if (offlineNotificationShown) return
        if (!PermissionHelper.isAccessibilityServiceEnabled(appContext)) return
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(appContext)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = appContext.getString(R.string.accessibility_offline_notification_text)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.accessibility_offline_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val posted = runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess
        if (posted) offlineNotificationShown = true
    }

    /** 服务重新连上（或总开关被关掉）时清掉提示，并允许下一轮掉线再提示。 */
    fun clearOffline(context: Context) {
        offlineNotificationShown = false
        runCatching {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private const val CHANNEL_ID = "slide_index_accessibility_offline"
    private const val NOTIFICATION_ID = 1002
}

enum class AccessibilityRecoverOutcome {
    NotNeeded,
    Connected,
    Failed,
}
