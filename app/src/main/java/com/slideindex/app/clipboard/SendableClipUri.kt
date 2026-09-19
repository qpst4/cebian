package com.slideindex.app.clipboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.content.edit
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

/**
 * 参考 AI 剪贴板 [ca.A]：为拖放/粘贴准备短期可授权的 content URI（`*.sendfiles`）。
 */
object SendableClipUri {

    const val AUTHORITY_SUFFIX = ".sendfiles"
    private const val PREFS = "sendable_clip_file_records"
    private const val TTL_MS = 600_000L
    private val idPattern = Pattern.compile("[a-f0-9]{32}")

    fun authorityFor(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    fun prepareForHost(
        context: Context,
        source: Uri,
        displayName: String?,
        mimeType: String?,
        hostPackage: String,
    ): Uri? {
        purgeExpired(context)
        val app = context.applicationContext
        val name = sanitizeFileName(displayName ?: source.lastPathSegment ?: "file")
        val mime = resolveMime(name, mimeType)
        val size = querySize(app, source) ?: return null
        if (size <= 0L) return null
        val id = UUID.randomUUID().toString().replace("-", "")
        if (!idPattern.matcher(id).matches()) return null
        val record = JSONObject()
            .put("source", source.toString())
            .put("name", name)
            .put("mime", mime)
            .put("size", size)
            .put("target", hostPackage.lowercase(Locale.ROOT))
            .put("expires", System.currentTimeMillis() + TTL_MS)
        if (!app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(id, record.toString())
            .commit()
        ) {
            return null
        }
        val sendUri = Uri.Builder()
            .scheme("content")
            .authority(authorityFor(app))
            .appendPath(id)
            .appendPath(name)
            .build()
        try {
            app.grantUriPermission(hostPackage, sendUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
            return null
        }
        return sendUri
    }

    internal fun resolveRecord(context: Context, id: String?): Record? {
        if (id.isNullOrBlank() || !idPattern.matcher(id).matches()) return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(id, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val expires = json.optLong("expires", 0L)
            if (expires <= System.currentTimeMillis()) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(id) }
                return null
            }
            Record(
                source = Uri.parse(json.getString("source")),
                name = json.optString("name", "file"),
                mime = json.optString("mime", "application/octet-stream"),
                target = json.optString("target", ""),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun purgeExpired(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val stale = prefs.all.mapNotNull { (key, value) ->
            val expires = runCatching {
                JSONObject(value as String).optLong("expires", 0L)
            }.getOrElse { 0L }
            if (expires <= now) key else null
        }
        if (stale.isEmpty()) return
        prefs.edit {
            stale.forEach { remove(it) }
        }
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()
    }

    private fun resolveMime(fileName: String, mimeType: String?): String {
        if (!mimeType.isNullOrBlank() && mimeType != "image/*") return mimeType
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: mimeType?.takeIf { it.isNotBlank() }
            ?: "image/jpeg"
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.replace('\\', '/').substringAfterLast('/').trim()
        val cleaned = INVALID_NAME.matcher(trimmed).replaceAll("_").trim('.', ' ')
        return cleaned.ifBlank { "file" }
    }

    private val INVALID_NAME = Pattern.compile("[\\\\/:*?\"<>|]")

    data class Record(
        val source: Uri,
        val name: String,
        val mime: String,
        val target: String,
    )
}
