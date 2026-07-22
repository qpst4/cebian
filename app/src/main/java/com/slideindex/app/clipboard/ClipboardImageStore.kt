package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ClipboardImageStore {
    private const val DIR_NAME = "clipboard"
    private const val IMAGE_DIR_NAME = "images"

    fun imageDir(context: Context): File =
        File(context.filesDir, "$DIR_NAME/$IMAGE_DIR_NAME").apply { mkdirs() }

    fun imageFile(context: Context, fileName: String): File =
        File(imageDir(context), fileName)

    fun fileNameForIndex(entryId: String, index: Int, total: Int): String =
        if (total == 1) "$entryId.png" else "${entryId}_$index.png"

    fun persistImageBytes(context: Context, entryId: String, bytes: ByteArray, index: Int, total: Int): String? {
        if (bytes.isEmpty()) return null
        val fileName = fileNameForIndex(entryId, index, total)
        val file = imageFile(context, fileName)
        return runCatching {
            file.writeBytes(bytes)
            fileName
        }.getOrNull()
    }

    fun persistFromUri(context: Context, entryId: String, uriString: String, index: Int, total: Int): String? {
        return runCatching {
            val uri = uriString.toUri()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty()) {
                    return@runCatching persistImageBytes(context, entryId, bytes, index, total)
                }
            }
            null
        }.getOrNull()
    }

    fun persistFromSource(context: Context, entryId: String, index: Int, total: Int, src: String): String? {
        val normalized = ClipboardHtmlParser.normalizeImageSrc(src.trim())
        if (normalized.isEmpty()) return null

        ClipboardHtmlParser.decodeDataUriImage(normalized)?.let { bytes ->
            return persistImageBytes(context, entryId, bytes, index, total)
        }

        return when {
            normalized.startsWith("content://", ignoreCase = true) ||
                normalized.startsWith("file://", ignoreCase = true) -> {
                persistFromUri(context, entryId, normalized, index, total)
            }
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> {
                downloadAndPersist(context, entryId, normalized, index, total)
            }
            else -> null
        }
    }

    fun collectImageSources(payload: ClipboardPayload): List<String> =
        collectImageSources(
            htmlText = payload.htmlText,
            imageUris = payload.resolvedImageUris(),
        )

    fun collectImageSourcesForEntry(entry: ClipboardEntry): List<String> =
        collectImageSources(
            htmlText = entry.htmlText,
            imageUris = entry.resolvedImageFileNames().ifEmpty {
                entry.uri?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            },
        )

    private fun collectImageSources(
        htmlText: String?,
        imageUris: List<String>,
    ): List<String> {
        val result = linkedSetOf<String>()
        htmlText
            ?.let { ClipboardHtmlParser.imageSources(it) }
            .orEmpty()
            .map { ClipboardHtmlParser.normalizeImageSrc(it.trim()) }
            .filter { it.isNotEmpty() }
            .filterNot { isUnsafeHtmlEmbeddedContentUri(it) }
            .forEach { result += it }
        imageUris
            .map { ClipboardHtmlParser.normalizeImageSrc(it.trim()) }
            .filter { it.isNotEmpty() }
            .forEach { uri ->
                if (!result.contains(uri)) result += uri
            }
        return result.toList()
    }

    fun persistAllFromPayload(context: Context, entryId: String, payload: ClipboardPayload): List<String> {
        val sources = collectImageSources(payload)
        if (sources.isEmpty()) return emptyList()
        val total = sources.size
        val fileNames = mutableListOf<String>()
        sources.forEachIndexed { index, src ->
            persistFromSource(context, entryId, index, total, src)?.let { fileNames += it }
        }
        return fileNames
    }

    fun persistFromPayload(context: Context, entryId: String, payload: ClipboardPayload): String? =
        persistAllFromPayload(context, entryId, payload).firstOrNull()

    fun loadBitmap(context: Context, fileName: String?): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun loadEntryThumbnail(context: Context, entry: ClipboardEntry): Bitmap? =
        loadEntryThumbnails(context, entry).firstOrNull()

    fun loadEntryThumbnails(context: Context, entry: ClipboardEntry): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { loadBitmap(context, it) }
        }
        if (!entry.hasImageContent() || entry.uri.isNullOrBlank()) return emptyList()
        return runCatching {
            context.contentResolver.openInputStream(entry.uri.toUri())?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()?.let { listOf(it) } ?: emptyList()
    }

    fun delete(context: Context, fileName: String) {
        if (fileName.isBlank()) return
        imageFile(context, fileName).delete()
    }

    fun deleteEntryImages(context: Context, entry: ClipboardEntry) {
        entry.resolvedImageFileNames().forEach { delete(context, it) }
    }

    fun uriForFile(context: Context, fileName: String): Uri? {
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
    }

    fun localUrisForEntry(context: Context, entry: ClipboardEntry): List<Uri> =
        entry.resolvedImageFileNames().mapNotNull { uriForFile(context, it) }

    /** 写入系统剪贴板时用 data URI，便于 Word 等应用粘贴内嵌图片。 */
    fun dataUriForFile(context: Context, fileName: String): String? {
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        return runCatching {
            bytesToDataUri(file.readBytes(), mimeTypeForFileName(fileName))
        }.getOrNull()
    }

    fun dataUriForLocalUri(context: Context, uri: Uri): String? {
        if (!isOwnClipboardImageUri(context, uri)) return null
        val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        return dataUriForFile(context, fileName)
    }

    private fun isOwnClipboardImageUri(context: Context, uri: Uri): Boolean {
        if (!uri.scheme.equals("content", ignoreCase = true)) return false
        val authority = uri.authority ?: return false
        if (authority != "${context.packageName}.fileprovider") return false
        return uri.path.orEmpty().contains("/clipboard_images/", ignoreCase = true)
    }

    private fun mimeTypeForFileName(fileName: String): String = when {
        fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
        fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
        fileName.endsWith(".bmp", ignoreCase = true) -> "image/bmp"
        else -> "image/png"
    }

    private fun bytesToDataUri(bytes: ByteArray, mimeType: String): String? {
        if (bytes.isEmpty()) return null
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$encoded"
    }

    private fun isUnsafeHtmlEmbeddedContentUri(src: String): Boolean {
        if (!src.startsWith("content://", ignoreCase = true)) return false
        val uri = src.toUri()
        val path = uri.path.orEmpty()
        // file_paths.xml 暴露为 clipboard_images/，URI 形如 …/clipboard_images/<file>
        if (path.contains("/clipboard_images/", ignoreCase = true)) return false
        if (path.contains("/$DIR_NAME/", ignoreCase = true)) return false
        val authority = uri.authority ?: return true
        return authority.contains("fileprovider", ignoreCase = true)
    }

    private fun downloadAndPersist(
        context: Context,
        entryId: String,
        urlString: String,
        index: Int,
        total: Int,
    ): String? {
        return runCatching {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.inputStream.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) return null
                persistImageBytes(context, entryId, bytes, index, total)
            }
        }.getOrNull()
    }

    fun persistFromBitmap(context: Context, entryId: String, bitmap: Bitmap): String? {
        val fileName = "$entryId.png"
        val file = imageFile(context, fileName)
        return runCatching {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            fileName
        }.getOrNull()
    }
}
