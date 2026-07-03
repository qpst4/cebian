package com.slideindex.app.launcher

import com.slideindex.app.data.AppInfo

object QuickLauncherDefaults {
    const val DEFAULT_ITEM_COUNT = 12

    fun fromApps(apps: List<AppInfo>, count: Int = DEFAULT_ITEM_COUNT): List<QuickLauncherItem> =
        apps.take(count).map { QuickLauncherItem.app(it.packageName, it.label) }

    fun effectiveItems(configured: List<QuickLauncherItem>, apps: List<AppInfo>): List<QuickLauncherItem> =
        if (configured.isNotEmpty()) configured else fromApps(apps)
}
