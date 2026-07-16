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
    val activeSource: PickResultTextSource = PickResultTextSource.A11Y,
    /** True when an OCR model was installed and recognition was attempted for this pick. */
    val ocrAvailable: Boolean = false,
) {
    val text: String?
        get() = textFor(activeSource)

    fun textFor(source: PickResultTextSource): String? {
        return when (source) {
            PickResultTextSource.A11Y -> a11yText?.takeIf { it.isNotBlank() } ?: ocrText?.takeIf { it.isNotBlank() }
            PickResultTextSource.OCR -> ocrText?.takeIf { it.isNotBlank() } ?: a11yText?.takeIf { it.isNotBlank() }
        }
    }

    fun canToggleSource(): Boolean = ocrAvailable
}
