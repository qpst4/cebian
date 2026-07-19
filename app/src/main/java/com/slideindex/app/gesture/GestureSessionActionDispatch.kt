package com.slideindex.app.gesture

import com.slideindex.app.overlay.OverlayPanelMode
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.resolvedTriggerMode
import com.slideindex.app.util.ContinuousAdjustController

internal fun GestureSession.dispatchMoveTimeGesture(
    classification: SwipeClassification,
    rawX: Float,
    rawY: Float,
    localX: Float,
    localY: Float,
) {
    val action = sessionSettings.actionFor(sessionSide, classification.trigger, sessionActiveHandleId)

    if (action == GestureAction.None || action is GestureAction.ClickPassthrough) return

    sessionMoveTimeActionFired = true
    sessionCallbacks.cancelDelayed(sessionLongPressCheckRunnable)
    sessionPathRecognizer.disqualifyLongPress()

    handleClassifiedGesture(
        classification = classification,
        rawX = rawX,
        rawY = rawY,
        localX = localX,
        localY = localY,
        gestureStartRawY = sessionPathRecognizer.gestureStartRawY(),
    )
}

internal fun GestureSession.trackContinuousGesture(
    classification: SwipeClassification,
    rawX: Float,
    rawY: Float,
    localX: Float,
    localY: Float,
) {
    val action = sessionSettings.actionFor(sessionSide, classification.trigger, sessionActiveHandleId)

    if (!action.supportsContinuousTracking(classification.trigger)) return

    when (action) {
        is GestureAction.OpenIndex -> {
            if (!sessionIndexMode) {
                enterIndexMode(localX, localY)
            } else {
                sessionIndexSession.updateSelection(localX, localY)
                sessionCallbacks.onRequestInvalidate()
            }
        }

        GestureAction.TaskSwitcher -> {
            if (sessionPanelMode != OverlayPanelMode.TASK_SWITCHER) {
                sessionContinuousPick.taskSwitcher = true
                openPanel(OverlayPanelMode.TASK_SWITCHER)
            }
        }

        GestureAction.QuickLauncher -> {
            if (sessionPanelMode != OverlayPanelMode.QUICK_LAUNCHER) {
                sessionContinuousPick.quickLauncher = true
                openPanel(OverlayPanelMode.QUICK_LAUNCHER)
            }
        }

        GestureAction.ShellCommandPanel -> {
            if (!sessionContinuousPick.shell) {
                sessionContinuousPick.shell = true
                sessionCallbacks.onOpenShellCommandPanel(continuousPick = true)
            }
        }

        GestureAction.AdjustVolume -> enterAdjustMode(ContinuousAdjustController.Mode.VOLUME, rawY)

        GestureAction.AdjustBrightness -> enterAdjustMode(ContinuousAdjustController.Mode.BRIGHTNESS, rawY)

        GestureAction.FloatingPointer -> {
            if (!sessionMoveTimeActionFired &&
                sessionPathRecognizer.hasMetThreshold(classification.trigger, rawX, rawY)
            ) {
                sessionMoveTimeActionFired = true
                sessionCallbacks.cancelDelayed(sessionLongPressCheckRunnable)
                sessionPathRecognizer.disqualifyLongPress()
                sessionCallbacks.hapticConfirmLaunch()
                sessionActionExecutor.execute(
                    GestureAction.FloatingPointer,
                    sessionSettings,
                    anchorRawX = rawX,
                    anchorRawY = rawY,
                    continueTouch = true,
                )
            }
        }

        else -> Unit
    }
}

