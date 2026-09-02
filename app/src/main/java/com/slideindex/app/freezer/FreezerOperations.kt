package com.slideindex.app.freezer

/**
 * Portions derived from EdgeX (https://github.com/oxohang/EdgeX)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreezerOperations {
    fun hasShellAccess(): Boolean = TaskManagerUtil.hasPrivilegedAccess()

    fun isFrozen(context: Context, packageName: String): Boolean =
        FreezerPrivilegedOps.isAppDisabled(context, packageName)

    suspend fun setFrozen(context: Context, packageName: String, frozen: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!TaskManagerUtil.hasPrivilegedAccess()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
            val (success, detail) = FreezerPrivilegedOps.setAppDisabled(context, packageName, frozen)
            if (success) return@withContext true
            withContext(Dispatchers.Main) {
                val message = when (detail) {
                    FreezerPrivilegedOps.NEED_ROOT_FOR_SYSTEM_DISABLE ->
                        context.getString(R.string.freezer_unfreeze_need_root)
                    else -> {
                        val messageRes = if (frozen) {
                            R.string.freezer_freeze_failed
                        } else {
                            R.string.freezer_unfreeze_failed
                        }
                        detail.take(160).ifBlank { null }?.let {
                            context.getString(messageRes, it)
                        } ?: context.getString(R.string.freezer_permission_required)
                    }
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            false
        }

    suspend fun launchAndUnfreeze(
        context: Context,
        appRepository: AppRepository,
        settings: AppSettings,
        app: AppInfo,
        fullscreen: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        if (isFrozen(context, app.packageName)) {
            if (!setFrozen(context, app.packageName, frozen = false)) {
                return@withContext false
            }
        }
        withContext(Dispatchers.Main) {
            launchApp(context, app, settings, appRepository, fullscreen)
        }
    }

    private fun launchApp(
        context: Context,
        app: AppInfo,
        settings: AppSettings,
        appRepository: AppRepository,
        fullscreen: Boolean,
    ): Boolean {
        val effectiveSettings = if (!fullscreen) {
            settings.copy(freeWindow = settings.freeWindow.copy(freeWindowEnabled = true))
        } else {
            settings
        }
        if (appRepository.launchApp(app, effectiveSettings, fullscreen = fullscreen)) return true
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(app.packageName)
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, flags)
        }.firstOrNull() ?: return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply {
            setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    suspend fun refreezeAll(context: Context, packages: Set<String>): Int =
        freezeAll(context, packages)

    suspend fun freezeAll(context: Context, packages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (!isFrozen(context, pkg) && setFrozen(context, pkg, frozen = true)) count++
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.freezer_refreeze_done, count), Toast.LENGTH_SHORT).show()
        }
        count
    }

    suspend fun unfreezeAll(context: Context, packages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (isFrozen(context, pkg) && setFrozen(context, pkg, frozen = false)) count++
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.freezer_unfreeze_all_done, count), Toast.LENGTH_SHORT).show()
        }
        count
    }
}
