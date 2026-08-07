package com.slideindex.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import rikka.shizuku.Shizuku

object TaskManagerUtil {

    private const val TAG = "TaskManagerUtil"
    private const val MIN_SWITCH_TO_TASK_API = 12
    private const val MIN_REMOVE_TASK_API = 1
    private const val MIN_TASK_IDS_API = 3
    private const val MIN_FORCE_STOP_API = 8
    private const val MIN_SHORTCUTS_API = 9
    const val ROOT_PROBE_BINDER_TIMEOUT_MS = 45_000L
    const val REQUEST_CODE = 1001

    private val SHIZUKU_PACKAGES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager",
    )

    data class RecentTaskRef(
        val taskId: Int,
        val identifier: String,
        val title: String? = null,
        val topComponent: String? = null,
    )

    data class ShellCommandResult(
        val exitCode: Int,
        val output: String,
    ) {
        val success: Boolean get() = exitCode == 0
    }

    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun applicationContext(): Context = appContext()

    @Volatile
    private var warmUpInFlight = false

    private val taskWorkerLock = Any()

    private fun peekBoundService(): ITaskManagerService? = ShizukuUserServiceHost.peek()

    private fun bindService(context: Context): ITaskManagerService? =
        ShizukuUserServiceHost.ensure(context, minApi = 0)

    private fun bindFreshService(minApi: Int = 0): ITaskManagerService? =
        ShizukuUserServiceHost.ensure(appContext(), minApi)

    private fun forceRestartUserService(context: Context) {
        ShizukuUserServiceHost.drop(context)
    }

    private fun readServiceApi(taskService: ITaskManagerService): Int =
        ShizukuUserServiceHost.readApi(taskService)

    fun isShizukuRunning(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean =
        isShizukuRunning() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun checkAndRequestPermission(activity: Activity): Boolean {
        if (hasPermission()) return true
        requestPermission(activity)
        return false
    }

    fun openShizukuApp(context: Context): Boolean {
        val packageManager = context.packageManager
        for (packageName in SHIZUKU_PACKAGES) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: continue
            val flags = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent.addFlags(flags))
            return true
        }
        return false
    }

    fun requestPermission(context: Context) {
        if (hasPermission()) return
        if (!isShizukuRunning()) {
            if (openShizukuApp(context)) {
                showShizukuToast(context, R.string.shizuku_start_service_hint)
            } else {
                showShizukuToast(context, R.string.shizuku_not_installed)
            }
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    private fun showShizukuToast(context: Context, messageResId: Int) {
        val appContext = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(appContext, messageResId, Toast.LENGTH_LONG).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, messageResId, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun warmUp() {
        if (!hasPermission() || warmUpInFlight) return
        warmUpInFlight = true
        Thread {
            try {
                bindService(appContext())
            } catch (error: Exception) {
                Log.w(TAG, "warmUp failed", error)
            } finally {
                warmUpInFlight = false
            }
        }.start()
    }

    fun prefetchRecentTasks(force: Boolean = false) {
        ensureServiceBound()
    }

    fun refreshRecentTaskPackages(): List<String> {
        return refreshRecentTasks().map { it.identifier }
    }

    fun ensureServiceBound() {
        if (!hasPermission() || peekBoundService() != null) return
        Thread {
            runCatching { bindService(appContext()) }
                .onFailure { error -> Log.w(TAG, "ensureServiceBound failed", error) }
        }.start()
    }

    fun refreshRecentTasks(): List<RecentTaskRef> {
        if (!hasPermission()) return emptyList()
        val taskService = peekBoundService() ?: bindService(appContext()) ?: return emptyList()
        return TaskManagerTaskQueries.fetchRecentTasksFromService(taskService)
    }

    fun resolveTaskIdForIdentifier(identifier: String): Int? {
        if (identifier.isBlank() || !hasPermission()) return null
        val service = bindService(appContext()) ?: bindFreshService() ?: return null
        val ids = runCatching { service.getTaskIdsForPackage(identifier.trim()) }
            .getOrDefault(emptyArray())
        return TaskManagerTaskQueries.resolveTaskIdFromIds(ids)
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
        val service = peekBoundService() ?: bindService(appContext()) ?: run {
            Log.w(TAG, "switchToTask failed: UserService unavailable")
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

    fun movePackageToFreeWindow(packageName: String, settings: AppSettings): Boolean =
        TaskManagerUtilFreeWindow.movePackageToFreeWindow(
            packageName = packageName,
            settings = settings,
            hasPermission = hasPermission(),
            bindFreshService = ::bindFreshService,
        )

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

    fun loadCategorizedSystemShortcutMap(
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> =
        TaskManagerUtilShortcuts.loadCategorizedSystemShortcutMap(
            hasPermission = hasPermission(),
            bindFreshService = ::bindFreshService,
            readServiceApi = ::readServiceApi,
            onProgress = onProgress,
        )

    fun showVoiceAssistant(): Boolean {
        if (!hasPermission()) return false
        return runOnTaskWorker {
            bindFreshService()?.showVoiceAssistant() == true
        }
    }

    fun runShellCommand(vararg cmd: String): Boolean {
        if (!hasPermission()) return false
        return runOnTaskWorker {
            bindFreshService()?.runShellCommand(cmd) == true
        }
    }

    fun runShellCommandOutput(vararg cmd: String): ShellCommandResult =
        TaskManagerUtilShell.runShellCommandOutput(hasPermission(), ::bindFreshService, *cmd)

    fun probeRootAvailable(): Boolean =
        TaskManagerUtilShell.probeRootAvailable(hasPermission(), ::bindFreshService, ::readServiceApi)

    fun runShellCommandLine(
        command: String,
        useRoot: Boolean,
        timeoutMs: Long = 35_000L,
    ): ShellCommandResult =
        TaskManagerUtilShell.runShellCommandLine(
            hasPermission = hasPermission(),
            bindFreshService = ::bindFreshService,
            readServiceApi = ::readServiceApi,
            command = command,
            useRoot = useRoot,
            timeoutMs = timeoutMs,
        )

    fun <T> runOnTaskWorker(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // bindService runs inline on main; never block main on a background worker lock.
            return block()
        }
        synchronized(taskWorkerLock) {
            return block()
        }
    }

    fun moveFrontTaskToFreeWindow(settings: AppSettings): Boolean =
        TaskManagerUtilFreeWindow.moveFrontTaskToFreeWindow(
            settings = settings,
            hasPermission = hasPermission(),
            appContext = appContext(),
            bindFreshService = ::bindFreshService,
            forceRestartUserService = ::forceRestartUserService,
        )

    fun restartShellService(): Int = ShizukuUserServiceHost.restart(appContext())

    fun getRecentTaskPackages(): List<String>? {
        if (!hasPermission()) return null
        val taskService = peekBoundService() ?: bindService(appContext()) ?: return null
        return try {
            taskService.getRecentTaskPackages().toList()
        } catch (e: Exception) {
            Log.e(TAG, "getRecentTaskPackages failed", e)
            null
        }
    }

    private fun appContext(): Context =
        applicationContext ?: error("TaskManagerUtil.initialize() must be called before use")
}
