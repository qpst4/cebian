package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

object ClipboardReader {
    fun read(context: Context): ClipboardPayload? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        val description = clip.description
        for (index in 0 until clip.itemCount) {
            val payload = parseItem(context, clip.getItemAt(index), description, index)
            if (payload != null) return payload
        }
        return null
    }

    private fun parseItem(
        context: Context,
        item: ClipData.Item,
        description: ClipDescription?,
        index: Int,
    ): ClipboardPayload? {
        val text = item.text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            return ClipboardPayload(
                type = ClipboardEntryType.TEXT,
                text = text,
            )
        }
        val uri = item.uri
        if (uri != null) {
            val mimeType = description?.getMimeType(index)
            val display = item.coerceToText(context)?.toString()?.trim() ?: uri.toString()
            return ClipboardPayload(
                type = ClipboardEntryType.URI,
                text = display,
                uri = uri.toString(),
                mimeType = mimeType,
            )
        }
        val intent = item.intent
        if (intent != null) {
            val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
            val display = item.coerceToText(context)?.toString()?.trim() ?: intentUri
            return ClipboardPayload(
                type = ClipboardEntryType.INTENT,
                text = display,
                intentUri = intentUri,
            )
        }
        val htmlText = item.htmlText?.trim()
        if (!htmlText.isNullOrBlank()) {
            val plain = item.coerceToText(context)?.toString()?.trim() ?: htmlText
            return ClipboardPayload(
                type = ClipboardEntryType.HTML,
                text = plain,
                htmlText = htmlText,
            )
        }
        val styled = item.coerceToStyledText(context)?.toString()?.trim()
        if (!styled.isNullOrBlank()) {
            return ClipboardPayload(
                type = ClipboardEntryType.TEXT,
                text = styled,
            )
        }
        val coerced = item.coerceToText(context)?.toString()?.trim()
        if (!coerced.isNullOrBlank()) {
            return ClipboardPayload(
                type = ClipboardEntryType.TEXT,
                text = coerced,
            )
        }
        return null
    }
}
