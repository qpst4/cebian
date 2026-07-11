package com.slideindex.app.shizuku

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.slideindex.app.util.TaskExclusions

/**
 * Generic system recents access through IActivityTaskManager / IActivityManager.
 * Intended to run inside the Shizuku UserService (adb/shell uid).
 */
internal object SystemRecentsAccess {

    private const val TAG = "SystemRecentsAccess"
    private const val MAX_TASKS = 40

    private const val RECENT_WITH_EXCLUDED = 0x0001
    private const val RECENT_IGNORE_UNAVAILABLE = 0x0002
    private const val RECENT_INCLUDE_PROFILES = 0x0004
    private const val RECENT_FLAGS =
        RECENT_WITH_EXCLUDED or RECENT_IGNORE_UNAVAILABLE or RECENT_INCLUDE_PROFILES

    private const val START_SUCCESS = 0
    private const val START_DELIVERED_TO_TOP = 3

    data class Task(
        val taskId: Int,
        val packageName: String,
        val component: String,
        val title: String?,
    )

    fun listTasks(): List<Task> {
        val merged = LinkedHashMap<Int, Task>()
        for (manager in taskManagers()) {
            queryManager(manager, merged)
        }
        val tasks = merged.values.filter { task ->
            task.packageName !in TaskExclusions.LAUNCHER_AND_SYSTEM
        }
        Log.i(TAG, "listTasks size=${tasks.size}")
        return tasks
    }

    fun frontTask(): Task? = listTasks().firstOrNull()

    fun findTaskId(identifier: String): Int? {
        val needle = identifier.trim()
        if (needle.isEmpty()) return null
        return listTasks().firstOrNull { task ->
            task.component == needle ||
                task.packageName == needle ||
                task.component.startsWith("$needle/") ||
                task.component.startsWith("$needle.")
        }?.taskId
    }

    fun matchesPackage(task: Task, packageName: String): Boolean {
        val target = packageName.trim()
        if (target.isEmpty()) return false
        return task.packageName == target ||
            task.component.startsWith("$target/") ||
            task.component.startsWith("$target.")
    }

