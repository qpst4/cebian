package com.slideindex.app.notification

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.slideindex.app.service.LaunchTrampolineActivity
import com.slideindex.app.service.MediaNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class NotificationHistoryRepository(
    context: Context,
    private val filterPreferences: NotificationFilterPreferences,
) {
    private val appContext = context.applicationContext
    private val historyFile = File(appContext.filesDir, HISTORY_FILE_NAME)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    @Volatile
    private var isLoaded = false

    private val _items = MutableStateFlow<List<NotificationHistoryItem>>(emptyList())
    val items: StateFlow<List<NotificationHistoryItem>> = _items.asStateFlow()

    /** Full payloads (base64 captures) kept in memory; [_items] exposes UI-safe copies only. */
    private var storageItems: List<NotificationHistoryItem> = emptyList()

    init {
        scope.launch { ensureLoaded() }
    }

    private suspend fun ensureLoaded() {
        if (isLoaded) return
        mutex.withLock {
            if (isLoaded) return
            val maxCount = filterPreferences.readSnapshot().notificationHistoryMaxCount
            val loaded = readFromDisk()
            val trimmed = loaded.take(maxCount)
            if (trimmed.size != loaded.size) {
                writeToDisk(trimmed)
            }
            publishItems(trimmed)
            isLoaded = true
        }
    }

    private fun publishItems(full: List<NotificationHistoryItem>) {
        storageItems = full
        val ui = full.forUiList()
        if (ui != _items.value) {
            _items.value = ui
        }
    }

    private fun NotificationHistoryItem.forUi(): NotificationHistoryItem {
        if (intentParcelBase64 == null &&
            intentExtrasBase64 == null &&
            pendingIntentBase64 == null &&
            extrasBase64 == null
        ) {
            return this
        }
        return copy(
            intentParcelBase64 = null,
            intentExtrasBase64 = null,
            pendingIntentBase64 = null,
            extrasBase64 = null,
        )
    }

    private fun List<NotificationHistoryItem>.forUiList(): List<NotificationHistoryItem> =
        map { it.forUi() }

    private suspend fun resolveFullItem(item: NotificationHistoryItem): NotificationHistoryItem {
        ensureLoaded()
        return mutex.withLock {
            storageItems.find { it.id == item.id }
                ?: item.notificationKey?.let { key ->
                    storageItems.find { it.notificationKey == key }
                }
                ?: item
        }
    }

    private fun schedulePersist(items: List<NotificationHistoryItem>) {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            mutex.withLock {
                writeToDisk(items)
            }
        }
    }

    private suspend fun persistNow(items: List<NotificationHistoryItem>) {
        persistJob?.cancel()
        mutex.withLock {
            writeToDisk(items)
        }
    }

    fun record(item: NotificationHistoryItem) {
        scope.launch {
            ensureLoaded()
            mutex.withLock {
                val current = storageItems
                val existing = item.notificationKey?.let { key ->
                    current.firstOrNull { it.notificationKey == key }
                }
                val merged = mergeCapture(existing, item)
                val withoutDuplicate = if (merged.notificationKey.isNullOrBlank()) {
                    current
                } else {
                    current.filterNot { it.notificationKey == merged.notificationKey }
                }
                val next = listOf(merged) + withoutDuplicate
                val maxCount = filterPreferences.readSnapshot().notificationHistoryMaxCount
                val trimmed = next.take(maxCount)
                publishItems(trimmed)
                schedulePersist(trimmed)
            }
        }
    }

    fun updateCapture(notificationKey: String, captured: NotificationHistoryIntentCapture.CapturedIntent) {
        scope.launch {
            ensureLoaded()
            mutex.withLock {
                val current = storageItems
                val index = current.indexOfFirst { it.notificationKey == notificationKey }
                if (index < 0) return@withLock
                val existing = current[index]
                val updated = existing.copy(
                    intentUri = captured.intentUri ?: existing.intentUri,
                    intentParcelBase64 = captured.intentParcelBase64 ?: existing.intentParcelBase64,
                    intentExtrasBase64 = captured.intentExtrasBase64 ?: existing.intentExtrasBase64,
                    pendingIntentBase64 = captured.pendingIntentBase64 ?: existing.pendingIntentBase64,
                    extrasBase64 = captured.extrasBase64 ?: existing.extrasBase64,
                )
                if (updated == existing) return@withLock
                val next = current.toMutableList()
                next[index] = updated
                storageItems = next
                schedulePersist(next)
            }
        }
    }

    fun delete(id: String) {
        scope.launch {
            ensureLoaded()
            mutex.withLock {
                val next = storageItems.filterNot { it.id == id }
                publishItems(next)
                persistNow(next)
            }
        }
    }

    fun clearAll() {
        scope.launch {
            ensureLoaded()
            mutex.withLock {
                publishItems(emptyList())
                persistNow(emptyList())
            }
        }
    }

    suspend fun applyMaxCountLimit(maxCount: Int) {
        ensureLoaded()
        mutex.withLock {
            val current = storageItems
            val trimmed = current.take(maxCount)
            if (trimmed.size != current.size) {
                publishItems(trimmed)
                persistNow(trimmed)
            }
        }
    }

    fun getActiveNotificationKeys(): Set<String> {
        val listener = MediaNotificationListener.instance ?: return emptySet()
        return runCatching {
            listener.activeNotifications?.map { it.key }.orEmpty().toSet()
        }.getOrDefault(emptySet())
    }

    fun getActiveNotifications(historyItems: List<NotificationHistoryItem>): List<ActiveNotificationEntry> {
        val listener = MediaNotificationListener.instance ?: return emptyList()
        val selfPackage = appContext.packageName
        val active = runCatching { listener.activeNotifications?.toList() }.getOrNull().orEmpty()
        val historyByKey = historyItems.mapNotNull { item ->
            item.notificationKey?.let { key -> key to item }
        }.toMap()
        return active
            .asSequence()
            .filter { it.packageName != selfPackage }
            .mapNotNull { sbn -> toActiveNotificationEntry(sbn, historyByKey[sbn.key]) }
            .sortedByDescending { it.postedAtMs }
            .toList()
    }

    suspend fun replayActive(entry: ActiveNotificationEntry): NotificationReplayResult {
        val item = entry.historyItem ?: NotificationHistoryItem(
            packageName = entry.packageName,
            title = entry.title,
            text = entry.text,
            postedAtMs = entry.postedAtMs,
            intentUri = null,
            notificationKey = entry.key,
        )
        return replay(item)
    }

    fun hideNotification(item: NotificationHistoryItem): Boolean {
        val key = item.notificationKey ?: return false
        return NotificationHider.hideNotification(key)
    }

    fun restoreAllSnoozed(): Int {
        val restoredKeys = NotificationHider.restoreAllSnoozed(appContext.packageName)
        if (restoredKeys.isEmpty()) return 0
        scope.launch {
            ensureLoaded()
            mutex.withLock {
                val current = storageItems
                val restoredKeySet = restoredKeys.toSet()
                val next = current.map { item ->
                    if (item.notificationKey in restoredKeySet && item.hidden) {
                        item.copy(hidden = false)
                    } else {
                        item
                    }
                }
                if (next != current) {
                    publishItems(next)
                    schedulePersist(next)
                }
            }
        }
        return restoredKeys.size
    }

    fun markHidden(id: String, hidden: Boolean) {
        scope.launch {
            ensureLoaded()
            mutex.withLock {
                val current = storageItems
                val index = current.indexOfFirst { it.id == id }
                if (index < 0) return@withLock
                val next = current.toMutableList()
                next[index] = next[index].copy(hidden = hidden)
                publishItems(next)
                schedulePersist(next)
            }
        }
    }

    suspend fun restoreToShade(
        item: NotificationHistoryItem,
        filterRepository: NotificationFilterRepository,
    ): NotificationRestoreResult {
        val ruleRemoved = filterRepository.removeRuleForItem(item)
        val unsnoozed = item.notificationKey?.let(NotificationHider::unsnoozeNotification) == true
        ensureLoaded()
        mutex.withLock {
            val current = storageItems
            val index = current.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                val next = current.toMutableList()
                next[index] = next[index].copy(hidden = false)
                publishItems(next)
                schedulePersist(next)
            }
        }
        return when {
            unsnoozed -> NotificationRestoreResult.RESTORED_TO_SHADE
            ruleRemoved -> NotificationRestoreResult.RULE_REMOVED_ONLY
            else -> NotificationRestoreResult.UNSNOOZE_FAILED
        }
    }

    suspend fun replay(item: NotificationHistoryItem): NotificationReplayResult = withContext(Dispatchers.Main) {
        val fullItem = resolveFullItem(item)
        if (replayFromActiveNotification(fullItem)) {
            Log.i(TAG, "Replayed via active notification PendingIntent: ${item.packageName}")
            return@withContext NotificationReplayResult.Success
        }

        if (replayFromCachedSbn(fullItem)) {
            Log.i(TAG, "Replayed via in-memory SBN cache: ${fullItem.packageName}")
            return@withContext NotificationReplayResult.Success
        }

        if (replayFromStoredIntent(fullItem)) {
            Log.i(TAG, "Replayed via stored intent: ${fullItem.packageName}")
            return@withContext NotificationReplayResult.Success
        }

        if (replayFromNotificationExtras(fullItem)) {
            Log.i(TAG, "Replayed via notification extras: ${fullItem.packageName}")
            return@withContext NotificationReplayResult.Success
        }

        if (replayFromStoredPendingIntentViaTrampoline(fullItem)) {
            Log.i(TAG, "Replayed via stored PendingIntent trampoline: ${fullItem.packageName}")
            return@withContext NotificationReplayResult.Success
        }

        if (replayFromStoredPendingIntent(fullItem)) {
            Log.i(TAG, "Replayed via stored PendingIntent.send(): ${fullItem.packageName}")
            return@withContext NotificationReplayResult.Success
        }

        if (replayFromRecreatedPendingIntent(fullItem)) {
            Log.i(TAG, "Replayed via recreated PendingIntent: ${fullItem.packageName}")
            return@withContext NotificationReplayResult.Success
        }

        val reason = buildReplayFailureReason(fullItem)
        Log.e(
            TAG,
            "All replay paths failed for ${fullItem.packageName}; $reason " +
                "pi=${!fullItem.pendingIntentBase64.isNullOrBlank()} " +
                "uri=${!fullItem.intentUri.isNullOrBlank()} " +
                "parcel=${!fullItem.intentParcelBase64.isNullOrBlank()} " +
                "notificationExtras=${!fullItem.extrasBase64.isNullOrBlank()} " +
                "cachedSbn=${NotificationSbnCache.find(fullItem.notificationKey.orEmpty(), fullItem.postedAtMs) != null}",
        )
        buildReplayFailure(fullItem, reason)
    }

    fun openTargetApp(packageName: String): Boolean = NotificationAppLauncher.open(appContext, packageName)

    private fun toActiveNotificationEntry(
        sbn: android.service.notification.StatusBarNotification,
        historyItem: NotificationHistoryItem?,
    ): ActiveNotificationEntry? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: ""
        if (title.isBlank() && text.isBlank()) return null
        return ActiveNotificationEntry(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            postedAtMs = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
            historyItem = historyItem,
        )
    }

    private fun mergeCapture(
        existing: NotificationHistoryItem?,
        incoming: NotificationHistoryItem,
    ): NotificationHistoryItem {
        if (existing == null) return incoming
        return incoming.copy(
            intentUri = incoming.intentUri ?: existing.intentUri,
            intentParcelBase64 = incoming.intentParcelBase64 ?: existing.intentParcelBase64,
            intentExtrasBase64 = incoming.intentExtrasBase64 ?: existing.intentExtrasBase64,
            pendingIntentBase64 = incoming.pendingIntentBase64 ?: existing.pendingIntentBase64,
            extrasBase64 = incoming.extrasBase64 ?: existing.extrasBase64,
            extractedCode = incoming.extractedCode ?: existing.extractedCode,
            extractionAttempted = incoming.extractionAttempted || existing.extractionAttempted,
            hidden = incoming.hidden || existing.hidden,
        )
    }

    private fun replayFromStoredIntent(item: NotificationHistoryItem): Boolean {
        val intent = parseStoredIntent(item) ?: run {
            Log.w(TAG, "No stored intent for ${item.packageName}")
            return false
        }
        return launchStoredIntent(intent, item.packageName, item.extrasBase64)
    }

    private fun replayFromNotificationExtras(item: NotificationHistoryItem): Boolean {
        val intent = NotificationHistoryIntentCapture.deserializeIntentFromNotificationExtras(
            extrasBase64 = item.extrasBase64,
            context = appContext,
            packageName = item.packageName,
        ) ?: run {
            Log.w(TAG, "No launch intent in notification extras for ${item.packageName}")
            return false
        }
        return launchStoredIntent(intent, item.packageName, item.extrasBase64)
    }

    private fun replayFromRecreatedPendingIntent(item: NotificationHistoryItem): Boolean {
        val intent = parseStoredIntent(item) ?: return false
        val pendingIntent = createActivityPendingIntent(intent) ?: return false
        return sendPendingIntent(pendingIntent)
    }

    private fun replayFromStoredPendingIntent(item: NotificationHistoryItem): Boolean {
        val pendingIntent = NotificationHistoryIntentCapture.deserializePendingIntent(item.pendingIntentBase64)
            ?: run {
                Log.w(TAG, "No stored PendingIntent for ${item.packageName}")
                return false
            }
        return sendPendingIntent(pendingIntent)
    }

    private fun replayFromStoredPendingIntentViaTrampoline(item: NotificationHistoryItem): Boolean {
        val encoded = item.pendingIntentBase64 ?: return false
        return sendPendingIntentViaTrampoline(encoded, item)
    }

    private fun replayFromActiveNotification(item: NotificationHistoryItem): Boolean {
        val key = item.notificationKey ?: return false
        val listener = MediaNotificationListener.instance ?: return false
        val sbn = findStatusBarNotification(listener, key) ?: return false
        return replayPendingIntentsFromSbn(sbn)
    }

    private fun replayFromCachedSbn(item: NotificationHistoryItem): Boolean {
        val key = item.notificationKey ?: return false
        val cached = NotificationSbnCache.find(key, item.postedAtMs) ?: return false
        return replayPendingIntentsFromSbn(cached)
    }

    private fun replayPendingIntentsFromSbn(sbn: android.service.notification.StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        if (sendPendingIntent(notification.contentIntent)) return true
        if (sendPendingIntent(notification.fullScreenIntent)) return true
        if (sendPendingIntent(notification.publicVersion?.contentIntent)) return true
        notification.actions?.forEach { action ->
            if (sendPendingIntent(action.actionIntent)) return true
        }
        return false
    }

    private fun findStatusBarNotification(
        listener: MediaNotificationListener,
        key: String,
    ): android.service.notification.StatusBarNotification? {
        runCatching {
            listener.activeNotifications?.firstOrNull { it.key == key }
        }.getOrNull()?.let { return it }
        return runCatching {
            listener.snoozedNotifications?.firstOrNull { it.key == key }
        }.getOrNull()
    }

    private fun sendPendingIntent(pendingIntent: PendingIntent?): Boolean {
        if (pendingIntent == null) return false
        val options = createPendingIntentSendOptions()
        val sentWithContext = runCatching {
            pendingIntent.send(appContext, 0, null, null, null, null, options)
        }.onFailure { error ->
            Log.w(TAG, "PendingIntent.send(context) failed", error)
        }.isSuccess
        if (sentWithContext) return true
        return runCatching {
            pendingIntent.send()
        }.onFailure { error ->
            Log.w(TAG, "PendingIntent.send() failed", error)
        }.isSuccess
    }

    private fun sendPendingIntentViaTrampoline(
        pendingIntentBase64: String,
        item: NotificationHistoryItem,
    ): Boolean {
        val fallbackIntent = parseStoredIntent(item)
            ?: NotificationHistoryIntentCapture.deserializeIntentFromNotificationExtras(
                extrasBase64 = item.extrasBase64,
                context = appContext,
                packageName = item.packageName,
            )
        return runCatching {
            appContext.startActivity(
                LaunchTrampolineActivity.createPendingIntentIntent(
                    context = appContext,
                    pendingIntentBase64 = pendingIntentBase64,
                    fallbackIntent = fallbackIntent,
                ),
            )
        }.onFailure { error ->
            Log.w(TAG, "PendingIntent trampoline launch failed for ${item.packageName}", error)
        }.isSuccess
    }

    private fun createPendingIntentSendOptions(): android.os.Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        val mode = when {
            Build.VERSION.SDK_INT >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            else -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        options.pendingIntentBackgroundActivityStartMode = mode
        return options.toBundle()
    }

    private fun parseStoredIntent(item: NotificationHistoryItem): Intent? {
        return NotificationHistoryIntentCapture.deserializeIntent(
            intentUri = item.intentUri,
            intentParcelBase64 = item.intentParcelBase64,
            intentExtrasBase64 = item.intentExtrasBase64,
        )?.let { intent ->
            NotificationHistoryIntentCapture.prepareIntentForReplay(intent, item.packageName)
        }
    }

    private fun launchStoredIntent(intent: Intent, packageName: String, extrasBase64: String? = null): Boolean {
        val enriched = NotificationHistoryIntentCapture.enrichIntentForReplay(
            intent = intent,
            extrasBase64 = extrasBase64,
            context = appContext,
            packageName = packageName,
        )
        val trampoline = LaunchTrampolineActivity.createIntent(appContext, enriched)
        if (runCatching { appContext.startActivity(trampoline) }.isSuccess) return true
        if (runCatching { appContext.startActivity(enriched) }.isSuccess) return true
        Log.e(TAG, "Stored intent launch failed for $packageName action=${enriched.action}")
        return false
    }

    private fun createActivityPendingIntent(intent: Intent): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutablePendingIntentFlag()
        return runCatching {
            PendingIntent.getActivity(appContext, intent.hashCode(), intent, flags)
        }.onFailure { error ->
            Log.w(TAG, "Failed to recreate PendingIntent from stored intent", error)
        }.getOrNull()
    }

    private fun immutablePendingIntentFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun buildReplayFailureReason(item: NotificationHistoryItem): String {
        return when {
            item.pendingIntentBase64.isNullOrBlank() &&
                item.intentUri.isNullOrBlank() &&
                item.intentParcelBase64.isNullOrBlank() &&
                item.extrasBase64.isNullOrBlank() &&
                item.notificationKey?.let { NotificationSbnCache.find(it, item.postedAtMs) } == null ->
                "未捕获到跳转信息"
            else -> "链接已被系统回收"
        }
    }

    private fun buildReplayFailure(item: NotificationHistoryItem, reason: String): NotificationReplayResult.Failure {
        val canOpenApp = NotificationAppLauncher.canOpen(appContext, item.packageName)
        return NotificationReplayResult.Failure(
            reason = reason,
            offerOpenApp = canOpenApp,
            packageName = item.packageName,
        )
    }

    private suspend fun readFromDisk(): List<NotificationHistoryItem> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        runCatching {
            NotificationHistoryCodec.decode(historyFile.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun writeToDisk(items: List<NotificationHistoryItem>) = withContext(Dispatchers.IO) {
        historyFile.writeText(NotificationHistoryCodec.encode(items))
    }

    companion object {
        private const val TAG = "NotifHistoryReplay"
        private const val HISTORY_FILE_NAME = "notification_history.json"
        private const val PERSIST_DEBOUNCE_MS = 1_500L
    }
}
