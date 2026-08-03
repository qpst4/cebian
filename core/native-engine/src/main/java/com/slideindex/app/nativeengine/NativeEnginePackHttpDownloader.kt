package com.slideindex.app.nativeengine

import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class NativeEnginePackHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(60.seconds)
        .readTimeout(15.minutes)
        .writeTimeout(15.minutes)
        .build(),
) {
    suspend fun downloadFileWithFallback(
        urls: List<String>,
        output: File,
        onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                downloadFile(url, output, onProgress)
                return@withContext
            } catch (error: Throwable) {
                lastError = error
                if (output.exists()) output.delete()
            }
        }
        throw IOException("download_failed:${lastError?.message ?: "unknown"}")
    }

    private fun downloadFile(url: String, output: File, onProgress: (Long) -> Unit) {
        output.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("http_${response.code}:$url")
            }
            val body = response.body ?: throw IOException("empty_body:$url")
            val total = body.contentLength().takeIf { it > 0L }
            body.byteStream().use { input ->
                output.outputStream().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded)
                    }
                    out.flush()
                }
            }
            if (output.length() <= 0L) {
                throw IOException("download_empty:$url")
            }
            if (total != null && output.length() != total) {
                throw IOException("download_size_mismatch:expected=$total,actual=${output.length()}:$url")
            }
        }
    }
}
