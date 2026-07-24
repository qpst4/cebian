package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

/**
 * LRU cache for clipboard list preview thumbnails so [LazyColumn] recycle does not re-decode.
 */
object ClipboardThumbnailCache {
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024

    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun loadEntryThumbnailsForPreview(
        context: Context,
        entry: ClipboardEntry,
        maxSidePx: Int,
    ): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { fileName ->
                getOrLoadFile(context, fileName, maxSidePx)
            }
        }
        if (!entry.hasImageContent() || entry.uri.isNullOrBlank()) return emptyList()
        return getOrLoadUri(context, entry.uri, maxSidePx)?.let { listOf(it) } ?: emptyList()
    }

    fun loadBlockThumbnail(context: Context, fileName: String, maxSidePx: Int): Bitmap? =
        getOrLoadFile(context, fileName, maxSidePx)

    fun evictEntry(entry: ClipboardEntry) {
        val fileNames = entry.resolvedImageFileNames()
        val uri = entry.uri?.takeIf { it.isNotBlank() }
        val keysToRemove = bitmapCache.snapshot().keys.filter { key ->
            fileNames.any { fileName -> key.startsWith("file:$fileName:") } ||
                (uri != null && key.startsWith("uri:$uri:"))
        }
        keysToRemove.forEach { bitmapCache.remove(it) }
    }

    fun clear() {
        bitmapCache.evictAll()
    }

    private fun getOrLoadFile(context: Context, fileName: String, maxSidePx: Int): Bitmap? {
        val key = fileKey(fileName, maxSidePx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadBitmapScaled(context, fileName, maxSidePx) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun getOrLoadUri(context: Context, uri: String, maxSidePx: Int): Bitmap? {
        val key = uriKey(uri, maxSidePx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadUriBitmapScaled(context, uri, maxSidePx) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun fileKey(fileName: String, maxSidePx: Int = 0): String = "file:$fileName:$maxSidePx"

    private fun uriKey(uri: String, maxSidePx: Int = 0): String = "uri:$uri:$maxSidePx"
}
