package com.slideindex.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ClipboardEntry(
    val id: String,
    val text: String,
    val createdAtEpochMs: Long,
)

@Singleton
class ClipboardHistoryRepository @Inject constructor(
    @ApplicationContext appContext: Context,
) {
    private val context = appContext.applicationContext
    private val storageDir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    private val indexFile = File(storageDir, INDEX_FILE_NAME)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = _entries.asStateFlow()

    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastCapturedText: String? = null

    init {
        ClipboardAccess.repository = this
        _entries.value = loadEntries()
    }

    suspend fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        mutex.withLock {
            val current = _entries.value
            if (current.firstOrNull()?.text == trimmed) return
            val entry = ClipboardEntry(
                id = UUID.randomUUID().toString(),
                text = trimmed,
                createdAtEpochMs = System.currentTimeMillis(),
            )
            val next = listOf(entry) + current.filterNot { it.text == trimmed }
            persist(next.take(MAX_ENTRIES))
        }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            persist(_entries.value.filterNot { it.id == id })
        }
    }

    suspend fun clearAll() {
        mutex.withLock { persist(emptyList()) }
    }

    /** 在悬浮窗获得焦点后调用，强制重新读取系统剪贴板（Android 10+ 无焦点时读不到）。 */
    fun refreshClipboard() {
        lastCapturedText = null
        captureFromSystemClipboard()
    }

    fun captureFromSystemClipboard(): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = clipboard.primaryClip ?: return false
        if (clip.itemCount <= 0) return false
        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (text == lastCapturedText) return false
        lastCapturedText = text
        scope.launch { addText(text) }
        return true
    }

    fun startListening() {
        if (clipListener != null) return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            captureFromSystemClipboard()
        }
        clipListener = listener
        clipboard.addPrimaryClipChangedListener(listener)
        captureFromSystemClipboard()
    }

    fun stopListening() {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipListener?.let { clipboard.removePrimaryClipChangedListener(it) }
        clipListener = null
    }

    private fun loadEntries(): List<ClipboardEntry> = runCatching {
        if (!indexFile.exists()) return emptyList()
        json.decodeFromString<List<ClipboardEntry>>(indexFile.readText())
    }.getOrDefault(emptyList())

    private fun persist(entries: List<ClipboardEntry>) {
        _entries.value = entries
        indexFile.writeText(json.encodeToString(entries))
    }

    companion object {
        private const val DIR_NAME = "clipboard"
        private const val INDEX_FILE_NAME = "history.json"
        private const val MAX_ENTRIES = 50
    }
}
