package com.slideindex.app.search

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows

class ImageSearchUrlBuilderTest {
    @Test
    fun build_googleHostedUrl() {
        val url = ImageSearchUrlBuilder.build(
            ImageSearchEngine.Google,
            "https://example.com/image.jpg",
        )
        assertEquals(
            "https://lens.google.com/uploadbyurl?url=https%3A%2F%2Fexample.com%2Fimage.jpg",
            url,
        )
    }

    @Test
    fun build_traceMoeHostedUrl() {
        val url = ImageSearchUrlBuilder.build(
            ImageSearchEngine.TraceMoe,
            "https://example.com/image.jpg",
        )
        assertEquals(
            "https://trace.moe/?url=https%3A%2F%2Fexample.com%2Fimage.jpg",
            url,
        )
    }

    @Test
    fun build_animeTraceHostedUrl() {
        val url = ImageSearchUrlBuilder.build(
            ImageSearchEngine.AnimeTrace,
            "https://example.com/image.jpg",
        )
        assertEquals(
            "https://www.animetrace.com/?url=https%3A%2F%2Fexample.com%2Fimage.jpg",
            url,
        )
    }

    @Test
    fun build_copyseekerHostedUrl() {
        val url = ImageSearchUrlBuilder.build(
            ImageSearchEngine.Copyseeker,
            "https://example.com/image.jpg",
        )
        assertEquals(
            "https://copyseeker.net/discovery?imageUrl=https%3A%2F%2Fexample.com%2Fimage.jpg",
            url,
        )
    }

    @Test
    fun build_yandexHostedUrl() {
        val url = ImageSearchUrlBuilder.build(
            ImageSearchEngine.Yandex,
            "https://example.com/image.jpg",
        )
        assertEquals(
            "https://yandex.com/images/search?rpt=imageview&url=https%3A%2F%2Fexample.com%2Fimage.jpg",
            url,
        )
    }

    @Test
    fun build_rejectsDirectPostEngines() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSearchUrlBuilder.build(
                ImageSearchEngine.Iqdb,
                "https://example.com/image.jpg",
            )
        }
    }
}
