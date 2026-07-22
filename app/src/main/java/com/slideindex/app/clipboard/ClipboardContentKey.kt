package com.slideindex.app.clipboard



object ClipboardContentKey {

    fun forPayload(payload: ClipboardPayload): String = forFields(

        type = payload.type,

        text = payload.text,

        uri = payload.uri,

        intentUri = payload.intentUri,

        htmlText = payload.htmlText,

        mimeType = payload.mimeType,

        imageUris = payload.resolvedImageUris(),

    )



    fun forEntry(entry: ClipboardEntry): String = forFields(

        type = entry.type,

        text = entry.text,

        uri = entry.uri,

        intentUri = entry.intentUri,

        htmlText = entry.htmlText,

        mimeType = entry.mimeType,

        imageUris = entry.uri?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),

    )



    private fun forFields(

        type: ClipboardEntryType,

        text: String,

        uri: String?,

        intentUri: String?,

        htmlText: String?,

        mimeType: String?,

        imageUris: List<String>,

    ): String {

        val normalizedText = textForKey(text)

        val imageIdentity = stableImageIdentity(uri, htmlText, mimeType, imageUris)

        return buildString {

            append(

                when (type) {

                    ClipboardEntryType.TEXT -> "t"

                    ClipboardEntryType.URI -> "u"

                    ClipboardEntryType.INTENT -> "i"

                    ClipboardEntryType.HTML -> "h"

                },

            )

            append(':')

            append(normalizedText)

            imageIdentity?.let { append("|img:").append(it) }

            intentUri?.let { append("|i:").append(it) }

            uri?.takeIf { isStableUri(it) && imageIdentity == null }?.let { append("|u:").append(it) }

            if (type == ClipboardEntryType.HTML && imageIdentity == null && !htmlText.isNullOrBlank()) {

                append("|h:").append(htmlText.hashCode())

            }

        }

    }



    private fun textForKey(text: String): String {

        val trimmed = text.trim()

        if (trimmed.startsWith("content://", ignoreCase = true) ||

            trimmed.startsWith("file://", ignoreCase = true)

        ) {

            return ""

        }

        return trimmed

    }



    private fun stableImageIdentity(

        uri: String?,

        htmlText: String?,

        mimeType: String?,

        imageUris: List<String>,

    ): String? {

        val htmlSources = htmlText?.let { ClipboardHtmlParser.imageSources(it) }.orEmpty()

        val allSources = (imageUris + htmlSources).distinct()

        if (allSources.isNotEmpty()) {

            return allSources.joinToString("|") { normalizeImageIdentity(it) }

        }

        uri?.takeIf { isStableUri(it) }?.let { return it }

        if (mimeType?.startsWith("image/") == true && !htmlText.isNullOrBlank()) {

            return "html-image:${htmlText.hashCode()}"

        }

        return null

    }



    private fun normalizeImageIdentity(src: String): String {

        val normalized = ClipboardHtmlParser.normalizeImageSrc(src)

        return when {

            normalized.startsWith("http://", ignoreCase = true) ||

                normalized.startsWith("https://", ignoreCase = true) -> normalized

            normalized.startsWith("data:image/", ignoreCase = true) ->

                "data:${normalized.length}:${normalized.hashCode()}"

            else -> normalized

        }

    }



    private fun isStableUri(uri: String): Boolean =

        uri.startsWith("http://", ignoreCase = true) ||

            uri.startsWith("https://", ignoreCase = true)

}

