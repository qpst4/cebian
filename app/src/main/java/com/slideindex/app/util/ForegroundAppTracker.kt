package com.slideindex.app.util

import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForegroundAppTracker(
    private val context: Context,
    scope: CoroutineScope,
) {
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    private var pollJob: Job? = scope.launch {
        while (isActive) {
            _foregroundPackage.value = queryForegroundPackage()
            delay(POLL_INTERVAL_MS)
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun queryForegroundPackage(): String? {
        if (!PermissionHelper.hasUsageAccess(context)) return null
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = end - LOOKBACK_MS
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            start,
            end,
        ) ?: return null
        return stats
            .asSequence()
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    companion object {
        private const val POLL_INTERVAL_MS = 400L
        private const val LOOKBACK_MS = 60_000L
    }
}
