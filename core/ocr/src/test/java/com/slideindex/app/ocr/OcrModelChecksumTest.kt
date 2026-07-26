package com.slideindex.app.ocr

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrModelChecksumTest {

    @Test
    fun sha256Hex_matchesKnownDigest() {
        val file = File.createTempFile("ocr-checksum", ".bin")
        val payload = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(payload)

        val digest = OcrModelChecksum.sha256Hex(file)

        assertEquals(digest, OcrModelChecksum.sha256Hex(file))
        assertTrue(digest.length == 64)
        file.delete()
    }

    @Test
    fun matches_returnsFalseWhenHashDiffers() {
        val file = File.createTempFile("ocr-checksum", ".bin")
        file.writeBytes(byteArrayOf(9))

        assertFalse(
            OcrModelChecksum.matches(
                file,
                "0000000000000000000000000000000000000000000000000000000000000000",
            ),
        )
        file.delete()
    }

    @Test
    fun matches_returnsTrueWhenHashOmitted() {
        val file = File.createTempFile("ocr-checksum", ".bin")
        file.writeBytes(byteArrayOf(9))

        assertTrue(OcrModelChecksum.matches(file, null))
        file.delete()
    }
}
