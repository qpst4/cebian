package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
            ),
        )
    }

    fun writePayload(context: Context, payload: ClipboardPayload) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = when (payload.type) {
            ClipboardEntryType.TEXT -> ClipData.newPlainText("clipboard", payload.text)
            ClipboardEntryType.URI -> {
                val uri = payload.uri?.toUri() ?: return
                ClipData.newUri(
                    context.contentResolver,
                    payload.mimeType ?: "text/*",
                    uri,
                )
            }
            ClipboardEntryType.INTENT -> {
                val intentUri = payload.intentUri ?: return
                val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                ClipData.newIntent("clipboard", intent)
            }
            ClipboardEntryType.HTML -> {
                val html = payload.htmlText ?: payload.text
                ClipData.newHtmlText("clipboard", payload.text, html)
            }
        }
        clipboard.setPrimaryClip(clip)
    }
}
