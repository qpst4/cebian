package com.slideindex.app.search

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.search.ral.PrivilegedActivityLauncher
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Launch non-exported activities using RootActivityLauncher-compatible strategy chain.
 *
 * When called on the main thread, work runs on a background thread and [onComplete]
 * is invoked on the main thread when finished (avoids ANR).
 */
object NonExportedActivityLauncher {
    private const val TAG = "NonExportedActivityLauncher"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun launch(
        context: Context,
        packageName: String,
        activityName: String,
        options: Bundle? = null,
        onComplete: ((Boolean) -> Unit)? = null,
    ): Boolean {
        if (packageName.isBlank() || activityName.isBlank()) {
            onComplete?.let { mainHandler.post { it(false) } }
            return false
        }
        if (!TaskManagerUtil.hasPermission()) {
            Log.w(TAG, "Shizuku permission missing for $packageName/$activityName")
            onComplete?.let { mainHandler.post { it(false) } }
            return false
        }
        if (options != null) {
            Log.d(TAG, "launch options ignored; RAL strategies do not accept activity options")
        }

        val appContext = context.applicationContext
        val work = {
            val success = runBlocking(Dispatchers.IO) {
                PrivilegedActivityLauncher.launch(
                    context = appContext,
                    packageName = packageName,
                    activityName = activityName,
                    privilegedOnly = true,
                ).isEmpty()
            }
            onComplete?.let { callback ->
                mainHandler.post { callback(success) }
            }
            success
        }

        return if (Looper.myLooper() == Looper.getMainLooper()) {
            Thread(
                Runnable { work() },
                "non-exported-launch",
            ).start()
            true
        } else {
            work()
        }
    }
}
