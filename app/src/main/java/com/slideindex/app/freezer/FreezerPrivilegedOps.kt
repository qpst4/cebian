package com.slideindex.app.freezer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.slideindex.app.BuildConfig
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.search.ral.HiddenFrameworkAccess
import com.slideindex.app.util.TaskManagerUtil
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 对齐雹 [HShell.setAppDisabled] / [HShizuku.setAppDisabled]：优先 PM API，其次 shell。
 * 解冻时兼容旧版以 `pm disable`（系统级）冻结的应用，需 Root 或 Shizuku(Root)。
 */
internal object FreezerPrivilegedOps {
    private const val APP_INFO_FLAGS =
        PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS

    fun isAppDisabled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, APP_INFO_FLAGS).enabled.not()
    }.getOrDefault(false)

    fun setAppDisabled(context: Context, packageName: String, disabled: Boolean): Pair<Boolean, String> {
        if (!appExists(context, packageName)) {
            return false to "package not found"
        }
        val shizukuRoot = TaskManagerUtil.probeRootAvailable()
        val useRoot = PrivilegeGateway.isRootMode() ||
            (PrivilegeGateway.isShizukuMode() && shizukuRoot)
        val userId = resolveUserId(useRoot)

        if (disabled) {
            TaskManagerUtil.forceStopPackage(packageName)
            TaskManagerUtil.runShellCommandLine(
                "am force-stop --user $userId $packageName",
                useRoot = useRoot,
            )
        }

        val errors = mutableListOf<String>()

        if (TaskManagerUtil.hasShizukuPermission()) {
            when (val result = setViaShizukuPackageManager(context, packageName, disabled, shizukuRoot, userId)) {
                is AttemptResult.Success -> return true to ""
                is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
            }
            if (!disabled) {
                when (val result = setViaShizukuPackageManager(
                    context = context,
                    packageName = packageName,
                    disabled = false,
                    shizukuRoot = shizukuRoot,
                    userId = userId,
                    enabledState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                )) {
                    is AttemptResult.Success -> return true to ""
                    is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
                }
            }
        }

        if (useRoot) {
            when (val result = setViaShell(context, packageName, disabled, userId, useRoot = true)) {
                is AttemptResult.Success -> return true to ""
                is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
            }
        }

        if (PrivilegeGateway.isShizukuMode() && TaskManagerUtil.hasShizukuPermission() && !useRoot) {
            when (val result = setViaShell(context, packageName, disabled, userId, useRoot = false)) {
                is AttemptResult.Success -> return true to ""
                is AttemptResult.Failure -> if (result.detail.isNotBlank()) errors += result.detail
            }
        }

        val detail = errors.lastOrNull().orEmpty()
        if (!disabled && !useRoot && isSystemDisabled(packageName, userId)) {
            return false to NEED_ROOT_FOR_SYSTEM_DISABLE
        }
        return false to detail
    }

    private fun appExists(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, APP_INFO_FLAGS)
        true
    }.getOrDefault(false)

    private fun resolveUserId(useRoot: Boolean): Int {
        val shellUser = TaskManagerUtil.runShellCommandLine("am get-current-user", useRoot = useRoot)
            .output
            .trim()
            .toIntOrNull()
        if (shellUser != null) return shellUser
        return currentUserId()
    }

    private fun currentUserId(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Process.myUserHandle().hashCode()
        }
        return runCatching {
            Process.myUserHandle().javaClass.getMethod("getIdentifier")
                .invoke(Process.myUserHandle()) as Int
        }.getOrDefault(0)
    }

    private fun bindShizukuPackageManager(): Any {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        return HiddenFrameworkAccess.bindPackageManager(binder)
    }

    private fun getApplicationEnabledSetting(packageName: String, userId: Int): Int? = runCatching {
        val packageManager = bindShizukuPackageManager()
        packageManager.javaClass.getMethod(
            "getApplicationEnabledSetting",
            String::class.java,
            Int::class.javaPrimitiveType,
        ).invoke(packageManager, packageName, userId) as Int
    }.getOrNull()

    private fun isSystemDisabled(packageName: String, userId: Int): Boolean {
        val state = getApplicationEnabledSetting(packageName, userId) ?: return false
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun setViaShizukuPackageManager(
        context: Context,
        packageName: String,
        disabled: Boolean,
        shizukuRoot: Boolean,
        userId: Int,
        enabledState: Int = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
    ): AttemptResult {
        return runCatching {
            val packageManager = bindShizukuPackageManager()
            val newState = when {
                !disabled -> enabledState
                shizukuRoot -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            packageManager.javaClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            ).invoke(
                packageManager,
                packageName,
                newState,
                PackageManager.DONT_KILL_APP,
                userId,
                BuildConfig.APPLICATION_ID,
            )
            if (waitForState(context, packageName, disabled)) {
                AttemptResult.Success
            } else {
                AttemptResult.Failure("shizuku pm state mismatch")
            }
        }.getOrElse { error ->
            AttemptResult.Failure(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun setViaShell(
        context: Context,
        packageName: String,
        disabled: Boolean,
        userId: Int,
        useRoot: Boolean,
    ): AttemptResult {
        val verb = if (disabled) "disable" else "enable"
        val commands = if (disabled) {
            buildList {
                add("pm $verb --user $userId $packageName")
                if (useRoot) add("pm $verb $packageName")
            }
        } else {
            buildList {
                if (useRoot) add("pm $verb $packageName")
                add("pm $verb --user $userId $packageName")
                if (!useRoot) add("pm $verb $packageName")
            }
        }
        var lastOutput = ""
        for (command in commands) {
            val result = TaskManagerUtil.runShellCommandLine(command, useRoot = useRoot)
            lastOutput = result.output.trim().ifBlank { lastOutput }
            if (waitForState(context, packageName, disabled)) {
                return AttemptResult.Success
            }
        }
        return AttemptResult.Failure(lastOutput)
    }

    private fun waitForState(context: Context, packageName: String, disabled: Boolean): Boolean {
        repeat(8) {
            if (isAppDisabled(context, packageName) == disabled) return true
            Thread.sleep(60)
        }
        return isAppDisabled(context, packageName) == disabled
    }

    private sealed interface AttemptResult {
        data object Success : AttemptResult
        data class Failure(val detail: String) : AttemptResult
    }

    const val NEED_ROOT_FOR_SYSTEM_DISABLE = "__need_root_for_system_disable__"
}
