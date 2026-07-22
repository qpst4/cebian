package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = readFromDisk()
                val removed = current.firstOrNull { it.id == id } ?: return@withLock
                removed.imageFileName?.let { File(imageDir, it).delete() }
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
                current.forEach { entry ->
                    entry.imageFileName?.let { File(imageDir, it).delete() }
                }
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
        return BitmapFactory.decodeFile(File(imageDir, fileName).absolutePath)
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
    }
}
