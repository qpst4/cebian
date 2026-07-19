package com.slideindex.app.barcode

data class BarcodeScanResult(
    val text: String,
    val format: String,
)

fun List<BarcodeScanResult>.joinDisplayText(): String =
    joinToString(separator = "\n") { it.text }
