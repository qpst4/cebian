package com.slideindex.app.clipboard

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

/**
 * 拖拽给第三方宿主时，把源文件镜像到系统 MediaStore。
 *
 * 微信 / QQ 等宿主只认系统相册、视频、音频或下载集合的 `content://media/...` URI，
 * 不认应用自己的 FileProvider authority。跨应用拖拽前先落一份系统可读镜像，
 * 才能让这些宿主在松手后真正读取到文件。
 */
object DragFileMirror {

    private const val PREFS = "drag_file_mirror_records"
    private const val TTL_MS = 60 * 60 * 1000L
    private const val CACHE_DIR = "SlideIndex/DragCache"
    private const val MAX_MIRROR_BYTES = 64L * 1024 * 1024
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 将 [source] 复制到 MediaStore 并返回系统 URI。失败时返回 null。
     *
     * 图片进入相册集合，视频 / 音频进入各自集合，其余文件进入下载集合。
     */
    fun prepareForHost(
        context: Context,
        source: Uri,
        displayName: String?,
        mimeType: String?,
        hostPackage: String?,
    ): Uri? {
        purgeExpired(context)
        val app = context.applicationContext
        val resolver = app.contentResolver
        val sourceSize = querySize(app, source)
        if (sourceSize != null && sourceSize > MAX_MIRROR_BYTES) return null
        val mime = resolveMime(app, source, displayName, mimeType)
        val name = ensureExtension(
            sanitizeFileName(displayName ?: queryDisplayName(app, source) ?: source.lastPathSegment ?: "file"),
            mime,
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathFor(mime))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching {
            resolver.insert(collectionFor(mime), values)
        }.getOrNull() ?: return null
        val copied = runCatching {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(target, "w")?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: return@runCatching false
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
        if (!copied || querySize(app, target) == 0L) {
            runCatching { resolver.delete(target, null, null) }
            return null
        }
        runCatching {
            resolver.update(
                target,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        if (!hostPackage.isNullOrBlank()) {
            runCatching {
                app.grantUriPermission(hostPackage, target, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = target.toString()
        val expiresAt = System.currentTimeMillis() + TTL_MS
        prefs.edit().putLong(key, expiresAt).apply()
        cleanupScope.launch {
            delay(TTL_MS + 5_000L)
            if (prefs.getLong(key, 0L) != expiresAt) return@launch
            runCatching { resolver.delete(target, null, null) }
            prefs.edit { remove(key) }
        }
        return target
    }

    /** 删除超过 TTL 的镜像；在每次新建镜像前调用，避免长期占用空间。 */
    fun purgeExpired(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val stale = prefs.all.mapNotNull { (key, value) ->
            val expires = value as? Long ?: return@mapNotNull key
            key.takeIf { expires <= now }
        }
        if (stale.isEmpty()) return
        stale.forEach { raw ->
            runCatching {
                app.contentResolver.delete(Uri.parse(raw), null, null)
            }
        }
        prefs.edit { stale.forEach { remove(it) } }
    }

    private fun collectionFor(mime: String): Uri = when {
        mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    private fun relativePathFor(mime: String): String = when {
        mime.startsWith("image/") -> "${Environment.DIRECTORY_PICTURES}/$CACHE_DIR"
        mime.startsWith("video/") -> "${Environment.DIRECTORY_MOVIES}/$CACHE_DIR"
        mime.startsWith("audio/") -> "${Environment.DIRECTORY_MUSIC}/$CACHE_DIR"
        else -> "${Environment.DIRECTORY_DOWNLOADS}/$CACHE_DIR"
    }

    private fun resolveMime(
        context: Context,
        source: Uri,
        displayName: String?,
        provided: String?,
    ): String {
        val normalized = provided?.trim()?.takeIf { it.isNotEmpty() && it != "*/*" }
        val fromResolver = runCatching { context.contentResolver.getType(source) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "*/*" }
        val fromExtension = extensionOf(displayName ?: source.lastPathSegment)
            .let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return when {
            normalized != null &&
                normalized != "image/*" &&
                normalized != "application/octet-stream" -> normalized
            !fromResolver.isNullOrBlank() -> fromResolver
            !fromExtension.isNullOrBlank() -> fromExtension
            normalized == "image/*" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private fun queryDisplayName(context: Context, source: Uri): String? = runCatching {
        context.contentResolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun querySize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull()

    private fun extensionOf(raw: String?): String =
        raw?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.length in 1..8 }
            .orEmpty()

    private fun ensureExtension(name: String, mime: String): String {
        if (name.substringAfterLast('.', "").isNotBlank()) return name
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return name
        return "$name.$extension"
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.replace('\\', '/').substringAfterLast('/').trim()
        val cleaned = INVALID_NAME.matcher(trimmed).replaceAll("_").trim('.', ' ')
        return cleaned.ifBlank { "file" }
    }

    private val INVALID_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]")
}