    fun switchToTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        val atm = primaryTaskManager() ?: return false
        if (startActivityFromRecents(atm, taskId)) {
            Log.i(TAG, "switchToTask($taskId) via startActivityFromRecents")
            return true
        }
        if (moveTaskToFront(atm, taskId)) {
            Log.i(TAG, "switchToTask($taskId) via moveTaskToFront")
            return true
        }
        Log.w(TAG, "switchToTask($taskId) failed")
        return false
    }

    fun removeTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        val atm = primaryTaskManager() ?: return false
        val removed = runCatching {
            SystemReflect.invoke(atm, "removeTask", taskId) as? Boolean
        }.getOrNull() == true ||
            runCatching {
                SystemReflect.invoke(atm, "removeTask", taskId, true) as? Boolean
            }.getOrNull() == true
        Log.i(TAG, "removeTask($taskId) -> $removed")
        return removed
    }

    fun toShellEntry(task: Task): ShellTaskEntry =
        ShellTaskEntry(
            taskId = task.taskId,
            packageName = task.packageName,
            rawIdentifier = task.component,
            taskTitle = task.title,
        )

    private fun queryManager(manager: Any, out: LinkedHashMap<Int, Task>) {
        val userId = currentUserId()
        val queries = listOf(
            { getRecentTasks(manager, MAX_TASKS, RECENT_FLAGS, userId) },
            { getRecentTasks(manager, MAX_TASKS, 0, userId) },
            { getRecentTasks(manager, MAX_TASKS, RECENT_FLAGS, 0) },
            { getTasks3(manager, MAX_TASKS, false, false) },
            { getTasks3(manager, MAX_TASKS, false, true) },
            { getTasks2(manager, MAX_TASKS, 1) },
            { getTasks2(manager, MAX_TASKS, 0) },
            { getTasks1(manager, MAX_TASKS) },
        )
        for (query in queries) {
            val raw = runCatching { query() }.getOrNull() ?: continue
            for (item in raw) {
                parseTask(item)?.let { task ->
                    if (task.taskId > 0) {
                        out.putIfAbsent(task.taskId, task)
                    }
                }
            }
        }
    }

    private fun parseTask(raw: Any): Task? {
        val taskId = SystemReflect.readInt(raw, "getTaskId", "getId", "taskId", "id")
            ?: SystemReflect.readField(raw, "key")?.let { key ->
                SystemReflect.readInt(key, "id", "taskId")
            }
            ?: return null
        if (taskId <= 0) return null

        val component = SystemReflect.readComponent(
            raw,
            "getTopActivity",
            "getRealActivity",
            "getBaseActivity",
            "getOrigActivity",
            "topActivity",
            "realActivity",
            "baseActivity",
            "origActivity",
        )
            ?: SystemReflect.readComponentFromInfo(raw, "topActivityInfo", "baseActivityInfo")
            ?: (SystemReflect.readField(raw, "baseIntent") as? Intent)?.component

        val packageName = component?.packageName
            ?: SystemReflect.readString(raw, "packageName", "processName")
            ?: (SystemReflect.readField(raw, "baseIntent") as? Intent)?.`package`
            ?: return null

        val componentName = component?.flattenToShortString() ?: packageName
        val title = readTitle(raw)
        return Task(taskId, packageName, componentName, title)
    }

    private fun readTitle(raw: Any): String? {
        val description = SystemReflect.invoke(raw, "getTaskDescription")
            ?: SystemReflect.readField(raw, "taskDescription")
        return description?.let {
            SystemReflect.readCharSequence(it, "getLabel", "label")?.toString()?.trim()
        }?.takeIf { it.isNotEmpty() }
    }

    private fun getRecentTasks(manager: Any, maxNum: Int, flags: Int, userId: Int): List<Any>? =
        SystemReflect.unwrapList(
            SystemReflect.invoke(manager, "getRecentTasks", maxNum, flags, userId),
        )

    private fun getTasks3(
        manager: Any,
        maxNum: Int,
        filterOnlyVisibleRecents: Boolean,
        keepIntentExtra: Boolean,
    ): List<Any>? =
        SystemReflect.unwrapList(
            SystemReflect.invoke(
                manager,
                "getTasks",
                maxNum,
                filterOnlyVisibleRecents,
                keepIntentExtra,
            ),
        )

    private fun getTasks2(manager: Any, maxNum: Int, flags: Int): List<Any>? =
        SystemReflect.unwrapList(SystemReflect.invoke(manager, "getTasks", maxNum, flags))

    private fun getTasks1(manager: Any, maxNum: Int): List<Any>? =
        SystemReflect.unwrapList(SystemReflect.invoke(manager, "getTasks", maxNum))

    private fun startActivityFromRecents(atm: Any, taskId: Int): Boolean {
        val options = ActivityOptions.makeBasic().toBundle() ?: Bundle()
        val result = SystemReflect.invoke(atm, "startActivityFromRecents", taskId, options) as? Number
            ?: return false
        val code = result.toInt()
        return code in START_SUCCESS..START_DELIVERED_TO_TOP
    }

    private fun moveTaskToFront(atm: Any, taskId: Int): Boolean =
        runCatching {
            SystemReflect.invoke(atm, "moveTaskToFront", taskId, 0, null)
            true
        }.getOrDefault(false)

    private fun primaryTaskManager(): Any? = taskManagers().firstOrNull()

    @SuppressLint("PrivateApi") // ActivityTaskManager / ActivityManager system service binders
    private fun taskManagers(): List<Any> {
        val managers = LinkedHashSet<Any>()
        runCatching { managers += bindActivityTaskManager() }
        runCatching {
            val clazz = Class.forName("android.app.ActivityTaskManager")
            managers += clazz.getMethod("getService").invoke(null)!!
        }
        runCatching {
            val clazz = Class.forName("android.app.ActivityManager")
            managers += clazz.getMethod("getService").invoke(null)!!
        }
        runCatching { managers += bindActivityManager() }
        return managers.toList()
    }

    @SuppressLint("PrivateApi") // IActivityTaskManager via ServiceManager
    private fun bindActivityTaskManager(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "activity_task") as IBinder
        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IActivityTaskManager unavailable")
    }

    @SuppressLint("PrivateApi") // IActivityManager via ServiceManager
    private fun bindActivityManager(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "activity") as IBinder
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IActivityManager unavailable")
    }

    private fun currentUserId(): Int = runCatching {
        Process.myUserHandle().javaClass.getMethod("getIdentifier")
            .invoke(Process.myUserHandle()) as Int
    }.getOrDefault(0)
}
