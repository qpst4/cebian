package com.slideindex.app.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite-backed clipboard history: keyset pagination, optional FTS5 search, indexed dedup.
 */
internal class ClipboardHistoryStore(
    context: Context,
    private val json: Json,
) {
    private val openHelper = StoreOpenHelper(context.applicationContext, json)

    private val ftsEnabled: Boolean
        get() = openHelper.ftsEnabled

    fun count(): Int = readableDatabase.use { db ->
        db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            if (!cursor.moveToFirst()) 0 else cursor.getInt(0)
        }
    }

    fun queryLatest(): ClipboardEntry? = queryPageBefore(createdBeforeMs = null, limit = 1).firstOrNull()

    fun queryPageBefore(createdBeforeMs: Long?, limit: Int): List<ClipboardEntry> {
        val pageSize = limit.coerceAtLeast(1)
        val selection = if (createdBeforeMs == null) null else "$COL_CREATED < ?"
        val selectionArgs = if (createdBeforeMs == null) null else arrayOf(createdBeforeMs.toString())
        return readableDatabase.use { db ->
            db.query(
                TABLE,
                arrayOf(COL_JSON),
                selection,
                selectionArgs,
                null,
                null,
                "$COL_CREATED DESC",
                pageSize.toString(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        decodeEntry(cursor.getString(0))?.let(::add)
                    }
                }
            }
        }
    }

    fun queryPage(offset: Int, limit: Int): List<ClipboardEntry> =
        queryPageBefore(createdBeforeMs = null, limit = limit + offset.coerceAtLeast(0))
            .drop(offset.coerceAtLeast(0))

    fun queryById(id: String): ClipboardEntry? = readableDatabase.use { db ->
        db.query(
            TABLE,
            arrayOf(COL_JSON),
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else decodeEntry(cursor.getString(0))
        }
    }

    fun queryByIds(ids: List<String>): List<ClipboardEntry> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val byId = readableDatabase.use { db ->
            db.query(
                TABLE,
                arrayOf(COL_ID, COL_JSON),
                "$COL_ID IN ($placeholders)",
                ids.toTypedArray(),
                null,
                null,
                null,
                null,
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        decodeEntry(cursor.getString(1))?.let { put(id, it) }
                    }
                }
            }
        }
        return ids.mapNotNull { byId[it] }
    }

    fun findLatestByFingerprint(fingerprint: String): ClipboardEntry? {
        if (fingerprint.isEmpty()) return null
        return queryWhere("$COL_FINGERPRINT = ?", arrayOf(fingerprint), 1).firstOrNull()
    }

    fun findLatestByContentKey(contentKey: String): ClipboardEntry? {
        if (contentKey.isEmpty()) return null
        return queryWhere("$COL_CONTENT_KEY = ?", arrayOf(contentKey), 1).firstOrNull()
    }

    fun queryByFingerprint(fingerprint: String): List<ClipboardEntry> {
        if (fingerprint.isEmpty()) return emptyList()
        return queryWhere("$COL_FINGERPRINT = ?", arrayOf(fingerprint))
    }

    fun queryByContentKey(contentKey: String): List<ClipboardEntry> {
        if (contentKey.isEmpty()) return emptyList()
        return queryWhere("$COL_CONTENT_KEY = ?", arrayOf(contentKey))
    }

    fun search(query: String, limit: Int = SEARCH_RESULT_LIMIT): List<ClipboardEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val safeLimit = limit.coerceAtLeast(1)
        if (ftsEnabled) {
            val ftsResults = runCatching { searchFts(trimmed, safeLimit) }.getOrDefault(emptyList())
            if (ftsResults.isNotEmpty()) return ftsResults
        }
        return searchLike(trimmed, safeLimit)
    }

    fun insert(entry: ClipboardEntry) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.insertWithOnConflict(TABLE, null, entryToValues(entry), SQLiteDatabase.CONFLICT_REPLACE)
                if (ftsEnabled) syncFts(db, entry)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun delete(id: String) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                if (ftsEnabled) deleteFtsByEntryId(db, id)
                db.delete(TABLE, "$COL_ID = ?", arrayOf(id))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun deleteByFingerprint(fingerprint: String) {
        if (fingerprint.isEmpty()) return
        deleteWhere("$COL_FINGERPRINT = ?", arrayOf(fingerprint))
    }

    fun deleteByContentKey(contentKey: String) {
        if (contentKey.isEmpty()) return
        deleteWhere("$COL_CONTENT_KEY = ?", arrayOf(contentKey))
    }

    fun deleteAll() {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                if (ftsEnabled) db.delete(FTS_TABLE, null, null)
                db.delete(TABLE, null, null)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun trimToMax(max: Int): List<ClipboardEntry> {
        if (max < 0) return emptyList()
        if (count() <= max) return emptyList()
        val toRemove = readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT $COL_JSON FROM $TABLE
                WHERE $COL_ID IN (
                    SELECT $COL_ID FROM $TABLE
                    ORDER BY $COL_CREATED DESC
                    LIMIT -1 OFFSET ?
                )
                """.trimIndent(),
                arrayOf(max.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        decodeEntry(cursor.getString(0))?.let(::add)
                    }
                }
            }
        }
        if (toRemove.isEmpty()) return emptyList()
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                if (ftsEnabled) toRemove.forEach { deleteFtsByEntryId(db, it.id) }
                val ids = toRemove.map { it.id }.toTypedArray()
                val placeholders = ids.joinToString(",") { "?" }
                db.delete(TABLE, "$COL_ID IN ($placeholders)", ids)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return toRemove
    }

    fun migrateFromJsonIndex(jsonEntries: List<ClipboardEntry>) {
        if (jsonEntries.isEmpty()) return
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                jsonEntries.forEach { entry ->
                    db.insertWithOnConflict(TABLE, null, entryToValues(entry), SQLiteDatabase.CONFLICT_REPLACE)
                    if (ftsEnabled) syncFts(db, entry)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun searchFts(query: String, limit: Int): List<ClipboardEntry> {
        val match = buildFtsMatchQuery(query) ?: return emptyList()
        val ids = readableDatabase.use { db ->
            db.rawQuery(
                "SELECT $FTS_COL_ENTRY_ID FROM $FTS_TABLE WHERE $FTS_COL_SEARCH_TEXT MATCH ? LIMIT ?",
                arrayOf(match, limit.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
        }
        return queryByIds(ids)
    }

    private fun searchLike(query: String, limit: Int): List<ClipboardEntry> {
        val pattern = "%${query.lowercase()}%"
        return readableDatabase.use { db ->
            db.query(
                TABLE,
                arrayOf(COL_JSON),
                "$COL_SEARCH LIKE ?",
                arrayOf(pattern),
                null,
                null,
                "$COL_CREATED DESC",
                limit.toString(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        decodeEntry(cursor.getString(0))?.let(::add)
                    }
                }
            }
        }
    }

    private fun deleteWhere(where: String, args: Array<String>) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                if (ftsEnabled) deleteFtsForWhere(db, where, args)
                db.delete(TABLE, where, args)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun deleteFtsForWhere(db: SQLiteDatabase, where: String, args: Array<String>) {
        db.query(TABLE, arrayOf(COL_ID), where, args, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                deleteFtsByEntryId(db, cursor.getString(0))
            }
        }
    }

    private fun deleteFtsByEntryId(db: SQLiteDatabase, entryId: String) {
        db.delete(FTS_TABLE, "$FTS_COL_ENTRY_ID = ?", arrayOf(entryId))
    }

    private fun syncFts(db: SQLiteDatabase, entry: ClipboardEntry) {
        deleteFtsByEntryId(db, entry.id)
        val values = ContentValues().apply {
            put(FTS_COL_ENTRY_ID, entry.id)
            put(FTS_COL_SEARCH_TEXT, searchBlob(entry))
        }
        db.insert(FTS_TABLE, null, values)
    }

    private fun buildFtsMatchQuery(raw: String): String? {
        val tokens = raw.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { token ->
            val escaped = token.replace("\"", "\"\"")
            "\"$escaped\"*"
        }
    }

    private fun queryWhere(where: String, args: Array<String>, limit: Int? = null): List<ClipboardEntry> =
        readableDatabase.use { db ->
            db.query(
                TABLE,
                arrayOf(COL_JSON),
                where,
                args,
                null,
                null,
                "$COL_CREATED DESC",
                limit?.toString(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        decodeEntry(cursor.getString(0))?.let(::add)
                    }
                }
            }
        }

    private fun entryToValues(entry: ClipboardEntry): ContentValues = ContentValues().apply {
        put(COL_ID, entry.id)
        put(COL_CREATED, entry.createdAtEpochMs)
        put(COL_SEARCH, searchBlob(entry))
        put(COL_CONTENT_KEY, ClipboardContentKey.forEntry(entry))
        put(COL_FINGERPRINT, ClipboardContentEquivalence.fingerprint(entry))
        put(COL_TYPE, entry.type.name)
        put(COL_PREVIEW, previewText(entry))
        put(COL_HAS_IMAGE, if (entry.hasImageContent()) 1 else 0)
        put(COL_JSON, json.encodeToString(entry))
    }

    private fun previewText(entry: ClipboardEntry): String {
        val text = entry.text.trim().ifBlank { entry.uri ?: entry.intentUri.orEmpty() }
        return text.take(PREVIEW_MAX_LEN)
    }

    private fun searchBlob(entry: ClipboardEntry): String = buildString {
        append(entry.text)
        entry.uri?.let { append('\n').append(it) }
        entry.intentUri?.let { append('\n').append(it) }
    }.lowercase()

    private fun decodeEntry(raw: String): ClipboardEntry? = runCatching {
        json.decodeFromString<ClipboardEntry>(raw)
    }.getOrNull()

    private val readableDatabase: SQLiteDatabase
        get() = openHelper.readableDatabase

    private val writableDatabase: SQLiteDatabase
        get() = openHelper.writableDatabase

    private class StoreOpenHelper(
        context: Context,
        private val json: Json,
    ) : SQLiteOpenHelper(
        context,
        DB_NAME,
        null,
        DB_VERSION,
    ) {
        var ftsEnabled: Boolean = false
            private set

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
            db.rawQuery("PRAGMA journal_mode=WAL", null).close()
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            ftsEnabled = readMetaFtsEnabled(db)
        }

        override fun onCreate(db: SQLiteDatabase) {
            createTable(db)
            createIndexes(db)
            createMetaTable(db)
            val enabled = tryEnableFtsAndRebuild(db)
            writeMetaFtsEnabled(db, enabled)
            ftsEnabled = enabled
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                migrateToV2(db)
            }
            if (oldVersion < 3) {
                migrateToV3(db)
            }
            if (oldVersion < 4) {
                migrateToV4(db)
            }
        }

        private fun createTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_CREATED INTEGER NOT NULL,
                    $COL_SEARCH TEXT NOT NULL,
                    $COL_CONTENT_KEY TEXT NOT NULL,
                    $COL_FINGERPRINT TEXT NOT NULL,
                    $COL_TYPE TEXT NOT NULL DEFAULT 'TEXT',
                    $COL_PREVIEW TEXT NOT NULL DEFAULT '',
                    $COL_HAS_IMAGE INTEGER NOT NULL DEFAULT 0,
                    $COL_JSON TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        private fun createMetaTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $META_TABLE (
                    $META_COL_KEY TEXT PRIMARY KEY NOT NULL,
                    $META_COL_VALUE TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        private fun createFtsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts5(
                    $FTS_COL_ENTRY_ID UNINDEXED,
                    $FTS_COL_SEARCH_TEXT,
                    tokenize='unicode61'
                )
                """.trimIndent(),
            )
        }

        private fun createIndexes(db: SQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${TABLE}_created ON $TABLE($COL_CREATED DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${TABLE}_search ON $TABLE($COL_SEARCH)")
            createDedupIndexes(db)
        }

        private fun createDedupIndexes(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${TABLE}_fingerprint_created " +
                    "ON $TABLE($COL_FINGERPRINT, $COL_CREATED DESC)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${TABLE}_content_key_created " +
                    "ON $TABLE($COL_CONTENT_KEY, $COL_CREATED DESC)",
            )
        }

        private fun migrateToV2(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_CONTENT_KEY TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_FINGERPRINT TEXT NOT NULL DEFAULT ''")
            createIndexes(db)
            backfillDedupColumns(db)
        }

        private fun migrateToV3(db: SQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS idx_${TABLE}_fingerprint")
            db.execSQL("DROP INDEX IF EXISTS idx_${TABLE}_content_key")
            createDedupIndexes(db)
        }

        private fun migrateToV4(db: SQLiteDatabase) {
            createMetaTable(db)
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_TYPE TEXT NOT NULL DEFAULT 'TEXT'")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_PREVIEW TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_HAS_IMAGE INTEGER NOT NULL DEFAULT 0")
            backfillLightweightColumns(db)
            val enabled = tryEnableFtsAndRebuild(db)
            writeMetaFtsEnabled(db, enabled)
            ftsEnabled = enabled
        }

        private fun tryEnableFtsAndRebuild(db: SQLiteDatabase): Boolean {
            if (!probeFts5(db)) return false
            return runCatching {
                createFtsTable(db)
                rebuildFts(db)
                true
            }.getOrDefault(false)
        }

        private fun probeFts5(db: SQLiteDatabase): Boolean = runCatching {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_PROBE_TABLE USING fts5(x)")
            db.execSQL("DROP TABLE IF EXISTS $FTS_PROBE_TABLE")
            true
        }.getOrDefault(false)

        private fun writeMetaFtsEnabled(db: SQLiteDatabase, enabled: Boolean) {
            val values = ContentValues().apply {
                put(META_COL_KEY, META_KEY_FTS_ENABLED)
                put(META_COL_VALUE, if (enabled) META_VALUE_TRUE else META_VALUE_FALSE)
            }
            db.insertWithOnConflict(META_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }

        private fun readMetaFtsEnabled(db: SQLiteDatabase): Boolean {
            if (!metaTableExists(db)) return false
            db.query(
                META_TABLE,
                arrayOf(META_COL_VALUE),
                "$META_COL_KEY = ?",
                arrayOf(META_KEY_FTS_ENABLED),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return false
                return cursor.getString(0) == META_VALUE_TRUE
            }
        }

        private fun metaTableExists(db: SQLiteDatabase): Boolean =
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(META_TABLE),
            ).use { it.moveToFirst() }

        private fun backfillDedupColumns(db: SQLiteDatabase) {
            db.query(TABLE, arrayOf(COL_ID, COL_JSON), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val entry = decodeEntryStatic(json, cursor.getString(1)) ?: continue
                    val values = ContentValues().apply {
                        put(COL_CONTENT_KEY, ClipboardContentKey.forEntry(entry))
                        put(COL_FINGERPRINT, ClipboardContentEquivalence.fingerprint(entry))
                    }
                    db.update(TABLE, values, "$COL_ID = ?", arrayOf(id))
                }
            }
        }

        private fun backfillLightweightColumns(db: SQLiteDatabase) {
            db.query(TABLE, arrayOf(COL_ID, COL_JSON), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val entry = decodeEntryStatic(json, cursor.getString(1)) ?: continue
                    val values = ContentValues().apply {
                        put(COL_TYPE, entry.type.name)
                        put(COL_PREVIEW, previewTextStatic(entry))
                        put(COL_HAS_IMAGE, if (entry.hasImageContent()) 1 else 0)
                    }
                    db.update(TABLE, values, "$COL_ID = ?", arrayOf(id))
                }
            }
        }

        private fun rebuildFts(db: SQLiteDatabase) {
            db.delete(FTS_TABLE, null, null)
            db.query(TABLE, arrayOf(COL_ID, COL_JSON), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val entry = decodeEntryStatic(json, cursor.getString(1)) ?: continue
                    val values = ContentValues().apply {
                        put(FTS_COL_ENTRY_ID, entry.id)
                        put(FTS_COL_SEARCH_TEXT, searchBlobStatic(entry))
                    }
                    db.insert(FTS_TABLE, null, values)
                }
            }
        }
    }

    companion object {
        private const val DB_NAME = "clipboard_history.db"
        private const val DB_VERSION = 4
        private const val TABLE = "clipboard_entries"
        private const val META_TABLE = "clipboard_meta"
        private const val FTS_TABLE = "clipboard_fts"
        private const val FTS_PROBE_TABLE = "_clipboard_fts5_probe"
        private const val COL_ID = "id"
        private const val COL_CREATED = "created_at_ms"
        private const val COL_SEARCH = "search_blob"
        private const val COL_CONTENT_KEY = "content_key"
        private const val COL_FINGERPRINT = "fingerprint"
        private const val COL_TYPE = "entry_type"
        private const val COL_PREVIEW = "preview_text"
        private const val COL_HAS_IMAGE = "has_image"
        private const val COL_JSON = "entry_json"
        private const val META_COL_KEY = "meta_key"
        private const val META_COL_VALUE = "meta_value"
        private const val META_KEY_FTS_ENABLED = "fts_enabled"
        private const val META_VALUE_TRUE = "1"
        private const val META_VALUE_FALSE = "0"
        private const val FTS_COL_ENTRY_ID = "entry_id"
        private const val FTS_COL_SEARCH_TEXT = "search_text"
        private const val PREVIEW_MAX_LEN = 500
        const val SEARCH_RESULT_LIMIT = 500

        private fun decodeEntryStatic(json: Json, raw: String): ClipboardEntry? = runCatching {
            json.decodeFromString<ClipboardEntry>(raw)
        }.getOrNull()

        private fun previewTextStatic(entry: ClipboardEntry): String {
            val text = entry.text.trim().ifBlank { entry.uri ?: entry.intentUri.orEmpty() }
            return text.take(PREVIEW_MAX_LEN)
        }

        private fun searchBlobStatic(entry: ClipboardEntry): String = buildString {
            append(entry.text)
            entry.uri?.let { append('\n').append(it) }
            entry.intentUri?.let { append('\n').append(it) }
        }.lowercase()
    }
}
