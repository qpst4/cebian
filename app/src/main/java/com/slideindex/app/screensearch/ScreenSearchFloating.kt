package com.slideindex.app.screensearch

import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.Rect
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.os.Looper
import com.slideindex.app.R
import com.slideindex.app.inspire.BaseFloatingWindow
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.service.SlideIndexAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
class ScreenSearchFloating private constructor(
    private val service: SlideIndexAccessibilityService,
) : BaseFloatingWindow(service, scope, TAG) {

    // 与 OverlayComposeDialogHost 一致：勿用 AccessibilityService 直接做 themedContext，避免 Flyme/Android 16 崩溃
    private val uiContext = OverlayCompose.themedContext(service.applicationContext)

    private val panelParams = WindowManager.LayoutParams()
    private val indicatorParams = WindowManager.LayoutParams()
    private val highlightParams = WindowManager.LayoutParams()

    private val highlightView = ScreenSearchHighlightView(uiContext)
    private val panelRoot = buildPanelView()
    private val indicatorRoot = buildIndicatorView()

    private var searchJob: Job? = null
    private var panelAttached = false
    private var indicatorAttached = false
    private var highlightAttached = false

    private var scrollDirection = ScreenSearchCapture.ScrollDirection.DOWN
    private var forceFullScan = true

    init {
        configureAccessibilityOverlay(
            panelParams,
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
        panelParams.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        val indicatorSize = (service.resources.displayMetrics.density * 48f).toInt()
        configureAccessibilityOverlay(
            indicatorParams,
            width = indicatorSize,
            height = indicatorSize,
            gravity = Gravity.TOP or Gravity.END,
            x = (service.resources.displayMetrics.density * 12f).toInt(),
            y = (service.resources.displayMetrics.density * 12f).toInt(),
        )

        configureAccessibilityOverlay(highlightParams)
        highlightView.visibility = View.GONE
    }

    private val keywordInput: EditText = panelRoot.findViewWithTag("keyword")
    private val accessibilitySwitch: CheckBox = panelRoot.findViewWithTag("a11y_switch")
    private val directionGroup: RadioGroup = panelRoot.findViewWithTag("direction")
    private val statusText: TextView = panelRoot.findViewWithTag("status")
    private val indicatorProgress: ProgressBar = indicatorRoot.findViewWithTag("progress")

    private fun buildPanelView(): View {
        val density = service.resources.displayMetrics.density
        val pad = (density * 16f).toInt()
        val root = LinearLayout(uiContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xF0222222.toInt())
        }
        val title = TextView(uiContext).apply {
            text = service.getString(R.string.gesture_action_screen_search)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }
        root.addView(title)
        val input = EditText(uiContext).apply {
            tag = "keyword"
            hint = service.getString(R.string.screen_search_keyword_hint)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x88FFFFFF.toInt())
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    startSearch()
                    true
                } else {
                    false
                }
            }
        }
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val switchRow = LinearLayout(uiContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val switchLabel = TextView(uiContext).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = service.getString(R.string.screen_search_accessibility_mode)
            setTextColor(0xFFFFFFFF.toInt())
        }
        val switch = CheckBox(uiContext).apply {
            tag = "a11y_switch"
            isChecked = true
        }
        switchRow.addView(switchLabel)
        switchRow.addView(switch)
        root.addView(switchRow)

        val dirLabel = TextView(uiContext).apply {
            text = service.getString(R.string.screen_search_scroll_direction)
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(dirLabel)
        val dirGroup = RadioGroup(uiContext).apply {
            tag = "direction"
            orientation = RadioGroup.HORIZONTAL
        }
        val down = RadioButton(uiContext).apply {
            id = View.generateViewId()
            text = service.getString(R.string.screen_search_direction_down)
            isChecked = true
        }
        val up = RadioButton(uiContext).apply {
            id = View.generateViewId()
            text = service.getString(R.string.screen_search_direction_up)
        }
        dirGroup.addView(down)
        dirGroup.addView(up)
        root.addView(dirGroup)
        dirGroup.setOnCheckedChangeListener { _, checkedId ->
            scrollDirection = if (checkedId == up.id) {
                ScreenSearchCapture.ScrollDirection.UP
            } else {
                ScreenSearchCapture.ScrollDirection.DOWN
            }
            if (root.visibility == View.VISIBLE) return@setOnCheckedChangeListener
            startSearch()
        }

        val status = TextView(uiContext).apply {
            tag = "status"
            text = service.getString(R.string.screen_search_status_idle)
            setTextColor(0xAAFFFFFF.toInt())
            textSize = 12f
        }
        root.addView(status)

        val close = Button(uiContext).apply {
            text = service.getString(R.string.screen_search_close)
            setOnClickListener { hide() }
        }
        root.addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun buildIndicatorView(): View {
        val size = (service.resources.displayMetrics.density * 48f).toInt()
        val frame = FrameLayout(uiContext).apply {
            setBackgroundColor(0xE6333333.toInt())
            setOnClickListener { stopSearchAndShowPanel() }
        }
        val progress = ProgressBar(uiContext).apply {
            tag = "progress"
            visibility = View.GONE
        }
        frame.addView(
            progress,
            FrameLayout.LayoutParams(size / 2, size / 2, Gravity.CENTER)
        )
        return frame
    }

    fun isPanelVisible(): Boolean = panelRoot.isAttachedToWindow && panelRoot.visibility == View.VISIBLE

    fun togglePanel() {
        if (isPanelVisible()) hide() else show()
    }

    fun show() {
        scope.launch(Dispatchers.Main.immediate) {
            ensurePanelAttached()
            panelRoot.visibility = View.VISIBLE
            updateUiSearching(false)
            keywordInput.requestFocus()
        }
    }

    fun hide() {
        scope.launch(Dispatchers.Main.immediate) {
            stopSearch()
            panelRoot.visibility = View.GONE
            indicatorRoot.visibility = View.GONE
            highlightView.clearRects()
            detachAllIfNeeded()
        }
    }

    fun destroy() {
        hide()
        synchronized(Companion) {
            if (instance === this) instance = null
        }
    }

    private fun startSearch() {
        val keyword = keywordInput.text?.toString()?.trim() ?: ""
        if (keyword.isEmpty()) {
            Toast.makeText(service, R.string.screen_search_empty_keyword, Toast.LENGTH_SHORT).show()
            return
        }
        if (!accessibilitySwitch.isChecked) {
            val modelId = service.deps.settingsRepository.readSnapshot().floatBallOcrModelId
            val engine = OcrDependencyAccess.catalogProvider(service)?.findModel(modelId)?.engine
            if (modelId.isBlank() || engine != OcrEngines.PPOCR) {
                Toast.makeText(service, R.string.screen_search_ocr_requires_ppocr, Toast.LENGTH_LONG).show()
                return
            }
        }
        searchJob?.cancel()
        forceFullScan = true
        panelRoot.visibility = View.GONE
        ensureIndicatorAttached()
        indicatorRoot.visibility = View.VISIBLE
        updateUiSearching(true)
        searchJob = scope.launch {
            runSearchLoop(keyword)
            updateUiSearching(false)
            updateIndicatorSearching(false)
        }
    }

    private suspend fun runSearchLoop(keyword: String) {
        delay(ScreenSearchCapture.PRE_START_DELAY_MS)
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val ocrModelId = service.deps.settingsRepository.readSnapshot().floatBallOcrModelId
        var captureFailures = 0

        for (step in 1..ScreenSearchCapture.MAX_ATTEMPTS) {
            if (!coroutineContext.isActive) return
            val reducedScope = !forceFullScan
            val result = if (accessibilitySwitch.isChecked) {
                ScreenSearchCapture.captureAndMatchAccessibility(
                    service,
                    keyword,
                    reducedScope,
                    scrollDirection,
                    screenWidth,
                    screenHeight,
                )
            } else {
                ScreenSearchCapture.captureAndMatchOcr(
                    service = service,
                    keyword = keyword,
                    reducedScope = reducedScope,
                    direction = scrollDirection,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    ocrModelId = ocrModelId,
                    hideSelfForCapture = { hideCaptureOverlays() },
                    restoreSelfAfterCapture = { restoreCaptureOverlays() },
                )
            }
            if (result.failed) {
                captureFailures++
                if (captureFailures >= ScreenSearchCapture.MAX_CAPTURE_FAILURES) break
                delay(ScreenSearchCapture.SCROLL_SETTLE_DELAY_MS)
                continue
            }
            captureFailures = 0
            if (result.matches.isNotEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    ensureHighlightAttached()
                    highlightView.showRects(result.matches)
                    Toast.makeText(service, R.string.screen_search_found_toast, Toast.LENGTH_SHORT).show()
                    indicatorRoot.visibility = View.GONE
                }
                return
            }
            val scrolled = performScroll()
            if (!scrolled) break
            forceFullScan = false
            delay(ScreenSearchCapture.SCROLL_SETTLE_DELAY_MS)
        }
    }

    private fun hideCaptureOverlays() {
        indicatorRoot.visibility = View.GONE
        highlightView.visibility = View.GONE
    }

    private fun restoreCaptureOverlays() {
        if (searchJob?.isActive == true) {
            indicatorRoot.visibility = View.VISIBLE
        }
        if (highlightView.hasRects()) {
            highlightView.visibility = View.VISIBLE
        }
    }

    private suspend fun performScroll(): Boolean {
        val metrics = service.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val x = w / 2f
        val (y1, y2) = when (scrollDirection) {
            ScreenSearchCapture.ScrollDirection.DOWN -> Pair(0.8f * h, 0.2f * h)
            ScreenSearchCapture.ScrollDirection.UP -> Pair(0.2f * h, 0.8f * h)
        }
        val path = Path().apply {
            moveTo(x, y1)
            lineTo(x, y2)
        }
        return suspendCancellableCoroutine { cont ->
            SlideIndexAccessibilityService.dispatchPointerSwipePath(
                x,
                y1,
                path,
                ScreenSearchCapture.SCROLL_DURATION_MS,
            ) { ok ->
                if (cont.isActive) cont.resume(ok)
            }
        }
    }

    private fun stopSearchAndShowPanel() {
        stopSearch()
        scope.launch(Dispatchers.Main.immediate) {
            ensurePanelAttached()
            panelRoot.visibility = View.VISIBLE
            indicatorRoot.visibility = View.GONE
            updateUiSearching(false)
        }
    }

    private fun stopSearch() {
        searchJob?.cancel(CancellationException("screen search stopped"))
        searchJob = null
    }

    private fun updateUiSearching(searching: Boolean) {
        statusText.text = if (searching) {
            service.getString(R.string.screen_search_status_searching)
        } else {
            service.getString(R.string.screen_search_status_idle)
        }
        updateIndicatorSearching(searching)
    }

    private fun updateIndicatorSearching(searching: Boolean) {
        indicatorProgress.visibility = if (searching) View.VISIBLE else View.GONE
    }

    private fun ensurePanelAttached() {
        if (panelAttached && panelRoot.isAttachedToWindow) return
        panelAttached = addViewSafely(panelRoot, panelParams)
    }

    private fun ensureIndicatorAttached() {
        if (indicatorAttached && indicatorRoot.isAttachedToWindow) return
        indicatorAttached = addViewSafely(indicatorRoot, indicatorParams)
    }

    private fun ensureHighlightAttached() {
        if (highlightAttached && highlightView.isAttachedToWindow) return
        highlightAttached = addViewSafely(highlightView, highlightParams)
    }

    private fun detachAllIfNeeded() {
        if (panelRoot.isAttachedToWindow) detachViewSafely(panelRoot)
        if (indicatorRoot.isAttachedToWindow) detachViewSafely(indicatorRoot)
        if (highlightView.isAttachedToWindow) detachViewSafely(highlightView)
        panelAttached = false
        indicatorAttached = false
        highlightAttached = false
    }

    companion object {
        private const val TAG = "ScreenSearchFloating"
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