internal fun GestureSession.handleClassifiedGesture(
    classification: SwipeClassification,
    rawX: Float,
    rawY: Float,
    localX: Float,
    localY: Float,
    gestureStartRawY: Float,
) {
    val action = sessionSettings.actionFor(sessionSide, classification.trigger, sessionActiveHandleId)

    when (action) {
        is GestureAction.OpenIndex -> enterIndexMode(localX, localY)

        GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> {
            val adjustControllerMode = when (action) {
                GestureAction.AdjustVolume -> ContinuousAdjustController.Mode.VOLUME
                GestureAction.AdjustBrightness -> ContinuousAdjustController.Mode.BRIGHTNESS
            }
            val triggerMode = sessionSettings.resolvedTriggerMode(sessionSide, classification.trigger, sessionActiveHandleId)
            when (triggerMode) {
                GestureTriggerMode.CONTINUOUS -> endSession()
                GestureTriggerMode.IMMEDIATE -> {
                    val fraction = sessionActionExecutor.readCurrentAdjustFraction(adjustControllerMode)
                    sessionCallbacks.hapticConfirmLaunch()
                    sessionCallbacks.onShowAdjustPanel(adjustControllerMode, fraction, rawY, deferWindowLayout = true)
                }
                GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> {
                    val fraction = sessionActionExecutor.readCurrentAdjustFraction(adjustControllerMode)
                    sessionCallbacks.hapticConfirmLaunch()
                    sessionCallbacks.onShowAdjustPanel(adjustControllerMode, fraction, rawY)
                    endSession()
                }
            }
        }

        is GestureAction.QuickLauncher -> {
            sessionContinuousPick.quickLauncher = false
            sessionCallbacks.hapticConfirmLaunch()
            openPanel(OverlayPanelMode.QUICK_LAUNCHER)
        }

        is GestureAction.ShellCommandPanel -> {
            sessionContinuousPick.shell = false
            sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onOpenShellCommandPanel(continuousPick = false)
        }

        is GestureAction.TaskSwitcher -> {
            sessionContinuousPick.taskSwitcher = false
            sessionCallbacks.hapticConfirmLaunch()
            openPanel(OverlayPanelMode.TASK_SWITCHER)
        }

        is GestureAction.LaunchApp -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        is GestureAction.LaunchShortcut -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.Back, GestureAction.Home, GestureAction.Recents -> {
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.CloseCurrentApp, GestureAction.FreeWindowCurrentApp -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.ClickPassthrough -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.dispatchClickPassthrough(rawX, rawY, ::endSession)
        }

        GestureAction.Flashlight, GestureAction.LaunchAssistant -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.ToggleMute,
        GestureAction.ToggleDnd,
        GestureAction.ScreenRecord,
        GestureAction.ToggleWifi,
        GestureAction.ToggleMobileData,
        GestureAction.SwitchInputMethod,
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
        GestureAction.WidgetPopupOverlay,
        GestureAction.StashPanel,
        GestureAction.FloatingPointer,
        GestureAction.PointerGestureRecorder,
        GestureAction.PointerRealtimeGesture,
        GestureAction.OpenFloatingPointerRadialMenu,
        is GestureAction.SimulatePointerSwipe,
        -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(
                action,
                sessionSettings,
                anchorRawX = rawX,
                anchorRawY = rawY,
                continueTouch = sessionMoveTimeActionFired,
            )
            endSession()
        }

        GestureAction.None -> endSession()
    }
}

/** @return true if the overlay session should end after this quick-launcher tap. */
internal fun GestureSession.dispatchQuickLauncherAction(
    action: GestureAction,
    localX: Float,
    localY: Float,
    rawY: Float,
    confirmHaptic: Boolean = true,
): Boolean {
    when (action) {
        GestureAction.OpenIndex -> {
            enterIndexMode(localX, localY)
            return false
        }
        GestureAction.QuickLauncher -> return false
        GestureAction.TaskSwitcher -> {
            sessionContinuousPick.taskSwitcher = false
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            openPanel(OverlayPanelMode.TASK_SWITCHER)
            return false
        }
        GestureAction.ShellCommandPanel -> {
            sessionContinuousPick.shell = false
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onOpenShellCommandPanel(continuousPick = false)
            return false
        }
        GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> {
            val mode = when (action) {
                GestureAction.AdjustVolume -> ContinuousAdjustController.Mode.VOLUME
                GestureAction.AdjustBrightness -> ContinuousAdjustController.Mode.BRIGHTNESS
            }
            val fraction = sessionActionExecutor.applyAdjustOnce(mode, rawY, rawY)
                ?: sessionActionExecutor.readCurrentAdjustFraction(mode)
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onShowAdjustPanel(mode, fraction, rawY)
            return true
        }
        else -> {
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings, anchorRawY = rawY)
            return true
        }
    }
}
