package com.slideindex.app.search.ral

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Based on [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) (GPL-3.0).
 *
 * RootActivityLauncher-compatible privileged activity launcher.
 *
 * Strategy order (descending priority): Normal, Iterative, ShizukuJava,
 * SamsungExploit, Root, AssistantJava.
 */
object PrivilegedActivityLauncher {
    const val TAG = "PrivilegedActivityLauncher"

    fun createLaunchArgs(
        context: Context,
        packageName: String,
        activityName: String,
    ): LaunchArgs {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val filters = context.packageManager.getAllIntentFiltersCompat(packageName)
        return LaunchArgs(intent, filters)
    }

    suspend fun launch(
        context: Context,
        packageName: String,
        activityName: String,
        privilegedOnly: Boolean = false,
    ): List<Throwable> {
        val appContext = context.applicationContext
        val args = createLaunchArgs(appContext, packageName, activityName)
        val errors = mutableListOf<Throwable>()
        val strategies = if (privilegedOnly) {
            activityLaunchStrategies.filter { strategy ->
                strategy !is ActivityLaunchStrategy.Normal &&
                    strategy !is ActivityLaunchStrategy.Iterative
            }
        } else {
            activityLaunchStrategies
        }

        for (strategy in strategies) {
            with(strategy) {
                if (!appContext.canRun(args)) return@with
                val result = appContext.tryLaunch(args)
                Log.d(TAG, "${strategy::class.simpleName} -> errors=${result.size}")
                if (result.isEmpty()) {
                    Log.i(TAG, "launched ${args.intent.component} via ${strategy.label}")
                    return emptyList()
                }
                errors += result
            }
        }

        return errors.ifEmpty {
            listOf(Exception("All launch strategies failed for ${args.intent.component?.flattenToString()}"))
        }
    }
}
