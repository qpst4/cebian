package com.slideindex.app.imageeditor

import android.graphics.Bitmap

object ImageEditorLaunchCache {
    @Volatile
    private var pendingBitmap: Bitmap? = null

    fun put(bitmap: Bitmap) {
        pendingBitmap?.takeIf { !it.isRecycled }?.recycle()
        pendingBitmap = bitmap
    }

    fun take(): Bitmap? {
        val bitmap = pendingBitmap
        pendingBitmap = null
        return bitmap
    }

    fun clear() {
        pendingBitmap?.takeIf { !it.isRecycled }?.recycle()
        pendingBitmap = null
    }
}
