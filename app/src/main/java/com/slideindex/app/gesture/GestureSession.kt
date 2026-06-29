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

    private var moveTimeActionFired = false

    private var wasAboveShortThreshold = false

    private var wasAboveLongThreshold = false

    private var longPressHapticFired = false

    private var lastRawX = 0f

    private var lastRawY = 0f

    private var lastLocalX = 0f

    private var lastLocalY = 0f

    private val longPressCheckRunnable = Runnable {
        if (!active || panelMode != OverlayPanelMode.NONE) return@Runnable
        maybeHapticLongPress(lastRawX, lastRawY)
        if (!pathRecognizer.isLongPressArmed()) return@Runnable
        val classification = pathRecognizer.classifyPartial(lastRawX, lastRawY, classifyOptions()) ?: return@Runnable
        when (settings.resolvedTriggerMode(side, classification.trigger)) {
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

    }



    fun isActive(): Boolean = active



    fun panelMode(): OverlayPanelMode = panelMode



    fun isAdjustMode(): Boolean = adjustMode != null

    fun adjustModeOrNull(): ContinuousAdjustController.Mode? = adjustMode

    fun adjustAnchorRawY(): Float = lastRawY



    fun onTouchDown(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {

        if (active) return true

        if (!zoneLayout.containsTrigger(localX, localY)) return false

        active = true

        indexMode = false

        adjustMode = null

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

        if (adjustMode != null) {
            actionExecutor.updateContinuousAdjust(adjustMode!!, rawY)
            callbacks.onRequestInvalidate()
            return
        }

        if (panelMode != OverlayPanelMode.NONE) {

            if (indexMode) {

                indexSession.updateSelection(localX, localY)

                callbacks.onRequestInvalidate()

            }

            return

        }

        pathRecognizer.onTouchMove(rawX, rawY)

        if (!pathRecognizer.longPressEligible()) {
            callbacks.cancelDelayed(longPressCheckRunnable)
        }

        maybeHapticLongPress(rawX, rawY)

        trackDistanceHaptics(rawX, rawY)

        val classification = pathRecognizer.classifyPartial(rawX, rawY, classifyOptions()) ?: return

        when (settings.resolvedTriggerMode(side, classification.trigger)) {

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

            OverlayPanelMode.QUICK_LAUNCHER, OverlayPanelMode.TASK_SWITCHER -> Unit

            OverlayPanelMode.NONE -> {

                if (adjustMode != null) {
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

                val mode = settings.resolvedTriggerMode(side, classification.trigger)

                if (mode == GestureTriggerMode.IMMEDIATE ||

                    (mode == GestureTriggerMode.CONTINUOUS && !indexMode)

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

        if (!active) return

        active = false

        indexMode = false

        adjustMode = null

        panelMode = OverlayPanelMode.NONE

        moveTimeActionFired = false

        wasAboveShortThreshold = false

        wasAboveLongThreshold = false

        longPressHapticFired = false

        callbacks.cancelDelayed(longPressCheckRunnable)

        actionExecutor.endContinuousAdjust()

        pathRecognizer.reset()

        indexSession.endSession()

        callbacks.onSessionEnd()

        callbacks.onRequestInvalidate()

    }



    fun reset() {

        if (active) endSession()

    }



    private fun classifyOptions(): SwipePathRecognizer.ClassifyOptions =

        if (settings.actionFor(side, GestureTriggerType.SHORT_SINGLE_TAP) is GestureAction.ClickPassthrough) {

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

        val action = settings.actionFor(side, classification.trigger)

        if (action == GestureAction.None || action is GestureAction.ClickPassthrough) return

        moveTimeActionFired = true

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

        val action = settings.actionFor(side, classification.trigger)

        if (!classification.trigger.supportsIndex) return

        when (action) {

            is GestureAction.OpenIndex -> {

                if (!indexMode) {

                    enterIndexMode(localX, localY)

                } else {

                    indexSession.updateSelection(localX, localY)

                    callbacks.onRequestInvalidate()

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

        val action = settings.actionFor(side, classification.trigger)

        when (action) {

            is GestureAction.OpenIndex -> {

                if (classification.trigger.supportsIndex) {

                    enterIndexMode(localX, localY)

                } else {

                    endSession()

                }

            }

            GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> {
                val adjustControllerMode = when (action) {
                    GestureAction.AdjustVolume -> ContinuousAdjustController.Mode.VOLUME
                    GestureAction.AdjustBrightness -> ContinuousAdjustController.Mode.BRIGHTNESS
                    else -> error("unreachable")
                }
                val triggerMode = settings.resolvedTriggerMode(side, classification.trigger)
                when (triggerMode) {
                    GestureTriggerMode.CONTINUOUS -> endSession()
                    GestureTriggerMode.IMMEDIATE -> enterAdjustMode(adjustControllerMode, rawY)
                    GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> {
                        val fraction = actionExecutor.readCurrentAdjustFraction(adjustControllerMode)
                        endSession()
                        callbacks.hapticConfirmLaunch()
                        callbacks.onShowAdjustPanel(adjustControllerMode, fraction, rawY)
                    }
                }
            }

            is GestureAction.QuickLauncher -> {
                callbacks.hapticConfirmLaunch()
                openPanel(OverlayPanelMode.QUICK_LAUNCHER)
            }

            is GestureAction.TaskSwitcher -> {
                callbacks.hapticConfirmLaunch()
                openPanel(OverlayPanelMode.TASK_SWITCHER)
            }

            is GestureAction.LaunchApp -> {

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

            GestureAction.None -> endSession()

        }

    }

}
