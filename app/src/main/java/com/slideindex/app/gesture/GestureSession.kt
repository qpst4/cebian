package com.slideindex.app.gesture



import com.slideindex.app.overlay.OverlayPanelMode

import com.slideindex.app.overlay.PanelSide

import com.slideindex.app.service.OverlayService

import com.slideindex.app.settings.AppSettings

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



    private var settings = AppSettings()

    private var active = false

    private var panelMode = OverlayPanelMode.NONE

    private var indexMode = false

    private var adjustMode: ContinuousAdjustController.Mode? = null

    /** Screen Y frozen when adjust mode starts; indicator layout must not follow the finger. */
    private var adjustLayoutAnchorRawY = 0f

    private var moveTimeActionFired = false

    private var wasAboveShortThreshold = false

    private var wasAboveLongThreshold = false

    private var longPressHapticFired = false

    private var lastRawX = 0f

    private var lastRawY = 0f

    private var lastLocalX = 0f

    private var lastLocalY = 0f

    private var activeHandleId = TriggerHandle.DEFAULT_ID

    private val longPressCheckRunnable = Runnable {
        if (!active || panelMode != OverlayPanelMode.NONE) return@Runnable
        maybeHapticLongPress(lastRawX, lastRawY)
        if (!pathRecognizer.isLongPressArmed()) return@Runnable
        val classification = pathRecognizer.classifyPartial(lastRawX, lastRawY, classifyOptions()) ?: return@Runnable
        when (settings.resolvedTriggerMode(side, classification.trigger, activeHandleId)) {
            GestureTriggerMode.IMMEDIATE -> {
                if (!moveTimeActionFired &&
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



    fun applySettings(newSettings: AppSettings) {

        settings = newSettings

        indexSession.applySettings(newSettings)

        pathRecognizer.applyDistances(newSettings.shortSwipeDistanceDp, newSettings.longSwipeDistanceDp)

        pathRecognizer.applyAngles(newSettings.gestureAngleConfig)

    }



    fun isActive(): Boolean = active



    fun panelMode(): OverlayPanelMode = panelMode



    fun isAdjustMode(): Boolean = adjustMode != null

    fun adjustModeOrNull(): ContinuousAdjustController.Mode? = adjustMode

    /** True after an IMMEDIATE action fired on this touch; blocks further in-gesture tracking. */
    fun isMoveTimeActionLocked(): Boolean = moveTimeActionFired

    /** Ends the initiating IMMEDIATE swipe while keeping the opened panel active. */
    fun releaseImmediateGestureLock(): Boolean {
        if (!moveTimeActionFired) return false
        moveTimeActionFired = false
        callbacks.onRequestInvalidate()
        return true
    }

    private var taskSwitcherContinuousPick = false

    fun taskSwitcherContinuousPickActive(): Boolean = taskSwitcherContinuousPick

    private var quickLauncherContinuousPick = false

    fun quickLauncherContinuousPickActive(): Boolean = quickLauncherContinuousPick

    private var shellCommandContinuousPick = false

    fun shellCommandContinuousPickActive(): Boolean = shellCommandContinuousPick

    fun adjustAnchorRawY(): Float = adjustLayoutAnchorRawY

    fun activeHandleId(): String = activeHandleId



    fun onTouchDown(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {

        if (active) return false

        if (!zoneLayout.containsTrigger(localX, localY)) return false

        activeHandleId = zoneLayout.findTriggerHandleAt(localX, localY) ?: TriggerHandle.DEFAULT_ID

        active = true

        indexMode = false

        adjustMode = null
        adjustLayoutAnchorRawY = 0f

        panelMode = OverlayPanelMode.NONE

        moveTimeActionFired = false

        wasAboveShortThreshold = false

        wasAboveLongThreshold = false

        longPressHapticFired = false

        lastRawX = rawX

        lastRawY = rawY

        lastLocalX = localX

        lastLocalY = localY

        OverlayService.captureGestureForegroundPackage()

        pathRecognizer.onTouchDown(rawX, rawY)

        callbacks.scheduleDelayed(longPressCheckRunnable, SwipePathRecognizer.LONG_PRESS_MS)

        return true

    }



    fun onTouchMove(rawX: Float, rawY: Float, localX: Float, localY: Float) {

        if (!active) return

        lastRawX = rawX

        lastRawY = rawY

        lastLocalX = localX

        lastLocalY = localY

        val currentAdjustMode = adjustMode
        if (currentAdjustMode != null) {
            actionExecutor.updateContinuousAdjust(currentAdjustMode, rawY)
            callbacks.onRequestInvalidate()
            return
        }

        if (panelMode != OverlayPanelMode.NONE) {

            if (indexMode && !moveTimeActionFired) {

                indexSession.updateSelection(localX, localY)

                callbacks.onRequestInvalidate()

            }

            return

        }

        // After an IMMEDIATE action fires, keep consuming the same touch but do not
        // re-classify the path (e.g. vertical slides in the trigger strip firing
        // CONTINUOUS actions like adjust volume/brightness or open index).
        if (moveTimeActionFired) return

        pathRecognizer.onTouchMove(rawX, rawY)

        if (!pathRecognizer.longPressEligible()) {
            callbacks.cancelDelayed(longPressCheckRunnable)
        }

        maybeHapticLongPress(rawX, rawY)

        trackDistanceHaptics(rawX, rawY)

        val classification = pathRecognizer.classifyPartial(rawX, rawY, classifyOptions()) ?: return

        when (settings.resolvedTriggerMode(side, classification.trigger, activeHandleId)) {

            GestureTriggerMode.IMMEDIATE -> {

                if (!moveTimeActionFired &&

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

        when (panelMode) {

            OverlayPanelMode.INDEX -> {

                // IMMEDIATE open: first finger-up only ends the triggering swipe; keep the
                // index panel open so the user can continue selecting on a new touch.
                if (releaseImmediateGestureLock()) return

                val app = indexSession.highlightedApp

                val longPressArmed = indexSession.longPressArmed

                endSession()

                app?.let {

                    callbacks.hapticConfirmLaunch()

                    actionExecutor.execute(

                        GestureAction.LaunchApp(it.packageName),

                        settings,

                        longPressArmed,

                    )

                }

            }

            OverlayPanelMode.QUICK_LAUNCHER, OverlayPanelMode.TASK_SWITCHER,
            OverlayPanelMode.SHELL_COMMANDS -> Unit

            OverlayPanelMode.NONE -> {

                if (adjustMode != null) {
                    val mode = adjustMode!!
                    val anchorRawY = adjustLayoutAnchorRawY
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

                if (moveTimeActionFired) {

                    endSession()

                    return

                }

                maybeHapticLongPress(rawX, rawY)

                val gestureStartRawY = pathRecognizer.gestureStartRawY()

                val classification = pathRecognizer.classifyOnUp(rawX, rawY, classifyOptions()) ?: run {

                    endSession()

                    return

                }

                val mode = settings.resolvedTriggerMode(side, classification.trigger, activeHandleId)

                if (mode == GestureTriggerMode.IMMEDIATE) {

                    endSession()

                    return

                }

                val action = settings.actionFor(side, classification.trigger, activeHandleId)
                if (mode == GestureTriggerMode.CONTINUOUS && !indexMode &&
                    (moveTimeActionFired || action.supportsContinuousTracking(classification.trigger))
                ) {

                    endSession()

                    return

                }

                handleClassifiedGesture(classification, rawX, rawY, localX, localY, gestureStartRawY)

            }

        }

    }



    fun openPanel(mode: OverlayPanelMode) {
        panelMode = mode
        indexMode = false
        callbacks.onSessionStart(mode)
        callbacks.onRequestInvalidate()
    }



    fun endSession() {
        if (!active && panelMode == OverlayPanelMode.NONE && adjustMode == null) return
        forceReset(notifySessionEnd = true)
    }

    fun forceReset(notifySessionEnd: Boolean = true) {
        val shouldNotify = notifySessionEnd &&
            (active || panelMode != OverlayPanelMode.NONE || adjustMode != null)

        active = false
        indexMode = false
        adjustMode = null
        adjustLayoutAnchorRawY = 0f
        panelMode = OverlayPanelMode.NONE
        moveTimeActionFired = false
        taskSwitcherContinuousPick = false
        quickLauncherContinuousPick = false
        shellCommandContinuousPick = false
        wasAboveShortThreshold = false
        wasAboveLongThreshold = false
        longPressHapticFired = false
        activeHandleId = TriggerHandle.DEFAULT_ID

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



    private fun classifyOptions(): SwipePathRecognizer.ClassifyOptions =

        if (settings.actionFor(side, GestureTriggerType.SHORT_SINGLE_TAP, activeHandleId) is GestureAction.ClickPassthrough) {

            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP

        } else {

            SwipePathRecognizer.ClassifyOptions.DEFAULT

        }

    private fun trackDistanceHaptics(rawX: Float, rawY: Float) {
        val distance = pathRecognizer.swipeDistance(rawX, rawY)
        val aboveShort = distance >= pathRecognizer.shortThresholdPx()
        val aboveLong = distance >= pathRecognizer.longThresholdPx()
        if (aboveShort && !wasAboveShortThreshold) {
            callbacks.cancelDelayed(longPressCheckRunnable)
            pathRecognizer.disqualifyLongPress()
            callbacks.hapticGestureStart()
        }
        if (aboveLong && !wasAboveLongThreshold) {
            callbacks.hapticLongThreshold()
        }
        wasAboveShortThreshold = aboveShort
        wasAboveLongThreshold = aboveLong
    }

    private fun maybeHapticLongPress(rawX: Float, rawY: Float) {
        if (longPressHapticFired) return
        pathRecognizer.refreshLongPress(rawX, rawY)
        if (pathRecognizer.isLongPressArmed()) {
            longPressHapticFired = true
            callbacks.hapticLongThreshold()
        }
    }



    private fun dispatchMoveTimeGesture(

        classification: SwipeClassification,

        rawX: Float,

        rawY: Float,

        localX: Float,

        localY: Float,

    ) {

        val action = settings.actionFor(side, classification.trigger, activeHandleId)

        if (action == GestureAction.None || action is GestureAction.ClickPassthrough) return

        moveTimeActionFired = true
        callbacks.cancelDelayed(longPressCheckRunnable)
        pathRecognizer.disqualifyLongPress()

        handleClassifiedGesture(

            classification = classification,

            rawX = rawX,

            rawY = rawY,

            localX = localX,

            localY = localY,

            gestureStartRawY = pathRecognizer.gestureStartRawY(),

        )

    }



    private fun trackContinuousGesture(

        classification: SwipeClassification,

        rawX: Float,

        rawY: Float,

        localX: Float,

        localY: Float,

    ) {

        val action = settings.actionFor(side, classification.trigger, activeHandleId)

        if (!action.supportsContinuousTracking(classification.trigger)) return

        when (action) {

            is GestureAction.OpenIndex -> {

                if (!indexMode) {

                    enterIndexMode(localX, localY)

                } else {

                    indexSession.updateSelection(localX, localY)

                    callbacks.onRequestInvalidate()

                }

            }

            GestureAction.TaskSwitcher -> {
                if (panelMode != OverlayPanelMode.TASK_SWITCHER) {
                    taskSwitcherContinuousPick = true
                    openPanel(OverlayPanelMode.TASK_SWITCHER)
                }
            }

            GestureAction.QuickLauncher -> {
                if (panelMode != OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherContinuousPick = true
                    openPanel(OverlayPanelMode.QUICK_LAUNCHER)
                }
            }

            GestureAction.ShellCommandPanel -> {
                if (panelMode != OverlayPanelMode.SHELL_COMMANDS) {
                    shellCommandContinuousPick = true
                    openPanel(OverlayPanelMode.SHELL_COMMANDS)
                }
            }

            GestureAction.AdjustVolume -> enterAdjustMode(ContinuousAdjustController.Mode.VOLUME, rawY)

            GestureAction.AdjustBrightness -> enterAdjustMode(ContinuousAdjustController.Mode.BRIGHTNESS, rawY)

            else -> Unit

        }

    }



    private fun enterAdjustMode(mode: ContinuousAdjustController.Mode, rawY: Float) {
        if (adjustMode == mode) {
            actionExecutor.updateContinuousAdjust(mode, rawY)
            return
        }
        if (actionExecutor.beginContinuousAdjust(mode, rawY)) {
            val enteringAdjust = adjustMode == null
            adjustMode = mode
            if (enteringAdjust) {
                adjustLayoutAnchorRawY = rawY
                callbacks.onSessionStart(OverlayPanelMode.NONE)
            }
            actionExecutor.updateContinuousAdjust(mode, rawY)
            callbacks.onRequestInvalidate()
        }
    }



    private fun enterIndexMode(localX: Float, localY: Float) {

        indexMode = true

        panelMode = OverlayPanelMode.INDEX

        callbacks.onSessionStart(OverlayPanelMode.INDEX)

        indexSession.updateSelection(localX, localY)

        callbacks.onRequestInvalidate()

    }



    private fun handleClassifiedGesture(

        classification: SwipeClassification,

        rawX: Float,

        rawY: Float,

        localX: Float,

        localY: Float,

        gestureStartRawY: Float,

    ) {

        val action = settings.actionFor(side, classification.trigger, activeHandleId)

        when (action) {

            is GestureAction.OpenIndex -> enterIndexMode(localX, localY)

            GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> {
                val adjustControllerMode = when (action) {
                    GestureAction.AdjustVolume -> ContinuousAdjustController.Mode.VOLUME
                    GestureAction.AdjustBrightness -> ContinuousAdjustController.Mode.BRIGHTNESS
                }
                val triggerMode = settings.resolvedTriggerMode(side, classification.trigger, activeHandleId)
                when (triggerMode) {
                    GestureTriggerMode.CONTINUOUS -> endSession()
                    GestureTriggerMode.IMMEDIATE -> {
                        val fraction = actionExecutor.readCurrentAdjustFraction(adjustControllerMode)
                        callbacks.hapticConfirmLaunch()
                        callbacks.onShowAdjustPanel(adjustControllerMode, fraction, rawY, deferWindowLayout = true)
                    }
                    GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> {
                        val fraction = actionExecutor.readCurrentAdjustFraction(adjustControllerMode)
                        callbacks.hapticConfirmLaunch()
                        callbacks.onShowAdjustPanel(adjustControllerMode, fraction, rawY)
                        endSession()
                    }
                }
            }

            is GestureAction.QuickLauncher -> {
                quickLauncherContinuousPick = false
                callbacks.hapticConfirmLaunch()
                openPanel(OverlayPanelMode.QUICK_LAUNCHER)
            }

            is GestureAction.ShellCommandPanel -> {
                shellCommandContinuousPick = false
                callbacks.hapticConfirmLaunch()
                openPanel(OverlayPanelMode.SHELL_COMMANDS)
            }

            is GestureAction.TaskSwitcher -> {
                taskSwitcherContinuousPick = false
                callbacks.hapticConfirmLaunch()
                openPanel(OverlayPanelMode.TASK_SWITCHER)
            }

            is GestureAction.LaunchApp -> {

                callbacks.hapticConfirmLaunch()

                actionExecutor.execute(action, settings)

                endSession()

            }

            is GestureAction.LaunchShortcut -> {

                callbacks.hapticConfirmLaunch()

                actionExecutor.execute(action, settings)

                endSession()

            }

            GestureAction.Back, GestureAction.Home, GestureAction.Recents -> {

                actionExecutor.execute(action, settings)

                endSession()

            }

            GestureAction.CloseCurrentApp, GestureAction.FreeWindowCurrentApp -> {

                callbacks.hapticConfirmLaunch()

                actionExecutor.execute(action, settings)

                endSession()

            }

            GestureAction.ClickPassthrough -> {

                callbacks.hapticConfirmLaunch()

                actionExecutor.dispatchClickPassthrough(rawX, rawY, ::endSession)

            }

            GestureAction.Flashlight, GestureAction.LaunchAssistant -> {

                callbacks.hapticConfirmLaunch()

                actionExecutor.execute(action, settings)

                endSession()

            }

            GestureAction.ToggleMute,
            GestureAction.ToggleDnd,
            GestureAction.ScreenRecord,
            GestureAction.ToggleWifi,
            GestureAction.ToggleMobileData,
            GestureAction.MediaPlayPause,
            GestureAction.MediaPrevious,
            GestureAction.MediaNext,
            GestureAction.PreviousApp,
            GestureAction.OpenNotifications,
            GestureAction.OpenQuickSettings,
            GestureAction.LockScreen,
            GestureAction.Screenshot,
            GestureAction.PowerMenu,
            GestureAction.KeepScreenOn,
            GestureAction.ScrollToTop,
            GestureAction.ScrollToBottom,
            GestureAction.QuickToolsOverlay,
            -> {
                callbacks.hapticConfirmLaunch()
                endSession()
                actionExecutor.execute(action, settings, anchorRawY = rawY)
            }

            GestureAction.None -> endSession()

        }

    }

    /** @return true if the overlay session should end after this quick-launcher tap. */
    fun performQuickLauncherAction(
        action: GestureAction,
        localX: Float,
        localY: Float,
        rawY: Float,
    ): Boolean {
        when (action) {
            GestureAction.OpenIndex -> {
                enterIndexMode(localX, localY)
                return false
            }
            GestureAction.QuickLauncher -> return false
            GestureAction.TaskSwitcher -> {
                taskSwitcherContinuousPick = false
                callbacks.hapticConfirmLaunch()
                openPanel(OverlayPanelMode.TASK_SWITCHER)
                return false
            }
            GestureAction.ShellCommandPanel -> {
                shellCommandContinuousPick = false
                callbacks.hapticConfirmLaunch()
                openPanel(OverlayPanelMode.SHELL_COMMANDS)
                return false
            }
            GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> {
                val mode = when (action) {
                    GestureAction.AdjustVolume -> ContinuousAdjustController.Mode.VOLUME
                    GestureAction.AdjustBrightness -> ContinuousAdjustController.Mode.BRIGHTNESS
                    else -> return true
                }
                val fraction = actionExecutor.applyAdjustOnce(mode, rawY, rawY)
                    ?: actionExecutor.readCurrentAdjustFraction(mode)
                callbacks.hapticConfirmLaunch()
                callbacks.onShowAdjustPanel(mode, fraction, rawY)
                return true
            }
            else -> {
                callbacks.hapticConfirmLaunch()
                actionExecutor.execute(action, settings, anchorRawY = rawY)
                return true
            }
        }
    }

}
