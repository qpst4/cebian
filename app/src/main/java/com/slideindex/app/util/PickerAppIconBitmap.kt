package com.slideindex.app.util

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PickerAppIconBitmap {
    private const val ICON_SIZE_PX = 96

    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceIn(48, 192),
    ) {}

    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            cache.get(packageName)?.let { return@withContext it }
            val pm = context.applicationContext.packageManager
            val drawable = try {
                pm.getApplicationIcon(packageName)
            } catch (_: Exception) {
                return@withContext null
            }
            val bitmap = drawable.toSafeImageBitmap(ICON_SIZE_PX)
            cache.put(packageName, bitmap)
            bitmap
        }

    fun clear() {
        cache.evictAll()
    }
}
