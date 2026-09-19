package com.slideindex.app.translate

data class TranslateLanguageOption(
    val code: String,
    val displayName: String,
    val mlKitLanguage: String,
)

object TranslateLanguageCatalog {
    val options: List<TranslateLanguageOption> = listOf(
        TranslateLanguageOption("zh-CN", "Chinese (Simplified)", "zh"),
        TranslateLanguageOption("zh-TW", "Chinese (Traditional)", "zh"),
        TranslateLanguageOption("en", "English", "en"),
        TranslateLanguageOption("ja", "Japanese", "ja"),
        TranslateLanguageOption("ar", "Arabic", "ar"),
        TranslateLanguageOption("ko", "Korean", "ko"),
        TranslateLanguageOption("fr", "French", "fr"),
        TranslateLanguageOption("de", "German", "de"),
        TranslateLanguageOption("es", "Spanish", "es"),
        TranslateLanguageOption("ru", "Russian", "ru"),
    )

    fun find(code: String): TranslateLanguageOption? =
        options.find { it.code.equals(code, ignoreCase = true) }

    fun displayName(code: String): String = find(code)?.displayName ?: code
}
