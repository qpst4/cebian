package com.slideindex.app.update

import com.slideindex.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object UpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/qpst4/cebian/releases/latest"
    private const val TIMEOUT_MS = 5000
    private const val HTTP_TOO_MANY_REQUESTS = 429

    private val USER_AGENT = "cebian/${BuildConfig.VERSION_NAME} (Android)"
    private val json = Json { ignoreUnknownKeys = true }

    const val RELEASES_PAGE_URL = "https://github.com/qpst4/cebian/releases"

    sealed interface FetchResult {
        data class Success(val release: GithubRelease) : FetchResult
        data class RateLimited(val resetEpochSeconds: Long) : FetchResult
        data object Failed : FetchResult
    }

    suspend fun fetchLatestRelease(): FetchResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    return@withContext FetchResult.Success(json.decodeFromString(body))
                }
                HttpURLConnection.HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> {
                    val remaining = conn.getHeaderField("X-RateLimit-Remaining")
                    val reset = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull() ?: 0L
                    if (conn.responseCode == HTTP_TOO_MANY_REQUESTS || remaining == "0") {
                        return@withContext FetchResult.RateLimited(reset)
                    }
                }
            }
            FetchResult.Failed
        } catch (_: Exception) {
            FetchResult.Failed
        } finally {
            conn?.disconnect()
        }
    }

    fun isRemoteNewer(remoteTag: String, localName: String): Boolean {
        val remote = parseVersion(remoteTag)
        val local = parseVersion(localName)
        val size = maxOf(remote.size, local.size)
        for (i in 0 until size) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(".")
            .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun pickApkAsset(release: GithubRelease): GithubRelease.Asset? =
        release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    fun displayVersion(raw: String): String {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        return "v$trimmed"
    }
}
