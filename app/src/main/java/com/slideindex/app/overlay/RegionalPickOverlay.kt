package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.inspire.PickPrefetchCache
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.util.PermissionHelper
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ephemeral regional screenshot & text pick invoked from edge gestures (no persistent float ball).
 */
@SuppressLint("StaticFieldLeak")
object RegionalPickOverlay {
    private const val TAG = "RegionalPickOverlay"
    private const val PAUSE_MS = 280L
    private const val REGIONAL_MOVE_DP = 3f
    private const val REGIONAL_RECT_MIN_SIDE_DP = 3f
    private const val CACHE_REFRESH_MS = 400L
    private const val CACHE_REFRESH_MOVE_DP = 3f
    private const val EDGE_MARGIN_DP = 8f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dragSession = FloatBallDragSession()

    private var windowManager: WindowManager? = null
    private var displayHost: FrameLayout? = null
    private var touchHost: RegionalPickTouchHost? = null
    private var cursorPreviewView: FloatBallCursorPreviewView? = null
    private var ballDragVisualView: FloatBallDragVisualView? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var settings: AppSettings? = null

    private var screenWidth = 0f
    private var screenHeight = 0f

    private var pickAnchor = Offset.Zero
    private var ballCenter = Offset.Zero
    private var lastFingerX = 0f
    private var lastFingerY = 0f
    private var dragSessionArmed = false
    private var selectionStart: Offset? = null
    private var selectionPreviewBounds: Rect? = null
    private var paused = false
    private var regionalActive = false
    private var sessionActive = false

    private var pauseRunnable: Runnable? = null
    private var cacheRefreshRunnable: Runnable? = null
    private var boundsLookupGeneration = 0
    private var lastPauseScheduleX = Float.NaN
    private var lastPauseScheduleY = Float.NaN
    private var lastCacheRefreshX = Float.NaN
    private var lastCacheRefreshY = Float.NaN
    private var liveBoundsLookupGeneration = 0

    var continuedGestureActive = false
        private set

    val isActive: Boolean get() = sessionActive

    fun isConsumingEdgeGestureTouch(): Boolean = continuedGestureActive

