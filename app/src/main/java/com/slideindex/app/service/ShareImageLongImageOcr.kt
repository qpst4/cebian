package com.slideindex.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Vertical tiling OCR for shared long screenshots.
 *
 * Keeps decode width (up to [TARGET_DECODE_WIDTH_PX]) instead of shrinking the whole image to 4096px,
 * then runs OCR on overlapping tiles serially while prefetching the next tile decode in parallel.
 */
object ShareImageLongImageOcr {
    const val TILE_HEIGHT_PX = 2000
    const val TILE_OVERLAP_PX = 200
    const val TILE_MIN_HEIGHT_PX = 2400
    const val TARGET_DECODE_WIDTH_PX = 1080
    const val THUMBNAIL_MAX_SIDE_PX = 800

    data class ImageBounds(val width: Int, val height: Int)

    fun shouldTile(bounds: ImageBounds): Boolean = bounds.height > TILE_MIN_HEIGHT_PX

    fun planTileRanges(
        imageHeight: Int,
        tileHeight: Int = TILE_HEIGHT_PX,
        overlap: Int = TILE_OVERLAP_PX,
    ): List<IntRange> {
        require(imageHeight > 0)
        require(tileHeight > 0)
        require(overlap in 0 until tileHeight)
        if (imageHeight <= tileHeight) {
            return listOf(0 until imageHeight)
        }
        val tiles = ArrayList<IntRange>()
        var top = 0
        while (top < imageHeight) {
            val bottom = min(top + tileHeight, imageHeight)
            tiles.add(top until bottom)
            if (bottom >= imageHeight) break
            top = bottom - overlap
        }
        return tiles
    }

    fun computeSampleSize(sourceWidth: Int, targetWidth: Int = TARGET_DECODE_WIDTH_PX): Int {
        if (sourceWidth <= targetWidth) return 1
        var sampleSize = 1
        while (sourceWidth / sampleSize > targetWidth) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun mergeTileTexts(blocks: List<String>, maxOverlapLines: Int = 3): String {
        if (blocks.isEmpty()) return ""
        val cleaned = blocks.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ""
        if (cleaned.size == 1) return cleaned.single()

        val mergedLines = cleaned.first().lines().map(String::trim).filter { it.isNotEmpty() }.toMutableList()
        for (index in 1 until cleaned.size) {
            val nextLines = cleaned[index].lines().map(String::trim).filter { it.isNotEmpty() }
            if (nextLines.isEmpty()) continue
            val overlap = overlapLineCount(mergedLines, nextLines, maxOverlapLines)
            mergedLines.addAll(nextLines.drop(overlap))
        }
        return mergedLines.joinToString("\n").trim()
    }

    internal fun overlapLineCount(
        previousLines: List<String>,
        nextLines: List<String>,
        maxCheck: Int,
    ): Int {
        val limit = min(maxCheck, min(previousLines.size, nextLines.size))
        for (count in limit downTo 1) {
            if (previousLines.takeLast(count) == nextLines.take(count)) {
                return count
            }
        }
        return 0
    }

    fun readImageBounds(context: Context, uri: Uri): ImageBounds? {
        readBoundsFromFileDescriptor(context, uri)?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            readBoundsFromImageDecoder(context, uri)?.let { return it }
        }
        return readBoundsFromInputStream(context, uri)
    }

    private fun readBoundsFromFileDescriptor(context: Context, uri: Uri): ImageBounds? {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return descriptor.use { pfd ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                ImageBounds(options.outWidth, options.outHeight)
            } else {
                null
            }
        }
    }

    private fun readBoundsFromInputStream(context: Context, uri: Uri): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return ImageBounds(options.outWidth, options.outHeight)
    }

    private fun readBoundsFromImageDecoder(context: Context, uri: Uri): ImageBounds? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            var bounds: ImageBounds? = null
            val decoded = ImageDecoder.decodeBitmap(
                source,
                ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
                    bounds = ImageBounds(info.size.width, info.size.height)
                    decoder.setTargetSize(1, 1)
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                },
            )
            decoded.recycle()
            bounds
        } catch (_: Exception) {
            null
        }
    }

    fun decodeThumbnail(context: Context, uri: Uri, bounds: ImageBounds): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxSide = maxOf(info.size.width, info.size.height)
                    if (maxSide > THUMBNAIL_MAX_SIDE_PX) {
                        val scale = THUMBNAIL_MAX_SIDE_PX.toFloat() / maxSide
                        val targetW = (info.size.width * scale).toInt().coerceAtLeast(1)
                        val targetH = (info.size.height * scale).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                decoded
            } else {
                decodeThumbnailLegacy(context, uri, bounds)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeThumbnailLegacy(context: Context, uri: Uri, bounds: ImageBounds): Bitmap? {
        val maxSide = maxOf(bounds.width, bounds.height)
        var sampleSize = 1
        while (maxSide / sampleSize > THUMBNAIL_MAX_SIDE_PX) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    suspend fun recognizeTiled(
        context: Context,
        uri: Uri,
        bounds: ImageBounds,
        modelId: String,
    ): String? {
        val sampleSize = computeSampleSize(bounds.width)
        val tileRanges = planTileRanges(bounds.height)
        if (tileRanges.isEmpty()) return null
        val tileTexts = ArrayList<String>(tileRanges.size)

        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { pfd ->
            val decoder = BitmapRegionDecoder.newInstance(pfd.fileDescriptor, false) ?: return null
            val decoderMutex = Mutex()
            try {
                coroutineScope {
                    suspend fun decodeRange(range: IntRange): Bitmap? =
                        decoderMutex.withLock {
                            decodeTileRegion(
                                decoder = decoder,
                                bounds = bounds,
                                range = range,
                                sampleSize = sampleSize,
                            )
                        }

                    var prefetchedTile: Deferred<Bitmap?>? = null
                    for ((index, range) in tileRanges.withIndex()) {
                        val tile = if (index == 0) {
                            decodeRange(range)
                        } else {
                            prefetchedTile?.await()
                        }
                        if (index + 1 < tileRanges.size) {
                            val nextRange = tileRanges[index + 1]
                            prefetchedTile = async(Dispatchers.IO) { decodeRange(nextRange) }
                        } else {
                            prefetchedTile = null
                        }

                        if (tile == null) continue
                        try {
                            val text = RegionalScreenshotOcr.recognizeBitmapPublic(
                                context,
                                modelId,
                                tile,
                            )?.trim()?.takeIf { it.isNotEmpty() }
                            if (text != null) {
                                tileTexts.add(text)
                            }
                        } finally {
                            tile.recycle()
                        }
                    }
                }
            } finally {
                decoder.recycle()
            }
        }
        return mergeTileTexts(tileTexts).takeIf { it.isNotEmpty() }
    }

    private fun decodeTileRegion(
        decoder: BitmapRegionDecoder,
        bounds: ImageBounds,
        range: IntRange,
        sampleSize: Int,
    ): Bitmap? {
        val rect = Rect(
            0,
            range.first.coerceAtLeast(0),
            bounds.width,
            range.last + 1,
        )
        if (rect.width() <= 0 || rect.height() <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            decoder.decodeRegion(rect, options)
        } catch (_: Exception) {
            null
        }
    }
}
