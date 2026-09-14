package com.slideindex.app.translate

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateService @Inject constructor(
    private val googleTranslateClient: GoogleTranslateClient,
    private val mlKitTranslateEngine: MlKitTranslateEngine,
    private val cloudLlmTranslateClient: CloudLlmTranslateClient,
) {
    suspend fun translate(
        text: String,
        targetLang: String,
        engine: TranslateEngine,
    ): TranslateResult = when (engine) {
        TranslateEngine.GOOGLE -> googleTranslateClient.translate(text, targetLang)
        TranslateEngine.ML_KIT -> mlKitTranslateEngine.translate(text, targetLang)
        TranslateEngine.CLOUD_LLM -> cloudLlmTranslateClient.translate(text, targetLang)
    }
}
