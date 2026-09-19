package com.slideindex.app.translate

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslateTargetResolverTest {
    @Test
    fun followApp_withArabicUiTag_resolvesArabic() {
        assertEquals(
            "ar",
            TranslateTargetResolver.resolve(
                storedCode = TranslateTargetLanguages.FOLLOW_APP,
                appUiLanguageTag = "ar",
            ),
        )
    }

    @Test
    fun followApp_withSystemUiTag_unsupportedLocale_fallsBackToEnglish() {
        assertEquals(
            "en",
            TranslateTargetResolver.resolve(
                storedCode = TranslateTargetLanguages.FOLLOW_APP,
                appUiLanguageTag = "",
                locale = Locale.forLanguageTag("pt-BR"),
            ),
        )
    }

    @Test
    fun fixedJapanese_ignoresAppUiLanguage() {
        assertEquals(
            "ja",
            TranslateTargetResolver.resolve(
                storedCode = "ja",
                appUiLanguageTag = "ar",
            ),
        )
    }

    @Test
    fun followApp_systemLocaleTraditionalChinese_mapsZhTw() {
        assertEquals(
            "zh-TW",
            TranslateTargetResolver.resolve(
                storedCode = TranslateTargetLanguages.FOLLOW_APP,
                appUiLanguageTag = "",
                locale = Locale.TAIWAN,
            ),
        )
    }

    @Test
    fun blankStoredCode_treatedAsFollowApp() {
        assertEquals(
            "en",
            TranslateTargetResolver.resolve(
                storedCode = "   ",
                appUiLanguageTag = "en",
            ),
        )
    }
}