    fun show(
        context: Context,
        appSettings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        continueTouch: Boolean,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, appSettings, anchorRawX, anchorRawY, continueTouch) }
            return
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return
        }
        val hostContext = com.slideindex.app.di.OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return
            }

        if (!continueTouch) return
        val x = anchorRawX ?: return
        val y = anchorRawY ?: return

        ensureWindows(hostContext, appSettings)
        FloatBallOverlay.suppressChromeForRegionalPick()
        resetSessionState()
        sessionActive = true
        continuedGestureActive = true
        beginSessionAt(x, y)
        touchHost?.beginContinuedGesture(x, y, SystemClock.uptimeMillis())
        syncCursorAppearance()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        continuedGestureActive = false
        sessionActive = false
        cancelPauseTimer()
        cancelCacheRefresh()
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        FloatBallPreviewBoundsCache.invalidate()
        cursorPreviewView?.visibility = View.GONE
        ballDragVisualView?.release()
        FloatBallOverlay.restoreChromeAfterRegionalPick()
        FloatBallPickResultPanel.releaseWarmUpShell()
    }

    fun forwardContinuedTouch(event: MotionEvent): Boolean {
        if (!continuedGestureActive) return false
        val host = touchHost ?: return false
        val handled = host.forwardContinuedTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            continuedGestureActive = false
        }
        return handled
    }

    private fun ensureWindows(hostContext: Context, appSettings: AppSettings) {
        if (displayHost != null) {
            settings = appSettings
            return
        }
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val dm = hostContext.resources.displayMetrics
        screenWidth = dm.widthPixels.toFloat()
        screenHeight = dm.heightPixels.toFloat()

        val preview = FloatBallCursorPreviewView(overlayContext)
        val ballVisual = FloatBallDragVisualView(overlayContext)
        val display = FrameLayout(overlayContext).apply {
            addView(
                preview,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                ballVisual,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val touch = RegionalPickTouchHost(
            context = overlayContext,
            onTouchAt = { rawX, rawY, action ->
                handleTouch(rawX, rawY, action)
            },
        )

        val displayParams = buildDisplayParams(hostContext)
        val touchParams = buildTouchParams(hostContext)

        val displayAdded = runCatching { wm.addView(display, displayParams) }.isSuccess
        if (!displayAdded) return
        val touchAdded = runCatching { wm.addView(touch, touchParams) }.isSuccess
        if (!touchAdded) {
            runCatching { wm.removeView(display) }
            return
        }

        windowManager = wm
        displayHost = display
        touchHost = touch
        cursorPreviewView = preview
        ballDragVisualView = ballVisual
        appContext = hostContext
        settings = appSettings
        registerScreenOffReceiver(hostContext)
    }

    private fun cleanupWindows() {
        continuedGestureActive = false
        sessionActive = false
        val wm = windowManager
        displayHost?.let { runCatching { wm?.removeView(it) } }
        touchHost?.let { runCatching { wm?.removeView(it) } }
        screenOffReceiver?.let { receiver ->
            runCatching { appContext?.unregisterReceiver(receiver) }
        }
        screenOffReceiver = null
        windowManager = null
        displayHost = null
        touchHost = null
        cursorPreviewView = null
        ballDragVisualView = null
        appContext = null
        settings = null
    }

    private fun handleTouch(rawX: Float, rawY: Float, action: Int) {
        when (action) {
            MotionEvent.ACTION_DOWN -> beginSessionAt(rawX, rawY)
            MotionEvent.ACTION_MOVE -> onFingerMove(rawX, rawY)
            MotionEvent.ACTION_UP -> finishSession()
            MotionEvent.ACTION_CANCEL -> {
                dismiss()
                cleanupWindows()
            }
        }
    }

    private fun beginSessionAt(rawX: Float, rawY: Float) {
        dragSession.reset()
        dragSessionArmed = false
        updatePickFromFinger(rawX, rawY, initial = true)
        selectionStart = null
        selectionPreviewBounds = null
        paused = false
        regionalActive = false
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        FloatBallPreviewBoundsCache.invalidate()
        cursorPreviewView?.visibility = View.VISIBLE
        lastPauseScheduleX = pickAnchor.x
        lastPauseScheduleY = pickAnchor.y
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
        schedulePauseTimer()
        startPreviewBoundsCache()
        applyPreviewBoundsFromCache()
        syncCursorAppearance()
    }

    private fun onFingerMove(rawX: Float, rawY: Float) {
        updatePickFromFinger(rawX, rawY, initial = false)
        val start = selectionStart
        if (start != null) {
            updateRegionalModeOnMove(start)
        } else if (paused) {
            paused = false
            selectionPreviewBounds = null
        }
        schedulePauseTimerIfMoved()
        schedulePreviewCacheRefresh()
        applyPreviewBoundsFromCache()
        syncCursorAppearance()
    }

    private fun updateRegionalModeOnMove(start: Offset) {
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val movePx = REGIONAL_MOVE_DP * density
        val distFromStart = hypot(pickAnchor.x - start.x, pickAnchor.y - start.y)
        if (regionalActive) {
            if (distFromStart < movePx) {
                regionalActive = false
                paused = false
                selectionStart = null
                selectionPreviewBounds = null
                boundsLookupGeneration++
                PickPrefetchCache.invalidate()
            }
            return
        }
        if (distFromStart >= movePx) {
            regionalActive = true
            boundsLookupGeneration++
            PickPrefetchCache.invalidate()
            selectionPreviewBounds = null
        }
    }

    private fun finishSession() {
        val host = appContext
        val currentSettings = settings
        if (host == null || currentSettings == null) {
            dismiss()
            cleanupWindows()
            return
        }
        val hadPickIntent = paused || selectionStart != null || regionalActive
        if (hadPickIntent) {
            if (!regionalActive) {
                ensurePreviewBoundsForPick()
            }
            submitPick(host, currentSettings)
        }
        dismiss()
        cleanupWindows()
    }

    private fun submitPick(host: Context, currentSettings: AppSettings) {
        val end = pickAnchor
        val start = selectionStart ?: end
        val dragRect = rectBetween(start, end)
        val isRegionalDrag = regionalActive
        val previewBounds = selectionPreviewBounds
        if (!isRegionalDrag && previewBounds == null) return

        val ocrFallbackEnabled = currentSettings.floatBallOcrFallbackEnabled
        val ocrModelId = currentSettings.floatBallOcrModelId

        when {
            isRegionalDrag -> {
                val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
                val minSidePx = (REGIONAL_RECT_MIN_SIDE_DP * density).roundToInt()
                if (dragRect.width() < minSidePx || dragRect.height() < minSidePx) return
                PickPerf.beginSession("regional_rect_gesture")
                val panelAnchorX = dragRect.centerX().toFloat()
                val panelAnchorY = dragRect.bottom.toFloat()
                FloatBallPickResultPanel.showLoading(
                    host,
                    panelAnchorX,
                    panelAnchorY,
                    PickResultTextSource.OCR,
                )
                SlideIndexAccessibilityService.pickFloatBallOnRelease(
                    context = host,
                    startX = start.x,
                    startY = start.y,
                    endX = end.x,
                    endY = end.y,
                    regionalRect = true,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                ) { result ->
                    FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                    PickPerf.endSession("END", "regional_rect_gesture")
                }
            }
            else -> {
                val bounds = previewBounds ?: return
                val panelAnchorX = bounds.centerX().toFloat()
                val panelAnchorY = bounds.bottom.toFloat()
                PickPerf.beginSession("preview_bounds_gesture")
                FloatBallPickResultPanel.showLoading(
                    host,
                    panelAnchorX,
                    panelAnchorY,
                    PickResultTextSource.A11Y,
                )
                SlideIndexAccessibilityService.pickFloatBallTextInRect(
                    context = host,
                    rect = bounds,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    previewBoundsPick = true,
                ) { result ->
                    FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                    PickPerf.endSession("END", "preview_bounds_gesture")
                }
            }
        }
    }

    private fun updatePickFromFinger(rawX: Float, rawY: Float, initial: Boolean) {
        val currentSettings = settings ?: return
        val view = cursorPreviewView ?: return
        val density = view.resources.displayMetrics.density
        val ballSizePx = currentSettings.floatBallSizeDp.coerceIn(36f, 72f) * density
        val marginPx = (EDGE_MARGIN_DP * density).roundToInt()
        val dockSide = if (rawX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT

        if (initial || !dragSessionArmed) {
            dragSession.armAtTouch(
                settings = currentSettings,
                screenX = rawX,
                screenY = rawY,
                ballCenterX = rawX,
                ballCenterY = rawY,
                ballSizePx = ballSizePx,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density,
                dockSide = dockSide,
                anchorPickAtFinger = false,
            )
            lastFingerX = rawX
            lastFingerY = rawY
            dragSessionArmed = true
        } else {
            dragSession.onFingerMove(rawX - lastFingerX, rawY - lastFingerY)
            lastFingerX = rawX
            lastFingerY = rawY
        }

        pickAnchor = dragSession.computePick(
            settings = currentSettings,
            ballSizePx = ballSizePx,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            marginPx = marginPx,
        )
        ballCenter = dragSession.clampedBallCenter(
            ballSizePx = ballSizePx,
            marginPx = marginPx,
            screenWidth = screenWidth.roundToInt(),
            screenHeight = screenHeight.roundToInt(),
        )
        updateBallLayout(currentSettings, dockSide, ballSizePx.roundToInt())
        applyPreviewBoundsFromCache()
    }

    private fun updateBallLayout(
        currentSettings: AppSettings,
        dockSide: FloatBallSide,
        ballSizePx: Int,
    ) {
        val visual = ballDragVisualView ?: return
        if (visual.visibility != View.VISIBLE) {
            visual.show(currentSettings, composeSnapshot = null, activeSide = dockSide)
        }
        visual.x = ballCenter.x - ballSizePx / 2f
        visual.y = ballCenter.y - ballSizePx / 2f
    }

    private fun onCursorPaused() {
        if (!sessionActive || paused) return
        cancelPauseTimer()
        regionalActive = false
        val anchor = pickAnchor
        selectionStart = anchor
        paused = true
        val bounds = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        if (bounds != null) {
            selectionPreviewBounds = bounds
            maybeStartPickPrefetch()
        } else {
            launchPreviewBoundsLookupFallback(anchor)
        }
        syncCursorAppearance()
    }

    private fun launchPreviewBoundsLookupFallback(anchor: Offset) {
        val generation = ++boundsLookupGeneration
        overlayScope.launch(Dispatchers.Default) {
            val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
                rawX = anchor.x,
                rawY = anchor.y,
            )
            withContext(Dispatchers.Main) {
                if (generation != boundsLookupGeneration) return@withContext
                if (!sessionActive || !paused || regionalActive) return@withContext
                if (bounds != null) {
                    selectionPreviewBounds = bounds
                    maybeStartPickPrefetch()
                    syncCursorAppearance()
                }
            }
        }
    }

    private fun ensurePreviewBoundsForPick() {
        if (selectionPreviewBounds != null) return
        val anchor = pickAnchor
        val cached = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        if (cached != null) {
            selectionPreviewBounds = cached
            return
        }
        val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
            rawX = anchor.x,
            rawY = anchor.y,
        )
        if (bounds != null) {
            selectionPreviewBounds = bounds
        }
    }

    private fun maybeStartPickPrefetch() {
        if (!paused) return
        val bounds = selectionPreviewBounds ?: return
        val host = appContext ?: return
        FloatBallPickResultPanel.warmUp(host)
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        PickPrefetchCache.startPreviewA11yPrefetch(
            service = service,
            rect = bounds,
            generation = boundsLookupGeneration,
        )
    }

    private fun schedulePauseTimerIfMoved() {
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val movePx = REGIONAL_MOVE_DP * density
        if (!lastPauseScheduleX.isNaN() && !lastPauseScheduleY.isNaN()) {
            if (hypot(pickAnchor.x - lastPauseScheduleX, pickAnchor.y - lastPauseScheduleY) < movePx) {
                return
            }
        }
        lastPauseScheduleX = pickAnchor.x
        lastPauseScheduleY = pickAnchor.y
        schedulePauseTimer()
    }

    private fun schedulePauseTimer() {
        cancelPauseTimer()
        val runnable = Runnable { onCursorPaused() }
        pauseRunnable = runnable
        mainHandler.postDelayed(runnable, PAUSE_MS)
    }

    private fun cancelPauseTimer() {
        pauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pauseRunnable = null
    }

    private fun startPreviewBoundsCache() {
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        FloatBallPreviewBoundsCache.refresh(
            service = service,
            onReady = {
                if (!sessionActive) return@refresh
                applyPreviewBoundsFromCache()
            },
        )
    }

    private fun schedulePreviewCacheRefresh() {
        val anchor = pickAnchor
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val movePx = CACHE_REFRESH_MOVE_DP * density
        if (!lastCacheRefreshX.isNaN() && !lastCacheRefreshY.isNaN()) {
            if (hypot(anchor.x - lastCacheRefreshX, anchor.y - lastCacheRefreshY) < movePx) {
                return
            }
        }
        lastCacheRefreshX = anchor.x
        lastCacheRefreshY = anchor.y
        cancelCacheRefresh()
        val runnable = Runnable {
            cacheRefreshRunnable = null
            if (!sessionActive) return@Runnable
            startPreviewBoundsCache()
        }
        cacheRefreshRunnable = runnable
        mainHandler.postDelayed(runnable, CACHE_REFRESH_MS)
    }

    private fun cancelCacheRefresh() {
        cacheRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        cacheRefreshRunnable = null
    }

    private fun applyPreviewBoundsFromCache() {
        if (!sessionActive) return
        if (regionalActive) return
        if (paused && selectionStart != null) return
        val cached = FloatBallPreviewBoundsCache.hitTestAt(pickAnchor.x, pickAnchor.y)
        if (cached != null) {
            updatePreviewBoundsIfChanged(cached)
            return
        }
        scheduleLivePreviewBoundsLookup()
    }

    private fun updatePreviewBoundsIfChanged(bounds: Rect) {
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val slopPx = (2f * density).roundToInt()
        val current = selectionPreviewBounds
        if (current == null || !previewBoundsStableEquals(current, bounds, slopPx)) {
            selectionPreviewBounds = bounds
            syncCursorAppearance()
        }
    }

    private fun scheduleLivePreviewBoundsLookup() {
        if (!sessionActive || regionalActive || (paused && selectionStart != null)) return
        val generation = ++liveBoundsLookupGeneration
        val x = pickAnchor.x
        val y = pickAnchor.y
        overlayScope.launch(Dispatchers.Default) {
            val bounds = SlideIndexAccessibilityService.findControlBoundsAt(rawX = x, rawY = y)
            withContext(Dispatchers.Main) {
                if (generation != liveBoundsLookupGeneration) return@withContext
                if (!sessionActive || regionalActive || (paused && selectionStart != null)) return@withContext
                if (bounds != null) {
                    updatePreviewBoundsIfChanged(bounds)
                }
            }
        }
    }

    private fun previewBoundsStableEquals(current: Rect, next: Rect, slopPx: Int): Boolean =
        kotlin.math.abs(current.left - next.left) <= slopPx &&
            kotlin.math.abs(current.top - next.top) <= slopPx &&
            kotlin.math.abs(current.right - next.right) <= slopPx &&
            kotlin.math.abs(current.bottom - next.bottom) <= slopPx

    private fun resetSessionState() {
        cancelPauseTimer()
        cancelCacheRefresh()
        boundsLookupGeneration++
        liveBoundsLookupGeneration++
        dragSession.reset()
        dragSessionArmed = false
        lastFingerX = 0f
        lastFingerY = 0f
        pickAnchor = Offset.Zero
        ballCenter = Offset.Zero
        selectionStart = null
        selectionPreviewBounds = null
        paused = false
        regionalActive = false
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
    }

    private fun syncCursorAppearance() {
        val view = cursorPreviewView ?: return
        val currentSettings = settings
        val hintMode = when {
            !paused -> FloatBallCursorPreviewView.HintMode.HIDDEN
            regionalActive -> FloatBallCursorPreviewView.HintMode.SCREENSHOT
            else -> FloatBallCursorPreviewView.HintMode.TEXT
        }
        view.setChromeState(
            visible = sessionActive,
            paused = paused,
            selectionStart = selectionStart,
            selectionPreviewBounds = selectionPreviewBounds,
            pickAnchor = pickAnchor,
            regionalDragActive = regionalActive,
            crossVisible = sessionActive,
            crossAlpha = 1f,
            crossPaused = paused,
            crossArmDp = currentSettings?.floatBallPickCrossArmDp?.coerceIn(4f, 16f) ?: 7.5f,
            hintMode = hintMode,
        )
    }

    private fun rectBetween(start: Offset, end: Offset): Rect {
        val left = min(start.x, end.x).roundToInt()
        val top = min(start.y, end.y).roundToInt()
        val right = max(start.x, end.x).roundToInt()
        val bottom = max(start.y, end.y).roundToInt()
        return Rect(left, top, right, bottom)
    }

    private fun registerScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    dismiss()
                    cleanupWindows()
                }
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun buildDisplayParams(context: Context): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

    private fun buildTouchParams(context: Context): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
}

