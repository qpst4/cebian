package com.slideindex.app.search

import android.content.Context
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SearchEngineFaviconFetcher {
    suspend fun fetchAndSave(context: Context, searchLink: String): String? = withContext(Dispatchers.IO) {
        val host = extractHost(searchLink) ?: return@withContext null
        val bytes = fetchFaviconBytes(host) ?: return@withContext null
        SearchEngineIconStorage.saveIconFromBytes(context, bytes)
    }

    private fun extractHost(searchLink: String): String? {
        return runCatching {
            val normalized = searchLink.trim().let { link ->
                if (link.contains("://")) link else "https://$link"
            }
            URL(normalized).host.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun fetchFaviconBytes(host: String): ByteArray? {
        val candidates = listOf(
            "https://www.google.com/s2/favicons?domain=$host&sz=128",
            "https://$host/favicon.ico"
        )
        for (url in candidates) {
            val bytes = downloadBytes(url) ?: continue
            if (bytes.isNotEmpty() && isValidImage(bytes)) {
                return bytes
            }
        }
        return null
    }

    private fun downloadBytes(urlString: String): ByteArray? {
        return runCatching {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun isValidImage(bytes: ByteArray): Boolean {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null
    }
}
