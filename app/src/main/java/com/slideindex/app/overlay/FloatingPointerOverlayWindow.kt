package com.slideindex.app.overlay

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.settings.AppSettings
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
                onSettingsUpdated(settings)
                return
            }
            cleanup()
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return
        }

        val hostContext = SlideIndexAccessibilityService.overlayHostContext()
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
        val app = hostContext.applicationContext as SlideIndexApp
        actionExecutor = ActionExecutor(
            context = hostContext,
            appRepository = app.appRepository,
            clickPassthroughHandler = null,
            overlayBrightness = null,
            side = PanelSide.LEFT,
        )
        registerScreenOffReceiver(hostContext)
        startSettingsSync(hostContext, settingsHolder)
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

    private fun startSettingsSync(context: Context, settingsHolder: MutableState<AppSettings>) {
        settingsCollectJob?.cancel()
        val app = context.applicationContext as SlideIndexApp
        settingsCollectJob = overlayScope.launch {
            app.settingsRepository.settings.collectLatest { latest ->
                settingsHolder.value = latest
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
        if (isShowing && isVisible) {
            dismiss()
        } else {
            show(context, settings, anchorRawX, anchorRawY, continueTouch)
        }
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
        mainHandler.postDelayed({ cleanup() }, 120L)
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
        mainHandler.postDelayed({ cleanup() }, QUICK_SWIPE_CLEANUP_DELAY_MS)
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
        mainHandler.postDelayed({ cleanup() }, OUTSIDE_DISMISS_CLEANUP_DELAY_MS)
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
    private const val QUICK_SWIPE_CLEANUP_DELAY_MS = 120L
    private const val OUTSIDE_DISMISS_CLEANUP_DELAY_MS = 120L
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

private data class OverlayScreenBounds(
    val width: Float,
    val height: Float,
)

private const val FLOATING_POINTER_RIPPLE_DURATION_MS = 280L

internal class FloatingPointerSession(
    val density: Float,
    val screenWidth: Float,
    val screenHeight: Float,
    private val settingsSource: () -> AppSettings,
) {
    val tapSlopPx = 10f * density

    fun joystickDiameterPx(): Float = settingsSource().floatingPointerJoystickDiameterPx
    fun joystickRadiusPx(): Float = joystickDiameterPx() / 2f

    val pointerX = mutableFloatStateOf(screenWidth / 2f)
    val pointerY = mutableFloatStateOf(screenHeight / 2f)
    val joystickCenterX = mutableFloatStateOf(0f)
    val joystickCenterY = mutableFloatStateOf(0f)
    val joystickActive = mutableStateOf(false)
    /** When hide-on-release is enabled, stays true after tap; false only after drag release. */
    val pointerVisible = mutableStateOf(true)
    val trailPoints = mutableStateListOf<FloatingPointerTrailPoint>()
    val rippleActive = mutableStateOf(false)
    val rippleCenterX = mutableFloatStateOf(0f)
    val rippleCenterY = mutableFloatStateOf(0f)
    val rippleStartTimeMs = mutableStateOf(0L)
    val radialMenuActive = mutableStateOf(false)
    val radialHighlightedSlot = mutableIntStateOf(-1)
    var radialMenuCenterX = 0f
    var radialMenuCenterY = 0f

    fun openRadialMenu(centerX: Float, centerY: Float) {
        radialMenuCenterX = centerX
        radialMenuCenterY = centerY
        joystickCenterX.floatValue = centerX
        joystickCenterY.floatValue = centerY
        radialMenuActive.value = true
        radialHighlightedSlot.intValue = -1
    }

    fun closeRadialMenu() {
        radialMenuActive.value = false
        radialHighlightedSlot.intValue = -1
    }

    fun updateRadialHighlight(fingerX: Float, fingerY: Float, settings: AppSettings): Boolean {
        if (!radialMenuActive.value) return false
        val inner = settings.floatingPointerRadialInnerDiameterPx / 2f
        val outer = settings.floatingPointerRadialOuterDiameterPx / 2f
        val previous = radialHighlightedSlot.intValue
        val newSlot = FloatingPointerRadialMenu.sectorIndexAt(
            centerX = radialMenuCenterX,
            centerY = radialMenuCenterY,
            fingerX = fingerX,
            fingerY = fingerY,
            innerRadius = inner,
            outerRadius = outer,
        ) ?: -1
        radialHighlightedSlot.intValue = newSlot
        return newSlot >= 0 && newSlot != previous
    }

    /** True until the first touch places joystick and pointer near the finger. */
    var awaitingPlacement = false

    /** Joystick center when the gesture started; the joystick area is anchored here. */
    private var gestureCenterX = 0f
    private var gestureCenterY = 0f
    private var gestureAreaLeft = 0f
    private var gestureAreaTop = 0f
    private var gestureAreaWidth = 0f
    private var gestureAreaHeight = 0f
    private var dragFingerAnchorX = 0f
    private var dragFingerAnchorY = 0f
    private var dragPointerAnchorX = 0f
    private var dragPointerAnchorY = 0f

    fun triggerRipple(x: Float, y: Float) {
        rippleCenterX.floatValue = x
        rippleCenterY.floatValue = y
        rippleStartTimeMs.value = System.currentTimeMillis()
        rippleActive.value = true
    }

    fun clearRippleIfExpired(nowMs: Long, durationMs: Long = FLOATING_POINTER_RIPPLE_DURATION_MS) {
        if (!rippleActive.value) return
        if (nowMs - rippleStartTimeMs.value >= durationMs) {
            rippleActive.value = false
        }
    }

    fun placeJoystickDefault() {
        val margin = 32f * density
        val radius = joystickRadiusPx()
        joystickCenterX.floatValue = margin + radius
        joystickCenterY.floatValue = screenHeight - margin - radius
    }

    fun placeAtTouch(rawX: Float, rawY: Float, settings: AppSettings) {
        val center = clampJoystickCenter(rawX, rawY, settings)
        joystickCenterX.floatValue = center.x
        joystickCenterY.floatValue = center.y
        gestureCenterX = center.x
        gestureCenterY = center.y
        establishGestureArea(settings)
        val pointer = FloatingPointerBounds.pointerForFingerInArea(
            fingerX = rawX,
            fingerY = rawY,
            areaLeft = gestureAreaLeft,
            areaTop = gestureAreaTop,
            areaWidth = gestureAreaWidth,
            areaHeight = gestureAreaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        pointerX.floatValue = pointer.x
        pointerY.floatValue = pointer.y
        awaitingPlacement = false
        armDrag(rawX, rawY)
    }

    /** Arms a new drag without moving the pointer or re-anchoring the joystick area. */
    fun beginGesture(rawX: Float, rawY: Float, settings: AppSettings) {
        if (awaitingPlacement || !hasEstablishedGestureArea()) {
            placeAtTouch(rawX, rawY, settings)
        } else {
            armDrag(rawX, rawY)
        }
    }

    private fun armDrag(fingerX: Float, fingerY: Float) {
        dragFingerAnchorX = fingerX
        dragFingerAnchorY = fingerY
        dragPointerAnchorX = pointerX.floatValue
        dragPointerAnchorY = pointerY.floatValue
    }

    fun hasEstablishedGestureArea(): Boolean =
        gestureAreaWidth > 0f && gestureAreaHeight > 0f

    private fun establishGestureArea(settings: AppSettings) {
        val (areaWidth, areaHeight) = FloatingPointerBounds.effectiveJoystickAreaSize(
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        gestureAreaWidth = areaWidth
        gestureAreaHeight = areaHeight
        gestureAreaLeft = gestureCenterX - areaWidth / 2f
        gestureAreaTop = gestureCenterY - areaHeight / 2f
    }

    fun refreshGestureArea(settings: AppSettings) {
        if (!hasEstablishedGestureArea()) return
        establishGestureArea(settings)
    }

    private fun clampJoystickCenter(rawX: Float, rawY: Float, settings: AppSettings): Offset {
        val (areaWidth, areaHeight) = FloatingPointerBounds.effectiveJoystickAreaSize(
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        return FloatingPointerBounds.clampJoystickCenter(
            rawX = rawX,
            rawY = rawY,
            joystickRadiusPx = joystickRadiusPx(),
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
    }

    fun applyLayoutSettings(settings: AppSettings) {
        val next = FloatingPointerBounds.clamp(
            position = Offset(pointerX.floatValue, pointerY.floatValue),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        pointerX.floatValue = next.x
        pointerY.floatValue = next.y
        refreshGestureArea(settings)
    }

    fun clearTrail() {
        trailPoints.clear()
    }

    fun pruneExpiredTrailPoints(nowMs: Long = System.currentTimeMillis()) {
        val maxAge = settingsSource().floatingPointerTrailDurationMs.coerceAtLeast(50)
        val cutoff = nowMs - maxAge
        while (trailPoints.isNotEmpty() && trailPoints.first().timeMs < cutoff) {
            trailPoints.removeAt(0)
        }
    }

    fun hasActiveTrail(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (trailPoints.size < 2) return false
        val maxAge = settingsSource().floatingPointerTrailDurationMs.coerceAtLeast(50)
        return trailPoints.count { nowMs - it.timeMs <= maxAge } >= 2
    }

    fun isNearPointer(rawX: Float, rawY: Float): Boolean {
        val hitRadius = settingsSource().floatingPointerPointerDiameterPx / 2f + tapSlopPx
        return kotlin.math.hypot(
            (rawX - pointerX.floatValue).toDouble(),
            (rawY - pointerY.floatValue).toDouble(),
        ) <= hitRadius
    }

    private fun recordTrail(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        trailPoints.add(FloatingPointerTrailPoint(x, y, now))
        val maxAge = settingsSource().floatingPointerTrailDurationMs.coerceAtLeast(50)
        while (trailPoints.size > 96) {
            trailPoints.removeAt(0)
        }
        val cutoff = now - maxAge
        while (trailPoints.isNotEmpty() && trailPoints.first().timeMs < cutoff) {
            trailPoints.removeAt(0)
        }
    }

    fun applyPointerFromTouch(rawX: Float, rawY: Float, @Suppress("UNUSED_PARAMETER") settings: AppSettings) {
        if (gestureAreaWidth <= 0f || gestureAreaHeight <= 0f) return
        val next = FloatingPointerBounds.pointerForFingerDeltaInArea(
            deltaX = rawX - dragFingerAnchorX,
            deltaY = rawY - dragFingerAnchorY,
            areaWidth = gestureAreaWidth,
            areaHeight = gestureAreaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pointerAnchorX = dragPointerAnchorX,
            pointerAnchorY = dragPointerAnchorY,
        )
        pointerX.floatValue = next.x
        pointerY.floatValue = next.y
        recordTrail(next.x, next.y)
    }
}

internal object FloatingPointerBounds {
    /** Linear mapping (1.0) keeps pointer speed proportional to finger speed at all positions. */
    const val EDGE_CURVE_POWER_X = 1.0f
    const val EDGE_CURVE_POWER_Y = 1.0f

    fun pointerForFingerInArea(
        fingerX: Float,
        fingerY: Float,
        areaLeft: Float,
        areaTop: Float,
        areaWidth: Float,
        areaHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        curvePowerX: Float = EDGE_CURVE_POWER_X,
        curvePowerY: Float = EDGE_CURVE_POWER_Y,
    ): Offset {
        val normX = if (areaWidth > 0f) {
            ((fingerX - areaLeft) / areaWidth).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val normY = if (areaHeight > 0f) {
            ((fingerY - areaTop) / areaHeight).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        return Offset(
            x = mapTravel(0f, normX, 0f, screenWidth, curvePowerX),
            y = mapTravel(0f, normY, 0f, screenHeight, curvePowerY),
        )
    }

    /** Maps finger travel since touch-down to pointer movement from the pointer position at down. */
    fun pointerForFingerDeltaInArea(
        deltaX: Float,
        deltaY: Float,
        areaWidth: Float,
        areaHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        pointerAnchorX: Float,
        pointerAnchorY: Float,
    ): Offset {
        val normDeltaX = if (areaWidth > 0f) deltaX / areaWidth else 0f
        val normDeltaY = if (areaHeight > 0f) deltaY / areaHeight else 0f
        return Offset(
            x = (pointerAnchorX + normDeltaX * screenWidth).coerceIn(0f, screenWidth),
            y = (pointerAnchorY + normDeltaY * screenHeight).coerceIn(0f, screenHeight),
        )
    }

    fun effectiveJoystickAreaSize(
        settings: AppSettings,
        screenWidth: Float,
        screenHeight: Float,
    ): Pair<Float, Float> {
        val zoom = settings.floatingPointerJoystickAreaZoomFraction.coerceIn(0.1f, 1f)
        val width = settings.floatingPointerJoystickAreaWidthPx.coerceIn(120f, 800f) * zoom
        val height = if (settings.floatingPointerMatchJoystickToScreenAspect && screenWidth > 0f) {
            width * (screenHeight / screenWidth)
        } else {
            settings.floatingPointerJoystickAreaHeightPx.coerceIn(120f, 1400f) * zoom
        }
        return width to height
    }

    fun clampJoystickCenter(
        rawX: Float,
        rawY: Float,
        joystickRadiusPx: Float,
        areaWidth: Float,
        areaHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
    ): Offset {
        val margin = 16f * density
        val insetX = maxOf(joystickRadiusPx, areaWidth / 2f) + margin
        val insetY = maxOf(joystickRadiusPx, areaHeight / 2f) + margin
        val x = if (insetX * 2f > screenWidth) {
            screenWidth / 2f
        } else {
            rawX.coerceIn(insetX, screenWidth - insetX)
        }
        val y = if (insetY * 2f > screenHeight) {
            screenHeight / 2f
        } else {
            rawY.coerceIn(insetY, screenHeight - insetY)
        }
        return Offset(x, y)
    }

    internal data class AreaPreviewLayout(
        val trigger: Offset,
        val joystickCenter: Offset,
        val joystickRadiusPx: Float,
        val areaWidth: Float,
        val areaHeight: Float,
        val areaRect: Rect,
        val areaRectOnScreen: Rect,
        val pointerPosition: Offset,
    )

    fun computeAreaPreviewLayout(
        settings: AppSettings,
        density: Float,
        screenWidth: Float,
        screenHeight: Float,
        triggerRawX: Float,
        triggerRawY: Float,
    ): AreaPreviewLayout {
        val joystickRadiusPx = settings.floatingPointerJoystickDiameterPx / 2f
        val (areaWidth, areaHeight) = effectiveJoystickAreaSize(settings, screenWidth, screenHeight)
        val joystickCenter = clampJoystickCenter(
            rawX = triggerRawX,
            rawY = triggerRawY,
            joystickRadiusPx = joystickRadiusPx,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
        val areaLeft = joystickCenter.x - areaWidth / 2f
        val areaTop = joystickCenter.y - areaHeight / 2f
        val areaRect = Rect(
            left = areaLeft,
            top = areaTop,
            right = areaLeft + areaWidth,
            bottom = areaTop + areaHeight,
        )
        val screenRect = Rect(0f, 0f, screenWidth, screenHeight)
        val pointerPosition = pointerForFingerInArea(
            fingerX = triggerRawX,
            fingerY = triggerRawY,
            areaLeft = areaLeft,
            areaTop = areaTop,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        return AreaPreviewLayout(
            trigger = Offset(triggerRawX, triggerRawY),
            joystickCenter = joystickCenter,
            joystickRadiusPx = joystickRadiusPx,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            areaRect = areaRect,
            areaRectOnScreen = areaRect.intersect(screenRect),
            pointerPosition = pointerPosition,
        )
    }

    fun mapTravel(
        start: Float,
        normalized: Float,
        min: Float,
        max: Float,
        curvePower: Float,
    ): Float {
        val curved = applyDeflectionCurve(normalized, curvePower)
        return when {
            normalized < 0f -> (start + curved * (start - min)).coerceIn(min, max)
            normalized > 0f -> (start + curved * (max - start)).coerceIn(min, max)
            else -> start.coerceIn(min, max)
        }
    }

    private fun applyDeflectionCurve(normalized: Float, curvePower: Float): Float {
        val sign = if (normalized < 0f) -1f else 1f
        val magnitude = kotlin.math.abs(normalized).coerceIn(0f, 1f)
        return sign * magnitude.pow(curvePower)
    }

    fun clamp(
        position: Offset,
        screenWidth: Float,
        screenHeight: Float,
    ): Offset {
        return Offset(
            x = position.x.coerceIn(0f, screenWidth),
            y = position.y.coerceIn(0f, screenHeight),
        )
    }
}

@Composable
private fun FloatingPointerDisplay(
    session: FloatingPointerSession,
    settings: AppSettings,
    visible: Boolean,
) {
    SlideIndexTheme(
        seedColor = Color(settings.themeColorArgb),
        dynamicColor = settings.dynamicColorEnabled,
    ) {
        if (!visible) return@SlideIndexTheme

        val pointerX by session.pointerX
        val pointerY by session.pointerY
        val joystickX by session.joystickCenterX
        val joystickY by session.joystickCenterY
        val joystickActive by session.joystickActive
        val pointerVisible by session.pointerVisible
        val radialMenuActive by session.radialMenuActive
        val radialHighlightedSlot by session.radialHighlightedSlot
        val rippleActive by session.rippleActive
        val showPointer = (!settings.floatingPointerHideWhenJoystickReleased || pointerVisible) &&
            !session.awaitingPlacement
        var animationTick by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { frameTime ->
                    val now = System.currentTimeMillis()
                    session.clearRippleIfExpired(now)
                    session.pruneExpiredTrailPoints(now)
                    if (session.hasActiveTrail(now) || session.rippleActive.value) {
                        animationTick = frameTime
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            // Subscribe to per-frame invalidation while trail or ripple is animating.
            animationTick
            Canvas(Modifier.fillMaxSize()) {
                val now = System.currentTimeMillis()
                drawFloatingPointerTrail(session.trailPoints, settings, now)
                if (showPointer) {
                    drawQcRingPointer(
                        center = Offset(pointerX, pointerY),
                        diameterPx = settings.floatingPointerPointerDiameterPx,
                        ringThicknessPx = settings.floatingPointerRingThicknessPx,
                        dotDiameterPx = settings.floatingPointerDotDiameterPx,
                        ringColor = Color(settings.floatingPointerRingColorArgb),
                        fillColor = Color(settings.floatingPointerFillColorArgb),
                        dotColor = Color(settings.floatingPointerDotColorArgb),
                    )
                }
                if (rippleActive) {
                    val elapsed = now - session.rippleStartTimeMs.value
                    drawFloatingPointerRipple(
                        center = Offset(session.rippleCenterX.floatValue, session.rippleCenterY.floatValue),
                        elapsedMs = elapsed,
                        ringColor = Color(settings.floatingPointerRingColorArgb),
                        pointerDiameterPx = settings.floatingPointerPointerDiameterPx,
                    )
                }
                if (!session.awaitingPlacement || joystickActive || radialMenuActive) {
                    drawQcJoystickDisc(
                        center = Offset(joystickX, joystickY),
                        radiusPx = session.joystickRadiusPx(),
                        innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
                        outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
                        gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
                        pressed = joystickActive && !radialMenuActive,
                    )
                }
                if (radialMenuActive) {
                    drawFloatingPointerRadialMenu(
                        center = Offset(session.radialMenuCenterX, session.radialMenuCenterY),
                        settings = settings,
                        slots = settings.floatingPointerRadialSlotActions,
                        highlightedSlot = radialHighlightedSlot,
                    )
                }
            }
        }
    }
}
