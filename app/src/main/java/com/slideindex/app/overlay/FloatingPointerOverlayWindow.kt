package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.InputTapUtil
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Floating pointer with QC-inspired visuals:
 * - Display layer (NOT_TOUCHABLE): pointer + joystick artwork.
 * - Touch layer: passthrough everywhere except the joystick disc; uses raw screen coords (no jitter).
 */
@SuppressLint("StaticFieldLeak") // Overlay singleton; ActionExecutor/views cleared when window hides
object FloatingPointerOverlayWindow {
    internal val mainHandler = Handler(Looper.getMainLooper())
    internal val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal val windowLifecycle by lazy { FloatingPointerWindowLifecycle(this) }
    internal val settingsSync by lazy { FloatingPointerSettingsSync(this) }

    internal var windowManager: WindowManager? = null
    internal var displayView: ComposeView? = null
    internal var touchHost: FloatingPointerHostLayout? = null
    internal var displayOwner: OverlayComposeOwner? = null
    internal var visibleState: MutableState<Boolean>? = null
    internal var settingsState: MutableState<AppSettings>? = null
    internal var session: FloatingPointerSession? = null
    internal var touchLayoutParams: WindowManager.LayoutParams? = null
    internal var screenOffReceiver: BroadcastReceiver? = null
    internal var appContext: Context? = null
    internal var settingsCollectJob: Job? = null
    internal var idleHideRunnable: Runnable? = null
    internal var outsideDismissGraceRunnable: Runnable? = null
    internal var outsideDismissSuppressed = false
    internal var touchCaptureUserCollapsed = false
    internal var actionExecutor: ActionExecutor? = null
    internal var isPointerTapInFlight = false
    internal var pointerTapOutsideSuppressUntilMs = 0L
    internal val pendingPointerTaps = ArrayDeque<PendingPointerTap>()
    internal var pendingPointerSwipeRunnable: Runnable? = null
    internal var isPointerSwipeInFlight = false
    internal var continuedGestureActive = false
    internal var pendingCleanupRunnable: Runnable? = null

    internal data class PendingPointerTap(
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
        windowLifecycle.show(context, settings, anchorRawX, anchorRawY, continueTouch)
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

    internal fun onTouchCycleComplete() = windowLifecycle.onTouchCycleComplete()

    internal fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean =
        windowLifecycle.shouldDismissOnOutsideTouch(event)

    fun toggle(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float? = null,
        anchorRawY: Float? = null,
        continueTouch: Boolean = false,
    ) {
        windowLifecycle.toggle(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun dismiss() {
        windowLifecycle.dismiss()
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

    internal fun executeRadialSlotAction(slotIndex: Int) {
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

    internal fun runQuickSwipeDismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runQuickSwipeDismiss() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) return
        session?.clearTrail()
        windowLifecycle.setTouchOverlayPassthrough(true)
        visible.value = false
        windowLifecycle.scheduleDismissCleanup()
    }

    internal fun runOutsideDismissPrepare() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runOutsideDismissPrepare() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) return
        session?.clearTrail()
        windowLifecycle.setTouchOverlayPassthrough(true)
        visible.value = false
        windowLifecycle.scheduleDismissCleanup()
    }

    internal fun runPointerTap(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runPointerTap(rawX, rawY) }
            return
        }
        enqueuePointerInjection(rawX, rawY)
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
        windowLifecycle.setTouchOverlayPassthrough(true)
        markPointerTapOutsideSuppress()
        Log.i(TAG, "injectPointerSwipe start ($startX, $startY) config=$config")
        InputTapUtil.dispatchPointerSwipeAsync(startX, startY, config) { ok ->
            Log.i(TAG, "injectPointerSwipe finished ok=$ok at ($startX, $startY)")
            onPointerSwipeComplete()
        }
    }

    private fun onPointerSwipeComplete() {
        isPointerSwipeInFlight = false
        windowLifecycle.setTouchOverlayPassthrough(false)
        extendPointerTapOutsideSuppressAfterComplete()
    }

    private fun onHaptic(settings: AppSettings) {
        displayView?.let { HapticHelper.appTick(it, settings) }
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

    private const val TAG = "FloatingPointerOverlay"
    /** Covers injected tap + in-flight ACTION_OUTSIDE echo (coords are zeroed). */
    private const val POINTER_TAP_OUTSIDE_SUPPRESS_MS = 500L
    /** ACTION_OUTSIDE can arrive after dispatchGesture/onFinished returns. */
    private const val POINTER_TAP_OUTSIDE_ECHO_AFTER_COMPLETE_MS = 350L
    private const val RADIAL_ACTION_INJECT_DELAY_MS = 120L
}
