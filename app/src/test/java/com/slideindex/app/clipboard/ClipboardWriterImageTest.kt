package com.slideindex.app.clipboard

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ClipboardWriterImageTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private fun imageEntry(
        text: String = "",
        uri: String = "content://media/external/images/media/42",
        htmlText: String? = null,
        contentBlocks: List<ClipboardContentBlock> = emptyList(),
    ) = ClipboardEntry(
        id = "entry-1",
        type = ClipboardEntryType.URI,
        text = text,
        uri = uri,
        htmlText = htmlText,
        mimeType = "image/png",
        imageFileName = "entry-1.png",
        imageFileNames = listOf("entry-1.png"),
        contentBlocks = contentBlocks,
        createdAtEpochMs = 1L,
    )

    @Test
    fun screenshotEntryBuildsImageFirstClipWithoutLabelText() {
        val entry = imageEntry(text = "Screenshot_20260815-120000.png")

        val clip = ClipboardWriter.buildClipForEntry(context, entry)

        assertNotNull(clip)
        assertEquals(1, clip!!.itemCount)
        assertNull("image clip must not carry the label as a text item", clip.getItemAt(0).text)
        assertNotNull(clip.getItemAt(0).uri)
        assertTrue(clip.description.getMimeType(0).startsWith("image/"))
    }

    @Test
    fun oldStoredLabelBlocksStillBuildImageFirstClip() {
        val entry = imageEntry(
            text = "Screenshot_20260815-120000.png",
            contentBlocks = listOf(
                ClipboardContentBlock.text("Screenshot_20260815-120000.png"),
                ClipboardContentBlock.image("entry-1.png"),
            ),
        )

        val clip = ClipboardWriter.buildClipForEntry(context, entry)

        assertNotNull(clip)
        assertEquals(1, clip!!.itemCount)
        assertNull(clip.getItemAt(0).text)
        assertNotNull(clip.getItemAt(0).uri)
        assertTrue(clip.description.getMimeType(0).startsWith("image/"))
    }

    @Test
    fun contentUriFallbackEntryBuildsImageFirstClip() {
        val entry = imageEntry(
            text = "content://media/external/images/media/42",
            uri = "content://media/external/images/media/42",
        )

        val clip = ClipboardWriter.buildClipForEntry(context, entry)

        assertNotNull(clip)
        assertNull(clip!!.getItemAt(0).text)
        assertEquals("content://media/external/images/media/42", clip.getItemAt(0).uri.toString())
    }

    @Test
    fun richEntryWithRealTextKeepsTextFirstItem() {
        val entry = imageEntry(
            text = "caption",
            htmlText = "<p>caption</p><img src=\"http://example.com/a.png\">",
            contentBlocks = listOf(
                ClipboardContentBlock.text("caption"),
                ClipboardContentBlock.image("entry-1.png"),
            ),
        )

        val clip = ClipboardWriter.buildClipForEntry(context, entry)

        assertNotNull(clip)
        assertEquals("caption", clip!!.getItemAt(0).text.toString())
    }

    @Test
    fun multiImageEntryBuildsClipWithUriItems() {
        val blocks = listOf(
            ClipboardContentBlock.image("entry-a.png"),
            ClipboardContentBlock.image("entry-b.png"),
        )

        val clip = ClipboardWriter.buildClipForBlocks(
            htmlText = null,
            blocks = blocks,
            resolveDataUri = { null },
            resolveContentUri = { name -> "content://test.images.provider/$name".toUri() },
        )

        assertNotNull(clip)
        assertEquals(2, clip!!.itemCount)
        assertNull(clip.getItemAt(0).text)
        assertNotNull(clip.getItemAt(0).uri)
        assertNotNull(clip.getItemAt(1).uri)
        assertTrue(clip.description.getMimeType(0).startsWith("image/"))
    }
}
