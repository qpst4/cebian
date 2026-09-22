package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.net.Uri
import com.slideindex.app.clipboard.ClipboardImageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PinDragHelperTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test
    fun itemsOf_textPin_trimsBody() {
        val items = PinDragHelper.itemsOf(PinContent.Text("  钉图文本\n"), "pin-1")

        assertEquals(listOf(PinDragItem.Text("钉图文本")), items)
    }

    @Test
    fun itemsOf_imagePin_usesStableEntryId() {
        val image = bitmap()
        val items = PinDragHelper.itemsOf(PinContent.Image(image), "pin-2")

        assertEquals(1, items.size)
        val item = items.first() as PinDragItem.Image
        assertEquals(image, item.bitmap)
        assertEquals("pin_drag_pin-2_0", item.entryId)
        assertEquals("pin_drag_pin-2_0.png", item.fileName)
    }

    @Test
    fun itemsOf_richPin_keepsBlockIndexAndSkipsBlankText() {
        val first = bitmap()
        val second = bitmap()
        val items = PinDragHelper.itemsOf(
            PinContent.Rich(
                listOf(
                    PinDisplayBlock.Text("第一段"),
                    PinDisplayBlock.Image(first),
                    PinDisplayBlock.Text("   "),
                    PinDisplayBlock.Image(second),
                )
            ),
            "pin-3",
        )

        assertEquals(3, items.size)
        assertEquals(PinDragItem.Text("第一段"), items[0])
        assertEquals("pin_drag_pin-3_1", (items[1] as PinDragItem.Image).entryId)
        assertEquals("pin_drag_pin-3_3", (items[2] as PinDragItem.Image).entryId)
    }

    @Test
    fun buildClip_textPin_producesPlainTextClip() {
        val items = PinDragHelper.itemsOf(PinContent.Text("hello"), "pin-4")

        val clip = PinDragHelper.buildClip(context, items)

        assertNotNull(clip)
        assertEquals("text/plain", clip!!.description.getMimeType(0))
        assertEquals("hello", clip.getItemAt(0).text.toString())
    }

    @Test
    fun buildClip_imagePin_declaresImageMimeAndPointsToCachedFile() {
        val items = PinDragHelper.itemsOf(PinContent.Image(bitmap()), "pin-5")

        // FileProvider 在 Robolectric 下取不到 authority，这里只验证落盘文件名与 ClipData 形状。
        val clip = PinDragHelper.buildClip(context, items) { Uri.parse("content://test/$it") }

        assertNotNull(clip)
        assertEquals("image/png", clip!!.description.getMimeType(0))
        assertEquals("content://test/pin_drag_pin-5_0.png", clip.getItemAt(0).uri.toString())
        assertTrue(ClipboardImageStore.imageFile(context, "pin_drag_pin-5_0.png").exists())
    }

    @Test
    fun buildClip_imagePin_reusesAlreadyPersistedFile() {
        val items = PinDragHelper.itemsOf(PinContent.Image(bitmap()), "pin-6")
        PinDragHelper.buildClip(context, items)
        val cached = ClipboardImageStore.imageFile(context, "pin_drag_pin-6_0.png")
        val stamp = 1_000L
        assertTrue(cached.setLastModified(stamp))

        PinDragHelper.buildClip(context, items)

        assertEquals(stamp, cached.lastModified())
    }

    @Test
    fun releaseCache_removesOnlyTargetPinFiles() {
        val kept = ClipboardImageStore.imageFile(context, "pin_drag_pin-b_0.png")
        val removed = ClipboardImageStore.imageFile(context, "pin_drag_pin-a_0.png")
        kept.parentFile?.mkdirs()
        kept.writeBytes(byteArrayOf(1))
        removed.writeBytes(byteArrayOf(1))

        PinDragHelper.releaseCache(context, "pin-a")

        assertTrue(!removed.exists())
        assertTrue(kept.exists())
    }
}
