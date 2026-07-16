package com.slideindex.app.overlay

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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.service.AccessibilityTextExtractor
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.ui.theme.SlideIndexTheme
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

private const val CROSS_ARM_DP = 14f
private const val CROSS_STROKE_DP = 2.5f
private const val RECT_MIN_SIDE_DP = 48f

/**
 * Persistent float ball: ball acts as joystick, crosshair/plus acts as screen pointer.
 * Independent from [FloatingPointerOverlayWindow] (edge-gesture virtual pointer).
 */
object FloatBallOverlay {
    private const val TAG = "FloatBallOverlay"
    private const val EDGE_MARGIN_DP = 8f
    private const val PAUSE_MS = 280L
    /** Shorter pause when preview bounds are already resolved under the pointer. */
    private const val PAUSE_MS_WITH_BOUNDS = 160L
    /** Brief confirm after bounds first appear mid-wait (red box 鈫?yellow). */
    private const val PAUSE_MS_AFTER_BOUNDS_READY = 120L
    /** Backup bounds retry while waiting for pause (primary lookup is immediate). */
    private const val PAUSE_PREFETCH_MS = 120L
    private const val BALL_LAYOUT_MIN_INTERVAL_MS = 16L
    private const val CURSOR_UPDATE_MIN_INTERVAL_MS = 16L
    private const val BOUNDS_LOOKUP_MIN_INTERVAL_MS = 50L
    private const val BOUNDS_LOOKUP_HEAVY_INTERVAL_MS = 80L
    private const val BOUNDS_LOOKUP_MIN_MOVE_DP = 14f
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dragSession = FloatBallDragSession()

    private var windowManager: WindowManager? = null
    private var ballView: View? = null
    private var ballComposeView: ComposeView? = null
    private var edgeCaptureView: ComposeView? = null
    private var lineView: ComposeView? = null
    private var cursorView: ComposeView? = null
    private var ballOwner: OverlayComposeOwner? = null
    private var edgeCaptureOwner: OverlayComposeOwner? = null
    private var lineOwner: OverlayComposeOwner? = null
    private var cursorOwner: OverlayComposeOwner? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var edgeCaptureParams: WindowManager.LayoutParams? = null
    private var lineParams: WindowManager.LayoutParams? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var appContext: Context? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    private var settingsState: MutableState<AppSettings>? = null
    private var cursorVisibleState: MutableState<Boolean>? = null
    private var cursorPausedState: MutableState<Boolean>? = null
    private var cursorAnchorState: MutableState<Offset>? = null
    private var selectionStartState: MutableState<Offset?>? = null
    private var selectionPreviewBoundsState: MutableState<Rect?>? = null

    private var onPositionPersisted: ((xFraction: Float, yFraction: Float) -> Unit)? = null
    private var onActiveSidePersisted: ((FloatBallSide) -> Unit)? = null
    private var pauseRunnable: Runnable? = null
    private var pausePrefetchRunnable: Runnable? = null
    private var captureSuppressed = false
    private var isDragging = false
    private var dragOriginatedFromLine = false
    private var dragActiveSideOverride: FloatBallSide? = null
    private var chromeHiddenForDrag = false
    private var committedActiveSideUntilPersist: FloatBallSide? = null
    private var activeSideAtDragStart: FloatBallSide? = null
    private var finishDragRequested = false
    private var lastBallLayoutMs = 0L
    private var ballLayoutThrottleRunnable: Runnable? = null
    private var lastCursorUpdateMs = 0L
    private var cursorUpdateThrottleRunnable: Runnable? = null
    private var pendingPickAnchor: Offset? = null
    private var dragScreenBounds: OverlayScreenBounds? = null
    private var boundsLookupGeneration = 0
    private var lastBoundsLookupMs = 0L
    private var lastBoundsLookupX = Float.NaN
    private var lastBoundsLookupY = Float.NaN
    private var boundsLookupThrottleRunnable: Runnable? = null

    private data class PreviewBoundsLookupProfile(
        val intervalMs: Long,
        val maxNodes: Int,
        val minMovePx: Float,
    )

    val isShowing: Boolean get() = ballView != null

