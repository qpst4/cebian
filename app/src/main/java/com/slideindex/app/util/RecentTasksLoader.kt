package com.slideindex.app.util

import android.util.Log
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.data.AppRepository
import com.slideindex.app.util.PinyinHelper



/**

 * Task switcher list backed by system recents (Shizuku). [syncFromSystem] is the source of truth.

 */

object RecentTasksLoader {

    private const val TAG = "RecentTasksLoader"

    @Volatile
    private var cachedEntries: List<RecentAppEntry> = emptyList()



    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())



    fun peekCached(): List<RecentAppEntry> = cachedEntries



    /** Query system recents and replace local cache (including empty). */

    fun syncFromSystem(appRepository: AppRepository): List<RecentAppEntry> {

        val tasks = TaskManagerUtil.refreshRecentTasks()
        cachedEntries = buildEntries(appRepository, tasks)
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
