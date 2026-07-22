package com.slideindex.app.clipboard

object ClipboardContentEquivalence {

    fun fingerprint(payload: ClipboardPayload): String =
        fingerprintBlocks(blocksFromPayload(payload))

    fun fingerprint(entry: ClipboardEntry): String =
        fingerprintBlocks(entry.resolvedContentBlocks())

    fun matches(entry: ClipboardEntry, payload: ClipboardPayload): Boolean {
        if (fingerprint(entry) == fingerprint(payload)) return true
        return looselyMatches(entry, payload)
    }

    fun looselyMatches(entry: ClipboardEntry, payload: ClipboardPayload): Boolean {
        val entryText = normalizedPlainText(entry.text, entry.htmlText)
        val payloadText = normalizedPlainText(payload.text, payload.htmlText)
        if (entryText != payloadText) return false
        return imageCountForEntry(entry) == rawImageCount(payload)
    }

    private fun blocksFromPayload(payload: ClipboardPayload): List<ClipboardContentBlock> {
        val rawHtmlSources = payload.htmlText
            ?.let { ClipboardHtmlParser.imageSources(it) }
            .orEmpty()
        val imageUris = payload.resolvedImageUris()
        val imageCount = maxOf(
            rawHtmlSources.size,
            imageUris.size,
            payload.resolvedImageFileNames().size,
        )
        val sources = when {
            rawHtmlSources.isNotEmpty() -> rawHtmlSources
            imageUris.isNotEmpty() -> imageUris
            else -> emptyList()
        }
        return ClipboardBlockParser.buildBlocks(
            text = payload.text,
            htmlText = payload.htmlText,
            imageFileNames = List(imageCount) { "" },
            imageSources = sources,
        )
    }

    private fun fingerprintBlocks(blocks: List<ClipboardContentBlock>): String {
        if (blocks.isEmpty()) return ""
        val combinedText = blocks
            .filter { it.kind == ClipboardBlockKind.TEXT }
            .joinToString("\n") { it.text.trim() }
            .trim()
        val structure = blocks.joinToString("") { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> "T"
                ClipboardBlockKind.IMAGE -> "I"
            }
        }
        val imageCount = blocks.count { it.kind == ClipboardBlockKind.IMAGE }
        return "$combinedText|$structure|n:$imageCount"
    }

    private fun imageCountForEntry(entry: ClipboardEntry): Int {
        val fromBlocks = entry.resolvedContentBlocks().count { it.kind == ClipboardBlockKind.IMAGE }
        val fromFiles = entry.resolvedImageFileNames().size
        val fromHtml = entry.htmlText?.let { ClipboardHtmlParser.imageSources(it).size } ?: 0
        return maxOf(fromBlocks, fromFiles, fromHtml)
    }

    private fun rawImageCount(payload: ClipboardPayload): Int {
        val fromHtml = payload.htmlText?.let { ClipboardHtmlParser.imageSources(it).size } ?: 0
        val fromUris = payload.resolvedImageUris().size
        val fromFiles = payload.resolvedImageFileNames().size
        return maxOf(fromHtml, fromUris, fromFiles)
    }

    private fun normalizedPlainText(plainText: String, htmlText: String?): String {
        val fromHtml = htmlText?.let { ClipboardHtmlParser.plainTextFromHtml(it).trim() }.orEmpty()
        return (fromHtml.ifBlank { plainText }).trim()
    }
}
