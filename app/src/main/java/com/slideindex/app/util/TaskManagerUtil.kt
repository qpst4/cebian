package com.slideindex.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.privilege.RootPrivilegedOperations
import com.slideindex.app.settings.PrivilegeMode
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

    @Volatile
    private var privilegedWarmUpInFlight = false

    @Volatile
    private var cachedDirectRootAccess: Boolean? = null

    @Volatile
    private var cachedDirectRootAccessAtMs = 0L

    private val taskWorkerLock = Any()

    private val privilegedOpsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PrivilegedTaskOps").apply { isDaemon = true }
    }

    private const val PRIVILEGED_OPS_TIMEOUT_MS = 8_000L

    private val onPrivilegedOpsThread = ThreadLocal.withInitial { false }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> privilegedMainThreadFallback(): T = when (T::class) {
        Boolean::class -> false as T
        ShellCommandResult::class -> ShellCommandResult(-1, "主线程跳过特权任务") as T
        Int::class -> 0 as T
        String::class -> "" as T
        Unit::class -> Unit as T
        else -> when {
            Map::class.java.isAssignableFrom(T::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                emptyMap<Any, Any>() as T
            }
            else -> throw IllegalStateException(
                "Privileged task cannot run on main thread for ${T::class.java.simpleName}",
            )
        }
    }

    private inline fun <reified T> runPrivilegedOpsBlocking(noinline block: () -> T): T {
        if (onPrivilegedOpsThread.get() == true) {
            return block()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            privilegedOpsExecutor.execute {
                onPrivilegedOpsThread.set(true)
                try {
                    runCatching { block() }
                } finally {
                    onPrivilegedOpsThread.set(false)
                }
            }
            return privilegedMainThreadFallback()
        }
        return try {
            privilegedOpsExecutor.submit(
                Callable {
                    onPrivilegedOpsThread.set(true)
                    try {
                        block()
                    } finally {
                        onPrivilegedOpsThread.set(false)
                    }
                },
            ).get(PRIVILEGED_OPS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "privileged task timed out", error)
            privilegedMainThreadFallback()
        } catch (error: Exception) {
            Log.e(TAG, "privileged task failed", error)
            privilegedMainThreadFallback()
        }
    }

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

    fun hasShizukuPermission(): Boolean =
        isShizukuRunning() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private const val ROOT_ACCESS_CACHE_MS = 30_000L

    @Volatile
    private var cachedRootAccess: Boolean? = null

    @Volatile
    private var cachedRootAccessAtMs = 0L

    fun invalidatePrivilegedAccessCache() {
        cachedRootAccess = null
        cachedRootAccessAtMs = 0L
        cachedDirectRootAccess = null
        cachedDirectRootAccessAtMs = 0L
    }

    fun hasPrivilegedAccess(): Boolean =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> cachedRootAccess()
            PrivilegeMode.SHIZUKU -> hasShizukuPermission()
        }

    fun peekPrivilegedAccess(): Boolean =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> cachedRootAccess ?: true
            PrivilegeMode.SHIZUKU -> hasShizukuPermission()
        }

    private fun cachedRootAccess(): Boolean {
        val now = SystemClock.elapsedRealtime()
        cachedRootAccess?.let { cached ->
            if (now - cachedRootAccessAtMs < ROOT_ACCESS_CACHE_MS) return cached
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleRootAccessProbe()
            return cachedRootAccess ?: true
        }
        val live = runPrivilegedOpsBlocking { RootPrivilegedOperations.probeRootAvailable() }
        cachedRootAccess = live
        cachedRootAccessAtMs = now
        return live
    }

    private fun scheduleRootAccessProbe() {
        privilegedOpsExecutor.execute {
            onPrivilegedOpsThread.set(true)
            try {
                val live = RootPrivilegedOperations.probeRootAvailable()
                cachedRootAccess = live
                cachedRootAccessAtMs = SystemClock.elapsedRealtime()
            } finally {
                onPrivilegedOpsThread.set(false)
            }
        }
    }

    fun hasPermission(): Boolean = hasPrivilegedAccess()

    fun checkAndRequestPermission(activity: Activity): Boolean {
        if (hasPrivilegedAccess()) return true
        if (PrivilegeGateway.isRootMode()) return false
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
        if (PrivilegeGateway.isRootMode()) return
        if (hasShizukuPermission()) return
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
        if (!PrivilegeGateway.isShizukuMode() || !hasShizukuPermission() || warmUpInFlight) return
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

    fun warmUpPrivilegedBackend() {
        when {
            PrivilegeGateway.isShizukuMode() && hasShizukuPermission() -> warmUp()
            PrivilegeGateway.isRootMode() && !privilegedWarmUpInFlight -> {
                privilegedWarmUpInFlight = true
                privilegedOpsExecutor.execute {
                    onPrivilegedOpsThread.set(true)
                    try {
                        val live = RootPrivilegedOperations.probeRootAvailable()
                        cachedRootAccess = live
                        cachedRootAccessAtMs = SystemClock.elapsedRealtime()
                    } catch (error: Exception) {
                        Log.w(TAG, "warmUpPrivilegedBackend failed", error)
                    } finally {
                        onPrivilegedOpsThread.set(false)
                        privilegedWarmUpInFlight = false
                    }
                }
            }
        }
    }

    fun probeDirectRootAvailable(): Boolean {
        val now = SystemClock.elapsedRealtime()
        cachedDirectRootAccess?.let { cached ->
            if (now - cachedDirectRootAccessAtMs < ROOT_ACCESS_CACHE_MS) return cached
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleDirectRootProbe()
            return cachedDirectRootAccess ?: false
        }
        val live = runPrivilegedOpsBlocking {
            com.slideindex.app.shizuku.TaskManagerShellExecutor.probeRootAvailable()
        }
        cachedDirectRootAccess = live
        cachedDirectRootAccessAtMs = now
        return live
    }

    private fun scheduleDirectRootProbe() {
        privilegedOpsExecutor.execute {
            onPrivilegedOpsThread.set(true)
            try {
                val live = com.slideindex.app.shizuku.TaskManagerShellExecutor.probeRootAvailable()
                cachedDirectRootAccess = live
                cachedDirectRootAccessAtMs = SystemClock.elapsedRealtime()
            } finally {
                onPrivilegedOpsThread.set(false)
            }
        }
    }

    fun prefetchRecentTasks(force: Boolean = false) {
        ensureServiceBound()
    }

    fun refreshRecentTaskPackages(): List<String> {
        return refreshRecentTasks().map { it.identifier }
    }

    fun ensureServiceBound() {
        if (!PrivilegeGateway.isShizukuMode() || !hasShizukuPermission() || peekBoundService() != null) return
        Thread {
            runCatching { bindService(appContext()) }
                .onFailure { error -> Log.w(TAG, "ensureServiceBound failed", error) }
        }.start()
    }

    fun refreshRecentTasks(): List<RecentTaskRef> {
        if (!hasPrivilegedAccess()) return emptyList()
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("refreshRecentTasks") {
                TaskManagerTaskQueries.parseRecentTaskRows(RootPrivilegedOperations.getRecentTasks())
            } ?: emptyList()
        }
        val taskService = peekBoundService() ?: bindService(appContext()) ?: return emptyList()
        return TaskManagerTaskQueries.fetchRecentTasksFromService(taskService)
    }

    fun resolveTaskIdForIdentifier(identifier: String): Int? {
        if (identifier.isBlank() || !hasPrivilegedAccess()) return null
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("resolveTaskIdForIdentifier") {
                val ids = RootPrivilegedOperations.getTaskIdsForPackage(identifier.trim())
                TaskManagerTaskQueries.resolveTaskIdFromIds(ids)
            }
        }
        val service = bindService(appContext()) ?: bindFreshService() ?: return null
        val ids = runCatching { service.getTaskIdsForPackage(identifier.trim()) }
            .getOrDefault(emptyArray())
        return TaskManagerTaskQueries.resolveTaskIdFromIds(ids)
    }

    fun removeTaskById(taskId: Int): Boolean {
        if (taskId <= 0 || !hasPrivilegedAccess()) return false
        if (PrivilegeGateway.isRootMode()) {
            return runOnTaskWorker { RootPrivilegedOperations.removeTaskById(taskId.toString()) }
        }
        return runOnTaskWorker {
            bindFreshService(MIN_REMOVE_TASK_API)?.removeTaskById(taskId.toString()) == true
        }
    }

    fun switchToTask(
        taskId: Int,
        identifier: String = "",
        topComponent: String = "",
    ): Boolean {
        if (!hasPrivilegedAccess()) {
            Log.w(TAG, "switchToTask skipped: no privileged access")
            return false
        }
        if (taskId <= 0 && identifier.isBlank()) {
            Log.w(TAG, "switchToTask skipped: no taskId or identifier")
            return false
        }
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("switchToTask:$taskId") {
                RootPrivilegedOperations.switchToTask(
                    if (taskId > 0) taskId.toString() else "",
                    identifier,
                    topComponent,
                )
            } ?: false
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
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("removeCurrentFrontAppTask") {
                val taskId = RootPrivilegedOperations.getFrontTaskId().takeIf { it.isNotBlank() } ?: return@runPrivilegedTask false
                RootPrivilegedOperations.removeTaskById(taskId)
            } ?: false
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
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("removeTaskByPackage:$packageName") {
                val taskIds = RootPrivilegedOperations.getTaskIdsForPackage(packageName)
                if (taskIds.isEmpty()) return@runPrivilegedTask false
                taskIds.any { RootPrivilegedOperations.removeTaskById(it) }
            } ?: false
        }
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
        if (!hasPrivilegedAccess()) return false
        if (PrivilegeGateway.isRootMode()) {
            return runOnTaskWorker { RootPrivilegedOperations.forceStopPackage(packageName) }
        }
        return runOnTaskWorker {
            bindFreshService(MIN_FORCE_STOP_API)?.forceStopPackage(packageName) == true
        }
    }

    fun movePackageToFreeWindow(packageName: String, settings: AppSettings): Boolean {
        if (!hasPrivilegedAccess()) return false
        if (PrivilegeGateway.isRootMode()) {
            return runOnTaskWorker {
                val taskId = RootPrivilegedOperations.getTaskIdsForPackage(packageName).firstOrNull()
                    ?: return@runOnTaskWorker false
                RootPrivilegedOperations.moveTaskToFreeWindow(taskId, settings, appContext())
            }
        }
        return TaskManagerUtilFreeWindow.movePackageToFreeWindow(
            packageName = packageName,
            settings = settings,
            hasPermission = true,
            bindFreshService = ::bindFreshService,
        )
    }

    fun getPublishedShortcuts(packageName: String): List<Pair<String, String>> {
        if (packageName.isBlank() || !hasPrivilegedAccess()) return emptyList()
        return runOnTaskWorker {
            val rows = if (PrivilegeGateway.isRootMode()) {
                RootPrivilegedOperations.getPublishedShortcuts(packageName)
            } else {
                bindFreshService(MIN_SHORTCUTS_API)?.getPublishedShortcuts(packageName).orEmpty()
            }
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
        if (packageName.isBlank() || shortcutId.isBlank() || !hasPrivilegedAccess()) return false
        return runOnTaskWorker {
            if (PrivilegeGateway.isRootMode()) {
                RootPrivilegedOperations.startPublishedShortcut(packageName, shortcutId)
            } else {
                bindFreshService(MIN_SHORTCUTS_API)?.startPublishedShortcut(packageName, shortcutId) == true
            }
        }
    }

    fun loadCategorizedSystemShortcutMap(
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> {
        if (PrivilegeGateway.isRootMode() && hasPrivilegedAccess()) {
            return runOnTaskWorker {
                TaskManagerUtilShortcuts.loadCategorizedSystemShortcutMapFromRows(
                    RootPrivilegedOperations.getAllPublishedShortcuts().toList(),
                    onProgress,
                )
            }
        }
        return TaskManagerUtilShortcuts.loadCategorizedSystemShortcutMap(
            hasPermission = hasShizukuPermission(),
            bindFreshService = ::bindFreshService,
            readServiceApi = ::readServiceApi,
            onProgress = onProgress,
        )
    }

    fun showVoiceAssistant(): Boolean {
        if (!hasPrivilegedAccess()) return false
        if (PrivilegeGateway.isRootMode()) {
            return runOnTaskWorker { RootPrivilegedOperations.showVoiceAssistant() }
        }
        return runOnTaskWorker {
            bindFreshService()?.showVoiceAssistant() == true
        }
    }

    fun runShellCommand(vararg cmd: String): Boolean {
        if (!hasPrivilegedAccess()) return false
        if (PrivilegeGateway.isRootMode()) {
            return runOnTaskWorker { RootPrivilegedOperations.runShellCommand(*cmd) }
        }
        return runOnTaskWorker {
            bindFreshService()?.runShellCommand(cmd) == true
        }
    }

    fun runShellCommandOutput(vararg cmd: String): ShellCommandResult {
        if (PrivilegeGateway.isRootMode()) {
            if (!hasPrivilegedAccess()) {
                return ShellCommandResult(exitCode = -1, output = "无 Root 权限")
            }
            return runOnTaskWorker { RootPrivilegedOperations.runShellCommandOutput(*cmd) }
        }
        return TaskManagerUtilShell.runShellCommandOutput(hasShizukuPermission(), ::bindFreshService, *cmd)
    }

    fun probeRootAvailable(): Boolean =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT ->
                runOnTaskWorker { RootPrivilegedOperations.probeRootAvailable() }
            PrivilegeMode.SHIZUKU ->
                TaskManagerUtilShell.probeRootAvailable(hasShizukuPermission(), ::bindFreshService, ::readServiceApi)
        }

    fun runShellCommandLine(
        command: String,
        useRoot: Boolean,
        timeoutMs: Long = 35_000L,
    ): ShellCommandResult {
        if (PrivilegeGateway.isRootMode()) {
            if (!hasPrivilegedAccess()) {
                return ShellCommandResult(exitCode = -1, output = "无 Root 权限")
            }
            return runOnTaskWorker { RootPrivilegedOperations.runShellCommandLine(command, timeoutMs) }
        }
        return TaskManagerUtilShell.runShellCommandLine(
            hasPermission = hasShizukuPermission(),
            bindFreshService = ::bindFreshService,
            readServiceApi = ::readServiceApi,
            command = command,
            useRoot = useRoot,
            timeoutMs = timeoutMs,
        )
    }

    internal inline fun <reified T> runOnTaskWorker(noinline block: () -> T): T {
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedOpsBlocking(block)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // bindService runs inline on main; never block main on a background worker lock.
            return block()
        }
        synchronized(taskWorkerLock) {
            return block()
        }
    }

    private fun <T> runPrivilegedTask(label: String, block: () -> T): T? {
        if (onPrivilegedOpsThread.get() == true) {
            return block()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            privilegedOpsExecutor.execute {
                onPrivilegedOpsThread.set(true)
                try {
                    runCatching { block() }
                } finally {
                    onPrivilegedOpsThread.set(false)
                }
            }
            Log.w(TAG, "$label dispatched on main thread")
            return null
        }
        return try {
            privilegedOpsExecutor.submit(
                Callable {
                    onPrivilegedOpsThread.set(true)
                    try {
                        block()
                    } finally {
                        onPrivilegedOpsThread.set(false)
                    }
                },
            ).get(PRIVILEGED_OPS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "$label timed out", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "$label failed", error)
            null
        }
    }

    fun moveFrontTaskToFreeWindow(settings: AppSettings): Boolean {
        if (!hasPrivilegedAccess()) return false
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("moveFrontTaskToFreeWindow") {
                val taskId = RootPrivilegedOperations.getFrontTaskId().takeIf { it.isNotBlank() }
                    ?: return@runPrivilegedTask false
                val frontPackage = RootPrivilegedOperations.getFrontTaskPackage()
                if (frontPackage.isNotBlank() &&
                    TaskExclusions.shouldSkipFreeWindow(frontPackage, appContext().packageName)
                ) {
                    return@runPrivilegedTask false
                }
                RootPrivilegedOperations.moveTaskToFreeWindow(taskId, settings, appContext())
            } ?: false
        }
        return TaskManagerUtilFreeWindow.moveFrontTaskToFreeWindow(
            settings = settings,
            hasPermission = true,
            appContext = appContext(),
            bindFreshService = ::bindFreshService,
            forceRestartUserService = ::forceRestartUserService,
        )
    }

    fun restartShellService(): Int {
        if (PrivilegeGateway.isRootMode()) return 0
        return ShizukuUserServiceHost.restart(appContext())
    }

    fun getRecentTaskPackages(): List<String>? {
        if (!hasPrivilegedAccess()) return null
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("getRecentTaskPackages") {
                RootPrivilegedOperations.getRecentTaskPackages().toList()
            }
        }
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