private class RegionalPickTouchHost(
    context: Context,
    private val onTouchAt: (rawX: Float, rawY: Float, action: Int) -> Unit,
) : FrameLayout(context) {
    private var capturing = false

    fun beginContinuedGesture(rawX: Float, rawY: Float, @Suppress("UNUSED_PARAMETER") downTimeMs: Long) {
        capturing = true
        onTouchAt(rawX, rawY, MotionEvent.ACTION_DOWN)
    }

    fun forwardContinuedTouch(event: MotionEvent): Boolean {
        if (!capturing) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                onTouchAt(event.rawX, event.rawY, MotionEvent.ACTION_MOVE)
                true
            }
            MotionEvent.ACTION_UP -> {
                onTouchAt(event.rawX, event.rawY, MotionEvent.ACTION_UP)
                capturing = false
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                onTouchAt(event.rawX, event.rawY, MotionEvent.ACTION_CANCEL)
                capturing = false
                true
            }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (RegionalPickOverlay.isConsumingEdgeGestureTouch()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                capturing = true
                onTouchAt(event.rawX, event.rawY, MotionEvent.ACTION_DOWN)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!capturing) return false
                onTouchAt(event.rawX, event.rawY, MotionEvent.ACTION_MOVE)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!capturing) return false
                onTouchAt(event.rawX, event.rawY, event.actionMasked)
                capturing = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
