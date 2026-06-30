package com.slideindex.app.util

import android.os.SystemClock
import android.util.Log
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.data.AppRepository

/**
 * Task switcher list backed by system recents (Shizuku). [syncFromSystem] is the source of truth.
 */
object RecentTasksLoader {

    private const val TAG = "RecentTasksLoader"
    private const val PROMOTE_GRACE_MS = 2_500L
    private const val SWITCH_SYNC_DELAY_MS = 400L

    @Volatile
    private var cachedEntries: List<RecentAppEntry> = emptyList()

    @Volatile
    private var lastPromotedEntry: RecentAppEntry? = null

    @Volatile
    private var lastPromotedAtMs = 0L

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingSwitchSync: Runnable? = null

    fun peekCached(): List<RecentAppEntry> = cachedEntries

    /** Move the switched-to task to the front until system recents catches up. */
    fun promoteEntry(entry: RecentAppEntry) {
        synchronized(this) {
            lastPromotedEntry = entry
            lastPromotedAtMs = SystemClock.elapsedRealtime()
            val current = cachedEntries.toMutableList()
            val index = current.indexOfFirst { sameTask(it, entry) }
            val item = if (index >= 0) current.removeAt(index) else entry
            current.add(0, item)
            cachedEntries = current
        }
    }

    fun requestRefreshAfterSwitch(appRepository: AppRepository) {
        TaskManagerUtil.invalidateRecentCache()
        pendingSwitchSync?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingSwitchSync = null
            refreshAsync(appRepository) { }
        }
        pendingSwitchSync = runnable
        mainHandler.postDelayed(runnable, SWITCH_SYNC_DELAY_MS)
    }

    /** Query system recents and replace local cache (including empty). */
    fun syncFromSystem(appRepository: AppRepository): List<RecentAppEntry> {
        val tasks = TaskManagerUtil.refreshRecentTasks()
        cachedEntries = applyRecentSwitchPromotion(buildEntries(appRepository, tasks))
        Log.i(
            TAG,
            "syncFromSystem (${tasks.size} -> ${cachedEntries.size}): ${
                cachedEntries.joinToString { "${it.taskId}|${it.rawIdentifier}" }
            }",
        )
        return cachedEntries
    }

    fun refreshAsync(appRepository: AppRepository, onComplete: (List<RecentAppEntry>) -> Unit) {
        if (!TaskManagerUtil.hasPermission()) {
            cachedEntries = emptyList()
            onComplete(emptyList())
            return
        }
        Thread {
            val fresh = TaskManagerUtil.runOnTaskWorker {
                syncFromSystem(appRepository)
            }
            mainHandler.post { onComplete(fresh) }
        }.start()
    }

    fun removePackages(packages: Collection<String>) {
        if (packages.isEmpty()) return
        val remove = packages.toSet()
        cachedEntries = cachedEntries.filterNot { it.app.packageName in remove }
        packages.forEach { TaskManagerUtil.removePackageFromCache(it) }
    }

    fun removeTaskIds(taskIds: Collection<Int>) {
        if (taskIds.isEmpty()) return
        val remove = taskIds.filter { it > 0 }.toSet()
        cachedEntries = cachedEntries.filterNot { it.taskId in remove }
    }

    private fun appContext() = SlideIndexApp.instance.applicationContext

    private fun applyRecentSwitchPromotion(entries: List<RecentAppEntry>): List<RecentAppEntry> {
        val promoted = lastPromotedEntry ?: return entries
        val elapsed = SystemClock.elapsedRealtime() - lastPromotedAtMs
        if (elapsed < 0 || elapsed > PROMOTE_GRACE_MS) {
            lastPromotedEntry = null
            return entries
        }
        if (entries.isEmpty()) return listOf(promoted)
        val mutable = entries.toMutableList()
        val index = mutable.indexOfFirst { sameTask(it, promoted) }
        val front = if (index >= 0) mutable.removeAt(index) else promoted
        mutable.add(0, front)
        if (index == 0) {
            lastPromotedEntry = null
        }
        return mutable
    }

    private fun sameTask(a: RecentAppEntry, b: RecentAppEntry): Boolean {
        if (b.taskId > 0 && a.taskId == b.taskId) return true
        if (a.app.packageName != b.app.packageName) return false
        if (b.rawIdentifier.isNotBlank() && a.rawIdentifier.isNotBlank()) {
            return a.rawIdentifier == b.rawIdentifier
        }
        return true
    }

    private fun buildEntries(
        appRepository: AppRepository,
        tasks: List<TaskManagerUtil.RecentTaskRef>,
    ): List<RecentAppEntry> {

        if (tasks.isEmpty()) return emptyList()

        val lockedPackages = TaskSwitcherLockStore.lockedPackages(appContext())

        val seenTaskIds = LinkedHashSet<Int>()

        val seenPackages = LinkedHashSet<String>()

        val entries = mutableListOf<RecentAppEntry>()

        for (task in tasks) {

            val isQuickShare = RecentPackageResolver.isQuickShareIdentifier(task.identifier) ||
                RecentPackageResolver.isQuickShareIdentifier(task.topComponent.orEmpty())

            val packageName = if (isQuickShare) {
                appRepository.resolveInstalledPackage(task.identifier)
                    ?: "com.google.android.gms"
            } else {
                appRepository.resolveInstalledPackage(task.identifier)
                    ?: task.identifier.takeIf { it.contains('.') && !it.contains('/') }
            } ?: run {
                Log.w(TAG, "skip ${task.taskId}|${task.identifier}: unresolved package")
                continue
            }

            if (task.taskId > 0) {

                if (!seenTaskIds.add(task.taskId)) continue

            } else if (!seenPackages.add(if (isQuickShare) "quickshare:$packageName" else packageName)) {

                continue

            }



            val baseApp = appRepository.lookupApp(packageName) ?: run {
                Log.w(TAG, "skip ${task.taskId}|$packageName: lookupApp failed")
                continue
            }

            val displayLabel = resolveDisplayLabel(
                task = task,
                fallbackLabel = baseApp.label,
            )

            Log.d(
                TAG,
                "taskLabel ${task.taskId}|${task.identifier}|dumpTitle=${task.title}|display=$displayLabel",
            )

            val app = baseApp.copy(
                label = displayLabel,
                letter = PinyinHelper.firstLetter(displayLabel),
            )

            entries += RecentAppEntry(
                app = app,
                lastUsed = 0L,
                isLocked = packageName in lockedPackages,
                taskId = task.taskId,
                rawIdentifier = task.identifier,
                topComponent = task.topComponent.orEmpty(),
            )

        }

        return entries

    }

    /** Prefer the same title as the system recents card, then concrete activity labels. */
    private fun resolveDisplayLabel(
        task: TaskManagerUtil.RecentTaskRef,
        fallbackLabel: String,
    ): String {
        TaskActivityLabelResolver.resolveDisplayTitle(
            appContext(),
            task.topComponent.orEmpty(),
            task.title,
        )?.let { return it }
        val identifier = task.identifier.trim()
        TaskActivityLabelResolver.resolveDisplayTitle(appContext(), identifier, null)?.let { return it }
        if (RecentPackageResolver.isSettingsAppInfoIdentifier(identifier)) return "应用信息"
        if (RecentPackageResolver.isQuickShareIdentifier(identifier)) return "快速分享"
        return fallbackLabel
    }

}
