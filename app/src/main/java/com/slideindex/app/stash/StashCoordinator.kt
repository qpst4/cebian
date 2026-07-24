package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.ScreenPinManager
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object StashCoordinator {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun addText(text: String, onDone: (Boolean) -> Unit = {}) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        scope.launch {
            onDone(repo.addText(text) != null)
        }
    }

    fun addImage(
        bitmap: Bitmap,
        pinDisplayWidthPx: Int? = null,
        pinDisplayHeightPx: Int? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        if (copy == null) {
            onDone(false)
            return
        }
        scope.launch {
            onDone(
                repo.addImage(
                    bitmap = copy,
                    pinDisplayWidthPx = pinDisplayWidthPx,
                    pinDisplayHeightPx = pinDisplayHeightPx,
                ) != null,
            )
        }
    }

    fun addRich(
        parts: List<StashRichPart>,
        htmlText: String? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copied = parts.mapNotNull { part ->
            when (part) {
                is StashRichPart.Text -> part
                is StashRichPart.Image -> {
                    val copy = part.bitmap.copy(part.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        ?: return@mapNotNull null
                    StashRichPart.Image(copy)
                }
            }
        }
        if (copied.isEmpty()) {
            onDone(false)
            return
        }
        scope.launch {
            onDone(repo.addRich(copied, htmlText) != null)
        }
    }

    fun pinImageFromStash(context: Context, entry: StashEntry, bitmap: Bitmap) {
        ScreenPinManager.pinFromStashImage(
            context = context,
            bitmap = bitmap,
            displayWidthPx = entry.pinDisplayWidthPx,
            displayHeightPx = entry.pinDisplayHeightPx,
        )
    }

    fun pinRichFromStash(context: Context, entry: StashEntry) {
        ScreenPinManager.pinStashRich(context, entry)
    }

    fun copyStashEntry(context: Context, entry: StashEntry): Boolean {
        val repo = StashAccess.repository
        return when (entry.type) {
            StashEntryType.TEXT -> {
                val text = entry.text.orEmpty()
                if (text.isBlank()) return false
                FloatBallTextPick.copyText(context, text)
                true
            }
            StashEntryType.IMAGE -> {
                val bitmap = repo?.loadImage(entry) ?: return false
                FloatBallTextPick.copyImage(context, bitmap)
                true
            }
            StashEntryType.RICH -> {
                val blocks = entry.resolvedContentBlocks()
                if (blocks.isEmpty()) return false
                ClipboardWriter.writeBlocks(
                    context = context,
                    blocks = blocks,
                    htmlText = entry.htmlText,
                    resolveDataUri = { fileName -> repo?.dataUriForFile(fileName) },
                    resolveContentUri = { fileName -> repo?.uriForFile(fileName) },
                    resolveDimensions = { fileName -> repo?.imageDimensions(fileName) },
                )
            }
        }
    }

    fun openStashPanel(context: Context) {
        FloatBallStashPanel.show(context)
    }

    fun pinTextToScreen(context: Context, text: String) {
        ScreenPinManager.pinText(context, text)
    }

    fun pinImageToScreen(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
    ) {
        ScreenPinManager.pinImage(context, bitmap, screenRect, layoutMeta)
    }

    fun pinRichFromClipboard(context: Context, entry: ClipboardEntry) {
        ScreenPinManager.pinClipboardEntry(context, entry)
    }

    fun addFromClipboard(context: Context, entry: ClipboardEntry, onDone: (Boolean) -> Unit = {}) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                val blocks = entry.resolvedContentBlocks().filter { block ->
                    when (block.kind) {
                        ClipboardBlockKind.TEXT -> block.text.isNotBlank()
                        ClipboardBlockKind.IMAGE -> block.fileName.isNotBlank()
                    }
                }
                when {
                    blocks.size > 1 -> {
                        val parts = blocks.mapNotNull { block ->
                            when (block.kind) {
                                ClipboardBlockKind.TEXT -> StashRichPart.Text(block.text)
                                ClipboardBlockKind.IMAGE -> {
                                    val bitmap = ClipboardImageStore.loadBitmap(context, block.fileName)
                                        ?: return@mapNotNull null
                                    StashRichPart.Image(bitmap)
                                }
                            }
                        }
                        parts.isNotEmpty() && repo.addRich(parts, entry.htmlText) != null
                    }
                    blocks.size == 1 -> {
                        val only = blocks.first()
                        when (only.kind) {
                            ClipboardBlockKind.TEXT -> repo.addText(only.text) != null
                            ClipboardBlockKind.IMAGE -> {
                                val bitmap = ClipboardImageStore.loadBitmap(context, only.fileName)
                                bitmap != null && repo.addImage(bitmap) != null
                            }
                        }
                    }
                    else -> {
                        val text = entry.text.trim()
                        when {
                            text.isNotEmpty() -> repo.addText(text) != null
                            entry.hasImageContent() -> {
                                val bitmap = ClipboardImageStore.loadEntryThumbnail(context, entry)
                                bitmap != null && repo.addImage(bitmap) != null
                            }
                            else -> false
                        }
                    }
                }
            }
            onDone(success)
        }
    }
}
