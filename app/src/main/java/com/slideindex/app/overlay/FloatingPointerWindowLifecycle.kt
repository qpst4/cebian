package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal class FloatingPointerWindowLifecycle(
    private val window: FloatingPointerOverlayWindow,
) {
    private val mainHandler get() = window.mainHandler

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        continueTouch: Boolean,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { window.show(context, settings, anchorRawX, anchorRawY, continueTouch) }
            return
        }
        if (window.isShowing) {
            if (window.isVisible) {
                window.settingsState?.value = settings
                OverlayPerformanceMonitorBinding.syncUserPreference(settings, window.appContext)
                window.settingsSync.onSettingsUpdated(settings)
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
            joystickPositionChanged = { centerX, centerY ->
                collapseTouchCapture(centerX, centerY, forceCollapse = true)
            },
            gestureEnd = { centerX, centerY, _ ->
                window.touchCaptureUserCollapsed = true
                collapseTouchCapture(centerX, centerY, forceCollapse = true)
            },
            pointerClick = { rawX, rawY -> window.runPointerTap(rawX, rawY) },
            outsideDismissPrepare = { window.runOutsideDismissPrepare() },
            quickSwipeDismiss = { window.runQuickSwipeDismiss() },
            dismiss = { window.dismiss() },
            radialMenuOpened = { expandTouchCapture() },
            radialMenuClosed = {
                window.session?.let {
                    collapseTouchCapture(it.joystickCenterX.floatValue, it.joystickCenterY.floatValue, forceCollapse = true)
                }
            },
            radialMenuAction = { slotIndex -> window.executeRadialSlotAction(slotIndex) },
            activity = { window.settingsSync.resetIdleTimer() },
            haptic = { displayCompose.let { HapticHelper.appTick(it, settingsHolder.value) } },
            dismissOnOutsideTouch = window::shouldDismissOnOutsideTouch,
            touchCycleComplete = { window.onTouchCycleComplete() },
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

        window.windowManager = wm
        window.displayView = displayCompose
        window.touchHost = touchLayout
        window.displayOwner = displayDialogOwner
        window.visibleState = visible
        window.settingsState = settingsHolder
        window.session = pointerSession
        window.touchLayoutParams = touchParams
        window.appContext = hostContext
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
            ?: run {
                Log.w(TAG, "show: accessibility service deps unavailable")
                runCatching { wm.removeView(displayCompose) }
                displayDialogOwner.destroy()
                return
            }
        window.actionExecutor = ActionExecutor(
            context = hostContext,
            appRepository = deps.appRepository,
            clickPassthroughHandler = null,
            overlayBrightness = null,
            side = PanelSide.LEFT,
            onShellCommandsPersist = { commands ->
                window.overlayScope.launch {
                    deps.settingsRepository.setShellCommands(commands)
                }
            },
        )
        registerScreenOffReceiver(hostContext)
        OverlayPerformanceMonitorBinding.onOverlayShown(settings, hostContext)
        window.settingsSync.startSettingsSync(deps, settingsHolder)
        window.settingsSync.resetIdleTimer()
        beginOutsideDismissGrace()

        val anchorX = anchorRawX
        val anchorY = anchorRawY
        val shouldContinueTouch = continueTouch && anchorX != null && anchorY != null
        window.continuedGestureActive = shouldContinueTouch
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

    fun toggle(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        continueTouch: Boolean,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { window.toggle(context, settings, anchorRawX, anchorRawY, continueTouch) }
            return
        }
        if (window.isShowing && window.isVisible) {
            if (anchorRawX != null && anchorRawY != null) {
                resummonAtAnchor(context, settings, anchorRawX, anchorRawY, continueTouch)
            } else {
                dismiss()
            }
            return
        }
        show(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { window.dismiss() }
            return
        }
        val visible = window.visibleState ?: return
        if (!visible.value) {
            cleanup()
            return
        }
        window.session?.clearTrail()
        window.session?.let {
            collapseTouchCapture(
                it.joystickCenterX.floatValue,
                it.joystickCenterY.floatValue,
                forceCollapse = true,
            )
        }
        visible.value = false
        scheduleCleanup()
    }

    fun cleanup() {
        OverlayPerformanceMonitorBinding.onOverlayHidden(window.appContext)
        cancelPendingCleanup()
        window.settingsSync.cancelSettingsSync()
        window.settingsSync.cancelIdleTimer()
        window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        window.outsideDismissGraceRunnable = null
        window.outsideDismissSuppressed = false
        val wm = window.windowManager
        window.displayView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        window.touchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        window.screenOffReceiver?.let { receiver ->
            window.appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        OverlayCompose.disposeComposeView(window.displayView)
        window.displayOwner?.destroy()
        window.displayOwner = null
        window.displayView = null
        window.touchHost = null
        window.windowManager = null
        window.visibleState = null
        window.settingsState = null
        window.session = null
        window.touchLayoutParams = null
        window.screenOffReceiver = null
        window.appContext = null
        window.actionExecutor = null
        window.touchCaptureUserCollapsed = false
        window.isPointerTapInFlight = false
        window.pointerTapOutsideSuppressUntilMs = 0L
        window.pendingPointerTaps.clear()
        window.pendingPointerSwipeRunnable?.let { mainHandler.removeCallbacks(it) }
        window.pendingPointerSwipeRunnable = null
        window.isPointerSwipeInFlight = false
        window.continuedGestureActive = false
    }

    fun collapseTouchCapture(
        centerX: Float,
        centerY: Float,
        @Suppress("UNUSED_PARAMETER") forceCollapse: Boolean = false,
    ) {
        val pointerSession = window.session ?: return
        if (pointerSession.radialMenuActive.value) return
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
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

    fun expandTouchCapture() {
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
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

    fun setTouchOverlayPassthrough(passthrough: Boolean) {
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "setTouchOverlayPassthrough failed", it) }
    }

    fun beginOutsideDismissGrace() {
        window.outsideDismissSuppressed = true
        window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            window.outsideDismissGraceRunnable = null
            window.outsideDismissSuppressed = false
        }
        window.outsideDismissGraceRunnable = runnable
        mainHandler.postDelayed(runnable, OUTSIDE_DISMISS_GRACE_MS)
    }

    fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean {
        if (window.outsideDismissSuppressed) return false
        if (window.isPointerTapInFlight) return false
        if (window.pendingPointerTaps.isNotEmpty()) return false
        if (SystemClock.uptimeMillis() < window.pointerTapOutsideSuppressUntilMs) return false
        val pointerSession = window.session ?: return true
        if (hasReliableOutsideTouchCoordinates(event) &&
            pointerSession.isNearPointer(event.rawX, event.rawY)
        ) {
            return false
        }
        return true
    }

    fun onTouchCycleComplete() {
        if (!window.outsideDismissSuppressed) return
        window.outsideDismissSuppressed = false
        window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        window.outsideDismissGraceRunnable = null
    }

    private fun resummonAtAnchor(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        continueTouch: Boolean,
    ) {
        cancelPendingCleanup()
        window.settingsSync.cancelIdleTimer()
        window.continuedGestureActive = false
        window.outsideDismissSuppressed = false
        window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        window.outsideDismissGraceRunnable = null
        window.visibleState?.value = false
        cleanup()
        show(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun scheduleDismissCleanup() {
        scheduleCleanup()
    }

    private fun scheduleCleanup() {
        cancelPendingCleanup()
        val runnable = Runnable {
            window.pendingCleanupRunnable = null
            cleanup()
        }
        window.pendingCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, FLOATING_POINTER_PRESENCE_ANIMATION_MS)
    }

    private fun cancelPendingCleanup() {
        window.pendingCleanupRunnable?.let { mainHandler.removeCallbacks(it) }
        window.pendingCleanupRunnable = null
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        window.screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
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
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun hasReliableOutsideTouchCoordinates(event: MotionEvent): Boolean {
        return event.rawX != 0f || event.rawY != 0f
    }

    private fun overlayScreenBounds(wm: WindowManager, fallback: DisplayMetrics): OverlayScreenBounds {
        val bounds = wm.currentWindowMetrics.bounds
        return OverlayScreenBounds(
            width = bounds.width().toFloat(),
            height = bounds.height().toFloat(),
        )
    }

    companion object {
        private const val TAG = "FloatingPointerOverlay"
        private const val OUTSIDE_DISMISS_GRACE_MS = 450L
    }
}
