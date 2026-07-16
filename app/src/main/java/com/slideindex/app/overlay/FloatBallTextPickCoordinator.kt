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

    private data class PickTextResolution(
        val a11yText: String?,
        val ocrText: String?,
        val activeSource: PickResultTextSource,
        val ocrAvailable: Boolean,
    ) {
        fun toResult(screenshot: Bitmap?, screenRect: Rect?): FloatBallPickResult {
            return FloatBallPickResult(
                a11yText = a11yText,
                ocrText = ocrText,
                screenshot = screenshot,
                screenRect = screenRect,
                activeSource = activeSource,
                ocrAvailable = ocrAvailable,
            )
        }
    }

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
                val rect = FloatBallOcrRegions.expandPoint(context.resources.displayMetrics, rawX, rawY)
                val result = pickRegion(
                    service,
                    context,
                    rect,
                    ocrFallbackEnabled,
                    ocrModelId,
                ) { screenshot ->
                    resolvePointPick(
                        service,
                        context,
                        rawX,
                        rawY,
                        screenshot,
                        ocrFallbackEnabled,
                        ocrModelId,
                    )
                }
                onResult(result.text)
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
        onResult: (FloatBallPickResult) -> Unit,
    ) {
        if (!pickInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                val metrics = context.resources.displayMetrics
                val safeRect = FloatBallOcrRegions.clampToScreen(
                    rect,
                    metrics.widthPixels,
                    metrics.heightPixels,
                )
                val result = pickRegion(
                    service,
                    context,
                    safeRect,
                    ocrFallbackEnabled,
                    ocrModelId,
                ) { screenshot ->
                    resolveRectPick(
                        service,
                        context,
                        safeRect,
                        screenshot,
                        ocrFallbackEnabled,
                        ocrModelId,
                        previewBoundsPick,
                    )
                }
                onResult(result)
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
                    pickRegion(
                        service,
                        context,
                        safeRect,
                        ocrFallbackEnabled,
                        ocrModelId,
                    ) { screenshot ->
                        resolveRectPick(
                            service,
                            context,
                            safeRect,
                            screenshot,
                            ocrFallbackEnabled,
                            ocrModelId,
                        )
                    }
                } else {
                    val rect = FloatBallOcrRegions.expandPoint(metrics, startX, startY)
                    pickRegion(
                        service,
                        context,
                        rect,
                        ocrFallbackEnabled,
                        ocrModelId,
                    ) { screenshot ->
                        resolvePointPick(
                            service,
                            context,
                            startX,
                            startY,
                            screenshot,
                            ocrFallbackEnabled,
                            ocrModelId,
                        )
                    }
                }
                onResult(result)
            } catch (error: Throwable) {
                Log.w(TAG, "pickOnRelease failed", error)
                onResult(
                    FloatBallPickResult(
                        a11yText = null,
                        ocrText = null,
                        screenshot = null,
                        screenRect = null,
                    ),
                )
            } finally {
                pickInFlight.set(false)
            }
        }
    }

    private suspend fun pickRegion(
        service: AccessibilityService,
        context: Context,
        rect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        resolve: suspend (screenshot: Bitmap?) -> PickTextResolution,
    ): FloatBallPickResult {
        return withOverlaysHiddenForCapture {
            val screenshot = captureRectBitmap(service, rect)
            val resolution = resolve(screenshot)
            resolution.toResult(screenshot, rect)
        }
    }

    private suspend fun resolvePointPick(
        service: AccessibilityService,
        context: Context,
        rawX: Float,
        rawY: Float,
        screenshot: Bitmap?,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
    ): PickTextResolution {
        val a11yText = withContext(Dispatchers.Default) {
            AccessibilityTextExtractor.collectTextAt(service, rawX, rawY)
                ?.takeIf { it.isNotBlank() }
                ?.let(AccessibilityTextExtractor::dedupeTextLines)
        }
        val ocrReady = ocrReady(context, ocrFallbackEnabled, ocrModelId)
        val ocrText = if (ocrReady && screenshot != null) {
            recognizeBitmap(context, ocrModelId, screenshot)
        } else {
            null
        }
        return PickTextResolution(
            a11yText = a11yText,
            ocrText = ocrText,
            activeSource = preferredSource(a11yText, ocrText),
            ocrAvailable = ocrReady,
        )
    }

    private suspend fun resolveRectPick(
        service: AccessibilityService,
        context: Context,
        rect: Rect,
        screenshot: Bitmap?,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        previewBoundsPick: Boolean = false,
    ): PickTextResolution {
        val a11yText = withContext(Dispatchers.Default) {
            if (previewBoundsPick) {
                val text = AccessibilityTextExtractor.collectTextForPreviewRect(service, rect)
                if (text.isNotBlank()) {
                    return@withContext AccessibilityTextExtractor.dedupeTextLines(text)
                }
                AccessibilityTextExtractor.collectTextAt(
                    service,
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat(),
                )?.takeIf { it.isNotBlank() }
                    ?.let(AccessibilityTextExtractor::dedupeTextLines)
            } else {
                val rectText = AccessibilityTextExtractor.collectTextInRect(service, rect)
                if (rectText.isNotBlank() && !AccessibilityTextExtractor.isWeakA11yPickResult(rectText)) {
                    return@withContext AccessibilityTextExtractor.dedupeTextLines(rectText)
                }
                val centerX = rect.centerX().toFloat()
                val centerY = rect.centerY().toFloat()
                val pointText = AccessibilityTextExtractor.collectTextAt(service, centerX, centerY)
                if (!pointText.isNullOrBlank() && !AccessibilityTextExtractor.isWeakA11yPickResult(pointText)) {
                    return@withContext AccessibilityTextExtractor.dedupeTextLines(pointText)
                }
                listOfNotNull(
                    rectText.takeIf { it.isNotBlank() },
                    pointText?.takeIf { it.isNotBlank() },
                ).map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                    ?.let(AccessibilityTextExtractor::dedupeTextLines)
            }
        }
        val ocrReady = ocrReady(context, ocrFallbackEnabled, ocrModelId)
        val ocrText = if (ocrReady && screenshot != null) {
            recognizeBitmap(context, ocrModelId, screenshot)
        } else {
            null
        }
        return PickTextResolution(
            a11yText = a11yText,
            ocrText = ocrText,
            activeSource = preferredSource(a11yText, ocrText),
            ocrAvailable = ocrReady,
        )
    }

    private suspend fun recognizeBitmap(
        context: Context,
        ocrModelId: String,
        screenshot: Bitmap,
    ): String? {
        return withContext(Dispatchers.Default) {
            RegionalScreenshotOcr.recognizeBitmapPublic(context, ocrModelId, screenshot)
        }?.takeIf { it.isNotBlank() }
            ?.let(AccessibilityTextExtractor::dedupeTextLines)
    }

    private fun preferredSource(a11yText: String?, ocrText: String?): PickResultTextSource {
        if (ocrText.isNullOrBlank()) return PickResultTextSource.A11Y
        if (a11yText.isNullOrBlank()) return PickResultTextSource.OCR
        if (AccessibilityTextExtractor.isWeakA11yPickResult(a11yText)) return PickResultTextSource.OCR
        val a11yLongest = a11yText.lines().maxOfOrNull { it.trim().length } ?: 0
        val ocrLongest = ocrText.lines().maxOfOrNull { it.trim().length } ?: 0
        return if (ocrLongest > a11yLongest) PickResultTextSource.OCR else PickResultTextSource.A11Y
    }

    private fun ocrReady(context: Context, ocrFallbackEnabled: Boolean, ocrModelId: String): Boolean {
        if (!ocrFallbackEnabled || ocrModelId.isBlank()) return false
        return OcrDependencyAccess.modelRepository(context)?.isInstalled(ocrModelId) == true
    }

    private suspend fun <T> withOverlaysHiddenForCapture(block: suspend () -> T): T {
        withContext(Dispatchers.Main.immediate) {
            FloatBallPickResultPanel.suppressForScreenshotCapture()
            FloatBallOverlay.suppressForScreenshotCapture()
        }
        delay(CAPTURE_HIDE_DELAY_MS)
        return try {
            block()
        } finally {
            withContext(Dispatchers.Main.immediate) {
                FloatBallOverlay.restoreAfterScreenshotCapture()
                FloatBallPickResultPanel.restoreAfterScreenshotCapture()
            }
        }
    }

    private suspend fun captureRectBitmap(
        service: AccessibilityService,
        rect: Rect,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            withContext(Dispatchers.Default) {
                RegionalScreenshotOcr.captureRectBitmap(service, rect)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "capture rect failed", error)
            null
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
