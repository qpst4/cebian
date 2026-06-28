package com.slideindex.app.util

import android.app.usage.UsageStatsManager
import android.content.Context
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository

data class RecentAppEntry(
    val app: AppInfo,
    val lastUsed: Long,
    val isLocked: Boolean = false,
    val taskId: Int = 0,
    val rawIdentifier: String = app.packageName,
    val topComponent: String = "",
)

object RecentAppsHelper {
    fun queryRecent(
        context: Context,
        appRepository: AppRepository,
        limit: Int = 12,
    ): List<RecentAppEntry> {
        if (!PermissionHelper.hasUsageAccess(context)) {
            return appRepository.getCachedApps().take(limit).map {
                RecentAppEntry(it, 0L)
            }
        }
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = end - 24 * 60 * 60 * 1000L
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            start,
            end,
        ) ?: return emptyList()
        val appsByPackage = appRepository.getCachedApps().associateBy { it.packageName }
        return stats
            .asSequence()
            .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }
            .mapNotNull { stat ->
                appsByPackage[stat.packageName]?.let { RecentAppEntry(it, stat.lastTimeUsed) }
            }
            .take(limit)
            .toList()
    }
}
