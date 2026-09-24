package com.slideindex.app.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * （[notifyOffline]），点一下直达系统无障碍设置。掉线期间按 [RENOTIFY_INTERVAL_MS] 重复提醒，
 * 通知被用户划掉/系统清掉则立刻允许重发；重连后由 [clearOffline] 复位。
 */
object AccessibilityRecoverNotifier {
    @Volatile
    private var reopenHintShownThisProcess = false

    /** 上次发出掉线通知的时间（elapsedRealtime）；0 表示当前没有处于「已提醒」状态。 */
    @Volatile
    private var lastOfflineNotifyAtMs = 0L

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
     * 掉线期间不会刷屏：通知还在时最多每 [RENOTIFY_INTERVAL_MS] 提醒一次；被划掉就立刻重发。
     */
    // 发送前已用 areNotificationsEnabled() 确认通知开关（含 POST_NOTIFICATIONS），
    // 但 lint 的 MissingPermission 不认这条守卫；与本仓库 RemindAlarmScheduler / ForegroundAppTracker 同款做法。
    @SuppressLint("MissingPermission")
    fun notifyOffline(context: Context) {
        val appContext = context.applicationContext
        if (!PermissionHelper.isAccessibilityServiceEnabled(appContext)) return
        // 先确认通知开关（含 POST_NOTIFICATIONS）再直接发送：与 StashPinNotificationHelper 同款写法。
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        val now = SystemClock.elapsedRealtime()
        val notifiedRecently = lastOfflineNotifyAtMs != 0L &&
            now - lastOfflineNotifyAtMs < RENOTIFY_INTERVAL_MS
        if (notifiedRecently && isOfflineNotificationActive(appContext)) return
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
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        lastOfflineNotifyAtMs = now
    }

    /** 服务重新连上（或总开关被关掉）时清掉提示，并允许下一轮掉线再提示。 */
    fun clearOffline(context: Context) {
        lastOfflineNotifyAtMs = 0L
        runCatching {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        }
    }

    /** 我们那条掉线通知当前是否还挂在通知栏上（用户划掉或系统清掉都会变 false）。 */
    private fun isOfflineNotificationActive(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            manager.activeNotifications.any {
                it.id == NOTIFICATION_ID && it.packageName == context.packageName
            }
        }.getOrDefault(false)
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

    /** 仍处于掉线状态时，最多隔这么久再提醒一次。 */
    private const val RENOTIFY_INTERVAL_MS = 10 * 60 * 1000L
}

enum class AccessibilityRecoverOutcome {
    NotNeeded,
    Connected,
    Failed,
}
