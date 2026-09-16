package com.slideindex.app.timeddnd

import android.app.AutomaticZenRule
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.slideindex.app.R
import com.slideindex.app.remind.RemindAlarmScheduler
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.VolumeControlHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TempDndManager {
    private const val TAG = "TempDndManager"

    const val ACTION_REDEFINE = "com.slideindex.app.action.TEMP_DND_REDEFINE"
    const val ACTION_STOP = "com.slideindex.app.action.TEMP_DND_STOP"
    const val KEY_INPUT_MINUTES = "temp_dnd_input_minutes"

    private const val UNIQUE_WORK_NAME = "temp_dnd_restore_work"
    private const val DND_NOTIFICATION_CHANNEL_ID = "temp_dnd_countdown"
    private const val DND_NOTIFICATION_ID = 3329

    private val TEMP_DND_CONDITION_URI: Uri =
        Uri.parse("slideindex://temp_dnd/active")

    private val DND_ENABLE_FILTER_CANDIDATES = intArrayOf(
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
        NotificationManager.INTERRUPTION_FILTER_NONE,
    )

    fun hasUsableDndPolicyAccess(context: Context): Boolean {
        val appContext = context.applicationContext
        if (PermissionHelper.hasNotificationPolicyAccess(appContext)) return true
        return runCatching {
            val raw = Settings.Secure.getString(
                appContext.contentResolver,
                "enabled_notification_policy_access_packages",
            ) ?: ""
            raw.split(':').any { it == appContext.packageName }
        }.getOrDefault(false)
    }

    suspend fun scheduledMinutes(context: Context): Int? =
        if (TempDndPreferences.isSessionActive(context)) {
            TempDndPreferences.readScheduledMinutes(context)
        } else {
            null
        }

    suspend fun enableTempDnd(context: Context, minutes: Int): Boolean {
        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
            ?: return false

        if (!hasUsableDndPolicyAccess(appContext)) {
            Log.w(TAG, "Policy access pre-check failed, will still try enabling DND directly")
        }

        val safeMinutes = RemindAlarmScheduler.clampMinutes(minutes)

        val snapshot = TempDndPreferences.readOriginalFilter(appContext)
        if (snapshot == TempDndPreferences.NO_FILTER_SNAPSHOT) {
            var current = VolumeControlHelper.readInterruptionFilter(appContext)
            if (current == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                current = NotificationManager.INTERRUPTION_FILTER_ALL
            }
            TempDndPreferences.writeOriginalFilter(appContext, current)
        }

        if (!enableDndFilter(appContext, notificationManager)) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.temp_dnd_policy_access_required),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            runCatching {
                appContext.startActivity(PermissionHelper.notificationPolicySettingsIntent())
            }
            return false
        }

        TempDndPreferences.writeScheduledMinutes(appContext, safeMinutes)
        scheduleRestore(appContext, safeMinutes)
        showCountdownNotification(appContext, safeMinutes)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.gesture_timed_dnd_scheduled, safeMinutes),
                Toast.LENGTH_SHORT,
            ).show()
        }
        return true
    }

    suspend fun restoreDnd(context: Context, showRestoredToast: Boolean = true): Boolean {
        val appContext = context.applicationContext
        val originalFilter = TempDndPreferences.readOriginalFilter(appContext)
        if (originalFilter == TempDndPreferences.NO_FILTER_SNAPSHOT) {
            return true
        }

        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
            ?: return false

        var target = originalFilter
        if (target == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            target = NotificationManager.INTERRUPTION_FILTER_ALL
        }

        val restored = restoreDndFilter(appContext, notificationManager, target)
        if (!restored) {
            Log.e(TAG, "Failed to restore DND filter")
            return false
        }

        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        TempDndPreferences.clearOriginalFilter(appContext)
        TempDndPreferences.clearScheduledMinutes(appContext)
        notificationManager.cancel(DND_NOTIFICATION_ID)

        if (showRestoredToast) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.gesture_timed_dnd_restored),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        return true
    }

    suspend fun cancelActive(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!TempDndPreferences.isSessionActive(appContext)) return false
        val success = restoreDnd(appContext, showRestoredToast = false)
        if (success) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.gesture_timed_dnd_cancelled),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        return success
    }

    private fun scheduleRestore(context: Context, minutes: Int) {
        val request = OneTimeWorkRequestBuilder<TempDndRestoreWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun showCountdownNotification(context: Context, minutes: Int) {
        if (!PermissionHelper.hasNotificationPermission(context)) return
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        createNotificationChannelIfNeeded(notificationManager, context)

        val redefineIntent = Intent(context, TempDndNotificationReceiver::class.java).apply {
            action = ACTION_REDEFINE
        }
        val redefinePending = PendingIntent.getBroadcast(
            context,
            1001,
            redefineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val remoteInput = RemoteInput.Builder(KEY_INPUT_MINUTES)
            .setLabel(context.getString(R.string.temp_dnd_notification_redefine_hint))
            .build()

        val stopIntent = Intent(context, TempDndNotificationReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            1002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, DND_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.temp_dnd_notification_title))
            .setContentText(context.getString(R.string.temp_dnd_notification_text, minutes))
            .setWhen(System.currentTimeMillis() + minutes * 60_000L)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    context.getString(R.string.temp_dnd_notification_redefine_action),
                    redefinePending,
                )
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .build(),
            )
            .addAction(
                0,
                context.getString(R.string.temp_dnd_notification_stop_action),
                stopPending,
            )
            .build()

        runCatching {
            notificationManager.notify(DND_NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Log.e(TAG, "Failed to post temporary DND notification", error)
        }
    }

    private fun createNotificationChannelIfNeeded(
        notificationManager: NotificationManager,
        context: Context,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            DND_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.temp_dnd_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.temp_dnd_notification_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private suspend fun enableDndFilter(
        context: Context,
        notificationManager: NotificationManager,
    ): Boolean {
        for (filter in DND_ENABLE_FILTER_CANDIDATES) {
            if (VolumeControlHelper.setInterruptionFilter(context, filter) != null &&
                VolumeControlHelper.isDndEnabled(context)
            ) {
                return true
            }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            return enableDndRuleState(context, notificationManager, active = true)
        }
        return false
    }

    private suspend fun restoreDndFilter(
        context: Context,
        notificationManager: NotificationManager,
        originalFilter: Int,
    ): Boolean {
        if (VolumeControlHelper.setInterruptionFilter(context, originalFilter) != null) {
            return true
        }
        if (Build.VERSION.SDK_INT >= 35) {
            return enableDndRuleState(context, notificationManager, active = false)
        }
        return false
    }

    private suspend fun enableDndRuleState(
        context: Context,
        notificationManager: NotificationManager,
        active: Boolean,
    ): Boolean {
        val ruleId = ensureTempDndRuleId(context, notificationManager) ?: return false
        return runCatching {
            val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
            notificationManager.setAutomaticZenRuleState(
                ruleId,
                Condition(TEMP_DND_CONDITION_URI, "SlideIndex temp dnd", state),
            )
            true
        }.onFailure { error ->
            Log.e(TAG, "Failed to update temp DND automatic rule state active=$active", error)
        }.getOrDefault(false)
    }

    private suspend fun ensureTempDndRuleId(
        context: Context,
        notificationManager: NotificationManager,
    ): String? {
        val stored = TempDndPreferences.readRuleId(context)
        if (stored.isNotEmpty()) {
            val exists = runCatching {
                notificationManager.getAutomaticZenRule(stored) != null
            }.getOrDefault(false)
            if (exists) return stored
        }

        if (Build.VERSION.SDK_INT < 35) return null

        val created = runCatching {
            notificationManager.addAutomaticZenRule(
                AutomaticZenRule(
                    "SlideIndex temporary DND",
                    null,
                    TEMP_DND_CONDITION_URI,
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    true,
                ),
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to create temp DND automatic rule", error)
        }.getOrNull()

        if (created.isNullOrEmpty()) return null
        TempDndPreferences.writeRuleId(context, created)
        return created
    }
}
