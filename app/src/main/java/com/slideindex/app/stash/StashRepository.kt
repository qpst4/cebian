package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.core.content.FileProvider
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class StashRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val stashDir = File(appContext.filesDir, STASH_DIR_NAME).apply { mkdirs() }
    private val imageDir = File(stashDir, IMAGE_DIR_NAME).apply { mkdirs() }
    private val indexFile = File(stashDir, INDEX_FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val thumbnailCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val _entries = MutableStateFlow<List<StashEntry>>(emptyList())
    val entries: StateFlow<List<StashEntry>> = _entries.asStateFlow()

    init {
        _entries.value = readFromDiskSync()
        StashAccess.repository = this
    }

    suspend fun addText(text: String): StashEntry? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val entry = StashEntry(
                    id = UUID.randomUUID().toString(),
                    type = StashEntryType.TEXT,
                    text = trimmed,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
                val next = listOf(entry) + readFromDisk()
                writeToDisk(next)
                _entries.value = next
                entry
            }
        }
    }

    suspend fun addImage(
        bitmap: Bitmap,
        pinDisplayWidthPx: Int? = null,
        pinDisplayHeightPx: Int? = null,
    ): StashEntry? {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                val fileName = "$id.png"
                val saved = saveImage(fileName, bitmap) ?: return@withLock null
                val entry = StashEntry(
                    id = id,
                    type = StashEntryType.IMAGE,
                    imageFileName = saved,
                    createdAtEpochMs = System.currentTimeMillis(),
                    pinDisplayWidthPx = pinDisplayWidthPx?.takeIf { it > 0 },
                    pinDisplayHeightPx = pinDisplayHeightPx?.takeIf { it > 0 },
                )
                val next = listOf(entry) + readFromDisk()
                writeToDisk(next)
                _entries.value = next
                entry
            }
        }
    }

    suspend fun addRich(
        parts: List<StashRichPart>,
        htmlText: String? = null,
    ): StashEntry? {
        if (parts.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                val imageTotal = parts.count { it is StashRichPart.Image }
                var imageIndex = 0
                val contentBlocks = mutableListOf<ClipboardContentBlock>()
                val textParts = mutableListOf<String>()
                val savedFiles = mutableListOf<String>()

                for (part in parts) {
                    when (part) {
                        is StashRichPart.Text -> {
                            val trimmed = part.text.trim()
                            if (trimmed.isEmpty()) continue
                            contentBlocks += ClipboardContentBlock.text(trimmed)
                            textParts += trimmed
                        }
                        is StashRichPart.Image -> {
                            val fileName = if (imageTotal <= 1) {
                                "$id.png"
                            } else {
                                "${id}_${imageIndex++}.png"
                            }
                            val saved = saveImage(fileName, part.bitmap)
                            if (saved == null) continue
                            savedFiles += saved
                            contentBlocks += ClipboardContentBlock.image(saved)
                        }
                    }
                }

                if (contentBlocks.isEmpty()) {
                    savedFiles.forEach { File(imageDir, it).delete() }
                    return@withLock null
                }

                val entry = StashEntry(
                    id = id,
                    type = StashEntryType.RICH,
                    text = textParts.joinToString("\n\n").ifBlank { null },
                    imageFileName = contentBlocks
                        .firstOrNull { it.kind == ClipboardBlockKind.IMAGE }
                        ?.fileName,
                    contentBlocks = contentBlocks,
                    htmlText = htmlText?.trim()?.takeIf { it.isNotEmpty() },
                    createdAtEpochMs = System.currentTimeMillis(),
                )
                val next = listOf(entry) + readFromDisk()
                writeToDisk(next)
                _entries.value = next
                entry
            }
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = readFromDisk()
                val removed = current.firstOrNull { it.id == id } ?: return@withLock
                deleteEntryImages(removed)
                val next = current.filterNot { it.id == id }
                writeToDisk(next)
                _entries.value = next
            }
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = readFromDisk()
                current.forEach { deleteEntryImages(it) }
                writeToDisk(emptyList())
                _entries.value = emptyList()
            }
        }
    }

    suspend fun toggleStar(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = readFromDisk().map { entry ->
                    if (entry.id == id) entry.copy(starred = !entry.starred) else entry
                }
                writeToDisk(next)
                _entries.value = next
            }
        }
    }

    fun loadImage(entry: StashEntry): Bitmap? {
        val fileName = entry.imageFileName ?: return null
        return loadBitmapByFileName(fileName)
    }

    fun loadBitmapByFileName(fileName: String?): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun loadImageThumbnail(entry: StashEntry, maxSidePx: Int = STASH_PREVIEW_MAX_SIDE_PX): Bitmap? {
        val fileName = entry.imageFileName ?: return null
        return loadThumbnailByFileName(entry.id, fileName, maxSidePx)
    }

    fun loadEntryThumbnailsForPreview(
        entry: StashEntry,
        maxSidePx: Int = STASH_PREVIEW_MAX_SIDE_PX,
    ): List<Bitmap> =
        entry.allImageFileNames().mapNotNull { fileName ->
            loadThumbnailByFileName(entry.id, fileName, maxSidePx)
        }

    fun loadThumbnailByFileName(
        entryId: String,
        fileName: String?,
        maxSidePx: Int = STASH_PREVIEW_MAX_SIDE_PX,
    ): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val cacheKey = "$entryId:$fileName:side$maxSidePx"
        thumbnailCache.get(cacheKey)?.let { return it }
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSidePx)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        thumbnailCache.put(cacheKey, bitmap)
        return bitmap
    }

    fun uriForFile(fileName: String?): Uri? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
    }

    fun dataUriForFile(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/png;base64,$encoded"
        }.getOrNull()
    }

    fun imageDimensions(fileName: String?): Pair<Int, Int>? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth to bounds.outHeight
        } else {
            null
        }
    }

    private fun deleteEntryImages(entry: StashEntry) {
        entry.allImageFileNames().forEach { File(imageDir, it).delete() }
    }

    /**
     * 暂存夹卡片预览：按卡片宽度解码，保证 [ContentScale.FillWidth] 不放大模糊；
     * 超长截图只保留顶部可见区域，避免整图解码 OOM。
     */
    fun loadImageThumbnailForCard(
        entry: StashEntry,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int,
    ): Bitmap? {
        if (targetWidthPx <= 0 || maxVisibleHeightPx <= 0) return null
        val fileName = entry.imageFileName ?: return null
        val cacheKey = "${entry.id}:w$targetWidthPx:h$maxVisibleHeightPx"
        thumbnailCache.get(cacheKey)?.let { return it }
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetWidthPx * 2) {
            sampleSize *= 2
        }
        val maxPixels = targetWidthPx.toLong() * maxVisibleHeightPx * 2L
        while (
            (bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight / sampleSize) > maxPixels
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        if (bitmap.width != targetWidthPx) {
            val scaledHeight = (
                bitmap.height.toFloat() * targetWidthPx / bitmap.width.coerceAtLeast(1)
                ).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidthPx, scaledHeight, true)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }

        if (bitmap.height > maxVisibleHeightPx) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, maxVisibleHeightPx)
            if (cropped !== bitmap) {
                bitmap.recycle()
                bitmap = cropped
            }
        }

        thumbnailCache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var sampleSize = 1
        var longest = maxOf(width, height)
        while (longest / sampleSize > maxSidePx) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun saveImage(fileName: String, bitmap: Bitmap): String? {
        val file = File(imageDir, fileName)
        return runCatching {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            fileName
        }.getOrNull()
    }

    private fun readFromDiskSync(): List<StashEntry> = readFromDisk()

    private fun readFromDisk(): List<StashEntry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StashEntry>>(indexFile.readText())
        }.getOrDefault(emptyList())
    }

    private fun writeToDisk(entries: List<StashEntry>) {
        indexFile.writeText(json.encodeToString(entries))
    }

    private companion object {
        const val STASH_DIR_NAME = "stash"
        const val IMAGE_DIR_NAME = "images"
        const val INDEX_FILE_NAME = "index.json"
        const val STASH_PREVIEW_MAX_SIDE_PX = 720
    }
}
