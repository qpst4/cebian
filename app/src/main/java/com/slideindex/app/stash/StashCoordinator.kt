package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.overlay.FloatBallStashPanel
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

    fun pinImageFromStash(context: Context, entry: StashEntry, bitmap: Bitmap) {
        ScreenPinManager.pinFromStashImage(
            context = context,
            bitmap = bitmap,
            displayWidthPx = entry.pinDisplayWidthPx,
            displayHeightPx = entry.pinDisplayHeightPx,
        )
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
                val blocks = entry.resolvedContentBlocks()
                if (blocks.isNotEmpty()) {
                    var any = false
                    blocks.forEach { block ->
                        when (block.kind) {
                            ClipboardBlockKind.TEXT -> {
                                if (block.text.isNotBlank() && repo.addText(block.text) != null) {
                                    any = true
                                }
                            }
                            ClipboardBlockKind.IMAGE -> {
                                val bitmap = ClipboardImageStore.loadBitmap(context, block.fileName)
                                if (bitmap != null && repo.addImage(bitmap) != null) {
                                    any = true
                                }
                            }
                        }
                    }
                    any
                } else {
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
            onDone(success)
        }
    }
}
