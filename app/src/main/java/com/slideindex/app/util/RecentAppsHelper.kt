package com.slideindex.app.util

import com.slideindex.app.data.AppInfo

data class RecentAppEntry(
    val app: AppInfo,
    val lastUsed: Long,
    val isLocked: Boolean = false,
    val taskId: Int = 0,
    val rawIdentifier: String = app.packageName,
    val topComponent: String = "",
)
