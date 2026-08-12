package com.slideindex.app.overlay.history

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.text.TextPaint
import android.view.DragEvent
import android.view.View
import android.view.View.DragShadowBuilder
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.stash.combinedText
import com.slideindex.app.stash.resolvedContentBlocks

internal data class HistoryDragPreview(
    val text: String = "",
    val bitmap: Bitmap? = null,
)

internal object HistoryEntryDragHelper {

    fun buildClipForStashEntry(context: Context, entry: StashEntry, repo: StashRepository?): ClipData? {
        return when (entry.type) {
            StashEntryType.TEXT -> {
                val text = entry.text.orEmpty().trim()
                if (text.isBlank()) null else ClipData.newPlainText("stash", text)
            }
            StashEntryType.IMAGE -> {
                val uri = repo?.uriForFile(entry.imageFileName) ?: return null
                ClipData.newUri(context.contentResolver, "image/*", uri)
            }
            StashEntryType.RICH -> {
                val blocks = entry.resolvedContentBlocks()
                if (blocks.isEmpty()) return null
                ClipboardWriter.buildClipForBlocks(
                    htmlText = entry.htmlText,
                    blocks = blocks,
                    resolveDataUri = { fileName -> repo?.dataUriForFile(fileName) },
                    resolveContentUri = { fileName -> repo?.uriForFile(fileName) },
                    resolveDimensions = { fileName -> repo?.imageDimensions(fileName) },
                )
            }
        }
    }

    fun previewForClipboardEntry(entry: ClipboardEntry, thumbnails: List<Bitmap>): HistoryDragPreview {
        val text = entry.text.trim().ifBlank { entry.resolvedContentBlocks().joinToString("\n") { block ->
            block.text.trim()
        }.trim() }
        val bitmap = thumbnails.firstOrNull()
        return HistoryDragPreview(text = text, bitmap = bitmap)
    }

    fun previewForStashEntry(
        entry: StashEntry,
        singleThumb: Bitmap?,
        richThumbnails: List<Bitmap>,
    ): HistoryDragPreview {
        val text = when (entry.type) {
            StashEntryType.TEXT -> entry.text.orEmpty()
            StashEntryType.RICH -> entry.combinedText()
            else -> ""
        }
        val bitmap = when (entry.type) {
            StashEntryType.IMAGE -> singleThumb
            StashEntryType.RICH -> richThumbnails.firstOrNull()
            else -> null
        }
        return HistoryDragPreview(text = text, bitmap = bitmap)
    }

    fun startDrag(
        view: View,
        clipData: ClipData,
        preview: HistoryDragPreview,
        onDragStart: () -> Unit,
        onDragEnd: () -> Unit,
    ) {
        view.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> onDragStart()
                DragEvent.ACTION_DRAG_ENDED -> {
                    onDragEnd()
                    view.setOnDragListener(null)
                }
            }
            true
        }
        val flags = buildDragFlags(clipData)
        view.startDragAndDrop(clipData, EntryDragShadowBuilder(view, preview), null, flags)
    }

    private fun buildDragFlags(clipData: ClipData): Int {
        var flags = View.DRAG_FLAG_GLOBAL
        for (index in 0 until clipData.itemCount) {
            if (clipData.getItemAt(index).uri != null) {
                flags = flags or View.DRAG_FLAG_GLOBAL_URI_READ
                break
            }
        }
        return flags
    }
}

private class EntryDragShadowBuilder(
    view: View,
    private val preview: HistoryDragPreview,
) : DragShadowBuilder(view) {
    private val density = view.resources.displayMetrics.density
    private val hasBitmap = preview.bitmap != null
    private val width = (density * if (hasBitmap) 160 else 180).toInt()
    private val height = (density * if (hasBitmap) 108 else 72).toInt()

    private val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(17, 24, 39)
        textSize = view.resources.displayMetrics.scaledDensity * 14
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val bitmap = preview.bitmap
        if (bitmap != null) {
            val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val dstRatio = width.toFloat() / height.toFloat()
            val src = if (srcRatio > dstRatio) {
                val srcWidth = (bitmap.height * dstRatio).toInt()
                val left = (bitmap.width - srcWidth) / 2
                android.graphics.Rect(left, 0, left + srcWidth, bitmap.height)
            } else {
                val srcHeight = (bitmap.width / dstRatio).toInt()
                val top = (bitmap.height - srcHeight) / 2
                android.graphics.Rect(0, top, bitmap.width, top + srcHeight)
            }
            canvas.drawBitmap(bitmap, src, RectF(0f, 0f, width.toFloat(), height.toFloat()), imagePaint)
            return
        }

        val normalized = preview.text.replace(Regex("\\s+"), " ").take(84)
        val firstLine = normalized.take(24)
        val secondLine = normalized.drop(24).take(24)
        val thirdLine = normalized.drop(48).take(24)
        val baseY = contentPaint.textSize
        canvas.drawText(firstLine, 0f, baseY, contentPaint)
        if (secondLine.isNotBlank()) {
            canvas.drawText(secondLine, 0f, baseY + contentPaint.textSize + 8, contentPaint)
        }
        if (thirdLine.isNotBlank()) {
            canvas.drawText(thirdLine, 0f, baseY + (contentPaint.textSize + 8) * 2, contentPaint)
        }
    }
}
