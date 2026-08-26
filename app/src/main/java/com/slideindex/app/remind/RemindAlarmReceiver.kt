package com.slideindex.app.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RemindAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 1)
        val serviceIntent = Intent(context, RemindAlarmService::class.java).apply {
            putExtra(EXTRA_MINUTES, minutes)
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val ACTION_REMIND = "com.slideindex.app.action.REMIND"
        const val EXTRA_MINUTES = "extra_minutes"
    }
}
