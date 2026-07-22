package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.Rect
import androidx.core.net.toUri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.inspire.PickPrefetchCache
import com.slideindex.app.service.AccessibilityTextExtractor
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.PermissionHelper
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
@SuppressLint("StaticFieldLeak")
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
    private var edgeCaptureHost: FloatBallStripHost? = null
    private var edgeCaptureComposeView: ComposeView? = null
    private var lineHost: FloatBallStripHost? = null
    private var lineComposeView: ComposeView? = null
    private var cursorView: ComposeView? = null
    private var ballOwner: OverlayComposeOwner? = null
    private var edgeCaptureOwner: OverlayComposeOwner? = null
    private var lineOwner: OverlayComposeOwner? = null
    private var cursorOwner: OverlayComposeOwner? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var edgeCaptureParams: WindowManager.LayoutParams? = null
    private var lineParams: WindowManager.LayoutParams? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    private var settingsState: MutableState<AppSettings>? = null
    private var cursorVisibleState: MutableState<Boolean>? = null
    private var cursorPausedState: MutableState<Boolean>? = null
    private var cursorAnchorState: MutableState<Offset>? = null
    private var selectionStartState: MutableState<Offset?>? = null
    private var selectionPreviewBoundsState: MutableState<Rect?>? = null
    private var stripZonePreviewState: MutableState<Boolean>? = null
    private var styleVisualGenerationState: MutableState<Int>? = null

    private var onPositionPersisted: ((xFraction: Float, yFraction: Float) -> Unit)? = null
    private var onActiveSidePersisted: ((FloatBallSide) -> Unit)? = null
    private var pauseRunnable: Runnable? = null
    private var pausePrefetchRunnable: Runnable? = null
    private var captureSuppressed = false
    private var isDragging = false
    private var dragOriginatedFromLine = false
    private var lineDragEndedWithGesture = false
    private var dragActiveSideOverride: FloatBallSide? = null
    private var passthroughRestorePending = false
    private var committedActiveSideUntilPersist: FloatBallSide? = null
    private var activeSideAtDragStart: FloatBallSide? = null
    private var finishDragRequested = false
    private var ballLayoutFrameScheduled = false
    private var cursorCommitFrameScheduled = false
    private var pendingPickAnchor: Offset? = null
    private var dragScreenBounds: OverlayScreenBounds? = null
    private var boundsLookupGeneration = 0
    private var lastBoundsLookupMs = 0L
    private var lastBoundsLookupX = Float.NaN
    private var lastBoundsLookupY = Float.NaN
    private var boundsLookupThrottleRunnable: Runnable? = null
    private val gestureHintWindow = FloatBallGestureHintWindow()
    private var currentGestureHintType: FloatBallGestureType? = null
    private var dragCursorRaised = false
    private var dragHintRaised = false

    private data class PreviewBoundsLookupProfile(
        val intervalMs: Long,
        val maxNodes: Int,
        val minMovePx: Float,
    )

    val isShowing: Boolean get() = ballView != null

    /** Ball/line/cursor/hint WM layers must stay above panel windows for z-order. */
    fun bringChromeAbovePanels() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringChromeAbovePanels() }
            return
        }
        // Re-adding ball/line during an active drag cancels the in-flight pointer gesture.
        if (!isDragging) {
            val line = lineHost
            val lineLp = lineParams
            if (line != null && lineLp != null && line.visibility == View.VISIBLE) {
                bringOverlayToFront(line, lineLp)
            }
            bringBallAboveLine()
        }
        val cursor = cursorView
        val cursorLp = cursorParams
        if (cursor != null && cursorLp != null) {
            bringOverlayToFront(cursor, cursorLp)
        }
        gestureHintWindow.bringToFront()
    }

    private fun raiseDragCursorAbovePanelsOnce() {
        if (dragCursorRaised) return
        dragCursorRaised = true
        val cursor = cursorView ?: return
        val cursorLp = cursorParams ?: return
        bringOverlayToFront(cursor, cursorLp)
    }

    private fun raiseDragGestureHintAbovePanelsOnce() {
        if (dragHintRaised) return
        dragHintRaised = true
        gestureHintWindow.bringToFront()
    }

    private fun resetDragChromeRaiseState() {
        dragCursorRaised = false
        dragHintRaised = false
    }

    fun setStripZonePreviewActive(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setStripZonePreviewActive(active) }
            return
        }
        stripZonePreviewState?.value = active
    }

    fun refreshStyleVisual() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshStyleVisual() }
            return
        }
        styleVisualGenerationState?.let { state ->
            state.value = state.value + 1
        }
        ballComposeView?.invalidate()
        settingsState?.value?.let { applyAllLayouts(it) }
    }

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
            if (floatBallStyleSignature(current) != floatBallStyleSignature(merged)) {
                refreshStyleVisual()
            }
            (ballView as? FloatBallStripHost)?.updateSettings(merged)
            edgeCaptureHost?.updateSettings(merged)
            lineHost?.updateSettings(merged)
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
        edgeCaptureHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        lineHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        cursorView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        gestureHintWindow.detach()
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        OverlayCompose.disposeComposeView(ballComposeView)
        OverlayCompose.disposeComposeView(edgeCaptureComposeView)
        OverlayCompose.disposeComposeView(lineComposeView)
        OverlayCompose.disposeComposeView(cursorView)
        ballOwner?.destroy()
        edgeCaptureOwner?.destroy()
        lineOwner?.destroy()
        cursorOwner?.destroy()
        FloatBallPickResultPanel.destroy()
        FloatBallStashPanel.destroy()
        ballOwner = null
        edgeCaptureOwner = null
        lineOwner = null
        cursorOwner = null
        ballComposeView = null
        ballView = null
        edgeCaptureHost = null
        edgeCaptureComposeView = null
        lineHost = null
        lineComposeView = null
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
        stripZonePreviewState = null
        styleVisualGenerationState = null
        onPositionPersisted = null
        onActiveSidePersisted = null
        screenOffReceiver = null
        appContext = null
        isDragging = false
        dragOriginatedFromLine = false
        lineDragEndedWithGesture = false
        dragActiveSideOverride = null
        committedActiveSideUntilPersist = null
        activeSideAtDragStart = null
        cancelBallLayoutFrame()
        cancelCursorCommitFrame()
        dragSession.reset()
        currentGestureHintType = null
        resetDragChromeRaiseState()
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
        edgeCaptureHost?.visibility = View.GONE
        lineHost?.visibility = View.GONE
        hideGestureHintWindow()
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

    /** Ends drag UI when the screen turns off; chrome windows stay attached. */
    fun onScreenOff() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onScreenOff() }
            return
        }
        hideCursor()
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
        val stripZonePreview = mutableStateOf(false)
        stripZonePreviewState = stripZonePreview
        val styleVisualGeneration = mutableStateOf(0)
        styleVisualGenerationState = styleVisualGeneration

        val dragCallbacks = object {
            fun onGestureHint(gestureType: FloatBallGestureType?) {
                currentGestureHintType = gestureType
                updateGestureHintWindow()
            }

            fun onStart(screenX: Float, screenY: Float) {
                activeSideAtDragStart = null
                dragOriginatedFromLine = false
                lineDragEndedWithGesture = false
                dragActiveSideOverride = null
                edgeCaptureHost?.visibility = View.GONE
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
                lineDragEndedWithGesture = false
                dragActiveSideOverride = null
                hideCursor()
                settingsState?.value?.let { restoreDockPosition(it) }
                updateChromeVisibility(settingsHolder.value)
            }
        }

        val ballDialogOwner = OverlayComposeOwner()
        val ballHost = FloatBallStripHost(overlayContext).apply {
            OverlayCompose.bindOwners(this, ballDialogOwner)
            updateSettings(settings)
            bindDragCallbacks(
                onDragStart = { screenX, screenY -> dragCallbacks.onStart(screenX, screenY) },
                onDrag = { dx, dy -> dragCallbacks.onDrag(dx, dy) },
                onDragEnd = { dragCallbacks.onEnd() },
                onDragCancel = { dragCallbacks.onCancel() },
                onGesture = { gestureType, rawX, rawY ->
                    hideGestureHintWindow()
                    performFloatBallGesture(settingsHolder.value, gestureType, rawX, rawY)
                },
                onGestureHint = dragCallbacks::onGestureHint,
            )
        }
        val ballCompose = OverlayCompose.createComposeView(overlayContext, ballDialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                FloatBallContent(
                    settingsState = settingsHolder,
                    stripZonePreviewState = stripZonePreview,
                    styleVisualGenerationState = styleVisualGeneration,
                )
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
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                FloatBallEdgeCaptureContent()
            }
        }
        val edgeHost = FloatBallStripHost(overlayContext).apply {
            OverlayCompose.bindOwners(this, edgeCaptureDialogOwner)
            updateSettings(settings)
            bindDragCallbacks(
                onDragStart = { screenX, screenY -> dragCallbacks.onStart(screenX, screenY) },
                onDrag = { dx, dy -> dragCallbacks.onDrag(dx, dy) },
                onDragEnd = { dragCallbacks.onEnd() },
                onDragCancel = { dragCallbacks.onCancel() },
                onGesture = { gestureType, rawX, rawY ->
                    hideGestureHintWindow()
                    performFloatBallGesture(settingsHolder.value, gestureType, rawX, rawY)
                },
                onGestureHint = dragCallbacks::onGestureHint,
            )
        }
        edgeHost.addView(
            edgeCaptureCompose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val lineDialogOwner = OverlayComposeOwner()
        val lineCompose = OverlayCompose.createComposeView(overlayContext, lineDialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                FloatBallLineContent(
                    settingsState = settingsHolder,
                    stripZonePreviewState = stripZonePreview,
                )
            }
        }
        val lineStripHost = FloatBallStripHost(overlayContext).apply {
            OverlayCompose.bindOwners(this, lineDialogOwner)
            updateSettings(settings)
            bindDragCallbacks(
                onDragStart = { screenX, screenY -> prepareLineDrag(screenX, screenY) },
                onDrag = { dx, dy ->
                    onFingerDrag(dx, dy)
                    onDragMoved()
                },
                onDragEnd = {
                    commitLineDragSideSwap()
                    completeDragGesture()
                },
                onDragCancel = {
                    if (lineDragEndedWithGesture) {
                        lineDragEndedWithGesture = false
                        commitLineDragSideSwap()
                    } else {
                        revertLineDragSideSwapIfNeeded()
                    }
                    cancelDragWithoutPick()
                },
                onGesture = { gestureType, rawX, rawY ->
                    lineDragEndedWithGesture = true
                    hideGestureHintWindow()
                    performFloatBallGesture(settingsHolder.value, gestureType, rawX, rawY)
                },
                onGestureHint = dragCallbacks::onGestureHint,
            )
        }
        lineStripHost.addView(
            lineCompose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

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

        val edgeAdded = runCatching { wm.addView(edgeHost, edgeCaptureLp) }.isSuccess
        if (!edgeAdded) {
            ballDialogOwner.destroy()
            edgeCaptureDialogOwner.destroy()
            lineDialogOwner.destroy()
            cursorDialogOwner.destroy()
            Log.e(TAG, "failed to add edge capture overlay")
            return
        }
        val lineAdded = runCatching { wm.addView(lineStripHost, lineLp) }.isSuccess
        if (!lineAdded) {
            runCatching { wm.removeView(edgeHost) }
            ballDialogOwner.destroy()
            edgeCaptureDialogOwner.destroy()
            lineDialogOwner.destroy()
            cursorDialogOwner.destroy()
            Log.e(TAG, "failed to add line overlay")
            return
        }
        val ballAdded = runCatching { wm.addView(ballHost, ballLp) }.isSuccess
        if (!ballAdded) {
            runCatching { wm.removeView(edgeHost) }
            runCatching { wm.removeView(lineStripHost) }
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
            runCatching { wm.removeView(edgeHost) }
            runCatching { wm.removeView(lineStripHost) }
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
        edgeCaptureHost = edgeHost
        edgeCaptureComposeView = edgeCaptureCompose
        lineHost = lineStripHost
        lineComposeView = lineCompose
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
        gestureHintWindow.attach(hostContext, wm)

        applyAllLayouts(settings)
    }

    private fun hideGestureHintWindow() {
        currentGestureHintType = null
        gestureHintWindow.hide()
    }

    private fun updateGestureHintWindow() {
        val gestureType = currentGestureHintType
        if (gestureType == null || !isDragging) {
            gestureHintWindow.hide()
            return
        }
        val settings = settingsState?.value ?: run {
            gestureHintWindow.hide()
            return
        }
        val action = settings.floatBallGestureActions[gestureType] ?: GestureAction.None
        if (action is GestureAction.None) {
            gestureHintWindow.hide()
            return
        }
        val view = ballView ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        gestureHintWindow.update(
            action = action,
            themeColorArgb = settings.themeColorArgb,
            fingerX = dragSession.dragFingerX,
            fingerY = dragSession.dragFingerY,
            dockSide = effectiveActiveSide(settings),
            density = density,
        )
        raiseDragGestureHintAbovePanelsOnce()
    }

    private fun performFloatBallGesture(
        settings: AppSettings,
        gestureType: FloatBallGestureType,
        rawX: Float,
        rawY: Float,
    ) {
        val action = settings.floatBallGestureActions[gestureType] ?: GestureAction.None
        if (action is GestureAction.None) return
        if (action is GestureAction.ClickPassthrough) {
            OverlayPassthrough.run(
                hideTriggers = ::hideFloatBallOverlaysForPassthrough,
                showTriggers = ::restoreFloatBallOverlaysAfterPassthrough,
                rawX = rawX,
                rawY = rawY,
                onComplete = {},
            )
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: ballView?.context?.applicationContext
            ?: return
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext) ?: return
        ActionExecutor(
            context = hostContext,
            appRepository = deps.appRepository,
            onShellCommandsPersist = { commands ->
                overlayScope.launch {
                    deps.settingsRepository.setShellCommands(commands)
                }
            },
        ).execute(
            action = action,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
        )
    }

    private fun hideFloatBallOverlaysForPassthrough() {
        passthroughRestorePending = true
        ballView?.visibility = View.GONE
        edgeCaptureHost?.visibility = View.GONE
        lineHost?.visibility = View.GONE
        cursorView?.visibility = View.GONE
        hideGestureHintWindow()
    }

    private fun restoreFloatBallOverlaysAfterPassthrough() {
        if (!passthroughRestorePending) return
        passthroughRestorePending = false
        settingsState?.value?.let { updateChromeVisibility(it) }
        if (cursorVisibleState?.value == true) {
            cursorView?.visibility = View.VISIBLE
        }
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
            lineHost?.let { runCatching { wm.updateViewLayout(it, params) } }
        }

        (ballView as? FloatBallStripHost)?.stripTouchable = true
        edgeCaptureHost?.visibility = View.GONE
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
        val ball = ballView ?: return
        val ballLp = ballParams ?: return
        bringOverlayToFront(ball, ballLp)
    }

    private fun recoverStuckLineCaptureIfNeeded(settings: AppSettings) {
        val metrics = lineHost?.resources?.displayMetrics ?: return
        val params = lineParams ?: return
        val stuckFullscreen = params.width >= metrics.widthPixels || params.height >= metrics.heightPixels
        if (!stuckFullscreen) return
        Log.w(TAG, "recovering stuck fullscreen line overlay")
        isDragging = false
        dragOriginatedFromLine = false
        lineDragEndedWithGesture = false
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
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
        val (screenWidthPx, screenHeightPx) = layoutScreenSize(metrics)
        val activeSide = FloatBallLayout.resolvedActiveSide(settings)
        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            val (left, top) = FloatBallLayout.ballTopLeft(
                settings,
                metrics,
                activeSide,
                screenWidthPx,
                screenHeightPx,
            )
            params.x = left
            params.y = top
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            val bounds = FloatBallLayout.ballWindowBounds(
                settings,
                metrics,
                activeSide,
                screenWidthPx,
                screenHeightPx,
            )
            params.x = bounds.left
            params.y = bounds.top
            params.width = bounds.width()
            params.height = bounds.height()
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyEdgeCaptureLayout(settings: AppSettings) {
        val wm = windowManager ?: return
        val view = edgeCaptureHost ?: return
        val params = edgeCaptureParams ?: return
        val metrics = view.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = layoutScreenSize(metrics)
        val activeSide = FloatBallLayout.resolvedActiveSide(settings)
        val bounds = FloatBallLayout.ballWindowBounds(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx,
        )
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyLineLayout(settings: AppSettings) {
        val wm = windowManager ?: return
        val view = lineHost ?: return
        val params = lineParams ?: return
        val metrics = view.resources.displayMetrics
        if (!FloatBallLayout.shouldShowLine(settings)) return
        val inactiveSide = FloatBallSide.opposite(FloatBallLayout.resolvedActiveSide(settings))
        val (screenWidthPx, screenHeightPx) = layoutScreenSize(metrics)
        val bounds = FloatBallLayout.lineStripBounds(
            settings,
            metrics,
            inactiveSide,
            screenWidthPx,
            screenHeightPx,
        )
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun updateChromeVisibility(settings: AppSettings) {
        if (captureSuppressed) {
            ballView?.visibility = View.GONE
            edgeCaptureHost?.visibility = View.GONE
            lineHost?.visibility = View.GONE
            return
        }
        if (isDragging) {
            ballView?.visibility = View.VISIBLE
            edgeCaptureHost?.visibility = View.GONE
            lineHost?.visibility = if (FloatBallLayout.shouldShowLine(settings)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            return
        }
        ballView?.visibility = View.VISIBLE
        edgeCaptureHost?.visibility = View.GONE
        lineHost?.visibility = if (FloatBallLayout.shouldShowLine(settings)) View.VISIBLE else View.GONE
    }

    private fun effectiveActiveSide(settings: AppSettings): FloatBallSide =
        dragActiveSideOverride ?: FloatBallLayout.resolvedActiveSide(settings)

    private fun prepareLineDrag(screenX: Float, screenY: Float) {
        val settings = settingsState?.value ?: return
        val dockedSide = FloatBallLayout.resolvedActiveSide(settings)
        val bothEdges = settings.floatBallPositionMode == FloatBallPositionMode.BOTH_EDGES
        activeSideAtDragStart = if (bothEdges) dockedSide else null
        dragOriginatedFromLine = bothEdges
        lineDragEndedWithGesture = false
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
        cancelBallLayoutFrame()
        cancelCursorCommitFrame()
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
        dragSession.onFingerMove(dx, dy)
        updatePickAndBallFromFinger(moveBallWindow = true)
    }

    private fun finishDrag(settings: AppSettings) {
        if (!isDragging) return
        cancelBallLayoutFrame()
        cancelCursorCommitFrame()
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
                    PickPerf.mark("showResultPanel_callback")
                    FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                    PickPerf.endSession("END", "regional_rect")
                }
            }
            else -> {
                val bounds = previewBounds ?: return
                val panelAnchorX = bounds.centerX().toFloat()
                val panelAnchorY = bounds.bottom.toFloat()
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
                }
            }
        }
    }

    private fun snapBallToEdge(settings: AppSettings) {
        restoreDockPosition(settings)
    }

    private fun persistBallCenterFraction() {
        val view = ballView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = layoutScreenSize(metrics)
        val density = metrics.density
        val ballSizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt()
        val activeSide = FloatBallLayout.resolvedActiveSide(settings)
        val (centerX, centerY) = FloatBallLayout.ballCenterPx(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx,
        )
        val customCenterXFraction = FloatBallLayout.coerceCustomCenterXFraction(centerX / screenWidthPx)
        val yFraction = FloatBallLayout.coercePositionYFraction(centerY / metrics.heightPixels)
        onPositionPersisted?.invoke(customCenterXFraction, yFraction)
    }

    private fun showCursorAtScreenTouch(
        screenX: Float,
        screenY: Float,
        deferBallWindowMutation: Boolean = false,
    ) {
        val view = ballView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = overlayScreenBounds(metrics)
        val ballSizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt()
        val screenWidth = bounds.width
        val screenHeight = bounds.height

        val (screenWidthPx, screenHeightPx) = layoutScreenSize(metrics)
        val activeSide = effectiveActiveSide(settings)
        val (ballCenterX, ballCenterY) = FloatBallLayout.ballCenterPx(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx,
        )
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
            dockSide = activeSide,
            // 左侧线条：加号跟手指才能贴左缘；右侧线条仍用球心偏移，保持从屏外进入。
            anchorPickAtFinger = dragOriginatedFromLine && activeSide == FloatBallSide.LEFT,
        )

        isDragging = true
        if (dragOriginatedFromLine && !deferBallWindowMutation) {
            setBallTouchable(false)
        }
        cancelBallLayoutFrame()
        cancelCursorCommitFrame()
        pendingPickAnchor = null
        lastBoundsLookupMs = 0L
        lastBoundsLookupX = Float.NaN
        lastBoundsLookupY = Float.NaN
        cancelBoundsLookupThrottle()
        dragScreenBounds = overlayScreenBounds(metrics)
        PickPrefetchCache.invalidate()
        selectionStartState?.value = null
        selectionPreviewBoundsState?.value = null
        cursorVisibleState?.value = true
        cursorPausedState?.value = false
        cursorView?.visibility = View.VISIBLE
        // Do not move or resize the ball window here — that cancels the Compose drag gesture.
        updatePickAndBallFromFinger(moveBallWindow = true)
        settingsState?.value?.let { updateChromeVisibility(it) }
        mainHandler.post {
            if (!isDragging) return@post
            raiseDragCursorAbovePanelsOnce()
        }
        schedulePauseTimer()
    }

    private fun setBallTouchable(touchable: Boolean) {
        val strip = ballView as? FloatBallStripHost ?: return
        strip.stripTouchable = touchable
    }

    private fun clearCursorUi() {
        dragOriginatedFromLine = false
        lineDragEndedWithGesture = false
        dragActiveSideOverride = null
        selectionPreviewBoundsState?.value = null
        setBallTouchable(true)
        settingsState?.value?.let { restorePassiveOverlayLayout(it) }
        cancelPauseTimer()
        cancelBallLayoutFrame()
        cancelCursorCommitFrame()
        cancelBoundsLookupThrottle()
        boundsLookupGeneration++
        lastBoundsLookupMs = 0L
        lastBoundsLookupX = Float.NaN
        lastBoundsLookupY = Float.NaN
        dragScreenBounds = null
        dragSession.reset()
        hideGestureHintWindow()
        resetDragChromeRaiseState()
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
                    PickPrefetchCache.invalidate()
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
        if (!hasRecentPreviewBoundsAt(anchor, profile.minMovePx)) {
            launchPreviewBoundsLookup(anchor, profile)
        }
        maybeStartPickPrefetch()
    }

    private fun maybeStartPickPrefetch() {
        if (cursorPausedState?.value != true) return
        val bounds = selectionPreviewBoundsState?.value ?: return
        val host = appContext ?: return
        FloatBallPickResultPanel.warmUp(host)
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        PickPrefetchCache.startPreviewA11yPrefetch(
            service = service,
            rect = bounds,
            generation = boundsLookupGeneration,
        )
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
                if (paused) {
                    maybeStartPickPrefetch()
                }
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

    private fun cancelBallLayoutFrame() {
        ballLayoutFrameScheduled = false
    }

    private fun cancelCursorCommitFrame() {
        cursorCommitFrameScheduled = false
        pendingPickAnchor = null
    }

    private fun scheduleBallLayoutOnNextFrame() {
        val view = ballView ?: return
        if (ballLayoutFrameScheduled) return
        ballLayoutFrameScheduled = true
        view.postOnAnimation {
            ballLayoutFrameScheduled = false
            if (!isDragging) return@postOnAnimation
            val settings = settingsState?.value ?: return@postOnAnimation
            applyDragBallLayout(settings)
        }
    }

    private fun scheduleCursorCommitOnNextFrame() {
        val view = ballView ?: return
        if (cursorCommitFrameScheduled) return
        cursorCommitFrameScheduled = true
        view.postOnAnimation {
            cursorCommitFrameScheduled = false
            if (!isDragging) return@postOnAnimation
            commitPickAnchor()
        }
    }

    private fun commitPickAnchor() {
        val pick = pendingPickAnchor ?: return
        pendingPickAnchor = null
        cursorAnchorState?.value = pick
    }

    private fun applyPickAnchor(pick: Offset) {
        if (isDragging) {
            cursorAnchorState?.value = pick
            return
        }
        pendingPickAnchor = pick
        scheduleCursorCommitOnNextFrame()
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
            val sizedBallPx = FloatBallLayout.ballSizePx(settings, metrics.density)
            val (windowX, windowY) = FloatBallLayout.stripWindowOriginForBallCenter(
                settings = settings,
                metrics = metrics,
                activeSide = activeSide,
                ballCenterX = center.x,
                ballCenterY = center.y,
                screenHeightPx = bounds.height.roundToInt(),
            )
            params.x = windowX
            params.y = windowY
            params.width = sizedBallPx
            params.height = sizedBallPx
        }
        runCatching { wm.updateViewLayout(view, params) }
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
        scheduleBallLayoutOnNextFrame()
    }

    private fun layoutScreenSize(metrics: android.util.DisplayMetrics): Pair<Int, Int> {
        val bounds = overlayScreenBounds(metrics)
        return bounds.width.roundToInt() to bounds.height.roundToInt()
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
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) hideCursor()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun floatBallStyleSignature(settings: AppSettings?): String {
        if (settings == null) return ""
        return buildString {
            append(settings.floatBallStyleType.storageKey)
            append('|')
            append(settings.floatBallGifUri)
            append('|')
            append(settings.floatBallCustomImageUri)
            append('|')
            append(settings.floatBallSlideshowUris.joinToString(","))
        }
    }
}

