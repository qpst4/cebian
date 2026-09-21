package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

object ClipboardWriter {

    fun write(context: Context, entry: ClipboardEntry) {
        writeInternal(context, entry, promote = true, grantReadToPackage = null)
    }

    /** 粘贴前写入系统剪贴板，不提升历史条目顺序（避免「粘贴」被当成「复制」）。 */
    fun writeForPaste(context: Context, entry: ClipboardEntry) {
        writeInternal(context, entry, promote = false, grantReadToPackage = null)
    }

    /**
     * 微信等宿主粘贴图片前需对前台包 [grantUriPermission]（参考 AI 剪贴板 PASTE_CLIP 链路）。
     */
    fun writeForPasteToHost(
        context: Context,
        entry: ClipboardEntry,
        hostPackage: String,
    ): Boolean {
        return writeInternal(context, entry, promote = false, grantReadToPackage = hostPackage)
    }

    fun grantClipReadToPackage(context: Context, clip: ClipData, hostPackage: String) {
        for (index in 0 until clip.itemCount) {
            val uri = clip.getItemAt(index).uri ?: continue
            if (uri.scheme != "content") continue
            try {
                context.grantUriPermission(
                    hostPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Throwable) {
            }
        }
    }

    private fun writeInternal(
        context: Context,
        entry: ClipboardEntry,
        promote: Boolean,
        grantReadToPackage: String?,
    ): Boolean {
        ClipboardAccess.repository?.noteOutgoingWrite(entry)
        if (promote) {
            ClipboardAccess.repository?.promoteById(entry.id)
        }
        val clip = buildClipForEntry(context, entry) ?: return false
        if (!grantReadToPackage.isNullOrBlank()) {
            grantClipReadToPackage(context, clip, grantReadToPackage)
        }
        safeSetPrimaryClip(context, clip) { buildFallbackUriClip(context, entry) }
        return true
    }

    fun buildClipForEntry(context: Context, entry: ClipboardEntry): ClipData? {
        if (entry.isPureImageEntry()) {
            val localUris = ClipboardImageStore.localUrisForEntry(context, entry)
            if (localUris.isNotEmpty()) {
                return buildPureImageClip(entry.mimeType, localUris)
            }
        }
        if (!entry.hasImageContent()) {
            val text = entry.text.trim()
            if (text.isNotEmpty()) {
                return ClipData.newPlainText("clipboard", text)
            }
        }
        val imageSources = ClipboardImageStore.collectImageSourcesForEntry(entry)
        val blocks = ClipboardImageLabel.blocksForClipboardWrite(
            blocks = entry.resolvedContentBlocks(),
            imageSources = imageSources,
            uri = entry.uri
        )
        if (blocks.isNotEmpty()) {
            return buildClipForBlocks(
                context = context,
                mimeType = entry.mimeType,
                htmlText = entry.htmlText,
                blocks = blocks,
                fallbackImageUris = readableFallbackImageUris(imageSources, entry.uri),
                resolveDataUri = { fileName -> ClipboardImageStore.uriForFile(context, fileName)?.toString() },
                resolveContentUri = { ClipboardImageStore.uriForFile(context, it) },
                resolveDimensions = { ClipboardImageStore.imageDimensions(context, it) }
            )
        }
        return buildClipData(
            context,
            ClipboardPayload(
                type = entry.type,
                text = entry.text,
                uri = entry.uri,
                intentUri = entry.intentUri,
                htmlText = entry.htmlText,
                mimeType = entry.mimeType,
                imageFileName = entry.imageFileName,
                imageFileNames = entry.resolvedImageFileNames()
            )
        )
    }

    /**
     * 回退用的图片 URI 列表。
     *
     * 本地图片文件缺失时 [ClipboardImageStore.uriForFile] 解析不出 URI，此时才会退到这里。
     * 内部文件名（`entry-1.png` 这类无 scheme 的相对路径）接收方读不到，必须过滤掉，
     * 并补上 entry 自身的 content URI 作为可用回退。
     */
    private fun readableFallbackImageUris(imageSources: List<String>, entryUri: String?): List<String> =
        (imageSources + listOfNotNull(entryUri))
            .map(String::trim)
            .filter { it.isNotEmpty() && it.toUri().scheme != null }
            .distinct()

    fun buildClipForBlocks(
        context: Context? = null,
        mimeType: String? = null,
        htmlText: String?,
        blocks: List<ClipboardContentBlock>,
        fallbackImageUris: List<String> = emptyList(),
        resolveDataUri: (String) -> String?,
        resolveContentUri: (String) -> Uri?,
        resolveDimensions: (String) -> Pair<Int, Int>? = { null }
    ): ClipData? {
        if (blocks.isEmpty()) return null
        return buildClipFromBlocks(
            context = context,
            mimeType = mimeType,
            htmlText = htmlText,
            blocks = blocks,
            fallbackImageUris = fallbackImageUris,
            resolveDataUri = resolveDataUri,
            resolveContentUri = resolveContentUri,
            resolveDimensions = resolveDimensions
        )
    }

    fun writeBlocks(
        context: Context,
        blocks: List<ClipboardContentBlock>,
        htmlText: String? = null,
        resolveDataUri: (String) -> String?,
        resolveContentUri: (String) -> Uri?,
        resolveDimensions: (String) -> Pair<Int, Int>? = { null }
    ): Boolean {
        if (blocks.isEmpty()) return false
        val clip = buildClipForBlocks(
            context = context,
            htmlText = htmlText,
            blocks = blocks,
            resolveDataUri = resolveDataUri,
            resolveContentUri = resolveContentUri,
            resolveDimensions = resolveDimensions
        ) ?: return false
        safeSetPrimaryClip(context, clip)
        return true
    }

    private fun buildClipFromBlocks(
        context: Context?,
        mimeType: String?,
        htmlText: String?,
        blocks: List<ClipboardContentBlock>,
        fallbackImageUris: List<String>,
        resolveDataUri: (String) -> String?,
        resolveContentUri: (String) -> Uri?,
        resolveDimensions: (String) -> Pair<Int, Int>?
    ): ClipData? {
        val imageBlocks = blocks.filter { it.kind == ClipboardBlockKind.IMAGE }
        val imageUris = resolveImageUrisForBlocks(imageBlocks, resolveContentUri, fallbackImageUris)
        if (blocks.all { it.kind == ClipboardBlockKind.IMAGE }) {
            return buildPureImageClip(mimeType, imageUris)
        }

        val plainText = blocks.filter { it.kind == ClipboardBlockKind.TEXT }
            .joinToString("\n") { it.text.trim() }
            .trim()

        if (blocks.all { it.kind == ClipboardBlockKind.TEXT } && htmlText.isNullOrBlank()) {
            return ClipData.newPlainText("clipboard", plainText)
        }

        val dataUris = imageBlocks.mapNotNull { resolveDataUri(it.fileName) }
        val originalHtml = htmlText?.trim()?.takeIf { it.isNotEmpty() }
        val html = when {
            !originalHtml.isNullOrBlank() &&
                dataUris.isNotEmpty() &&
                ClipboardHtmlParser.imageSources(originalHtml).size == dataUris.size -> {
                ClipboardHtmlParser.rebuildHtmlImageSources(originalHtml, dataUris)
            }
            else -> {
                ClipboardHtmlParser.buildHtmlFromBlocks(
                    blocks = blocks,
                    imageSrcForFile = resolveDataUri,
                    imageSizeForFile = resolveDimensions
                )
            }
        }
        return buildRichHtmlClip(plainText, html, imageUris)
    }

    private fun resolveImageUrisForBlocks(
        imageBlocks: List<ClipboardContentBlock>,
        resolveContentUri: (String) -> Uri?,
        fallbackImageUris: List<String>
    ): List<Uri> {
        val localUris = imageBlocks.mapNotNull { resolveContentUri(it.fileName) }
        if (localUris.isNotEmpty()) return localUris
        return fallbackImageUris.mapNotNull { runCatching { it.toUri() }.getOrNull() }
    }

    /**
     * 构造显式声明 mime 的 URI 剪贴项。
     *
     * `ClipData.newUri(resolver, label, uri)` 的第二参是 **label**，mime 由 resolver 反查：
     * 一旦查不到（媒体行已删除、调用方没有读权限等），description 会退化成 `text/plain`，
     * 图片剪贴就可能被接收方当纯文本处理。这里直接声明类型，不依赖反查结果。
     */
    private fun uriClip(mimeType: String, imageUris: List<Uri>): ClipData {
        val clip = ClipData(
            ClipDescription("clipboard", arrayOf(mimeType)),
            ClipData.Item(imageUris.first())
        )
        imageUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return clip
    }

    private fun buildPureImageClip(mimeType: String?, imageUris: List<Uri>): ClipData? {
        if (imageUris.isEmpty()) return null
        return uriClip(mimeType ?: "image/*", imageUris)
    }

    fun writePayload(context: Context, payload: ClipboardPayload) {
        val clip = buildClipData(context, payload) ?: return
        safeSetPrimaryClip(context, clip)
    }

    private fun buildClipData(context: Context, payload: ClipboardPayload): ClipData? {
        val localImageUris = payload.resolvedImageFileNames()
            .mapNotNull { ClipboardImageStore.uriForFile(context, it) }
        val remoteImageUris = payload.resolvedImageUris()
            .filter { uri -> localImageUris.none { local -> local.toString() == uri } }
            .mapNotNull { runCatching { it.toUri() }.getOrNull() }
        val imageUris = (localImageUris + remoteImageUris).distinctBy { it.toString() }

        val html = payload.htmlText?.trim()?.takeIf { it.isNotEmpty() }
        val imageSources = payload.resolvedImageUris()
        val plainText = ClipboardImageLabel.stripMetadataText(
            text = payload.text,
            imageSources = imageSources,
            uri = payload.uri
        )

        if (!html.isNullOrBlank() && imageUris.isEmpty()) {
            val plain = plainText.ifBlank { ClipboardHtmlParser.plainTextFromHtml(html) }
            return ClipData.newHtmlText("clipboard", plain, html)
        }

        if (imageUris.isNotEmpty()) {
            val imageSrcs = imageUris.map { uri -> uri.toString() }
            val rebuiltHtml = when {
                !html.isNullOrBlank() &&
                    ClipboardHtmlParser.imageSources(html).size == imageSrcs.size -> {
                    ClipboardHtmlParser.rebuildHtmlImageSources(html, imageSrcs)
                }
                !html.isNullOrBlank() && ClipboardHtmlParser.imageSources(html).size > 1 -> {
                    ClipboardHtmlParser.buildHtml(
                        plainText.ifBlank { ClipboardHtmlParser.plainTextFromHtml(html) },
                        imageSrcs
                    )
                }
                plainText.isNotBlank() || imageUris.size > 1 -> {
                    ClipboardHtmlParser.buildHtml(plainText, imageSrcs)
                }
                else -> null
            }
            if (rebuiltHtml != null) {
                return buildRichHtmlClip(plainText, rebuiltHtml, imageUris)
            }
            return buildMultiImageClip(payload, imageUris, plainText, imageSrcs)
        }

        return when (payload.type) {
            ClipboardEntryType.TEXT -> ClipData.newPlainText("clipboard", payload.text)
            ClipboardEntryType.URI -> {
                val uri = payload.uri?.toUri() ?: return null
                val mime = payload.mimeType
                if (mime.isNullOrBlank()) {
                    ClipData.newUri(context.contentResolver, "clipboard", uri)
                } else {
                    uriClip(mime, listOf(uri))
                }
            }
            ClipboardEntryType.INTENT -> {
                val intentUri = payload.intentUri ?: return null
                val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                ClipData.newIntent("clipboard", intent)
            }
            ClipboardEntryType.HTML -> {
                val plain = payload.text
                ClipData.newHtmlText("clipboard", plain, plain)
            }
        }
    }

    private fun buildRichHtmlClip(
        plainText: String,
        html: String,
        imageUris: List<Uri>
    ): ClipData? {
        if (html.isBlank() && plainText.isBlank() && imageUris.isEmpty()) return null
        val clip = if (html.isNotBlank()) {
            ClipData.newHtmlText("clipboard", plainText.ifBlank { " " }, html)
        } else {
            ClipData.newPlainText("clipboard", plainText)
        }
        imageUris.forEach { uri -> clip.addItem(ClipData.Item(uri)) }
        return clip
    }

    private fun buildMultiImageClip(
        payload: ClipboardPayload,
        imageUris: List<Uri>,
        plainText: String,
        imageSrcs: List<String>
    ): ClipData? {
        val mimeType = payload.mimeType ?: "image/*"
        if (plainText.isNotBlank()) {
            val html = ClipboardHtmlParser.buildHtml(plainText, imageSrcs)
            return buildRichHtmlClip(plainText, html, imageUris)
        }
        return uriClip(mimeType, imageUris)
    }

    private fun safeSetPrimaryClip(
        context: Context,
        clip: ClipData,
        fallback: (() -> ClipData?)? = null
    ) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        try {
            clipboard.setPrimaryClip(clip)
        } catch (_: RuntimeException) {
            // TransactionTooLargeException: clip parcel exceeds Binder limit.
            // Retry with a smaller fallback clip if available.
            val fb = fallback?.invoke() ?: return
            runCatching { clipboard.setPrimaryClip(fb) }
        }
    }

    private fun buildFallbackUriClip(context: Context, entry: ClipboardEntry): ClipData? {
        val uris = ClipboardImageStore.localUrisForEntry(context, entry)
        if (uris.isNotEmpty()) {
            return buildPureImageClip(entry.mimeType, uris)
        }
        val text = entry.text.trim()
        if (text.isNotEmpty()) {
            return ClipData.newPlainText("clipboard", text)
        }
        return null
    }
}
