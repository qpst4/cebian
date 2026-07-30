package com.slideindex.app.service

import android.app.Activity
import android.os.Bundle
import com.slideindex.app.stash.StashPinNotificationHelper

/** One-shot activity for pin-restore notification content taps (lint: no BroadcastReceiver as content intent). */
class PinNotificationRestoreTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == StashPinNotificationHelper.ACTION_RESTORE_PIN) {
            StashPinNotificationHelper.restoreToScreenPin(applicationContext)
        }
        finish()
    }
}
