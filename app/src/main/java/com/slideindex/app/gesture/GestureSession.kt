package com.slideindex.app.gesture



import com.slideindex.app.overlay.OverlayPanelMode

import com.slideindex.app.overlay.PanelSide

import com.slideindex.app.service.OverlayService

import com.slideindex.app.settings.AppSettings



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

        fun onSessionEnd()

        fun onRequestInvalidate()

        fun hapticGestureStart()

        fun hapticConfirmLaunch()

    }



    private var settings = AppSettings()

    private var active = false

    private var panelMode = OverlayPanelMode.NONE

    private var indexMode = false

    private var moveTimeActionFired = false



    fun applySettings(newSettings: AppSettings) {

        settings = newSettings

        indexSession.applySettings(newSettings)

        pathRecognizer.applyDistances(newSettings.shortSwipeDistanceDp, newSettings.longSwipeDistanceDp)

    }



    fun isActive(): Boolean = active



    fun panelMode(): OverlayPanelMode = panelMode



    fun isIndexMode(): Boolean = indexMode



    fun onTouchDown(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {

        if (active) return true

        if (!zoneLayout.containsTrigger(localX, localY)) return false

        active = true

        indexMode = false

        panelMode = OverlayPanelMode.NONE

        moveTimeActionFired = false

        OverlayService.captureGestureForegroundPackage()

        pathRecognizer.onTouchDown(rawX, rawY)

        callbacks.hapticGestureStart()

        return true

    }



    fun onTouchMove(rawX: Float, rawY: Float, localX: Float, localY: Float) {

        if (!active) return

        if (panelMode != OverlayPanelMode.NONE) {

            if (indexMode) {

                indexSession.updateSelection(localX, localY)

                callbacks.onRequestInvalidate()

            }

            return

        }

        pathRecognizer.onTouchMove(rawX, rawY)

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

                trackContinuousGesture(classification, localX, localY)

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

                if (moveTimeActionFired) {

                    endSession()

                    return

                }

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

                handleClassifiedGesture(classification, rawX, rawY, localX, localY)

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

        panelMode = OverlayPanelMode.NONE

        moveTimeActionFired = false

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

        )

    }



    private fun trackContinuousGesture(

        classification: SwipeClassification,

        localX: Float,

        localY: Float,

    ) {

        val action = settings.actionFor(side, classification.trigger)

        if (action !is GestureAction.OpenIndex || !classification.trigger.supportsIndex) return

        if (!indexMode) {

            enterIndexMode(localX, localY)

        } else {

            indexSession.updateSelection(localX, localY)

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

            GestureAction.None -> endSession()

        }

    }

}


