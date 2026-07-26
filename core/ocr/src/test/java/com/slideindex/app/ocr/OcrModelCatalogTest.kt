package com.slideindex.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrModelCatalogTest {

    @Test
    fun totalDownloadBytes_sumsFileSizesWhenPresent() {
        val entry = OcrModelEntry(
            id = "ppocrv6-tiny",
            sizeBytes = 1L,
            files = listOf(
                OcrModelFileSpec(
                    relativePath = "det/inference.onnx",
                    url = "https://example.com/det.onnx",
                    sizeBytes = 100L,
                ),
                OcrModelFileSpec(
                    relativePath = "rec/inference.onnx",
                    url = "https://example.com/rec.onnx",
                    sizeBytes = 200L,
                ),
            ),
        )

        assertEquals(300L, entry.totalDownloadBytes)
    }

    @Test
    fun totalDownloadBytes_fallsBackToModelSizeWithoutFiles() {
        val entry = OcrModelEntry(
            id = "mlkit-chinese",
            sizeBytes = 26_214_400L,
            engine = OcrEngines.MLKIT_CHINESE,
            files = emptyList(),
        )

        assertEquals(26_214_400L, entry.totalDownloadBytes)
    }
}
