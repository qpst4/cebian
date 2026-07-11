package com.slideindex.app.shizuku

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.util.Log

internal class TaskManagerFreeWindowOperations(
    private val shell: TaskManagerShellExecutor = TaskManagerShellExecutor,
    private val tasks: TaskManagerTaskOperations = TaskManagerTaskOperations(),
) {

    fun moveTaskToFreeWindow(
        taskIdStr: String?,
        windowingMode: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        return try {
            val taskId = taskIdStr?.toIntOrNull()?.takeIf { it > 0 } ?: return false
            val bounds = Rect(left, top, right, bottom)
            val frontTaskId = tasks.readFrontTask()?.taskId
            val isFrontTask = frontTaskId == taskId
            Log.i(
                TAG,
                "moveTaskToFreeWindow taskId=$taskId frontTaskId=$frontTaskId isFront=$isFrontTask mode=$windowingMode bounds=$bounds",
            )
            if (isFrontTask) {
                moveFrontTaskToFreeWindow(taskId, windowingMode, bounds)
            } else {
                moveBackgroundTaskToFreeWindow(taskId, windowingMode, bounds)
            }
        } catch (error: Exception) {
            Log.e(TAG, "moveTaskToFreeWindow failed", error)
            false
        }
    }

    private fun moveFrontTaskToFreeWindow(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        if (moveTaskToFreeWindowViaRecents(taskId, windowingMode, bounds)) return true
        if (moveTaskToFreeWindowViaSystemApi(taskId, windowingMode, bounds)) return true
        if (moveTaskToFreeWindowViaShell(taskId, bounds)) return true
        for (mode in candidateWindowingModes(windowingMode)) {
            if (mode == windowingMode) continue
            if (moveTaskToFreeWindowViaRecents(taskId, mode, bounds)) return true
            if (moveTaskToFreeWindowViaSystemApi(taskId, mode, bounds)) return true
        }
        for (mode in candidateWindowingModes(windowingMode)) {
            if (relaunchViaShell(taskId, mode, bounds)) return true
        }
        return false
    }

    private fun moveBackgroundTaskToFreeWindow(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        for (mode in candidateWindowingModes(windowingMode)) {
            if (relaunchViaShell(taskId, mode, bounds)) return true
        }
        for (mode in candidateWindowingModes(windowingMode)) {
            if (moveTaskToFreeWindowViaSystemApi(taskId, mode, bounds)) return true
        }
        if (focusAndResizeTaskViaShell(taskId, bounds)) return true
        for (mode in candidateWindowingModes(windowingMode)) {
            if (moveTaskToFreeWindowViaRecents(taskId, mode, bounds)) return true
        }
        return moveTaskToFreeWindowViaShell(taskId, bounds)
    }

    private fun candidateWindowingModes(primary: Int): IntArray =
        linkedSetOf(primary, 11, 5, 100, 102).toIntArray()

    private fun moveTaskToFreeWindowViaRecents(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return try {
            val options = ActivityOptions.makeBasic()
            applyLaunchWindowingMode(options, windowingMode)
            options.setLaunchBounds(bounds)
            val bundle = options.toBundle() ?: Bundle()
            if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
                bundle.putInt(KEY_WINDOWING_MODE, windowingMode)
            }
            invokeStartActivityFromRecents(taskId, bundle)
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFreeWindowViaRecents failed taskId=$taskId", e)
            false
        }
    }

    private fun invokeStartActivityFromRecents(taskId: Int, bundle: Bundle): Boolean {
        return try {
            val atm = activityTaskManager()
            val result = SystemReflect.invoke(atm, "startActivityFromRecents", taskId, bundle) as? Number
                ?: return false
            val code = result.toInt()
            code in START_SUCCESS..START_DELIVERED_TO_TOP
        } catch (e: Exception) {
            Log.w(TAG, "invokeStartActivityFromRecents failed taskId=$taskId", e)
            false
        }
    }

    private fun applyLaunchWindowingMode(options: ActivityOptions, mode: Int) {
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, mode)
        } catch (_: Exception) {
        }
    }

    private fun moveTaskToFreeWindowViaSystemApi(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return try {
            val atm = activityTaskManager()
            var windowingApplied = false
            runCatching {
                atm.javaClass.getMethod(
                    "setTaskWindowingMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).invoke(atm, taskId, windowingMode, true)
                windowingApplied = true
            }
            runCatching {
                atm.javaClass.getMethod(
                    "resizeTask",
                    Int::class.javaPrimitiveType,
                    Rect::class.java,
                    Int::class.javaPrimitiveType,
                ).invoke(atm, taskId, bounds, RESIZE_MODE_SYSTEM)
            }
            val options = ActivityOptions.makeBasic()
            applyLaunchWindowingMode(options, windowingMode)
            options.setLaunchBounds(bounds)
            val bundle = options.toBundle() ?: Bundle()
            if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
                bundle.putInt(KEY_WINDOWING_MODE, windowingMode)
            }
            val movedToFront = runCatching {
                atm.javaClass.getMethod(
                    "moveTaskToFront",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Bundle::class.java,
                ).invoke(atm, taskId, 0, bundle) as? Boolean
            }.getOrNull() == true
            windowingApplied || movedToFront
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFreeWindowViaSystemApi failed taskId=$taskId", e)
            false
        }
    }

    private fun focusAndResizeTaskViaShell(taskId: Int, bounds: Rect): Boolean {
        val focused = shell.shellCommand("cmd", "activity", "task", "focus", taskId.toString())
        val resized = moveTaskToFreeWindowViaShell(taskId, bounds)
        return focused && resized
    }

    private fun moveTaskToFreeWindowViaShell(taskId: Int, bounds: Rect): Boolean {
        return shell.shellCommand(
            "cmd", "activity", "task", "resize",
            taskId.toString(),
            bounds.left.toString(),
            bounds.top.toString(),
            bounds.right.toString(),
            bounds.bottom.toString(),
        )
    }

    private fun relaunchViaShell(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        val taskListDump = shell.shellOutput("cmd", "activity", "task", "list")
        val activitiesDump = shell.shellOutput("dumpsys", "activity", "activities")
        val component = TaskShellParser.findComponentForTaskId(
            taskId,
            taskListDump,
            activitiesDump,
        ) ?: return false
        if (!shell.shellCommand(
                "am", "start", "-n", component,
                "--windowingMode", windowingMode.toString(),
                "--activity-single-top", "--activity-clear-top",
            )
        ) {
            return false
        }
        return moveTaskToFreeWindowViaShell(taskId, bounds)
    }

    @SuppressLint("PrivateApi") // IActivityTaskManager for freeform window ops
    private fun activityTaskManager(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "activity_task") as IBinder
        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IActivityTaskManager is null")
    }

    companion object {
        private const val TAG = "TaskManagerUserService"
        private const val RESIZE_MODE_SYSTEM = 0
        private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
        private const val START_SUCCESS = 0
        private const val START_DELIVERED_TO_TOP = 3
    }
}
