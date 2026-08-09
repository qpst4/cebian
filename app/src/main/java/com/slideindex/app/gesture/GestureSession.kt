package com.slideindex.app.gesture

import com.slideindex.app.overlay.OverlayPanelMode
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.resolvedTriggerMode
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.util.ContinuousAdjustController

class GestureSession(
    private val side: PanelSide,
    private val zoneLayout: GestureZoneLayout,
    private val indexSession: SlideAlongRailSession,
    private val pathRecognizer: SwipePathRecognizer,
    private val actionExecutor: ActionExecutor,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onSessionStart(mode: OverlayPanelMode)
        fun onLeaveOpenFingerTrackingFinished() {}
        fun onOpenShellCommandPanel(continuousPick: Boolean)
        fun onShellCommandPanelContinuousRelease()
        fun onShowHoneycombLauncher(continuousPick: Boolean, rawX: Float, rawY: Float)
        fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float)
        fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float)
        fun onShowAdjustPanel(
            mode: ContinuousAdjustController.Mode,
            fraction: Float,
            anchorRawY: Float,
            deferWindowLayout: Boolean = false,
        )
        fun onSessionEnd()
        fun onRequestInvalidate()
        fun hapticGestureStart()
        fun hapticLongThreshold()
        fun hapticConfirmLaunch()
        fun scheduleDelayed(runnable: Runnable, delayMs: Long)
        fun cancelDelayed(runnable: Runnable)
    }

    internal var sessionSettings = AppSettings()
    internal val sessionSide: PanelSide get() = side
    internal val sessionIndexSession: SlideAlongRailSession get() = indexSession
    internal val sessionPathRecognizer: SwipePathRecognizer get() = pathRecognizer
    internal val sessionActionExecutor: ActionExecutor get() = actionExecutor
    internal val sessionCallbacks: Callbacks get() = callbacks
    internal val sessionContinuousPick = GestureSessionContinuousPick()
    internal val sessionLongPressCheckRunnable: Runnable get() = longPressCheckRunnable

    private var active = false
    internal var sessionPanelMode = OverlayPanelMode.NONE
    internal var sessionIndexMode = false
    internal var sessionAdjustMode: ContinuousAdjustController.Mode? = null
    internal var sessionAdjustLayoutAnchorRawY = 0f
    internal var sessionMoveTimeActionFired = false
    internal var sessionActiveHandleId = TriggerHandle.DEFAULT_ID
    internal var sessionQuickLauncherPanelId: String = ""

    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastLocalX = 0f
    private var lastLocalY = 0f

    private val thresholdTracker: GestureSessionThresholdTracker
    private val longPressCheckRunnable: Runnable

    init {
        thresholdTracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = callbacks,
            cancelLongPressCheck = { callbacks.cancelDelayed(longPressCheckRunnable) },
        )
        longPressCheckRunnable = Runnable {
            if (!active || sessionPanelMode != OverlayPanelMode.NONE) return@Runnable
            thresholdTracker.maybeHapticLongPress(lastRawX, lastRawY)
            if (!pathRecognizer.isLongPressArmed()) return@Runnable
            val classification = pathRecognizer.classifyPartial(lastRawX, lastRawY, classifyOptions()) ?: return@Runnable
            when (sessionSettings.resolvedTriggerMode(side, classification.trigger, sessionActiveHandleId)) {
                GestureTriggerMode.IMMEDIATE -> {
                    if (!sessionMoveTimeActionFired &&
                        pathRecognizer.hasMetThreshold(classification.trigger, lastRawX, lastRawY)
                    ) {
                        dispatchMoveTimeGesture(
                            classification,
                            lastRawX,
                            lastRawY,
                            lastLocalX,
                            lastLocalY,
                        )
                    }
                }
                GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT, GestureTriggerMode.CONTINUOUS -> Unit
            }
        }
    }

    fun applySettings(newSettings: AppSettings) {
        sessionSettings = newSettings
        indexSession.applySettings(newSettings)
        pathRecognizer.applyAngles(newSettings.gestureAngles)
        applyActiveHandleDistances()
    }

    private fun applyActiveHandleDistances() {
        val handle = sessionSettings.triggerHandle(side, sessionActiveHandleId)
            ?: sessionSettings.primaryTriggerHandle(side)
        pathRecognizer.applyDistances(handle.shortSwipeDistanceDp, handle.longSwipeDistanceDp)
    }

    fun isActive(): Boolean = active

    fun panelMode(): OverlayPanelMode = sessionPanelMode

    fun isAdjustMode(): Boolean = sessionAdjustMode != null

    fun adjustModeOrNull(): ContinuousAdjustController.Mode? = sessionAdjustMode

    /** True after an IMMEDIATE action fired on this touch; blocks further in-gesture tracking. */
    fun isMoveTimeActionLocked(): Boolean = sessionMoveTimeActionFired

    /** Ends the initiating IMMEDIATE swipe while keeping the opened panel active. */
    fun releaseImmediateGestureLock(): Boolean {
        if (!sessionMoveTimeActionFired) return false
        sessionMoveTimeActionFired = false
        // leave-open 面板保留 sessionPanelMode，但手指手势必须结束，否则 scene/触摸态会悬空。
        active = false
        callbacks.cancelDelayed(longPressCheckRunnable)
        callbacks.onRequestInvalidate()
        return true
    }

    /**
     * Clears finger-tracking for leave-open panels (QL / TaskSwitcher / Shell) while keeping
     * [sessionPanelMode]. No-op while continuous-pick is still active.
     */
    fun finishLeaveOpenFingerTracking() {
        when (sessionPanelMode) {
            OverlayPanelMode.QUICK_LAUNCHER,
            OverlayPanelMode.TASK_SWITCHER,
            OverlayPanelMode.SHELL_COMMANDS,
            -> {
                if (sessionContinuousPick.quickLauncherActive() ||
                    sessionContinuousPick.taskSwitcherActive() ||
                    sessionContinuousPick.shellActive()
                ) {
                    return
                }
                if (!active && !sessionMoveTimeActionFired) return
                active = false
                sessionMoveTimeActionFired = false
                callbacks.cancelDelayed(longPressCheckRunnable)
                callbacks.onRequestInvalidate()
                callbacks.onLeaveOpenFingerTrackingFinished()
            }
            else -> Unit
        }
    }

    fun taskSwitcherContinuousPickActive(): Boolean = sessionContinuousPick.taskSwitcherActive()

    fun quickLauncherPanelId(): String = sessionQuickLauncherPanelId

    fun quickLauncherContinuousPickActive(): Boolean = sessionContinuousPick.quickLauncherActive()

    fun clearQuickLauncherContinuousPick() {
        sessionContinuousPick.clearQuickLauncher()
    }

    fun shellCommandContinuousPickActive(): Boolean = sessionContinuousPick.shellActive()

    fun clearShellContinuousPick() {
        sessionContinuousPick.clearShell()
    }

    fun honeycombContinuousPickActive(): Boolean = sessionContinuousPick.honeycombActive()

    fun clearHoneycombContinuousPick() {
        sessionContinuousPick.clearHoneycomb()
    }

    fun adjustAnchorRawY(): Float = sessionAdjustLayoutAnchorRawY

    fun activeHandleId(): String = sessionActiveHandleId

    fun onTouchDown(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {
        if (active) return false
        if (sessionSettings.triggerHandles(side).isEmpty()) return false
        if (!zoneLayout.containsTriggerAtScreen(rawX, rawY, localX, localY)) return false

        val matchedHandleId = zoneLayout.findTriggerHandleAtScreen(rawX, rawY)
            ?: zoneLayout.findTriggerHandleAt(localX, localY)
            ?: return false

        sessionActiveHandleId = matchedHandleId

        applyActiveHandleDistances()

        active = true
        sessionIndexMode = false
        sessionAdjustMode = null
        sessionAdjustLayoutAnchorRawY = 0f
        sessionPanelMode = OverlayPanelMode.NONE
        sessionMoveTimeActionFired = false
        thresholdTracker.reset()
        sessionContinuousPick.reset()

        lastRawX = rawX
        lastRawY = rawY
        lastLocalX = localX
        lastLocalY = localY

        OverlayService.captureGestureForegroundPackage()
        val stripBounds = zoneLayout.screenTriggerStripBounds(sessionActiveHandleId)
        pathRecognizer.onTouchDown(rawX, rawY, stripBounds)
        callbacks.scheduleDelayed(longPressCheckRunnable, SwipePathRecognizer.LONG_PRESS_MS)
        return true
    }

    fun onTouchMove(rawX: Float, rawY: Float, localX: Float, localY: Float) {
        if (!active) return

        lastRawX = rawX
        lastRawY = rawY
        lastLocalX = localX
        lastLocalY = localY

        val currentAdjustMode = sessionAdjustMode
        if (currentAdjustMode != null) {
            actionExecutor.updateContinuousAdjust(currentAdjustMode, rawY)
            callbacks.onRequestInvalidate()
            return
        }

        if (sessionContinuousPick.honeycombActive()) {
            callbacks.onHoneycombLauncherPointerMove(rawX, rawY)
            return
        }

        if (sessionPanelMode != OverlayPanelMode.NONE) {
            if (sessionIndexMode && indexSession.updateSelection(localX, localY)) {
                callbacks.onRequestInvalidate()
            }
            return
        }

        if (sessionMoveTimeActionFired) return

        pathRecognizer.onTouchMove(rawX, rawY)

        if (!pathRecognizer.longPressEligible()) {
            callbacks.cancelDelayed(longPressCheckRunnable)
        }

        thresholdTracker.maybeHapticLongPress(rawX, rawY)
        thresholdTracker.trackDistanceHaptics(rawX, rawY)

        val classification = pathRecognizer.classifyPartial(rawX, rawY, classifyOptions()) ?: return

        when (sessionSettings.resolvedTriggerMode(side, classification.trigger, sessionActiveHandleId)) {
            GestureTriggerMode.IMMEDIATE -> {
                if (!sessionMoveTimeActionFired &&
                    pathRecognizer.hasMetThreshold(classification.trigger, rawX, rawY)
                ) {
                    dispatchMoveTimeGesture(classification, rawX, rawY, localX, localY)
                }
            }
            GestureTriggerMode.CONTINUOUS -> {
                trackContinuousGesture(classification, rawX, rawY, localX, localY)
            }
            GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> Unit
        }
    }

    fun onTouchUp(rawX: Float, rawY: Float, localX: Float, localY: Float) {
        if (!active) return

        when (sessionPanelMode) {
            OverlayPanelMode.INDEX -> {
                if (releaseImmediateGestureLock()) return

                val app = indexSession.highlightedApp
                val longPressArmed = indexSession.longPressArmed
                endSession()
                app?.let {
                    callbacks.hapticConfirmLaunch()
                    actionExecutor.execute(
                        GestureAction.LaunchApp(it.packageName),
                        sessionSettings,
                        longPressArmed,
                    )
                }
            }

            OverlayPanelMode.QUICK_LAUNCHER, OverlayPanelMode.TASK_SWITCHER,
            OverlayPanelMode.SHELL_COMMANDS -> finishLeaveOpenFingerTracking()

            OverlayPanelMode.NONE -> {
                if (sessionContinuousPick.honeycombActive()) {
                    sessionContinuousPick.clearHoneycomb()
                    callbacks.onHoneycombLauncherContinuousRelease(rawX, rawY)
                    endSession()
                    return
                }

                if (sessionContinuousPick.shell) {
                    sessionContinuousPick.clearShell()
                    callbacks.onShellCommandPanelContinuousRelease()
                    endSession()
                    return
                }

                if (sessionAdjustMode != null) {
                    val mode = sessionAdjustMode!!
                    val anchorRawY = sessionAdjustLayoutAnchorRawY
                    val previewFraction = actionExecutor.adjustFraction()
                    actionExecutor.endContinuousAdjust()
                    val fraction = if (mode == ContinuousAdjustController.Mode.BRIGHTNESS) {
                        previewFraction
                    } else {
                        actionExecutor.readCurrentAdjustFraction(mode)
                    }
                    callbacks.onShowAdjustPanel(mode, fraction, anchorRawY)
                    endSession()
                    return
                }

                if (sessionMoveTimeActionFired) {
                    endSession()
                    return
                }

                thresholdTracker.maybeHapticLongPress(rawX, rawY)

                val gestureStartRawY = pathRecognizer.gestureStartRawY()
                val classification = pathRecognizer.classifyOnUp(rawX, rawY, classifyOptions()) ?: run {
                    endSession()
                    return
                }

                val mode = sessionSettings.resolvedTriggerMode(side, classification.trigger, sessionActiveHandleId)
                if (mode == GestureTriggerMode.IMMEDIATE) {
                    endSession()
                    return
                }

                val action = sessionSettings.actionFor(side, classification.trigger, sessionActiveHandleId)
                if (mode == GestureTriggerMode.CONTINUOUS && !sessionIndexMode &&
                    (sessionMoveTimeActionFired || action.supportsContinuousTracking(classification.trigger))
                ) {
                    endSession()
                    return
                }

                handleClassifiedGesture(classification, rawX, rawY, localX, localY, gestureStartRawY)
                // ON_RELEASE 打开 leave-open 面板后结束手指跟踪，避免 active 一直为 true。
                finishLeaveOpenFingerTracking()
            }
        }
    }

    internal fun openPanel(mode: OverlayPanelMode) {
        sessionPanelMode = mode
        sessionIndexMode = false
        callbacks.onSessionStart(mode)
        callbacks.onRequestInvalidate()
    }

    internal fun openQuickLauncherPanel(action: GestureAction.QuickLauncher) {
        sessionQuickLauncherPanelId = QuickLauncherPanelDefaults.resolvePanelId(
            sessionSettings.quickLauncherPanels,
            action.panelId,
        )
        openPanel(OverlayPanelMode.QUICK_LAUNCHER)
    }

    fun openDiscretePanel(
        action: GestureAction,
        localX: Float,
        localY: Float,
        rawX: Float,
        rawY: Float,
    ) {
        forceReset(notifySessionEnd = false)
        pathRecognizer.seedExternalAnchor(rawX, rawY)
        when (action) {
            is GestureAction.OpenIndex -> {
                active = true
                enterIndexMode(localX, localY)
            }
            is GestureAction.QuickLauncher -> {
                sessionContinuousPick.quickLauncher = false
                active = true
                openQuickLauncherPanel(action)
            }
            is GestureAction.TaskSwitcher -> {
                sessionContinuousPick.taskSwitcher = false
                active = true
                openPanel(OverlayPanelMode.TASK_SWITCHER)
            }
            is GestureAction.ShellCommandPanel -> {
                sessionContinuousPick.shell = false
                callbacks.onOpenShellCommandPanel(continuousPick = false)
            }
            is GestureAction.HoneycombLauncher -> {
                sessionContinuousPick.honeycomb = false
                callbacks.onShowHoneycombLauncher(continuousPick = false, rawX, rawY)
            }
            GestureAction.AdjustVolume -> {
                active = true
                val fraction = actionExecutor.readCurrentAdjustFraction(ContinuousAdjustController.Mode.VOLUME)
                callbacks.onShowAdjustPanel(ContinuousAdjustController.Mode.VOLUME, fraction, rawY)
            }
            GestureAction.AdjustBrightness -> {
                active = true
                val fraction = actionExecutor.readCurrentAdjustFraction(ContinuousAdjustController.Mode.BRIGHTNESS)
                callbacks.onShowAdjustPanel(ContinuousAdjustController.Mode.BRIGHTNESS, fraction, rawY)
            }
            else -> Unit
        }
    }

    fun endSession() {
        if (!active && sessionPanelMode == OverlayPanelMode.NONE && sessionAdjustMode == null) return
        forceReset(notifySessionEnd = true)
    }

    fun forceReset(notifySessionEnd: Boolean = true) {
        val shouldNotify = notifySessionEnd &&
            (active || sessionPanelMode != OverlayPanelMode.NONE || sessionAdjustMode != null)

        active = false
        sessionIndexMode = false
        sessionAdjustMode = null
        sessionAdjustLayoutAnchorRawY = 0f
        sessionPanelMode = OverlayPanelMode.NONE
        sessionMoveTimeActionFired = false
        sessionContinuousPick.reset()
        thresholdTracker.reset()
        sessionActiveHandleId = TriggerHandle.DEFAULT_ID
        sessionQuickLauncherPanelId = ""

        callbacks.cancelDelayed(longPressCheckRunnable)
        actionExecutor.endContinuousAdjust()
        pathRecognizer.reset()
        indexSession.endSession()

        if (shouldNotify) {
            callbacks.onSessionEnd()
        }
        callbacks.onRequestInvalidate()
    }

    fun reset() {
        forceReset(notifySessionEnd = true)
    }

    internal fun classifyOptions(): SwipePathRecognizer.ClassifyOptions =
        if (sessionSettings.actionFor(side, GestureTriggerType.SHORT_SINGLE_TAP, sessionActiveHandleId) is GestureAction.ClickPassthrough) {
            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP
        } else {
            SwipePathRecognizer.ClassifyOptions.DEFAULT
        }

    internal fun enterAdjustMode(mode: ContinuousAdjustController.Mode, rawY: Float) {
        if (sessionAdjustMode == mode) {
            actionExecutor.updateContinuousAdjust(mode, rawY)
            return
        }
        if (actionExecutor.beginContinuousAdjust(mode, rawY)) {
            val enteringAdjust = sessionAdjustMode == null
            sessionAdjustMode = mode
            if (enteringAdjust) {
                sessionAdjustLayoutAnchorRawY = rawY
                callbacks.onSessionStart(OverlayPanelMode.NONE)
            }
            actionExecutor.updateContinuousAdjust(mode, rawY)
            callbacks.onRequestInvalidate()
        }
    }

    internal fun enterIndexMode(localX: Float, localY: Float) {
        sessionIndexMode = true
        sessionPanelMode = OverlayPanelMode.INDEX
        callbacks.onSessionStart(OverlayPanelMode.INDEX)
        indexSession.updateSelection(localX, localY)
        callbacks.onRequestInvalidate()
    }

    /** @return true if the overlay session should end after this quick-launcher tap. */
    fun performQuickLauncherAction(
        action: GestureAction,
        localX: Float,
        localY: Float,
        rawY: Float,
        confirmHaptic: Boolean = true,
    ): Boolean = dispatchQuickLauncherAction(action, localX, localY, rawY, confirmHaptic)
}
