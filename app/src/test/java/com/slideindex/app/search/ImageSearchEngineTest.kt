package com.slideindex.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSearchEngineTest {
    @Test
    fun yandexUsesHostedUrl() {
        val yandex = ImageSearchEngine.Yandex
        assertTrue(yandex.usesHostedUrl)
        assertFalse(yandex.usesDirectPost)
        assertEquals("https://yandex.com/images/", yandex.externalPageUrl)
        assertTrue(ImageSearchEngine.hostedUrlEngines.contains(yandex))
        assertFalse(ImageSearchEngine.directPostEngines.contains(yandex))
    }
}
