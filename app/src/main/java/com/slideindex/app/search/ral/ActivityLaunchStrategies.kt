@file:Suppress("unused")

/**
 * Based on [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) (GPL-3.0).
 */
package com.slideindex.app.search.ral

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.settings.PrivilegeMode
import rikka.shizuku.Shizuku
import rikka.shizuku.SystemServiceHelper

internal sealed interface ActivityLaunchStrategy : LaunchStrategy {
    data object Normal : ActivityLaunchStrategy {
        override val priority: Int = 100
        override val label: String = "Normal"

        override suspend fun Context.tryLaunch(args: LaunchArgs): List<Throwable> =
            try {
                val intent = Intent(args.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                withContext(Dispatchers.Main) {
                    startActivity(intent)
                }
                emptyList()
            } catch (error: Throwable) {
                Log.e(PrivilegedActivityLauncher.TAG, "Failure to normally start Activity", error)
                listOf(error)
            }
    }

    data object Iterative : ActivityLaunchStrategy, IterativeLaunchStrategy {
        override val priority: Int = 10
        override val label: String = "Iterative"

        override fun extraFlags(): Int = Intent.FLAG_ACTIVITY_NEW_TASK

        override suspend fun Context.performLaunch(args: LaunchArgs, intent: Intent) {
            withContext(Dispatchers.Main) {
                startActivity(intent)
            }
        }
    }

    data object SamsungExploit : ActivityLaunchStrategy {
        override val priority: Int = 1
        override val label: String = "SamsungExploit"

        override suspend fun Context.canRun(args: LaunchArgs): Boolean = isTouchWiz

        override suspend fun Context.tryLaunch(args: LaunchArgs): List<Throwable> =
            try {
                val wrapperIntent = Intent("com.samsung.server.telecom.USER_SELECT_WIFI_SERVICE_CALL")
                wrapperIntent.putExtra("extra_call_intent", args.intent)
                applicationContext.sendBroadcast(wrapperIntent)
                emptyList()
            } catch (error: Exception) {
                Log.e(PrivilegedActivityLauncher.TAG, "Failure to launch with Samsung exploit", error)
                listOf(error)
            }
    }

    data object ShizukuJava : ActivityLaunchStrategy, BinderActivityLaunchStrategy, ShizukuLaunchStrategy {
        override val label: String = "ShizukuJava"
    }

    data object AssistantJava : ActivityLaunchStrategy, ShizukuLaunchStrategy {
        override val priority: Int = 0
        override val label: String = "AssistantJava"

        private const val SECURE_ASSISTANT = "assistant"

        private fun Context.hasWriteSecureSettings(): Boolean =
            checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED

        override suspend fun Context.canRun(args: LaunchArgs): Boolean =
            PrivilegeGateway.isShizukuMode() &&
                (hasWriteSecureSettings() ||
                    (Shizuku.pingBinder() && (hasShizukuPermission || requestShizukuPermission())))

        override suspend fun Context.callLaunch(intent: Intent) {
            if (!hasWriteSecureSettings()) {
                val packageManager = HiddenFrameworkAccess.bindPackageManager(
                    wrapBinder(SystemServiceHelper.getSystemService("package")),
                )
                HiddenFrameworkAccess.grantRuntimePermission(
                    packageManager = packageManager,
                    packageName = packageName,
                    permission = android.Manifest.permission.WRITE_SECURE_SETTINGS,
                )
            }

            val currentAssistant = Settings.Secure.getString(contentResolver, SECURE_ASSISTANT)
            val replacedAssistant = intent.component?.flattenToString()

            try {
                Settings.Secure.putString(contentResolver, SECURE_ASSISTANT, replacedAssistant)
                try {
                    withContext(Dispatchers.Main) {
                        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
                        HiddenFrameworkAccess.launchAssist(
                            searchManager,
                            intent.extras ?: HiddenFrameworkAccess.emptyAssistExtras(),
                        )
                    }
                } catch (_: Throwable) {
                    if (Shizuku.pingBinder() && (hasShizukuPermission || requestShizukuPermission())) {
                        HiddenFrameworkAccess.injectAssistKeyEvents()
                    } else {
                        throw IllegalStateException("Shizuku was unavailable for injecting key events.")
                    }
                }
                delay(500)
            } finally {
                try {
                    Settings.Secure.putString(contentResolver, SECURE_ASSISTANT, currentAssistant)
                } catch (error: Throwable) {
                    error.printStackTrace()
                }
            }
        }
    }

    data object Root : ActivityLaunchStrategy, RootLaunchStrategy {
        override val priority: Int = 1
        override val label: String = "Root"

        override fun makeCommand(args: LaunchArgs): String {
            val component = args.intent.component
            if (component != null) {
                return "am start -n ${component.flattenToShortString()}"
            }
            val uri = args.intent.toUri(Intent.URI_INTENT_SCHEME)
            return "am start '$uri'"
        }
    }
}

internal val activityLaunchStrategies: List<ActivityLaunchStrategy> = listOf(
    ActivityLaunchStrategy.Normal,
    ActivityLaunchStrategy.Iterative,
    ActivityLaunchStrategy.ShizukuJava,
    ActivityLaunchStrategy.SamsungExploit,
    ActivityLaunchStrategy.Root,
    ActivityLaunchStrategy.AssistantJava,
).sortedByDescending { it.priority }

internal fun ActivityLaunchStrategy.allowedForCurrentPrivilegeMode(): Boolean =
    when (PrivilegeGateway.mode) {
        PrivilegeMode.ROOT -> when (this) {
            is ActivityLaunchStrategy.ShizukuJava,
            is ActivityLaunchStrategy.AssistantJava,
            -> false
            else -> true
        }
        PrivilegeMode.SHIZUKU -> this !is RootLaunchStrategy
    }
