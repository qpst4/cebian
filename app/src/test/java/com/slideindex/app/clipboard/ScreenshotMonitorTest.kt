package com.slideindex.app.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotMonitorTest {
    @Test
    fun detectsScreenshotsDirectory() {
        assertTrue(
            ScreenshotMonitor.isScreenshotCandidate(
                displayName = "IMG_001.png",
                mimeType = "image/png",
                relativePath = "DCIM/Screenshots/",
                dataPath = null,
            ),
        )
    }

    @Test
    fun detectsScreenshotKeywordInFileName() {
        assertTrue(
            ScreenshotMonitor.isScreenshotCandidate(
                displayName = "Screenshot_20260725-175800.png",
                mimeType = "image/png",
                relativePath = "fooViewSave/picture/",
                dataPath = null,
            ),
        )
    }

    @Test
    fun detectsSlideIndexRegionalScreenshotFileName() {
        assertTrue(
            ScreenshotMonitor.isScreenshotCandidate(
                displayName = "Screenshot_20260725-175800.png",
                mimeType = "image/png",
                relativePath = "DCIM/Screenshots/",
                dataPath = null,
            ),
        )
    }

    @Test
    fun ignoresUnrelatedImages() {
        assertFalse(
            ScreenshotMonitor.isScreenshotCandidate(
                displayName = "float_ball_20260725_175800.png",
                mimeType = "image/png",
                relativePath = "Pictures/SlideIndex/",
                dataPath = null,
            ),
        )
    }

    @Test
    fun rejectsStaleImages() {
        val nowSec = System.currentTimeMillis() / 1000
        assertFalse(
            ScreenshotMonitor.isRecentEnough(
                dateTaken = (nowSec - 300) * 1000,
                dateAdded = null,
                dateModified = null,
            ),
        )
        assertTrue(
            ScreenshotMonitor.isRecentEnough(
                dateTaken = (nowSec - 30) * 1000,
                dateAdded = null,
                dateModified = null,
            ),
        )
    }
}
