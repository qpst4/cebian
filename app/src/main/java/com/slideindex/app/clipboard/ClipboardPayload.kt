package com.slideindex.app.clipboard

data class ClipboardPayload(
    val type: ClipboardEntryType,
    val text: String,
    val uri: String? = null,
    val intentUri: String? = null,
    val htmlText: String? = null,
    val mimeType: String? = null,
) {
    fun contentKey(): String = when (type) {
        ClipboardEntryType.TEXT -> "t:$text"
        ClipboardEntryType.URI -> "u:${uri ?: text}"
        ClipboardEntryType.INTENT -> "i:${intentUri ?: text}"
        ClipboardEntryType.HTML -> "h:${htmlText ?: text}"
    }

    fun toEntry(id: String, createdAtEpochMs: Long): ClipboardEntry = ClipboardEntry(
        id = id,
        type = type,
        text = text,
        uri = uri,
        intentUri = intentUri,
        htmlText = htmlText,
        mimeType = mimeType,
        createdAtEpochMs = createdAtEpochMs,
    )
}
