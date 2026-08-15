package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

object ClipboardWriter {

    fun write(context: Context, entry: ClipboardEntry) {
        writeInternal(context, entry, promote = true)
    }

    /** 粘贴前写入系统剪贴板，不提升历史条目顺序（避免「粘贴」被当成「复制」）。 */
    fun writeForPaste(context: Context, entry: ClipboardEntry) {
        writeInternal(context, entry, promote = false)
    }

    private fun writeInternal(context: Context, entry: ClipboardEntry, promote: Boolean) {
        ClipboardAccess.repository?.noteOutgoingWrite(entry)
        if (promote) {
            ClipboardAccess.repository?.promoteById(entry.id)
        }
        val clip = buildClipForEntry(context, entry) ?: return
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    }

    fun buildClipForEntry(context: Context, entry: ClipboardEntry): ClipData? {
        val imageSources = ClipboardImageStore.collectImageSourcesForEntry(entry)
        val blocks = ClipboardImageLabel.blocksForClipboardWrite(
            blocks = entry.resolvedContentBlocks(),
            imageSources = imageSources,
            uri = entry.uri,
        )
        if (blocks.isNotEmpty()) {
            return buildClipForBlocks(
                context = context,
                mimeType = entry.mimeType,
                htmlText = entry.htmlText,
                blocks = blocks,
                fallbackImageUris = imageSources,
                resolveDataUri = { ClipboardImageStore.dataUriForFile(context, it) },
                resolveContentUri = { ClipboardImageStore.uriForFile(context, it) },
                resolveDimensions = { ClipboardImageStore.imageDimensions(context, it) },
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
                imageFileNames = entry.resolvedImageFileNames(),
            ),
        )
    }

    fun buildClipForBlocks(
        context: Context? = null,
        mimeType: String? = null,
        htmlText: String?,
        blocks: List<ClipboardContentBlock>,
        fallbackImageUris: List<String> = emptyList(),
        resolveDataUri: (String) -> String?,
        resolveContentUri: (String) -> Uri?,
        resolveDimensions: (String) -> Pair<Int, Int>? = { null },
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
            resolveDimensions = resolveDimensions,
        )
    }

    fun writeBlocks(
        context: Context,
        blocks: List<ClipboardContentBlock>,
        htmlText: String? = null,
        resolveDataUri: (String) -> String?,
        resolveContentUri: (String) -> Uri?,
        resolveDimensions: (String) -> Pair<Int, Int>? = { null },
    ): Boolean {
        if (blocks.isEmpty()) return false
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = buildClipForBlocks(
            context = context,
            htmlText = htmlText,
            blocks = blocks,
            resolveDataUri = resolveDataUri,
            resolveContentUri = resolveContentUri,
            resolveDimensions = resolveDimensions,
        ) ?: return false
        clipboard.setPrimaryClip(clip)
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
        resolveDimensions: (String) -> Pair<Int, Int>?,
    ): ClipData? {
        val imageBlocks = blocks.filter { it.kind == ClipboardBlockKind.IMAGE }
        val imageUris = resolveImageUrisForBlocks(imageBlocks, resolveContentUri, fallbackImageUris)
        if (blocks.all { it.kind == ClipboardBlockKind.IMAGE }) {
            return buildPureImageClip(context, mimeType, imageUris)
        }

        val plainText = blocks.filter { it.kind == ClipboardBlockKind.TEXT }
            .joinToString("\n\n") { it.text.trim() }
            .trim()
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
                    imageSizeForFile = resolveDimensions,
                )
            }
        }
        return buildRichHtmlClip(plainText, html, imageUris)
    }

    private fun resolveImageUrisForBlocks(
        imageBlocks: List<ClipboardContentBlock>,
        resolveContentUri: (String) -> Uri?,
        fallbackImageUris: List<String>,
    ): List<Uri> {
        val localUris = imageBlocks.mapNotNull { resolveContentUri(it.fileName) }
        if (localUris.isNotEmpty()) return localUris
        return fallbackImageUris.mapNotNull { runCatching { it.toUri() }.getOrNull() }
    }

    private fun buildPureImageClip(
        context: Context?,
        mimeType: String?,
        imageUris: List<Uri>,
    ): ClipData? {
        if (imageUris.isEmpty()) return null
        val type = mimeType ?: "image/*"
        val first = imageUris.first()
        if (context == null) {
            return ClipData.newRawUri("clipboard", first)
        }
        return if (imageUris.size == 1) {
            ClipData.newUri(context.contentResolver, type, first)
        } else {
            ClipData.newUri(context.contentResolver, type, first).also { clip ->
                imageUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
        }
    }

    fun writePayload(context: Context, payload: ClipboardPayload) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = buildClipData(context, payload) ?: return
        clipboard.setPrimaryClip(clip)
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
            uri = payload.uri,
        )

        if (!html.isNullOrBlank() && imageUris.isEmpty()) {
            val plain = plainText.ifBlank { ClipboardHtmlParser.plainTextFromHtml(html) }
            return ClipData.newHtmlText("clipboard", plain, html)
        }

        if (imageUris.isNotEmpty()) {
            val imageSrcs = imageUris.map { uri ->
                ClipboardImageStore.dataUriForLocalUri(context, uri) ?: uri.toString()
            }
            val rebuiltHtml = when {
                !html.isNullOrBlank() &&
                    ClipboardHtmlParser.imageSources(html).size == imageSrcs.size -> {
                    ClipboardHtmlParser.rebuildHtmlImageSources(html, imageSrcs)
                }
                !html.isNullOrBlank() && ClipboardHtmlParser.imageSources(html).size > 1 -> {
                    ClipboardHtmlParser.buildHtml(
                        plainText.ifBlank { ClipboardHtmlParser.plainTextFromHtml(html) },
                        imageSrcs,
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
            return buildMultiImageClip(context, payload, imageUris, plainText, imageSrcs)
        }

        return when (payload.type) {
            ClipboardEntryType.TEXT -> ClipData.newPlainText("clipboard", payload.text)
            ClipboardEntryType.URI -> {
                val uri = payload.uri?.toUri() ?: return null
                ClipData.newUri(
                    context.contentResolver,
                    payload.mimeType ?: "text/*",
                    uri,
                )
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
        imageUris: List<Uri>,
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
        context: Context,
        payload: ClipboardPayload,
        imageUris: List<Uri>,
        plainText: String,
        imageSrcs: List<String>,
    ): ClipData? {
        val mimeType = payload.mimeType ?: "image/*"
        if (plainText.isNotBlank()) {
            val html = ClipboardHtmlParser.buildHtml(plainText, imageSrcs)
            return buildRichHtmlClip(plainText, html, imageUris)
        }
        val first = imageUris.first()
        return if (imageUris.size == 1) {
            ClipData.newUri(context.contentResolver, mimeType, first)
        } else {
            ClipData.newUri(context.contentResolver, mimeType, first).also { clip ->
                imageUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
        }
    }
}
