package com.slideindex.app.search.websuggestions

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 */

/** Fetches web search suggestions from Google's public suggest API. */
object WebSuggestionsUtils {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun getSuggestions(query: String): List<String> {
        return try {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isBlank()) return emptyList()

            val encodedQuery = URLEncoder.encode(trimmedQuery, Charsets.UTF_8.name())
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery"
            val responseBody = executeCancellable(Request.Builder().url(url).build()) ?: return emptyList()
            if (responseBody.isBlank()) return emptyList()

            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() < 2) return emptyList()

            val suggestionsArray = jsonArray.getJSONArray(1)
            val suggestions = mutableListOf<String>()
            val maxSuggestions = minOf(5, suggestionsArray.length())

            for (i in 0 until maxSuggestions) {
                val suggestion = suggestionsArray.getString(i)
                if (suggestion.isBlank()) continue
                val capitalizedSuggestion = suggestion.replaceFirstChar { it.uppercase() }
                val matchesQuery = i == 0 && capitalizedSuggestion.equals(trimmedQuery, ignoreCase = true)
                if (matchesQuery) continue
                suggestions.add(capitalizedSuggestion)
            }
            suggestions
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun executeCancellable(request: Request): String? =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val body = if (it.isSuccessful) it.body.string() else null
                            if (continuation.isActive) continuation.resume(body)
                        }
                    }
                },
            )
        }
}