@Composable
private fun FloatBallEdgeCaptureContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    )
}

@Composable
private fun FloatBallLineContent(
    settingsState: MutableState<AppSettings>,
    stripZonePreviewState: MutableState<Boolean>,
) {
    val settings = settingsState.value
    val stripPreviewActive by stripZonePreviewState
    val inactiveSide = FloatBallSide.opposite(FloatBallLayout.resolvedActiveSide(settings))
    val lineColor = Color(settings.themeColorArgb)
        .copy(alpha = settings.floatBallLineOpacity.coerceIn(0.1f, 1f))

    Box(modifier = Modifier.fillMaxSize()) {
        if (stripPreviewActive) {
            FloatBallStripZonePreviewLayer(
                settings = settings,
                side = inactiveSide,
                lineColor = lineColor,
                showEdgeLine = true,
            )
        } else {
            FloatBallEdgeLineVisual(
                side = inactiveSide,
                lineColor = lineColor,
            )
        }
    }
}

@Composable
private fun FloatBallContent(
    settingsState: MutableState<AppSettings>,
    stripZonePreviewState: MutableState<Boolean>,
    styleVisualGenerationState: MutableState<Int>,
) {
    val settings = settingsState.value
    val stripPreviewActive by stripZonePreviewState
    val styleGeneration by styleVisualGenerationState
    val sizeDp = settings.floatBallSizeDp.coerceIn(36f, 72f).dp
    val ballColor = Color(settings.themeColorArgb).copy(alpha = settings.floatBallOpacity.coerceIn(0.3f, 1f))
    val isCustom = settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM
    val activeSide = FloatBallLayout.resolvedActiveSide(settings)
    val dockAlignment = when {
        isCustom -> Alignment.Center
        activeSide == FloatBallSide.LEFT -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    val lineColor = Color(settings.themeColorArgb)
        .copy(alpha = settings.floatBallLineOpacity.coerceIn(0.1f, 1f))

    SlideIndexTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (isCustom) Alignment.Center else dockAlignment,
        ) {
            if (stripPreviewActive && !isCustom) {
                FloatBallStripZonePreviewLayer(
                    settings = settings,
                    side = activeSide,
                    lineColor = lineColor,
                    showEdgeLine = false,
                )
            }
            if (isCustom) {
                key(styleGeneration) {
                    FloatBallStyledVisual(
                        sizeDp = sizeDp,
                        ballColor = ballColor,
                        settings = settings,
                    )
                }
            } else {
                key(styleGeneration) {
                    FloatBallStyledVisual(
                        sizeDp = sizeDp,
                        ballColor = ballColor,
                        settings = settings,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatBallEdgeLineVisual(
    side: FloatBallSide,
    lineColor: Color,
) {
    val density = LocalDensity.current
    val lineWidth = with(density) { 4.dp }
    val outerAlignment = when (side) {
        FloatBallSide.LEFT -> Alignment.CenterStart
        FloatBallSide.RIGHT -> Alignment.CenterEnd
    }
    val roundedEdge = when (side) {
        FloatBallSide.LEFT -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        FloatBallSide.RIGHT -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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
private fun FloatBallStripZonePreviewLayer(
    settings: AppSettings,
    side: FloatBallSide,
    lineColor: Color,
    showEdgeLine: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val metrics = context.resources.displayMetrics
    val ballSizePx = FloatBallLayout.ballSizePx(settings, metrics.density)
    val previewWidthPx = if (showEdgeLine) {
        FloatBallLayout.lineTriggerWidthPx(settings, metrics.widthPixels, metrics.density)
    } else {
        ballSizePx
    }
    val previewWidth = with(density) { previewWidthPx.toDp() }
    val outerAlignment = when (side) {
        FloatBallSide.LEFT -> Alignment.CenterStart
        FloatBallSide.RIGHT -> Alignment.CenterEnd
    }
    val roundedEdge = when (side) {
        FloatBallSide.LEFT -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        FloatBallSide.RIGHT -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
    }
    val lineWidth = with(density) { 4.dp }
    val previewColor = lineColor.copy(alpha = (lineColor.alpha * 0.28f).coerceIn(0.08f, 0.45f))
    val lineAlignment = when (side) {
        FloatBallSide.LEFT -> Alignment.CenterStart
        FloatBallSide.RIGHT -> Alignment.CenterEnd
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = outerAlignment,
    ) {
        Box(
            modifier = Modifier
                .width(previewWidth)
                .fillMaxHeight()
                .clip(roundedEdge)
                .background(previewColor),
            contentAlignment = lineAlignment,
        ) {
            if (showEdgeLine) {
                Box(
                    modifier = Modifier
                        .width(lineWidth)
                        .fillMaxHeight()
                        .clip(roundedEdge)
                        .background(lineColor),
                )
            }
        }
    }
}

@Composable
private fun FloatBallStyledVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    ballColor: Color,
    settings: AppSettings,
) {
    when (settings.floatBallStyleType) {
        FloatBallStyleType.DEFAULT -> FloatBallVisual(sizeDp = sizeDp, ballColor = ballColor)
        FloatBallStyleType.PRESET_1 -> FloatBallPresetVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            outer = Color(0xFF42A5F5),
            inner = Color(0xFF1565C0),
        )
        FloatBallStyleType.PRESET_2 -> FloatBallPresetVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            outer = Color(0xFF66BB6A),
            inner = Color(0xFF2E7D32),
            square = true,
        )
        FloatBallStyleType.PRESET_3 -> FloatBallPresetVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            outer = Color(0xFFFFA726),
            inner = Color(0xFFE65100),
            ring = true,
        )
        FloatBallStyleType.PRESET_4 -> FloatBallPresetVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            outer = Color(0xFFAB47BC),
            inner = Color(0xFF6A1B9A),
            ring = true,
            square = true,
        )
        FloatBallStyleType.CUSTOM_IMAGE -> FloatBallUriVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            uri = settings.floatBallCustomImageUri,
        )
        FloatBallStyleType.SLIDESHOW -> FloatBallSlideshowVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            uris = settings.floatBallSlideshowUris,
        )
        FloatBallStyleType.GIF -> FloatBallGifVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            ballColor = ballColor,
            uri = settings.floatBallGifUri,
        )
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
private fun FloatBallPresetVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    outer: Color,
    inner: Color,
    square: Boolean = false,
    ring: Boolean = false,
) {
    val shape = if (square) RoundedCornerShape(12.dp) else CircleShape
    val alpha = opacity.coerceIn(0.3f, 1f)
    Box(
        modifier = Modifier
            .size(sizeDp)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Brush.radialGradient(listOf(inner.copy(alpha = alpha), outer.copy(alpha = alpha)))),
        contentAlignment = Alignment.Center,
    ) {
        if (ring) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.85f),
                    radius = size.minDimension * 0.32f,
                    style = Stroke(width = size.minDimension * 0.06f),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size((sizeDp.value * 0.35f).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha * 0.9f)),
            )
        }
    }
}

