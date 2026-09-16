package com.slideindex.app.screensearch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.R
import com.slideindex.app.inspire.BaseFloatingWindow
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.screensearch.ScreenSearchCapture.ScrollDirection
import com.slideindex.app.gesture.PointerSwipeDirection
import com.slideindex.app.service.SlideIndexAccessibilityGestureInjector
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
class ScreenSearchFloating private constructor(
    private val service: SlideIndexAccessibilityService,
) : BaseFloatingWindow(service, scope, TAG) {

    private val panelState = ScreenSearchPanelState()
    private var panelRequestFocus by mutableStateOf(false)
    private var indicatorSearching by mutableStateOf(false)
    private var indicatorSuccess by mutableStateOf(false)

    private val panelOwner = OverlayComposeOwner()
    private val indicatorOwner = OverlayComposeOwner()

    private val panelParams = WindowManager.LayoutParams()
    private val indicatorParams = WindowManager.LayoutParams()
    private val highlightParams = WindowManager.LayoutParams()

    private val highlightView = ScreenSearchHighlightView(
        OverlayCompose.themedContext(service.applicationContext)
    )

    private val panelComposeView: ComposeView = createPanelComposeView()
    private val indicatorComposeView: ComposeView = createIndicatorComposeView()

    private var searchJob: Job? = null
    private var panelAttached = false
    private var indicatorAttached = false
    private var highlightAttached = false
    private var forceFullScan = true

    init {
        panelParams.flags = PANEL_FLAGS_INTERACTIVE
        configureAccessibilityOverlay(
            panelParams,
            flags = PANEL_FLAGS_INTERACTIVE,
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
        panelParams.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        val indicatorWidth = (service.resources.displayMetrics.density * 56f).toInt()
        val indicatorMargin = (service.resources.displayMetrics.density * 10f).toInt()
        configureAccessibilityOverlay(
            indicatorParams,
            flags = INDICATOR_FLAGS,
            width = indicatorWidth,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.CENTER_VERTICAL or Gravity.END,
            x = indicatorMargin,
            y = 0,
        )

        configureAccessibilityOverlay(
            highlightParams,
            flags = HIGHLIGHT_FLAGS,
        )
        highlightView.visibility = View.GONE
        panelComposeView.visibility = View.GONE
        indicatorComposeView.visibility = View.GONE
    }

    private fun themeSettings(): AppSettings = service.deps.settingsRepository.readSnapshot()

    private fun createPanelComposeView(): ComposeView {
        val context = OverlayCompose.themedContext(service.applicationContext)
        return OverlayCompose.createComposeView(context, panelOwner).apply {
            setContent {
                ModuleTheme(settings = themeSettings()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ScreenSearchPanelCard(
                            state = panelState,
                            requestInitialFocus = panelRequestFocus,
                            onStartScroll = { direction -> startSearch(direction) },
                            onClose = { hide() },
                        )
                    }
                }
            }
        }
    }

    private fun createIndicatorComposeView(): ComposeView {
        val context = OverlayCompose.themedContext(service.applicationContext)
        return OverlayCompose.createComposeView(context, indicatorOwner).apply {
            setContent {
                ModuleTheme(settings = themeSettings()) {
                    ScreenSearchIndicatorCard(
                        searching = indicatorSearching,
                        showSuccess = indicatorSuccess,
                        onDismissOrStop = {
                            if (indicatorSuccess) {
                                dismissResults()
                            } else {
                                stopSearchAndShowPanel()
                            }
                        },
                        onContinueUp = { continueSearch(ScrollDirection.UP) },
                        onContinueDown = { continueSearch(ScrollDirection.DOWN) },
                    )
                }
            }
        }
    }

    fun isPanelVisible(): Boolean =
        panelAttached && panelComposeView.isAttachedToWindow && panelComposeView.visibility == View.VISIBLE

    fun togglePanel() {
        when {
            isPanelVisible() -> hide()
            highlightView.hasRects() -> dismissResults()
            else -> show()
        }
    }

    fun show() {
        scope.launch(Dispatchers.Main.immediate) {
            ensureViewsAttached()
            highlightView.hideRects()
            panelRequestFocus = true
            panelComposeView.visibility = View.VISIBLE
            indicatorComposeView.visibility = View.GONE
            indicatorSearching = false
            indicatorSuccess = false
            panelState.isSearching = false
            updateWindowFocus(panelVisible = true)
        }
    }

    private fun hideSoftInput() {
        val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val windowToken = panelComposeView.findFocus()?.windowToken ?: panelComposeView.windowToken
        if (windowToken != null) {
            imm?.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    fun hide() {
        scope.launch(Dispatchers.Main.immediate) {
            hideSoftInput()
            stopSearch()
            panelComposeView.visibility = View.GONE
            indicatorComposeView.visibility = View.GONE
            highlightView.hideRects()
            panelRequestFocus = false
            detachAllIfNeeded()
        }
    }

    fun destroy() {
        scope.launch(Dispatchers.Main.immediate) {
            hideSoftInput()
            stopSearch()
            panelComposeView.visibility = View.GONE
            indicatorComposeView.visibility = View.GONE
            highlightView.hideRects()
            detachAllIfNeeded()
            panelOwner.destroy()
            indicatorOwner.destroy()
            synchronized(Companion) {
                if (instance === this@ScreenSearchFloating) instance = null
            }
        }
    }

    private fun startSearch(direction: ScrollDirection = panelState.scrollDirection) {
        val keyword = resolveSearchKeyword() ?: return
        panelState.scrollDirection = direction
        launchSearchJob(
            keyword,
            scrollFirst = false,
            preStartDelay = true,
            resetFullScan = true,
        )
    }

    private fun continueSearch(direction: ScrollDirection) {
        val keyword = resolveSearchKeyword() ?: return
        panelState.scrollDirection = direction
        launchSearchJob(
            keyword,
            scrollFirst = true,
            preStartDelay = false,
            resetFullScan = false,
        )
    }

    private fun resolveSearchKeyword(): String? {
        val keyword = panelState.keyword.trim()
        if (keyword.isEmpty()) {
            Toast.makeText(service, R.string.screen_search_empty_keyword, Toast.LENGTH_SHORT).show()
            return null
        }
        if (!panelState.accessibilityMode) {
            val modelId = service.deps.settingsRepository.readSnapshot().floatBallOcrModelId
            val engine = OcrDependencyAccess.catalogProvider(service)?.findModel(modelId)?.engine
            if (modelId.isBlank() || engine != OcrEngines.PPOCR) {
                Toast.makeText(service, R.string.screen_search_ocr_requires_ppocr, Toast.LENGTH_LONG).show()
                return null
            }
        }
        panelState.keyword = keyword
        return keyword
    }

    private fun launchSearchJob(
        keyword: String,
        scrollFirst: Boolean,
        preStartDelay: Boolean,
        resetFullScan: Boolean,
    ) {
        searchJob?.cancel()
        hideSoftInput()
        if (resetFullScan) {
            forceFullScan = true
        }
        highlightView.hideRects()
        ensureViewsAttached()
        panelRequestFocus = false
        panelComposeView.visibility = View.GONE
        indicatorComposeView.visibility = View.VISIBLE
        indicatorSuccess = false
        indicatorSearching = true
        panelState.isSearching = true
        updateWindowFocus(panelVisible = false)
        searchJob = scope.launch {
            if (scrollFirst) {
                val scrolled = performScroll(panelState.scrollDirection)
                if (scrolled) {
                    awaitSettleAfterScrollIfNeeded()
                }
            }
            runSearchLoop(keyword, preStartDelay = preStartDelay)
            withContext(Dispatchers.Main.immediate) {
                panelState.isSearching = false
                indicatorSearching = false
            }
        }
    }

    private suspend fun runSearchLoop(
        keyword: String,
        preStartDelay: Boolean = true,
    ) {
        if (preStartDelay) {
            delay(ScreenSearchCapture.PRE_START_DELAY_MS)
        }
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val ocrModelId = service.deps.settingsRepository.readSnapshot().floatBallOcrModelId
        val direction = panelState.scrollDirection
        var captureFailures = 0
        var stallCount = 0
        var reachedBoundary = false

        for (step in 1..ScreenSearchCapture.MAX_ATTEMPTS) {
            if (!coroutineContext.isActive) return
            val reducedScope = useReducedScanScope()
            val result = captureMatch(
                keyword = keyword,
                direction = direction,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                ocrModelId = ocrModelId,
                reducedScope = reducedScope,
            )
            if (result.failed) {
                captureFailures++
                if (captureFailures >= ScreenSearchCapture.MAX_CAPTURE_FAILURES) break
                delay(ScreenSearchCapture.SCROLL_SETTLE_DELAY_MS)
                continue
            }
            captureFailures = 0
            if (result.matches.isNotEmpty()) {
                val rects = if (panelState.accessibilityMode || step == 1) {
                    result.matches
                } else {
                    ScreenSearchCapture.awaitUiSettle(service)
                    val settled = captureMatch(
                        keyword = keyword,
                        direction = direction,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        ocrModelId = ocrModelId,
                        reducedScope = false,
                    )
                    settled.matches.ifEmpty { result.matches }
                }
                withContext(Dispatchers.Main.immediate) {
                    ensureViewsAttached()
                    highlightView.showRects(rects)
                    Toast.makeText(service, R.string.screen_search_found_toast, Toast.LENGTH_SHORT).show()
                    indicatorSearching = false
                    indicatorSuccess = true
                    indicatorComposeView.visibility = View.VISIBLE
                }
                return
            }
            val beforeFingerprint = ScreenSearchCapture.buildScreenMotionFingerprint(service, screenWidth, screenHeight)
            val scrolled = performScroll(direction)
            if (!scrolled) {
                stallCount++
                if (stallCount >= 3) {
                    reachedBoundary = true
                    break
                }
                continue
            }
            forceFullScan = false
            awaitSettleAfterScrollIfNeeded()

            val afterFingerprint = ScreenSearchCapture.buildScreenMotionFingerprint(service, screenWidth, screenHeight)
            if (!beforeFingerprint.isNullOrEmpty() && !afterFingerprint.isNullOrEmpty() && beforeFingerprint == afterFingerprint) {
                stallCount++
                if (stallCount >= 3) {
                    reachedBoundary = true
                    break
                }
            } else {
                stallCount = 0
            }
        }
        withContext(Dispatchers.Main.immediate) {
            val toastRes = if (reachedBoundary) {
                R.string.screen_search_reached_end_toast
            } else {
                R.string.screen_search_not_found_toast
            }
            Toast.makeText(service, toastRes, Toast.LENGTH_SHORT).show()
            stopSearchAndShowPanel()
        }
    }

    private fun useReducedScanScope(): Boolean = false

    private suspend fun awaitSettleAfterScrollIfNeeded() {
        if (!panelState.accessibilityMode) {
            ScreenSearchCapture.awaitUiSettle(service)
        }
    }

    private suspend fun captureMatch(
        keyword: String,
        direction: ScrollDirection,
        screenWidth: Int,
        screenHeight: Int,
        ocrModelId: String,
        reducedScope: Boolean,
    ): ScreenSearchCapture.CaptureResult {
        return if (panelState.accessibilityMode) {
            ScreenSearchCapture.captureAndMatchAccessibility(
                service,
                keyword,
                reducedScope,
                direction,
                screenWidth,
                screenHeight,
            )
        } else {
            ScreenSearchCapture.captureAndMatchOcr(
                service = service,
                keyword = keyword,
                reducedScope = reducedScope,
                direction = direction,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                ocrModelId = ocrModelId,
                hideSelfForCapture = { hideCaptureOverlays() },
                restoreSelfAfterCapture = { restoreCaptureOverlays() },
            )
        }
    }

    private fun hideCaptureOverlays() {
        indicatorComposeView.visibility = View.GONE
        highlightView.visibility = View.GONE
    }

    private fun restoreCaptureOverlays() {
        if (searchJob?.isActive == true) {
            indicatorComposeView.visibility = View.VISIBLE
            indicatorSearching = true
        }
        if (highlightView.hasRects()) {
            highlightView.visibility = View.VISIBLE
        }
    }

    private suspend fun performScroll(direction: ScrollDirection): Boolean {
        val metrics = service.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val x = w / 2f
        val y = h / 2f
        if (panelState.accessibilityMode) {
            val pointerDir = when (direction) {
                ScrollDirection.DOWN -> PointerSwipeDirection.DOWN
                ScrollDirection.UP -> PointerSwipeDirection.UP
            }
            val scrolled = withContext(Dispatchers.Main.immediate) {
                SlideIndexAccessibilityGestureInjector.performScrollableNodeAction(
                    service,
                    x,
                    y,
                    pointerDir,
                )
            }
            if (scrolled) {
                delay(ScreenSearchCapture.A11Y_SCROLL_LAYOUT_DELAY_MS)
                return true
            }
        }
        val gestureOk = performPointerSwipeScroll(x, h, direction, gestureDurationMs())
        if (gestureOk && panelState.accessibilityMode) {
            delay(ScreenSearchCapture.A11Y_SCROLL_LAYOUT_DELAY_MS)
        }
        return gestureOk
    }

    private fun gestureDurationMs(): Long {
        return if (panelState.accessibilityMode) {
            ScreenSearchCapture.SCROLL_GESTURE_FALLBACK_DURATION_MS
        } else {
            ScreenSearchCapture.SCROLL_DURATION_MS
        }
    }

    private suspend fun performPointerSwipeScroll(
        x: Float,
        screenHeight: Float,
        direction: ScrollDirection,
        durationMs: Long,
    ): Boolean {
        val (y1, y2) = ScreenSearchCapture.scrollFingerYPositions(screenHeight, direction)
        return suspendCancellableCoroutine { cont ->
            SlideIndexAccessibilityService.dispatchPointerDragNoFling(
                startX = x,
                startY = y1,
                endX = x,
                endY = y2,
                dragDurationMs = durationMs,
                holdDurationMs = 140L,
            ) { ok ->
                if (cont.isActive) cont.resume(ok)
            }
        }
    }

    private fun dismissResults() {
        scope.launch(Dispatchers.Main.immediate) {
            highlightView.hideRects()
            indicatorComposeView.visibility = View.GONE
            indicatorSuccess = false
            indicatorSearching = false
            detachAllIfNeeded()
        }
    }

    private fun stopSearchAndShowPanel() {
        stopSearch()
        scope.launch(Dispatchers.Main.immediate) {
            highlightView.hideRects()
            ensureViewsAttached()
            panelRequestFocus = true
            panelComposeView.visibility = View.VISIBLE
            indicatorComposeView.visibility = View.GONE
            indicatorSearching = false
            indicatorSuccess = false
            panelState.isSearching = false
            updateWindowFocus(panelVisible = true)
        }
    }

    private fun stopSearch() {
        searchJob?.cancel(CancellationException("screen search stopped"))
        searchJob = null
        panelState.isSearching = false
        indicatorSearching = false
    }

    private fun ensureViewsAttached() {
        if (!panelAttached) {
            panelAttached = addViewSafely(panelComposeView, panelParams)
        }
        if (!indicatorAttached) {
            indicatorAttached = addViewSafely(indicatorComposeView, indicatorParams)
        }
        if (!highlightAttached) {
            highlightAttached = addViewSafely(highlightView, highlightParams)
        }
    }

    private fun updateWindowFocus(panelVisible: Boolean) {
        val nextFlags = if (panelVisible) {
            panelParams.flags and PANEL_FOCUS_MASK.inv()
        } else {
            panelParams.flags or PANEL_FOCUS_MASK
        }
        if (nextFlags == panelParams.flags) return
        panelParams.flags = nextFlags
        updateViewLayoutSafely(panelComposeView, panelParams)
    }

    private fun detachAllIfNeeded() {
        if (panelComposeView.isAttachedToWindow) detachViewSafely(panelComposeView)
        if (indicatorComposeView.isAttachedToWindow) detachViewSafely(indicatorComposeView)
        if (highlightView.isAttachedToWindow) detachViewSafely(highlightView)
        panelAttached = false
        indicatorAttached = false
        highlightAttached = false
    }

    companion object {
        private const val TAG = "ScreenSearchFloating"

        private const val OVERLAY_BASE =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        private const val HIGHLIGHT_FLAGS =
            OVERLAY_BASE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        private const val PANEL_FOCUS_MASK =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        private const val PANEL_FLAGS_INTERACTIVE =
            OVERLAY_BASE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        private const val INDICATOR_FLAGS =
            OVERLAY_BASE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        @Volatile
        private var instance: ScreenSearchFloating? = null

        fun get(service: SlideIndexAccessibilityService): ScreenSearchFloating {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "ScreenSearchFloating must be used on the main thread"
            }
            return instance ?: synchronized(this) {
                instance ?: ScreenSearchFloating(service).also { instance = it }
            }
        }

        fun destroy() {
            instance?.destroy()
            instance = null
        }
    }
}
