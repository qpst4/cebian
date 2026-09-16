package com.slideindex.app.imageeditor

import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.overlay.ScreenshotLayoutMeta

data class ImageEditorLaunchPayload(
    val bitmap: Bitmap,
    val screenRect: Rect?,
    val layoutMeta: ScreenshotLayoutMeta?,
    val pickReturnContext: ImageEditorPickReturnContext? = null,
)

object ImageEditorLaunchCache {
    @Volatile
    private var pending: ImageEditorLaunchPayload? = null

    fun put(
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ) {
        pending?.bitmap?.takeIf { !it.isRecycled && it !== bitmap }?.recycle()
        pending?.pickReturnContext?.recycleImageCopies()
        pending = ImageEditorLaunchPayload(
            bitmap = bitmap,
            screenRect = screenRect?.let { Rect(it) },
            layoutMeta = layoutMeta?.copy(),
            pickReturnContext = pickReturnContext,
        )
    }

    fun take(): ImageEditorLaunchPayload? {
        val payload = pending
        pending = null
        return payload
    }

    fun clear() {
        pending?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        pending?.pickReturnContext?.recycleImageCopies()
        pending = null
    }
}
