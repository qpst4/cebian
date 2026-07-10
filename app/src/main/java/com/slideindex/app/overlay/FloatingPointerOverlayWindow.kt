package com.slideindex.app.overlay

import com.slideindex.app.di.OverlayDependencies
import com.slideindex.app.di.OverlayDependencyAccess
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.InputTapUtil
import com.slideindex.app.util.PermissionHelper
import java.util.ArrayDeque
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating pointer with QC-inspired visuals:
 * - Display layer (NOT_TOUCHABLE): pointer + joystick artwork.
 * - Touch layer: passthrough everywhere except the joystick disc; uses raw screen coords (no jitter).
 */
object FloatingPointerOverlayWindow {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var displayView: ComposeView? = null
    private var touchHost: FloatingPointerHostLayout? = null
    private var displayOwner: OverlayComposeOwner? = null
    private var visibleState: MutableState<Boolean>? = null
    private var settingsState: MutableState<AppSettings>? = null
    private var session: FloatingPointerSession? = null
    private var touchLayoutParams: WindowManager.LayoutParams? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var settingsCollectJob: Job? = null
    private var idleHideRunnable: Runnable? = null
    private var outsideDismissGraceRunnable: Runnable? = null
    /** Ignore outside-click dismiss until the triggering finger lifts or grace elapses. */
    private var outsideDismissSuppressed = false
    /** After a joystick gesture, keep touch capture collapsed so the screen stays scrollable. */
    private var touchCaptureUserCollapsed = false
    private var actionExecutor: ActionExecutor? = null
    /** Blocks re-entry while an injected pointer tap is in flight. */
    private var isPointerTapInFlight = false
    /**
     * Ignore ACTION_OUTSIDE dismiss until this uptime. Injected pointer taps echo as
     * ACTION_OUTSIDE with zeroed coordinates (InputDispatcher FLAG_ZERO_COORDS), so
     * coordinate filtering cannot distinguish them from real outside taps.
     */
    private var pointerTapOutsideSuppressUntilMs = 0L
    /** Queued joystick taps to run after the current injection finishes. */
    private val pendingPointerTaps = ArrayDeque<PendingPointerTap>()
    private var pendingPointerSwipeRunnable: Runnable? = null
    private var isPointerSwipeInFlight = false
    /** Edge gesture finger is still down; MOVE/UP are forwarded from edge capture. */
    private var continuedGestureActive = false
    private var pendingCleanupRunnable: Runnable? = null

    private data class PendingPointerTap(
        val rawX: Float,
        val rawY: Float,
    )

