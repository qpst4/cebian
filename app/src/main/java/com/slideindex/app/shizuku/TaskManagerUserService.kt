package com.slideindex.app.shizuku

import android.content.Context
import androidx.annotation.Keep

class TaskManagerUserService() : ITaskManagerService.Stub() {

    private val shell = TaskManagerShellExecutor
    private val shellRunner = ShellCommandRunner { args -> shell.shellCommand(*args) }
    private val tasks = TaskManagerTaskOperations(shellRunner)
    private val shortcuts = TaskManagerShortcutResolver(shell)
    private val freeWindow = TaskManagerFreeWindowOperations(DefaultTaskShellPort, tasks)

    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun removeTaskById(taskIdStr: String?): Boolean =
        tasks.removeTaskById(taskIdStr)

    override fun getFrontTaskId(): String =
        tasks.getFrontTaskId()

    override fun getFrontTaskPackage(): String =
        tasks.getFrontTaskPackage()

    override fun getApiVersion(): Int = API_VERSION

    override fun getTaskIdsForPackage(packageName: String?): Array<String> =
        tasks.getTaskIdsForPackage(packageName)

    override fun getRecentTaskPackages(): Array<String> =
        tasks.getRecentTaskPackages()

    override fun getRecentTasks(): Array<String> =
        tasks.getRecentTasks()

    override fun switchToTask(
        taskIdStr: String?,
        identifier: String?,
        topComponentStr: String?,
    ): Boolean = tasks.switchToTask(taskIdStr, identifier, topComponentStr)

    override fun showVoiceAssistant(): Boolean =
        tasks.showVoiceAssistant()

    override fun runShellCommand(cmd: Array<out String>?): Boolean {
        if (cmd.isNullOrEmpty()) return false
        return shell.shellCommand(*cmd)
    }

    override fun runShellCommandOutput(cmd: Array<out String>?): String {
        if (cmd.isNullOrEmpty()) return shell.formatShellOutput(-1, "Empty command")
        val result = shell.shellCommandWithOutput(*cmd)
        return shell.formatShellOutput(result.exitCode, result.output)
    }

    override fun runShellCommandLine(command: String?, useRoot: Boolean, forceAdb: Boolean): String {
        val trimmed = command?.trim().orEmpty()
        if (trimmed.isEmpty()) return shell.formatShellOutput(-1, "Empty command")
        val wantRoot = useRoot && !forceAdb
        val result = when {
            wantRoot -> shell.runAsRootUser(trimmed)
            else -> shell.runAsShellUser(trimmed)
        }
        return shell.formatShellOutput(result.exitCode, result.output)
    }

    override fun probeRootAvailable(): Boolean =
        shell.probeRootAvailable()

    override fun forceStopPackage(packageName: String?): Boolean =
        tasks.forceStopPackage(packageName)

    override fun getPublishedShortcuts(packageName: String?): Array<String> =
        shortcuts.getPublishedShortcuts(packageName)

    override fun getAllPublishedShortcuts(): Array<String> =
        shortcuts.getAllPublishedShortcuts()

    override fun startPublishedShortcut(packageName: String?, shortcutId: String?): Boolean =
        tasks.startPublishedShortcut(packageName, shortcutId)

    override fun moveTaskToFreeWindow(
        taskIdStr: String?,
        windowingMode: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean = freeWindow.moveTaskToFreeWindow(taskIdStr, windowingMode, left, top, right, bottom)

    companion object {
        const val API_VERSION = ShizukuUserServiceHost.SERVICE_BUILD
        const val SHELL_DOWNGRADE_HINT = TaskManagerShellExecutor.SHELL_DOWNGRADE_HINT
    }
}
