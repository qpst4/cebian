package com.slideindex.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.slideindex.app.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApkInstaller {
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 20000
    private const val BUFFER_SIZE = 8 * 1024
    private const val UPDATE_DIR_NAME = "update"

    fun updateDir(context: Context): File {
        val base = context.externalCacheDir ?: context.cacheDir
        return File(base, UPDATE_DIR_NAME)
    }

    fun apkFileName(version: String): String {
        val safe = version.trim().ifBlank { "update" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$safe.apk"
    }

    fun apkFile(context: Context, version: String): File =
        File(updateDir(context), apkFileName(version))

    suspend fun download(
        url: String,
        destFile: File,
        expectedSize: Long,
        onProgress: (Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val partFile = File(destFile.parentFile, destFile.name + ".part")
        var conn: HttpURLConnection? = null
        try {
            destFile.parentFile?.mkdirs()
            if (isDownloaded(destFile, expectedSize)) {
                onProgress(100)
                return@withContext true
            }
            var existing = if (partFile.exists()) partFile.length() else 0L
            if (expectedSize > 0 && existing > expectedSize) {
                partFile.delete()
                existing = 0L
            }
            if (expectedSize > 0 && existing == expectedSize) {
                return@withContext finalizePart(partFile, destFile, expectedSize, onProgress)
            }

            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                if (existing > 0) {
                    setRequestProperty("Range", "bytes=$existing-")
                }
            }
            val append: Boolean
            when (conn.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> append = true
                HttpURLConnection.HTTP_OK -> {
                    append = false
                    existing = 0L
                    if (partFile.exists()) partFile.delete()
                }
                else -> return@withContext false
            }
            val remaining = conn.contentLength.toLong()
            val total = when {
                expectedSize > 0 -> expectedSize
                remaining > 0 -> existing + remaining
                else -> -1L
            }
            var downloaded = existing
            var lastPercent = -1
            conn.inputStream.use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }
            finalizePart(partFile, destFile, expectedSize, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun finalizePart(
        partFile: File,
        destFile: File,
        expectedSize: Long,
        onProgress: (Int) -> Unit,
    ): Boolean {
        if (expectedSize > 0 && partFile.length() != expectedSize) {
            partFile.delete()
            return false
        }
        if (destFile.exists()) destFile.delete()
        if (!partFile.renameTo(destFile)) {
            partFile.copyTo(destFile, overwrite = true)
            partFile.delete()
        }
        onProgress(100)
        return true
    }

    fun isDownloaded(file: File, expectedSize: Long): Boolean =
        expectedSize > 0 && file.exists() && file.length() == expectedSize

    fun clearOutdatedApks(dir: File, keepFileName: String) {
        val files = dir.listFiles() ?: return
        val keepPart = "$keepFileName.part"
        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            val isApk = name.endsWith(".apk", ignoreCase = true)
            val isPart = name.endsWith(".apk.part", ignoreCase = true)
            if ((isApk || isPart) && name != keepFileName && name != keepPart) {
                file.delete()
            }
        }
    }

    fun installApk(context: Context, file: File): Boolean =
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun gotoUnknownSourceSetting(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
            }
        }
    }
}
