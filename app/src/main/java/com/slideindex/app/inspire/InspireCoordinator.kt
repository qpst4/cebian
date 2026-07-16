package com.slideindex.app.inspire

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.AccessibilityTextExtractor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GestureEVO Inspire pick pipeline: parallel accessibility + screenshot, then result UI.
 */
object InspireCoordinator {
    private const val CAPTURE_HIDE_DELAY_MS = 50L
    private const val TRANSITION_MAX_WAIT_MS = 250L
    private const val SCREENSHOT_TIMEOUT_MS = 2_000L
    /** Regional / preview a11y: abandon waiting after this (work may continue in background). */
    private const val A11Y_TIMEOUT_MS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Dedicated pick thread; avoids [Dispatchers.Default] starvation from a11y/OCR/app work. */
    private val pickDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PickPipeline").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val ocrDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PickOcr").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    /** Isolated from [Dispatchers.Default] so abandoned a11y work cannot starve picks. */
    private val a11yScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        if (!pickInFlight.compareAndSet(false, true)) {
            PickPerf.mark("pick_rejected", "inFlight=true")
            return
        }
        scope.launch(pickDispatcher) {
            try {
                val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
                val safeRect = FloatBallOcrRegions.clampToScreen(rect, screenWidth, screenHeight)
                val ocrReady = isOcrReady(context, ocrFallbackEnabled, ocrModelId)
                val deferOcr = ocrReady
                PickPerf.mark("pick_rect_ready", "preview=$previewBoundsPick deferOcr=$deferOcr")
                val result = processScreenContent(
                    service = service,
                    context = context,
                    dragSelectRect = safeRect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    previewBoundsPick = previewBoundsPick,
                    presentPickPanel = true,
                    deferOcr = deferOcr,
                )
                withContext(Dispatchers.Main.immediate) { onResult(result) }
                if (deferOcr) {
                    launchDeferredOcr(
                        context = context,
                        ocrModelId = ocrModelId,
                        result = result,
                        switchToOcrOnComplete = false,
                    )
                }
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
        if (!pickInFlight.compareAndSet(false, true)) {
            PickPerf.mark("pick_rejected", "inFlight=true")
            return
        }
        scope.launch(pickDispatcher) {
            try {
                PickPerf.mark("pickOnRelease_start", "regionalRect=$regionalRect ocr=$ocrFallbackEnabled")
                val metrics = context.resources.displayMetrics
                val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
                val rect = if (regionalRect) {
                    rectBetween(startX, startY, endX, endY).let {
                        FloatBallOcrRegions.clampToScreen(it, screenWidth, screenHeight)
                    }
                } else {
                    FloatBallOcrRegions.expandPoint(metrics, startX, startY, screenWidth, screenHeight)
                }
                val ocrReady = isOcrReady(context, ocrFallbackEnabled, ocrModelId)
                val deferOcr = regionalRect && ocrReady
                PickPerf.mark("pick_rect_ready", "regional=$regionalRect deferOcr=$deferOcr")
                val result = processScreenContent(
                    service = service,
                    context = context,
                    dragSelectRect = rect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    presentPickPanel = true,
                    regionalRectPick = regionalRect,
                    deferOcr = deferOcr,
                )
                withContext(Dispatchers.Main.immediate) { onResult(result) }
                if (deferOcr) {
                    launchDeferredOcr(
                        context = context,
                        ocrModelId = ocrModelId,
                        result = result,
                        switchToOcrOnComplete = regionalRect,
                    )
                }
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
        regionalRectPick: Boolean = false,
        deferOcr: Boolean = false,
    ): FloatBallPickResult {
        PickPerf.mark(
            "processScreenContent_start",
            "rect=$dragSelectRect preview=$previewBoundsPick regional=$regionalRectPick deferOcr=$deferOcr ocr=$ocrFallbackEnabled",
        )
        InspireDataHolder.clear()
        InspireDataHolder.setDragRect(Rect(dragSelectRect))

        val startUptimeMs = SystemClock.uptimeMillis()
        var a11yTimedOut = false
        val a11yDeferred = launchA11yCollect(
            service = service,
            dragSelectRect = dragSelectRect,
            previewBoundsPick = previewBoundsPick,
            regionalRectPick = regionalRectPick,
        )

        PickPerf.mark("overlays_hide_start")
        withOverlaysHiddenForCapture {
            PickPerf.mark("overlays_hide_end")
            val shotStart = SystemClock.elapsedRealtime()
            PickPerf.mark("screenshot_start")
            val fullBitmap = RegionalScreenshotOcr.captureDisplayBitmapPublic(service)
            PickPerf.markStepDuration(
                "screenshot_capture_done",
                shotStart,
                "bitmap=${fullBitmap != null}",
            )
            if (fullBitmap != null) {
                val managed = ManagedBitmap.from(fullBitmap)
                try {
                    val cropStart = SystemClock.elapsedRealtime()
                    val cropped = AccessibilityNodeManager.cropByRect(managed, dragSelectRect)
                    PickPerf.markStepDuration(
                        "screenshot_crop_done",
                        cropStart,
                        "cropped=${cropped != null}",
                    )
                    if (cropped != null) {
                        InspireDataHolder.replaceScreenshotBitmap(cropped)
                        cropped.close()
                    }
                } finally {
                    managed.close()
                }
            } else {
                PickPerf.markStepDuration("screenshot_end", shotStart, "no_bitmap")
            }
            if (fullBitmap != null) {
                PickPerf.markStepDuration("screenshot_end", shotStart)
            }
        }

        val a11yWaitStart = SystemClock.elapsedRealtime()
        val collected = withTimeoutOrNull(A11Y_TIMEOUT_MS) { a11yDeferred.await() }.orEmpty()
        if (collected.isEmpty()) {
            a11yTimedOut = true
            PickPerf.markStepDuration(
                "a11y_wait_timeout",
                a11yWaitStart,
                "limit=${A11Y_TIMEOUT_MS}ms",
            )
        } else {
            PickPerf.markStepDuration("a11y_wait_done", a11yWaitStart, "words=${collected.size}")
        }
        InspireDataHolder.setAccessibilityContent(collected)

        val elapsed = SystemClock.uptimeMillis() - startUptimeMs
        if (!a11yTimedOut && elapsed < TRANSITION_MAX_WAIT_MS) {
            val delayMs = TRANSITION_MAX_WAIT_MS - elapsed
            PickPerf.mark("transition_delay", "delayMs=$delayMs")
            delay(delayMs)
        }

        PickPerf.mark("buildPickResult_start")
        val result = buildPickResult(
            context = context,
            dragSelectRect = dragSelectRect,
            ocrFallbackEnabled = ocrFallbackEnabled,
            ocrModelId = ocrModelId,
            ocrOnly = a11yTimedOut,
            deferOcr = deferOcr,
            previewBoundsPick = previewBoundsPick,
            regionalRectPick = regionalRectPick,
        )
        PickPerf.mark("buildPickResult_end", "source=${result.activeSource}")
        return result
    }

    private fun launchA11yCollect(
        service: AccessibilityService,
        dragSelectRect: Rect,
        previewBoundsPick: Boolean,
        regionalRectPick: Boolean,
    ): Deferred<List<String>> = a11yScope.async {
        val a11yStart = SystemClock.elapsedRealtime()
        val path = when {
            previewBoundsPick -> "collectTextForPreviewRect"
            regionalRectPick -> "collectTextInRect"
            else -> "collectTextForPreviewRect"
        }
        PickPerf.mark("a11y_start", "path=$path")
        val words = when {
            previewBoundsPick -> {
                runInterruptible {
                    AccessibilityTextExtractor.collectTextForPreviewRect(service, dragSelectRect)
                }.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { listOf(it) }
                    .orEmpty()
            }
            regionalRectPick -> {
                runInterruptible {
                    AccessibilityTextExtractor.collectTextInRect(service, dragSelectRect)
                }.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { listOf(it) }
                    .orEmpty()
            }
            else -> emptyList()
        }
        PickPerf.markStepDuration("a11y_end", a11yStart, "words=${words.size}")
        words
    }

    private fun isOcrReady(
        context: Context,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
    ): Boolean {
        return ocrFallbackEnabled &&
            ocrModelId.isNotBlank() &&
            OcrDependencyAccess.modelRepository(context)?.isInstalled(ocrModelId) == true
    }

    private fun launchDeferredOcr(
        context: Context,
        ocrModelId: String,
        result: FloatBallPickResult,
        switchToOcrOnComplete: Boolean,
    ) {
        val bitmap = result.screenshot ?: return
        scope.launch(ocrDispatcher) {
            val ocrStart = SystemClock.elapsedRealtime()
            PickPerf.mark("ocr_async_start", "model=$ocrModelId")
            val ocrText = RegionalScreenshotOcr.recognizeBitmapPublic(
                context,
                ocrModelId,
                bitmap,
            )?.trim()?.takeIf { it.isNotEmpty() }
            PickPerf.markStepDuration("ocr_async_end", ocrStart, "len=${ocrText?.length ?: 0}")
            withContext(Dispatchers.Main.immediate) {
                if (!ocrText.isNullOrBlank()) {
                    FloatBallPickResultPanel.updateOcrText(ocrText, switchToOcrOnComplete)
                } else {
                    FloatBallPickResultPanel.finishOcrPending()
                }
            }
        }
    }

    private suspend fun buildPickResult(
        context: Context,
        dragSelectRect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        ocrOnly: Boolean = false,
        deferOcr: Boolean = false,
        previewBoundsPick: Boolean = false,
        regionalRectPick: Boolean = false,
    ): FloatBallPickResult {
        val rawAccessibility = InspireDataHolder.accessibilityContent.orEmpty()
        val a11yText = rawAccessibility.joinToString(separator = "").trim().takeIf { it.isNotEmpty() }

        val ocrReady = isOcrReady(context, ocrFallbackEnabled, ocrModelId)

        val ocrText = if (ocrReady && !deferOcr) {
            val ocrStart = SystemClock.elapsedRealtime()
            PickPerf.mark("ocr_start", "model=$ocrModelId")
            val screenshot = InspireDataHolder.acquireScreenshotBitmap()
            val recognized = try {
                screenshot?.requireBitmap()?.let { bitmap ->
                    withContext(ocrDispatcher) {
                        RegionalScreenshotOcr.recognizeBitmapPublic(context, ocrModelId, bitmap)
                    }?.trim()?.takeIf { it.isNotEmpty() }
                }
            } finally {
                screenshot?.close()
            }
            PickPerf.markStepDuration("ocr_end", ocrStart, "len=${recognized?.length ?: 0}")
            recognized
        } else {
            PickPerf.mark(
                "ocr_skipped",
                "ocrFallback=$ocrFallbackEnabled model=$ocrModelId ocrOnly=$ocrOnly deferOcr=$deferOcr",
            )
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
            deferOcr && regionalRectPick -> PickResultTextSource.OCR
            deferOcr && previewBoundsPick && !a11yText.isNullOrBlank() -> PickResultTextSource.A11Y
            deferOcr -> PickResultTextSource.OCR
            ocrOnly && !ocrText.isNullOrBlank() -> PickResultTextSource.OCR
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
            ocrPending = deferOcr && ocrReady,
            ocrPreferSwitchOnComplete = deferOcr && regionalRectPick,
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
