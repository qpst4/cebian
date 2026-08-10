package com.slideindex.app.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val createdAtEpochMs: Long,
)

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val historyFile = File(appContext.filesDir, HISTORY_FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val entries: StateFlow<List<SearchHistoryEntry>> = _entries.asStateFlow()

    init {
        _entries.value = readFromDiskSync()
        SearchHistoryAccess.repository = this
    }

    fun recordAsync(query: String, maxEntries: Int) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            append(trimmed, maxEntries)
        }
    }

    suspend fun append(query: String, maxEntries: Int) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val limit = maxEntries.coerceAtLeast(1)
        mutex.withLock {
            val entry = SearchHistoryEntry(
                query = trimmed,
                createdAtEpochMs = System.currentTimeMillis(),
            )
            val withoutDup = readFromDisk().filterNot { it.query == trimmed }
            val next = (listOf(entry) + withoutDup).take(limit)
            writeToDisk(next)
            _entries.value = next
        }
    }

    suspend fun trimToMax(maxEntries: Int) {
        val limit = maxEntries.coerceAtLeast(1)
        mutex.withLock {
            val current = readFromDisk()
            if (current.size <= limit) return@withLock
            val next = current.take(limit)
            writeToDisk(next)
            _entries.value = next
        }
    }

    suspend fun clear() {
        mutex.withLock {
            writeToDisk(emptyList())
            _entries.value = emptyList()
        }
    }

    private fun readFromDiskSync(): List<SearchHistoryEntry> = runCatching {
        if (!historyFile.exists()) return emptyList()
        json.decodeFromString<List<SearchHistoryEntry>>(historyFile.readText())
    }.getOrDefault(emptyList())

    private suspend fun readFromDisk(): List<SearchHistoryEntry> = withContext(Dispatchers.IO) {
        readFromDiskSync()
    }

    private suspend fun writeToDisk(entries: List<SearchHistoryEntry>) = withContext(Dispatchers.IO) {
        historyFile.writeText(json.encodeToString(entries))
    }

    companion object {
        private const val HISTORY_FILE_NAME = "search_panel_history.json"
    }
}

object SearchHistoryAccess {
    @Volatile
    var repository: SearchHistoryRepository? = null
}
