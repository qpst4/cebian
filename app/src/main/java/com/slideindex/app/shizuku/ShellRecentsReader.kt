package com.slideindex.app.shizuku

import android.os.SystemClock
import android.util.Log
import com.slideindex.app.util.TaskExclusions

/**
 * Recent-task queries via root/shell dumps. Used when privileged ops run in the app process
 * instead of the Shizuku UserService.
 */
internal class ShellRecentsReader(
    private val shell: TaskShellPort,
) : RecentsReader {

    @Volatile
    private var cachedDumps: ShellRecentsDumps? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    override fun listTasks(): List<SystemRecentsAccess.Task> {
        val dumps = dumps()
        var entries = TaskShellParser.listRecentTaskEntries(
            dumps.recents,
            dumps.taskList,
            dumps.activities,
        )
        if (entries.isEmpty() && dumps.taskList.isNotBlank()) {
            entries = TaskShellParser.listAllCmdTaskEntries(dumps.taskList)
            Log.i(TAG, "listTasks fallback cmd-only -> ${entries.size}")
        }
        val tasks = entries.mapNotNull { it.toRecentsTask() }
            .filter { it.packageName !in TaskExclusions.LAUNCHER_AND_SYSTEM }
        if (tasks.isEmpty()) {
            Log.w(
                TAG,
                "listTasks empty parsed=${entries.size} recents=${dumps.recents.length} " +
                    "taskList=${dumps.taskList.length} activities=${dumps.activities.length} " +
                    "taskPreview=${dumps.taskList.lineSequence().take(3).joinToString(" | ")}",
            )
        } else {
            Log.i(TAG, "listTasks size=${tasks.size}")
        }
        return tasks
    }

    override fun frontTask(): SystemRecentsAccess.Task? {
        val activities = dumps().activities.ifBlank {
            runPrivilegedOutput(
                "dumpsys activity activities 2>/dev/null | head -n 200",
                ACTIVITIES_TIMEOUT_MS,
            )
        }
        return TaskShellParser.findFrontTask(activities)?.toRecentsTask()
    }

    override fun findTaskId(identifier: String): Int? {
        val dumps = dumps()
        return TaskShellParser.findTaskIdForIdentifier(
            identifier,
            dumps.recents,
            dumps.taskList,
            dumps.activities,
        )
    }

    override fun switchToTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        if (shell.shellCommand("cmd", "activity", "task", "focus", taskId.toString())) {
            Log.i(TAG, "switchToTask($taskId) via cmd focus")
            return true
        }
        val dumps = dumps()
        val component = TaskShellParser.findComponentForTaskId(
            taskId,
            dumps.taskList,
            dumps.recents,
            dumps.activities,
        ) ?: return false
        val started = shell.shellCommand(
            "am",
            "start",
            "-n",
            component,
            "--activity-single-top",
            "--activity-clear-top",
        )
        Log.i(TAG, "switchToTask($taskId) via am start -> $started")
        return started
    }

    override fun removeTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        val script = buildString {
            append("cmd activity task remove $taskId 2>/dev/null && exit 0; ")
            append("cmd activity task remove-task $taskId 2>/dev/null && exit 0; ")
            append("am task remove $taskId 2>/dev/null && exit 0; ")
            append("am stack remove $taskId 2>/dev/null && exit 0; ")
            append("exit 1")
        }
        val result = TaskManagerShellExecutor.runPrivilegedCommand(script, REMOVE_TASK_TIMEOUT_MS)
        if (result.exitCode == 0) {
            Log.i(TAG, "removeTask($taskId) via batched script")
            invalidateCache()
            return true
        }
        Log.w(TAG, "removeTask($taskId) failed preview=${result.output.take(120)}")
        return false
    }

    override fun matchesPackage(task: SystemRecentsAccess.Task, packageName: String): Boolean =
        SystemRecentsAccess.matchesPackage(task, packageName)

    private fun dumps(): ShellRecentsDumps {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedDumps
        if (cached != null && now - cachedAtMs < CACHE_TTL_MS) {
            return cached
        }
        val fetched = fetchDumps()
        cachedDumps = fetched
        cachedAtMs = now
        return fetched
    }

    private fun invalidateCache() {
        cachedDumps = null
        cachedAtMs = 0L
    }

    private fun fetchDumps(): ShellRecentsDumps {
        val taskList = runPrivilegedOutput("cmd activity task list 2>/dev/null", TASK_LIST_TIMEOUT_MS)
        if (taskList.isNotBlank()) {
            Log.i(TAG, "fetchDumps taskList=${taskList.length}")
            return ShellRecentsDumps(recents = "", taskList = taskList, activities = "")
        }
        return fetchDumpsFull()
    }

    private fun fetchDumpsFull(): ShellRecentsDumps {
        val script = buildString {
            append("echo $MARKER_RECENTS; ")
            append("dumpsys activity recents 2>/dev/null | head -n 400; ")
            append("echo $MARKER_TASKLIST; ")
            append("cmd activity task list 2>/dev/null; ")
            append("echo $MARKER_ACTIVITIES; ")
            append("dumpsys activity activities 2>/dev/null | head -n 500")
        }
        val batched = TaskManagerShellExecutor.runPrivilegedCommand(script, FULL_FETCH_TIMEOUT_MS)
        if (TaskManagerShellExecutor.hasUsableOutput(batched)) {
            val dumps = splitDumpOutput(batched.output)
            Log.i(
                TAG,
                "fetchDumps full exit=0 recents=${dumps.recents.length} " +
                    "taskList=${dumps.taskList.length} activities=${dumps.activities.length}",
            )
            return dumps
        }
        Log.w(
            TAG,
            "fetchDumps full failed exit=${batched.exitCode} preview=${batched.output.take(160)}",
        )
        val taskList = runPrivilegedOutput("cmd activity task list 2>/dev/null", TASK_LIST_TIMEOUT_MS)
        return ShellRecentsDumps(recents = "", taskList = taskList, activities = "")
    }

    private fun runPrivilegedOutput(command: String, timeoutMs: Long): String {
        val result = TaskManagerShellExecutor.runPrivilegedCommand(command, timeoutMs)
        return if (TaskManagerShellExecutor.hasUsableOutput(result)) {
            result.output
        } else {
            Log.w(TAG, "runPrivilegedOutput failed cmd=$command preview=${result.output.take(120)}")
            ""
        }
    }

    private fun splitDumpOutput(output: String): ShellRecentsDumps =
        ShellRecentsDumps(
            recents = section(output, MARKER_RECENTS, MARKER_TASKLIST),
            taskList = section(output, MARKER_TASKLIST, MARKER_ACTIVITIES),
            activities = section(output, MARKER_ACTIVITIES, null),
        )

    private fun section(output: String, startMarker: String, endMarker: String?): String {
        val start = output.indexOf(startMarker)
        if (start < 0) return ""
        val contentStart = start + startMarker.length
        val contentEnd = endMarker?.let { marker ->
            output.indexOf(marker, contentStart).takeIf { it >= 0 } ?: output.length
        } ?: output.length
        return output.substring(contentStart, contentEnd).trim()
    }

    private data class ShellRecentsDumps(
        val recents: String,
        val taskList: String,
        val activities: String,
    )

    private fun ShellTaskEntry.toRecentsTask(): SystemRecentsAccess.Task? {
        if (taskId <= 0) return null
        val component = when {
            rawIdentifier.contains('/') -> rawIdentifier
            rawIdentifier.isNotBlank() -> rawIdentifier
            packageName.isNotBlank() -> packageName
            else -> return null
        }
        val pkg = packageName.ifBlank {
            component.substringBefore('/').ifBlank { component.substringBefore('.') }
        }.ifBlank { return null }
        return SystemRecentsAccess.Task(
            taskId = taskId,
            packageName = pkg,
            component = component,
            title = taskTitle,
        )
    }

    companion object {
        private const val TAG = "ShellRecentsReader"
        private const val TASK_LIST_TIMEOUT_MS = 5_000L
        private const val ACTIVITIES_TIMEOUT_MS = 5_000L
        private const val FULL_FETCH_TIMEOUT_MS = 8_000L
        private const val CACHE_TTL_MS = 3_000L
        private const val REMOVE_TASK_TIMEOUT_MS = 4_000L
        private const val MARKER_RECENTS = "___SI_RECENTS___"
        private const val MARKER_TASKLIST = "___SI_TASKLIST___"
        private const val MARKER_ACTIVITIES = "___SI_ACTIVITIES___"
    }
}
