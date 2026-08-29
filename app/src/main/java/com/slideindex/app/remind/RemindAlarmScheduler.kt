package com.slideindex.app.remind

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.slideindex.app.R

object RemindAlarmScheduler {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 120
    val PRESET_MINUTES = listOf(1, 3, 5, 10, 15)

    private const val REQUEST_CODE_BASE = 4000

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun isPending(context: Context, minutes: Int): Boolean {
        val safeMinutes = clampMinutes(minutes)
        return pendingIntent(
            context,
            safeMinutes,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null
    }

    fun toggle(context: Context, minutes: Int): Boolean {
        val safeMinutes = clampMinutes(minutes)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(
            context,
            safeMinutes,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Toast.makeText(
                context,
                context.getString(R.string.gesture_remind_cancelled, safeMinutes),
                Toast.LENGTH_SHORT,
            ).show()
            return true
        }
        val scheduleIntent = pendingIntent(
            context,
            safeMinutes,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return false
        val triggerAt = System.currentTimeMillis() + safeMinutes * 60_000L
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
            context.getString(R.string.gesture_remind_scheduled, safeMinutes),
            Toast.LENGTH_SHORT,
        ).show()
        return true
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