    fun showOrUpdate(
        context: Context,
        settings: AppSettings,
        onPositionPersisted: (xFraction: Float, yFraction: Float) -> Unit,
        onActiveSidePersisted: (FloatBallSide) -> Unit = {},
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showOrUpdate(context, settings, onPositionPersisted, onActiveSidePersisted) }
            return
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            dismiss()
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "accessibility service not connected")
                return
            }

        this.onPositionPersisted = onPositionPersisted
        this.onActiveSidePersisted = onActiveSidePersisted
        if (!isShowing) {
            ensureWindows(hostContext, settings)
        } else {
            val incoming = settings
            val pendingSide = committedActiveSideUntilPersist
            if (pendingSide != null && incoming.floatBallActiveSide != pendingSide) {
                settingsState?.value = incoming.copy(floatBallActiveSide = pendingSide)
                return
            }
            if (pendingSide != null && incoming.floatBallActiveSide == pendingSide) {
                committedActiveSideUntilPersist = null
            }
            val current = settingsState?.value
            val merged = if (
                isDragging &&
                current != null &&
                incoming.floatBallActiveSide != current.floatBallActiveSide
            ) {
                incoming.copy(floatBallActiveSide = current.floatBallActiveSide)
            } else {
                incoming
            }
            settingsState?.value = merged
            recoverStuckLineCaptureIfNeeded(merged)
            if (!isDragging) {
                restorePassiveOverlayLayout(merged)
            }
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        hideCursor()
        cancelPauseTimer()
        val wm = windowManager
        ballView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        edgeCaptureView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        lineView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        cursorView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        OverlayCompose.disposeComposeView(ballComposeView)
        OverlayCompose.disposeComposeView(edgeCaptureView)
        OverlayCompose.disposeComposeView(lineView)
        OverlayCompose.disposeComposeView(cursorView)
        ballOwner?.destroy()
        edgeCaptureOwner?.destroy()
        lineOwner?.destroy()
        cursorOwner?.destroy()
        ballOwner = null
        edgeCaptureOwner = null
        lineOwner = null
        cursorOwner = null
        ballComposeView = null
        ballView = null
        edgeCaptureView = null
        lineView = null
        cursorView = null
        ballParams = null
        edgeCaptureParams = null
        lineParams = null
        cursorParams = null
        windowManager = null
        settingsState = null
        cursorVisibleState = null
        cursorPausedState = null
        cursorAnchorState = null
        selectionStartState = null
        selectionPreviewBoundsState = null
        onPositionPersisted = null
        onActiveSidePersisted = null
        screenOffReceiver = null
        appContext = null
        isDragging = false
        dragOriginatedFromLine = false
        dragActiveSideOverride = null
        chromeHiddenForDrag = false
        committedActiveSideUntilPersist = null
        activeSideAtDragStart = null
        cancelBallLayoutThrottle()
        dragSession.reset()
    }

    fun relayout() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { relayout() }
            return
        }
        if (captureSuppressed) return
        val settings = settingsState?.value ?: return
        if (isDragging) {
            val view = ballView ?: return
            val metrics = view.resources.displayMetrics
            val bounds = overlayScreenBounds(metrics)
            dragSession.refreshPointerTravel(
                settings = settings,
                screenWidth = bounds.width,
                screenHeight = bounds.height,
            )
            updatePickAndBallFromFinger(moveBallWindow = true)
        } else {
            applyAllLayouts(settings)
        }
    }

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        captureSuppressed = true
        ballView?.visibility = View.GONE
        edgeCaptureView?.visibility = View.GONE
        lineView?.visibility = View.GONE
        hideCursor()
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        captureSuppressed = false
        val settings = settingsState?.value
        if (settings != null) {
            updateChromeVisibility(settings)
        } else {
            ballView?.visibility = View.VISIBLE
        }
    }

    private fun ensureWindows(hostContext: Context, settings: AppSettings) {
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val settingsHolder = mutableStateOf(settings)
        val cursorVisible = mutableStateOf(false)
        val cursorPaused = mutableStateOf(false)
        val cursorAnchor = mutableStateOf(Offset.Zero)
        val selectionStart = mutableStateOf<Offset?>(null)
        val selectionPreviewBounds = mutableStateOf<Rect?>(null)

        val dragCallbacks = object {
            fun onStart(screenX: Float, screenY: Float) {
                activeSideAtDragStart = null
                dragOriginatedFromLine = false
                dragActiveSideOverride = null
                edgeCaptureView?.visibility = View.GONE
                showCursorAtScreenTouch(screenX, screenY, deferBallWindowMutation = true)
            }

            fun onDrag(dx: Float, dy: Float) {
                onFingerDrag(dx, dy)
                onDragMoved()
            }

            fun onEnd() {
                if (dragOriginatedFromLine) return
                completeDragGesture()
            }

            fun onCancel() {
                if (dragOriginatedFromLine) return
                activeSideAtDragStart = null
                dragOriginatedFromLine = false
                dragActiveSideOverride = null
                chromeHiddenForDrag = false
                hideCursor()
                settingsState?.value?.let { restoreDockPosition(it) }
                updateChromeVisibility(settingsHolder.value)
            }
        }

        val ballDialogOwner = OverlayComposeOwner()
        val ballHost = FloatBallStripHost(overlayContext).apply {
            OverlayCompose.bindOwners(this, ballDialogOwner)
            bindDragCallbacks(
                onDragStart = { screenX, screenY -> dragCallbacks.onStart(screenX, screenY) },
                onDrag = { dx, dy -> dragCallbacks.onDrag(dx, dy) },
                onDragEnd = { dragCallbacks.onEnd() },
                onDragCancel = { dragCallbacks.onCancel() },
            )
        }
        val ballCompose = OverlayCompose.createComposeView(overlayContext, ballDialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                FloatBallContent(settingsState = settingsHolder)
            }
        }
        ballHost.addView(
            ballCompose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val edgeCaptureDialogOwner = OverlayComposeOwner()
        val edgeCaptureCompose = OverlayCompose.createComposeView(overlayContext, edgeCaptureDialogOwner).apply {
            setContent {
                FloatBallEdgeCaptureContent(
                    windowOrigin = {
                        val params = edgeCaptureParams ?: return@FloatBallEdgeCaptureContent IntOffset.Zero
                        IntOffset(params.x, params.y)
                    },
                    onDragStart = { screenX, screenY -> dragCallbacks.onStart(screenX, screenY) },
                    onDrag = { dx, dy -> dragCallbacks.onDrag(dx, dy) },
                    onDragEnd = { dragCallbacks.onEnd() },
                    onDragCancel = { dragCallbacks.onCancel() },
                )
            }
        }

        val lineDialogOwner = OverlayComposeOwner()
        val lineCompose = OverlayCompose.createComposeView(overlayContext, lineDialogOwner).apply {
            setContent {
                FloatBallLineContent(
                    settingsState = settingsHolder,
                    windowOrigin = {
                        val params = lineParams ?: return@FloatBallLineContent IntOffset.Zero
                        IntOffset(params.x, params.y)
                    },
                    onLineDragDown = { screenX, screenY -> prepareLineDrag(screenX, screenY) },
                    onLineDrag = { dx, dy ->
                        onFingerDrag(dx, dy)
                        onDragMoved()
                    },
                    onLineDragEnd = {
                        commitLineDragSideSwap()
                        completeDragGesture()
                    },
                    onLineDragCancel = {
                        revertLineDragSideSwapIfNeeded()
                        cancelDragWithoutPick()
                    },
                )
            }
        }

        val cursorDialogOwner = OverlayComposeOwner()
        val cursorCompose = OverlayCompose.createComposeView(overlayContext, cursorDialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                val visible by cursorVisible
                val paused by cursorPaused
                val selectionStart by selectionStart
                val selectionPreviewBounds by selectionPreviewBounds
                val pickAnchor by cursorAnchor
                FloatBallCursorContent(
                    visible = visible,
                    paused = paused,
                    selectionStart = selectionStart,
                    selectionPreviewBounds = selectionPreviewBounds,
                    pickAnchor = pickAnchor,
                )
            }
        }

        val ballLp = buildTouchableStripLayoutParams(hostContext)
        val edgeCaptureLp = buildTouchableStripLayoutParams(hostContext)
        val lineLp = buildTouchableStripLayoutParams(hostContext)
        val cursorLp = buildCursorLayoutParams(hostContext)

        val edgeAdded = runCatching { wm.addView(edgeCaptureCompose, edgeCaptureLp) }.isSuccess
        if (!edgeAdded) {
            ballDialogOwner.destroy()
            edgeCaptureDialogOwner.destroy()
            lineDialogOwner.destroy()
            cursorDialogOwner.destroy()
            Log.e(TAG, "failed to add edge capture overlay")
            return
        }
        val lineAdded = runCatching { wm.addView(lineCompose, lineLp) }.isSuccess
        if (!lineAdded) {
            runCatching { wm.removeView(edgeCaptureCompose) }
            ballDialogOwner.destroy()
            edgeCaptureDialogOwner.destroy()
            lineDialogOwner.destroy()
            cursorDialogOwner.destroy()
            Log.e(TAG, "failed to add line overlay")
            return
        }
        val ballAdded = runCatching { wm.addView(ballHost, ballLp) }.isSuccess
        if (!ballAdded) {
            runCatching { wm.removeView(edgeCaptureCompose) }
            runCatching { wm.removeView(lineCompose) }
            ballDialogOwner.destroy()
            edgeCaptureDialogOwner.destroy()
            lineDialogOwner.destroy()
            cursorDialogOwner.destroy()
            Log.e(TAG, "failed to add ball overlay")
            return
        }
        val cursorAdded = runCatching { wm.addView(cursorCompose, cursorLp) }.isSuccess
        if (!cursorAdded) {
            runCatching { wm.removeView(ballHost) }
            runCatching { wm.removeView(edgeCaptureCompose) }
            runCatching { wm.removeView(lineCompose) }
            ballDialogOwner.destroy()
            edgeCaptureDialogOwner.destroy()
            lineDialogOwner.destroy()
            cursorDialogOwner.destroy()
            Log.e(TAG, "failed to add cursor overlay")
            return
        }

        windowManager = wm
        ballView = ballHost
        ballComposeView = ballCompose
        edgeCaptureView = edgeCaptureCompose
        lineView = lineCompose
        cursorView = cursorCompose
        ballOwner = ballDialogOwner
        edgeCaptureOwner = edgeCaptureDialogOwner
        lineOwner = lineDialogOwner
        cursorOwner = cursorDialogOwner
        ballParams = ballLp
        edgeCaptureParams = edgeCaptureLp
        lineParams = lineLp
        cursorParams = cursorLp
        settingsState = settingsHolder
        cursorVisibleState = cursorVisible
        cursorPausedState = cursorPaused
        cursorAnchorState = cursorAnchor
        selectionStartState = selectionStart
        selectionPreviewBoundsState = selectionPreviewBounds
        appContext = hostContext
        registerScreenOffReceiver(hostContext)

        applyAllLayouts(settings)
    }

    /**
     * Reset drag capture and snap ball/line back to edge strips.
     * Shrinks any fullscreen line capture left from an in-progress or stuck gesture.
     */
    private fun restorePassiveOverlayLayout(settings: AppSettings, fixZOrder: Boolean = true) {
        val wm = windowManager ?: return

        ballParams?.let { params ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            ballView?.let { runCatching { wm.updateViewLayout(it, params) } }
        }

        lineParams?.let { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            lineView?.let { runCatching { wm.updateViewLayout(it, params) } }
        }

        edgeCaptureView?.visibility = View.GONE
        applyAllLayouts(settings)
        if (fixZOrder) {
            bringBallAboveLine()
        }
    }

    private fun bringOverlayToFront(view: View, params: WindowManager.LayoutParams) {
        val wm = windowManager ?: return
        if (!view.isAttachedToWindow) return
        runCatching {
            wm.removeView(view)
            wm.addView(view, params)
        }
    }

    /** Ball strip must stay above line when idle so the ball receives touches. */
    private fun bringBallAboveLine() {
        val wm = windowManager ?: return
        val ball = ballView ?: return
        val ballLp = ballParams ?: return
        bringOverlayToFront(ball, ballLp)
    }

    private fun recoverStuckLineCaptureIfNeeded(settings: AppSettings) {
        val metrics = lineView?.resources?.displayMetrics ?: return
        val params = lineParams ?: return
        val stuckFullscreen = params.width >= metrics.widthPixels || params.height >= metrics.heightPixels
        if (!stuckFullscreen) return
        Log.w(TAG, "recovering stuck fullscreen line overlay")
        isDragging = false
        dragOriginatedFromLine = false
        dragActiveSideOverride = null
        activeSideAtDragStart = null
        hideCursor()
        restorePassiveOverlayLayout(settings)
    }

    private fun buildTouchableStripLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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

    private fun buildCursorLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
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
    }

    private fun applyAllLayouts(settings: AppSettings, relayoutChrome: Boolean = true) {
        applyBallLayout(settings)
        if (relayoutChrome) {
            applyEdgeCaptureLayout(settings)
            applyLineLayout(settings)
        }
        updateChromeVisibility(settings)
    }

    private fun applyBallLayout(settings: AppSettings) {
        val wm = windowManager ?: return
        val view = ballView ?: return
        val params = ballParams ?: return
        val metrics = view.resources.displayMetrics
        val activeSide = FloatBallLayout.resolvedActiveSide(settings)
        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            val (left, top) = FloatBallLayout.ballTopLeft(settings, metrics, activeSide)
            params.x = left
            params.y = top
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            val bounds = FloatBallLayout.edgeStripBounds(settings, metrics, activeSide)
            params.x = bounds.left
            params.y = bounds.top
            params.width = bounds.width()
            params.height = bounds.height()
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyEdgeCaptureLayout(settings: AppSettings) {
        val wm = windowManager ?: return
        val view = edgeCaptureView ?: return
        val params = edgeCaptureParams ?: return
        val metrics = view.resources.displayMetrics
        val activeSide = FloatBallLayout.resolvedActiveSide(settings)
        val bounds = FloatBallLayout.edgeStripBounds(settings, metrics, activeSide)
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyLineLayout(settings: AppSettings) {
        val wm = windowManager ?: return
        val view = lineView ?: return
        val params = lineParams ?: return
        val metrics = view.resources.displayMetrics
        if (!FloatBallLayout.shouldShowLine(settings)) return
        val inactiveSide = FloatBallSide.opposite(FloatBallLayout.resolvedActiveSide(settings))
        val bounds = FloatBallLayout.edgeStripBounds(settings, metrics, inactiveSide)
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun updateChromeVisibility(settings: AppSettings) {
        if (captureSuppressed) {
            ballView?.visibility = View.GONE
            edgeCaptureView?.visibility = View.GONE
            lineView?.visibility = View.GONE
            return
        }
        if (isDragging) {
            ballView?.visibility = View.VISIBLE
            edgeCaptureView?.visibility = View.GONE
            lineView?.visibility = when {
                dragOriginatedFromLine && FloatBallLayout.shouldShowLine(settings) -> View.VISIBLE
                else -> View.GONE
            }
            return
        }
        ballView?.visibility = View.VISIBLE
        edgeCaptureView?.visibility = View.GONE
        lineView?.visibility = if (FloatBallLayout.shouldShowLine(settings)) View.VISIBLE else View.GONE
    }

    private fun effectiveActiveSide(settings: AppSettings): FloatBallSide =
        dragActiveSideOverride ?: FloatBallLayout.resolvedActiveSide(settings)

    private fun prepareLineDrag(screenX: Float, screenY: Float) {
        val settings = settingsState?.value ?: return
        val dockedSide = FloatBallLayout.resolvedActiveSide(settings)
        val bothEdges = settings.floatBallPositionMode == FloatBallPositionMode.BOTH_EDGES
        activeSideAtDragStart = if (bothEdges) dockedSide else null
        dragOriginatedFromLine = bothEdges
        dragActiveSideOverride = if (bothEdges) {
            FloatBallSide.opposite(dockedSide)
        } else {
            null
        }
        showCursorAtScreenTouch(screenX, screenY, deferBallWindowMutation = true)
        mainHandler.post {
            if (!isDragging || !dragOriginatedFromLine) return@post
            setBallTouchable(false)
            settingsState?.value?.let { applyDragBallLayout(it) }
        }
    }

    private fun commitLineDragSideSwap() {
        if (!dragOriginatedFromLine) return
        val fromSide = activeSideAtDragStart ?: return
        val settings = settingsState?.value ?: return
        if (settings.floatBallPositionMode != FloatBallPositionMode.BOTH_EDGES) return
        val targetSide = FloatBallSide.opposite(fromSide)
        if (FloatBallLayout.resolvedActiveSide(settings) != targetSide) {
            applyActiveSide(targetSide)
        }
    }

    private fun revertLineDragSideSwapIfNeeded() {
        if (!dragOriginatedFromLine) return
        val revertSide = activeSideAtDragStart ?: return
        val settings = settingsState?.value ?: return
        if (settings.floatBallPositionMode != FloatBallPositionMode.BOTH_EDGES) return
        if (FloatBallLayout.resolvedActiveSide(settings) != revertSide) {
            applyActiveSide(revertSide)
        }
    }

    private fun cancelDragWithoutPick() {
        if (!isDragging) return
        cancelBallLayoutThrottle()
        cancelCursorUpdateThrottle()
        activeSideAtDragStart = null
        val settings = settingsState?.value
        if (settings != null) {
            restoreDockPosition(settings)
        }
        clearCursorUi()
        isDragging = false
        settings?.let { updateChromeVisibility(it) }
    }

    private fun completeDragGesture() {
        if (!isDragging || finishDragRequested) return
        finishDragRequested = true
        val settings = settingsState?.value ?: run {
            finishDragRequested = false
            return
        }
        finishDrag(settings)
        finishDragRequested = false
    }

    private fun applyActiveSide(targetSide: FloatBallSide) {
        val settings = settingsState?.value ?: return
        if (settings.floatBallPositionMode != FloatBallPositionMode.BOTH_EDGES) return
        val updated = settings.copy(floatBallActiveSide = targetSide)
        settingsState?.value = updated
        committedActiveSideUntilPersist = targetSide
        onActiveSidePersisted?.invoke(targetSide)
        applyBallLayout(updated)
        if (!isDragging) {
            applyEdgeCaptureLayout(updated)
            applyLineLayout(updated)
            updateChromeVisibility(updated)
        }
    }

    private fun restoreDockPosition(settings: AppSettings) {
        applyAllLayouts(settings)
    }

    private fun onFingerDrag(dx: Float, dy: Float) {
        if (!isDragging) return
        if (!chromeHiddenForDrag) {
            chromeHiddenForDrag = true
            settingsState?.value?.let { updateChromeVisibility(it) }
        }
        dragSession.onFingerMove(dx, dy)
        updatePickAndBallFromFinger(moveBallWindow = true)
    }

    private fun finishDrag(settings: AppSettings) {
        if (!isDragging) return
        cancelBallLayoutThrottle()
        commitPickAnchor()
        if (cursorPausedState?.value == true) {
            handlePickOnRelease(settings)
        }
        commitLineDragSideSwap()
        dragActiveSideOverride = null
        activeSideAtDragStart = null
        restoreDockPosition(settingsState?.value ?: settings)

        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            persistBallCenterFraction()
        }

        clearCursorUi()
        isDragging = false
        updateChromeVisibility(settingsState?.value ?: settings)
    }

    private fun handlePickOnRelease(settings: AppSettings) {
        val end = cursorAnchorState?.value ?: return
        val start = selectionStartState?.value ?: end
        val host = appContext ?: return
        val view = ballView ?: return
        val minSidePx = (RECT_MIN_SIDE_DP * view.resources.displayMetrics.density).roundToInt()
        val dragRect = rectBetween(start, end)
        val isRegionalDrag = dragRect.width() >= minSidePx && dragRect.height() >= minSidePx
        val previewBounds = selectionPreviewBoundsState?.value
        if (!isRegionalDrag && previewBounds == null) {
            return
        }
        val ocrFallbackEnabled = settings.floatBallOcrFallbackEnabled
        val ocrModelId = settings.floatBallOcrModelId

        when {
            isRegionalDrag -> {
                PickPerf.beginSession("regional_rect")
                PickPerf.mark("ACTION_UP", "regionalRect=true ocr=$ocrFallbackEnabled")
                val panelAnchorX = dragRect.centerX().toFloat()
                val panelAnchorY = dragRect.bottom.toFloat()
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
                    PickPerf.mark("showResultPanel_callback")
                    FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                    PickPerf.endSession("END", "regional_rect")
                }
            }
            else -> {
                val bounds = previewBounds ?: return
                val panelAnchorX = bounds.centerX().toFloat()
                val panelAnchorY = bounds.bottom.toFloat()
                SlideIndexAccessibilityService.pickFloatBallTextInRect(
                    context = host,
                    rect = bounds,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    previewBoundsPick = true,
                ) { result ->
                    FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                }
            }
        }
    }

    private fun snapBallToEdge(settings: AppSettings) {
        restoreDockPosition(settings)
    }

    private fun persistBallCenterFraction() {
        val view = ballView ?: return
        val params = ballParams ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val ballSizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt()
        val activeSide = FloatBallLayout.resolvedActiveSide(settings)
        val (centerX, centerY) = FloatBallLayout.ballCenterPx(settings, metrics, activeSide)
        val xFraction = (centerX / metrics.widthPixels).coerceIn(0.05f, 0.95f)
        val yFraction = (centerY / metrics.heightPixels).coerceIn(0.05f, 0.95f)
        onPositionPersisted?.invoke(xFraction, yFraction)
    }

    private fun showCursorAtScreenTouch(
        screenX: Float,
        screenY: Float,
        deferBallWindowMutation: Boolean = false,
    ) {
        val params = ballParams ?: return
        val view = ballView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = overlayScreenBounds(metrics)
        val ballSizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt()
        val screenWidth = bounds.width
        val screenHeight = bounds.height

        val activeSide = effectiveActiveSide(settings)
        val (ballCenterX, ballCenterY) = FloatBallLayout.ballCenterPx(settings, metrics, activeSide)
        dragSession.armAtTouch(
            settings = settings,
            screenX = screenX,
            screenY = screenY,
            ballCenterX = ballCenterX,
            ballCenterY = ballCenterY,
            ballSizePx = ballSizePx.toFloat(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )

        isDragging = true
        chromeHiddenForDrag = false
        if (dragOriginatedFromLine && !deferBallWindowMutation) {
            setBallTouchable(false)
        }
        lastBallLayoutMs = 0L
        lastCursorUpdateMs = 0L
        pendingPickAnchor = null
        lastBoundsLookupMs = 0L
        lastBoundsLookupX = Float.NaN
        lastBoundsLookupY = Float.NaN
        cancelBoundsLookupThrottle()
        dragScreenBounds = overlayScreenBounds(metrics)
        selectionStartState?.value = null
        selectionPreviewBoundsState?.value = null
        cursorVisibleState?.value = true
        cursorPausedState?.value = false
        // Do not move or resize the ball window here 鈥?that cancels the Compose drag gesture.
        updatePickAndBallFromFinger(moveBallWindow = true)
        schedulePauseTimer()
    }

    private fun setBallTouchable(touchable: Boolean) {
        val wm = windowManager ?: return
        val view = ballView ?: return
        val params = ballParams ?: return
        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun clearCursorUi() {
        dragOriginatedFromLine = false
        dragActiveSideOverride = null
        chromeHiddenForDrag = false
        selectionPreviewBoundsState?.value = null
        setBallTouchable(true)
        settingsState?.value?.let { restorePassiveOverlayLayout(it) }
        cancelPauseTimer()
        cancelBallLayoutThrottle()
        cancelCursorUpdateThrottle()
        cancelBoundsLookupThrottle()
        boundsLookupGeneration++
        lastBoundsLookupMs = 0L
        lastBoundsLookupX = Float.NaN
        lastBoundsLookupY = Float.NaN
        dragScreenBounds = null
        dragSession.reset()
        cursorVisibleState?.value = false
        cursorPausedState?.value = false
        selectionStartState?.value = null
    }

    private fun hideCursor() {
        isDragging = false
        activeSideAtDragStart = null
        clearCursorUi()
        settingsState?.value?.let { updateChromeVisibility(it) }
    }

    private fun onDragMoved() {
        val start = selectionStartState?.value
        if (start != null) {
            val anchor = cursorAnchorState?.value
            if (anchor != null) {
                val view = ballView
                val minSidePx = if (view != null) {
                    RECT_MIN_SIDE_DP * view.resources.displayMetrics.density
                } else {
                    RECT_MIN_SIDE_DP
                }
                if (kotlin.math.abs(anchor.x - start.x) >= minSidePx ||
                    kotlin.math.abs(anchor.y - start.y) >= minSidePx
                ) {
                    boundsLookupGeneration++
                    selectionPreviewBoundsState?.value = null
                }
            }
            return
        }
        cursorPausedState?.value = false
        schedulePauseTimer()
    }

    private fun schedulePauseTimer() {
        cancelPauseTimer()
        val anchor = cursorAnchorState?.value
        val density = ballView?.resources?.displayMetrics?.density ?: 1f
        val profile = previewLookupProfile(density)
        if (anchor != null) {
            if (!hasRecentPreviewBoundsAt(anchor, profile.minMovePx)) {
                launchPreviewBoundsLookup(anchor, profile)
            }
            val prefetch = Runnable {
                pausePrefetchRunnable = null
                if (!isDragging || cursorPausedState?.value == true) return@Runnable
                if (!hasRecentPreviewBoundsAt(anchor, profile.minMovePx)) {
                    launchPreviewBoundsLookup(anchor, profile)
                }
            }
            pausePrefetchRunnable = prefetch
            mainHandler.postDelayed(prefetch, PAUSE_PREFETCH_MS)
            val runnable = Runnable { onCursorPaused() }
            pauseRunnable = runnable
            val pauseDelay = if (hasRecentPreviewBoundsAt(anchor, profile.minMovePx)) {
                PAUSE_MS_WITH_BOUNDS
            } else {
                PAUSE_MS
            }
            mainHandler.postDelayed(runnable, pauseDelay)
            return
        }
        val runnable = Runnable { onCursorPaused() }
        pauseRunnable = runnable
        mainHandler.postDelayed(runnable, PAUSE_MS)
    }

    private fun onCursorPaused() {
        if (cursorVisibleState?.value != true) return
        val anchor = cursorAnchorState?.value ?: return
        selectionStartState?.value = anchor
        cursorPausedState?.value = true
        val density = ballView?.resources?.displayMetrics?.density ?: 1f
        val profile = previewLookupProfile(density)
        if (hasRecentPreviewBoundsAt(anchor, profile.minMovePx)) return
        launchPreviewBoundsLookup(anchor, profile)
    }

    private fun previewLookupProfile(density: Float): PreviewBoundsLookupProfile {
        val heavyApp = SlideIndexAccessibilityService.currentForegroundPackage() == WECHAT_PACKAGE
        return PreviewBoundsLookupProfile(
            intervalMs = if (heavyApp) BOUNDS_LOOKUP_HEAVY_INTERVAL_MS else BOUNDS_LOOKUP_MIN_INTERVAL_MS,
            maxNodes = if (heavyApp) {
                AccessibilityTextExtractor.HEAVY_PREVIEW_MAX_TRAVERSAL_NODES
            } else {
                AccessibilityTextExtractor.PREVIEW_MAX_TRAVERSAL_NODES
            },
            minMovePx = BOUNDS_LOOKUP_MIN_MOVE_DP * density,
        )
    }

    private fun schedulePreviewBoundsLookup(anchor: Offset, density: Float) {
        if (cursorVisibleState?.value != true) return
        if (cursorPausedState?.value == true) return
        val profile = previewLookupProfile(density)
        if (!lastBoundsLookupX.isNaN() && !lastBoundsLookupY.isNaN()) {
            val moved = hypot(anchor.x - lastBoundsLookupX, anchor.y - lastBoundsLookupY)
            if (moved < profile.minMovePx) return
        }
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastBoundsLookupMs
        if (elapsed >= profile.intervalMs) {
            launchPreviewBoundsLookup(anchor, profile)
            return
        }
        if (boundsLookupThrottleRunnable != null) return
        val runnable = Runnable {
            boundsLookupThrottleRunnable = null
            if (!isDragging || cursorPausedState?.value == true) return@Runnable
            val latest = pendingPickAnchor ?: cursorAnchorState?.value ?: return@Runnable
            val latestDensity = ballView?.resources?.displayMetrics?.density ?: density
            launchPreviewBoundsLookup(latest, previewLookupProfile(latestDensity))
        }
        boundsLookupThrottleRunnable = runnable
        mainHandler.postDelayed(runnable, (profile.intervalMs - elapsed).coerceAtLeast(1L))
    }

    private fun launchPreviewBoundsLookup(anchor: Offset, profile: PreviewBoundsLookupProfile) {
        lastBoundsLookupMs = SystemClock.uptimeMillis()
        lastBoundsLookupX = anchor.x
        lastBoundsLookupY = anchor.y
        val generation = ++boundsLookupGeneration
        val x = anchor.x
        val y = anchor.y
        val allowAllWindowsFallback =
            SlideIndexAccessibilityService.currentForegroundPackage() != WECHAT_PACKAGE
        overlayScope.launch(Dispatchers.Default) {
            var bounds = SlideIndexAccessibilityService.findControlBoundsAt(
                rawX = x,
                rawY = y,
                activeWindowOnly = true,
                maxNodes = profile.maxNodes,
            )
            if (bounds == null && allowAllWindowsFallback) {
                bounds = SlideIndexAccessibilityService.findControlBoundsAt(
                    rawX = x,
                    rawY = y,
                    activeWindowOnly = false,
                    maxNodes = profile.maxNodes,
                )
            }
            withContext(Dispatchers.Main) {
                if (generation != boundsLookupGeneration) return@withContext
                if (!isDragging || cursorVisibleState?.value != true) return@withContext
                val paused = cursorPausedState?.value == true
                if (paused) {
                    val start = selectionStartState?.value ?: return@withContext
                    if (start != anchor) return@withContext
                }
                val hadBounds = selectionPreviewBoundsState?.value != null
                selectionPreviewBoundsState?.value = bounds
                if (
                    bounds != null &&
                    !hadBounds &&
                    !paused &&
                    pauseRunnable != null
                ) {
                    pauseRunnable?.let { mainHandler.removeCallbacks(it) }
                    val runnable = Runnable { onCursorPaused() }
                    pauseRunnable = runnable
                    mainHandler.postDelayed(runnable, PAUSE_MS_AFTER_BOUNDS_READY)
                }
            }
        }
    }

    private fun cancelBoundsLookupThrottle() {
        boundsLookupThrottleRunnable?.let { mainHandler.removeCallbacks(it) }
        boundsLookupThrottleRunnable = null
    }

    private fun rectBetween(start: Offset, end: Offset): Rect {
        val left = min(start.x, end.x).roundToInt()
        val top = min(start.y, end.y).roundToInt()
        val right = max(start.x, end.x).roundToInt()
        val bottom = max(start.y, end.y).roundToInt()
        return Rect(left, top, right, bottom)
    }

    private fun hasRecentPreviewBoundsAt(anchor: Offset, minMovePx: Float): Boolean {
        if (selectionPreviewBoundsState?.value == null) return false
        if (lastBoundsLookupX.isNaN() || lastBoundsLookupY.isNaN()) return false
        return hypot(anchor.x - lastBoundsLookupX, anchor.y - lastBoundsLookupY) < minMovePx
    }

    private fun cancelPauseTimer() {
        pauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pauseRunnable = null
        pausePrefetchRunnable?.let { mainHandler.removeCallbacks(it) }
        pausePrefetchRunnable = null
    }

    private fun cancelBallLayoutThrottle() {
        ballLayoutThrottleRunnable?.let { mainHandler.removeCallbacks(it) }
        ballLayoutThrottleRunnable = null
    }

    private fun cancelCursorUpdateThrottle() {
        cursorUpdateThrottleRunnable?.let { mainHandler.removeCallbacks(it) }
        cursorUpdateThrottleRunnable = null
        pendingPickAnchor = null
    }

    private fun scheduleThrottledCursorUpdate() {
        if (cursorUpdateThrottleRunnable != null) return
        val delayMs = CURSOR_UPDATE_MIN_INTERVAL_MS - (SystemClock.uptimeMillis() - lastCursorUpdateMs)
        val runnable = Runnable {
            cursorUpdateThrottleRunnable = null
            if (!isDragging) return@Runnable
            commitPickAnchor()
        }
        cursorUpdateThrottleRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs.coerceAtLeast(1L))
    }

    private fun commitPickAnchor() {
        val pick = pendingPickAnchor ?: return
        pendingPickAnchor = null
        lastCursorUpdateMs = SystemClock.uptimeMillis()
        cursorAnchorState?.value = pick
    }

    private fun applyPickAnchor(pick: Offset) {
        pendingPickAnchor = pick
        val now = SystemClock.uptimeMillis()
        if (now - lastCursorUpdateMs >= CURSOR_UPDATE_MIN_INTERVAL_MS) {
            commitPickAnchor()
            return
        }
        scheduleThrottledCursorUpdate()
    }

    private fun applyDragBallLayout(settings: AppSettings) {
        val wm = windowManager ?: return
        val view = ballView ?: return
        val params = ballParams ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val ballSizePx = FloatBallLayout.ballSizePx(settings, density)
        val marginPx = FloatBallLayout.marginPx(density)
        val bounds = overlayScreenBounds(metrics)
        val activeSide = effectiveActiveSide(settings)
        val center = dragSession.clampedBallCenter(
            ballSizePx = ballSizePx.toFloat(),
            marginPx = marginPx,
            screenWidth = bounds.width.roundToInt(),
            screenHeight = bounds.height.roundToInt(),
        )

        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            params.x = (center.x - ballSizePx / 2f).roundToInt()
            params.y = (center.y - ballSizePx / 2f).roundToInt()
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            val strip = FloatBallLayout.edgeStripBounds(settings, metrics, activeSide)
            val (windowX, windowY) = FloatBallLayout.stripWindowOriginForBallCenter(
                settings = settings,
                metrics = metrics,
                activeSide = activeSide,
                ballCenterX = center.x,
                ballCenterY = center.y,
            )
            params.x = windowX
            params.y = windowY
            params.width = strip.width()
            params.height = strip.height()
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun scheduleThrottledBallLayout() {
        if (ballLayoutThrottleRunnable != null) return
        val delayMs = BALL_LAYOUT_MIN_INTERVAL_MS - (SystemClock.uptimeMillis() - lastBallLayoutMs)
        val runnable = Runnable {
            ballLayoutThrottleRunnable = null
            if (!isDragging) return@Runnable
            val settings = settingsState?.value ?: return@Runnable
            lastBallLayoutMs = SystemClock.uptimeMillis()
            applyDragBallLayout(settings)
        }
        ballLayoutThrottleRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs.coerceAtLeast(1L))
    }

    private fun updatePickAndBallFromFinger(moveBallWindow: Boolean) {
        val view = ballView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = dragScreenBounds ?: overlayScreenBounds(metrics).also { dragScreenBounds = it }
        val ballSizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt()
        val marginPx = (EDGE_MARGIN_DP * density).roundToInt()
        val screenWidth = bounds.width
        val screenHeight = bounds.height

        val pick = dragSession.computePick(
            settings = settings,
            ballSizePx = ballSizePx.toFloat(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            marginPx = marginPx,
        )
        applyPickAnchor(pick)
        if (cursorVisibleState?.value == true && cursorPausedState?.value != true) {
            schedulePreviewBoundsLookup(pick, density)
        }

        if (!moveBallWindow) return
        scheduleThrottledBallLayout()
    }

    private fun overlayScreenBounds(fallback: android.util.DisplayMetrics): OverlayScreenBounds {
        val wm = windowManager
        if (wm != null) {
            val bounds = runCatching { wm.currentWindowMetrics.bounds }.getOrNull()
            if (bounds != null) {
                return OverlayScreenBounds(
                    width = bounds.width().toFloat(),
                    height = bounds.height().toFloat(),
                )
            }
        }
        return OverlayScreenBounds(
            width = fallback.widthPixels.toFloat(),
            height = fallback.heightPixels.toFloat(),
        )
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) hideCursor()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }
}

@Composable
private fun FloatBallEdgeCaptureContent(
    windowOrigin: () -> IntOffset,
    onDragStart: (screenX: Float, screenY: Float) -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val origin = windowOrigin()
                        onDragStart(origin.x + offset.x, origin.y + offset.y)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            },
    )
}

