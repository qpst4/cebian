package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import androidx.core.net.toUri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.compositor.OverlayCompositor
import com.slideindex.app.overlay.compositor.OverlaySceneController
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

private const val RECT_MIN_SIDE_DP = 48f
/** FV n2.g.f22819b0: regional rect must be >= 3dp on both sides or screenshot is cancelled. */
private const val REGIONAL_RECT_MIN_SIDE_DP = 3f
/** Slop-phase pick cross fades in from this alpha to 1.0 before full drag starts. */
private const val PICK_PREVIEW_ALPHA_MIN = 0.25f

/**
 * Persistent float ball: ball acts as joystick, crosshair/plus acts as screen pointer.
 * Independent from [FloatingPointerOverlayWindow] (edge-gesture virtual pointer).
 */
@SuppressLint("StaticFieldLeak")
object FloatBallOverlay {
    private const val TAG = "FloatBallOverlay"
    private const val EDGE_MARGIN_DP = 8f
    private const val PAUSE_MS = 280L
    /** FV O0: reschedule cache rebuild after finger moves this many dp. */
    private const val CACHE_REFRESH_MOVE_DP = 3f
    /** FV O0: delay before rebuilding preview bounds cache during drag. */
    private const val CACHE_REFRESH_MS = 400L
    /** FV G4: defer first preview-bounds cache build after drag starts. */
    private const val INITIAL_CACHE_DELAY_MS = 300L
    /** Defer chrome z-order sync until side-panel enter animation settles. */
    private const val CHROME_RAISE_DEFER_MS = 320L
    /** After deferred pick screenshot lands, let panel layout settle before chrome WM work. */
    private const val PICK_SCREENSHOT_CHROME_SETTLE_MS = 48L
    /** Fallback when deferred screenshot never arrives. */
    private const val PICK_SCREENSHOT_CHROME_FALLBACK_MS = 900L
    private const val FLOAT_BALL_PASSTHROUGH_FRAMES_BEFORE_INJECT = 3
    private const val FLOAT_BALL_PASSTHROUGH_RESTORE_DELAY_MS = 220L

    private var passivePickPreviewAlpha = 1f
    private var passivePickPreviewAnchor: Offset? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dragSession = FloatBallDragSession()

    private var windowManager: WindowManager? = null
    private var displayView: ComposeView? = null
    private var displayLayoutParams: WindowManager.LayoutParams? = null
    private var displayOwner: OverlayComposeOwner? = null
    private var touchHost: FloatBallTouchHostLayout? = null
    private var touchLayoutParams: WindowManager.LayoutParams? = null
    private var lineTouchHost: FloatBallStripHost? = null
    private var lineTouchLayoutParams: WindowManager.LayoutParams? = null
    private var ballIdleChromeOwner: OverlayComposeOwner? = null
    private var lineIdleChromeOwner: OverlayComposeOwner? = null
    private var ballIdleChromeView: ComposeView? = null
    private var lineIdleChromeView: ComposeView? = null
    private var ballComposeView: ComposeView? = null
    private var ballDragVisualView: FloatBallDragVisualView? = null
    private var cursorPreviewView: FloatBallCursorPreviewView? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var positionYPreviewRestore: Float? = null
    private var appearancePreviewRestore: FloatBallAppearancePreviewSnapshot? = null

    private data class FloatBallAppearancePreviewSnapshot(
        val sizeDp: Float,
        val opacity: Float,
        val visibleFraction: Float,
        val lineHeightFraction: Float,
        val lineWidthFraction: Float,
        val lineOpacity: Float,
    )

    private var sceneState: FloatBallSceneState? = null

    private val settingsState: MutableState<AppSettings>?
        get() = sceneState?.settingsState
    private val cursorVisibleState: MutableState<Boolean>?
        get() = sceneState?.cursorVisible
    private val cursorPausedState: MutableState<Boolean>?
        get() = sceneState?.cursorPaused
    private val cursorAnchorState: MutableState<Offset>?
        get() = sceneState?.cursorAnchor
    private val selectionStartState: MutableState<Offset?>?
        get() = sceneState?.selectionStart
    private val selectionPreviewBoundsState: MutableState<Rect?>?
        get() = sceneState?.selectionPreviewBounds
    private val stripZonePreviewState: MutableState<Boolean>?
        get() = sceneState?.stripZonePreview
    private val styleVisualGenerationState: androidx.compose.runtime.MutableIntState?
        get() = sceneState?.styleVisualGeneration
    private val ballDraggingState: MutableState<Boolean>?
        get() = sceneState?.ballDragging

    private var onPositionPersisted: ((xFraction: Float, yFraction: Float) -> Unit)? = null
    private var onActiveSidePersisted: ((FloatBallSide) -> Unit)? = null
    private var pauseRunnable: Runnable? = null
    private var passiveLineRestoreRunnable: Runnable? = null
    private var deferredGifResumeRunnable: Runnable? = null
    private var deferredDragStartGeneration = 0
    private var lastPauseScheduleX = Float.NaN
    private var lastPauseScheduleY = Float.NaN
    private var cacheRefreshRunnable: Runnable? = null
    private var initialCacheRunnable: Runnable? = null
    private var lastCacheRefreshX = Float.NaN
    private var lastCacheRefreshY = Float.NaN
    private var captureSuppressed = false
    private var chromeDetachedForCapture = false
    private var isDragging = false
    private var chromeZOrderFront = true
    /** Panel opened during drag; defer WM remove/add until [restoreAfterDragEnd]. */
    private var chromeRaiseDeferredForDrag = false
    /** Non-null: only re-add triggers on these sides; null = all sides (center/fullscreen panels). */
    private var edgeChromeRaiseSides: Set<PanelSide>? = null
    private var pendingChromeRaiseRunnable: Runnable? = null
    private var pendingPickScreenshotChromeFallback: Runnable? = null

    /**
     * 触摸窗状态：空闲 WM 小块（球区/线条区）；手势在 [ACTION_DOWN] 命中后同窗扩全屏，UP 后缩回。
     * 拖拽中禁止 bringOverlayToFront / 换窗，避免丢 MOVE/UP。
     */

    private fun setDragging(dragging: Boolean) {
        val wasDragging = isDragging
        isDragging = dragging
        when {
            dragging && !wasDragging -> {
                cancelPassiveLineRestore()
                cancelDeferredGifResume()
                cancelDeferredDragStart()
                clearSplitIdleChrome()
                displayView?.visibility = View.VISIBLE
                sceneState?.ballDragging?.value = true
            }
            !dragging && wasDragging -> {
                cancelDeferredDragStart()
                deactivateDragBallVisual()
                scheduleDeferredGifResume()
            }
            else -> {
                sceneState?.ballDragging?.value = dragging
            }
        }
        if (!dragging) {
            sceneState?.ballCenterPx?.value = null
        }
    }

    private fun cancelDeferredGifResume() {
        deferredGifResumeRunnable?.let { mainHandler.removeCallbacks(it) }
        deferredGifResumeRunnable = null
    }

    /** Resume GIF one frame after Compose restore — spreads release CPU spike. */
    private fun scheduleDeferredGifResume() {
        cancelDeferredGifResume()
        val host = displayView ?: run {
            sceneState?.ballDragging?.value = false
            return
        }
        val runnable = Runnable {
            deferredGifResumeRunnable = null
            if (!isDragging) {
                sceneState?.ballDragging?.value = false
            }
        }
        deferredGifResumeRunnable = runnable
        host.postOnAnimation(runnable)
    }

    private fun cancelDeferredDragStart() {
        deferredDragStartGeneration++
    }

    /** Spread drag-start CPU: ball shell and cross on the next animation frame. */
    private fun scheduleDeferredDragStart(deferBallWindowMutation: Boolean) {
        cancelDeferredDragStart()
        val host = displayView ?: return
        val generation = deferredDragStartGeneration
        host.postOnAnimation {
            if (generation != deferredDragStartGeneration || !isDragging) return@postOnAnimation
            ballDraggingState?.value = true
            activateDragBallVisual()
            if (!deferBallWindowMutation || !dragOriginatedFromLine) {
                flushDragChromeLayout(syncAnchorState = true)
            }
            setCursorLayersVisible(true)
            settingsState?.value?.let { updateChromeVisibility(it) }
        }
    }

    private fun activateDragBallVisual() {
        val dragVisual = ballDragVisualView ?: return
        val settings = settingsState?.value ?: return
        val snapshot = FloatBallDragVisualRenderer.captureFromComposeTree(ballComposeView)
        dragVisual.show(settings, snapshot, effectiveActiveSide(settings))
        sceneState?.ballComposeVisible?.value = false
    }

    private fun deactivateDragBallVisual() {
        ballDragVisualView?.release()
        sceneState?.ballComposeVisible?.value = true
    }

