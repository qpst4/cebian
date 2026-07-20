package com.slideindex.app.overlay.pickresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickResultUrlTest {
    @Test
    fun normalize_httpsUrl() {
        assertEquals(
            "https://example.com/path",
            PickResultUrl.normalizeOpenableUrl("https://example.com/path"),
        )
    }

    @Test
    fun normalize_wwwUrl() {
        assertEquals(
            "https://www.example.com",
            PickResultUrl.normalizeOpenableUrl("www.example.com"),
        )
    }

    @Test
    fun normalize_bareHost() {
        assertEquals(
            "https://example.com/foo",
            PickResultUrl.normalizeOpenableUrl("example.com/foo"),
        )
    }

    @Test
    fun normalize_trimsTrailingPunctuation() {
        assertEquals(
            "https://example.com",
            PickResultUrl.normalizeOpenableUrl("https://example.com."),
        )
    }

    @Test
    fun normalize_excludesAndroidPackageNames() {
        assertNull(PickResultUrl.normalizeOpenableUrl("com.android.settings"))
        assertNull(PickResultUrl.normalizeOpenableUrl("org.example.app"))
        assertNull(PickResultUrl.normalizeOpenableUrl("android.app.Activity"))
        assertNull(PickResultUrl.normalizeOpenableUrl("androidx.core.content.ContextCompat"))
        assertNull(PickResultUrl.normalizeOpenableUrl("io.github.foo.bar"))
    }

    @Test
    fun extract_multipleUrls() {
        val urls = PickResultUrl.extractOpenableUrls(
            "see https://a.com and www.b.com/path",
        )
        assertEquals(
            listOf("https://a.com", "https://www.b.com/path"),
            urls,
        )
    }

    @Test
    fun resolve_selectedUrl() {
        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = "visit https://a.com and https://b.com",
            activeText = "https://b.com",
            hasSelection = true,
        )
        assertTrue(action is PickResultOpenLinkAction.Open)
        assertEquals("https://b.com", (action as PickResultOpenLinkAction.Open).url)
    }

    @Test
    fun resolve_fullTextSingleUrl() {
        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = "https://example.com",
            activeText = "https://example.com",
            hasSelection = false,
        )
        assertTrue(action is PickResultOpenLinkAction.Open)
    }

    @Test
    fun resolve_fullTextMultipleUrls() {
        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = "https://a.com https://b.com",
            activeText = "https://a.com https://b.com",
            hasSelection = false,
        )
        assertTrue(action is PickResultOpenLinkAction.Choose)
        assertEquals(2, (action as PickResultOpenLinkAction.Choose).urls.size)
    }

    @Test
    fun resolve_invalidSelection_returnsNull() {
        assertNull(
            PickResultUrl.resolveOpenLinkAction(
                fullText = "https://a.com https://b.com",
                activeText = "hello",
                hasSelection = true,
            ),
        )
    }
}
