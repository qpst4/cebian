package com.slideindex.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.slideindex.app.stash.StashPinNotificationHelper

class PinNotificationRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != StashPinNotificationHelper.ACTION_RESTORE_PIN) return
        StashPinNotificationHelper.restoreToScreenPin(context.applicationContext)
    }
}
