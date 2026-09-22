package com.slideindex.app.clipboard

import android.app.Application
import android.content.ClipData
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ClipboardDragClipHelperTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun nonMediaStoreHostKeepsClipUnchanged() {
        val clip = ClipData.newPlainText("clipboard", "hello")

        val result = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clip,
            hostPackage = "com.example.chat",
        )

        assertSame(clip, result)
    }

    @Test
    fun blankHostKeepsClipUnchanged() {
        val clip = ClipData.newPlainText("clipboard", "hello")

        val result = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clip,
            hostPackage = null,
        )

        assertSame(clip, result)
    }

    @Test
    fun hostWithoutUriItemsKeepsClipUnchanged() {
        val clip = ClipData.newPlainText("clipboard", "hello")

        val result = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clip,
            hostPackage = "com.example.chat",
        )

        assertSame(clip, result)
    }

    @Test
    fun plainTextClipHasShareableContent() {
        assertTrue(
            ClipboardDragShareFallback.hasShareableContent(
                ClipData.newPlainText("clipboard", "hello")
            )
        )
    }

    @Test
    fun htmlClipHasShareableContent() {
        assertTrue(
            ClipboardDragShareFallback.hasShareableContent(
                ClipData.newHtmlText("clipboard", "hello", "<b>hello</b>")
            )
        )
    }

    @Test
    fun blankClipHasNoShareableContent() {
        assertFalse(
            ClipboardDragShareFallback.hasShareableContent(
                ClipData.newPlainText("clipboard", "")
            )
        )
    }
}
