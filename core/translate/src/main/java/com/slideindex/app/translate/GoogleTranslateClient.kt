package com.slideindex.app.translate

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

@Singleton
class GoogleTranslateClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20.seconds)
        .readTimeout(30.seconds)
        .build()

    suspend fun translate(text: String, targetLang: String): TranslateResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext TranslateResult.Failure("empty_text")
        val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
        val url =
            "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=auto&tl=${URLEncoder.encode(targetLang, Charsets.UTF_8.name())}&dt=t&q=$encoded"
        runCatching {
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!response.isSuccessful) {
                return@withContext TranslateResult.Failure("http_${response.code}")
            }
            val body = response.body.string()
            if (body.isBlank()) {
                return@withContext TranslateResult.Failure("empty_response")
            }
            val translated = parseTranslatedText(body)
                ?: return@withContext TranslateResult.Failure("parse_failed")
            val detected = parseDetectedLanguage(body)
            TranslateResult.Success(translatedText = translated, detectedSourceLanguage = detected)
        }.getOrElse { error ->
            TranslateResult.Failure(error.message ?: "network_error")
        }
    }

    private fun parseTranslatedText(body: String): String? = runCatching {
        val root = JSONArray(body)
        val segments = root.optJSONArray(0) ?: return null
        buildString {
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONArray(index) ?: continue
                append(segment.optString(0, ""))
            }
        }.trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun parseDetectedLanguage(body: String): String? = runCatching {
        val root = JSONArray(body)
        root.optString(2, "").takeIf { it.isNotBlank() }
    }.getOrNull()
}
