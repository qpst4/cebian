package com.slideindex.app.barcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.Binarizer
import com.google.zxing.NotFoundException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ZxingBarcodeScanner {
    private const val MAX_SCAN_DIMENSION = 2000
    private const val PICK_UPSCALE_MIN_DIMENSION = 960
    private const val PICK_UPSCALE_FACTOR = 2f

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

    private val qrHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
        ),
    )

    private val qrTryHarderHints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
        ),
    )

    fun scanBitmap(bitmap: Bitmap, pickFastPath: Boolean = false): List<BarcodeScanResult> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val prepared = prepareScanBitmap(bitmap, pickFastPath)
        val shouldRecyclePrepared = prepared !== bitmap
        return try {
            if (pickFastPath) {
                decodeWithStrategies(prepared, qrHints)?.let { return it }
                decodeWithStrategies(prepared, qrTryHarderHints)?.let { return it }
            }
            decodeWithStrategies(prepared, allFormatHints).orEmpty()
        } finally {
            if (shouldRecyclePrepared) {
                prepared.recycle()
            }
        }
    }

    private fun prepareScanBitmap(bitmap: Bitmap, pickFastPath: Boolean): Bitmap {
        if (!pickFastPath) {
            return scaleDownIfNeeded(bitmap, MAX_SCAN_DIMENSION)
        }
        val padded = padQuietZone(bitmap, FloatBallOcrRegions.PICK_CROP_EXPAND_FRACTION)
        val upscaled = scaleUpIfSmall(padded)
        if (upscaled !== padded) {
            padded.recycle()
        }
        val limited = scaleDownIfNeeded(upscaled, MAX_SCAN_DIMENSION)
        if (limited !== upscaled) {
            upscaled.recycle()
        }
        return limited
    }

    /** Simulates screen-space quiet-zone expand for pick crops without changing the UI bitmap. */
    private fun padQuietZone(bitmap: Bitmap, fraction: Float): Bitmap {
        if (fraction <= 0f) return bitmap
        val padX = (bitmap.width * fraction).roundToInt()
        val padY = (bitmap.height * fraction).roundToInt()
        if (padX <= 0 && padY <= 0) return bitmap
        val padded = createBitmap(
            bitmap.width + padX * 2,
            bitmap.height + padY * 2,
        )
        Canvas(padded).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, padX.toFloat(), padY.toFloat(), null)
        }
        return padded
    }

    private fun decodeWithStrategies(
        bitmap: Bitmap,
        hints: Map<DecodeHintType, Any>,
    ): List<BarcodeScanResult>? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (luminance in luminanceVariants(pixels, width, height)) {
            val source = ByteArrayLuminanceSource(width, height, luminance)
            for (binarizer in BinarizerStrategy.entries) {
                tryDecode(source, hints, binarizer).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    private fun luminanceVariants(pixels: IntArray, width: Int, height: Int): List<ByteArray> {
        val standard = rgbLuminance(pixels)
        val contrast = contrastStretchedLuminance(pixels)
        return listOf(
            standard,
            invertLuminance(standard),
            contrast,
            invertLuminance(contrast),
        )
    }

    private fun rgbLuminance(pixels: IntArray): ByteArray {
        val output = ByteArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            output[index] = (0.299 * red + 0.587 * green + 0.114 * blue).roundToInt()
                .coerceIn(0, 255)
                .toByte()
        }
        return output
    }

    private fun contrastStretchedLuminance(pixels: IntArray): ByteArray {
        val gray = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            (0.299 * red + 0.587 * green + 0.114 * blue).roundToInt()
        }
        var min = 255
        var max = 0
        for (value in gray) {
            min = min(min, value)
            max = max(max, value)
        }
        if (max <= min) {
            return gray.map { it.coerceIn(0, 255).toByte() }.toByteArray()
        }
        return ByteArray(gray.size) { index ->
            (((gray[index] - min) * 255f) / (max - min)).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    private fun invertLuminance(luminance: ByteArray): ByteArray =
        ByteArray(luminance.size) { index ->
            (255 - (luminance[index].toInt() and 0xFF)).toByte()
        }

    private enum class BinarizerStrategy {
        Hybrid {
            override fun create(source: LuminanceSource): Binarizer = HybridBinarizer(source)
        },
        GlobalHistogram {
            override fun create(source: LuminanceSource): Binarizer = GlobalHistogramBinarizer(source)
        },
        ;

        abstract fun create(source: LuminanceSource): Binarizer
    }

    private fun tryDecode(
        source: LuminanceSource,
        hints: Map<DecodeHintType, Any>,
        binarizer: BinarizerStrategy,
    ): List<BarcodeScanResult> {
        val binaryBitmap = BinaryBitmap(binarizer.create(source))
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

    private fun scaleUpIfSmall(bitmap: Bitmap): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim >= PICK_UPSCALE_MIN_DIMENSION) return bitmap
        val targetMax = min(
            (maxDim * PICK_UPSCALE_FACTOR).roundToInt(),
            MAX_SCAN_DIMENSION,
        ).coerceAtLeast(maxDim)
        if (targetMax <= maxDim) return bitmap
        val scale = targetMax.toFloat() / maxDim
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(targetWidth, targetHeight)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxDim
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(targetWidth, targetHeight)
    }

    private class ByteArrayLuminanceSource(
        private val width: Int,
        private val height: Int,
        private val luminance: ByteArray,
    ) : LuminanceSource(width, height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val output = row ?: ByteArray(width)
            System.arraycopy(luminance, y * width, output, 0, width)
            return output
        }

        override fun getMatrix(): ByteArray = luminance

        override fun isCropSupported(): Boolean = true

        override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource {
            require(left >= 0 && top >= 0)
            require(left + width <= this.width && top + height <= this.height)
            val cropped = ByteArray(width * height)
            val rowStride = this.width
            for (y in 0 until height) {
                System.arraycopy(
                    luminance,
                    (top + y) * rowStride + left,
                    cropped,
                    y * width,
                    width,
                )
            }
            return ByteArrayLuminanceSource(width, height, cropped)
        }

        override fun isRotateSupported(): Boolean = false
    }
}
