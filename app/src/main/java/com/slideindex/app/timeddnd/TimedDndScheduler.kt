package com.slideindex.app.timeddnd

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.remind.RemindAlarmScheduler
import com.slideindex.app.util.VolumeControlHelper

object TimedDndScheduler {
    private const val PREFS_NAME = "timed_dnd"
    private const val KEY_RESTORE_FILTER = "restore_filter"
    private const val KEY_SCHEDULED_MINUTES = "scheduled_minutes"
    private const val REQUEST_CODE = 4101

    fun scheduledMinutes(context: Context): Int? {
        val appContext = context.applicationContext
        if (pendingIntent(appContext, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) == null) {
            return null
        }
        val minutes = prefs(appContext).getInt(KEY_SCHEDULED_MINUTES, 0)
        return minutes.takeIf { it > 0 }
    }

    fun isActive(context: Context): Boolean = scheduledMinutes(context) != null

    fun startOrCancel(context: Context, minutes: Int): Boolean {
        val appContext = context.applicationContext
        if (isActive(appContext)) {
            cancel(appContext, restoreNow = true)
            Toast.makeText(
                appContext,
                appContext.getString(R.string.gesture_timed_dnd_cancelled),
                Toast.LENGTH_SHORT,
            ).show()
            return true
        }
        if (!VolumeControlHelper.hasAccess(appContext)) return false
        val safeMinutes = RemindAlarmScheduler.clampMinutes(minutes)
        val restoreFilter = VolumeControlHelper.readInterruptionFilter(appContext)
        if (!VolumeControlHelper.ensureDndEnabled(appContext)) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.gesture_action_toggle_dnd_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        prefs(appContext).edit()
            .putInt(KEY_RESTORE_FILTER, restoreFilter)
            .putInt(KEY_SCHEDULED_MINUTES, safeMinutes)
            .apply()
        scheduleAlarm(appContext, safeMinutes)
        Toast.makeText(
            appContext,
            appContext.getString(R.string.gesture_timed_dnd_scheduled, safeMinutes),
            Toast.LENGTH_SHORT,
        ).show()
        return true
    }

    fun restoreFromAlarm(context: Context) {
        val appContext = context.applicationContext
        val restoreFilter = prefs(appContext).getInt(
            KEY_RESTORE_FILTER,
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        clearSchedule(appContext)
        if (VolumeControlHelper.hasAccess(appContext)) {
            VolumeControlHelper.setInterruptionFilter(appContext, restoreFilter)
        }
        Toast.makeText(
            appContext,
            appContext.getString(R.string.gesture_timed_dnd_restored),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun cancel(context: Context, restoreNow: Boolean) {
        val restoreFilter = prefs(context).getInt(
            KEY_RESTORE_FILTER,
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        clearSchedule(context)
        if (restoreNow && VolumeControlHelper.hasAccess(context)) {
            VolumeControlHelper.setInterruptionFilter(context, restoreFilter)
        }
    }

    private fun clearSchedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent(context, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { pending ->
            alarmManager.cancel(pending)
            pending.cancel()
        }
        prefs(context).edit().clear().apply()
    }

    private fun scheduleAlarm(context: Context, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(
            context,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        if (!alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            scheduleExactAlarm(alarmManager, triggerAt, pendingIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleExactAlarm(
        alarmManager: AlarmManager,
        triggerAt: Long,
        scheduleIntent: PendingIntent,
    ) {
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, scheduleIntent)
        }.onFailure {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, scheduleIntent)
        }
    }

    private fun pendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, TimedDndReceiver::class.java).apply {
            action = TimedDndReceiver.ACTION_RESTORE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
