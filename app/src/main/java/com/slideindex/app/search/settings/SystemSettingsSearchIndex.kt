package com.slideindex.app.search.settings

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SystemSettingsSearchIndex {
    private const val TAG = "SystemSettingsSearch"
    private const val INDEXABLES_RAW_PATH = "settings/indexables/raw"
    private const val LEGACY_RAW_PATH = "search_indexables_raw"

    private const val COLUMN_TITLE = "title"
    private const val COLUMN_SCREEN_TITLE = "screenTitle"
    private const val COLUMN_KEYWORDS = "keywords"
    private const val COLUMN_INTENT_TARGET_PACKAGE = "intentTargetPackage"
    private const val COLUMN_INTENT_TARGET_CLASS = "intentTargetClass"
    private const val COLUMN_INTENT_ACTION = "intentAction"
    private const val COLUMN_KEY = "key"

    private val mutex = Mutex()

    @Volatile
    private var cachedEntries: List<SystemSettingsSearchEntry>? = null

    private val settingsAuthorities = listOf(
        "com.android.settings",
        "com.meizu.settings",
        "com.meizu.flyme.settings",
    )

    suspend fun ensureLoaded(context: Context): List<SystemSettingsSearchEntry> {
        cachedEntries?.let { return it }
        return mutex.withLock {
            cachedEntries ?: loadEntries(context.applicationContext).also { cachedEntries = it }
        }
    }

    suspend fun search(
        context: Context,
        query: String,
        limit: Int,
    ): List<SystemSettingsSearchEntry> {
        val entries = ensureLoaded(context)
        return SystemSettingsSearchMatcher.search(entries, query, limit)
    }

    fun invalidate() {
        cachedEntries = null
    }

    private fun loadEntries(context: Context): List<SystemSettingsSearchEntry> {
        val resolver = context.contentResolver
        val loaded = linkedSetOf<SystemSettingsSearchEntry>()
        settingsAuthorities.forEach { authority ->
            indexUrisForAuthority(authority).forEach { uri ->
                runCatching {
                    loadFromUri(resolver, uri, authority)
                }.onSuccess { entries ->
                    if (entries.isNotEmpty()) {
                        loaded += entries
                        Log.i(TAG, "loaded ${entries.size} settings index rows from $uri")
                    }
                }.onFailure { error ->
                    Log.d(TAG, "settings index unavailable at $uri: ${error.message}")
                }
            }
        }
        if (loaded.isNotEmpty()) return loaded.toList()

        val fromManifest = SystemSettingsManifestIndex.loadEntries(context)
        if (fromManifest.isNotEmpty()) {
            Log.i(TAG, "provider empty; using ${fromManifest.size} manifest settings entries")
        } else {
            Log.w(TAG, "no settings index from provider or manifest")
        }
        return fromManifest
    }

    private fun indexUrisForAuthority(authority: String): List<Uri> = listOf(
        "content://$authority/$INDEXABLES_RAW_PATH".toUri(),
        "content://$authority/$LEGACY_RAW_PATH".toUri(),
    )

    private fun loadFromUri(
        resolver: android.content.ContentResolver,
        uri: Uri,
        defaultPackage: String,
    ): List<SystemSettingsSearchEntry> {
        val projection = arrayOf(
            COLUMN_TITLE,
            COLUMN_SCREEN_TITLE,
            COLUMN_KEYWORDS,
            COLUMN_INTENT_TARGET_PACKAGE,
            COLUMN_INTENT_TARGET_CLASS,
            COLUMN_INTENT_ACTION,
            COLUMN_KEY,
        )
        val cursor = resolver.query(uri, projection, null, null, null) ?: return emptyList()
        cursor.use {
            return buildList {
                while (it.moveToNext()) {
                    parseRow(it, defaultPackage)?.let(::add)
                }
            }
        }
    }

    private fun parseRow(cursor: Cursor, defaultPackage: String): SystemSettingsSearchEntry? {
        val title = cursor.readString(COLUMN_TITLE)?.trim().orEmpty()
        if (title.isEmpty()) return null
        val packageName = cursor.readString(COLUMN_INTENT_TARGET_PACKAGE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultPackage
        return SystemSettingsSearchEntry(
            title = title,
            screenTitle = cursor.readString(COLUMN_SCREEN_TITLE),
            keywords = cursor.readString(COLUMN_KEYWORDS),
            packageName = packageName,
            className = cursor.readString(COLUMN_INTENT_TARGET_CLASS),
            action = cursor.readString(COLUMN_INTENT_ACTION),
            key = cursor.readString(COLUMN_KEY),
        )
    }

    private fun Cursor.readString(columnName: String): String? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }
}
