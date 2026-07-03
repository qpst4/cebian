package com.slideindex.app.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.slideindex.app.util.TaskManagerUtil

/**
 * Invisible one-shot activity so [Intent]s and published shortcuts can be started from overlay / FGS context.
 * Android 10+ blocks background activity starts; relaying through a foreground activity works.
 */
class LaunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntent = readLaunchIntent()
        val shortcutPackage = intent.getStringExtra(EXTRA_SHORTCUT_PACKAGE)
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        window.decorView.post {
            when {
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

    private fun launchPublishedShortcut(packageName: String, shortcutId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
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
        }
        if (TaskManagerUtil.hasPermission()) {
            Thread {
                TaskManagerUtil.startPublishedShortcut(packageName, shortcutId)
            }.start()
        }
    }

    private fun readLaunchIntent(): Intent? {
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
        private const val EXTRA_SHORTCUT_PACKAGE = "shortcut_package"
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"

        fun createIntent(context: Context, launchIntent: Intent): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_LAUNCH_INTENT, launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

        fun createShortcutIntent(context: Context, packageName: String, shortcutId: String): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_SHORTCUT_PACKAGE, packageName)
                putExtra(EXTRA_SHORTCUT_ID, shortcutId)
            }
    }
}
