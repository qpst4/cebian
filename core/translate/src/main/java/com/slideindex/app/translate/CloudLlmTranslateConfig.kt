package com.slideindex.app.translate

fun interface CloudLlmTranslateConfig {
    fun current(): CloudLlmTranslateCredentials
}

data class CloudLlmTranslateCredentials(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()
}
