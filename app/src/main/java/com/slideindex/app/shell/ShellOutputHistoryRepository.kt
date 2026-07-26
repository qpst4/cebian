package com.slideindex.app.shell

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ShellOutputHistoryEntry(
    val id: String,
    val label: String,
    val command: String,
    val exitCode: Int,
    val output: String,
    val executedAtEpochMs: Long,
)

@Singleton
class ShellOutputHistoryRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val historyFile = File(appContext.filesDir, HISTORY_FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<ShellOutputHistoryEntry>>(emptyList())
    val entries: StateFlow<List<ShellOutputHistoryEntry>> = _entries.asStateFlow()

    init {
        _entries.value = readFromDiskSync()
    }

    suspend fun append(
        label: String,
        command: String,
        exitCode: Int,
        output: String,
    ) {
        mutex.withLock {
            val entry = ShellOutputHistoryEntry(
                id = "${System.currentTimeMillis()}-${command.hashCode()}",
                label = label,
                command = command,
                exitCode = exitCode,
                output = output,
                executedAtEpochMs = System.currentTimeMillis(),
            )
            val next = (listOf(entry) + readFromDisk()).take(MAX_ENTRIES)
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

    suspend fun exportRawJson(): String? = mutex.withLock {
        if (!historyFile.exists()) return@withLock null
        withContext(Dispatchers.IO) { historyFile.readText() }
    }

    suspend fun importRawJson(raw: String): Result<Unit> = mutex.withLock {
        runCatching {
            val decoded = json.decodeFromString<List<ShellOutputHistoryEntry>>(raw)
            val next = decoded.take(MAX_ENTRIES)
            writeToDisk(next)
            _entries.value = next
        }
    }

    suspend fun reloadFromDisk() {
        mutex.withLock {
            _entries.value = readFromDiskSync()
        }
    }

    private fun readFromDiskSync(): List<ShellOutputHistoryEntry> = runCatching {
        if (!historyFile.exists()) return emptyList()
        json.decodeFromString<List<ShellOutputHistoryEntry>>(historyFile.readText())
    }.getOrDefault(emptyList())

    private suspend fun readFromDisk(): List<ShellOutputHistoryEntry> = withContext(Dispatchers.IO) {
        readFromDiskSync()
    }

    private suspend fun writeToDisk(entries: List<ShellOutputHistoryEntry>) = withContext(Dispatchers.IO) {
        historyFile.writeText(json.encodeToString(entries))
    }

    companion object {
        private const val HISTORY_FILE_NAME = "shell_output_history.json"
        private const val MAX_ENTRIES = 40
    }
}
