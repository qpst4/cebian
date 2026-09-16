package com.slideindex.app.timeddnd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimedDndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTORE) return
        TimedDndScheduler.restoreFromAlarm(context)
    }

    companion object {
        const val ACTION_RESTORE = "com.slideindex.app.action.TIMED_DND_RESTORE"
    }
}
