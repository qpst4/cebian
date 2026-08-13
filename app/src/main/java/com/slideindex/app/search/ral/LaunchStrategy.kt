/**
 * Based on [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) (GPL-3.0).
 */
package com.slideindex.app.search.ral

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import eu.chainfire.libsuperuser.Shell
import rikka.shizuku.Shizuku

internal interface LaunchStrategy {
    val priority: Int
        get() = 0

    val label: String

    suspend fun Context.canRun(args: LaunchArgs): Boolean = true

    suspend fun Context.tryLaunch(args: LaunchArgs): List<Throwable>
}

internal interface CommandLaunchStrategy : LaunchStrategy {
    fun makeCommand(args: LaunchArgs): String
}

internal interface BinderWrapperLaunchStrategy : LaunchStrategy, BinderWrapper {
    suspend fun Context.callLaunch(intent: Intent)

    override suspend fun Context.tryLaunch(args: LaunchArgs): List<Throwable> =
        try {
            callLaunch(args.intent)
            emptyList()
        } catch (error: Throwable) {
            Log.e(PrivilegedActivityLauncher.TAG, "Failure to launch through ${this::class.java.simpleName}", error)
            listOf(error)
        }
}

internal interface ShizukuLaunchStrategy : BinderWrapperLaunchStrategy, ShizukuBinderWrapperHost {
    override suspend fun Context.canRun(args: LaunchArgs): Boolean =
        Shizuku.pingBinder() && (hasShizukuPermission || requestShizukuPermission())
}

internal interface BinderActivityLaunchStrategy : BinderWrapperLaunchStrategy {
    override val priority: Int
        get() = 2

    override suspend fun Context.callLaunch(intent: Intent) {
        val wrappedBinder = wrapBinder(HiddenFrameworkAccess.activityServiceBinder())
        val activityManager = HiddenFrameworkAccess.bindActivityManager(wrappedBinder)
        val (_, callingPackage) = getUidAndPackage()
        val result = HiddenFrameworkAccess.startActivity(activityManager, callingPackage, intent)
        HiddenFrameworkAccess.requireStartSuccess(result)
    }
}

internal interface RootLaunchStrategy : CommandLaunchStrategy {
    override suspend fun Context.canRun(args: LaunchArgs): Boolean = Shell.SU.available()

    override suspend fun Context.tryLaunch(args: LaunchArgs): List<Throwable> {
        val command = StringBuilder(makeCommand(args))
        appendCommandExtras(command, args)
        val errorOutput = mutableListOf<String>()
        val result = Shell.Pool.SU.run(command.toString(), null, errorOutput, false)
        return if (result == 0) emptyList() else listOf(Exception(errorOutput.joinToString("\n")))
    }
}

internal interface IterativeLaunchStrategy : LaunchStrategy {
    fun extraFlags(): Int? = null

    override suspend fun Context.canRun(args: LaunchArgs): Boolean =
        args.filters.isNotEmpty()

    suspend fun Context.performLaunch(args: LaunchArgs, intent: Intent)

    override suspend fun Context.tryLaunch(args: LaunchArgs): List<Throwable> {
        val errors = mutableListOf<Throwable>()
        args.filters.forEach { filter ->
            try {
                val variant = Intent(args.intent)
                extraFlags()?.let(variant::addFlags)
                variant.categories?.clear()
                variant.action = if (filter.countActions() > 0) {
                    filter.getAction(0)
                } else {
                    Intent.ACTION_MAIN
                }
                variant.data = if (filter.countDataSchemes() > 0) {
                    "${filter.getDataScheme(0)}://yes".toUri()
                } else {
                    null
                }
                filter.categoriesIterator()?.forEach(variant::addCategory)
                performLaunch(args, variant)
                return emptyList()
            } catch (error: Throwable) {
                Log.e(PrivilegedActivityLauncher.TAG, "Error with alternative filter", error)
                errors += error
            }
        }
        return errors
    }
}

private fun appendCommandExtras(command: StringBuilder, args: LaunchArgs) {
    command.append(" -a ${args.intent.action}")
    args.intent.categories?.forEach { category ->
        command.append(" -c \"$category\"")
    }
}
