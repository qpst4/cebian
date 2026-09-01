package com.slideindex.app.util

import android.os.SystemClock
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandTemplate
import com.slideindex.app.shell.ShellTemplateContext

object ShellCommandExecutor {
    private const val ROOT_PROBE_CACHE_MS = 30_000L

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    @Volatile
    private var rootProbeAtMs = 0L

    fun execute(
        command: ShellCommand,
        templateContext: ShellTemplateContext? = null,
    ): TaskManagerUtil.ShellCommandResult {
        val useRoot = resolveUseRoot()
        val expanded = templateContext?.let {
            ShellCommandTemplate.expand(command.command, it)
        } ?: command.command
        return TaskManagerUtil.runShellCommandLine(
            command = expanded,
            useRoot = useRoot,
        )
    }

    fun probeRootAvailable(): Boolean = TaskManagerUtil.probeRootAvailable()

    fun invalidateRootCache() {
        cachedRootAvailable = null
        rootProbeAtMs = 0L
    }

    private fun resolveUseRoot(): Boolean {
        if (PrivilegeGateway.isRootMode()) return true
        val now = SystemClock.elapsedRealtime()
        cachedRootAvailable?.let { cached ->
            if (now - rootProbeAtMs < ROOT_PROBE_CACHE_MS) return cached
        }
        val available = probeRootAvailable()
        cachedRootAvailable = available
        rootProbeAtMs = now
        return available
    }
}
