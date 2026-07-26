package com.slideindex.app.ocr

import java.io.File
import java.io.IOException

internal object OcrModelDownloadSupport {

    fun resolveDownloadUrls(mirrorUrls: List<String>, primaryUrl: String): List<String> =
        buildList {
            addAll(mirrorUrls)
            add(primaryUrl)
        }.distinct()
            .filterNot { it.contains("huggingface.co", ignoreCase = true) }
            .ifEmpty { listOf(primaryUrl) }

    fun minBytesFor(relativePath: String): Long = when {
        relativePath.endsWith(".onnx", ignoreCase = true) -> MIN_ONNX_FILE_BYTES
        relativePath.endsWith(".traineddata", ignoreCase = true) -> MIN_TRAINEDDATA_FILE_BYTES
        else -> 1L
    }

    fun partialWriteMode(httpCode: Int, existingBytes: Long): PartialWriteMode? = when (httpCode) {
        206 -> PartialWriteMode(append = true, resetExisting = false)
        200 -> PartialWriteMode(
            append = false,
            resetExisting = existingBytes > 0L,
        )
        in 200..299 -> PartialWriteMode(append = false, resetExisting = false)
        else -> null
    }

    fun finalizeDownloadedFile(partial: File, target: File) {
        if (!partial.isFile || partial.length() <= 0L) {
            throw IOException("partial_missing:${partial.absolutePath}")
        }
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        if (!target.isFile || target.length() <= 0L) {
            target.delete()
            throw IOException("finalize_failed:${target.absolutePath}")
        }
    }

    data class PartialWriteMode(
        val append: Boolean,
        val resetExisting: Boolean,
    )

    private const val MIN_ONNX_FILE_BYTES = 64L * 1024L
    private const val MIN_TRAINEDDATA_FILE_BYTES = 512L * 1024L
}
