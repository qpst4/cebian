package com.slideindex.app.inspire

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.AccessibilityTextExtractor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GestureEVO Inspire pick pipeline: parallel accessibility + screenshot, then result UI.
 */
object InspireCoordinator {
    private const val CAPTURE_HIDE_DELAY_MS = 50L
    private const val TRANSITION_MAX_WAIT_MS = 250L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pickInFlight = AtomicBoolean(false)

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
                val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
                val safeRect = FloatBallOcrRegions.clampToScreen(rect, screenWidth, screenHeight)
                val result = processScreenContent(
                    service = service,
                    context = context,
                    dragSelectRect = safeRect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    previewBoundsPick = previewBoundsPick,
                    presentPickPanel = true,
                )
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
                val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
                val rect = if (regionalRect) {
                    rectBetween(startX, startY, endX, endY).let {
                        FloatBallOcrRegions.clampToScreen(it, screenWidth, screenHeight)
                    }
                } else {
                    FloatBallOcrRegions.expandPoint(metrics, startX, startY, screenWidth, screenHeight)
                }
                val result = processScreenContent(
                    service = service,
                    context = context,
                    dragSelectRect = rect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    presentPickPanel = true,
                )
                onResult(result)
            } finally {
                pickInFlight.set(false)
            }
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
                val metrics = context.resources.displayMetrics
                val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
                val rect = FloatBallOcrRegions.expandPoint(metrics, rawX, rawY, screenWidth, screenHeight)
                val result = processScreenContent(
                    service = service,
                    context = context,
                    dragSelectRect = rect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                )
                onResult(result.text)
            } finally {
                pickInFlight.set(false)
            }
        }
    }

    suspend fun processScreenContent(
        service: AccessibilityService,
        context: Context,
        dragSelectRect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        previewBoundsPick: Boolean = false,
        presentPickPanel: Boolean = false,
    ): FloatBallPickResult = withContext(Dispatchers.Default) {
        if (presentPickPanel) {
            withContext(Dispatchers.Main.immediate) {
                FloatBallPickResultPanel.dismiss()
            }
        }
        InspireDataHolder.clear()
        InspireDataHolder.setDragRect(Rect(dragSelectRect))

        val startUptimeMs = SystemClock.uptimeMillis()
        withOverlaysHiddenForCapture {
            coroutineScope {
                val accessibilityJob = async {
                    val words = if (previewBoundsPick) {
                        AccessibilityTextExtractor.collectTextForPreviewRect(service, dragSelectRect)
                            .trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { listOf(it) }
                            .orEmpty()
                    } else {
                        val collected = mutableListOf<String>()
                        val nodes = AccessibilityNodeManager.getScreenContent(service, dragSelectRect)
                        nodes.forEach { node ->
                            node.content?.takeIf { it.isNotEmpty() }?.let(collected::add)
                        }
                        collected
                    }
                    InspireDataHolder.setAccessibilityContent(words)
                }
                val screenshotJob = async {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@async
                    val fullBitmap = RegionalScreenshotOcr.captureDisplayBitmapPublic(service) ?: return@async
                    val managed = ManagedBitmap.from(fullBitmap)
                    try {
                        val cropped = AccessibilityNodeManager.cropByRect(managed, dragSelectRect)
                        if (cropped != null) {
                            InspireDataHolder.replaceScreenshotBitmap(cropped)
                            cropped.close()
                        }
                    } finally {
                        managed.close()
                    }
                }
                accessibilityJob.await()
                screenshotJob.await()
            }
        }

        if (presentPickPanel) {
            withContext(Dispatchers.Main.immediate) {
                FloatBallPickResultPanel.showLoading(context)
            }
        }

        val elapsed = SystemClock.uptimeMillis() - startUptimeMs
        if (elapsed < TRANSITION_MAX_WAIT_MS) {
            delay(TRANSITION_MAX_WAIT_MS - elapsed)
        }

        buildPickResult(context, dragSelectRect, ocrFallbackEnabled, ocrModelId)
    }

    private suspend fun buildPickResult(
        context: Context,
        dragSelectRect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
    ): FloatBallPickResult {
        val rawAccessibility = InspireDataHolder.accessibilityContent.orEmpty()
        val a11yText = rawAccessibility.joinToString(separator = "").trim().takeIf { it.isNotEmpty() }

        val ocrReady = ocrFallbackEnabled &&
            ocrModelId.isNotBlank() &&
            OcrDependencyAccess.modelRepository(context)?.isInstalled(ocrModelId) == true

        val ocrText = if (ocrReady) {
            val screenshot = InspireDataHolder.acquireScreenshotBitmap()
            try {
                screenshot?.requireBitmap()?.let { bitmap ->
                    withContext(Dispatchers.Default) {
                        RegionalScreenshotOcr.recognizeBitmapPublic(context, ocrModelId, bitmap)
                    }?.trim()?.takeIf { it.isNotEmpty() }
                }
            } finally {
                screenshot?.close()
            }
        } else {
            null
        }

        val screenshotCopy = InspireDataHolder.acquireScreenshotBitmap()?.let { handle ->
            try {
                handle.requireBitmap().copy(handle.requireBitmap().config ?: Bitmap.Config.ARGB_8888, false)
            } finally {
                handle.close()
            }
        }

        val activeSource = when {
            ocrText.isNullOrBlank() -> PickResultTextSource.A11Y
            a11yText.isNullOrBlank() -> PickResultTextSource.OCR
            else -> {
                val a11yLongest = a11yText.lines().maxOfOrNull { it.trim().length } ?: 0
                val ocrLongest = ocrText.lines().maxOfOrNull { it.trim().length } ?: 0
                if (ocrLongest > a11yLongest) PickResultTextSource.OCR else PickResultTextSource.A11Y
            }
        }

        return FloatBallPickResult(
            a11yText = a11yText,
            ocrText = ocrText,
            screenshot = screenshotCopy,
            screenRect = Rect(dragSelectRect),
            activeSource = activeSource,
            ocrAvailable = ocrReady,
        )
    }

    private suspend fun <T> withOverlaysHiddenForCapture(block: suspend () -> T): T {
        withContext(Dispatchers.Main.immediate) {
            FloatBallOverlay.suppressForScreenshotCapture()
            InspireFloating.hide()
        }
        delay(CAPTURE_HIDE_DELAY_MS)
        return try {
            block()
        } finally {
            withContext(Dispatchers.Main.immediate) {
                FloatBallOverlay.restoreAfterScreenshotCapture()
            }
        }
    }

    private fun rectBetween(startX: Float, startY: Float, endX: Float, endY: Float): Rect {
        val left = minOf(startX, endX).toInt()
        val top = minOf(startY, endY).toInt()
        val right = maxOf(startX, endX).toInt()
        val bottom = maxOf(startY, endY).toInt()
        return Rect(left, top, right, bottom)
    }
}
