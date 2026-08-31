package com.slideindex.app.translate

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class TranslateDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
)

@Singleton
class TranslateDownloadProgressTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun queryProgress(mlKitLanguage: String): TranslateDownloadProgress? = try {
        val manager = context.getSystemService(DownloadManager::class.java)
        if (manager == null) {
            null
        } else {
            val query = DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_PENDING or
                    DownloadManager.STATUS_RUNNING or
                    DownloadManager.STATUS_PAUSED,
            )
            manager.query(query)?.use { cursor ->
                var found: TranslateDownloadProgress? = null
                while (cursor.moveToNext()) {
                    val progress = readProgress(cursor, mlKitLanguage)
                    if (progress != null) {
                        found = progress
                        break
                    }
                }
                found
            }
        }
    } catch (e: Throwable) {
        null
    }

    private fun readProgress(cursor: Cursor, mlKitLanguage: String): TranslateDownloadProgress? = try {
        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
        val uri = if (uriIndex >= 0) cursor.getString(uriIndex).orEmpty() else ""
        val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val title = if (titleIndex >= 0) cursor.getString(titleIndex).orEmpty() else ""
        if (!matchesLanguage(uri, title, mlKitLanguage)) {
            null
        } else {
            val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val downloaded = if (downloadedIndex >= 0) cursor.getLong(downloadedIndex) else 0L
            val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val total = if (totalIndex >= 0) {
                cursor.getLong(totalIndex).takeIf { value -> value > 0L }
            } else {
                null
            }
            TranslateDownloadProgress(
                bytesDownloaded = downloaded.coerceAtLeast(0L),
                totalBytes = total,
            )
        }
    } catch (e: Throwable) {
        null
    }

    private fun matchesLanguage(uri: String, title: String, mlKitLanguage: String): Boolean {
        val needle = mlKitLanguage.lowercase()
        val haystacks = listOf(uri.lowercase(), title.lowercase())
        return haystacks.any { haystack ->
            haystack.contains("/$needle") ||
                haystack.contains("_$needle.") ||
                haystack.contains("${needle}_") ||
                haystack == "$needle.zip"
        }
    }
}