@Composable
private fun FloatBallLineContent(
    settingsState: MutableState<AppSettings>,
    windowOrigin: () -> IntOffset,
    onLineDragDown: (screenX: Float, screenY: Float) -> Unit,
    onLineDrag: (dx: Float, dy: Float) -> Unit,
    onLineDragEnd: () -> Unit,
    onLineDragCancel: () -> Unit,
) {
    val settings = settingsState.value
    val lineColor = Color(settings.themeColorArgb)
        .copy(alpha = settings.floatBallLineOpacity.coerceIn(0.1f, 1f))
    val inactiveSide = FloatBallSide.opposite(FloatBallLayout.resolvedActiveSide(settings))
    val density = LocalDensity.current
    val lineWidth = with(density) { 4.dp }
    val outerAlignment = when (inactiveSide) {
        FloatBallSide.LEFT -> Alignment.CenterStart
        FloatBallSide.RIGHT -> Alignment.CenterEnd
    }
    val roundedEdge = when (inactiveSide) {
        FloatBallSide.LEFT -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        FloatBallSide.RIGHT -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val origin = windowOrigin()
                        onLineDragDown(origin.x + offset.x, origin.y + offset.y)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onLineDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = { onLineDragEnd() },
                    onDragCancel = { onLineDragCancel() },
                )
            },
        contentAlignment = outerAlignment,
    ) {
        Box(
            modifier = Modifier
                .width(lineWidth)
                .fillMaxHeight()
                .clip(roundedEdge)
                .background(lineColor),
        )
    }
}

