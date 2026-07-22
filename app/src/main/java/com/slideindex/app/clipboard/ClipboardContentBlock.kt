package com.slideindex.app.clipboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClipboardBlockKind {
    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,
}

@Serializable
data class ClipboardContentBlock(
    val kind: ClipboardBlockKind,
    val text: String = "",
    val fileName: String = "",
) {
    companion object {
        fun text(value: String): ClipboardContentBlock =
            ClipboardContentBlock(kind = ClipboardBlockKind.TEXT, text = value)

        fun image(fileName: String): ClipboardContentBlock =
            ClipboardContentBlock(kind = ClipboardBlockKind.IMAGE, fileName = fileName)
    }
}

fun ClipboardEntry.resolvedContentBlocks(): List<ClipboardContentBlock> {
    if (contentBlocks.isNotEmpty()) return contentBlocks
    return ClipboardBlockParser.buildBlocks(
        text = text,
        htmlText = htmlText,
        imageFileNames = resolvedImageFileNames(),
        imageSources = ClipboardImageStore.collectImageSourcesForEntry(this),
    )
}

fun ClipboardEntry.hasRichPinContent(): Boolean =
    resolvedContentBlocks().any {
        when (it.kind) {
            ClipboardBlockKind.TEXT -> it.text.isNotBlank()
            ClipboardBlockKind.IMAGE -> it.fileName.isNotBlank()
        }
    }

fun ClipboardEntry.shouldOfferExpand(): Boolean {
    val blocks = resolvedContentBlocks()
    if (blocks.size > 1) return true
    val onlyText = blocks.singleOrNull()?.kind == ClipboardBlockKind.TEXT
    if (onlyText && blocks.first().text.length > 120) return true
    return hasImageContent() && text.trim().isNotEmpty() && text.trim() != uri
}
