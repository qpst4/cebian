package com.slideindex.app.overlay

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

/**
 * 将用户从文档选择器获得的 URI 复制到应用私有目录，避免重装/权限失效后悬浮球样式丢失。
 */
internal object FloatBallStyleAssetStore {
    private const val ASSET_DIR = "float_ball_assets"
    private const val GIF_FILE = "float_ball.gif"
    private const val CUSTOM_IMAGE_FILE = "float_ball_custom"
    private const val SLIDE_PREFIX = "float_ball_slide_"

    fun importGif(context: Context, sourceUri: Uri): String? =
        importReplacing(context, sourceUri, GIF_FILE)

    fun importCustomImage(context: Context, sourceUri: Uri): String? =
        importReplacing(
            context = context,
            sourceUri = sourceUri,
            destName = CUSTOM_IMAGE_FILE + extensionFor(context, sourceUri)
        )

    fun importSlideshow(context: Context, sourceUris: List<Uri>): List<String> {
        if (sourceUris.isEmpty()) return emptyList()
        val dir = assetDir(context)
        dir.listFiles()
            ?.filter { it.name.startsWith(SLIDE_PREFIX) }
            ?.forEach { it.delete() }
        return sourceUris.mapIndexedNotNull { index, uri ->
            importReplacing(
                context = context,
                sourceUri = uri,
                destName = "$SLIDE_PREFIX$index${extensionFor(context, uri)}",
                reuseDir = dir
            )
        }
    }

    fun resolveReadableUri(context: Context, stored: String): Uri? {
        if (stored.isBlank()) return null
        val uri = stored.toUri()
        return if (canRead(context, uri)) uri else null
    }

    fun canRead(context: Context, stored: String): Boolean {
        if (stored.isBlank()) return false
        return canRead(context, stored.toUri())
    }

    private fun canRead(context: Context, uri: Uri): Boolean =
        runCatching {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return@runCatching false
                    val file = File(path)
                    file.exists() && file.canRead() && file.length() > 0L
                }
                else -> context.contentResolver.openInputStream(uri)?.use { } != null
            }
        }.getOrDefault(false)

    private fun importReplacing(
        context: Context,
        sourceUri: Uri,
        destName: String,
        reuseDir: File? = null
    ): String? = runCatching {
        val dir = reuseDir ?: assetDir(context)
        dir.mkdirs()
        val dest = File(dir, destName)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        check(dest.exists() && dest.length() > 0L)
        dest.toUri().toString()
    }.getOrNull()

    private fun assetDir(context: Context): File = File(context.filesDir, ASSET_DIR)

    private fun extensionFor(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when {
            mime.contains("gif", ignoreCase = true) -> ".gif"
            mime.contains("png", ignoreCase = true) -> ".png"
            mime.contains("webp", ignoreCase = true) -> ".webp"
            mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> ".jpg"
            else -> ".img"
        }
    }
}
