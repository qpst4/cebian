package com.slideindex.app.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction

object RemindAlarmScheduler {
    private const val REQUEST_CODE_BASE = 4000

    fun toggle(context: Context, action: GestureAction): Boolean {
        val minutes = minutesFor(action) ?: return false
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, minutes, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Toast.makeText(
                context,
                context.getString(R.string.gesture_remind_cancelled, minutes),
                Toast.LENGTH_SHORT,
            ).show()
            return true
        }
        val scheduleIntent = pendingIntent(context, minutes, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ?: return false
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, scheduleIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, scheduleIntent)
            }
        }.onFailure {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, scheduleIntent)
        }
        Toast.makeText(
            context,
            context.getString(R.string.gesture_remind_scheduled, minutes),
            Toast.LENGTH_SHORT,
        ).show()
        return true
    }

    private fun minutesFor(action: GestureAction): Int? = when (action) {
        GestureAction.Remind1m -> 1
        GestureAction.Remind3m -> 3
        GestureAction.Remind5m -> 5
        GestureAction.Remind10m -> 10
        GestureAction.Remind15m -> 15
        else -> null
    }

    private fun pendingIntent(context: Context, minutes: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, RemindAlarmReceiver::class.java).apply {
            action = RemindAlarmReceiver.ACTION_REMIND
            putExtra(RemindAlarmReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + minutes,
            intent,
            flags,
        )
    }
}
