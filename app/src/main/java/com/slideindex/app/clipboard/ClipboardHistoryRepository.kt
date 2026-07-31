package com.slideindex.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.slideindex.app.clipboard.monitor.ClipboardMonitorController
import com.slideindex.app.clipboard.monitor.ClipboardMonitorProcess
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.SettingsRepository
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
    val type: ClipboardEntryType = ClipboardEntryType.TEXT,
    val text: String,
    val uri: String? = null,
    val intentUri: String? = null,
    val htmlText: String? = null,
    val mimeType: String? = null,
    val imageFileName: String? = null,
    val imageFileNames: List<String> = emptyList(),
    val contentBlocks: List<ClipboardContentBlock> = emptyList(),
    val createdAtEpochMs: Long,
) {
    fun contentKey(): String = ClipboardContentKey.forEntry(this)

    fun matchesQuery(query: String): Boolean {
        val lower = query.lowercase()
        return text.contains(lower, ignoreCase = true) ||
            uri?.contains(lower, ignoreCase = true) == true ||
            intentUri?.contains(lower, ignoreCase = true) == true
    }
}

@Singleton
class ClipboardHistoryRepository @Inject constructor(
    @ApplicationContext appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val clipboardMonitorController: ClipboardMonitorController,
) {
    private val context = appContext.applicationContext
    private val storageDir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    private val indexFile = File(storageDir, INDEX_FILE_NAME)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = _entries.asStateFlow()

    private val refreshDebounceHandler = Handler(Looper.getMainLooper())
    private var pendingRefreshContext: Context? = null
    private var pendingPromoteExistingOnMatch = false
    private var pendingPassiveClipboardRefresh = false
    private val refreshRunnable = Runnable { performClipboardRefresh(pendingRefreshContext) }

    private var screenshotMonitor: ScreenshotMonitor? = null
    private var lastCapturedKey: String? = null
    private var lastCapturedFingerprint: String? = null
    private var lastCapturedAtMs: Long = 0L
    private var lastScreenshotIngestAtMs: Long = 0L
    private var outgoingWriteKey: String? = null
    private var outgoingWriteFingerprint: String? = null
    private var outgoingWriteSuppressUntilMs: Long = 0L
    private var suppressedOutgoingEntryId: String? = null
    private var skipIngestRemaining: Int = 0
    private val inFlightFingerprints = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        ClipboardAccess.repository = this
        _entries.value = trimToConfiguredMax(loadEntries())
        clipboardMonitorController.onPayloadCaptured = { payload ->
            scope.launch {
                ingestPayload(payload)
            }
        }
    }

    suspend fun addPayload(
        payload: ClipboardPayload,
        promoteExistingOnMatch: Boolean = true,
        fromPassiveRefresh: Boolean = false,
    ) {
        if (payload.text.trim().isEmpty() &&
            payload.uri.isNullOrBlank() &&
            payload.intentUri.isNullOrBlank() &&
            !payload.hasImageContent()
        ) {
            return
        }
        mutex.withLock {
            val current = _entries.value
            val contentKey = payload.contentKey()
            val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
            findMatchingEntry(current, payload)?.let { existing ->
                if (!promoteExistingOnMatch) return
                if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
                lastCapturedKey = contentKey
                lastCapturedFingerprint = fingerprint
                lastCapturedAtMs = System.currentTimeMillis()
                persist(trimToConfiguredMax(promoteExistingEntry(current, existing)))
                return
            }
            val entryId = UUID.randomUUID().toString()
            val imageFileNames = persistPayloadImages(entryId, payload)
            val imageSources = ClipboardImageStore.collectImageSources(payload)
            val baseEntry = payload.toEntry(
                id = entryId,
                createdAtEpochMs = System.currentTimeMillis(),
            ).copy(
                imageFileName = imageFileNames.firstOrNull() ?: payload.imageFileName,
                imageFileNames = imageFileNames.ifEmpty { payload.resolvedImageFileNames() },
            )
            val entry = baseEntry.copy(
                contentBlocks = ClipboardBlockParser.buildBlocks(
                    text = baseEntry.text,
                    htmlText = baseEntry.htmlText,
                    imageFileNames = baseEntry.resolvedImageFileNames(),
                    imageSources = imageSources,
                ),
            )
            if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
            val next = listOf(entry) + current.filterNot {
                ClipboardContentEquivalence.fingerprint(it) == fingerprint ||
                    ClipboardContentEquivalence.matches(it, payload)
            }
            lastCapturedKey = contentKey
            lastCapturedFingerprint = fingerprint
            lastCapturedAtMs = System.currentTimeMillis()
            persist(trimToConfiguredMax(next))
        }
    }

    suspend fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        addPayload(
            ClipboardPayload(
                type = ClipboardEntryType.TEXT,
                text = trimmed,
            ),
        )
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            val removed = _entries.value.firstOrNull { it.id == id }
            removed?.let { ClipboardImageStore.deleteEntryImages(context, it) }
            persist(_entries.value.filterNot { it.id == id })
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            _entries.value.forEach { entry ->
                ClipboardImageStore.deleteEntryImages(context, entry)
            }
            persist(emptyList())
        }
    }

    suspend fun trimToConfiguredMax() {
        mutex.withLock {
            persist(trimToConfiguredMax(_entries.value))
        }
    }

    /**
     * 通过 1×1 悬浮窗抢焦点读取系统剪贴板（Android 10+）。
     * 后台监听路径会做防抖，避免连续复制时频繁 add/remove 悬浮窗导致卡顿。
     */
    fun refreshClipboardWithFocus(
        triggerContext: Context? = null,
        force: Boolean = false,
        promoteExistingOnMatch: Boolean = true,
    ) {
        if (force) {
            cancelScheduledClipboardRefresh()
            pendingPassiveClipboardRefresh = true
            pendingPromoteExistingOnMatch = promoteExistingOnMatch
            performClipboardRefresh(triggerContext)
            return
        }
        scheduleClipboardRefresh(
            triggerContext = triggerContext,
            promoteExistingOnMatch = promoteExistingOnMatch,
            passiveRefresh = true,
        )
    }

    /** 在悬浮窗获得焦点后调用，强制重新读取系统剪贴板（Android 10+ 无焦点时读不到）。 */
    fun refreshClipboard(readContext: Context? = null) {
        refreshClipboardWithFocus(readContext, force = true)
    }

    fun noteOutgoingWrite(entry: ClipboardEntry) {
        val key = entry.contentKey()
        val fingerprint = ClipboardContentEquivalence.fingerprint(entry)
        lastCapturedKey = key
        lastCapturedFingerprint = fingerprint
        lastCapturedAtMs = System.currentTimeMillis()
        outgoingWriteKey = key
        outgoingWriteFingerprint = fingerprint
        suppressedOutgoingEntryId = entry.id
        outgoingWriteSuppressUntilMs = System.currentTimeMillis() + 4_000L
        // 监听与 logcat 可能各触发一次刷新，预留余量避免回弹入库
        skipIngestRemaining = 3
        clipboardMonitorController.config.ignoreNextCopy = true
    }

    /** 应用内写回剪贴板时，将对应历史条目置顶（不经过系统监听回流）。 */
    fun promoteById(id: String) {
        scope.launch {
            mutex.withLock {
                val current = _entries.value
                val existing = current.firstOrNull { it.id == id } ?: return@withLock
                persist(trimToConfiguredMax(promoteExistingEntry(current, existing)))
            }
        }
    }

    fun ingestPayload(
        payload: ClipboardPayload,
        promoteExistingOnMatch: Boolean = true,
        fromPassiveRefresh: Boolean = false,
    ) {
        if (consumeOutgoingWriteSkip()) return
        if (payload.text.trim().isEmpty() &&
            payload.uri.isNullOrBlank() &&
            payload.intentUri.isNullOrBlank() &&
            !payload.hasImageContent()
        ) {
            return
        }
        val contentKey = payload.contentKey()
        val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
        val now = System.currentTimeMillis()
        if ((contentKey == lastCapturedKey || fingerprint == lastCapturedFingerprint) &&
            now - lastCapturedAtMs < SAME_CLIP_DEDUP_MS
        ) {
            if (promoteExistingOnMatch) {
                promoteExistingPayloadIfNeeded(payload, fromPassiveRefresh)
            }
            return
        }
        if (now < outgoingWriteSuppressUntilMs) {
            if (contentKey == outgoingWriteKey || fingerprint == outgoingWriteFingerprint) {
                lastCapturedKey = contentKey
                lastCapturedFingerprint = fingerprint
                lastCapturedAtMs = now
                return
            }
            suppressedOutgoingEntryId?.let { entryId ->
                val suppressedEntry = _entries.value.firstOrNull { it.id == entryId }
                if (suppressedEntry != null && ClipboardContentEquivalence.matches(suppressedEntry, payload)) {
                    lastCapturedKey = contentKey
                    lastCapturedFingerprint = fingerprint
                    lastCapturedAtMs = now
                    return
                }
            }
        }
        if (!inFlightFingerprints.add(fingerprint)) {
            if (promoteExistingOnMatch) {
                promoteExistingPayloadIfNeeded(payload, fromPassiveRefresh)
            }
            return
        }
        lastCapturedKey = contentKey
        lastCapturedFingerprint = fingerprint
        lastCapturedAtMs = now
        scope.launch {
            try {
                addPayload(payload, promoteExistingOnMatch, fromPassiveRefresh)
            } finally {
                inFlightFingerprints.remove(fingerprint)
            }
        }
    }

    fun ingestCapturedText(text: String) {
        ingestPayload(
            ClipboardPayload(
                type = ClipboardEntryType.TEXT,
                text = text,
            ),
        )
    }

    fun ingestScreenshot(uri: Uri, displayName: String?, mimeType: String? = "image/png") {
        lastScreenshotIngestAtMs = System.currentTimeMillis()
        val label = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: "screenshot"
        ingestPayload(
            ClipboardPayload(
                type = ClipboardEntryType.URI,
                text = label,
                uri = uri.toString(),
                mimeType = mimeType?.takeIf { it.isNotBlank() } ?: "image/*",
                imageUris = listOf(uri.toString()),
            ),
        )
    }

    fun captureFromSystemClipboard(readContext: Context? = null): Boolean {
        val payload = ClipboardReader.read(readContext ?: context) ?: return false
        ingestPayload(payload)
        return true
    }

    fun syncClipboardMonitoringFromSettings() {
        if (!ClipboardMonitorProcess.isMainProcess(context)) return
        val settings = settingsRepository.readSnapshot()
        if (!settings.clipboardBackgroundMonitoring) {
            stopClipboardListening()
            return
        }
        clipboardMonitorController.startIfNeeded(settings.clipboardBackgroundMonitoringMode)
    }

    fun restartClipboardMonitoringFromSettings() {
        if (!ClipboardMonitorProcess.isMainProcess(context)) return
        val settings = settingsRepository.readSnapshot()
        if (!settings.clipboardBackgroundMonitoring) {
            stopClipboardListening()
            return
        }
        clipboardMonitorController.restart(settings.clipboardBackgroundMonitoringMode)
    }

    fun startClipboardListening() {
        restartClipboardMonitoringFromSettings()
    }

    fun stopClipboardListening() {
        cancelScheduledClipboardRefresh()
        clipboardMonitorController.stop()
    }

    fun startScreenshotMonitoring() {
        stopScreenshotMonitoring()
        if (!settingsRepository.readSnapshot().clipboardScreenshotMonitoring) return
        if (!ClipboardPermissionHelper.hasMediaReadPermission(context)) return
        startScreenshotMonitor()
    }

    fun stopScreenshotMonitoring() {
        screenshotMonitor?.stop()
        screenshotMonitor = null
    }

    fun startListening() {
        startClipboardListening()
        startScreenshotMonitoring()
    }

    fun stopListening() {
        stopClipboardListening()
        stopScreenshotMonitoring()
    }

    private fun startScreenshotMonitor() {
        if (!ClipboardPermissionHelper.hasMediaReadPermission(context)) return
        screenshotMonitor = ScreenshotMonitor(context) { uri, displayName, mimeType ->
            ingestScreenshot(uri, displayName, mimeType)
        }.also { it.start() }
    }

    private var pendingUseFocusReader = true

    private fun scheduleClipboardRefresh(
        triggerContext: Context? = null,
        useFocusReader: Boolean = shouldUseFocusReader(),
        promoteExistingOnMatch: Boolean = true,
        passiveRefresh: Boolean = false,
    ) {
        pendingPassiveClipboardRefresh = passiveRefresh
        pendingRefreshContext = triggerContext ?: pendingRefreshContext
        pendingUseFocusReader = useFocusReader
        pendingPromoteExistingOnMatch = promoteExistingOnMatch
        refreshDebounceHandler.removeCallbacks(refreshRunnable)
        refreshDebounceHandler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
    }

    private fun shouldUseFocusReader(): Boolean = true

    private fun cancelScheduledClipboardRefresh() {
        pendingRefreshContext = null
        pendingPromoteExistingOnMatch = false
        pendingPassiveClipboardRefresh = false
        refreshDebounceHandler.removeCallbacks(refreshRunnable)
    }

    private fun resolvePromoteExistingOnMatch(requested: Boolean): Boolean {
        if (pendingPassiveClipboardRefresh) return false
        return requested
    }

    private fun shouldBlockDisplacingScreenshot(
        payload: ClipboardPayload,
        fromPassiveRefresh: Boolean,
    ): Boolean {
        if (!fromPassiveRefresh) return false
        if (payload.hasImageContent()) return false
        if (System.currentTimeMillis() - lastScreenshotIngestAtMs >= SCREENSHOT_TOP_GUARD_MS) return false
        val top = _entries.value.firstOrNull() ?: return false
        return top.hasImageContent()
    }

    private fun performClipboardRefresh(triggerContext: Context? = null) {
        val fromPassiveRefresh = pendingPassiveClipboardRefresh
        pendingRefreshContext = null
        val promoteExistingOnMatch = resolvePromoteExistingOnMatch(pendingPromoteExistingOnMatch)
        pendingPromoteExistingOnMatch = false
        pendingPassiveClipboardRefresh = false
        if (consumeOutgoingWriteSkip()) return
        val readContext = triggerContext ?: context
        if (pendingUseFocusReader) {
            ClipboardFocusReader.read(readContext) { payload ->
                if (payload != null) ingestPayload(payload, promoteExistingOnMatch, fromPassiveRefresh)
            }
        } else {
            ClipboardReader.read(readContext)?.let {
                ingestPayload(it, promoteExistingOnMatch, fromPassiveRefresh)
            }
        }
    }

    private fun consumeOutgoingWriteSkip(): Boolean {
        if (skipIngestRemaining <= 0) return false
        skipIngestRemaining--
        return true
    }

    private fun findMatchingEntry(
        entries: List<ClipboardEntry>,
        payload: ClipboardPayload,
    ): ClipboardEntry? {
        val contentKey = payload.contentKey()
        val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
        return entries.firstOrNull {
            it.contentKey() == contentKey ||
                ClipboardContentEquivalence.fingerprint(it) == fingerprint ||
                ClipboardContentEquivalence.matches(it, payload)
        }
    }

    private fun promoteExistingPayloadIfNeeded(
        payload: ClipboardPayload,
        fromPassiveRefresh: Boolean = false,
    ) {
        if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
        val existing = findMatchingEntry(_entries.value, payload) ?: return
        scope.launch {
            mutex.withLock {
                val current = _entries.value
                val match = current.firstOrNull { it.id == existing.id } ?: return@withLock
                if (current.firstOrNull()?.id == match.id) return@withLock
                val contentKey = payload.contentKey()
                val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
                lastCapturedKey = contentKey
                lastCapturedFingerprint = fingerprint
                lastCapturedAtMs = System.currentTimeMillis()
                persist(trimToConfiguredMax(promoteExistingEntry(current, match)))
            }
        }
    }

    private fun promoteExistingEntry(
        current: List<ClipboardEntry>,
        existing: ClipboardEntry,
    ): List<ClipboardEntry> {
        val promoted = existing.copy(createdAtEpochMs = System.currentTimeMillis())
        return listOf(promoted) + current.filterNot { it.id == existing.id }
    }

    private fun persistPayloadImages(entryId: String, payload: ClipboardPayload): List<String> {
        if (!payload.hasImageContent()) return emptyList()
        val existing = payload.resolvedImageFileNames().filter {
            ClipboardImageStore.imageFile(context, it).exists()
        }
        if (existing.isNotEmpty()) return existing
        return ClipboardImageStore.persistAllFromPayload(context, entryId, payload)
    }

    private fun configuredMaxEntries(): Int =
        settingsRepository.readSnapshot().clipboardHistoryMaxEntries

    private fun trimToConfiguredMax(entries: List<ClipboardEntry>): List<ClipboardEntry> {
        val max = configuredMaxEntries()
        if (max < 0) return entries
        if (entries.size <= max) return entries
        val kept = entries.take(max)
        entries.drop(max).forEach { entry ->
            ClipboardImageStore.deleteEntryImages(context, entry)
        }
        return kept
    }

    private fun loadEntries(): List<ClipboardEntry> = runCatching {
        if (!indexFile.exists()) return emptyList()
        json.decodeFromString<List<ClipboardEntry>>(indexFile.readText())
    }.getOrDefault(emptyList())

    private fun persist(entries: List<ClipboardEntry>) {
        _entries.value = entries
        indexFile.writeText(json.encodeToString(entries))
    }

    suspend fun reloadFromDisk() {
        mutex.withLock {
            _entries.value = trimToConfiguredMax(loadEntries())
        }
    }

    companion object {
        private const val DIR_NAME = "clipboard"
        private const val INDEX_FILE_NAME = "history.json"
        private const val REFRESH_DEBOUNCE_MS = 400L
        private const val SAME_CLIP_DEDUP_MS = 400L
        private const val SCREENSHOT_TOP_GUARD_MS = 10_000L
    }
}
