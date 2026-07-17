package com.slideindex.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareImageLongImageOcrTest {

    @Test
    fun shouldTile_whenHeightExceedsThreshold() {
        assertTrue(
            ShareImageLongImageOcr.shouldTile(
                ShareImageLongImageOcr.ImageBounds(width = 1080, height = 2401),
            ),
        )
        assertFalse(
            ShareImageLongImageOcr.shouldTile(
                ShareImageLongImageOcr.ImageBounds(width = 1080, height = 2400),
            ),
        )
    }

    @Test
    fun planTileRanges_longImage_hasOverlap() {
        val tiles = ShareImageLongImageOcr.planTileRanges(imageHeight = 5000)
        assertEquals(3, tiles.size)
        assertEquals(0 until 2000, tiles[0])
        assertEquals(1800 until 3800, tiles[1])
        assertEquals(3600 until 5000, tiles[2])
    }

    @Test
    fun planTileRanges_shortImage_returnsSingleTile() {
        val tiles = ShareImageLongImageOcr.planTileRanges(imageHeight = 1500)
        assertEquals(listOf(0 until 1500), tiles)
    }

    @Test
    fun computeSampleSize_scalesWideImagesDownToTargetWidth() {
        assertEquals(1, ShareImageLongImageOcr.computeSampleSize(1080))
        assertEquals(2, ShareImageLongImageOcr.computeSampleSize(2160))
        assertEquals(4, ShareImageLongImageOcr.computeSampleSize(4320))
    }

    @Test
    fun mergeTileTexts_deduplicatesOverlapLines() {
        val merged = ShareImageLongImageOcr.mergeTileTexts(
            listOf(
                "第一行\n第二行\n第三行",
                "第二行\n第三行\n第四行",
            ),
        )
        assertEquals("第一行\n第二行\n第三行\n第四行", merged)
    }

    @Test
    fun overlapLineCount_returnsZeroWhenNoMatch() {
        val count = ShareImageLongImageOcr.overlapLineCount(
            previousLines = listOf("A", "B"),
            nextLines = listOf("C", "D"),
            maxCheck = 3,
        )
        assertEquals(0, count)
    }
}
