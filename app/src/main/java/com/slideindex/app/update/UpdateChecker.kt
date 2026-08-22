package com.slideindex.app.update

import com.slideindex.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** One section of in-app update notes: an optional title (e.g. 新增 / 变更 / 修复) plus items. */
data class UpdateNotesGroup(
    val title: String? = null,
    val items: List<String> = emptyList(),
)

object UpdateChecker {
    private val MANIFEST_URLS = listOf(
        "https://raw.githubusercontent.com/qpst4/cebian/main/update.json",
        "https://cdn.jsdelivr.net/gh/qpst4/cebian@main/update.json",
    )
    private const val TIMEOUT_MS = 8000

    private val USER_AGENT = "cebian/${BuildConfig.VERSION_NAME} (Android)"
    private val json = Json { ignoreUnknownKeys = true }

    const val RELEASES_PAGE_URL = "https://github.com/qpst4/cebian/releases"

    sealed interface FetchResult {
        data class Success(val manifest: UpdateManifest) : FetchResult
        data object Failed : FetchResult
    }

    suspend fun fetchLatestManifest(): FetchResult = withContext(Dispatchers.IO) {
        var best: UpdateManifest? = null
        for (url in MANIFEST_URLS) {
            when (val result = fetchManifest(url)) {
                is FetchResult.Success -> {
                    best = when (val current = best) {
                        null -> result.manifest
                        else -> pickBetterManifest(current, result.manifest)
                    }
                }
                FetchResult.Failed -> Unit
            }
        }
        best?.let { FetchResult.Success(it) } ?: FetchResult.Failed
    }

    private fun fetchManifest(url: String): FetchResult {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return FetchResult.Failed
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString<UpdateManifest>(body)
            if (manifest.version.isBlank() || manifest.apkUrl.isBlank()) {
                return FetchResult.Failed
            }
            return FetchResult.Success(manifest)
        } catch (_: Exception) {
            return FetchResult.Failed
        } finally {
            conn?.disconnect()
        }
    }

    internal fun pickBetterManifest(current: UpdateManifest, candidate: UpdateManifest): UpdateManifest =
        when {
            isRemoteNewer(candidate.version, current.version) -> candidate
            isRemoteNewer(current.version, candidate.version) -> current
            candidate.apkSize > 0L && current.apkSize <= 0L -> candidate
            current.apkSize > 0L && candidate.apkSize <= 0L -> current
            else -> current
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

    fun displayVersion(raw: String): String {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        return "v$trimmed"
    }

    /**
     * Parses manifest notes into display groups. Lines starting with `## ` begin a new group
     * (title follows the marker); `- ` lines become items. Any other non-empty line is treated
     * as a legacy flat item, so old manifests keep rendering as a single ungrouped list.
     */
    fun parseUpdateNotes(notes: String): List<UpdateNotesGroup> {
        if (notes.isBlank()) return emptyList()
        val groups = mutableListOf<UpdateNotesGroup>()
        var title: String? = null
        val items = mutableListOf<String>()

        fun flush() {
            if (title != null || items.isNotEmpty()) {
                groups += UpdateNotesGroup(title = title, items = items.toList())
                title = null
                items.clear()
            }
        }

        for (rawLine in notes.replace('；', '\n').lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("##") -> {
                    flush()
                    title = line.removePrefix("##").trim().ifBlank { null }
                }
                line.startsWith("- ") -> items += line.removePrefix("- ").trim()
                else -> items += line
            }
        }
        flush()
        return groups
    }

    /** Renders notes as plain text: grouped notes keep their titles and restart numbering per
     * group; legacy flat notes render as a single numbered list. */
    fun formatNotesForDisplay(notes: String): String {
        if (notes.isBlank()) return notes
        val builder = StringBuilder()
        parseUpdateNotes(notes).forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) builder.append('\n')
            val title = group.title
            if (!title.isNullOrBlank()) {
                builder.append(title).append('\n')
            }
            group.items.forEachIndexed { index, item ->
                if (index > 0) builder.append('\n')
                builder.append(chineseOrdinal(index + 1)).append('、').append(item)
            }
        }
        return builder.toString()
    }

    internal fun chineseOrdinal(number: Int): String {
        if (number <= 0) return number.toString()
        val digits = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        return when (number) {
            in 1..9 -> digits[number]
            in 10..19 -> "十" + if (number % 10 == 0) "" else digits[number % 10]
            in 20..99 -> {
                val tens = number / 10
                val ones = number % 10
                digits[tens] + "十" + if (ones == 0) "" else digits[ones]
            }
            else -> number.toString()
        }
    }
}
