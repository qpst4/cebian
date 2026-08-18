package com.slideindex.app.tasks

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.data.AppInfo
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentPackageResolver
import com.slideindex.app.util.TaskActivityLabelResolver
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore

/**
 * Client-side task switcher: live system recents via Shizuku, mapped to overlay UI entries.
 */
object TaskSwitcherRepository {

    private const val TAG = "TaskSwitcherRepository"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedEntries: List<RecentAppEntry> = emptyList()

    fun getCachedEntries(): List<RecentAppEntry> = cachedEntries

    @Volatile
    private var refreshInFlight = false

    private val pendingCallbacks = mutableListOf<(List<RecentAppEntry>) -> Unit>()

    fun refreshAsync(appRepository: AppRepository, onComplete: (List<RecentAppEntry>) -> Unit) {
        if (!TaskManagerUtil.hasPermission()) {
            onComplete(emptyList())
            return
        }
        TaskManagerUtil.ensureServiceBound()
        synchronized(this) {
            pendingCallbacks += onComplete
            if (refreshInFlight) return
            refreshInFlight = true
        }
        Thread {
            val entries = runCatching { loadEntries(appRepository) }
                .getOrElse { error ->
                    Log.e(TAG, "refresh failed", error)
                    emptyList()
                }
            val callbacks = synchronized(this) {
                refreshInFlight = false
                pendingCallbacks.toList().also { pendingCallbacks.clear() }
            }
            mainHandler.post { callbacks.forEach { it(entries) } }
        }.start()
    }

    fun syncFromSystem(appRepository: AppRepository): List<RecentAppEntry> =
        loadEntries(appRepository)

    fun requestRefreshAfterSwitch(appRepository: AppRepository) {
        mainHandler.postDelayed({ refreshAsync(appRepository) { } }, 400L)
    }

    fun removePackages(packages: Collection<String>) {
        // Session-only optimistic updates are handled in EdgeGestureOverlayView.recentApps.
    }

    fun removeTaskIds(taskIds: Collection<Int>) {
        // Session-only optimistic updates are handled in EdgeGestureOverlayView.recentApps.
    }

    private fun loadEntries(appRepository: AppRepository): List<RecentAppEntry> {
        val refs = TaskManagerUtil.refreshRecentTasks()
        val locked = TaskSwitcherLockStore.lockedPackages(TaskManagerUtil.applicationContext())
        val seenIds = LinkedHashSet<Int>()
        val seenPackages = LinkedHashSet<String>()
        val entries = mutableListOf<RecentAppEntry>()

        for (ref in refs) {
            val packageName = resolvePackage(appRepository, ref) ?: continue
            if (ref.taskId > 0) {
                if (!seenIds.add(ref.taskId)) continue
            } else if (!seenPackages.add(packageName)) {
                continue
            }

            val appInfo = appRepository.ensureAppInfo(packageName) ?: continue
            val label = resolveLabel(ref, appInfo)
            entries += RecentAppEntry(
                app = appInfo.copy(
                    label = label,
                    letter = PinyinHelper.firstLetter(label),
                ),
                lastUsed = 0L,
                isLocked = packageName in locked,
                taskId = ref.taskId,
                rawIdentifier = ref.identifier,
                topComponent = ref.topComponent.orEmpty(),
            )
        }

        Log.i(TAG, "loadEntries ${refs.size} -> ${entries.size}")
        val result = entries.toList()
        cachedEntries = result
        return result
    }

    private fun resolvePackage(
        appRepository: AppRepository,
        ref: TaskManagerUtil.RecentTaskRef,
    ): String? {
        if (RecentPackageResolver.isQuickShareIdentifier(ref.identifier) ||
            RecentPackageResolver.isQuickShareIdentifier(ref.topComponent.orEmpty())
        ) {
            return appRepository.resolveInstalledPackage(ref.identifier) ?: "com.google.android.gms"
        }
        val candidates = linkedSetOf(ref.identifier, ref.topComponent.orEmpty())
            .filter { it.isNotBlank() }
        for (candidate in candidates) {
            appRepository.resolveInstalledPackage(candidate)?.let { return it }
            val normalized = RecentPackageResolver.normalizeIdentifier(candidate)
            if (normalized.contains('.')) {
                appRepository.resolveInstalledPackage(normalized)?.let { return it }
                if (!normalized.contains('/')) return normalized
            }
        }
        return null
    }

    private fun resolveLabel(ref: TaskManagerUtil.RecentTaskRef, appInfo: AppInfo): String {
        val context = TaskManagerUtil.applicationContext()
        TaskActivityLabelResolver.resolveDisplayTitle(
            context,
            ref.topComponent.orEmpty(),
            ref.title,
        )?.let { return it }
        TaskActivityLabelResolver.resolveDisplayTitle(context, ref.identifier, null)?.let { return it }
        if (RecentPackageResolver.isSettingsAppInfoIdentifier(ref.identifier)) return "应用信息"
        if (RecentPackageResolver.isQuickShareIdentifier(ref.identifier)) return "快速分享"
        return appInfo.label
    }
}
