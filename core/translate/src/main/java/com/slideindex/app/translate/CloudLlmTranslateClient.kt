package com.slideindex.app.translate

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class CloudLlmTranslateClient @Inject constructor(
    private val config: CloudLlmTranslateConfig,
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20.seconds)
        .readTimeout(60.seconds)
        .writeTimeout(30.seconds)
        .build()

    suspend fun translate(text: String, targetLang: String): TranslateResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext TranslateResult.Failure("empty_text")

        val credentials = config.current()
        if (!credentials.isConfigured) {
            return@withContext TranslateResult.Failure("api_key_not_configured")
        }

        val targetLabel = TranslateLanguageCatalog.displayName(targetLang)
        val systemPrompt =
            "You are a professional translator. Translate the user message into $targetLabel (BCP-47: $targetLang). " +
                "Preserve line breaks where reasonable. Output only the translation " +
                "with no explanations, notes, or markdown code fences."

        val cleanBaseUrl = credentials.baseUrl.trimEnd('/')
        val endpoint = if (cleanBaseUrl.endsWith("/chat/completions")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/chat/completions"
        }

        val requestBodyJson = buildJsonObject {
            put("model", credentials.model)
            put("temperature", 0.2)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", trimmed)
                })
            })
        }

        runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${credentials.apiKey.trim()}")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TranslateResult.Failure("http_${response.code}")
                }
                val responseStr = response.body.string()
                val rootObj = jsonParser.parseToJsonElement(responseStr).jsonObject
                val choices = rootObj["choices"]?.jsonArray
                if (choices.isNullOrEmpty()) {
                    Log.w(TAG, "No choices in cloud translate response")
                    return@withContext TranslateResult.Failure("cloud_empty")
                }
                val content = choices[0].jsonObject["message"]
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?.trim()
                    ?.let(::sanitizeTranslationOutput)
                if (content.isNullOrBlank()) {
                    TranslateResult.Failure("cloud_empty")
                } else {
                    TranslateResult.Success(translatedText = content)
                }
            }
        }.getOrElse { error ->
            Log.e(TAG, "Cloud LLM translate failed", error)
            TranslateResult.Failure(error.message ?: "network_error")
        }
    }

    private fun sanitizeTranslationOutput(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            val end = text.lastIndexOf("```")
            if (end > 3) {
                text = text.substring(3, end).trim()
                val firstLineBreak = text.indexOf('\n')
                if (firstLineBreak > 0 && text.substring(0, firstLineBreak).all { it.isLetter() || it == '-' }) {
                    text = text.substring(firstLineBreak + 1).trim()
                }
            }
        }
        return text.trim()
    }

    private companion object {
        const val TAG = "CloudLlmTranslate"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
