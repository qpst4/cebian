package com.slideindex.app.overlay

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PinSizingTest {
    @Test
    fun resolvePinPlacementRect_mapsLogicalRectToCaptureSpace() {
        val screenRect = Rect(259, 2186, 817, 2318)
        val layoutMeta = ScreenshotLayoutMeta(
            screenWidth = 1080,
            screenHeight = 2400,
            captureWidth = 1080,
            captureHeight = 2280,
        )
        val placement = resolvePinPlacementRect(screenRect, layoutMeta)!!
        assertEquals(
            FloatBallOcrRegions.mapScreenRectToBitmap(
                screenRect,
                1080,
                2400,
                1080,
                2280,
            ),
            placement,
        )
    }

    @Test
    fun resolvePinImageDisplaySizePx_usesMappedPlacementWhenMetaDiffers() {
        val screenRect = Rect(100, 200, 400, 500)
        val layoutMeta = ScreenshotLayoutMeta(
            screenWidth = 1080,
            screenHeight = 2400,
            captureWidth = 1080,
            captureHeight = 2280,
        )
        val (width, height) = resolvePinImageDisplaySizePx(
            bitmap = android.graphics.Bitmap.createBitmap(280, 280, android.graphics.Bitmap.Config.ARGB_8888),
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
        )
        val placement = resolvePinPlacementRect(screenRect, layoutMeta)!!
        assertEquals(placement.width(), width)
        assertEquals(placement.height(), height)
    }
}
