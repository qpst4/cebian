package com.slideindex.app.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper

/** Prompt when silent accessibility rebind fails after app-launch retries. */
object AccessibilityRecoverNotifier {
    @Volatile
    private var reopenHintShownThisProcess = false

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
}

enum class AccessibilityRecoverOutcome {
    NotNeeded,
    Connected,
    Failed,
}