    val isShowing: Boolean get() = displayView != null
    val isVisible: Boolean get() = visibleState?.value == true

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float? = null,
        anchorRawY: Float? = null,
        continueTouch: Boolean = false,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, settings, anchorRawX, anchorRawY, continueTouch) }
            return
        }
        if (isShowing) {
            if (visibleState?.value == true) {
                settingsState?.value = settings
                OverlayPerformanceMonitorBinding.syncUserPreference(settings, appContext)
                onSettingsUpdated(settings)
                return
            }
            cleanup()
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return
        }

        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return
            }

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dm = hostContext.resources.displayMetrics
        val screenBounds = overlayScreenBounds(wm, dm)
        val visible = mutableStateOf(false)
        val settingsHolder = mutableStateOf(settings)
        val pointerSession = FloatingPointerSession(
            density = dm.density,
            screenWidth = screenBounds.width,
            screenHeight = screenBounds.height,
            settingsSource = { settingsHolder.value },
        )
        if (anchorRawX != null && anchorRawY != null) {
            pointerSession.placeAtTouch(anchorRawX, anchorRawY, settings)
        } else {
            pointerSession.awaitingPlacement = true
        }

        val displayDialogOwner = OverlayComposeOwner()
        val displayCompose = OverlayCompose.createComposeView(overlayContext, displayDialogOwner).apply {
            setContent {
                val currentSettings by settingsHolder
                val isVisible by visible
                FloatingPointerDisplay(
                    session = pointerSession,
                    settings = currentSettings,
                    visible = isVisible,
                )
            }
        }

        val touchLayout = FloatingPointerHostLayout(
            context = overlayContext,
            session = pointerSession,
            settingsProvider = { settingsHolder.value },
            onJoystickPositionChanged = { centerX, centerY ->
                collapseTouchCapture(centerX, centerY, forceCollapse = true)
            },
            onGestureEnd = { centerX, centerY, _ ->
                touchCaptureUserCollapsed = true
                collapseTouchCapture(centerX, centerY, forceCollapse = true)
            },
            onPointerClick = { rawX, rawY -> runPointerTap(rawX, rawY) },
            onOutsideDismissPrepare = { runOutsideDismissPrepare() },
            onQuickSwipeDismiss = { runQuickSwipeDismiss() },
            onDismiss = { dismiss() },
            onRadialMenuOpened = { expandTouchCapture() },
            onRadialMenuClosed = {
                session?.let {
                    collapseTouchCapture(it.joystickCenterX.floatValue, it.joystickCenterY.floatValue, forceCollapse = true)
                }
            },
            onRadialMenuAction = { slotIndex -> executeRadialSlotAction(slotIndex) },
            onActivity = { resetIdleTimer() },
            onHaptic = { displayCompose.let { HapticHelper.appTick(it, settingsHolder.value) } },
            shouldDismissOnOutsideTouch = ::shouldDismissOnOutsideTouch,
            onTouchCycleComplete = { onTouchCycleComplete() },
        )

        val displayParams = buildDisplayParams(hostContext)
        val touchParams = if (pointerSession.awaitingPlacement) {
            buildExpandedTouchParams(hostContext)
        } else {
            buildCollapsedTouchParams(hostContext, pointerSession)
        }

        val displayAdded = runCatching { wm.addView(displayCompose, displayParams) }
            .onFailure { Log.e(TAG, "display addView failed", it) }
            .isSuccess
        if (!displayAdded) {
            displayDialogOwner.destroy()
            return
        }

        val touchAdded = runCatching { wm.addView(touchLayout, touchParams) }
            .onFailure { Log.e(TAG, "touch addView failed", it) }
            .isSuccess
        if (!touchAdded) {
            runCatching { wm.removeView(displayCompose) }
            displayDialogOwner.destroy()
            return
        }

        windowManager = wm
        displayView = displayCompose
        touchHost = touchLayout
        displayOwner = displayDialogOwner
        visibleState = visible
        settingsState = settingsHolder
        session = pointerSession
        touchLayoutParams = touchParams
        appContext = hostContext
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
            ?: run {
                Log.w(TAG, "show: accessibility service deps unavailable")
                runCatching { wm.removeView(displayCompose) }
                displayDialogOwner.destroy()
                return
            }
        actionExecutor = ActionExecutor(
            context = hostContext,
            appRepository = deps.appRepository,
            clickPassthroughHandler = null,
            overlayBrightness = null,
            side = PanelSide.LEFT,
            onShellCommandsPersist = { commands ->
                overlayScope.launch {
                    deps.settingsRepository.setShellCommands(commands)
                }
            },
        )
        registerScreenOffReceiver(hostContext)
        OverlayPerformanceMonitorBinding.onOverlayShown(settings, hostContext)
        startSettingsSync(deps, settingsHolder)
        resetIdleTimer()
        beginOutsideDismissGrace()

        val anchorX = anchorRawX
        val anchorY = anchorRawY
        val shouldContinueTouch = continueTouch && anchorX != null && anchorY != null
        continuedGestureActive = shouldContinueTouch
        displayCompose.post {
            visible.value = true
            if (shouldContinueTouch) {
                touchLayout.beginContinuedGesture(
                    anchorX,
                    anchorY,
                    SystemClock.uptimeMillis(),
                )
            }
        }
    }

    /** True while an edge-gesture finger is still down and events are forwarded here. */
    fun isConsumingEdgeGestureTouch(): Boolean = continuedGestureActive

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

    private fun beginOutsideDismissGrace() {
        outsideDismissSuppressed = true
        outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            outsideDismissGraceRunnable = null
            outsideDismissSuppressed = false
        }
        outsideDismissGraceRunnable = runnable
        mainHandler.postDelayed(runnable, OUTSIDE_DISMISS_GRACE_MS)
    }

    private fun onTouchCycleComplete() {
        if (!outsideDismissSuppressed) return
        outsideDismissSuppressed = false
        outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        outsideDismissGraceRunnable = null
    }

    private fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean {
        if (outsideDismissSuppressed) return false
        if (isPointerTapInFlight) return false
        if (pendingPointerTaps.isNotEmpty()) return false
        if (SystemClock.uptimeMillis() < pointerTapOutsideSuppressUntilMs) return false
        val pointerSession = session ?: return true
        if (hasReliableOutsideTouchCoordinates(event) &&
            pointerSession.isNearPointer(event.rawX, event.rawY)
        ) {
            return false
        }
        return true
    }

    private fun hasReliableOutsideTouchCoordinates(event: MotionEvent): Boolean {
        // Outside touches in other apps/UIDs are zeroed for security; only trust non-origin coords.
        return event.rawX != 0f || event.rawY != 0f
    }

    private fun markPointerTapOutsideSuppress() {
        val until = SystemClock.uptimeMillis() + POINTER_TAP_OUTSIDE_SUPPRESS_MS
        if (until > pointerTapOutsideSuppressUntilMs) {
            pointerTapOutsideSuppressUntilMs = until
        }
    }

    private fun extendPointerTapOutsideSuppressAfterComplete() {
        val until = SystemClock.uptimeMillis() + POINTER_TAP_OUTSIDE_ECHO_AFTER_COMPLETE_MS
        if (until > pointerTapOutsideSuppressUntilMs) {
            pointerTapOutsideSuppressUntilMs = until
        }
    }

    private fun resetIdleTimer() {
        idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        idleHideRunnable = null
        val settings = settingsState?.value ?: return
        if (!settings.floatingPointerHideWhenIdle) return
        if (session?.joystickActive?.value == true || session?.radialMenuActive?.value == true) return
        val runnable = Runnable {
            if (session?.joystickActive?.value == true || session?.radialMenuActive?.value == true) {
                resetIdleTimer()
                return@Runnable
            }
            dismiss()
        }
        idleHideRunnable = runnable
        mainHandler.postDelayed(runnable, settings.floatingPointerIdleHideDelayMs.toLong())
    }

    private fun startSettingsSync(deps: OverlayDependencies, settingsHolder: MutableState<AppSettings>) {
        settingsCollectJob?.cancel()
        settingsCollectJob = overlayScope.launch {
            deps.settingsRepository.settings.collectLatest { latest ->
                settingsHolder.value = latest
                OverlayPerformanceMonitorBinding.syncUserPreference(latest, appContext)
                onSettingsUpdated(latest)
            }
        }
    }

    private fun onSettingsUpdated(settings: AppSettings) {
        session?.applyLayoutSettings(settings)
        if (session?.joystickActive?.value == true || session?.radialMenuActive?.value == true) {
            resetIdleTimer()
            return
        }
        session?.let {
            collapseTouchCapture(it.joystickCenterX.floatValue, it.joystickCenterY.floatValue, forceCollapse = true)
        }
        resetIdleTimer()
    }

    fun toggle(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float? = null,
        anchorRawY: Float? = null,
        continueTouch: Boolean = false,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { toggle(context, settings, anchorRawX, anchorRawY, continueTouch) }
            return
        }
        if (isShowing && isVisible) {
            if (anchorRawX != null && anchorRawY != null) {
                resummonAtAnchor(context, settings, anchorRawX, anchorRawY, continueTouch)
            } else {
                dismiss()
            }
            return
        }
        show(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    private fun resummonAtAnchor(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        continueTouch: Boolean,
    ) {
        cancelPendingCleanup()
        idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        idleHideRunnable = null
        continuedGestureActive = false
        outsideDismissSuppressed = false
        outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        outsideDismissGraceRunnable = null
        visibleState?.value = false
        cleanup()
        show(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) {
            cleanup()
            return
        }
        session?.clearTrail()
        session?.let {
            collapseTouchCapture(
                it.joystickCenterX.floatValue,
                it.joystickCenterY.floatValue,
                forceCollapse = true,
            )
        }
        visible.value = false
        scheduleCleanup()
    }

    private fun scheduleCleanup() {
        cancelPendingCleanup()
        val runnable = Runnable {
            pendingCleanupRunnable = null
            cleanup()
        }
        pendingCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, FLOATING_POINTER_PRESENCE_ANIMATION_MS)
    }

    private fun cancelPendingCleanup() {
        pendingCleanupRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingCleanupRunnable = null
    }

    private fun collapseTouchCapture(
        centerX: Float,
        centerY: Float,
        @Suppress("UNUSED_PARAMETER") forceCollapse: Boolean = false,
    ) {
        val pointerSession = session ?: return
        if (pointerSession.radialMenuActive.value) return
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        val size = pointerSession.joystickDiameterPx().roundToInt()
        val maxX = (pointerSession.screenWidth - size).roundToInt().coerceAtLeast(0)
        val maxY = (pointerSession.screenHeight - size).roundToInt().coerceAtLeast(0)
        val targetX = (centerX - pointerSession.joystickRadiusPx()).roundToInt().coerceIn(0, maxX)
        val targetY = (centerY - pointerSession.joystickRadiusPx()).roundToInt().coerceIn(0, maxY)
        if (params.width == size &&
            params.height == size &&
            params.x == targetX &&
            params.y == targetY &&
            pointerSession.joystickCenterX.floatValue == centerX &&
            pointerSession.joystickCenterY.floatValue == centerY
        ) {
            return
        }
        pointerSession.joystickCenterX.floatValue = centerX
        pointerSession.joystickCenterY.floatValue = centerY
        params.width = size
        params.height = size
        params.x = targetX
        params.y = targetY
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "collapseTouchCapture failed", it) }
    }

    private fun expandTouchCapture() {
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        if (params.width == WindowManager.LayoutParams.MATCH_PARENT &&
            params.height == WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "expandTouchCapture failed", it) }
    }

    private fun executeRadialSlotAction(slotIndex: Int) {
        val settings = settingsState?.value ?: return
        val slots = settings.floatingPointerRadialSlotActions
        val action = slots.getOrNull(slotIndex) ?: return
        if (action is GestureAction.None) return
        val pointerSession = session ?: return
        onHaptic(settings)
        if (action is GestureAction.SimulatePointerSwipe) {
            schedulePointerSwipe(
                startX = pointerSession.pointerX.floatValue,
                startY = pointerSession.pointerY.floatValue,
                config = action.config,
            )
            return
        }
        val executor = actionExecutor ?: return
        executor.execute(
            action = action,
            settings = settings,
            anchorRawX = pointerSession.pointerX.floatValue,
            anchorRawY = pointerSession.pointerY.floatValue,
        )
    }

    /**
     * Defers pointer swipe until the radial-menu touch cycle finishes and the touch overlay
     * stops intercepting injected gestures.
     */
    fun schedulePointerSwipe(startX: Float, startY: Float, config: PointerSwipeConfig) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { schedulePointerSwipe(startX, startY, config) }
            return
        }
        pendingPointerSwipeRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingPointerSwipeRunnable = null
            enqueuePointerSwipe(startX, startY, config)
        }
        pendingPointerSwipeRunnable = runnable
        mainHandler.postDelayed(runnable, RADIAL_ACTION_INJECT_DELAY_MS)
    }

    private fun enqueuePointerSwipe(startX: Float, startY: Float, config: PointerSwipeConfig) {
        if (isPointerTapInFlight || isPointerSwipeInFlight) {
            mainHandler.postDelayed(
                { enqueuePointerSwipe(startX, startY, config) },
                SlideIndexAccessibilityService.POINTER_TAP_CHAIN_GAP_MS,
            )
            return
        }
        startPointerSwipe(startX, startY, config)
    }

    private fun startPointerSwipe(startX: Float, startY: Float, config: PointerSwipeConfig) {
        isPointerSwipeInFlight = true
        setTouchOverlayPassthrough(true)
        markPointerTapOutsideSuppress()
        Log.i(TAG, "injectPointerSwipe start ($startX, $startY) config=$config")
        InputTapUtil.dispatchPointerSwipeAsync(startX, startY, config) { ok ->
            Log.i(TAG, "injectPointerSwipe finished ok=$ok at ($startX, $startY)")
            onPointerSwipeComplete()
        }
    }

    private fun onPointerSwipeComplete() {
        isPointerSwipeInFlight = false
        setTouchOverlayPassthrough(false)
        extendPointerTapOutsideSuppressAfterComplete()
    }

    private fun onHaptic(settings: AppSettings) {
        displayView?.let { HapticHelper.appTick(it, settings) }
    }

    private fun runQuickSwipeDismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runQuickSwipeDismiss() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) return
        session?.clearTrail()
        setTouchOverlayPassthrough(true)
        visible.value = false
        scheduleCleanup()
    }

    private fun runOutsideDismissPrepare() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runOutsideDismissPrepare() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) return
        session?.clearTrail()
        setTouchOverlayPassthrough(true)
        visible.value = false
        scheduleCleanup()
    }

    private fun runPointerTap(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runPointerTap(rawX, rawY) }
            return
        }
        enqueuePointerInjection(rawX, rawY)
    }

    private fun enqueuePointerInjection(rawX: Float, rawY: Float) {
        if (isPointerTapInFlight) {
            pendingPointerTaps.addLast(PendingPointerTap(rawX, rawY))
            return
        }
        startPointerTap(rawX, rawY)
    }

    private fun startPointerTap(rawX: Float, rawY: Float) {
        isPointerTapInFlight = true
        markPointerTapOutsideSuppress()
        injectPointerTap(rawX, rawY)
    }

    private fun injectPointerTap(rawX: Float, rawY: Float) {
        InputTapUtil.dispatchPointerTapAsync(rawX, rawY) { _ ->
            onPointerTapComplete()
        }
    }

    private fun onPointerTapComplete() {
        val next = pendingPointerTaps.pollFirst()
        if (next != null) {
            markPointerTapOutsideSuppress()
            mainHandler.postDelayed(
                { injectPointerTap(next.rawX, next.rawY) },
                SlideIndexAccessibilityService.POINTER_TAP_CHAIN_GAP_MS,
            )
            return
        }
        isPointerTapInFlight = false
        extendPointerTapOutsideSuppressAfterComplete()
    }

    private fun setTouchOverlayPassthrough(passthrough: Boolean) {
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "setTouchOverlayPassthrough failed", it) }
    }

    private fun buildDisplayParams(context: Context): WindowManager.LayoutParams {
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
            applyCutoutMode()
        }
    }

    private fun buildExpandedTouchParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            applyCutoutMode()
        }
    }

    private fun buildCollapsedTouchParams(
        context: Context,
        pointerSession: FloatingPointerSession,
    ): WindowManager.LayoutParams {
        val size = pointerSession.joystickDiameterPx().roundToInt()
        val maxX = (pointerSession.screenWidth - size).roundToInt().coerceAtLeast(0)
        val maxY = (pointerSession.screenHeight - size).roundToInt().coerceAtLeast(0)
        return WindowManager.LayoutParams(
            size,
            size,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (pointerSession.joystickCenterX.floatValue - pointerSession.joystickRadiusPx())
                .roundToInt()
                .coerceIn(0, maxX)
            y = (pointerSession.joystickCenterY.floatValue - pointerSession.joystickRadiusPx())
                .roundToInt()
                .coerceIn(0, maxY)
            applyCutoutMode()
        }
    }

    private fun WindowManager.LayoutParams.applyCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun cleanup() {
        OverlayPerformanceMonitorBinding.onOverlayHidden(appContext)
        cancelPendingCleanup()
        settingsCollectJob?.cancel()
        settingsCollectJob = null
        idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        idleHideRunnable = null
        outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        outsideDismissGraceRunnable = null
        outsideDismissSuppressed = false
        val wm = windowManager
        displayView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        touchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        OverlayCompose.disposeComposeView(displayView)
        displayOwner?.destroy()
        displayOwner = null
        displayView = null
        touchHost = null
        windowManager = null
        visibleState = null
        settingsState = null
        session = null
        touchLayoutParams = null
        screenOffReceiver = null
        appContext = null
        actionExecutor = null
        touchCaptureUserCollapsed = false
        isPointerTapInFlight = false
        pointerTapOutsideSuppressUntilMs = 0L
        pendingPointerTaps.clear()
        pendingPointerSwipeRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingPointerSwipeRunnable = null
        isPointerSwipeInFlight = false
        continuedGestureActive = false
    }

    private const val TAG = "FloatingPointerOverlay"
    private const val OUTSIDE_DISMISS_GRACE_MS = 450L
    /** Covers injected tap + in-flight ACTION_OUTSIDE echo (coords are zeroed). */
    private const val POINTER_TAP_OUTSIDE_SUPPRESS_MS = 500L
    /** ACTION_OUTSIDE can arrive after dispatchGesture/onFinished returns. */
    private const val POINTER_TAP_OUTSIDE_ECHO_AFTER_COMPLETE_MS = 350L
    private const val RADIAL_ACTION_INJECT_DELAY_MS = 120L

    /**
     * Full-screen overlay coordinates. [DisplayMetrics.heightPixels] can stop above the gesture
     * nav strip while FLAG_LAYOUT_IN_SCREEN overlays still draw and receive rawY through it.
     */
    private fun overlayScreenBounds(wm: WindowManager, fallback: DisplayMetrics): OverlayScreenBounds {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            return OverlayScreenBounds(
                width = bounds.width().toFloat(),
                height = bounds.height().toFloat(),
            )
        }
        @Suppress("DEPRECATION")
        val real = DisplayMetrics().also { metrics ->
            wm.defaultDisplay.getRealMetrics(metrics)
        }
        return OverlayScreenBounds(
            width = real.widthPixels.toFloat().takeIf { it > 0f } ?: fallback.widthPixels.toFloat(),
            height = real.heightPixels.toFloat().takeIf { it > 0f } ?: fallback.heightPixels.toFloat(),
        )
    }
}
