package com.slideindex.app.search

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ImageSearchPostUploader {
    private const val TAG = "ImageSearchPostUploader"
    private const val TIMEOUT_MS = 60_000
    private const val JPEG_QUALITY = 50
    private const val MAX_LENGTH = 800
    private const val BOUNDARY = "U1DCBvfRB8uKxu-pX-R-854T-dkBP8UH"
    private const val LINE_BREAK = "\r\n"

    suspend fun search(bitmap: Bitmap, engine: ImageSearchEngine): ImageSearchPostResult? =
        withContext(Dispatchers.IO) {
            require(engine.usesDirectPost) { "Engine $engine does not use direct POST upload" }
            val uploadBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            try {
                val imageBytes = compressToJpeg(uploadBitmap)
                postImage(engine, imageBytes)
            } finally {
                uploadBitmap.recycle()
            }
        }

    private fun compressToJpeg(bitmap: Bitmap): ByteArray {
        val resized = ImageSearchBitmapUtils.resizeForUpload(bitmap, maxLength = MAX_LENGTH)
        val shouldRecycleResized = resized !== bitmap
        return try {
            ByteArrayOutputStream().apply {
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, this)
            }.toByteArray()
        } finally {
            if (shouldRecycleResized) {
                resized.recycle()
            }
        }
    }

    private fun postImage(engine: ImageSearchEngine, imageBytes: ByteArray): ImageSearchPostResult? {
        val startedAt = System.currentTimeMillis()
        val postUrl = engine.postUploadUrl ?: return null
        val baseUrl = engine.webViewBaseUrl ?: return null
        val fileName = "${System.currentTimeMillis() / 1000}.jpg"
        return try {
            val connection = (URL(postUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                instanceFollowRedirects = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
            }
            DataOutputStream(connection.outputStream).use { dos ->
                writeMultipartBody(dos, engine, fileName, imageBytes)
                dos.flush()
            }
            val responseCode = connection.responseCode
            Log.d(TAG, "${engine.name} response $responseCode in ${System.currentTimeMillis() - startedAt}ms")
            if (responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP
            ) {
                val location = connection.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    Log.d(TAG, "${engine.name} redirect: $location")
                    return ImageSearchPostResult.RedirectUrl(location)
                }
            }
            val html = readResponseBody(connection)
            if (html.isNullOrBlank()) {
                Log.w(TAG, "${engine.name} empty response body")
                null
            } else {
                ImageSearchPostResult.Html(baseUrl = baseUrl, html = html)
            }
        } catch (e: Exception) {
            Log.w(TAG, "${engine.name} upload error", e)
            null
        }
    }

    private fun writeMultipartBody(
        dos: DataOutputStream,
        engine: ImageSearchEngine,
        fileName: String,
        imageBytes: ByteArray,
    ) {
        writeFileField(dos, "file", fileName, imageBytes)
        dos.writeBytes("--$BOUNDARY--$LINE_BREAK")
    }

    private fun writeFileField(
        dos: DataOutputStream,
        fieldName: String,
        fileName: String,
        imageBytes: ByteArray,
    ) {
        dos.writeBytes("--$BOUNDARY$LINE_BREAK")
        dos.writeBytes(
            "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"$LINE_BREAK",
        )
        dos.writeBytes("Content-Type: application/octet-stream$LINE_BREAK")
        dos.writeBytes(LINE_BREAK)
        dos.write(imageBytes)
        dos.writeBytes(LINE_BREAK)
    }

    private fun readResponseBody(connection: HttpURLConnection): String? {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return null
        return BufferedReader(InputStreamReader(stream)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    append(LINE_BREAK)
                    line = reader.readLine()
                }
            }.trim()
        }.takeIf { it.isNotBlank() }
    }
}
