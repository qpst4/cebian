package com.slideindex.app.screensearch

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
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

    /** 单步滑动约为屏高 30%，减轻大步跨过关键字（GEVO 为 0.8↔0.2 约 60%）。 */
    fun scrollFingerYPositions(screenHeight: Float, direction: ScrollDirection): Pair<Float, Float> {
        val near = screenHeight * SCROLL_FINGER_NEAR_EDGE_RATIO
        val far = screenHeight * SCROLL_FINGER_FAR_EDGE_RATIO
        return when (direction) {
            ScrollDirection.DOWN -> Pair(near, far)
            ScrollDirection.UP -> Pair(far, near)
        }
    }

    fun buildRevealCropRect(width: Int, height: Int, direction: ScrollDirection): Rect {
        val bandH = (height * REVEAL_SCAN_RATIO).toInt().coerceIn(32, height)
        return when (direction) {
            ScrollDirection.DOWN -> Rect(0, (height - bandH).coerceAtLeast(0), width, height)
            ScrollDirection.UP -> Rect(0, 0, width, bandH)
        }
    }

    fun normalizeForSearch(text: String): String =
        text.replace("\\s+".toRegex(), "")

    suspend fun captureAndMatchAccessibility(
        service: AccessibilityService,
        keyword: String,
        @Suppress("UNUSED_PARAMETER") reducedScope: Boolean,
        @Suppress("UNUSED_PARAMETER") direction: ScrollDirection,
        screenWidth: Int,
        screenHeight: Int,
    ): CaptureResult = withContext(Dispatchers.Main.immediate) {
        val root = service.rootInActiveWindow
        if (root == null) {
            return@withContext CaptureResult(emptyList(), failed = true)
        }
        val normalizedKeyword = normalizeForSearch(keyword)
        if (normalizedKeyword.isEmpty()) {
            return@withContext CaptureResult(emptyList(), failed = true)
        }
        val screenBounds = Rect(0, 0, screenWidth, screenHeight)
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
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val rawText = text.ifEmpty { desc }
                if (rawText.isNotEmpty()) {
                    val normalizedNodeText = normalizeForSearch(rawText)
                    if (normalizedNodeText.contains(normalizedKeyword, ignoreCase = true)) {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        if (bounds.width() > 0 && bounds.height() > 0 && Rect.intersects(bounds, screenBounds)) {
                            matches.add(bounds)
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::addLast)
                }
                if (node !== root) node.recycle()
            }
        } catch (_: Throwable) {
            return@withContext CaptureResult(emptyList(), failed = true)
        } finally {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node !== root) node.recycle()
            }
            root.recycle()
        }
        CaptureResult(matches, failed = false)
    }

    /** 列表惯性滚动结束后再采样，避免高亮框与停稳后的文字错位。 */
    suspend fun awaitUiSettle(service: AccessibilityService) {
        delay(SCROLL_SETTLE_MIN_DELAY_MS)
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        var lastFingerprint: String? = null
        var stablePolls = 0
        val deadline = SystemClock.uptimeMillis() + SCROLL_SETTLE_MAX_WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            delay(SCROLL_SETTLE_POLL_MS)
            val fingerprint = buildScreenMotionFingerprint(service, screenWidth, screenHeight)
            if (!fingerprint.isNullOrEmpty() && fingerprint == lastFingerprint) {
                stablePolls++
                if (stablePolls >= SCROLL_SETTLE_STABLE_POLLS) return
            } else {
                stablePolls = 0
                lastFingerprint = fingerprint
            }
        }
    }

    fun buildScreenMotionFingerprint(
        service: AccessibilityService,
        screenWidth: Int,
        screenHeight: Int,
    ): String? {
        val root = service.rootInActiveWindow ?: return null
        val viewportTop = (screenHeight * 0.16f).toInt()
        val viewportBottom = (screenHeight * 0.86f).toInt()
        val viewport = Rect(0, viewportTop, screenWidth, viewportBottom)

        val parts = ArrayList<String>(UI_SCROLL_FINGERPRINT_NODES)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        try {
            while (queue.isNotEmpty() && parts.size < UI_SCROLL_FINGERPRINT_NODES) {
                val node = queue.removeFirst()
                if (node.isVisibleToUser) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    // 仅关注屏幕中间可滚动视口区域（排除顶部常驻标题栏与底部操作栏）
                    if (bounds.width() > 0 && bounds.height() > 0 && Rect.intersects(bounds, viewport)) {
                        val text = node.text ?: node.contentDescription
                        // 仅采集叶子节点或包含文本的节点，忽略全屏容器 ViewGroup
                        if (node.childCount == 0 || !text.isNullOrBlank()) {
                            val q = UI_SETTLE_BOUNDS_QUANT_PX
                            val textHash = text?.hashCode() ?: 0
                            parts.add(
                                "${bounds.left / q},${bounds.top / q}," +
                                    "${bounds.right / q},${bounds.bottom / q},$textHash",
                            )
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::addLast)
                }
                if (node !== root) node.recycle()
            }
        } catch (_: Throwable) {
            // fall through
        } finally {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node !== root) node.recycle()
            }
            root.recycle()
        }
        if (parts.isEmpty()) return null
        return parts.joinToString(";")
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
            val normalizedKeyword = normalizeForSearch(keyword)
            val lines = inference.recognizePpOcrLines(ocrModelId, working) ?: return CaptureResult(emptyList(), failed = true)
            val matches = lines
                .filter { line ->
                    val normalizedLine = normalizeForSearch(line.text)
                    normalizedLine.contains(normalizedKeyword, ignoreCase = true)
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
    /** 连续 capture 失败时的重试间隔（对齐 GEVO，不用于滚动后常规取词）。 */
    const val SCROLL_SETTLE_DELAY_MS = 220L
    const val REVEAL_SCAN_RATIO = 0.65f
    const val SCROLL_DURATION_MS = 420L
    /** 无障碍单步滚动后等待布局刷新（给无障碍事件派发和节点树构建留足缓冲）。 */
    const val A11Y_SCROLL_LAYOUT_DELAY_MS = 360L
    /** 无障碍滚动失败时，慢速手势回退，减轻 fling。 */
    const val SCROLL_GESTURE_FALLBACK_DURATION_MS = 420L

    private const val SCROLL_FINGER_NEAR_EDGE_RATIO = 0.68f
    private const val SCROLL_FINGER_FAR_EDGE_RATIO = 0.38f

    private const val SCROLL_SETTLE_MIN_DELAY_MS = 280L
    private const val SCROLL_SETTLE_POLL_MS = 80L
    private const val SCROLL_SETTLE_STABLE_POLLS = 3
    private const val SCROLL_SETTLE_MAX_WAIT_MS = 1_400L
    private const val UI_SCROLL_FINGERPRINT_NODES = 64
    private const val UI_SETTLE_BOUNDS_QUANT_PX = 12
}
