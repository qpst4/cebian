package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.service.AccessibilityTextExtractor
import com.slideindex.app.service.RegionalScreenshotOcr
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Float-ball text pick: accessibility first (QC), then regional screenshot + OCR fallback.
 * Results are delivered to [FloatBallPickResultPanel] — never copied immediately.
 */
object FloatBallTextPickCoordinator {
    private const val TAG = "FloatBallTextPick"
    private const val CAPTURE_HIDE_DELAY_MS = 64L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pickInFlight = AtomicBoolean(false)

    fun pickAt(
        service: AccessibilityService,
        context: Context,
        rawX: Float,
        rawY: Float,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        onResult: (String?) -> Unit,
    ) {
        if (!pickInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                onResult(resolvePointText(service, context, rawX, rawY, ocrFallbackEnabled, ocrModelId))
            } finally {
                pickInFlight.set(false)
            }
        }
    }

    fun pickInRect(
        service: AccessibilityService,
        context: Context,
        rect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        previewBoundsPick: Boolean = false,
        onResult: (String?) -> Unit,
    ) {
        if (!pickInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                onResult(
                    resolveRectText(
                        service,
                        context,
                        rect,
                        ocrFallbackEnabled,
                        ocrModelId,
                        screenshot = null,
                        previewBoundsPick = previewBoundsPick,
                    ),
                )
            } finally {
                pickInFlight.set(false)
            }
        }
    }

    fun pickOnRelease(
        service: AccessibilityService,
        context: Context,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        regionalRect: Boolean,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        onResult: (FloatBallPickResult) -> Unit,
    ) {
        if (!pickInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                val metrics = context.resources.displayMetrics
                val result = if (regionalRect) {
                    val rect = rectBetween(startX, startY, endX, endY)
                    val safeRect = FloatBallOcrRegions.clampToScreen(
                        rect,
                        metrics.widthPixels,
                        metrics.heightPixels,
                    )
                    val screenshot = captureRect(service, safeRect)
                    val text = resolveRectText(
                        service,
                        context,
                        safeRect,
                        ocrFallbackEnabled,
                        ocrModelId,
                        screenshot,
                    )
                    FloatBallPickResult(text = text, screenshot = screenshot, screenRect = safeRect)
                } else {
                    val text = resolvePointText(
                        service,
                        context,
                        startX,
                        startY,
                        ocrFallbackEnabled,
                        ocrModelId,
                    )
                    FloatBallPickResult(text = text, screenshot = null, screenRect = null)
                }
                onResult(result)
            } catch (error: Throwable) {
                Log.w(TAG, "pickOnRelease failed", error)
                onResult(FloatBallPickResult(text = null, screenshot = null, screenRect = null))
            } finally {
                pickInFlight.set(false)
            }
        }
    }

    private suspend fun resolvePointText(
        service: AccessibilityService,
        context: Context,
        rawX: Float,
        rawY: Float,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
    ): String? {
        val a11yText = AccessibilityTextExtractor.collectTextAt(service, rawX, rawY)
        if (!a11yText.isNullOrBlank() &&
            !AccessibilityTextExtractor.isWeakA11yPickResult(a11yText)
        ) {
            return AccessibilityTextExtractor.dedupeTextLines(a11yText)
        }
        if (!ocrReady(context, ocrFallbackEnabled, ocrModelId)) {
            return a11yText?.takeIf { it.isNotBlank() }?.let(AccessibilityTextExtractor::dedupeTextLines)
        }
        val rect = FloatBallOcrRegions.expandPoint(context.resources.displayMetrics, rawX, rawY)
        val ocrText = recognizeWithCapture(service, context, rect, ocrModelId)
        return AccessibilityTextExtractor.preferLongerPickText(a11yText, ocrText)
    }

    private suspend fun resolveRectText(
        service: AccessibilityService,
        context: Context,
        rect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        screenshot: Bitmap?,
        previewBoundsPick: Boolean = false,
    ): String? {
        if (previewBoundsPick) {
            val text = AccessibilityTextExtractor.collectTextForPreviewRect(service, rect)
            if (text.isNotBlank()) {
                return AccessibilityTextExtractor.dedupeTextLines(text)
            }
            val pointText = AccessibilityTextExtractor.collectTextAt(
                service,
                rect.centerX().toFloat(),
                rect.centerY().toFloat(),
            )
            return pointText?.takeIf { it.isNotBlank() }
                ?.let(AccessibilityTextExtractor::dedupeTextLines)
        }
        val a11yText = AccessibilityTextExtractor.collectTextInRect(service, rect)
        if (a11yText.isNotBlank() && !AccessibilityTextExtractor.isWeakA11yPickResult(a11yText)) {
            return AccessibilityTextExtractor.dedupeTextLines(a11yText)
        }
        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()
        val pointText = AccessibilityTextExtractor.collectTextAt(service, centerX, centerY)
        if (!pointText.isNullOrBlank() && !AccessibilityTextExtractor.isWeakA11yPickResult(pointText)) {
            return AccessibilityTextExtractor.dedupeTextLines(pointText)
        }
        val mergedA11y = listOfNotNull(
            a11yText.takeIf { it.isNotBlank() },
            pointText?.takeIf { it.isNotBlank() },
        ).map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
            ?.let(AccessibilityTextExtractor::dedupeTextLines)
        if (!ocrReady(context, ocrFallbackEnabled, ocrModelId)) {
            return mergedA11y
        }
        val ocrText = if (screenshot != null) {
            withContext(Dispatchers.Default) {
                RegionalScreenshotOcr.recognizeBitmapPublic(context, ocrModelId, screenshot)
            }
        } else {
            recognizeWithCapture(service, context, rect, ocrModelId)
        }
        return AccessibilityTextExtractor.preferLongerPickText(mergedA11y, ocrText)
    }

    private fun ocrReady(context: Context, ocrFallbackEnabled: Boolean, ocrModelId: String): Boolean {
        if (!ocrFallbackEnabled || ocrModelId.isBlank()) return false
        return OcrDependencyAccess.modelRepository(context)?.isInstalled(ocrModelId) == true
    }

    private suspend fun captureRect(
        service: AccessibilityService,
        rect: Rect,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            FloatBallOverlay.suppressForScreenshotCapture()
            delay(CAPTURE_HIDE_DELAY_MS)
            withContext(Dispatchers.Default) {
                RegionalScreenshotOcr.captureRectBitmap(service, rect)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "capture rect failed", error)
            null
        } finally {
            FloatBallOverlay.restoreAfterScreenshotCapture()
        }
    }

    private suspend fun recognizeWithCapture(
        service: AccessibilityService,
        context: Context,
        rect: Rect,
        ocrModelId: String,
    ): String? {
        return try {
            FloatBallOverlay.suppressForScreenshotCapture()
            delay(CAPTURE_HIDE_DELAY_MS)
            withContext(Dispatchers.Default) {
                RegionalScreenshotOcr.recognizeInRect(service, context, rect, ocrModelId)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "ocr fallback failed", error)
            null
        } finally {
            FloatBallOverlay.restoreAfterScreenshotCapture()
        }
    }

    private fun rectBetween(startX: Float, startY: Float, endX: Float, endY: Float): Rect {
        val left = min(startX, endX).roundToInt()
        val top = min(startY, endY).roundToInt()
        val right = max(startX, endX).roundToInt()
        val bottom = max(startY, endY).roundToInt()
        return Rect(left, top, right, bottom)
    }
}
