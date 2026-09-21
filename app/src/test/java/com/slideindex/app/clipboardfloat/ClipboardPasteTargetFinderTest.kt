package com.slideindex.app.clipboardfloat

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ClipboardPasteTargetFinderTest {

    @Test
    fun `dedupe drops outer container when child editable is present`() {
        val child = Rect(10, 10, 50, 50)
        val parent = Rect(0, 0, 100, 100)
        val other = Rect(200, 200, 280, 240)
        val result = ClipboardPasteTargetFinder.dedupeNestedTargets(listOf(parent, child, other))
        assertEquals(listOf(child, other), result)
    }
}
