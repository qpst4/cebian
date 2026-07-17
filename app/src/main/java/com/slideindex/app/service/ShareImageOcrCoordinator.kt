package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.perf.PickPerf
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Handles shared images: decode, show pick-result panel, run on-device OCR. */
object ShareImageOcrCoordinator {
    private const val MAX_DECODE_SIDE_PX = 4096

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ocrDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ShareImageOcr").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    fun resolveImageUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_SEND -> readSendUri(intent)
            Intent.ACTION_SEND_MULTIPLE -> readSendMultipleUri(intent)
            else -> null
        }
    }

    suspend fun handleSharedImage(
        context: Context,
        uri: Uri,
        modelId: String,
    ): Boolean {
        PickPerf.mark("share_image_ocr_start", "model=$modelId")
        if (!SlideIndexAccessibilityService.isConnected()) {
            Toast.makeText(context, R.string.share_image_ocr_service_required, Toast.LENGTH_LONG).show()
            return false
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        val modelReady = modelId.isNotBlank() &&
            OcrDependencyAccess.modelRepository(context)?.isInstalled(modelId) == true
        if (!modelReady) {
            Toast.makeText(context, R.string.share_image_ocr_model_required, Toast.LENGTH_LONG).show()
            return false
        }

        val bounds = withContext(Dispatchers.IO) {
            ShareImageLongImageOcr.readImageBounds(context, uri)
                ?: decodeShareImage(context, uri)?.let { bitmap ->
                    ShareImageLongImageOcr.ImageBounds(bitmap.width, bitmap.height).also {
                        bitmap.recycle()
                    }
                }
        }
        if (bounds == null) {
            Toast.makeText(context, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        return if (ShareImageLongImageOcr.shouldTile(bounds)) {
            handleTiledSharedImage(hostContext, context, uri, bounds, modelId)
        } else {
            handleSingleSharedImage(hostContext, context, uri, modelId)
        }
    }

    private suspend fun handleSingleSharedImage(
        hostContext: Context,
        context: Context,
        uri: Uri,
        modelId: String,
    ): Boolean {
        val bitmap = withContext(Dispatchers.IO) { decodeShareImage(context, uri) }
        if (bitmap == null) {
            Toast.makeText(context, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        val screenshotCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        if (screenshotCopy == null) {
            bitmap.recycle()
            Toast.makeText(context, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        showPendingResult(hostContext, screenshotCopy)
        launchSingleOcr(hostContext, modelId, bitmap)
        PickPerf.mark("share_image_ocr_panel_shown", "mode=single size=${screenshotCopy.width}x${screenshotCopy.height}")
        return true
    }

    private suspend fun handleTiledSharedImage(
        hostContext: Context,
        context: Context,
        uri: Uri,
        bounds: ShareImageLongImageOcr.ImageBounds,
        modelId: String,
    ): Boolean {
        val thumbnail = withContext(Dispatchers.IO) {
            ShareImageLongImageOcr.decodeThumbnail(context, uri, bounds)
        }
        if (thumbnail == null) {
            Toast.makeText(context, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        val screenshotCopy = thumbnail.copy(thumbnail.config ?: Bitmap.Config.ARGB_8888, false)
        if (screenshotCopy == null) {
            thumbnail.recycle()
            Toast.makeText(context, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        thumbnail.recycle()

        val tileCount = ShareImageLongImageOcr.planTileRanges(bounds.height).size
        showPendingResult(hostContext, screenshotCopy)
        launchTiledOcr(hostContext, context, uri, bounds, modelId)
        PickPerf.mark(
            "share_image_ocr_panel_shown",
            "mode=tiled source=${bounds.width}x${bounds.height} tiles=$tileCount",
        )
        return true
    }

    private fun showPendingResult(hostContext: Context, screenshotCopy: Bitmap) {
        FloatBallPickResultPanel.showResult(
            context = hostContext,
            result = FloatBallPickResult(
                a11yText = null,
                ocrText = null,
                screenshot = screenshotCopy,
                screenRect = null,
                activeSource = PickResultTextSource.OCR,
                ocrAvailable = true,
                ocrPending = true,
                ocrPreferSwitchOnComplete = true,
                a11ySourceEnabled = false,
            ),
        )
    }

    private fun launchSingleOcr(context: Context, modelId: String, bitmap: Bitmap) {
        scope.launch(ocrDispatcher) {
            try {
                val ocrText = RegionalScreenshotOcr.recognizeBitmapPublic(
                    context,
                    modelId,
                    bitmap,
                )?.trim()?.takeIf { it.isNotEmpty() }
                deliverOcrResult(context, ocrText)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun launchTiledOcr(
        hostContext: Context,
        context: Context,
        uri: Uri,
        bounds: ShareImageLongImageOcr.ImageBounds,
        modelId: String,
    ) {
        scope.launch(ocrDispatcher) {
            val ocrText = ShareImageLongImageOcr.recognizeTiled(
                context = context,
                uri = uri,
                bounds = bounds,
                modelId = modelId,
            )
            deliverOcrResult(hostContext, ocrText)
        }
    }

    private suspend fun deliverOcrResult(context: Context, ocrText: String?) {
        withContext(Dispatchers.Main.immediate) {
            if (!ocrText.isNullOrBlank()) {
                FloatBallPickResultPanel.updateOcrText(ocrText, switchToOcr = true)
            } else {
                FloatBallPickResultPanel.finishOcrPending()
                Toast.makeText(context, R.string.float_ball_text_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readSendUri(intent: Intent): Uri? {
        val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return streamUri ?: intent.clipData?.getItemAt(0)?.uri
    }

    private fun readSendMultipleUri(intent: Intent): Uri? {
        val streams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        return streams?.firstOrNull() ?: intent.clipData?.getItemAt(0)?.uri
    }

    internal fun decodeShareImage(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxSide = maxOf(info.size.width, info.size.height)
                    if (maxSide > MAX_DECODE_SIDE_PX) {
                        val scale = MAX_DECODE_SIDE_PX.toFloat() / maxSide
                        val targetW = (info.size.width * scale).toInt().coerceAtLeast(1)
                        val targetH = (info.size.height * scale).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                downscaleIfNeeded(decoded)
            } else {
                decodeShareImageLegacy(context, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeShareImageLegacy(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxSide / sampleSize > MAX_DECODE_SIDE_PX) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null
        return downscaleIfNeeded(decoded)
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_DECODE_SIDE_PX) return bitmap
        val scale = MAX_DECODE_SIDE_PX.toFloat() / maxSide
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
}
