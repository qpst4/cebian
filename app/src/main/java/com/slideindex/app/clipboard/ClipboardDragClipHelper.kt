package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * 跨应用拖放前按前台宿主调整 URI。
 *
 * 微信 / QQ 等宿主会忽略应用私有 FileProvider URI，只认系统 MediaStore URI；
 * 对这些宿主先把文件镜像到 MediaStore，再交给系统拖拽框架。
 */
object ClipboardDragClipHelper {

    fun remapForHostIfNeeded(
        context: Context,
        clipData: ClipData,
        hostPackage: String?,
    ): ClipData? {
        if (hostPackage.isNullOrBlank() || hostPackage == context.packageName) return clipData
        return remapUrisForHost(context, clipData, hostPackage)
    }

    private fun remapUrisForHost(
        context: Context,
        base: ClipData,
        hostPackage: String,
    ): ClipData? {
        if ((0 until base.itemCount).none { base.getItemAt(it).uri != null }) return base
        val items = mutableListOf<ClipData.Item>()
        val mimeTypes = linkedSetOf<String>()
        for (index in 0 until base.description.mimeTypeCount) {
            mimeTypes += base.description.getMimeType(index)
        }
        var mirroredCount = 0
        for (index in 0 until base.itemCount) {
            val item = base.getItemAt(index)
            val sourceUri = item.uri
            if (sourceUri == null) {
                items.add(item)
                continue
            }
            val mime = resolveMime(context, sourceUri)
            val sendUri = DragFileMirror.prepareForHost(
                context = context,
                source = sourceUri,
                displayName = resolveDisplayName(context, sourceUri, mime),
                mimeType = mime,
                hostPackage = hostPackage,
            ) ?: continue
            items += ClipData.Item(sendUri)
            mimeTypes += mime
            mimeTypes += wildcardFor(mime)
            if (!mime.startsWith("image/") && !mime.startsWith("video/") && !mime.startsWith("audio/")) {
                mimeTypes += "*/*"
            }
            mirroredCount++
        }
        if (mirroredCount == 0 || items.isEmpty()) return null
        val label = base.description.label?.toString()?.takeIf { it.isNotBlank() } ?: "clipboard"
        val description = ClipDescription(label, mimeTypes.toTypedArray())
        val clip = ClipData(description, items.first())
        items.drop(1).forEach { clip.addItem(it) }
        return clip
    }

    private fun resolveMime(context: Context, uri: Uri): String {
        val fromResolver = runCatching { context.contentResolver.getType(uri) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "*/*" }
        if (fromResolver != null && fromResolver != "application/octet-stream") return fromResolver
        val fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        )
        return fromExtension ?: fromResolver ?: "application/octet-stream"
    }

    private fun resolveDisplayName(context: Context, uri: Uri, mime: String): String {
        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val raw = queried ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "drag"
        if (raw.substringAfterLast('.', "").isNotBlank()) return raw
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        return "$raw.$extension"
    }

    private fun wildcardFor(mime: String): String =
        if (mime.contains('/')) "${mime.substringBefore('/')}/*" else "*/*"
}
