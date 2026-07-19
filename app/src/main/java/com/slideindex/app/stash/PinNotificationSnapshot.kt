package com.slideindex.app.stash

import android.graphics.Rect
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import kotlinx.serialization.Serializable

@Serializable
data class PinNotificationSnapshot(
    val type: String,
    val text: String? = null,
    val x: Int,
    val y: Int,
    val expandedWidthPx: Int = 0,
    val expandedHeightPx: Int = 0,
    val screenRectLeft: Int? = null,
    val screenRectTop: Int? = null,
    val screenRectRight: Int? = null,
    val screenRectBottom: Int? = null,
    val layoutScreenWidth: Int? = null,
    val layoutScreenHeight: Int? = null,
    val layoutCaptureWidth: Int? = null,
    val layoutCaptureHeight: Int? = null,
) {
    fun toScreenRect(): Rect? {
        val left = screenRectLeft ?: return null
        val top = screenRectTop ?: return null
        val right = screenRectRight ?: return null
        val bottom = screenRectBottom ?: return null
        return Rect(left, top, right, bottom)
    }

    fun toLayoutMeta(): ScreenshotLayoutMeta? {
        val screenWidth = layoutScreenWidth ?: return null
        val screenHeight = layoutScreenHeight ?: return null
        val captureWidth = layoutCaptureWidth ?: return null
        val captureHeight = layoutCaptureHeight ?: return null
        return ScreenshotLayoutMeta(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            captureWidth = captureWidth,
            captureHeight = captureHeight,
        )
    }

    companion object {
        const val TYPE_IMAGE = "IMAGE"
        const val TYPE_TEXT = "TEXT"
    }
}
