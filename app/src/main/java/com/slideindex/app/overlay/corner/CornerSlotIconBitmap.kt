package com.slideindex.app.overlay.corner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.util.GestureActionIconBitmap

internal object CornerSlotIconBitmap {
    private val appIconCache = object : LruCache<String, Bitmap>(64) {}

    fun get(
        context: Context,
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int,
    ): Bitmap {
        if (action is GestureAction.LaunchApp) {
            val cacheKey = "${action.packageName}:$sizePx"
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
