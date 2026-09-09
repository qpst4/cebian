package com.slideindex.app.shizuku

import android.util.Log

internal fun interface ShellCommandRunner {
    fun shellCommand(vararg cmd: String): Boolean
}

internal interface RecentsReader {
    fun listTasks(): List<SystemRecentsAccess.Task>
    fun removeTask(taskId: Int): Boolean
    fun frontTask(): SystemRecentsAccess.Task?
    fun findTaskId(identifier: String): Int?
    fun switchToTask(taskId: Int): Boolean
    fun matchesPackage(task: SystemRecentsAccess.Task, packageName: String): Boolean
}

private object DefaultShellRunner : ShellCommandRunner {
    override fun shellCommand(vararg cmd: String): Boolean =
        TaskManagerShellExecutor.shellCommand(*cmd)
}

private object SystemRecentsReader : RecentsReader {
    override fun listTasks(): List<SystemRecentsAccess.Task> = SystemRecentsAccess.listTasks()

    override fun removeTask(taskId: Int): Boolean = SystemRecentsAccess.removeTask(taskId)

    override fun frontTask(): SystemRecentsAccess.Task? = SystemRecentsAccess.frontTask()

    override fun findTaskId(identifier: String): Int? = SystemRecentsAccess.findTaskId(identifier)

    override fun switchToTask(taskId: Int): Boolean = SystemRecentsAccess.switchToTask(taskId)

    override fun matchesPackage(task: SystemRecentsAccess.Task, packageName: String): Boolean =
        SystemRecentsAccess.matchesPackage(task, packageName)
}

internal class TaskManagerTaskOperations(
    private val shell: ShellCommandRunner = DefaultShellRunner,
    private val recents: RecentsReader = SystemRecentsReader
) {

    fun removeTaskById(taskIdStr: String?): Boolean {
        val id = taskIdStr?.toIntOrNull()?.takeIf { it > 0 } ?: return false
        if (recents.removeTask(id)) {
            Log.i(TAG, "removeTaskById($id) via system API succeeded")
            return true
        }
        val commands = listOf(
            arrayOf("cmd", "activity", "task", "remove", id.toString()),
            arrayOf("cmd", "activity", "task", "remove-task", id.toString()),
            arrayOf("am", "task", "remove", id.toString()),
            arrayOf("am", "stack", "remove", id.toString())
        )
        for (command in commands) {
            if (shell.shellCommand(*command)) {
                Log.i(TAG, "removeTaskById($id) via ${command.joinToString(" ")} succeeded")
                return true
            }
        }
        Log.w(TAG, "removeTaskById($id) failed")
        return false
    }

    fun getFrontTaskId(): String {
        return try {
            readFrontTask()?.taskId?.toString().orEmpty().also {
                Log.i(TAG, "getFrontTaskId -> $it")
            }
        } catch (error: Exception) {
            Log.e(TAG, "getFrontTaskId failed", error)
            ""
        }
    }

    fun getFrontTaskPackage(): String {
        return try {
            readFrontTask()?.packageName?.takeIf { it.isNotBlank() }.orEmpty().also {
                Log.i(TAG, "getFrontTaskPackage -> $it")
            }
        } catch (error: Exception) {
            Log.e(TAG, "getFrontTaskPackage failed", error)
            ""
        }
    }

    fun getTaskIdsForPackage(packageName: String?): Array<String> {
        if (packageName.isNullOrBlank()) return emptyArray()
        return recents.listTasks()
            .filter { recents.matchesPackage(it, packageName) }
            .map { it.taskId }
            .filter { it > 0 }
            .distinct()
            .map { it.toString() }
            .toTypedArray()
    }

    fun getRecentTaskPackages(): Array<String> {
        return recents.listTasks()
            .map { it.packageName }
            .distinct()
            .toTypedArray()
    }

    fun getRecentTasks(): Array<String> {
        val entries = recents.listTasks()
        Log.i(TAG, "getRecentTasks -> ${entries.size}")
        return entries.map(::formatRecentTaskRow).toTypedArray()
    }

    fun switchToTask(
        taskIdStr: String?,
        identifier: String?,
        topComponentStr: String?
    ): Boolean {
        val rawId = identifier?.trim().orEmpty()
        val knownTaskId = taskIdStr?.toIntOrNull()?.takeIf { it > 0 }
        val resolvedId = knownTaskId
            ?: if (rawId.isNotEmpty()) recents.findTaskId(rawId) else null
        if (resolvedId == null || resolvedId <= 0) {
            Log.w(TAG, "switchToTask unresolved taskIdStr=$taskIdStr identifier=$rawId")
            return false
        }
        return recents.switchToTask(resolvedId)
    }

    fun showVoiceAssistant(): Boolean {
        if (shell.shellCommand("cmd", "voiceinteraction", "show")) {
            Log.i(TAG, "showVoiceAssistant via cmd voiceinteraction show succeeded")
            return true
        }
        Log.w(TAG, "showVoiceAssistant failed")
        return false
    }

    fun forceStopPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val stopped = shell.shellCommand("am", "force-stop", packageName)
        Log.i(TAG, "forceStopPackage($packageName) -> $stopped")
        return stopped
    }

    fun startPublishedShortcut(packageName: String?, shortcutId: String?): Boolean {
        if (packageName.isNullOrBlank() || shortcutId.isNullOrBlank()) return false
        val attempts = listOf(
            arrayOf("cmd", "shortcut", "start-shortcut", "--user", "0", packageName, shortcutId),
            arrayOf("cmd", "shortcut", "start-shortcut", packageName, shortcutId)
        )
        val started = attempts.any { shell.shellCommand(*it) }
        Log.i(TAG, "startPublishedShortcut($packageName, $shortcutId) -> $started")
        return started
    }

    fun readFrontTask(): ShellTaskEntry? =
        recents.frontTask()?.let { SystemRecentsAccess.toShellEntry(it) }

    companion object {
        private const val TAG = "TaskManagerUserService"

        internal fun formatRecentTaskRow(task: SystemRecentsAccess.Task): String {
            val topComponent = task.component.takeIf { it.contains('/') }.orEmpty()
            return "${task.taskId}\t${task.component}\t${task.title.orEmpty()}\t$topComponent"
        }
    }
}
