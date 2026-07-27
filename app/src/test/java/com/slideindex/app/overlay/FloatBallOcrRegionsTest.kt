package com.slideindex.app.overlay

import android.graphics.Rect
import android.util.DisplayMetrics
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FloatBallOcrRegionsTest {
    @Test
    fun expandPoint_createsRegionAroundAnchor() {
        val metrics = DisplayMetrics().apply {
            density = 2f
            widthPixels = 1080
            heightPixels = 2400
        }
        val rect = FloatBallOcrRegions.expandPoint(metrics, 400f, 800f)
        assertTrue(rect.contains(400, 800))
        assertTrue(rect.width() >= 160)
        assertTrue(rect.height() >= 160)
    }

    @Test
    fun clampToScreen_keepsRectInsideDisplay() {
        val rect = FloatBallOcrRegions.clampToScreen(Rect(-10, -10, 20, 20), 1080, 2400)
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertTrue(rect.right <= 1080)
        assertTrue(rect.bottom <= 2400)
    }

    @Test
    fun padScreenRect_expandsRectEdges() {
        val rect = Rect(100, 2186, 400, 2318)
        val padded = FloatBallOcrRegions.padScreenRect(rect, paddingPx = 3)
        assertEquals(97, padded.left)
        assertEquals(2183, padded.top)
        assertEquals(403, padded.right)
        assertEquals(2321, padded.bottom)
    }

    @Test
    fun mapScreenRectToBitmap_bottomControlWithPadding_extendsCropTowardBitmapBottom() {
        val baseRect = Rect(259, 2186, 817, 2318)
        val withoutPadding = FloatBallOcrRegions.mapScreenRectToBitmap(
            screenRect = baseRect,
            screenWidth = 1080,
            screenHeight = 2400,
            bitmapWidth = 1080,
            bitmapHeight = 2280,
        )
        val withPadding = FloatBallOcrRegions.mapScreenRectToBitmap(
            screenRect = FloatBallOcrRegions.padScreenRect(baseRect),
            screenWidth = 1080,
            screenHeight = 2400,
            bitmapWidth = 1080,
            bitmapHeight = 2280,
        )
        val cropRect = FloatBallOcrRegions.clampToScreen(withPadding, 1080, 2280)
        assertTrue(cropRect.bottom > withoutPadding.bottom)
        assertTrue(cropRect.bottom <= 2280)
        assertTrue(cropRect.height() >= 130)
        assertTrue(cropRect.width() > 500)
    }

    @Test
    fun mapScreenRectToBitmap_scalesBottomControlWhenScreenshotIsShorter() {
        // displayMetrics height (a11y coords) can exceed takeScreenshot bitmap height.
        val screenRect = Rect(259, 2186, 817, 2318)
        val mapped = FloatBallOcrRegions.mapScreenRectToBitmap(
            screenRect = screenRect,
            screenWidth = 1080,
            screenHeight = 2400,
            bitmapWidth = 1080,
            bitmapHeight = 2280,
        )
        assertTrue(mapped.top < screenRect.top)
        assertTrue(mapped.bottom <= 2280)
        val mappedHeight = mapped.bottom - mapped.top
        val sourceHeight = screenRect.bottom - screenRect.top
        assertTrue(mappedHeight >= (sourceHeight * 0.9f).roundToInt())
        assertTrue(mapped.width() > 500)
    }

    @Test
    fun expandScreenRect_expandsByFractionAndClampsToScreen() {
        val rect = Rect(100, 200, 300, 400)
        val expanded = FloatBallOcrRegions.expandScreenRect(rect, 0.12f, 1080, 2400)
        assertEquals(76, expanded.left)
        assertEquals(176, expanded.top)
        assertEquals(324, expanded.right)
        assertEquals(424, expanded.bottom)
        assertTrue(expanded.width() > rect.width())
        assertTrue(expanded.height() > rect.height())
    }

    @Test
    fun mapScreenRectToBitmap_noOpWhenSizesMatch() {
        val screenRect = Rect(100, 200, 400, 500)
        val mapped = FloatBallOcrRegions.mapScreenRectToBitmap(
            screenRect = screenRect,
            screenWidth = 1080,
            screenHeight = 2400,
            bitmapWidth = 1080,
            bitmapHeight = 2400,
        )
        assertEquals(screenRect, mapped)
    }
}
