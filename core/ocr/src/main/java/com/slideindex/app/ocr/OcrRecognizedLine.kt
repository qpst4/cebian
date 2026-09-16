package com.slideindex.app.ocr

import android.graphics.Rect

data class OcrRecognizedLine(
    val bounds: Rect,
    val text: String,
)
