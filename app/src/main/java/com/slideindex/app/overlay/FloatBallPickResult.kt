package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.graphics.Rect

enum class PickResultTextSource {
    A11Y,
    OCR,
}

data class FloatBallPickResult(
    val a11yText: String?,
    val ocrText: String?,
    val screenshot: Bitmap?,
    val screenRect: Rect?,
    val layoutMeta: ScreenshotLayoutMeta? = null,
    val activeSource: PickResultTextSource = PickResultTextSource.A11Y,
    /** True when an OCR model was installed and recognition was attempted for this pick. */
    val ocrAvailable: Boolean = false,
    /** OCR is running asynchronously after the panel was shown. */
    val ocrPending: Boolean = false,
    /** When async OCR completes, switch the visible tab to OCR (regional screenshot). */
    val ocrPreferSwitchOnComplete: Boolean = false,
    /** False for external share-image OCR (no accessibility source). */
    val a11ySourceEnabled: Boolean = true,
    /** Shared image OCR session; enables background processing while pending. */
    val isShareImageOcr: Boolean = false,
) {
    val text: String?
        get() = textFor(activeSource)

    fun textFor(source: PickResultTextSource): String? {
        return when (source) {
            PickResultTextSource.A11Y -> a11yText?.takeIf { it.isNotBlank() }
            PickResultTextSource.OCR -> ocrText?.takeIf { it.isNotBlank() }
        }
    }

    fun hasA11ySource(): Boolean = !a11yText.isNullOrBlank()

    fun canToggleSource(): Boolean = hasA11ySource() && ocrAvailable
}
