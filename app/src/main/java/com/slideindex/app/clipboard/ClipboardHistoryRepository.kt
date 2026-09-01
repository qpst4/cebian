package com.slideindex.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.slideindex.app.clipboard.monitor.ClipboardMonitorController
import com.slideindex.app.clipboard.monitor.ClipboardMonitorProcess
import com.slideindex.app.settings.effectiveClipboardMonitoringMode
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val store = ClipboardHistoryStore(context, json)
    private val _entryCount = MutableStateFlow(0)
    val entryCount: StateFlow<Int> = _entryCount.asStateFlow()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

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
        migrateLegacyJsonIfNeeded()
        refreshEntryCount()
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
        writeMutex.withLock {
            val contentKey = payload.contentKey()
            val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
            findMatchingEntry(payload)?.let { existing ->
                if (!promoteExistingOnMatch) return
                if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
                lastCapturedKey = contentKey
                lastCapturedFingerprint = fingerprint
                lastCapturedAtMs = System.currentTimeMillis()
                promoteExistingEntry(existing)
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
            removeMatchingEntries(payload, fingerprint)
            lastCapturedKey = contentKey
            lastCapturedFingerprint = fingerprint
            lastCapturedAtMs = System.currentTimeMillis()
            store.insert(entry)
            applyTrimToConfiguredMaxLocked()
            bumpRevisionLocked()
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
        writeMutex.withLock {
            val removed = store.queryById(id)
            removed?.let { ClipboardImageStore.deleteEntryImages(context, it) }
            store.delete(id)
            bumpRevisionLocked()
        }
    }

    suspend fun clearAll() {
        writeMutex.withLock {
            var cursor: Long? = null
            while (true) {
                val batch = store.queryPageBefore(cursor, 100)
                if (batch.isEmpty()) break
                batch.forEach { ClipboardImageStore.deleteEntryImages(context, it) }
                if (batch.size < 100) break
                cursor = batch.last().createdAtEpochMs
            }
            store.deleteAll()
            bumpRevisionLocked()
        }
    }

    suspend fun trimToConfiguredMax() {
        writeMutex.withLock {
            applyTrimToConfiguredMaxLocked()
            bumpRevisionLocked()
        }
    }

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
        skipIngestRemaining = 3
        clipboardMonitorController.config.ignoreNextCopy = true
    }

    fun promoteById(id: String) {
        scope.launch {
            writeMutex.withLock {
                val existing = store.queryById(id) ?: return@withLock
                promoteExistingEntry(existing)
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
                val suppressedEntry = store.queryById(entryId)
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
        clipboardMonitorController.startIfNeeded(settings.effectiveClipboardMonitoringMode())
    }

    fun restartClipboardMonitoringFromSettings() {
        if (!ClipboardMonitorProcess.isMainProcess(context)) return
        val settings = settingsRepository.readSnapshot()
        if (!settings.clipboardBackgroundMonitoring) {
            stopClipboardListening()
            return
        }
        clipboardMonitorController.restart(settings.effectiveClipboardMonitoringMode())
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

    suspend fun reloadFromDisk() {
        writeMutex.withLock {
            if (indexFile.exists()) {
                val legacy = runCatching {
                    json.decodeFromString<List<ClipboardEntry>>(indexFile.readText())
                }.getOrDefault(emptyList())
                store.deleteAll()
                if (legacy.isNotEmpty()) {
                    store.migrateFromJsonIndex(trimLegacyToConfiguredMax(legacy))
                }
                indexFile.renameTo(File(storageDir, "$INDEX_FILE_NAME.migrated"))
            }
            refreshEntryCountLocked()
            bumpRevisionLocked()
        }
    }

    suspend fun loadHistoryPage(
        createdBeforeMs: Long? = null,
        limit: Int,
    ): ClipboardHistoryPage = withContext(Dispatchers.IO) {
        val pageSize = limit.coerceAtLeast(1)
        val total = store.count()
        val slice = store.queryPageBefore(createdBeforeMs, pageSize)
        ClipboardHistoryPage(
            entries = slice,
            totalCount = total,
            hasMore = slice.size == pageSize,
        )
    }

    /** @deprecated 使用 keyset [loadHistoryPage]；offset 仅作兼容。 */
    suspend fun loadFloatHistoryPage(offset: Int, limit: Int): ClipboardHistoryPage {
        var cursor: Long? = null
        var skipped = 0
        val target = offset.coerceAtLeast(0)
        val pageSize = limit.coerceAtLeast(1)
        while (skipped < target) {
            val step = minOf(100, target - skipped)
            val page = loadHistoryPage(cursor, step)
            if (page.entries.isEmpty()) {
                return ClipboardHistoryPage(emptyList(), page.totalCount, hasMore = false)
            }
            skipped += page.entries.size
            cursor = page.nextCursor
            if (!page.hasMore) break
        }
        return loadHistoryPage(cursor, pageSize)
    }

    suspend fun searchHistory(query: String, limit: Int = ClipboardHistoryStore.SEARCH_RESULT_LIMIT): List<ClipboardEntry> =
        withContext(Dispatchers.IO) {
            store.search(query, limit)
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
        val top = store.queryLatest() ?: return false
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

    private fun findMatchingEntry(payload: ClipboardPayload): ClipboardEntry? {
        val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
        if (fingerprint.isNotEmpty()) {
            store.findLatestByFingerprint(fingerprint)?.let { return it }
        }
        val contentKey = payload.contentKey()
        store.findLatestByContentKey(contentKey)?.let { return it }
        return null
    }

    private fun promoteExistingPayloadIfNeeded(
        payload: ClipboardPayload,
        fromPassiveRefresh: Boolean = false,
    ) {
        if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
        val existing = findMatchingEntry(payload) ?: return
        scope.launch {
            writeMutex.withLock {
                val match = store.queryById(existing.id) ?: return@withLock
                if (store.queryLatest()?.id == match.id) return@withLock
                promoteExistingEntry(match)
            }
        }
    }

    private fun promoteExistingEntry(existing: ClipboardEntry) {
        val fingerprint = ClipboardContentEquivalence.fingerprint(existing)
        val contentKey = existing.contentKey()
        val preservedImageNames = existing.resolvedImageFileNames().toSet()
        val matching = collectMatchingEntries(fingerprint, contentKey)
        matching
            .filter { it.id != existing.id }
            .forEach { deleteEntryImagesExceptPreserved(it, preservedImageNames) }
        deleteMatchingRows(fingerprint, contentKey)
        val promoted = existing.copy(createdAtEpochMs = System.currentTimeMillis())
        store.insert(promoted)
        applyTrimToConfiguredMaxLocked()
        bumpRevisionLocked()
    }

    private fun removeMatchingEntries(payload: ClipboardPayload, fingerprint: String) {
        val contentKey = payload.contentKey()
        collectMatchingEntries(fingerprint, contentKey).forEach { entry ->
            ClipboardImageStore.deleteEntryImages(context, entry)
        }
        deleteMatchingRows(fingerprint, contentKey)
    }

    private fun collectMatchingEntries(fingerprint: String, contentKey: String): List<ClipboardEntry> {
        val byId = linkedMapOf<String, ClipboardEntry>()
        if (fingerprint.isNotEmpty()) {
            store.queryByFingerprint(fingerprint).forEach { byId[it.id] = it }
        }
        if (contentKey.isNotEmpty()) {
            store.queryByContentKey(contentKey).forEach { byId[it.id] = it }
        }
        return byId.values.toList()
    }

    private fun deleteMatchingRows(fingerprint: String, contentKey: String) {
        if (fingerprint.isNotEmpty()) {
            store.deleteByFingerprint(fingerprint)
        }
        if (contentKey.isNotEmpty()) {
            store.deleteByContentKey(contentKey)
        }
    }

    private fun deleteEntryImagesExceptPreserved(entry: ClipboardEntry, preserved: Set<String>) {
        entry.resolvedImageFileNames()
            .filter { it !in preserved }
            .forEach { ClipboardImageStore.delete(context, it) }
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

    private fun applyTrimToConfiguredMaxLocked() {
        val max = configuredMaxEntries()
        if (max < 0) return
        val removed = store.trimToMax(max)
        removed.forEach { entry ->
            ClipboardImageStore.deleteEntryImages(context, entry)
        }
    }

    private fun migrateLegacyJsonIfNeeded(force: Boolean = false) {
        if (!force && store.count() > 0) return
        if (!indexFile.exists()) return
        val legacy = runCatching {
            json.decodeFromString<List<ClipboardEntry>>(indexFile.readText())
        }.getOrDefault(emptyList())
        if (legacy.isEmpty()) return
        if (force) {
            store.deleteAll()
        }
        store.migrateFromJsonIndex(trimLegacyToConfiguredMax(legacy))
        indexFile.renameTo(File(storageDir, "$INDEX_FILE_NAME.migrated"))
    }


    private fun trimLegacyToConfiguredMax(entries: List<ClipboardEntry>): List<ClipboardEntry> {
        val max = configuredMaxEntries()
        if (max < 0) return entries
        if (entries.size <= max) return entries
        val kept = entries.take(max)
        entries.drop(max).forEach { entry ->
            ClipboardImageStore.deleteEntryImages(context, entry)
        }
        return kept
    }

    private fun refreshEntryCount() {
        _entryCount.value = store.count()
    }

    private fun refreshEntryCountLocked() {
        _entryCount.value = store.count()
    }

    private fun bumpRevisionLocked() {
        refreshEntryCountLocked()
        _revision.value = _revision.value + 1L
    }

    companion object {
        private const val DIR_NAME = "clipboard"
        private const val INDEX_FILE_NAME = "history.json"
        private const val REFRESH_DEBOUNCE_MS = 400L
        private const val SAME_CLIP_DEDUP_MS = 400L
        private const val SCREENSHOT_TOP_GUARD_MS = 10_000L
    }
}
