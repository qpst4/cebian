package com.slideindex.app.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.TaskManagerUserService
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object TaskManagerUtil {

    private const val TAG = "TaskManagerUtil"
    private const val PREFETCH_DEBOUNCE_MS = 800L
    private const val MIN_RECENT_TASKS_API = 11
    private const val MIN_SWITCH_TO_TASK_API = 12
    private const val MIN_REMOVE_TASK_API = 1
    private const val MIN_TASK_IDS_API = 3
    private const val MIN_FREE_WINDOW_API = 5
    private const val MIN_FRONT_PACKAGE_API = 7
    private const val MIN_FORCE_STOP_API = 8
    private const val MIN_SHORTCUTS_API = 9
    const val REQUEST_CODE = 1001

    data class RecentTaskRef(
        val taskId: Int,
        val identifier: String,
        val title: String? = null,
        val topComponent: String? = null,
    )

    @Volatile
    private var service: ITaskManagerService? = null

    @Volatile
    private var pendingBindLatch: CountDownLatch? = null

    private val bindLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val taskWorkerLock = Any()

    private var cachedServiceArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    private var cachedRecentPackages: List<String> = emptyList()

    @Volatile
    private var lastRecentRefreshElapsedMs = 0L

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                ITaskManagerService.Stub.asInterface(binder)
            } else {
                Log.e(TAG, "UserService connected with invalid binder")
                null
            }
            if (service != null) {
                Log.i(TAG, "UserService connected")
            }
            pendingBindLatch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UserService disconnected")
            service = null
        }
    }

    fun isShizukuRunning(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean =
        isShizukuRunning() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun checkAndRequestPermission(activity: Activity): Boolean {
        if (!isShizukuRunning()) return false
        return if (hasPermission()) {
            true
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
            false
        }
    }

    fun requestPermission() {
        if (isShizukuRunning() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun warmUp() {
        if (!hasPermission()) return
        Thread {
            runOnTaskWorker {
                bindFreshService()
                RecentTasksLoader.syncFromSystem(SlideIndexApp.instance.appRepository)
            }
        }.start()
    }

    fun prefetchRecentTasks() {
        if (!hasPermission()) return
        val elapsed = SystemClock.elapsedRealtime() - lastRecentRefreshElapsedMs
        if (elapsed in 0 until PREFETCH_DEBOUNCE_MS) return
        Thread {
            runOnTaskWorker {
                RecentTasksLoader.syncFromSystem(SlideIndexApp.instance.appRepository)
            }
        }.start()
    }

    fun invalidateRecentCache() {
        lastRecentRefreshElapsedMs = 0L
    }

    fun peekRecentTaskPackages(): List<String> = cachedRecentPackages

    fun refreshRecentTaskPackages(): List<String> {
        return refreshRecentTasks().map { it.identifier }
    }

    fun refreshRecentTasks(): List<RecentTaskRef> {
        if (!hasPermission()) return emptyList()
        val taskService = bindService(appContext()) ?: bindFreshService() ?: return cachedTasksOrEmpty()
        val tasks = runCatching {
            loadRecentTasksFromService(taskService)
        }.getOrElse { error ->
            Log.e(TAG, "refreshRecentTasks failed, falling back to packages", error)
            loadRecentTasksFromPackages(taskService)
        }
        cachedRecentPackages = tasks.map { it.identifier }
        markRecentRefresh()
        Log.i(
            TAG,
            "refreshRecentTasks (${tasks.size}): ${tasks.joinToString { "${it.taskId}|${it.identifier}" }}",
        )
        return tasks
    }

    private fun loadRecentTasksFromService(taskService: ITaskManagerService): List<RecentTaskRef> {
        val api = runCatching { taskService.apiVersion }.getOrDefault(0)
        if (api < MIN_RECENT_TASKS_API) {
            Log.w(TAG, "loadRecentTasksFromService using package fallback (api=$api)")
            return loadRecentTasksFromPackages(taskService)
        }
        val tasks = runCatching {
            taskService.getRecentTasks().mapNotNull(::parseRecentTaskRow)
        }.getOrElse { error ->
            Log.e(TAG, "getRecentTasks failed", error)
            emptyList()
        }
        if (tasks.isNotEmpty()) return tasks
        return loadRecentTasksFromPackages(taskService)
    }

    private fun loadRecentTasksFromPackages(taskService: ITaskManagerService): List<RecentTaskRef> {
        return taskService.getRecentTaskPackages()
            .filter { it.isNotBlank() }
            .map { RecentTaskRef(taskId = 0, identifier = it) }
    }

    private fun parseRecentTaskRow(row: String): RecentTaskRef? {
        val trimmed = row.trim()
        if (trimmed.isEmpty()) return null
        val tabParts = trimmed.split('\t')
        if (tabParts.size >= 2) {
            val taskId = tabParts[0].trim().toIntOrNull() ?: return null
            val identifier = tabParts[1].trim()
            if (identifier.isEmpty()) return null
            val title = tabParts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
            val topComponent = tabParts.getOrNull(3)?.trim()?.takeIf { it.contains('/') }
            return RecentTaskRef(taskId, identifier, title, topComponent)
        }
        val match = Regex("^(\\d+)\\s+(\\S+)$").find(trimmed) ?: return null
        val taskId = match.groupValues[1].toIntOrNull() ?: return null
        val identifier = match.groupValues[2].trim()
        if (identifier.isEmpty()) return null
        return RecentTaskRef(taskId, identifier)
    }

    private fun cachedTasksOrEmpty(): List<RecentTaskRef> {
        if (cachedRecentPackages.isEmpty()) return emptyList()
        return cachedRecentPackages.map { RecentTaskRef(taskId = 0, identifier = it) }
    }

    private fun markRecentRefresh() {
        lastRecentRefreshElapsedMs = SystemClock.elapsedRealtime()
    }

    fun removePackageFromCache(packageName: String) {
        cachedRecentPackages = cachedRecentPackages.filterNot {
            RecentPackageResolver.matches(it, packageName)
        }
    }

    fun removeTaskById(taskId: Int): Boolean {
        if (taskId <= 0 || !hasPermission()) return false
        return runOnTaskWorker {
            bindFreshService(MIN_REMOVE_TASK_API)?.removeTaskById(taskId.toString()) == true
        }
    }

    fun switchToTask(
        taskId: Int,
        identifier: String = "",
        topComponent: String = "",
    ): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "switchToTask skipped: no Shizuku permission")
            return false
        }
        if (taskId <= 0 && identifier.isBlank()) {
            Log.w(TAG, "switchToTask skipped: no taskId or identifier")
            return false
        }
        val service = bindService(appContext()) ?: run {
            Log.e(TAG, "switchToTask failed: UserService unavailable")
            return false
        }
        val api = runCatching { service.apiVersion }.getOrDefault(0)
        if (api < MIN_SWITCH_TO_TASK_API) {
            Log.w(TAG, "switchToTask skipped: UserService api=$api lacks switch support")
            return false
        }
        return runCatching {
            service.switchToTask(
                if (taskId > 0) taskId.toString() else "",
                identifier,
                topComponent,
            )
        }.getOrElse { error ->
            Log.e(
                TAG,
                "switchToTask binder error taskId=$taskId identifier=$identifier component=$topComponent",
                error,
            )
            false
        }
    }

    fun resolveTaskIdForIdentifier(identifier: String): Int? {
        if (identifier.isBlank() || !hasPermission()) return null
        val service = bindService(appContext()) ?: bindFreshService() ?: return null
        val ids = runCatching { service.getTaskIdsForPackage(identifier.trim()).toList() }
            .getOrDefault(emptyList())
        return ids.singleOrNull()?.toIntOrNull() ?: ids.firstOrNull()?.toIntOrNull()
    }

    fun removeCurrentFrontAppTask(): Boolean {
        val packageName = OverlayService.foregroundPackage
        if (!packageName.isNullOrBlank()) {
            return removeTaskByPackage(packageName)
        }
        val taskService = bindService(appContext()) ?: return false
        return try {
            val taskId = taskService.getFrontTaskId().takeIf { it.isNotBlank() } ?: return false
            taskService.removeTaskById(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "removeCurrentFrontAppTask failed", e)
            false
        }
    }

    fun removeTaskByPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (TaskSwitcherLockStore.isLocked(appContext(), packageName)) return false
        return try {
            val taskService = bindFreshService(MIN_TASK_IDS_API) ?: return false
            val taskIds = taskService.getTaskIdsForPackage(packageName)
            if (taskIds.isEmpty()) return false
            taskIds.any { taskService.removeTaskById(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeTaskByPackage($packageName) failed", e)
            false
        }
    }

    fun forceStopPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!hasPermission()) return false
        return runOnTaskWorker {
            bindFreshService(MIN_FORCE_STOP_API)?.forceStopPackage(packageName) == true
        }
    }

    fun movePackageToFreeWindow(packageName: String, settings: AppSettings): Boolean {
        if (packageName.isBlank() || !hasPermission()) return false
        return runOnTaskWorker {
            val taskService = bindFreshService(MIN_FREE_WINDOW_API) ?: return@runOnTaskWorker false
            val taskId = taskService.getTaskIdsForPackage(packageName).firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return@runOnTaskWorker false
            invokeMoveTaskToFreeWindow(taskService, taskId, settings)
        }
    }

    fun getPublishedShortcuts(packageName: String): List<Pair<String, String>> {
        if (packageName.isBlank() || !hasPermission()) return emptyList()
        return runOnTaskWorker {
            val rows = bindFreshService(MIN_SHORTCUTS_API)?.getPublishedShortcuts(packageName).orEmpty()
            rows.mapNotNull { row ->
                val parts = row.split('\t', limit = 2)
                val id = parts.getOrNull(0)?.trim().orEmpty()
                if (id.isEmpty()) return@mapNotNull null
                val label = parts.getOrNull(1)?.trim().orEmpty().ifBlank { id }
                id to label
            }
        }
    }

    fun startPublishedShortcut(packageName: String, shortcutId: String): Boolean {
        if (packageName.isBlank() || shortcutId.isBlank() || !hasPermission()) return false
        return runOnTaskWorker {
            bindFreshService(MIN_SHORTCUTS_API)?.startPublishedShortcut(packageName, shortcutId) == true
        }
    }

    fun <T> runOnTaskWorker(block: () -> T): T {
        synchronized(taskWorkerLock) {
            return block()
        }
    }

    fun moveFrontTaskToFreeWindow(settings: AppSettings): Boolean {
        if (!hasPermission()) return false
        return runOnTaskWorker {
            moveFrontTaskToFreeWindowLocked(settings)
        }
    }

    private fun moveFrontTaskToFreeWindowLocked(settings: AppSettings): Boolean {
        return try {
            val taskService = bindFreshService(MIN_FRONT_PACKAGE_API) ?: return false
            val taskId = taskService.getFrontTaskId().takeIf { it.isNotBlank() } ?: run {
                Log.w(TAG, "moveFrontTaskToFreeWindow: no front task id")
                return false
            }
            val frontPackage = runCatching {
                taskService.getFrontTaskPackage().takeIf { it.isNotBlank() }
            }.getOrElse { error ->
                Log.w(TAG, "moveFrontTaskToFreeWindow: getFrontTaskPackage failed", error)
                null
            }
            if (frontPackage != null &&
                TaskExclusions.shouldSkipFreeWindow(frontPackage, appContext().packageName)
            ) {
                Log.w(TAG, "moveFrontTaskToFreeWindow: excluded front package=$frontPackage")
                return false
            }
            moveTaskToFreeWindowWithRetry(taskService, taskId, settings, frontPackage)
        } catch (error: Exception) {
            Log.e(TAG, "moveFrontTaskToFreeWindow failed", error)
            false
        }
    }

    private fun moveTaskToFreeWindowWithRetry(
        taskService: ITaskManagerService,
        taskId: String,
        settings: AppSettings,
        logPackage: String?,
    ): Boolean {
        var service = taskService
        var moved = invokeMoveTaskToFreeWindow(service, taskId, settings)
        if (!moved) {
            forceRestartUserService(appContext())
            synchronized(bindLock) { this.service = null }
            service = bindFreshService(MIN_FREE_WINDOW_API) ?: return false
            moved = invokeMoveTaskToFreeWindow(service, taskId, settings)
        }
        Log.i(TAG, "moveFrontTaskToFreeWindow: taskId=$taskId package=$logPackage moved=$moved")
        return moved
    }

    private fun invokeMoveTaskToFreeWindow(
        taskService: ITaskManagerService,
        taskId: String,
        settings: AppSettings,
    ): Boolean {
        return try {
            val bounds = FreeWindowLauncher.launchBounds(appContext(), settings)
            val mode = settings.resolvedFreeWindowMode().windowingMode
            taskService.moveTaskToFreeWindow(
                taskId,
                mode,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
            )
        } catch (e: Exception) {
            Log.e(TAG, "invokeMoveTaskToFreeWindow($taskId) failed", e)
            false
        }
    }

    private fun bindFreshService(minApi: Int = 0): ITaskManagerService? {
        var bound = bindService(appContext()) ?: return null
        var api = runCatching { bound.apiVersion }.getOrDefault(0)
        if (api < minApi) {
            Log.w(
                TAG,
                "bindFreshService: UserService api=$api stale, restarting for $minApi",
            )
            forceRestartUserService(appContext())
            synchronized(bindLock) { service = null }
            bound = bindService(appContext()) ?: return null
            api = runCatching { bound.apiVersion }.getOrDefault(0)
        }
        if (api < minApi) {
            Log.e(
                TAG,
                "bindFreshService: rebound UserService still stale api=$api expected=$minApi",
            )
            forceRestartUserService(appContext())
            return null
        }
        return bound
    }

    private fun forceRestartUserService(context: Context) {
        synchronized(bindLock) {
            runCatching {
                cachedServiceArgs?.let { Shizuku.unbindUserService(it, userServiceConnection, true) }
            }
            runCatching { service?.destroy() }
            service = null
            cachedServiceArgs = null
        }
    }

    fun getRecentTaskPackages(): List<String>? {
        if (!hasPermission()) return null
        val taskService = service?.takeIf { it.asBinder().pingBinder() }
            ?: bindService(appContext())
            ?: return null
        return try {
            taskService.getRecentTaskPackages().toList()
        } catch (e: Exception) {
            Log.e(TAG, "getRecentTaskPackages failed", e)
            null
        }
    }

    private fun appContext(): Context = SlideIndexApp.instance.applicationContext

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs {
        cachedServiceArgs?.let { return it }
        val version = TaskManagerUserService.API_VERSION
        return Shizuku.UserServiceArgs(
            ComponentName(context.packageName, TaskManagerUserService::class.java.name),
        )
            .daemon(true)
            .processNameSuffix("task_manager_v$version")
            .tag("TaskManagerUserService_v$version")
            .version(version)
            .also { cachedServiceArgs = it }
    }

    private fun bindService(context: Context): ITaskManagerService? {
        if (!hasPermission()) return null

        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        service = null

        synchronized(bindLock) {
            service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
            service = null

            val latch = CountDownLatch(1)
            pendingBindLatch = latch
            return try {
                val bindRunnable = Runnable {
                    runCatching {
                        Shizuku.bindUserService(userServiceArgs(context), userServiceConnection)
                    }.onFailure {
                        pendingBindLatch?.countDown()
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    bindRunnable.run()
                } else {
                    mainHandler.post(bindRunnable)
                }
                val connected = latch.await(15, TimeUnit.SECONDS)
                service?.takeIf { connected && it.asBinder().pingBinder() }
            } catch (e: Exception) {
                Log.e(TAG, "bindService failed", e)
                null
            } finally {
                pendingBindLatch = null
            }
        }
    }
}
