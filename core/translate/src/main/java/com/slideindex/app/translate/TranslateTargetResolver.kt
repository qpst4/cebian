package com.slideindex.app.translate

import java.util.Locale

object TranslateTargetResolver {
    fun resolve(
        storedCode: String,
        appUiLanguageTag: String?,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (!TranslateTargetLanguages.isFollowApp(storedCode)) {
            TranslateLanguageCatalog.find(storedCode)?.let { return it.code }
        }
        return resolveFromAppLanguage(appUiLanguageTag, locale)
    }

    private fun resolveFromAppLanguage(appUiLanguageTag: String?, locale: Locale): String {
        val normalized = appUiLanguageTag?.trim()?.lowercase().orEmpty()
        return when {
            normalized.isEmpty() -> mapSystemLocale(locale)
            normalized.startsWith("zh") -> "zh-CN"
            normalized == "en" -> "en"
            normalized == "ja" -> "ja"
            normalized == "ar" -> "ar"
            else -> mapSystemLocale(locale)
        }
    }

    private fun mapSystemLocale(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        val country = locale.country.uppercase(Locale.ROOT)

        if (country.isNotEmpty()) {
            TranslateLanguageCatalog.find("$language-$country")?.let { return it.code }
        }
        TranslateLanguageCatalog.find(language)?.let { return it.code }

        if (language == "zh") {
            return when (country) {
                "TW", "HK", "MO" -> "zh-TW"
                else -> "zh-CN"
            }
        }

        TranslateLanguageCatalog.options.firstOrNull { option ->
            option.mlKitLanguage.equals(language, ignoreCase = true)
        }?.let { return it.code }

        return "en"
    }
}
