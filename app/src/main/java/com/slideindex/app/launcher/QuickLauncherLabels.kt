package com.slideindex.app.launcher

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo

object QuickLauncherLabels {
    fun defaultTypeLabel(context: Context, type: QuickLauncherItemType): String =
        when (type) {
            QuickLauncherItemType.APP -> ""
            QuickLauncherItemType.SHORTCUT ->
                context.getString(R.string.quick_launcher_item_shortcut)
            QuickLauncherItemType.ACTION ->
                context.getString(R.string.quick_launcher_item_action)
            QuickLauncherItemType.WIDGET ->
                context.getString(R.string.quick_launcher_item_widget)
        }

    fun resolveLabel(
        context: Context,
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
    ): String =
        when (item.type) {
            QuickLauncherItemType.APP ->
                appsByPackage[item.payload]?.label ?: item.label.ifBlank { item.payload }
            QuickLauncherItemType.SHORTCUT ->
                item.label.ifBlank { defaultTypeLabel(context, item.type) }
            QuickLauncherItemType.ACTION ->
                item.label.ifBlank { defaultTypeLabel(context, item.type) }
            QuickLauncherItemType.WIDGET ->
                item.label.ifBlank { defaultTypeLabel(context, item.type) }
        }
}
