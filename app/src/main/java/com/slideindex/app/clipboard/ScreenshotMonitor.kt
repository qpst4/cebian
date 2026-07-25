package com.slideindex.app.clipboard

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull

/**
 * 监听 MediaStore 新增图片，将系统/FV 等应用保存的截图写入剪贴板历史。
 * 判定逻辑参考 OctoClip（app.octoclip）的 ScreenshotMonitor。
 */
class ScreenshotMonitor(
    private val context: Context,
    private val onScreenshot: (Uri, displayName: String?, mimeType: String?) -> Unit,
) {
    private val appContext = context.applicationContext
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var observer: ContentObserver? = null
    @Volatile
    private var running = false

    private val recentlyProcessed = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 128
    }
    private val pendingChecks = mutableMapOf<Uri, Runnable>()
    private val pendingRetries = mutableMapOf<Uri, Int>()

    fun start() {
        if (running) return
        ensureHandler()
        running = true
        pendingRetries.clear()
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (!running) return
                if (uri != null) {
                    scheduleProcess(uri)
                } else {
                    scheduleQuickScan()
                }
            }

            override fun onChange(selfChange: Boolean) {
                if (!running) return
                scheduleQuickScan()
            }
        }
        observer = obs
        runCatching {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                obs,
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to register screenshot observer", error)
            running = false
        }
    }

    fun stop() {
        if (!running && observer == null && handlerThread == null) return
        running = false
        observer?.let {
            runCatching { appContext.contentResolver.unregisterContentObserver(it) }
        }
        observer = null
        synchronized(pendingChecks) {
            pendingChecks.values.forEach { handler?.removeCallbacks(it) }
            pendingChecks.clear()
        }
        pendingRetries.clear()
        handler?.removeCallbacksAndMessages(null)
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    private fun ensureHandler() {
        if (handler != null) return
        HandlerThread("ScreenshotMonitor").apply {
            start()
            handlerThread = this
            handler = Handler(looper)
        }
    }

    private fun scheduleProcess(uri: Uri, debounceMs: Long = PROCESS_DEBOUNCE_MS) {
        val workHandler = handler ?: return
        synchronized(pendingChecks) {
            pendingChecks.remove(uri)?.let { workHandler.removeCallbacks(it) }
            val runnable = Runnable {
                synchronized(pendingChecks) { pendingChecks.remove(uri) }
                if (running) processUri(uri)
            }
            pendingChecks[uri] = runnable
            workHandler.postDelayed(runnable, debounceMs)
        }
    }

    private fun scheduleQuickScan() {
        handler?.postDelayed({
            if (running) quickScanLatest()
        }, PROCESS_DEBOUNCE_MS)
    }

    private fun processUri(uri: Uri) {
        val row = queryMedia(uri) ?: run {
            quickScanLatest()
            return
        }
        if (row.isTrashed) return
        if (row.isPending) {
            val retries = pendingRetries.getOrDefault(uri, 0)
            if (retries >= MAX_PENDING_RETRIES) {
                pendingRetries.remove(uri)
                return
            }
            pendingRetries[uri] = retries + 1
            scheduleProcess(uri, debounceMs = PENDING_RETRY_MS)
            return
        }
        pendingRetries.remove(uri)
        if (!shouldIngest(row)) return
        notifyScreenshot(row)
    }

    private fun quickScanLatest() {
        val resolver = appContext.contentResolver
        val projection = projection()
        val bundle = Bundle().apply {
            putString(
                ContentResolverQueryArgs.SQL_SELECTION,
                "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?",
            )
            putStringArray(
                ContentResolverQueryArgs.SQL_SELECTION_ARGS,
                arrayOf((System.currentTimeMillis() / 1000 - 1).toString()),
            )
            putStringArray(
                ContentResolverQueryArgs.SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
            )
            putInt(ContentResolverQueryArgs.SORT_DIRECTION, 1)
            putInt(ContentResolverQueryArgs.QUERY_ARG_LIMIT, 1)
        }
        val cursor = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    bundle,
                    null,
                )
            } else {
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?",
                    arrayOf((System.currentTimeMillis() / 1000 - 120).toString()),
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC LIMIT 1",
                )
            }
        }.getOrNull() ?: return

        cursor.use {
            if (!it.moveToFirst()) return
            val id = it.getLongOrNull(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)) ?: return
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val row = readRow(uri, it) ?: return
            if (row.isTrashed || row.isPending) return
            if (!shouldIngest(row)) return
            notifyScreenshot(row)
        }
    }

    private fun queryMedia(uri: Uri): MediaRow? {
        val cursor = appContext.contentResolver.query(uri, projection(), null, null, null)
            ?: return null
        return cursor.use {
            if (!it.moveToFirst()) null else readRow(uri, it)
        }
    }

    private fun readRow(uri: Uri, cursor: android.database.Cursor): MediaRow? {
        fun optionalString(column: String): String? {
            val index = cursor.getColumnIndex(column)
            if (index < 0) return null
            return cursor.getStringOrNull(index)
        }
        fun optionalLong(column: String): Long? {
            val index = cursor.getColumnIndex(column)
            if (index < 0) return null
            return cursor.getLongOrNull(index)
        }
        fun optionalInt(column: String): Int? {
            val index = cursor.getColumnIndex(column)
            if (index < 0) return null
            return cursor.getIntOrNull(index)
        }
        return MediaRow(
            uri = uri,
            displayName = optionalString(MediaStore.MediaColumns.DISPLAY_NAME),
            mimeType = optionalString(MediaStore.MediaColumns.MIME_TYPE),
            relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                optionalString(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                null
            },
            dataPath = optionalString(MediaStore.MediaColumns.DATA),
            size = optionalLong(MediaStore.MediaColumns.SIZE) ?: 0L,
            dateTaken = optionalLong(MediaStore.MediaColumns.DATE_TAKEN),
            dateAdded = optionalLong(MediaStore.MediaColumns.DATE_ADDED),
            dateModified = optionalLong(MediaStore.MediaColumns.DATE_MODIFIED),
            isPending = optionalInt(MediaStore.MediaColumns.IS_PENDING) == 1,
            isTrashed = optionalInt(MediaStore.MediaColumns.IS_TRASHED) == 1,
        )
    }

    private fun shouldIngest(row: MediaRow): Boolean {
        if (row.size <= 0L) return false
        if (!isScreenshotCandidate(row.displayName, row.mimeType, row.relativePath, row.dataPath)) {
            return false
        }
        if (!isRecentEnough(row.dateTaken, row.dateAdded, row.dateModified)) {
            return false
        }
        val key = row.displayName?.takeIf { it.isNotBlank() } ?: row.uri.toString()
        synchronized(recentlyProcessed) {
            val last = recentlyProcessed[key]
            val now = System.currentTimeMillis()
            if (last != null && now - last < DEDUP_MS) return false
            recentlyProcessed[key] = now
        }
        return true
    }

    private fun notifyScreenshot(row: MediaRow) {
        runCatching {
            onScreenshot(row.uri, row.displayName, row.mimeType)
        }.onFailure { error ->
            Log.w(TAG, "Screenshot callback failed for ${row.uri}", error)
        }
    }

    private data class MediaRow(
        val uri: Uri,
        val displayName: String?,
        val mimeType: String?,
        val relativePath: String?,
        val dataPath: String?,
        val size: Long,
        val dateTaken: Long?,
        val dateAdded: Long?,
        val dateModified: Long?,
        val isPending: Boolean,
        val isTrashed: Boolean,
    )

    private object ContentResolverQueryArgs {
        const val SQL_SELECTION = "android:query-arg-sql-selection"
        const val SQL_SELECTION_ARGS = "android:query-arg-sql-selection-args"
        const val SORT_COLUMNS = "android:query-arg-sort-columns"
        const val SORT_DIRECTION = "android:query-arg-sort-direction"
        const val QUERY_ARG_LIMIT = "android:query-arg-limit"
    }

    companion object {
        private const val TAG = "ScreenshotMonitor"
        private const val FRESHNESS_SEC = 120L
        private const val DEDUP_MS = 300L
        private const val PROCESS_DEBOUNCE_MS = 50L
        private const val PENDING_RETRY_MS = 100L
        private const val MAX_PENDING_RETRIES = 20

        private val NAME_KEYWORDS = setOf(
            "screenshot",
            "截图",
            "截屏",
            "screen_shot",
            "screencap",
            "capture",
            "screen-shot",
            "截圖",
        )

        fun isScreenshotCandidate(
            displayName: String?,
            mimeType: String?,
            relativePath: String?,
            dataPath: String?,
        ): Boolean {
            val mime = mimeType?.lowercase()
            if (mime != null && !mime.startsWith("image/")) return false

            val rel = relativePath?.lowercase().orEmpty()
            val data = dataPath?.lowercase().orEmpty()
            if (rel.contains("screenshots") || data.contains("/screenshots/")) {
                return true
            }
            val name = (displayName ?: dataPath ?: "").lowercase()
            return NAME_KEYWORDS.any { keyword -> name.contains(keyword) }
        }

        fun isRecentEnough(dateTaken: Long?, dateAdded: Long?, dateModified: Long?): Boolean {
            val nowSec = System.currentTimeMillis() / 1000
            val timestamps = buildList {
                dateTaken?.takeIf { it > 0 }?.let { value ->
                    add(if (value > 1_000_000_000_000L) value / 1000 else value)
                }
                dateAdded?.takeIf { it > 0 }?.let { add(it) }
                dateModified?.takeIf { it > 0 }?.let { add(it) }
            }
            val newest = timestamps.maxOrNull() ?: return false
            return nowSec - newest <= FRESHNESS_SEC
        }

        private fun projection(): Array<String> {
            val columns = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                columns += MediaStore.MediaColumns.RELATIVE_PATH
                columns += MediaStore.MediaColumns.IS_PENDING
                columns += MediaStore.MediaColumns.IS_TRASHED
            } else {
                columns += MediaStore.MediaColumns.DATA
            }
            return columns.toTypedArray()
        }
    }
}
