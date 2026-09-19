package com.slideindex.app.clipboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * 参考 [com.toolbar.clipboard.SendableFileProvider]：只读转发源 URI，供微信等宿主拖放读取。
 */
class SendableClipFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = context != null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val record = recordFor(uri) ?: return null
        val columns = projection?.toList()?.ifEmpty { null }
            ?: listOf("_display_name", "_size", "mime_type")
        val row = Array(columns.size) { index ->
            when (columns[index]) {
                "_display_name" -> record.name
                "mime_type" -> record.mime
                "_size" -> querySize(record.source)?.toString()
                else -> null
            }
        }
        return MatrixCursor(columns.toTypedArray(), 1).apply { addRow(row) }
    }

    override fun getType(uri: Uri): String? = recordFor(uri)?.mime

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.startsWith("r") || mode.contains('w')) {
            throw SecurityException("Sendable clip files are read-only")
        }
        val record = recordFor(uri) ?: throw FileNotFoundException("Sendable URI expired")
        val callingUid = Binder.getCallingUid()
        verifyCallerPackage(callingUid, record.target)
        val identity = Binder.clearCallingIdentity()
        try {
            val ctx = context ?: throw FileNotFoundException(record.name)
            if (record.source.scheme == "file") {
                val path = record.source.path ?: throw FileNotFoundException(record.name)
                val file = File(path)
                if (!file.isFile || file.length() <= 0L) throw FileNotFoundException(record.name)
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            return ctx.contentResolver.openFileDescriptor(record.source, "r")
                ?: throw FileNotFoundException(record.name)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun recordFor(uri: Uri): SendableClipUri.Record? {
        val ctx = context ?: return null
        if (uri.authority != SendableClipUri.authorityFor(ctx)) return null
        val id = uri.pathSegments.firstOrNull() ?: return null
        return SendableClipUri.resolveRecord(ctx, id)
    }

    private fun querySize(source: Uri): Long? {
        val ctx = context ?: return null
        return runCatching {
            ctx.contentResolver.openFileDescriptor(source, "r")?.use { it.statSize }
        }.getOrNull()
    }

    private fun verifyCallerPackage(callingUid: Int, expectedPackage: String) {
        if (expectedPackage.isBlank()) return
        val pm = context?.packageManager ?: return
        val packages = pm.getPackagesForUid(callingUid) ?: return
        val expected = expectedPackage.lowercase()
        if (packages.none { it.equals(expected, ignoreCase = true) }) {
            throw SecurityException("UID not allowed to read sendable clip")
        }
    }
}
