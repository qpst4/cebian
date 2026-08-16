package com.slideindex.app.overlay.appswitcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.QuickLauncherIconResolver

internal object AppSwitcherSlotIconBitmap {
    private val cache = object : LruCache<String, Bitmap>(96) {}

    fun get(
        context: Context,
        item: QuickLauncherItem,
        sizePx: Int,
        appsByPackage: Map<String, com.slideindex.app.data.AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ): Bitmap {
        val cacheKey = "${item.type}:${item.payload}:${item.label}:$sizePx"
        cache.get(cacheKey)?.let { return it }
        val drawable = QuickLauncherIconResolver.iconDrawable(
            item = item,
            appsByPackage = appsByPackage,
            context = context,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
        ) ?: return createBitmap(sizePx, sizePx)
        val bitmap = drawableToBitmap(drawable, sizePx)
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
