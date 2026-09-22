package com.slideindex.app.clipboard

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ClipboardWriterFileUriTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun localContentFileEntryBuildsUriClipInsteadOfText() {
        val entry = ClipboardEntry(
            id = "pdf-1",
            type = ClipboardEntryType.URI,
            text = "content://com.example.provider/document/1",
            uri = "content://com.example.provider/document/1",
            mimeType = "application/pdf",
            createdAtEpochMs = 1L,
        )

        val clip = ClipboardWriter.buildClipForEntry(context, entry)

        assertNotNull(clip)
        assertEquals(1, clip!!.itemCount)
        assertNull(clip.getItemAt(0).text)
        assertEquals(
            "content://com.example.provider/document/1",
            clip.getItemAt(0).uri.toString(),
        )
        assertEquals("application/pdf", clip.description.getMimeType(0))
    }

    @Test
    fun localFileUriKeepsTextMimeAsFile() {
        val entry = ClipboardEntry(
            id = "txt-1",
            type = ClipboardEntryType.URI,
            text = "file:///storage/emulated/0/Download/notes.txt",
            uri = "file:///storage/emulated/0/Download/notes.txt",
            mimeType = "text/plain",
            createdAtEpochMs = 1L,
        )

        val clip = ClipboardWriter.buildClipForEntry(context, entry)

        assertNotNull(clip)
        assertNull(clip!!.getItemAt(0).text)
        assertEquals("text/plain", clip.description.getMimeType(0))
    }

    @Test
    fun remoteLinkEntryStillBuildsTextClip() {
        val entry = ClipboardEntry(
            id = "link-1",
            type = ClipboardEntryType.URI,
            text = "https://example.com/article",
            uri = "https://example.com/article",
            mimeType = "text/uri-list",
            createdAtEpochMs = 1L,
        )

        val clip = ClipboardWriter.buildClipForEntry(context, entry)

        assertNotNull(clip)
        assertNull(clip!!.getItemAt(0).uri)
        assertEquals("https://example.com/article", clip.getItemAt(0).text.toString())
    }
}
