package com.slideindex.app.inspire

import android.graphics.Rect
import com.slideindex.app.overlay.ScreenshotLayoutMeta

/**
 * GestureEVO InspireDataHolder — passes pick results from overlay to content UI.
 */
object InspireDataHolder {
    private var screenshotBitmap: ManagedBitmap? = null
    var screenshotLayoutMeta: ScreenshotLayoutMeta? = null
        private set
    var accessibilityContent: List<String>? = null
        private set
    var dragRect: Rect? = null
        private set
    var forceImageTextSelection: Boolean = false
        private set

    fun setAccessibilityContent(value: List<String>?) {
        accessibilityContent = value
    }

    fun setDragRect(rect: Rect?) {
        dragRect = rect
    }

    fun setForceImageTextSelection(value: Boolean) {
        forceImageTextSelection = value
    }

    fun replaceScreenshotBitmap(handle: ManagedBitmap?, layoutMeta: ScreenshotLayoutMeta? = null) {
        screenshotBitmap?.close()
        screenshotBitmap = null
        screenshotBitmap = handle?.acquire()
        screenshotLayoutMeta = layoutMeta
    }

    fun acquireScreenshotBitmap(): ManagedBitmap? = screenshotBitmap?.acquire()

    fun clearScreenshotBitmap() {
        screenshotBitmap?.close()
        screenshotBitmap = null
        screenshotLayoutMeta = null
    }

    fun clear() {
        clearScreenshotBitmap()
        accessibilityContent = null
        forceImageTextSelection = false
        dragRect = null
    }
}