@Composable
private fun FloatBallContent(
    settingsState: MutableState<AppSettings>,
) {
    val settings = settingsState.value
    val sizeDp = settings.floatBallSizeDp.coerceIn(36f, 72f).dp
    val ballColor = Color(settings.themeColorArgb).copy(alpha = settings.floatBallOpacity.coerceIn(0.3f, 1f))
    val isCustom = settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM
    val activeSide = FloatBallLayout.resolvedActiveSide(settings)
    val dockAlignment = when {
        isCustom -> Alignment.Center
        activeSide == FloatBallSide.LEFT -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }

    SlideIndexTheme {
        if (isCustom) {
            FloatBallVisual(sizeDp = sizeDp, ballColor = ballColor)
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = dockAlignment,
            ) {
                FloatBallVisual(sizeDp = sizeDp, ballColor = ballColor)
            }
        }
    }
}

@Composable
private fun FloatBallVisual(sizeDp: androidx.compose.ui.unit.Dp, ballColor: Color) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(ballColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((sizeDp.value * 0.35f).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f)),
        )
    }
}

@Composable
private fun FloatBallCursorContent(
    visible: Boolean,
    paused: Boolean,
    selectionStart: Offset?,
    selectionPreviewBounds: Rect?,
    pickAnchor: Offset,
) {
    if (!visible) return
    val crossColor = if (paused) Color(0xFFFFC107) else Color(0xFFE53935)
    val markerCenter = pickAnchor
    val density = LocalDensity.current
    val armPx = with(density) { CROSS_ARM_DP.dp.toPx() }
    val strokePx = with(density) { CROSS_STROKE_DP.dp.toPx() }
    val plusRadiusPx = with(density) { 11.dp.toPx() }
    val minRegionalPx = with(density) { RECT_MIN_SIDE_DP.dp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val useControlBounds = selectionPreviewBounds != null &&
            (selectionStart == null ||
                (kotlin.math.abs(pickAnchor.x - selectionStart.x) < minRegionalPx &&
                    kotlin.math.abs(pickAnchor.y - selectionStart.y) < minRegionalPx))
        if (useControlBounds) {
            val bounds = selectionPreviewBounds
            val previewLeft = bounds.left.toFloat()
            val previewTop = bounds.top.toFloat()
            val previewRight = bounds.right.toFloat()
            val previewBottom = bounds.bottom.toFloat()
            if (previewRight > previewLeft && previewBottom > previewTop) {
                val fillColor = if (paused) Color(0x33FFC107) else Color(0x22E53935)
                val strokeColor = if (paused) Color(0xFFFFC107) else Color(0xFFE53935)
                drawRect(
                    color = fillColor,
                    topLeft = Offset(previewLeft, previewTop),
                    size = androidx.compose.ui.geometry.Size(
                        previewRight - previewLeft,
                        previewBottom - previewTop,
                    ),
                )
                drawRect(
                    color = strokeColor,
                    topLeft = Offset(previewLeft, previewTop),
                    size = androidx.compose.ui.geometry.Size(
                        previewRight - previewLeft,
                        previewBottom - previewTop,
                    ),
                    style = Stroke(width = strokePx),
                )
            }
        } else if (paused && selectionStart != null) {
            val previewLeft = min(selectionStart.x, pickAnchor.x)
            val previewTop = min(selectionStart.y, pickAnchor.y)
            val previewRight = max(selectionStart.x, pickAnchor.x)
            val previewBottom = max(selectionStart.y, pickAnchor.y)
            if (previewRight > previewLeft && previewBottom > previewTop) {
                drawRect(
                    color = Color(0x33FFC107),
                    topLeft = Offset(previewLeft, previewTop),
                    size = androidx.compose.ui.geometry.Size(
                        previewRight - previewLeft,
                        previewBottom - previewTop,
                    ),
                )
                drawRect(
                    color = Color(0xFFFFC107),
                    topLeft = Offset(previewLeft, previewTop),
                    size = androidx.compose.ui.geometry.Size(
                        previewRight - previewLeft,
                        previewBottom - previewTop,
                    ),
                    style = Stroke(width = strokePx),
                )
            }
        }
        if (paused) {
            drawCircle(
                color = crossColor,
                radius = plusRadiusPx,
                center = markerCenter,
            )
            val plusArm = plusRadiusPx * 0.42f
            val plusStroke = strokePx * 1.1f
            drawLine(
                color = Color.White,
                start = Offset(markerCenter.x - plusArm, markerCenter.y),
                end = Offset(markerCenter.x + plusArm, markerCenter.y),
                strokeWidth = plusStroke,
            )
            drawLine(
                color = Color.White,
                start = Offset(markerCenter.x, markerCenter.y - plusArm),
                end = Offset(markerCenter.x, markerCenter.y + plusArm),
                strokeWidth = plusStroke,
            )
        } else {
            drawLine(
                color = crossColor,
                start = Offset(markerCenter.x - armPx, markerCenter.y),
                end = Offset(markerCenter.x + armPx, markerCenter.y),
                strokeWidth = strokePx,
            )
            drawLine(
                color = crossColor,
                start = Offset(markerCenter.x, markerCenter.y - armPx),
                end = Offset(markerCenter.x, markerCenter.y + armPx),
                strokeWidth = strokePx,
            )
            drawCircle(
                color = crossColor,
                radius = strokePx * 1.2f,
                center = markerCenter,
                style = Stroke(width = strokePx),
            )
        }
    }
}