    private var dragOriginatedFromLine = false
    private var lineDragEndedWithGesture = false
    private var dragActiveSideOverrideState: MutableState<FloatBallSide?>? = null
    private var dragActiveSideOverride: FloatBallSide?
        get() = dragActiveSideOverrideState?.value
        set(value) {
            dragActiveSideOverrideState?.value = value
        }
    private var passthroughRestorePending = false
    private var committedActiveSideUntilPersist: FloatBallSide? = null
    private var activeSideAtDragStart: FloatBallSide? = null
    private var finishDragRequested = false
    private var dragChromeLayoutFrameScheduled = false
    private var cursorCommitFrameScheduled = false
    private var pendingPickAnchor: Offset? = null
    private var pendingCursorFrameAnchor: Offset? = null
    private var currentDragPickAnchor = Offset.Zero
    private var cursorPreviewActive = false
    private var dragScreenBounds: OverlayScreenBounds? = null
    private var boundsLookupGeneration = 0
    /** Latched after yellow pause + small finger move; stays until drag ends (FV regional mode). */
    private var regionalPickActive = false
    private val gestureHintWindow = FloatBallGestureHintWindow()
    private var currentGestureHintType: FloatBallGestureType? = null

    val isShowing: Boolean get() = displayView != null

    /** Display + touch + hint WM layers must stay above panel windows for z-order. */
    fun bringChromeAbovePanels() {
        scheduleChromeAbovePanels(delayMs = 0L)
    }

    /** Called when another overlay window is added above float-ball chrome. */
    fun notifyPanelAttachedAboveChrome(edgeSide: PanelSide? = null) {
        chromeZOrderFront = false
        edgeChromeRaiseSides = when (edgeSide) {
            null -> null
            else -> (edgeChromeRaiseSides ?: emptySet()) + edgeSide
        }
        if (isDragging) {
            chromeRaiseDeferredForDrag = true
        }
        SlideIndexAccessibilityService.notifyEdgeChromeBelowPanel()
    }

