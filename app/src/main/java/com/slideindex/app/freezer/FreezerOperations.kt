package com.slideindex.app.freezer

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreezerOperations {
    fun hasShellAccess(): Boolean =
        TaskManagerUtil.hasPermission() || TaskManagerUtil.probeRootAvailable()

    suspend fun setFrozen(context: Context, packageName: String, frozen: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!TaskManagerUtil.hasPermission() && !TaskManagerUtil.probeRootAvailable()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
            val useRoot = TaskManagerUtil.probeRootAvailable()
            val commands = if (frozen) {
                listOf(
                    "pm disable-user --user 0 $packageName",
                    "pm disable $packageName",
                )
            } else {
                listOf(
                    "pm enable $packageName",
                    "pm enable-user --user 0 $packageName",
                )
            }
            var lastOutput = ""
            for (command in commands) {
                val result = TaskManagerUtil.runShellCommandLine(command, useRoot = useRoot)
                if (result.success) return@withContext true
                lastOutput = result.output.trim()
            }
            withContext(Dispatchers.Main) {
                val detail = lastOutput.take(120).ifBlank { null }
                val message = if (detail != null) {
                    context.getString(R.string.freezer_command_failed, detail)
                } else {
                    context.getString(R.string.freezer_permission_required)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            false
        }

    suspend fun refreezeAll(context: Context, packages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_permission_required, Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }
        val pm = context.packageManager
        var count = 0
        for (pkg in packages) {
            val enabled = runCatching {
                pm.getApplicationEnabledSetting(pkg) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }.getOrDefault(false)
            if (enabled && setFrozen(context, pkg, frozen = true)) count++
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.freezer_refreeze_done, count), Toast.LENGTH_SHORT).show()
        }
        count
    }

    fun isFrozen(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationEnabledSetting(packageName) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }.getOrDefault(false)
}
