package com.slideindex.app.imageeditor

import android.graphics.Rect
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import kotlin.math.roundToInt

object ImageEditorPinMeta {
    fun applyCropToPinMeta(
        screenRect: Rect?,
        layoutMeta: ScreenshotLayoutMeta?,
        cropRectInBitmap: Rect,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Pair<Rect?, ScreenshotLayoutMeta?> {
        val bw = bitmapWidth.coerceAtLeast(1)
        val bh = bitmapHeight.coerceAtLeast(1)
        val cw = cropRectInBitmap.width().coerceAtLeast(1)
        val ch = cropRectInBitmap.height().coerceAtLeast(1)

        val updatedMeta = when (layoutMeta) {
            null -> ScreenshotLayoutMeta(
                screenWidth = bw,
                screenHeight = bh,
                captureWidth = cw,
                captureHeight = ch,
            )
            else -> layoutMeta.copy(
                captureWidth = cw,
                captureHeight = ch,
            )
        }

        val updatedRect = screenRect?.let { rect ->
            if (rect.isEmpty) return@let null
            val left = rect.left + rect.width() * (cropRectInBitmap.left.toFloat() / bw)
            val top = rect.top + rect.height() * (cropRectInBitmap.top.toFloat() / bh)
            val right = rect.left + rect.width() * (cropRectInBitmap.right.toFloat() / bw)
            val bottom = rect.top + rect.height() * (cropRectInBitmap.bottom.toFloat() / bh)
            Rect(
                left.roundToInt(),
                top.roundToInt(),
                right.roundToInt().coerceAtLeast(left.roundToInt() + 1),
                bottom.roundToInt().coerceAtLeast(top.roundToInt() + 1),
            )
        }

        return updatedRect to if (updatedRect != null) null else updatedMeta
    }
}
