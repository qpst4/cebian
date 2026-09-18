package com.slideindex.app.launcher

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.util.AppLocaleApplier

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
            QuickLauncherItemType.FOLDER ->
                context.getString(R.string.quick_launcher_item_folder)
        }

    fun resolveLabel(
        context: Context,
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>
    ): String {
        val localized = AppLocaleApplier.wrapOverlayContext(context)
        return when (item.type) {
            QuickLauncherItemType.APP ->
                appsByPackage[item.payload]?.label ?: item.label.ifBlank { item.payload }
            QuickLauncherItemType.SHORTCUT ->
                item.label.ifBlank { defaultTypeLabel(localized, item.type) }
            QuickLauncherItemType.ACTION -> {
                val action = QuickLauncherItemCodec.parseActionPayload(item.payload)
                if (action != null) {
                    gestureActionLabelText(localized, action)
                } else {
                    item.label.ifBlank { defaultTypeLabel(localized, item.type) }
                }
            }
            QuickLauncherItemType.WIDGET ->
                item.label.ifBlank { defaultTypeLabel(localized, item.type) }
            QuickLauncherItemType.FOLDER ->
                item.label.ifBlank { defaultTypeLabel(localized, item.type) }
        }
    }
}
