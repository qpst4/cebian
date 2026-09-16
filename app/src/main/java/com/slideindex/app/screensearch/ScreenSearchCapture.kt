package com.slideindex.app.screensearch

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.slideindex.app.inspire.AccessibilityNodeManager
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.inspire.InspireFloating
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.SlideIndexAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

object ScreenSearchCapture {
    private const val CAPTURE_HIDE_DELAY_MS = 50L

    data class CaptureResult(
        val matches: List<Rect>,
        val failed: Boolean,
    )

    enum class ScrollDirection { DOWN, UP }

    fun buildRevealCropRect(width: Int, height: Int, direction: ScrollDirection): Rect {
        val bandH = (height * REVEAL_SCAN_RATIO).toInt().coerceIn(32, height)
        return when (direction) {
            ScrollDirection.DOWN -> Rect(0, (height - bandH).coerceAtLeast(0), width, height)
            ScrollDirection.UP -> Rect(0, 0, width, bandH)
        }
    }

    suspend fun captureAndMatchAccessibility(
        service: AccessibilityService,
        keyword: String,
        reducedScope: Boolean,
        direction: ScrollDirection,
        screenWidth: Int,
        screenHeight: Int,
    ): CaptureResult = withContext(Dispatchers.Main.immediate) {
        val root = service.rootInActiveWindow
        if (root == null) {
            return@withContext CaptureResult(emptyList(), failed = true)
        }
        val scanRect = if (reducedScope) {
            buildRevealCropRect(screenWidth, screenHeight, direction)
        } else {
            Rect(0, 0, screenWidth, screenHeight)
        }
        val matches = mutableListOf<Rect>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (!node.isVisibleToUser) {
                    if (node !== root) node.recycle()
                    continue
                }
                val rawText = node.text?.toString()?.trim()
                    ?: node.contentDescription?.toString()?.trim()
                    ?: ""
                if (rawText.isNotEmpty() && rawText.contains(keyword, ignoreCase = true)) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (Rect.intersects(bounds, scanRect)) {
                        matches.add(bounds)
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::addLast)
                }
                if (node !== root) node.recycle()
            }
        } catch (_: Throwable) {
            return@withContext CaptureResult(emptyList(), failed = true)
        }
        CaptureResult(matches, failed = false)
    }

    suspend fun captureAndMatchOcr(
        service: SlideIndexAccessibilityService,
        keyword: String,
        reducedScope: Boolean,
        direction: ScrollDirection,
        screenWidth: Int,
        screenHeight: Int,
        ocrModelId: String,
        hideSelfForCapture: () -> Unit,
        restoreSelfAfterCapture: () -> Unit,
    ): CaptureResult {
        val catalog = OcrDependencyAccess.catalogProvider(service)
        val engine = catalog?.findModel(ocrModelId)?.engine
        if (ocrModelId.isBlank() || engine != OcrEngines.PPOCR) {
            return CaptureResult(emptyList(), failed = true)
        }
        val inference = OcrDependencyAccess.inferenceService(service) ?: return CaptureResult(emptyList(), failed = true)

        var bitmap: Bitmap? = null
        var offsetX = 0
        var offsetY = 0
        return try {
            withOverlaysHiddenForCapture(hideSelfForCapture, restoreSelfAfterCapture) {
                bitmap = RegionalScreenshotOcr.captureDisplayBitmapPublic(service)
            }
            val full = bitmap ?: return CaptureResult(emptyList(), failed = true)
            val working: Bitmap
            if (reducedScope) {
                val cropRect = buildRevealCropRect(screenWidth, screenHeight, direction)
                offsetX = cropRect.left
                offsetY = cropRect.top
                val cropped = AccessibilityNodeManager.cropByRect(full, cropRect)
                    ?: return CaptureResult(emptyList(), failed = true)
                if (cropped !== full) {
                    full.recycle()
                }
                working = cropped
            } else {
                working = full
            }
            val lines = inference.recognizePpOcrLines(ocrModelId, working) ?: return CaptureResult(emptyList(), failed = true)
            val matches = lines
                .filter { line ->
                    line.text.trim().contains(keyword, ignoreCase = true)
                }
                .map { line ->
                    Rect(line.bounds).apply {
                        offset(offsetX, offsetY)
                    }
                }
            CaptureResult(matches, failed = false)
        } catch (_: Throwable) {
            CaptureResult(emptyList(), failed = true)
        } finally {
            bitmap?.recycle()
        }
    }

    private suspend fun <T> withOverlaysHiddenForCapture(
        hideSelf: () -> Unit,
        restoreSelf: () -> Unit,
        block: suspend () -> T,
    ): T {
        withContext(Dispatchers.Main.immediate) {
            FloatingPointerOverlayWindow.suppressForScreenshotCapture()
            FloatBallOverlay.suppressForScreenshotCapture()
            InspireFloating.hide()
            hideSelf()
        }
        delay(CAPTURE_HIDE_DELAY_MS)
        return try {
            block()
        } finally {
            withContext(Dispatchers.Main.immediate) {
                FloatingPointerOverlayWindow.restoreAfterScreenshotCapture()
                FloatBallOverlay.restoreAfterScreenshotCapture()
                restoreSelf()
            }
        }
    }

    const val MAX_ATTEMPTS = 60
    const val MAX_CAPTURE_FAILURES = 3
    const val PRE_START_DELAY_MS = 250L
    const val SCROLL_SETTLE_DELAY_MS = 220L
    const val REVEAL_SCAN_RATIO = 0.65f
    const val SCROLL_DURATION_MS = 200L
}
