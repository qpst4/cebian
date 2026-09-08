package com.slideindex.app.ocr

sealed class OcrRecognizeResult {
    data class Success(val text: String) : OcrRecognizeResult()
    data class Failure(val reason: String) : OcrRecognizeResult()

    fun textOrNull(): String? = (this as? Success)?.text?.trim()?.takeIf { it.isNotEmpty() }
}
