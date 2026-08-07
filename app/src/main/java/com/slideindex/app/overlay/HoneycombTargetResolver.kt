package com.slideindex.app.overlay

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.util.QuickLauncherIconResolver

internal object HoneycombTargetResolver {
    fun resolve(
        context: Context,
        items: List<QuickLauncherItem>,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository? = null,
    ): List<HoneycombRuntimeTarget> =
        items.mapNotNull { item ->
            if (item.type != QuickLauncherItemType.APP &&
                item.type != QuickLauncherItemType.SHORTCUT &&
                item.type != QuickLauncherItemType.ACTION
            ) {
                return@mapNotNull null
            }
            val label = item.label.ifBlank {
                when (item.type) {
                    QuickLauncherItemType.APP ->
                        appsByPackage[item.payload]?.label ?: item.payload
                    QuickLauncherItemType.SHORTCUT -> item.payload
                    QuickLauncherItemType.ACTION -> item.label.ifBlank { item.payload }
                    else -> item.payload
                }
            }
            val icon = when {
                appRepository != null && item.type == QuickLauncherItemType.APP ->
                    appRepository.peekLaunchIconDrawable(item.payload)
                appRepository == null || item.type == QuickLauncherItemType.SHORTCUT ||
                    item.type == QuickLauncherItemType.ACTION ->
                    QuickLauncherIconResolver.iconDrawable(item, appsByPackage, context)
                else -> null
            }
            HoneycombRuntimeTarget(item, label, icon)
        }
}
