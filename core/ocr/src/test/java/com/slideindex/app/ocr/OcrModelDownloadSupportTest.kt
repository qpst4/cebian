package com.slideindex.app.ocr

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OcrModelDownloadSupportTest {

    @Test
    fun resolveDownloadUrls_prefersMirrorsAndSkipsHuggingface() {
        val urls = OcrModelDownloadSupport.resolveDownloadUrls(
            mirrorUrls = listOf(
                "https://www.modelscope.cn/models/foo/resolve/master/inference.onnx",
                "https://huggingface.co/Paddle/foo/resolve/main/inference.onnx",
            ),
            primaryUrl = "https://huggingface.co/Paddle/foo/resolve/main/inference.onnx",
        )

        assertEquals(
            listOf("https://www.modelscope.cn/models/foo/resolve/master/inference.onnx"),
            urls,
        )
    }

    @Test
    fun resolveDownloadUrls_fallsBackToPrimaryWhenMirrorsFilteredOut() {
        val primary = "https://raw.githubusercontent.com/example/model.onnx"
        val urls = OcrModelDownloadSupport.resolveDownloadUrls(
            mirrorUrls = listOf("https://huggingface.co/foo/resolve/main/model.onnx"),
            primaryUrl = primary,
        )

        assertEquals(listOf(primary), urls)
    }

    @Test
    fun minBytesFor_usesOnnxThreshold() {
        assertEquals(64L * 1024L, OcrModelDownloadSupport.minBytesFor("det/inference.onnx"))
        assertEquals(1L, OcrModelDownloadSupport.minBytesFor("rec/inference.yml"))
    }

    @Test
    fun partialWriteMode_handlesResumeAndRestart() {
        assertEquals(
            OcrModelDownloadSupport.PartialWriteMode(append = true, resetExisting = false),
            OcrModelDownloadSupport.partialWriteMode(206, existingBytes = 10L),
        )
        assertEquals(
            OcrModelDownloadSupport.PartialWriteMode(append = false, resetExisting = true),
            OcrModelDownloadSupport.partialWriteMode(200, existingBytes = 10L),
        )
        assertNull(OcrModelDownloadSupport.partialWriteMode(404, existingBytes = 0L))
    }

    @Test
    fun finalizeDownloadedFile_renamesPartialToTarget() {
        val dir = Files.createTempDirectory("ocr-finalize").toFile()
        val partial = File(dir, "model.part")
        val target = File(dir, "model.bin")
        partial.writeBytes(byteArrayOf(7, 8, 9))

        OcrModelDownloadSupport.finalizeDownloadedFile(partial, target)

        assertArrayEquals(byteArrayOf(7, 8, 9), target.readBytes())
        assertFalse(partial.exists())
        dir.deleteRecursively()
    }
}
