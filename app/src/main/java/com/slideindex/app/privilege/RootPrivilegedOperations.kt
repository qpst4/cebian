package com.slideindex.app.privilege

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shizuku.ShellRecentsReader
import com.slideindex.app.shizuku.ShellCommandRunner
import com.slideindex.app.shizuku.TaskManagerFreeWindowOperations
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import com.slideindex.app.shizuku.TaskManagerShortcutResolver
import com.slideindex.app.shizuku.TaskManagerTaskOperations
import com.slideindex.app.shizuku.TaskShellPort
import com.slideindex.app.shizuku.RootTaskShellPort
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.settings.resolvedFreeWindowMode

internal object RootPrivilegedOperations {
    private val shellPort: TaskShellPort = RootTaskShellPort
    private val shellRunner = ShellCommandRunner { args ->
        shellPort.shellCommand(*args)
    }
    private val tasks = TaskManagerTaskOperations(
        shell = shellRunner,
        recents = ShellRecentsReader(shellPort),
    )
    private val freeWindow = TaskManagerFreeWindowOperations(shell = shellPort, tasks = tasks)

    fun probeRootAvailable(): Boolean = TaskManagerShellExecutor.probeRootAvailable()

    fun removeTaskById(taskId: String?): Boolean = tasks.removeTaskById(taskId)

    fun getFrontTaskId(): String = tasks.getFrontTaskId()

    fun getFrontTaskPackage(): String = tasks.getFrontTaskPackage()

    fun getTaskIdsForPackage(packageName: String?): Array<String> =
        tasks.getTaskIdsForPackage(packageName)

    fun getRecentTaskPackages(): Array<String> = tasks.getRecentTaskPackages()

    fun getRecentTasks(): Array<String> = tasks.getRecentTasks()

    fun switchToTask(taskIdStr: String?, identifier: String?, topComponentStr: String?): Boolean =
        tasks.switchToTask(taskIdStr, identifier, topComponentStr)

    fun showVoiceAssistant(): Boolean = tasks.showVoiceAssistant()

    fun forceStopPackage(packageName: String?): Boolean = tasks.forceStopPackage(packageName)

    fun getPublishedShortcuts(packageName: String?): Array<String> =
        TaskManagerShortcutResolver(TaskManagerShellExecutor, preferRoot = true)
            .getPublishedShortcuts(packageName)

    fun getAllPublishedShortcuts(): Array<String> =
        TaskManagerShortcutResolver(TaskManagerShellExecutor, preferRoot = true)
            .getAllPublishedShortcuts()

    fun startPublishedShortcut(packageName: String?, shortcutId: String?): Boolean =
        tasks.startPublishedShortcut(packageName, shortcutId)

    fun runShellCommandLine(command: String, timeoutMs: Long): com.slideindex.app.util.TaskManagerUtil.ShellCommandResult {
        val result = TaskManagerShellExecutor.runAsRootUser(command.trim(), timeoutMs)
        return com.slideindex.app.util.TaskManagerUtil.ShellCommandResult(result.exitCode, result.output)
    }

    fun runShellCommand(vararg cmd: String): Boolean = shellPort.shellCommand(*cmd)

    fun runShellCommandOutput(vararg cmd: String): com.slideindex.app.util.TaskManagerUtil.ShellCommandResult {
        val output = shellPort.shellOutput(*cmd)
        val success = !output.startsWith("Command timed out") && !output.contains("Execution failed")
        return com.slideindex.app.util.TaskManagerUtil.ShellCommandResult(
            exitCode = if (success) 0 else -1,
            output = output,
        )
    }

    fun moveTaskToFreeWindow(
        taskId: String,
        settings: AppSettings,
        context: android.content.Context,
    ): Boolean {
        val bounds = FreeWindowLauncher.launchBounds(context, settings)
        val mode = settings.resolvedFreeWindowMode().windowingMode
        return freeWindow.moveTaskToFreeWindow(
            taskId,
            mode,
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
        )
    }
}
