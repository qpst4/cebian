package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

object ClipboardWriter {
    fun write(context: Context, entry: ClipboardEntry) {
        writePayload(
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
        val plainText = payload.text.trim()

        if (!html.isNullOrBlank() && imageUris.isEmpty()) {
            val plain = plainText.ifBlank { ClipboardHtmlParser.plainTextFromHtml(html) }
            return ClipData.newHtmlText("clipboard", plain, html)
        }

        if (imageUris.isNotEmpty()) {
            val imageUriStrings = imageUris.map { it.toString() }
            val rebuiltHtml = when {
                !html.isNullOrBlank() && ClipboardHtmlParser.imageSources(html).size > 1 -> {
                    ClipboardHtmlParser.buildHtml(
                        plainText.ifBlank { ClipboardHtmlParser.plainTextFromHtml(html) },
                        imageUriStrings,
                    )
                }
                plainText.isNotBlank() || imageUris.size > 1 -> {
                    ClipboardHtmlParser.buildHtml(plainText, imageUriStrings)
                }
                else -> null
            }
            if (rebuiltHtml != null) {
                return ClipData.newHtmlText(
                    "clipboard",
                    plainText.ifBlank { " " },
                    rebuiltHtml,
                )
            }
            return buildMultiImageClip(context, payload, imageUris, plainText)
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

    private fun buildMultiImageClip(
        context: Context,
        payload: ClipboardPayload,
        imageUris: List<Uri>,
        plainText: String,
    ): ClipData? {
        val mimeType = payload.mimeType ?: "image/*"
        if (plainText.isNotBlank()) {
            return ClipData.newPlainText("clipboard", plainText).also { clip ->
                imageUris.forEach { clip.addItem(ClipData.Item(it)) }
            }
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
