package com.slideindex.app.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import com.slideindex.app.shizuku.TaskManagerUserService
import rikka.shizuku.Shizuku
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object TaskManagerUtil {

    private const val TAG = "TaskManagerUtil"
    private const val MIN_RECENT_TASKS_API = 11
    private const val MIN_SWITCH_TO_TASK_API = 12
    private const val MIN_REMOVE_TASK_API = 1
    private const val MIN_TASK_IDS_API = 3
    private const val MIN_FREE_WINDOW_API = 5
    private const val MIN_FRONT_PACKAGE_API = 7
    private const val MIN_FORCE_STOP_API = 8
    private const val MIN_SHORTCUTS_API = 9
    private const val MIN_SHELL_OUTPUT_API = 15
    private const val SHELL_BINDER_TIMEOUT_MS = 35_000L
    private const val ALL_SHORTCUTS_BINDER_TIMEOUT_MS = 180_000L
    const val ROOT_PROBE_BINDER_TIMEOUT_MS = 45_000L
    private const val MIN_PROBE_ROOT_API = 17
    private const val MIN_SHELL_FORCE_ADB_API = 28
    private const val MIN_SHELL_LINE_V1_API = 16
    const val REQUEST_CODE = 1001

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
    private var warmUpInFlight = false

    @Volatile
    private var allShortcutsCacheLoadedAt: Long = 0L

    @Volatile
    private var allShortcutsCache: Map<String, List<Pair<String, String>>> = emptyMap()

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
        return fetchRecentTasksFromService(taskService)
    }

    private fun fetchRecentTasksFromService(taskService: ITaskManagerService): List<RecentTaskRef> {
        val tasks = runCatching {
            taskService.getRecentTasks().mapNotNull(::parseRecentTaskRow)
        }.getOrElse { error ->
            Log.e(TAG, "refreshRecentTasks failed", error)
            emptyList()
        }
        Log.i(
            TAG,
            "refreshRecentTasks (${tasks.size}): ${tasks.joinToString { "${it.taskId}|${it.identifier}" }}",
        )
        return tasks
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

    fun invalidateAllShortcutsCache() {
        allShortcutsCacheLoadedAt = 0L
        allShortcutsCache = emptyMap()
    }

    fun loadCategorizedSystemShortcutMap(
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> {
        val merged = ShortcutKind.entries.associateWith { linkedMapOf<String, LinkedHashMap<String, SystemShortcutEntry>>() }
        fun putEntry(kind: ShortcutKind, packageName: String, entry: SystemShortcutEntry) {
            merged.getValue(kind).getOrPut(packageName) { linkedMapOf() }.putIfAbsent(entry.id, entry)
        }
        fun absorbFlat(packageName: String, id: String, label: String) {
            if (!ShortcutDisplayRules.isDisplayable(id, label)) return
            putEntry(
                ShortcutKind.DYNAMIC,
                packageName,
                SystemShortcutEntry(
                    id = id,
                    label = label,
                    kinds = setOf(ShortcutKind.DYNAMIC),
                ),
            )
        }

        onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.DUMPSYS, 0, 0))
        if (!hasPermission()) {
            Log.w(TAG, "loadCategorizedSystemShortcutMap: no Shizuku permission")
            return finalizeCategorizedShortcutMap(merged)
        }

        val rows = fetchAllPublishedShortcutRows()
        Log.i(TAG, "loadCategorizedSystemShortcutMap Shizuku rows=${rows.size}")
        rows.forEach { row ->
            val parts = row.split('\t')
            if (parts.size >= 3) {
                absorbFlat(parts[0].trim(), parts[1].trim(), parts[2].trim())
            }
        }
        return finalizeCategorizedShortcutMap(merged)
    }

    private fun finalizeCategorizedShortcutMap(
        merged: Map<ShortcutKind, LinkedHashMap<String, LinkedHashMap<String, SystemShortcutEntry>>>,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> =
        merged.mapValues { (_, byPackage) ->
            byPackage.mapValues { (_, entries) -> entries.values.toList() }
        }

    private fun fetchAllPublishedShortcutRows(): List<String> {
        if (!hasPermission()) return emptyList()
        val fetch = fetchAllPublishedShortcutRowsBlocking()
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            runOnTaskWorker { fetch }
        } else {
            fetch
        }
    }

    private fun fetchAllPublishedShortcutRowsBlocking(): List<String> {
        val service = bindFreshService(minApi = 0) ?: run {
            Log.w(TAG, "fetchAllPublishedShortcutRows: UserService bind failed")
            return emptyList()
        }
        val api = readServiceApi(service)
        Log.i(TAG, "fetchAllPublishedShortcutRows: bound api=$api")
        val startedAt = System.currentTimeMillis()
        val rows = invokeBinderWithTimeout(ALL_SHORTCUTS_BINDER_TIMEOUT_MS) {
            service.getAllPublishedShortcuts()
        }
        val elapsedMs = System.currentTimeMillis() - startedAt
        if (rows == null) {
            Log.w(
                TAG,
                "fetchAllPublishedShortcutRows: binder timed out after ${elapsedMs}ms " +
                    "(limit=${ALL_SHORTCUTS_BINDER_TIMEOUT_MS}ms)",
            )
            return emptyList()
        }
        Log.i(TAG, "fetchAllPublishedShortcutRows: received ${rows.size} rows in ${elapsedMs}ms")
        return rows.toList()
    }

    private fun <T> invokeBinderWithTimeout(timeoutMs: Long, block: () -> T): T? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable { block() }).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "Binder call timed out after ${timeoutMs}ms", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "Binder call failed", error)
            null
        } finally {
            executor.shutdownNow()
        }
    }

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

    fun runShellCommandOutput(vararg cmd: String): ShellCommandResult {
        if (!hasPermission()) {
            return ShellCommandResult(exitCode = -1, output = "无 Shizuku 权限")
        }
        val service = bindFreshService(MIN_SHELL_OUTPUT_API) ?: bindFreshService()
            ?: return ShellCommandResult(
                exitCode = -1,
                output = "Shizuku UserService 连接失败，请重启 Shizuku 后重试",
            )
        val raw = invokeShellBinderWithTimeout {
            service.runShellCommandOutput(cmd)
        } ?: return ShellCommandResult(
            exitCode = -1,
            output = "Shell 命令超时（${SHELL_BINDER_TIMEOUT_MS / 1000}s），请重试",
        )
        return parseShellResultRaw(raw)
    }

    fun probeRootAvailable(): Boolean {
        if (!hasPermission()) return false
        val service = bindFreshService(MIN_PROBE_ROOT_API)
            ?: bindFreshService(MIN_SHELL_OUTPUT_API)
            ?: return false
        val api = readServiceApi(service)
        if (api >= MIN_PROBE_ROOT_API) {
            return invokeShellBinderBooleanWithTimeout(ROOT_PROBE_BINDER_TIMEOUT_MS) {
                service.probeRootAvailable()
            } ?: false
        }
        val rootResult = runShellCommandLineDirect(
            service = service,
            command = "id -u",
            useRoot = true,
            timeoutMs = ROOT_PROBE_BINDER_TIMEOUT_MS,
        )
        val rootUid = parseNumericUid(rootResult.output)
        if (rootResult.exitCode != 0 || rootUid != 0) return false
        val adbResult = runShellCommandLineDirect(
            service = service,
            command = "id -u",
            useRoot = false,
            timeoutMs = 8_000L,
        )
        if (adbResult.exitCode != 0) return true
        val adbUid = parseNumericUid(adbResult.output) ?: return false
        return adbUid != 0
    }

    private fun parseNumericUid(output: String): Int? {
        val trimmed = output.trim()
        trimmed.toIntOrNull()?.let { return it }
        return Regex("""uid=(\d+)""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun runShellCommandLine(
        command: String,
        useRoot: Boolean,
        timeoutMs: Long = SHELL_BINDER_TIMEOUT_MS,
    ): ShellCommandResult {
        if (!hasPermission()) {
            return ShellCommandResult(exitCode = -1, output = "无 Shizuku 权限")
        }
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return ShellCommandResult(exitCode = -1, output = "命令为空")
        }
        val minApi = if (useRoot) MIN_SHELL_OUTPUT_API else MIN_SHELL_FORCE_ADB_API
        val service = bindFreshService(minApi)
        if (service == null) {
            val clientBuild = TaskManagerUserService.API_VERSION
            return ShellCommandResult(
                exitCode = -1,
                output = "无法连接 Shell 服务（期望 build=$clientBuild）。\n" +
                    "请点击「重启 Shell 服务」；若仍失败，请完全关闭 Shizuku 与本应用后重试。",
            )
        }
        return runShellCommandLineDirect(
            service = service,
            command = trimmed,
            useRoot = useRoot,
            timeoutMs = timeoutMs,
            requireAdbIdentity = !useRoot,
        )
    }

    private fun runShellCommandLineDirect(
        service: ITaskManagerService,
        command: String,
        useRoot: Boolean,
        timeoutMs: Long,
        requireAdbIdentity: Boolean = !useRoot,
    ): ShellCommandResult {
        val api = readServiceApi(service)
        val raw = invokeShellBinderWithTimeout(timeoutMs) {
            when {
                api >= MIN_SHELL_FORCE_ADB_API ->
                    service.runShellCommandLine(command, useRoot, false)
                useRoot && api >= MIN_SHELL_OUTPUT_API -> {
                    val args = com.slideindex.app.shell.ShellCommandCodec.buildExecArgs(
                        command,
                        useRoot = true,
                    )
                    service.runShellCommandOutput(args)
                }
                api >= MIN_SHELL_LINE_V1_API ->
                    service.runShellCommandLine(command, useRoot, false)
                else -> null
            }
        }
        if (raw == null) {
            return ShellCommandResult(
                exitCode = -1,
                output = if (api < MIN_SHELL_OUTPUT_API) {
                    "Shizuku 服务版本过旧 (api=$api)，请重启 Shizuku 后重试"
                } else {
                    "Shell 命令超时（${timeoutMs / 1000}s），请重试"
                },
            )
        }
        return sanitizeAdbShellResult(parseShellResultRaw(raw), requireAdbIdentity)
    }

    private fun sanitizeAdbShellResult(
        result: ShellCommandResult,
        requireAdbIdentity: Boolean,
    ): ShellCommandResult {
        if (!requireAdbIdentity || result.exitCode != 0) return result
        if (result.output.startsWith(TaskManagerUserService.SHELL_DOWNGRADE_HINT)) return result
        val uid = parseNumericUid(result.output)
        if (uid == 0) {
            return ShellCommandResult(
                exitCode = -1,
                output = "命令仍在 root 身份下执行（uid=0）。\n" +
                    "adb 模式应返回 uid=2000。\n" +
                    "若 Shizuku 以 Root 启动，请改用无线调试/adb 方式启动 Shizuku，" +
                    "或在 APatch 中取消 Shell 的永久 Root 授权。",
            )
        }
        return result
    }

    private fun invokeShellBinderBooleanWithTimeout(
        timeoutMs: Long = SHELL_BINDER_TIMEOUT_MS,
        block: () -> Boolean,
    ): Boolean? = invokeTaskBinderBooleanWithTimeout(timeoutMs, block)

    private fun invokeTaskBinderBooleanWithTimeout(
        timeoutMs: Long,
        block: () -> Boolean,
    ): Boolean? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable { block() }).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "Task binder call timed out after ${timeoutMs}ms", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "Task binder call failed", error)
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private fun invokeTaskBinderWithTimeout(
        timeoutMs: Long,
        block: () -> Array<String>,
    ): Array<String>? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable { block() }).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "Task binder call timed out after ${timeoutMs}ms", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "Task binder call failed", error)
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private fun invokeShellBinderWithTimeout(
        timeoutMs: Long = SHELL_BINDER_TIMEOUT_MS,
        block: () -> String?,
    ): String? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable { block() }).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "Shell binder call timed out", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "Shell binder call failed", error)
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private fun parseShellResultRaw(raw: String?): ShellCommandResult {
        if (raw.isNullOrBlank()) {
            return ShellCommandResult(
                exitCode = -1,
                output = "Shell 服务无响应。请完全关闭并重启 Shizuku，然后重新打开本应用",
            )
        }
        return parseShellCommandOutput(raw)
    }

    private fun parseShellCommandOutput(raw: String): ShellCommandResult {
        val marker = "\n---\n"
        val index = raw.indexOf(marker)
        if (index < 0) {
            return ShellCommandResult(exitCode = -1, output = raw)
        }
        val exitCode = raw.substring(0, index).toIntOrNull() ?: -1
        val output = raw.substring(index + marker.length)
        return ShellCommandResult(exitCode, output)
    }

    fun <T> runOnTaskWorker(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // bindService runs inline on main; never block main on a background worker lock.
            return block()
        }
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

    /** Manually forces a full unbind + rebind, useful when a zombie daemon UserService is stuck. */
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

    private fun appContext(): Context = SlideIndexApp.instance.applicationContext
}
