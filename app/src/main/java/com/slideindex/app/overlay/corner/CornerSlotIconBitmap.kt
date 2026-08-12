package com.slideindex.app.overlay.corner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ManagedShortcutIconResolver
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver
import com.slideindex.app.util.GestureActionIconBitmap

internal object CornerSlotIconBitmap {
    private val appIconCache = object : LruCache<String, Bitmap>(64) {}

    fun get(
        context: Context,
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
    ): Bitmap {
        if (action is GestureAction.LaunchApp) {
            val cacheKey = "app:${action.packageName}:$sizePx"
            appIconCache.get(cacheKey)?.let { return it }
            val drawable = runCatching {
                context.packageManager.getApplicationIcon(action.packageName)
            }.getOrNull()
            if (drawable != null) {
                val bitmap = drawableToBitmap(drawable, sizePx)
                appIconCache.put(cacheKey, bitmap)
                return bitmap
            }
        }
        if (action is GestureAction.LaunchShortcut) {
            val managedKey = "managed:${action.payloadKey}:$sizePx:${activityShortcuts.map { it.identityKey() to it.iconPath }.hashCode()}"
            appIconCache.get(managedKey)?.let { return it }
            ManagedShortcutIconResolver.bitmapForLaunchShortcut(
                context = context,
                action = action,
                catalog = activityShortcuts,
                sizePx = sizePx,
            )?.let { bitmap ->
                appIconCache.put(managedKey, bitmap)
                return bitmap
            }
            val hostPackage = ManagedShortcutIconResolver.hostPackageForLaunchShortcut(action.payloadKey)
            if (!hostPackage.isNullOrBlank()) {
                val cacheKey = "host:$hostPackage:$sizePx"
                appIconCache.get(cacheKey)?.let { return it }
                val drawable = runCatching {
                    context.packageManager.getApplicationIcon(hostPackage)
                }.getOrNull()
                if (drawable != null) {
                    val bitmap = drawableToBitmap(drawable, sizePx)
                    appIconCache.put(cacheKey, bitmap)
                    return bitmap
                }
            }
        }
        if (action is GestureAction.ExecuteShellCommand) {
            ShellCommandIconResolver.findForCommandLine(action.command, shellCommands)?.let { matched ->
                val cacheKey =
                    "shell:${matched.id}:${matched.iconType}:${matched.iconPath.orEmpty()}:${matched.textIcon.orEmpty()}:$sizePx"
                appIconCache.get(cacheKey)?.let { return it }
                ShellCommandIconResolver.resolveBitmap(context, matched, sizePx)?.let { bitmap ->
                    appIconCache.put(cacheKey, bitmap)
                    return bitmap
                }
            }
        }
        return GestureActionIconBitmap.get(action, sizePx, tintArgb)
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val icon = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        return bitmap
    }
}
