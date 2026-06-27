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
    const val REQUEST_CODE = 1001

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
        if (!hasPermission()) return cachedRecentPackages
        val taskService = bindService(appContext()) ?: return cachedRecentPackages
        return try {
            cachedRecentPackages = taskService.getRecentTaskPackages().toList()
            markRecentRefresh()
            cachedRecentPackages
        } catch (e: Exception) {
            Log.e(TAG, "refreshRecentTaskPackages failed", e)
            cachedRecentPackages
        }
    }

    private fun markRecentRefresh() {
        lastRecentRefreshElapsedMs = SystemClock.elapsedRealtime()
    }

    fun removePackageFromCache(packageName: String) {
        cachedRecentPackages = cachedRecentPackages.filterNot { it == packageName }
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "removeCurrentFrontAppTask failed", e)
            false
        }
    }

    fun removeTaskByPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (TaskSwitcherLockStore.isLocked(appContext(), packageName)) return false
        val taskService = bindService(appContext()) ?: return false
        return try {
            val taskIds = taskService.getTaskIdsForPackage(packageName)
            if (taskIds.isEmpty()) return false
            taskIds.forEach { taskService.removeTaskById(it) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "removeTaskByPackage($packageName) failed", e)
            false
        }
    }

    fun forceStopPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!hasPermission()) return false
        return runOnTaskWorker {
            bindFreshService()?.forceStopPackage(packageName) == true
        }
    }

    fun movePackageToFreeWindow(packageName: String, settings: AppSettings): Boolean {
        if (packageName.isBlank() || !hasPermission()) return false
        return runOnTaskWorker {
            val taskService = bindFreshService() ?: return@runOnTaskWorker false
            val taskId = taskService.getTaskIdsForPackage(packageName).firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return@runOnTaskWorker false
            invokeMoveTaskToFreeWindow(taskService, taskId, settings)
        }
    }

    fun getPublishedShortcuts(packageName: String): List<Pair<String, String>> {
        if (packageName.isBlank() || !hasPermission()) return emptyList()
        return runOnTaskWorker {
            val rows = bindFreshService()?.getPublishedShortcuts(packageName).orEmpty()
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
            bindFreshService()?.startPublishedShortcut(packageName, shortcutId) == true
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
            val taskService = bindFreshService() ?: return false
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
            service = bindFreshService() ?: return false
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

    private fun bindFreshService(): ITaskManagerService? {
        var bound = bindService(appContext()) ?: return null
        if (verifyServiceApi(bound)) return bound
        Log.w(TAG, "bindFreshService: stale UserService, restarting")
        forceRestartUserService(appContext())
        synchronized(bindLock) { service = null }
        bound = bindService(appContext()) ?: return null
        if (!verifyServiceApi(bound)) {
            Log.e(TAG, "bindFreshService: API still unavailable; proceeding anyway")
        }
        return bound
    }

    private fun verifyServiceApi(taskService: ITaskManagerService): Boolean {
        return try {
            taskService.apiVersion == TaskManagerUserService.API_VERSION
        } catch (e: Exception) {
            Log.w(TAG, "UserService API probe failed", e)
            false
        }
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
