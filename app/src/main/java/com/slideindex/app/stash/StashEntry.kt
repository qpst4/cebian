package com.slideindex.app.stash

import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import kotlinx.serialization.Serializable

@Serializable
enum class StashEntryType {
    TEXT,
    IMAGE,
    RICH,
}

@Serializable
data class StashEntry(
    val id: String,
    val type: StashEntryType,
    val text: String? = null,
    val imageFileName: String? = null,
    val contentBlocks: List<ClipboardContentBlock> = emptyList(),
    val htmlText: String? = null,
    val createdAtEpochMs: Long,
    val starred: Boolean = false,
    /** 钉图在屏幕上的显示宽高（逻辑像素），用于从暂存夹恢复时保持原尺寸。 */
    val pinDisplayWidthPx: Int? = null,
    val pinDisplayHeightPx: Int? = null,
)

sealed class StashRichPart {
    data class Text(val text: String) : StashRichPart()
    data class Image(val bitmap: android.graphics.Bitmap) : StashRichPart()
}

fun StashEntry.resolvedContentBlocks(): List<ClipboardContentBlock> {
    if (contentBlocks.isNotEmpty()) return contentBlocks
    return when (type) {
        StashEntryType.TEXT -> {
            val body = text?.trim().orEmpty()
            if (body.isEmpty()) emptyList() else listOf(ClipboardContentBlock.text(body))
        }
        StashEntryType.IMAGE -> {
            val fileName = imageFileName?.takeIf { it.isNotBlank() } ?: return emptyList()
            listOf(ClipboardContentBlock.image(fileName))
        }
        StashEntryType.RICH -> emptyList()
    }
}

fun StashEntry.allImageFileNames(): List<String> {
    val fromBlocks = contentBlocks
        .filter { it.kind == ClipboardBlockKind.IMAGE }
        .map { it.fileName }
        .filter { it.isNotBlank() }
    if (fromBlocks.isNotEmpty()) return fromBlocks.distinct()
    return listOfNotNull(imageFileName?.takeIf { it.isNotBlank() })
}

fun StashEntry.combinedText(): String =
    resolvedContentBlocks()
        .filter { it.kind == ClipboardBlockKind.TEXT }
        .joinToString("\n\n") { it.text.trim() }
        .trim()
        .ifBlank { text?.trim().orEmpty() }

fun StashEntry.shouldOfferExpand(): Boolean {
    return when (type) {
        StashEntryType.TEXT -> (text?.length ?: 0) > 120
        StashEntryType.IMAGE -> !imageFileName.isNullOrBlank()
        StashEntryType.RICH -> {
            val blocks = resolvedContentBlocks()
            if (blocks.size > 1) return true
            val onlyText = blocks.singleOrNull()?.kind == ClipboardBlockKind.TEXT
            if (onlyText == true && blocks.first().text.length > 120) return true
            allImageFileNames().isNotEmpty() && combinedText().isNotBlank()
        }
    }
}
