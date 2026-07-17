package com.slideindex.app.service

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ShareImageOcrCoordinatorTest {

    @Test
    fun resolveImageUri_send_returnsExtraStream() {
        val uri = Uri.parse("content://example/image.png")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        assertEquals(uri, ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun resolveImageUri_sendMultiple_returnsFirstStream() {
        val first = Uri.parse("content://example/first.png")
        val second = Uri.parse("content://example/second.png")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }
        assertEquals(first, ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun resolveImageUri_unknownAction_returnsNull() {
        val intent = Intent(Intent.ACTION_VIEW)
        assertNull(ShareImageOcrCoordinator.resolveImageUri(intent))
    }
}
