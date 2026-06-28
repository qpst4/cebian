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

    fun applySettings(newSettings: AppSettings) {
        settings = newSettings
        indexSession.applySettings(newSettings)
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
        OverlayService.captureGestureForegroundPackage()
        pathRecognizer.onTouchDown(rawX, rawY)
        callbacks.hapticGestureStart()
        return true
    }

    fun onTouchMove(rawX: Float, rawY: Float, localX: Float, localY: Float) {
        if (!active || panelMode != OverlayPanelMode.NONE) {
            if (indexMode) {
                indexSession.updateSelection(localX, localY)
                callbacks.onRequestInvalidate()
            }
            return
        }
        pathRecognizer.onTouchMove(rawX, rawY)
        if (!indexMode && pathRecognizer.isVerticalDominant(rawX, rawY)) {
            val passthroughTap = settings.actionFor(side, GestureTriggerType.SHORT_SINGLE_TAP) is
                GestureAction.ClickPassthrough
            val stillTapLike = passthroughTap && pathRecognizer.isWithinLenientTapSlop(rawX, rawY)
            if (!stillTapLike) {
                val vertical = pathRecognizer.verticalDirection(rawY) ?: return
                val action = settings.actionFor(side, vertical)
                if (action is GestureAction.OpenIndex) {
                    enterIndexMode(localX, localY)
                }
            }
        }
        if (indexMode) {
            indexSession.updateSelection(localX, localY)
            callbacks.onRequestInvalidate()
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
                val classifyOptions = if (
                    settings.actionFor(side, GestureTriggerType.SHORT_SINGLE_TAP) is GestureAction.ClickPassthrough
                ) {
                    SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP
                } else {
                    SwipePathRecognizer.ClassifyOptions.DEFAULT
                }
                val classification = pathRecognizer.classifyOnUp(rawX, rawY, classifyOptions) ?: run {
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
        pathRecognizer.reset()
        indexSession.endSession()
        callbacks.onSessionEnd()
        callbacks.onRequestInvalidate()
    }

    fun reset() {
        if (active) endSession()
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
                callbacks.hapticConfirmLaunch()
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
