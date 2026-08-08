package com.slideindex.app.ocr

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OcrModelHttpDownloader(
    private val client: OkHttpClient,
) {
    suspend fun downloadFileWithFallback(
        urls: List<String>,
        output: File,
        relativePath: String,
        onProgress: suspend (Long) -> Unit,
    ) {
        val minBytes = OcrModelDownloadSupport.minBytesFor(relativePath)
        var lastError: Throwable? = null
        var lastUrl: String? = null
        for (url in urls.distinct()) {
            try {
                if (lastUrl != null && lastUrl != url && output.exists()) {
                    output.delete()
                }
                lastUrl = url
                if (!output.isFile || output.length() < minBytes) {
                    downloadFile(
                        url = url,
                        output = output,
                        relativePath = relativePath,
                        onProgress = onProgress,
                    )
                }
                val size = output.length()
                if (output.isFile && size >= minBytes) return
                lastError = IOException("download_too_small:${size}:$url")
                if (output.exists() && size < minBytes) output.delete()
            } catch (error: Throwable) {
                val size = if (output.exists()) output.length() else 0L
                if (output.isFile && size >= minBytes) return
                lastError = error
                if (output.exists() && size < minBytes) output.delete()
            }
        }
        throw IOException(
            "download_failed:${lastError?.message ?: "unknown"}",
            lastError,
        )
    }

    suspend fun downloadFile(
        url: String,
        output: File,
        relativePath: String,
        onProgress: suspend (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        output.parentFile?.mkdirs()
        var existing = if (output.exists()) output.length() else 0L
        val minBytes = OcrModelDownloadSupport.minBytesFor(relativePath)
        if (existing > 0L && existing < minBytes) {
            output.delete()
            existing = 0L
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DOWNLOAD_USER_AGENT)
            .header("Accept", "application/octet-stream,*/*")
            .apply {
                if (url.contains("modelscope.cn", ignoreCase = true)) {
                    header("Referer", "https://www.modelscope.cn/")
                }
                if (existing > 0L) {
                    header("Range", "bytes=$existing-")
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val writeMode = OcrModelDownloadSupport.partialWriteMode(response.code, existing)
                ?: throw IOException("http_${response.code}:$url")
            if (writeMode.resetExisting && output.exists()) {
                output.delete()
                existing = 0L
            }
            val append = writeMode.append

            val body = response.body
            val remaining = body.contentLength()
            val total = when {
                remaining > 0L -> existing + remaining
                else -> -1L
            }

            body.byteStream().use { input ->
                FileOutputStream(output, append).use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var fileDownloaded = existing
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        stream.write(buffer, 0, read)
                        fileDownloaded += read
                        onProgress(fileDownloaded)
                    }
                }
            }

            val actualLength = output.length()
            if (!output.isFile || actualLength < minBytes) {
                output.delete()
                throw IOException("download_too_small:${actualLength}:$url")
            }
            if (!append && total > 0L && actualLength != total) {
                output.delete()
                throw IOException("download_size_mismatch:expected=$total,actual=$actualLength:$url")
            }
        }
    }

    private companion object {
        private const val DOWNLOAD_USER_AGENT =
            "SlideIndex/1.0 (Android; okhttp) ModelDownloader"
    }
}
