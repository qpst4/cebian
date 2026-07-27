package com.slideindex.app.barcode

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader

object ZxingBarcodeScanner {
    private const val MAX_SCAN_DIMENSION = 2000
    private const val PICK_FAST_SCAN_DIMENSION = 1280

    private val allFormatHints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODABAR,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.ITF,
        ),
    )

    private val pickFastHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
        ),
    )

    fun scanBitmap(bitmap: Bitmap, pickFastPath: Boolean = false): List<BarcodeScanResult> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val maxDim = if (pickFastPath) PICK_FAST_SCAN_DIMENSION else MAX_SCAN_DIMENSION
        val scanBitmap = scaleDownIfNeeded(bitmap, maxDim)
        val shouldRecycle = scanBitmap !== bitmap
        return try {
            if (pickFastPath) {
                val quick = decodeBitmap(scanBitmap, pickFastHints)
                if (quick.isNotEmpty()) return quick
            }
            decodeBitmap(scanBitmap, allFormatHints)
        } finally {
            if (shouldRecycle) {
                scanBitmap.recycle()
            }
        }
    }

    private fun decodeBitmap(
        bitmap: Bitmap,
        hints: Map<DecodeHintType, Any>,
    ): List<BarcodeScanResult> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = GenericMultipleBarcodeReader(
            MultiFormatReader().apply { setHints(hints) },
        )
        return try {
            reader.decodeMultiple(binaryBitmap)
                .mapNotNull { result ->
                    val text = result.text?.trim().orEmpty()
                    if (text.isEmpty()) return@mapNotNull null
                    BarcodeScanResult(
                        text = text,
                        format = result.barcodeFormat.name,
                    )
                }
                .distinctBy { "${it.format}:${it.text}" }
        } catch (_: NotFoundException) {
            emptyList()
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int = MAX_SCAN_DIMENSION): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxDim
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
