package com.slideindex.app.translate

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.seconds

sealed class CloudLlmModelListResult {
    data class Success(val modelIds: List<String>) : CloudLlmModelListResult()
    data class Failure(val code: String) : CloudLlmModelListResult()
}

object CloudLlmTextModelFilter {
    private val excludeSubstrings = listOf(
        "-vl",
        "/vl",
        "vision",
        "embed",
        "rerank",
        "tts",
        "whisper",
        "cogview",
        "wanx",
        "speech",
        "realtime",
        "transcribe",
        "glm-4v",
        "glm-4.5v",
        "omni",
        "image",
        "video",
        "audio",
        "ocr",
        "dall-e",
        "sora",
    )

    fun filterForTextGeneration(modelIds: List<String>): List<String> {
        return modelIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { id -> !shouldExclude(id) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    private fun shouldExclude(id: String): Boolean {
        val lower = id.lowercase()
        return excludeSubstrings.any { lower.contains(it) }
    }

}

@Singleton
class CloudLlmModelCatalogClient @Inject constructor() {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20.seconds)
        .readTimeout(45.seconds)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cacheMutex = Mutex()
    private var cacheKey: String? = null
    private var cacheModels: List<String> = emptyList()
    private var cacheAtMs: Long = 0L

    suspend fun fetchTextModels(
        apiKey: String,
        baseUrl: String,
        bypassCache: Boolean = false,
    ): CloudLlmModelListResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext CloudLlmModelListResult.Failure("api_key_missing")
        }
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            return@withContext CloudLlmModelListResult.Failure("base_url_missing")
        }

        val key = cacheLookupKey(normalizedBase, trimmedKey)
        if (!bypassCache) {
            cacheMutex.withLock {
                if (cacheKey == key && cacheModels.isNotEmpty()) {
                    val age = System.currentTimeMillis() - cacheAtMs
                    if (age < CACHE_TTL_MS) {
                        return@withContext CloudLlmModelListResult.Success(cacheModels)
                    }
                }
            }
        }

        val endpoint = modelsEndpoint(normalizedBase)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $trimmedKey")
            .get()
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext CloudLlmModelListResult.Failure("http_${response.code}")
                }
                val body = response.body.string()
                val rawIds = parseOpenAiModelList(body)
                if (rawIds.isEmpty()) {
                    return@withContext CloudLlmModelListResult.Failure("empty_list")
                }
                val filtered = CloudLlmTextModelFilter.filterForTextGeneration(rawIds)
                if (filtered.isEmpty()) {
                    return@withContext CloudLlmModelListResult.Failure("no_text_models")
                }
                cacheMutex.withLock {
                    cacheKey = key
                    cacheModels = filtered
                    cacheAtMs = System.currentTimeMillis()
                }
                CloudLlmModelListResult.Success(filtered)
            }
        }.getOrElse { error ->
            Log.w(TAG, "fetchTextModels failed: ${error.message}")
            CloudLlmModelListResult.Failure(error.message ?: "network_error")
        }
    }

    internal fun modelsEndpoint(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return when {
            base.endsWith("/models", ignoreCase = true) -> base
            base.endsWith("/v1", ignoreCase = true) ||
                base.endsWith("/v4", ignoreCase = true) -> "$base/models"
            else -> "$base/v1/models"
        }
    }

    internal fun parseOpenAiModelList(body: String): List<String> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            element.jsonObject["id"]?.jsonPrimitive?.content
        }
    }

    private fun cacheLookupKey(baseUrl: String, apiKey: String): String {
        return "$baseUrl|${apiKey.hashCode()}"
    }

    private companion object {
        const val TAG = "CloudLlmModelCatalog"
        val CACHE_TTL_MS = 15.minutes.inWholeMilliseconds
    }
}