@Composable
private fun FloatBallUriVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    uri: String,
) {
    val context = LocalContext.current
    val bitmap = remember(uri) { FloatBallImageLoader.loadBitmap(context, uri) }
    val shape = CircleShape
    val alpha = opacity.coerceIn(0.3f, 1f)
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.shape = shape
                        clip = true
                    },
                contentScale = ContentScale.Crop,
                alpha = alpha,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = alpha * 0.5f)),
            )
        }
    }
}

@Composable
private fun FloatBallSlideshowVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    uris: List<String>,
) {
    if (uris.isEmpty()) {
        FloatBallUriVisual(sizeDp = sizeDp, opacity = opacity, uri = "")
        return
    }
    var index by remember(uris) { mutableIntStateOf(0) }
    LaunchedEffect(uris) {
        while (true) {
            delay(3000L)
            index = (index + 1) % uris.size
        }
    }
    FloatBallUriVisual(sizeDp = sizeDp, opacity = opacity, uri = uris[index])
}

@Composable
private fun FloatBallGifVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    ballColor: Color,
    uri: String,
) {
    val context = LocalContext.current
    val shape = CircleShape
    val alpha = opacity.coerceIn(0.3f, 1f)
    val readable = uri.isNotBlank() && FloatBallStyleAssetStore.canRead(context, uri)
    if (uri.isBlank()) {
        FloatBallUriVisual(sizeDp = sizeDp, opacity = opacity, uri = uri)
        return
    }
    if (!readable) {
        FloatBallVisual(sizeDp = sizeDp, ballColor = ballColor.copy(alpha = alpha))
        return
    }
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(shape),
    ) {
        key(uri, sizeDp) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.shape = shape
                        clip = true
                    },
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        this.alpha = alpha
                    }
                },
                update = { imageView ->
                    imageView.alpha = alpha
                    imageView.requestLayout()
                    runCatching {
                        val readableUri = FloatBallStyleAssetStore.resolveReadableUri(context, uri) ?: return@runCatching
                        val source = ImageDecoder.createSource(context.contentResolver, readableUri)
                        val drawable = ImageDecoder.decodeDrawable(source)
                        imageView.setImageDrawable(drawable)
                        if (drawable is android.graphics.drawable.AnimatedImageDrawable) {
                            drawable.start()
                        }
                    }
                },
            )
        }
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
                val strokeColor = if (paused) Color(0xFFFFC107) else Color(0xFFE53935)
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
