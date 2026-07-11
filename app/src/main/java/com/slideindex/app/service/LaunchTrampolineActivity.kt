package com.slideindex.app.service

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.slideindex.app.notification.NotificationHistoryIntentCapture
import com.slideindex.app.util.TaskManagerUtil

/**
 * Invisible one-shot activity so [Intent]s and published shortcuts can be started from overlay / FGS context.
 * Android 10+ blocks background activity starts; relaying through a foreground activity works.
 */
class LaunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pendingIntentEncoded = intent.getStringExtra(EXTRA_PENDING_INTENT_B64)
        val fallbackIntent = readFallbackIntent()
        val launchIntent = readLaunchIntent()
        val shortcutPackage = intent.getStringExtra(EXTRA_SHORTCUT_PACKAGE)
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        window.decorView.post {
            when {
                !pendingIntentEncoded.isNullOrBlank() -> {
                    sendStoredPendingIntent(pendingIntentEncoded, fallbackIntent)
                }
                launchIntent != null -> {
                    runCatching {
                        startActivity(launchIntent)
                    }.onFailure { error ->
                        Log.e(TAG, "startActivity from trampoline failed", error)
                    }
                }
                !shortcutPackage.isNullOrBlank() && !shortcutId.isNullOrBlank() -> {
                    launchPublishedShortcut(shortcutPackage, shortcutId)
                }
            }
            window.decorView.postDelayed({
                if (!isFinishing) {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }, FINISH_DELAY_MS)
        }
    }

    private fun sendStoredPendingIntent(encoded: String, fallbackIntent: Intent?) {
        val pendingIntent = NotificationHistoryIntentCapture.deserializePendingIntent(encoded)
        if (pendingIntent != null) {
            val options = createPendingIntentSendOptions()
            val sentWithOptions = runCatching {
                pendingIntent.send(this, 0, null, null, null, null, options)
            }.onFailure { error ->
                Log.e(TAG, "PendingIntent.send from trampoline failed", error)
            }.isSuccess
            if (sentWithOptions) return
            val sent = runCatching {
                pendingIntent.send()
            }.onFailure { error ->
                Log.e(TAG, "PendingIntent.send() from trampoline failed", error)
            }.isSuccess
            if (sent) return
        }
        if (fallbackIntent != null) {
            runCatching {
                startActivity(fallbackIntent)
            }.onFailure { error ->
                Log.e(TAG, "fallback startActivity from trampoline failed", error)
            }
        }
    }

    private fun readFallbackIntent(): Intent? {
        intent.getStringExtra(EXTRA_FALLBACK_INTENT_B64)?.let { encoded ->
            NotificationHistoryIntentCapture.deserializeIntentParcel(encoded)
                ?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_FALLBACK_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_FALLBACK_INTENT)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun createPendingIntentSendOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        options.pendingIntentBackgroundActivityStartMode = mode
        return options.toBundle()
    }

    private fun launchPublishedShortcut(packageName: String, shortcutId: String) {
        val launcherApps = getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            val started = runCatching {
                launcherApps.startShortcut(
                    packageName,
                    shortcutId,
                    null,
                    null,
                    Process.myUserHandle(),
                )
            }.onFailure { error ->
                Log.e(TAG, "startShortcut($packageName, $shortcutId) failed", error)
            }.isSuccess
            if (started) return
        }
        if (TaskManagerUtil.hasPermission()) {
            Thread {
                TaskManagerUtil.startPublishedShortcut(packageName, shortcutId)
            }.start()
        }
    }

    private fun readLaunchIntent(): Intent? {
        intent.getStringExtra(EXTRA_LAUNCH_INTENT_B64)?.let { encoded ->
            NotificationHistoryIntentCapture.deserializeIntentParcel(encoded)
                ?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_LAUNCH_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_LAUNCH_INTENT)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private const val TAG = "LaunchTrampoline"
        private const val FINISH_DELAY_MS = 400L
        private const val EXTRA_LAUNCH_INTENT = "launch_intent"
        private const val EXTRA_LAUNCH_INTENT_B64 = "launch_intent_b64"
        private const val EXTRA_PENDING_INTENT_B64 = "pending_intent_b64"
        private const val EXTRA_FALLBACK_INTENT = "fallback_intent"
        private const val EXTRA_FALLBACK_INTENT_B64 = "fallback_intent_b64"
        private const val EXTRA_SHORTCUT_PACKAGE = "shortcut_package"
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"

        fun createPendingIntentIntent(
            context: Context,
            pendingIntentBase64: String,
            fallbackIntent: Intent? = null,
        ): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PENDING_INTENT_B64, pendingIntentBase64)
                fallbackIntent?.let { intent ->
                    NotificationHistoryIntentCapture.serializeIntentParcel(intent)?.let { encoded ->
                        putExtra(EXTRA_FALLBACK_INTENT_B64, encoded)
                    } ?: putExtra(EXTRA_FALLBACK_INTENT, intent)
                }
            }

        fun createIntent(context: Context, launchIntent: Intent): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val prepared = launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                NotificationHistoryIntentCapture.serializeIntentParcel(prepared)?.let { encoded ->
                    putExtra(EXTRA_LAUNCH_INTENT_B64, encoded)
                } ?: putExtra(EXTRA_LAUNCH_INTENT, prepared)
            }

        fun createShortcutIntent(context: Context, packageName: String, shortcutId: String): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_SHORTCUT_PACKAGE, packageName)
                putExtra(EXTRA_SHORTCUT_ID, shortcutId)
            }
    }
}
