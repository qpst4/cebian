package com.slideindex.app.search

import android.graphics.Bitmap

internal object ImageSearchBitmapUtils {
    fun resizeForUpload(source: Bitmap, maxLength: Int = 1280): Bitmap {
        if (source.width <= maxLength && source.height <= maxLength) return source
        val aspectRatio = source.width.toDouble() / source.height.toDouble()
        val targetWidth = if (aspectRatio >= 1) maxLength else (maxLength * aspectRatio).toInt()
        val targetHeight = if (aspectRatio < 1) maxLength else (maxLength / aspectRatio).toInt()
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
