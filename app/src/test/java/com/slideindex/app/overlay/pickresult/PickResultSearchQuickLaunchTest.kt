package com.slideindex.app.overlay.pickresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 搜索按钮长按滑选条的选项与初始项。 */
class PickResultSearchQuickLaunchTest {

    @Test
    fun `offers fullscreen and free window when free window enabled`() {
        val options = pickResultSearchQuickLaunchOptions(offerFreeWindow = true)

        assertTrue(options.first())
        assertEquals(listOf(true, false), options)
    }

    @Test
    fun `offers fullscreen only when free window disabled`() {
        assertEquals(listOf(true), pickResultSearchQuickLaunchOptions(offerFreeWindow = false))
    }

    @Test
    fun `starts on last used free window choice`() {
        val options = pickResultSearchQuickLaunchOptions(offerFreeWindow = true)

        assertEquals(0, pickResultSearchQuickLaunchStartIndex(options, initialFullscreen = true))
        assertEquals(1, pickResultSearchQuickLaunchStartIndex(options, initialFullscreen = false))
    }

    @Test
    fun `falls back to fullscreen when remembered free window is unavailable`() {
        val options = pickResultSearchQuickLaunchOptions(offerFreeWindow = false)

        assertEquals(0, pickResultSearchQuickLaunchStartIndex(options, initialFullscreen = false))
    }
}
