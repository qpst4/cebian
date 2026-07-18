package com.slideindex.app.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AggregatedImageSearchIconLoader {
    private fun cacheFile(context: Context, engine: ImageSearchEngine): File {
        val dir = File(context.filesDir, "search_icons").apply { mkdirs() }
        return File(dir, "imgagg-${engine.name.lowercase()}.png")
    }

    suspend fun load(context: Context, engine: ImageSearchEngine): Bitmap? = withContext(Dispatchers.IO) {
        val cached = cacheFile(context, engine)
        if (cached.exists()) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { return@withContext it }
        }
        val sourceUrl = engine.faviconSourceUrl ?: return@withContext null
        val tempPath = SearchEngineFaviconFetcher.fetchAndSave(context, sourceUrl) ?: return@withContext null
        val tempFile = File(context.filesDir, tempPath)
        if (!tempFile.exists()) return@withContext null
        runCatching {
            tempFile.copyTo(cached, overwrite = true)
            SearchEngineIconStorage.deleteIconIfOwned(context, tempPath)
            BitmapFactory.decodeFile(cached.absolutePath)
        }.getOrNull()
    }
}