    /**
     * Edge gesture ended with no content panel: presentation is gone or was tracking-only.
     * Clears deferred z-order flags without WM remove/add (avoids trigger/ball flash on tap).
     */
    fun restoreChromeZOrderIfIdle() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreChromeZOrderIfIdle() }
            return
        }
        cancelPendingChromeRaise()
        cancelPickPanelChromeRaiseDeferred()
        chromeRaiseDeferredForDrag = false
        edgeChromeRaiseSides = null
        chromeZOrderFront = true
    }

    /**
     * Defer chrome raise until deferred pick screenshot is applied (or [onPickPanelDeferredScreenshotSkipped]).
     * Avoids stacking WM remove/add with [FloatBallPickResultPanel.updatePickScreenshot].
     */
    fun scheduleChromeAbovePanelsAfterDeferredPickScreenshot() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { scheduleChromeAbovePanelsAfterDeferredPickScreenshot() }
            return
        }
        cancelPickPanelChromeRaiseDeferred()
        val fallback = Runnable {
            pendingPickScreenshotChromeFallback = null
            scheduleChromeAbovePanels(delayMs = 0L)
        }
        pendingPickScreenshotChromeFallback = fallback
        mainHandler.postDelayed(fallback, PICK_SCREENSHOT_CHROME_FALLBACK_MS)
    }

    fun onPickPanelScreenshotApplied() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onPickPanelScreenshotApplied() }
            return
        }
        cancelPickPanelChromeRaiseDeferred()
        OverlayCompositor.bringAboveContentPanels()
        scheduleChromeAbovePanels(delayMs = PICK_SCREENSHOT_CHROME_SETTLE_MS)
    }

    fun onPickPanelDeferredScreenshotSkipped() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onPickPanelDeferredScreenshotSkipped() }
            return
        }
        cancelPickPanelChromeRaiseDeferred()
        scheduleChromeAbovePanels(delayMs = PICK_SCREENSHOT_CHROME_SETTLE_MS)
    }

    fun cancelPickPanelChromeRaiseDeferred() {
        pendingPickScreenshotChromeFallback?.let { mainHandler.removeCallbacks(it) }
        pendingPickScreenshotChromeFallback = null
    }

    /**
     * Forcefully re-adds FloatBall overlays to WindowManager top z-order to guarantee visibility above panels.
     */
    fun bringChromeAbovePanelsForce() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringChromeAbovePanelsForce() }
            return
        }
        chromeZOrderFront = false
        bringChromeAbovePanelsNow(forceExplicitReAdd = true)
    }

    /**
     * Coalesces chrome z-order work and defers it past panel enter animations when possible.
     * Uses [WindowManager.updateViewLayout] when chrome is already on top; otherwise re-adds views.
     */
    fun scheduleChromeAbovePanels(delayMs: Long = CHROME_RAISE_DEFER_MS) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { scheduleChromeAbovePanels(delayMs) }
            return
        }
        if (captureSuppressed) return
        if (OverlaySceneController.isEdgeGestureActive()) return
        if (isDragging) {
            if (!chromeZOrderFront) {
                chromeRaiseDeferredForDrag = true
            }
            return
        }
        pendingChromeRaiseRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingChromeRaiseRunnable = null
            bringChromeAbovePanelsNow(forceExplicitReAdd = false)
        }
        pendingChromeRaiseRunnable = runnable
        if (delayMs <= 0L) {
            mainHandler.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    private fun bringChromeAbovePanelsNow(forceExplicitReAdd: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringChromeAbovePanelsNow(forceExplicitReAdd) }
            return
        }
        if (OverlaySceneController.isEdgeGestureActive()) return
        if (passthroughRestorePending) return
        if (captureSuppressed) return
        if (isDragging) {
            if (!chromeZOrderFront) {
                chromeRaiseDeferredForDrag = true
            }
            return
        }
        val forceReAdd = forceExplicitReAdd || !chromeZOrderFront
        val edgeSides = edgeChromeRaiseSides
        edgeChromeRaiseSides = null
        settingsState?.value?.let { settings ->
            recoverIdleTouchCaptureLayouts(settings)
            val touchEnabled = !passthroughRestorePending && !captureSuppressed
            val splitIdle = shouldUseSplitIdleChrome(settings)
            if (touchEnabled) {
                if (!splitIdle) {
                    val display = displayView
                    val displayLp = displayLayoutParams
                    if (display != null && displayLp != null) {
                        bringOverlayToFront(display, displayLp, forceReAdd = forceReAdd)
                    }
                }
                val lineTouch = lineTouchHost
                val lineTouchLp = lineTouchLayoutParams
                if (lineTouch != null && lineTouchLp != null && lineTouch.isVisible) {
                    bringOverlayToFront(lineTouch, lineTouchLp, forceReAdd = forceReAdd)
                }
                val touch = touchHost
                val touchLp = touchLayoutParams
                if (touch != null && touchLp != null) {
                    bringOverlayToFront(touch, touchLp, forceReAdd = forceReAdd)
                }
            }
        }
        gestureHintWindow.bringToFront()
        SlideIndexAccessibilityService.bringEdgeChromeAbovePanels(forceReAdd = forceReAdd, sides = edgeSides)
        chromeZOrderFront = true
    }

    /** Run deferred z-order raise one frame after drag-end layout restore. */
    private fun flushDeferredChromeRaiseIfNeeded() {
        val needsRaise = chromeRaiseDeferredForDrag || !chromeZOrderFront
        chromeRaiseDeferredForDrag = false
        if (!needsRaise) return
        val host = displayView
        if (host != null) {
            host.postOnAnimation { scheduleChromeAbovePanels(delayMs = 0L) }
        } else {
            scheduleChromeAbovePanels(delayMs = 0L)
        }
    }

    private fun cancelPendingChromeRaise() {
        pendingChromeRaiseRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingChromeRaiseRunnable = null
    }

    fun setStripZonePreviewActive(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setStripZonePreviewActive(active) }
            return
        }
        stripZonePreviewState?.value = active
    }

    fun previewPositionYFraction(fraction: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { previewPositionYFraction(fraction) }
            return
        }
        val state = settingsState ?: return
        if (positionYPreviewRestore == null) {
            positionYPreviewRestore = state.value.floatBallPositionYFraction
        }
        val coerced = FloatBallLayout.coercePositionYFraction(fraction)
        val updated = state.value.copy(floatBallPositionYFraction = coerced)
        state.value = updated
        applyAllLayouts(updated)
    }

    fun endPositionYPreview(restoreIfNeeded: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { endPositionYPreview(restoreIfNeeded) }
            return
        }
        val baseline = positionYPreviewRestore
        positionYPreviewRestore = null
        if (!restoreIfNeeded || baseline == null) return
        val state = settingsState ?: return
        val restored = state.value.copy(floatBallPositionYFraction = baseline)
        state.value = restored
        applyAllLayouts(restored)
    }

    fun clearPositionYPreviewRestore() {
        positionYPreviewRestore = null
    }

    fun previewAppearance(
        sizeDp: Float? = null,
        opacity: Float? = null,
        visibleFraction: Float? = null,
        lineHeightFraction: Float? = null,
        lineWidthFraction: Float? = null,
        lineOpacity: Float? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                previewAppearance(
                    sizeDp,
                    opacity,
                    visibleFraction,
                    lineHeightFraction,
                    lineWidthFraction,
                    lineOpacity,
                )
            }
            return
        }
        val state = settingsState ?: return
        if (appearancePreviewRestore == null) {
            val current = state.value
            appearancePreviewRestore = FloatBallAppearancePreviewSnapshot(
                sizeDp = current.floatBallSizeDp,
                opacity = current.floatBallOpacity,
                visibleFraction = current.floatBallVisibleFraction,
                lineHeightFraction = current.floatBallLineHeightFraction,
                lineWidthFraction = current.floatBallLineWidthFraction,
                lineOpacity = current.floatBallLineOpacity,
            )
        }
        val current = state.value
        val updated = current.copy(
            floatBallSizeDp = sizeDp?.coerceIn(36f, 72f) ?: current.floatBallSizeDp,
            floatBallOpacity = opacity?.coerceIn(0f, 1f) ?: current.floatBallOpacity,
            floatBallVisibleFraction = visibleFraction?.let(FloatBallLayout::coerceVisibleFraction)
                ?: current.floatBallVisibleFraction,
            floatBallLineHeightFraction = lineHeightFraction?.coerceIn(0.04f, 0.4f)
                ?: current.floatBallLineHeightFraction,
            floatBallLineWidthFraction = lineWidthFraction?.coerceIn(0.01f, 0.50f)
                ?: current.floatBallLineWidthFraction,
            floatBallLineOpacity = lineOpacity?.coerceIn(0f, 1f) ?: current.floatBallLineOpacity,
        )
        state.value = updated
        invalidateChrome()
        val layoutChanged = sizeDp != null || visibleFraction != null ||
            lineHeightFraction != null || lineWidthFraction != null
        if (layoutChanged) {
            applyAllLayouts(updated)
            syncTouchWindowLayout(updated)
        }
    }

    fun endAppearancePreview(restoreIfNeeded: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { endAppearancePreview(restoreIfNeeded) }
            return
        }
        val baseline = appearancePreviewRestore
        appearancePreviewRestore = null
        if (!restoreIfNeeded || baseline == null) return
        val state = settingsState ?: return
        val restored = state.value.copy(
            floatBallSizeDp = baseline.sizeDp,
            floatBallOpacity = baseline.opacity,
            floatBallVisibleFraction = baseline.visibleFraction,
            floatBallLineHeightFraction = baseline.lineHeightFraction,
            floatBallLineWidthFraction = baseline.lineWidthFraction,
            floatBallLineOpacity = baseline.lineOpacity,
        )
        state.value = restored
        invalidateChrome()
        bumpScreenLayoutGeneration()
        applyAllLayouts(restored)
        syncTouchWindowLayout(restored)
    }

    private fun invalidateChrome() {
        ballComposeView?.invalidate()
        displayView?.invalidate()
    }

    fun clearAppearancePreviewRestore() {
        appearancePreviewRestore = null
    }

    fun refreshStyleVisual() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshStyleVisual() }
            return
        }
        styleVisualGenerationState?.let { state ->
            state.intValue = state.intValue + 1
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
        if (RegionalPickOverlay.isActive) {
            settingsState?.value = settings
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "accessibility service not connected")
                return
            }

        this.onPositionPersisted = onPositionPersisted
        this.onActiveSidePersisted = onActiveSidePersisted
        if (isShowing && !areChromeWindowsAttached()) {
            // 熄屏/锁屏后系统可能摘掉 TYPE_ACCESSIBILITY_OVERLAY，本地引用仍在。
            // 先清理再重建，避免 isShowing=true 却永远不 ensureWindows。
            val persistPosition = onPositionPersisted
            val persistSide = onActiveSidePersisted
            dismiss()
            this.onPositionPersisted = persistPosition
            this.onActiveSidePersisted = persistSide
            ensureWindows(hostContext, settings)
        } else if (!isShowing) {
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
            val merged = when {
                appearancePreviewRestore != null && current != null -> incoming.copy(
                    floatBallSizeDp = current.floatBallSizeDp,
                    floatBallOpacity = current.floatBallOpacity,
                    floatBallVisibleFraction = current.floatBallVisibleFraction,
                    floatBallLineHeightFraction = current.floatBallLineHeightFraction,
                    floatBallLineWidthFraction = current.floatBallLineWidthFraction,
                    floatBallLineOpacity = current.floatBallLineOpacity,
                )
                isDragging &&
                    current != null &&
                    incoming.floatBallActiveSide != current.floatBallActiveSide -> incoming.copy(
                    floatBallActiveSide = current.floatBallActiveSide,
                )
                else -> incoming
            }
            settingsState?.value = merged
            if (floatBallStyleSignature(current) != floatBallStyleSignature(merged)) {
                refreshStyleVisual()
            }
            touchHost?.updateSettings(merged)
            lineTouchHost?.updateSettings(merged)
            if (!isDragging) {
                recoverIdleTouchCaptureLayouts(merged)
                restorePassiveOverlayLayout(merged)
            }
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        cancelPassiveLineRestore()
        cancelDeferredGifResume()
        cancelDeferredDragStart()
        hideCursor(restorePassive = false)
        deactivateDragBallVisual()
        cancelPauseTimer()
        cancelPendingChromeRaise()
        val wm = windowManager
        displayView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        touchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        lineTouchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        destroySplitIdleChrome()
        gestureHintWindow.detach()
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        OverlayCompose.disposeComposeView(displayView)
        displayOwner?.destroy()
        FloatBallPickResultPanel.destroy()
        FloatBallStashPanel.destroy()
        displayOwner = null
        displayView = null
        displayLayoutParams = null
        touchHost = null
        touchLayoutParams = null
        lineTouchHost = null
        lineTouchLayoutParams = null
        ballComposeView = null
        ballDragVisualView = null
        cursorPreviewView = null
        windowManager = null
        sceneState = null
        dragActiveSideOverrideState = null
        onPositionPersisted = null
        onActiveSidePersisted = null
        screenOffReceiver = null
        appContext = null
        setDragging(false)
        dragOriginatedFromLine = false
        lineDragEndedWithGesture = false
        dragActiveSideOverride = null
        committedActiveSideUntilPersist = null
        activeSideAtDragStart = null
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        dragSession.reset()
        currentGestureHintType = null
        chromeZOrderFront = true
        cancelPendingChromeRaise()
        captureSuppressed = false
        chromeDetachedForCapture = false
    }

    fun relayout() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { relayout() }
            return
        }
        if (captureSuppressed) return
        settingsState?.value ?: return
        val view = displayView
        if (view != null && view.isAttachedToWindow) {
            view.post { relayoutNow() }
        } else {
            relayoutNow()
        }
    }

    private fun relayoutNow() {
        if (captureSuppressed) return
        val currentSettings = settingsState?.value ?: return
        if (isDragging) {
            val host = displayView ?: return
            val bounds = FloatBallScreenMetrics.bounds(host.context, windowManager)
            dragSession.refreshPointerTravel(
                settings = currentSettings,
                screenWidth = bounds.width,
                screenHeight = bounds.height,
            )
            updatePickAndBallFromFinger(moveBallWindow = true)
        } else {
            recoverIdleTouchCaptureLayouts(currentSettings)
            applyAllLayouts(currentSettings)
        }
        bumpScreenLayoutGeneration()
    }

    private fun bumpScreenLayoutGeneration() {
        sceneState?.screenLayoutGeneration?.let { state ->
            state.intValue = state.intValue + 1
        }
    }

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        if (captureSuppressed) return
        captureSuppressed = true
        sceneState?.chromeVisible?.value = false
        sceneState?.ballVisible?.value = false
        sceneState?.lineVisible?.value = false
        sceneState?.ballComposeVisible?.value = false
        clearSplitIdleChrome()
        hideGestureHintWindow()
        hideCursor()
        detachChromeWindowsForCapture()
    }

    fun suppressChromeForRegionalPick() = suppressForScreenshotCapture()

    /** Hide float-ball chrome during edge regional pick without WM layout churn. */
    fun hideChromeForEdgeRegionalPick() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideChromeForEdgeRegionalPick() }
            return
        }
        if (sceneState == null) return
        captureSuppressed = true
        sceneState?.chromeVisible?.value = false
        sceneState?.ballVisible?.value = false
        sceneState?.lineVisible?.value = false
        sceneState?.ballComposeVisible?.value = false
        dragActiveSideOverride = null
        clearSplitIdleChrome()
        hideGestureHintWindow()
        hideCursor()
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        if (!captureSuppressed) return
        captureSuppressed = false
        reattachChromeWindowsAfterCapture()
        val settings = settingsState?.value
        if (settings != null) {
            updateChromeVisibility(settings)
            syncSplitIdleChrome(settings)
        } else {
            sceneState?.chromeVisible?.value = true
        }
    }

    fun restoreChromeAfterRegionalPick() = restoreAfterScreenshotCapture()

    private fun detachChromeWindowsForCapture() {
        if (chromeDetachedForCapture) return
        val wm = windowManager ?: return
        displayView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(view) }
            }
        }
        touchHost?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(view) }
            }
        }
        lineTouchHost?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(view) }
            }
        }
        chromeDetachedForCapture = true
    }

    private fun reattachChromeWindowsAfterCapture() {
        if (!chromeDetachedForCapture) return
        val wm = windowManager ?: return
        val display = displayView
        val touch = touchHost
        val lineTouch = lineTouchHost
        val displayLp = displayLayoutParams
        val touchLp = touchLayoutParams
        val lineTouchLp = lineTouchLayoutParams
        if (display != null && displayLp != null && !display.isAttachedToWindow) {
            runCatching { wm.addView(display, displayLp) }
        }
        if (touch != null && touchLp != null && !touch.isAttachedToWindow) {
            runCatching { wm.addView(touch, touchLp) }
        }
        if (lineTouch != null && lineTouchLp != null && !lineTouch.isAttachedToWindow) {
            runCatching { wm.addView(lineTouch, lineTouchLp) }
        }
        chromeDetachedForCapture = false
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
        FloatBallStashPanel.warmUpBelowChrome(hostContext)
        FloatBallPickResultPanel.warmUp(hostContext)
        com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow.warmUp(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val state = FloatBallSceneState(settings)
        sceneState = state
        dragActiveSideOverrideState = mutableStateOf(null)

        val cursorPreview = FloatBallCursorPreviewView(overlayContext).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        val ballDragVisual = FloatBallDragVisualView(overlayContext)

        val dragCallbacks = object {
            fun onGestureHint(gestureType: FloatBallGestureType?) {
                currentGestureHintType = gestureType
                updateGestureHintWindow()
            }

            fun onPreviewStart(screenX: Float, screenY: Float) {
                showCursorPickPreview(screenX, screenY)
            }

            fun onPreviewProgress(progress: Float) {
                updateCursorPickPreviewAlpha(progress)
            }

            fun onPreviewCancel() {
                cancelCursorPickPreview()
            }

            fun onStart(screenX: Float, screenY: Float) {
                activeSideAtDragStart = null
                dragOriginatedFromLine = false
                lineDragEndedWithGesture = false
                dragActiveSideOverride = null
                expandBallTouchCapture()
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
            }
        }

        val displayDialogOwner = OverlayComposeOwner()
        val displayCompose = OverlayCompose.createComposeView(overlayContext, displayDialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                FloatBallChrome(
                    sceneState = state,
                    dragActiveSideOverrideState = dragActiveSideOverrideState!!,
                    cursorPreviewView = cursorPreview,
                    ballDragVisualView = ballDragVisual,
                    onBallComposeViewReady = { composeView ->
                        ballComposeView = composeView
                    },
                )
            }
        }

        val touchLayout = FloatBallTouchHostLayout(
            context = overlayContext,
            sceneState = state,
            settingsProvider = { state.settingsState.value },
            activeSideProvider = { effectiveActiveSide(state.settingsState.value) },
            screenSizeProvider = { FloatBallScreenMetrics.sizePx(overlayContext, wm) },
        ).apply {
            updateSettings(settings)
            bindBallCallbacks(
                onDragStart = { screenX, screenY -> dragCallbacks.onStart(screenX, screenY) },
                onDrag = { dx, dy -> dragCallbacks.onDrag(dx, dy) },
                onDragEnd = { dragCallbacks.onEnd() },
                onDragCancel = { dragCallbacks.onCancel() },
                onGesture = { gestureType, rawX, rawY ->
                    hideGestureHintWindow()
                    performFloatBallGesture(state.settingsState.value, gestureType, rawX, rawY)
                },
                onGestureHint = dragCallbacks::onGestureHint,
                onPickPreviewStart = dragCallbacks::onPreviewStart,
                onPickPreviewProgress = dragCallbacks::onPreviewProgress,
                onPickPreviewCancel = dragCallbacks::onPreviewCancel,
            )
        }

        val lineTouchLayout = FloatBallStripHost(
            context = overlayContext,
            sceneState = state,
            settingsProvider = { state.settingsState.value },
            activeSideProvider = { effectiveActiveSide(state.settingsState.value) },
            screenSizeProvider = { FloatBallScreenMetrics.sizePx(overlayContext, wm) },
        ).apply {
            updateSettings(settings)
            bindDragCallbacks(
                onDragStart = { screenX, screenY ->
                    prepareLineDrag(screenX, screenY)
                },
                onDrag = { dx, dy ->
                    onFingerDrag(dx, dy)
                    onDragMoved()
                },
                onDragEnd = {
                    // 与球侧一致：换边在 finishDrag 里于取词提交之后执行。
                    completeDragGesture()
                },
                onDragCancel = {
                    when {
                        lineDragEndedWithGesture -> {
                            lineDragEndedWithGesture = false
                            commitLineDragSideSwap()
                            cancelDragWithoutPick()
                        }
                        hasPickPauseIntentForCommit() -> completeDragGesture()
                        else -> {
                            revertLineDragSideSwapIfNeeded()
                            cancelDragWithoutPick()
                        }
                    }
                },
                onGesture = { gestureType, rawX, rawY ->
                    lineDragEndedWithGesture = true
                    hideGestureHintWindow()
                    performFloatBallGesture(state.settingsState.value, gestureType, rawX, rawY, fromLineStrip = true)
                },
                onGestureHint = dragCallbacks::onGestureHint,
                onPickPreviewStart = dragCallbacks::onPreviewStart,
                onPickPreviewProgress = dragCallbacks::onPreviewProgress,
                onPickPreviewCancel = dragCallbacks::onPreviewCancel,
            )
        }

        val displayLp = buildDisplayLayoutParams(hostContext)
        val touchLp = buildTouchLayoutParams(hostContext)
        val lineTouchLp = buildTouchLayoutParams(hostContext)

        val displayAdded = runCatching { wm.addView(displayCompose, displayLp) }
            .onFailure { Log.e(TAG, "failed to add display overlay", it) }
            .isSuccess
        if (!displayAdded) {
            displayDialogOwner.destroy()
            return
        }

        val touchAdded = runCatching { wm.addView(touchLayout, touchLp) }
            .onFailure { Log.e(TAG, "failed to add ball touch overlay", it) }
            .isSuccess
        if (!touchAdded) {
            runCatching { wm.removeView(displayCompose) }
            displayDialogOwner.destroy()
            return
        }

        val lineTouchAdded = runCatching { wm.addView(lineTouchLayout, lineTouchLp) }
            .onFailure { Log.e(TAG, "failed to add line touch overlay", it) }
            .isSuccess
        if (!lineTouchAdded) {
            runCatching { wm.removeView(touchLayout) }
            runCatching { wm.removeView(displayCompose) }
            displayDialogOwner.destroy()
            return
        }

        windowManager = wm
        displayView = displayCompose
        displayLayoutParams = displayLp
        displayOwner = displayDialogOwner
        touchHost = touchLayout
        touchLayoutParams = touchLp
        lineTouchHost = lineTouchLayout
        lineTouchLayoutParams = lineTouchLp
        ballDragVisualView = ballDragVisual
        cursorPreviewView = cursorPreview
        appContext = hostContext
        registerScreenOffReceiver(hostContext)
        gestureHintWindow.attach(hostContext, wm)

        applyAllLayouts(settings)
        scheduleChromeAbovePanels(delayMs = 0L)
        displayCompose.post { scheduleChromeAbovePanels(delayMs = 0L) }
    }

    private fun releaseAllTouchCaptures() {
        touchHost?.forceEndGestureCapture()
        lineTouchHost?.cancelGesture()
        collapseBallTouchHostFromFullscreen()
        collapseLineTouchHostFromFullscreen()
    }

    /** 空闲态：从全屏捕获缩回触钮区，并同步 WM 几何（z-order 重挂前必须调用）。 */
    private fun recoverIdleTouchCaptureLayouts(settings: AppSettings) {
        if (isDragging) return
        collapseBallTouchHostFromFullscreen()
        collapseLineTouchHostFromFullscreen()
        syncTouchWindowLayout(settings)
        ensureDisplayPassthrough()
        syncSplitIdleChrome(settings)
    }

    private fun shouldUseSplitIdleChrome(settings: AppSettings): Boolean {
        if (!FloatBallLayout.shouldShowLine(settings)) return false
        if (isDragging || captureSuppressed || passthroughRestorePending) return false
        if (sceneState?.stripZonePreview?.value == true) return false
        if (cursorVisibleState?.value == true) return false
        if (cursorPreviewActive) return false
        if (passiveLineRestoreRunnable != null) return false
        return true
    }

    private fun syncSplitIdleChrome(settings: AppSettings) {
        if (!shouldUseSplitIdleChrome(settings)) {
            clearSplitIdleChrome()
            displayView?.visibility = View.VISIBLE
            return
        }
        displayView?.visibility = View.GONE
        val overlayContext = touchHost?.context ?: displayView?.context ?: return
        val state = sceneState ?: return
        if (ballIdleChromeView == null) {
            val owner = OverlayComposeOwner()
            ballIdleChromeOwner = owner
            ballIdleChromeView = OverlayCompose.createComposeView(overlayContext, owner).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setContent {
                    FloatBallIdleBallChrome(
                        sceneState = state,
                        dragActiveSideOverrideState = dragActiveSideOverrideState!!,
                        onBallComposeViewReady = { composeView -> ballComposeView = composeView },
                    )
                }
            }
        }
        if (lineIdleChromeView == null) {
            val owner = OverlayComposeOwner()
            lineIdleChromeOwner = owner
            lineIdleChromeView = OverlayCompose.createComposeView(overlayContext, owner).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setContent {
                    FloatBallIdleLineChrome(sceneState = state)
                }
            }
        }
        touchHost?.setIdleChrome(ballIdleChromeView, ballIdleChromeOwner)
        lineTouchHost?.setIdleChrome(lineIdleChromeView, lineIdleChromeOwner)
    }

    private fun clearSplitIdleChrome() {
        touchHost?.setIdleChrome(null, null)
        lineTouchHost?.setIdleChrome(null, null)
    }

    private fun destroySplitIdleChrome() {
        clearSplitIdleChrome()
        OverlayCompose.disposeComposeView(ballIdleChromeView)
        OverlayCompose.disposeComposeView(lineIdleChromeView)
        ballIdleChromeView = null
        lineIdleChromeView = null
        ballIdleChromeOwner?.destroy()
        ballIdleChromeOwner = null
        lineIdleChromeOwner?.destroy()
        lineIdleChromeOwner = null
    }

    private fun ensureDisplayPassthrough() {
        val view = displayView ?: return
        val wm = windowManager ?: return
        val params = displayLayoutParams ?: return
        val needsNotTouchable = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
        if (!needsNotTouchable) return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (view.isAttachedToWindow) {
            runCatching { wm.updateViewLayout(view, params) }
                .onFailure { Log.w(TAG, "ensureDisplayPassthrough failed", it) }
        }
    }

    private fun syncTouchCaptureLayouts() {
        settingsState?.value?.let {
            syncBallTouchWindowLayout(it)
            syncLineTouchWindowLayout(it)
        }
    }

    private fun setLineTouchHostEnabled(enabled: Boolean) {
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        view.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (view.isAttachedToWindow) {
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    /** 球体拖出：全屏捕获手势，隐藏线条触摸窗。 */
    private fun expandBallTouchCapture() {
        setLineTouchHostEnabled(false)
        expandTouchHostToFullscreen(touchHost, touchLayoutParams)
    }

    /**
     * 线条拖出：全屏扩展必须在 [lineTouchHost] 上（手势在此窗发起），
     * 不可改由球体窗接管，否则 MOVE/UP 丢失。
     */
    private fun expandLineTouchCapture() {
        setBallTouchHostPassthrough(true)
        expandTouchHostToFullscreen(lineTouchHost, lineTouchLayoutParams)
    }

    private fun expandTouchHostToFullscreen(
        view: View?,
        params: WindowManager.LayoutParams?,
    ) {
        val wm = windowManager ?: return
        if (view == null || params == null) return
        if (params.width == WindowManager.LayoutParams.MATCH_PARENT &&
            params.height == WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        params.x = 0
        params.y = 0
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "expandTouchHostToFullscreen failed", it) }
    }

    private fun collapseLineTouchHostFromFullscreen() {
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        if (params.width != WindowManager.LayoutParams.MATCH_PARENT &&
            params.height != WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        settingsState?.value?.let { syncLineTouchWindowLayout(it) }
            ?: run {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { wm.updateViewLayout(view, params) }
            }
    }

    private fun collapseBallTouchHostFromFullscreen() {
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        if (params.width != WindowManager.LayoutParams.MATCH_PARENT &&
            params.height != WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        settingsState?.value?.let { syncBallTouchWindowLayout(it) }
            ?: run {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { wm.updateViewLayout(view, params) }
            }
    }

    private fun setBallTouchHostPassthrough(passthrough: Boolean) {
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (!captureSuppressed && !passthroughRestorePending) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "setBallTouchHostPassthrough failed", it) }
    }

    private fun setLineTouchHostPassthrough(passthrough: Boolean) {
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (!captureSuppressed && !passthroughRestorePending) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "setLineTouchHostPassthrough failed", it) }
    }

    /** 空闲态：球体触摸窗仅覆盖球区（WM 小块，避免挡屏）。 */
    private fun syncBallTouchWindowLayout(settings: AppSettings) {
        if (isDragging) return
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        val state = sceneState ?: return
        val metrics = view.resources.displayMetrics
        val (screenW, screenH) = FloatBallScreenMetrics.sizePx(view.context, wm)
        val activeSide = effectiveActiveSide(settings)
        val bounds = state.ballTouchBounds(
            settings = settings,
            metrics = metrics,
            activeSide = activeSide,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
        )
        if (captureSuppressed || passthroughRestorePending) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width().coerceAtLeast(1)
        params.height = bounds.height().coerceAtLeast(1)
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "syncBallTouchWindowLayout failed", it) }
    }

    /** 空闲态：线条触摸窗仅覆盖线条触发区。 */
    private fun syncLineTouchWindowLayout(settings: AppSettings) {
        if (isDragging) {
            if (!dragOriginatedFromLine) {
                setLineTouchHostEnabled(false)
            }
            return
        }
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        val state = sceneState ?: return
        val showLine = state.lineVisible.value &&
            FloatBallLayout.shouldShowLine(settings) &&
            !captureSuppressed &&
            !passthroughRestorePending
        if (!showLine) {
            setLineTouchHostEnabled(false)
            return
        }
        val metrics = view.resources.displayMetrics
        val (screenW, screenH) = FloatBallScreenMetrics.sizePx(view.context, wm)
        val inactiveSide = FloatBallSide.opposite(effectiveActiveSide(settings))
        val bounds = state.lineHitRect(
            settings = settings,
            metrics = metrics,
            inactiveSide = inactiveSide,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
        )
        view.visibility = View.VISIBLE
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width().coerceAtLeast(1)
        params.height = bounds.height().coerceAtLeast(1)
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "syncLineTouchWindowLayout failed", it) }
    }

    private fun syncTouchWindowLayout(settings: AppSettings) {
        syncBallTouchWindowLayout(settings)
        syncLineTouchWindowLayout(settings)
    }

    private fun buildDisplayLayoutParams(context: Context): WindowManager.LayoutParams {
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
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
    }

    private fun buildTouchLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            1,
            1,
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
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
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
        val view = displayView ?: return
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
    }

    private fun performFloatBallGesture(
        settings: AppSettings,
        gestureType: FloatBallGestureType,
        rawX: Float,
        rawY: Float,
        fromLineStrip: Boolean = false,
    ) {
        val action = settings.floatBallGestureActions[gestureType] ?: GestureAction.None
        if (action is GestureAction.None) return
        if (action is GestureAction.ClickPassthrough) {
            if (passthroughRestorePending) return
            OverlayPassthrough.run(
                hideTriggers = ::hideFloatBallOverlaysForPassthrough,
                showTriggers = ::restoreFloatBallOverlaysAfterPassthrough,
                rawX = rawX,
                rawY = rawY,
                onComplete = {},
                framesBeforeInject = FLOAT_BALL_PASSTHROUGH_FRAMES_BEFORE_INJECT,
                restoreDelayMs = FLOAT_BALL_PASSTHROUGH_RESTORE_DELAY_MS,
            )
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: displayView?.context?.applicationContext
            ?: return
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext) ?: return
        val panelSide = if (fromLineStrip && settings.floatBallPositionMode == FloatBallPositionMode.BOTH_EDGES) {
            FloatBallLayout.panelSideForLineStrip(settings)
        } else {
            FloatBallLayout.panelSideFor(settings)
        }
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
            panelSide = panelSide,
        )
    }

    private fun hideFloatBallOverlaysForPassthrough() {
        if (passthroughRestorePending) return
        passthroughRestorePending = true
        cancelPendingChromeRaise()
        cancelCursorPickPreview()
        hideGestureHintWindow()
        setBallTouchHostPassthrough(true)
        setLineTouchHostPassthrough(true)
        setFloatBallPassthroughWindowsVisible(false)
    }

    private fun restoreFloatBallOverlaysAfterPassthrough() {
        if (!passthroughRestorePending) return
        passthroughRestorePending = false
        setBallTouchHostPassthrough(false)
        setLineTouchHostPassthrough(false)
        setFloatBallPassthroughWindowsVisible(true)
        settingsState?.value?.let { updateChromeVisibility(it) }
    }

    private fun setFloatBallPassthroughWindowsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        displayView?.visibility = visibility
        touchHost?.visibility = visibility
        lineTouchHost?.visibility = visibility
        if (!visible) {
            gestureHintWindow.hide()
        }
    }

    private fun setCursorLayersVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        cursorPreviewView?.visibility = visibility
        if (visible) {
            syncCursorChromeAppearance()
        } else {
            syncCursorPreviewAppearance()
        }
    }

    private fun syncCursorChromeAppearance() {
        syncCursorPreviewAppearance()
    }

    private fun syncCursorPreviewAppearance() {
        val view = cursorPreviewView ?: return
        val settings = settingsState?.value
        val layersVisible = cursorVisibleState?.value == true
        val crossVisible = layersVisible || cursorPreviewActive
        val crossAlpha = if (cursorPreviewActive && !isDragging) {
            passivePickPreviewAlpha
        } else {
            1f
        }
        view.setChromeState(
            visible = layersVisible,
            paused = cursorPausedState?.value == true,
            selectionStart = selectionStartState?.value,
            selectionPreviewBounds = selectionPreviewBoundsState?.value,
            pickAnchor = currentPickAnchor() ?: Offset.Zero,
            regionalDragActive = regionalPickActive,
            crossVisible = crossVisible,
            crossAlpha = crossAlpha,
            crossPaused = cursorPausedState?.value == true,
            crossArmDp = settings?.floatBallPickCrossArmDp?.coerceIn(4f, 16f) ?: 7.5f,
            hintMode = resolveCursorHintMode(),
        )
    }

    /** FV op_hint_icon: yellow cross only; A until finger moves, then screenshot (latched). */
    private fun resolveCursorHintMode(): FloatBallCursorPreviewView.HintMode {
        if (cursorVisibleState?.value != true || cursorPausedState?.value != true) {
            return FloatBallCursorPreviewView.HintMode.HIDDEN
        }
        if (regionalPickActive) {
            return FloatBallCursorPreviewView.HintMode.SCREENSHOT
        }
        return FloatBallCursorPreviewView.HintMode.TEXT
    }

    private fun currentPickAnchor(): Offset? {
        if (cursorPreviewActive && !isDragging) {
            return passivePickPreviewAnchor
        }
        if (isDragging) return currentDragPickAnchor
        return cursorAnchorState?.value
    }

    private fun restorePassiveOverlayLayout(
        settings: AppSettings,
        @Suppress("UNUSED_PARAMETER") fixZOrder: Boolean = true,
        deferLineRestore: Boolean = false,
        skipBallLayout: Boolean = false,
    ) {
        setBallTouchable(true)
        if (!skipBallLayout) {
            applyBallLayout(settings)
        }
        if (deferLineRestore) {
            updateChromeVisibility(settings)
            schedulePassiveLineRestore()
        } else {
            applyLineLayout(settings)
            updateChromeVisibility(settings)
        }
    }

    private fun cancelPassiveLineRestore() {
        passiveLineRestoreRunnable?.let { mainHandler.removeCallbacks(it) }
        passiveLineRestoreRunnable = null
    }

    /** Show line strip one frame after ball Compose/GIF restore — spreads release CPU spike. */
    private fun schedulePassiveLineRestore() {
        cancelPassiveLineRestore()
        val host = displayView ?: return
        val runnable = Runnable {
            passiveLineRestoreRunnable = null
            if (captureSuppressed) return@Runnable
            val settings = settingsState?.value ?: return@Runnable
            applyLineLayout(settings)
            updateChromeVisibility(settings)
        }
        passiveLineRestoreRunnable = runnable
        host.postOnAnimation(runnable)
    }

    private fun restoreAfterDragEnd(settings: AppSettings) {
        clearCursorUi(restoreLayout = false)
        cancelPassiveLineRestore()
        applyBallLayout(settings)
        setDragging(false)
        FloatBallPickResultPanel.releaseWarmUpShell()
        setBallTouchHostPassthrough(false)
        restorePassiveOverlayLayout(
            settings = settings,
            fixZOrder = false,
            deferLineRestore = true,
            skipBallLayout = true,
        )
        releaseAllTouchCaptures()
        syncTouchCaptureLayouts()
        flushDeferredChromeRaiseIfNeeded()
    }

    private fun areChromeWindowsAttached(): Boolean {
        val display = displayView ?: return false
        val touch = touchHost ?: return false
        if (!display.isAttachedToWindow || !touch.isAttachedToWindow) return false
        val line = lineTouchHost
        return line == null || line.isAttachedToWindow
    }

    private fun bringOverlayToFront(
        view: View,
        params: WindowManager.LayoutParams,
        forceReAdd: Boolean = false,
    ) {
        val wm = windowManager ?: return
        if (!view.isAttachedToWindow) {
            if (!forceReAdd) return
            runCatching {
                wm.addView(view, params)
                view.requestLayout()
                view.invalidate()
            }.onFailure { Log.w(TAG, "bringOverlayToFront re-add failed", it) }
            return
        }
        if (forceReAdd) {
            runCatching {
                wm.removeView(view)
                wm.addView(view, params)
                view.requestLayout()
                view.invalidate()
            }.onFailure { Log.w(TAG, "bringOverlayToFront forceReAdd failed", it) }
            return
        }
        runCatching {
            wm.updateViewLayout(view, params)
            view.requestLayout()
            view.invalidate()
        }.onFailure { Log.w(TAG, "bringOverlayToFront updateViewLayout failed", it) }
    }

    private fun applyAllLayouts(settings: AppSettings, relayoutChrome: Boolean = true) {
        applyBallLayout(settings)
        if (relayoutChrome) {
            applyLineLayout(settings)
        }
        updateChromeVisibility(settings)
    }

    private fun applyBallLayout(settings: AppSettings) {
        sceneState?.ballCenterPx?.value = null
    }

    private fun applyLineLayout(settings: AppSettings) {
        if (!FloatBallLayout.shouldShowLine(settings)) {
            sceneState?.lineVisible?.value = false
            return
        }
        sceneState?.lineVisible?.value = !isDragging || dragOriginatedFromLine
    }

    private fun updateChromeVisibility(settings: AppSettings) {
        val state = sceneState ?: return
        if (captureSuppressed) {
            state.chromeVisible.value = false
            syncTouchWindowLayout(settings)
            return
        }
        state.chromeVisible.value = true
        if (isDragging || passiveLineRestoreRunnable != null) {
            state.ballVisible.value = true
            state.lineVisible.value = dragOriginatedFromLine && FloatBallLayout.shouldShowLine(settings)
            if (!dragOriginatedFromLine) {
                setLineTouchHostEnabled(false)
            }
            return
        }
        state.ballVisible.value = true
        state.lineVisible.value = FloatBallLayout.shouldShowLine(settings)
        touchHost?.ballStripTouchable = true
        lineTouchHost?.stripTouchable = true
        syncTouchWindowLayout(settings)
        syncSplitIdleChrome(settings)
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
        expandLineTouchCapture()
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
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        activeSideAtDragStart = null
        val settings = settingsState?.value
        if (settings != null) {
            restoreAfterDragEnd(settings)
        } else {
            clearCursorUi(restoreLayout = false)
            setDragging(false)
        }
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
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        flushDragChromeLayout(syncAnchorState = true)
        commitPickAnchor()
        val hadPauseIntent = cursorPausedState?.value == true || selectionStartState?.value != null
        if (hadPauseIntent) {
            if (!regionalPickActive) {
                ensurePreviewBoundsForPick()
            }
            handlePickOnRelease(settings)
        }
        commitLineDragSideSwap()
        dragActiveSideOverride = null
        activeSideAtDragStart = null

        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            persistBallCenterFraction()
        }

        restoreAfterDragEnd(settingsState?.value ?: settings)
    }

    private fun handlePickOnRelease(settings: AppSettings) {
        val end = currentPickAnchor() ?: return
        val start = selectionStartState?.value ?: end
        val host = appContext ?: return
        val view = displayView ?: return
        val dragRect = rectBetween(start, end)
        val isRegionalDrag = regionalPickActive
        val previewBounds = selectionPreviewBoundsState?.value
        if (!isRegionalDrag && previewBounds == null) {
            return
        }
        val ocrFallbackEnabled = settings.floatBallOcrFallbackEnabled
        val ocrModelId = settings.floatBallOcrModelId

        when {
            isRegionalDrag -> {
                val density = view.resources.displayMetrics.density
                val minSidePx = (REGIONAL_RECT_MIN_SIDE_DP * density).roundToInt()
                if (dragRect.width() < minSidePx || dragRect.height() < minSidePx) {
                    return
                }
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
                PickPerf.beginSession("preview_bounds")
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
                    PickPerf.endSession("END", "preview_bounds")
                }
            }
        }
    }

    private fun snapBallToEdge(settings: AppSettings) {
        restoreDockPosition(settings)
    }

    private fun persistBallCenterFraction() {
        val view = displayView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
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
        val yFraction = FloatBallLayout.coercePositionYFraction(centerY / screenHeightPx)
        onPositionPersisted?.invoke(customCenterXFraction, yFraction)
    }

    private fun showCursorPickPreview(@Suppress("UNUSED_PARAMETER") screenX: Float, @Suppress("UNUSED_PARAMETER") screenY: Float) {
        if (isDragging) return
        passivePickPreviewAnchor = computePassivePickAnchor()
        cursorPreviewActive = true
        passivePickPreviewAlpha = pickPreviewAlpha(0f)
        syncCursorPreviewAppearance()
        cursorPreviewView?.visibility = View.VISIBLE
    }

    private fun updateCursorPickPreviewAlpha(progress: Float) {
        if (!cursorPreviewActive || isDragging) return
        passivePickPreviewAlpha = pickPreviewAlpha(progress)
        syncCursorPreviewAppearance()
    }

    private fun cancelCursorPickPreview() {
        if (!cursorPreviewActive || isDragging) return
        cursorPreviewActive = false
        passivePickPreviewAlpha = 1f
        passivePickPreviewAnchor = null
        cursorPreviewView?.visibility = View.GONE
        syncCursorPreviewAppearance()
    }

    private fun computePassivePickAnchor(): Offset {
        val view = displayView ?: return Offset.Zero
        val settings = settingsState?.value ?: return Offset.Zero
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = FloatBallScreenMetrics.bounds(view.context, windowManager)
        val ballSizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density)
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
        val activeSide = effectiveActiveSide(settings)
        val (ballCenterX, ballCenterY) = FloatBallLayout.ballCenterPx(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx,
        )
        return FloatBallPickAnchor.pickPointForBallCenter(
            settings = settings,
            ballCenterX = ballCenterX,
            ballCenterY = ballCenterY,
            ballSizePx = ballSizePx,
            screenWidth = bounds.width,
            screenHeight = bounds.height,
            density = density,
            dockSide = activeSide,
        )
    }

    private fun pickPreviewAlpha(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return PICK_PREVIEW_ALPHA_MIN + (1f - PICK_PREVIEW_ALPHA_MIN) * eased
    }

    private fun showCursorAtScreenTouch(
        screenX: Float,
        screenY: Float,
        deferBallWindowMutation: Boolean = false,
        fromEdgeGesture: Boolean = false,
    ) {
        val view = displayView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = FloatBallScreenMetrics.bounds(view.context, windowManager)
        val ballSizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt()
        val screenWidth = bounds.width
        val screenHeight = bounds.height

        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
        val activeSide = if (fromEdgeGesture) {
            if (screenX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
        } else {
            effectiveActiveSide(settings)
        }
        val (ballCenterX, ballCenterY) = if (fromEdgeGesture) {
            screenX to screenY
        } else {
            FloatBallLayout.ballCenterPx(
                settings,
                metrics,
                activeSide,
                screenWidthPx,
                screenHeightPx,
            )
        }
        val pickDockSide = when {
            fromEdgeGesture -> if (screenX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
            else -> effectiveActiveSide(settings)
        }
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
            pickDockSide = pickDockSide,
        )

        setDragging(true)
        if (!fromEdgeGesture && dragOriginatedFromLine && !deferBallWindowMutation) {
            setBallTouchable(false)
        }
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        pendingPickAnchor = null
        cancelBoundsLookupGeneration()
        cancelCacheRefresh()
        cancelInitialPreviewBoundsCache()
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
        dragScreenBounds = FloatBallScreenMetrics.bounds(view.context, windowManager)
        PickPrefetchCache.invalidate()
        FloatBallPreviewBoundsCache.invalidate()
        regionalPickActive = false
        selectionStartState?.value = null
        selectionPreviewBoundsState?.value = null
        cursorVisibleState?.value = true
        cursorPausedState?.value = false
        cursorPreviewActive = false
        passivePickPreviewAlpha = 1f
        // Do not move or resize the ball window here — that cancels the Compose drag gesture.
        updatePickAndBallFromFinger(
            moveBallWindow = fromEdgeGesture || (deferBallWindowMutation && dragOriginatedFromLine),
        )
        syncCursorPreviewAppearance()
        if (cursorPreviewView?.visibility != View.VISIBLE) {
            setCursorLayersVisible(true)
        }
        scheduleDeferredDragStart(deferBallWindowMutation || fromEdgeGesture)
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        schedulePauseTimer()
        val anchor = currentPickAnchor()
        if (anchor != null) {
            lastPauseScheduleX = anchor.x
            lastPauseScheduleY = anchor.y
        }
        scheduleInitialPreviewBoundsCache()
    }

    private fun setBallTouchable(touchable: Boolean) {
        touchHost?.ballStripTouchable = touchable
    }

    private fun clearCursorUi(restoreLayout: Boolean = true) {
        dragOriginatedFromLine = false
        lineDragEndedWithGesture = false
        dragActiveSideOverride = null
        selectionPreviewBoundsState?.value = null
        setBallTouchable(true)
        if (restoreLayout) {
            settingsState?.value?.let { restorePassiveOverlayLayout(it) }
        }
        cancelPauseTimer()
        cancelCacheRefresh()
        cancelInitialPreviewBoundsCache()
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        boundsLookupGeneration++
        FloatBallPreviewBoundsCache.invalidate()
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
        dragScreenBounds = null
        currentDragPickAnchor = Offset.Zero
        regionalPickActive = false
        passivePickPreviewAlpha = 1f
        dragSession.reset()
        hideGestureHintWindow()
        cursorPreviewActive = false
        passivePickPreviewAlpha = 1f
        cursorVisibleState?.value = false
        cursorPausedState?.value = false
        selectionStartState?.value = null
        setCursorLayersVisible(false)
    }

    private fun hideCursor(restorePassive: Boolean = true) {
        activeSideAtDragStart = null
        if (isDragging) {
            clearCursorUi(restoreLayout = false)
            setDragging(false)
            setBallTouchHostPassthrough(false)
            releaseAllTouchCaptures()
            syncTouchCaptureLayouts()
            if (restorePassive) {
                settingsState?.value?.let {
                    restorePassiveOverlayLayout(it, fixZOrder = false, deferLineRestore = true)
                }
            }
            flushDeferredChromeRaiseIfNeeded()
            return
        }
        clearCursorUi(restoreLayout = restorePassive)
    }

    private fun onDragMoved() {
        val start = selectionStartState?.value
        if (start != null) {
            val anchor = currentPickAnchor()
            if (anchor != null) {
                updateRegionalPickModeOnMove(anchor, start)
            }
            syncCursorPreviewAppearance()
            return
        }
        if (cursorPausedState?.value == true) {
            cursorPausedState?.value = false
            syncCursorChromeAppearance()
        }
        schedulePauseTimerIfMoved()
        schedulePreviewCacheRefresh()
    }

    private fun updateRegionalPickModeOnMove(anchor: Offset, start: Offset) {
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val movePx = CACHE_REFRESH_MOVE_DP * density
        val distFromStart = hypot(anchor.x - start.x, anchor.y - start.y)

        if (regionalPickActive) {
            if (distFromStart < movePx) {
                restoreActiveDragFromPauseOrigin()
            }
            return
        }
        if (distFromStart >= movePx) {
            enterRegionalPickMode()
        }
    }

    private fun enterRegionalPickMode() {
        regionalPickActive = true
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        selectionPreviewBoundsState?.value = null
        syncCursorChromeAppearance()
    }

    /** Screenshot mode: plus back at pause origin → red cross, resume normal drag. */
    private fun restoreActiveDragFromPauseOrigin() {
        regionalPickActive = false
        cursorPausedState?.value = false
        selectionStartState?.value = null
        unlockActivePickGestureFromPause()
        cancelPauseTimer()
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        FloatBallPickResultPanel.releaseWarmUpShell()
        applyPreviewBoundsFromCache()
        syncCursorChromeAppearance()
    }

    /** FV L0: only reset 280ms pause countdown when finger moves meaningfully. */
    private fun schedulePauseTimerIfMoved() {
        val anchor = currentPickAnchor() ?: return
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val movePx = CACHE_REFRESH_MOVE_DP * density
        if (!lastPauseScheduleX.isNaN() && !lastPauseScheduleY.isNaN()) {
            if (hypot(anchor.x - lastPauseScheduleX, anchor.y - lastPauseScheduleY) < movePx) {
                return
            }
        }
        lastPauseScheduleX = anchor.x
        lastPauseScheduleY = anchor.y
        schedulePauseTimer()
    }

    /** Yellow cross: finger held still long enough to lock regional pick start. */
    private fun schedulePauseTimer() {
        cancelPauseTimer()
        val runnable = Runnable { onCursorPaused() }
        pauseRunnable = runnable
        mainHandler.postDelayed(runnable, PAUSE_MS)
    }

    /** FV G4: async full-tree scan into preview bounds cache. */
    private fun startPreviewBoundsCache() {
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        FloatBallPreviewBoundsCache.refresh(
            service = service,
            onReady = {
                if (!isDragging || cursorVisibleState?.value != true) return@refresh
                applyPreviewBoundsFromCache()
            },
        )
    }

    /** FV G4: first cache build waits ~300ms so fast drags skip the heavy a11y walk. */
    private fun scheduleInitialPreviewBoundsCache() {
        cancelInitialPreviewBoundsCache()
        val runnable = Runnable {
            initialCacheRunnable = null
            if (!isDragging || cursorVisibleState?.value != true) return@Runnable
            val anchor = currentPickAnchor()
            if (anchor != null) {
                lastCacheRefreshX = anchor.x
                lastCacheRefreshY = anchor.y
            }
            startPreviewBoundsCache()
        }
        initialCacheRunnable = runnable
        mainHandler.postDelayed(runnable, INITIAL_CACHE_DELAY_MS)
    }

    private fun cancelInitialPreviewBoundsCache() {
        initialCacheRunnable?.let { mainHandler.removeCallbacks(it) }
        initialCacheRunnable = null
    }

    /** FV O0: rebuild cache after finger moves, without blocking MOVE. */
    private fun schedulePreviewCacheRefresh() {
        if (cursorPausedState?.value == true) return
        if (selectionStartState?.value != null) return
        val anchor = currentPickAnchor() ?: return
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val movePx = CACHE_REFRESH_MOVE_DP * density
        if (!lastCacheRefreshX.isNaN() && !lastCacheRefreshY.isNaN()) {
            if (hypot(anchor.x - lastCacheRefreshX, anchor.y - lastCacheRefreshY) < movePx) {
                return
            }
        }
        cancelCacheRefresh()
        val runnable = Runnable {
            cacheRefreshRunnable = null
            if (!isDragging || cursorVisibleState?.value != true) return@Runnable
            if (cursorPausedState?.value == true) return@Runnable
            val latest = currentPickAnchor() ?: return@Runnable
            lastCacheRefreshX = latest.x
            lastCacheRefreshY = latest.y
            startPreviewBoundsCache()
        }
        cacheRefreshRunnable = runnable
        mainHandler.postDelayed(runnable, CACHE_REFRESH_MS)
    }

    /** FV o1.r(x,y): instant hit-test on cached rects — coalesced to one pass per animation frame. */
    private fun applyPreviewBoundsFromCache() {
        if (!isDragging || cursorVisibleState?.value != true) return
        if (regionalPickActive) return
        if (cursorPausedState?.value == true && selectionStartState?.value != null) return
        val anchor = currentPickAnchor() ?: return
        val bounds = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y) ?: return
        val current = selectionPreviewBoundsState?.value
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val slopPx = (2f * density).roundToInt()
        if (current == null || !previewBoundsStableEquals(current, bounds, slopPx)) {
            selectionPreviewBoundsState?.value = bounds
            syncCursorChromeAppearance()
        }
    }

    private fun lockActivePickGestureFromPause() {
        if (dragOriginatedFromLine) {
            lineTouchHost?.lockPickFromPause()
        } else {
            touchHost?.lockPickFromPause()
        }
    }

    /** 黄框/A 悬停：界面已暂停，松手应提交取词而非 cancel。 */
    private fun hasPickPauseIntentForCommit(): Boolean {
        if (cursorPausedState?.value == true) return true
        if (selectionStartState?.value != null) return true
        return false
    }

    private fun unlockActivePickGestureFromPause() {
        touchHost?.unlockPickFromPause()
        lineTouchHost?.unlockPickFromPause()
    }

    private fun onCursorPaused() {
        if (cursorVisibleState?.value != true) return
        if (cursorPausedState?.value == true) return
        val anchor = currentPickAnchor() ?: return
        cancelPauseTimer()
        regionalPickActive = false
        cursorAnchorState?.value = anchor
        val bounds = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        selectionStartState?.value = anchor
        cursorPausedState?.value = true
        lockActivePickGestureFromPause()
        if (bounds != null) {
            selectionPreviewBoundsState?.value = bounds
            maybeStartPickPrefetch()
        } else {
            launchPreviewBoundsLookupFallback(anchor)
        }
        syncCursorChromeAppearance()
    }

    /** One-shot tree lookup when cache is not ready at pause time. */
    private fun launchPreviewBoundsLookupFallback(anchor: Offset) {
        val generation = ++boundsLookupGeneration
        val x = anchor.x
        val y = anchor.y
        overlayScope.launch(Dispatchers.Default) {
            val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
                rawX = x,
                rawY = y,
            )
            withContext(Dispatchers.Main) {
                if (generation != boundsLookupGeneration) return@withContext
                if (!isDragging || cursorVisibleState?.value != true) return@withContext
                if (cursorPausedState?.value != true) return@withContext
                if (regionalPickActive) return@withContext
                if (bounds != null) {
                    selectionPreviewBoundsState?.value = bounds
                    maybeStartPickPrefetch()
                    syncCursorChromeAppearance()
                }
            }
        }
    }

    private fun cancelBoundsLookupGeneration() {
        boundsLookupGeneration++
    }

    private fun previewBoundsStableEquals(current: Rect, next: Rect, slopPx: Int): Boolean {
        return kotlin.math.abs(current.left - next.left) <= slopPx &&
            kotlin.math.abs(current.top - next.top) <= slopPx &&
            kotlin.math.abs(current.right - next.right) <= slopPx &&
            kotlin.math.abs(current.bottom - next.bottom) <= slopPx
    }

    private fun cancelCacheRefresh() {
        cacheRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        cacheRefreshRunnable = null
    }

    private fun ensurePreviewBoundsForPick() {
        if (selectionPreviewBoundsState?.value != null) return
        val anchor = currentPickAnchor() ?: return
        val cached = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        if (cached != null) {
            selectionPreviewBoundsState?.value = cached
            syncCursorChromeAppearance()
            return
        }
        val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
            rawX = anchor.x,
            rawY = anchor.y,
        )
        if (bounds != null) {
            selectionPreviewBoundsState?.value = bounds
            syncCursorChromeAppearance()
        }
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

    private fun rectBetween(start: Offset, end: Offset): Rect {
        val left = min(start.x, end.x).roundToInt()
        val top = min(start.y, end.y).roundToInt()
        val right = max(start.x, end.x).roundToInt()
        val bottom = max(start.y, end.y).roundToInt()
        return Rect(left, top, right, bottom)
    }

    private fun cancelPauseTimer() {
        pauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pauseRunnable = null
    }

    private fun cancelDragChromeLayoutFrame() {
        dragChromeLayoutFrameScheduled = false
        pendingCursorFrameAnchor = null
    }

    private fun scheduleDragChromeLayoutOnNextFrame() {
        val view = displayView ?: cursorPreviewView ?: return
        if (dragChromeLayoutFrameScheduled) return
        dragChromeLayoutFrameScheduled = true
        view.postOnAnimation {
            dragChromeLayoutFrameScheduled = false
            if (!isDragging) return@postOnAnimation
            commitDragChromeLayoutFrame()
        }
    }

    private fun flushDragChromeLayout(syncAnchorState: Boolean = false) {
        cancelDragChromeLayoutFrame()
        if (!isDragging && !syncAnchorState) return
        commitDragChromeLayoutFrame(forceAnchorState = syncAnchorState)
    }

    /** Ball + cursor chrome in one animation tick (FV-style single-frame move). */
    private fun commitDragChromeLayoutFrame(forceAnchorState: Boolean = false) {
        val pick = pendingCursorFrameAnchor ?: currentDragPickAnchor
        pendingCursorFrameAnchor = null
        settingsState?.value?.let { applyDragBallLayout(it) }
        val needsPreviewAnchor = forceAnchorState ||
            cursorPausedState?.value == true ||
            selectionStartState?.value != null
        if (needsPreviewAnchor) {
            cursorAnchorState?.value = pick
        }
        syncCursorPreviewAppearance()
        if (isDragging && cursorVisibleState?.value == true) {
            applyPreviewBoundsFromCache()
        }
    }

    private fun cancelCursorCommitFrame() {
        cursorCommitFrameScheduled = false
        pendingPickAnchor = null
    }

    private fun scheduleCursorCommitOnNextFrame() {
        val view = displayView ?: return
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
            currentDragPickAnchor = pick
            pendingCursorFrameAnchor = pick
            return
        }
        pendingPickAnchor = pick
        scheduleCursorCommitOnNextFrame()
    }

    private fun applyDragBallLayout(settings: AppSettings) {
        val view = displayView ?: return
        val metrics = view.resources.displayMetrics
        val ballSizePx = FloatBallLayout.ballSizePx(settings, metrics.density)
        val marginPx = FloatBallLayout.marginPx(metrics.density)
        val screenBounds = dragScreenBounds ?: FloatBallScreenMetrics.bounds(view.context, windowManager)
            .also { dragScreenBounds = it }
        val center = dragSession.clampedBallCenter(
            ballSizePx = ballSizePx.toFloat(),
            marginPx = marginPx,
            screenWidth = screenBounds.width.roundToInt(),
            screenHeight = screenBounds.height.roundToInt(),
        )
        sceneState?.ballCenterPx?.value = center
    }

    private fun updatePickAndBallFromFinger(moveBallWindow: Boolean) {
        val view = displayView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = dragScreenBounds ?: FloatBallScreenMetrics.bounds(view.context, windowManager)
            .also { dragScreenBounds = it }
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

        if (!moveBallWindow) return
        scheduleDragChromeLayoutOnNextFrame()
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

