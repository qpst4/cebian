package com.slideindex.app.overlay

import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.HapticHelper

/**
 * Bridges [GestureSession] to [EdgeGestureSessionCoordinator] without a circular construction dependency.
 */
internal class GestureSessionCallbackBridge : GestureSession.Callbacks {
    lateinit var delegate: GestureSession.Callbacks

    override fun onSessionStart(mode: OverlayPanelMode) = delegate.onSessionStart(mode)
    override fun onSessionEnd() = delegate.onSessionEnd()
    override fun onOpenShellCommandPanel(continuousPick: Boolean) =
        delegate.onOpenShellCommandPanel(continuousPick)
    override fun onShellCommandPanelContinuousRelease() =
        delegate.onShellCommandPanelContinuousRelease()
    override fun onShowAdjustPanel(
        mode: com.slideindex.app.util.ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        deferWindowLayout: Boolean,
    ) = delegate.onShowAdjustPanel(mode, fraction, anchorRawY, deferWindowLayout)
    override fun onRequestInvalidate() = delegate.onRequestInvalidate()
    override fun hapticGestureStart() = delegate.hapticGestureStart()
    override fun hapticLongThreshold() = delegate.hapticLongThreshold()
    override fun hapticConfirmLaunch() = delegate.hapticConfirmLaunch()
    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) =
        delegate.scheduleDelayed(runnable, delayMs)
    override fun cancelDelayed(runnable: Runnable) = delegate.cancelDelayed(runnable)
}

internal class EdgeGestureSessionCoordinator(
    private val view: android.view.View,
    private val gestureSession: GestureSession,
    private val panelGridSession: PanelGridSession,
    private val panelEnterAnimator: OverlayPanelEnterAnimator,
    private val adjustPanelController: AdjustPanelOverlayController,
    private val taskSwitcherController: TaskSwitcherOverlayController,
    private val quickLauncherController: QuickLauncherOverlayController,
    private val shellCoordinator: ShellPanelOverlayController,
    private val gestureAnimationCoordinator: GestureAnimationCoordinator,
    private val layoutCoordinator: EdgeGestureLayoutCoordinator,
    private val settingsProvider: () -> AppSettings,
    private val runAfterLayout: (() -> Unit) -> Unit,
    private val onSessionStartCallback: () -> Unit,
    private val onAdjustPanelLayoutCallback: (Float) -> Unit,
    private val notifyPresentationTouchRequirementChanged: () -> Unit,
    private val requestInvalidate: () -> Unit,
) : GestureSession.Callbacks {
    private var lastAdjustInvalidateMs = 0L

    override fun onSessionStart(mode: OverlayPanelMode) {
        layoutCoordinator.syncZoneLayout()
        panelEnterAnimator.cancel()
        when (mode) {
            OverlayPanelMode.TASK_SWITCHER -> {
                panelEnterAnimator.resetToHidden()
                taskSwitcherController.onSessionStart()
            }
            OverlayPanelMode.INDEX, OverlayPanelMode.QUICK_LAUNCHER,
            OverlayPanelMode.SHELL_COMMANDS -> {
                panelEnterAnimator.resetToHidden()
                if (mode == OverlayPanelMode.SHELL_COMMANDS) {
                    shellCoordinator.onSessionStart()
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherController.onSessionStart()
                }
            }
            OverlayPanelMode.NONE -> {
                panelEnterAnimator.resetToComplete()
                if (gestureSession.isAdjustMode()) {
                    adjustPanelController.onSessionStartAdjustMode()
                }
            }
        }
        panelGridSession.reset()
        onSessionStartCallback()
        notifyPresentationTouchRequirementChanged()
        if (mode != OverlayPanelMode.NONE || gestureSession.isAdjustMode()) {
            gestureAnimationCoordinator.onSessionStartDismissIfNeeded()
        }
        if (mode != OverlayPanelMode.NONE) {
            runAfterLayout {
                if (gestureSession.panelMode() != mode) return@runAfterLayout
                layoutCoordinator.syncZoneLayout()
                if (mode == OverlayPanelMode.TASK_SWITCHER) {
                    taskSwitcherController.onLayoutReady()
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherController.onLayoutReady()
                }
                panelEnterAnimator.startEnter(
                    panelMode = mode,
                    onShellEnterEnded = { shellCoordinator.onPanelEnterAnimationEnded() },
                    onQuickLauncherEnterEnded = { quickLauncherController.onPanelEnterAnimationEnded() },
                )
            }
        }
    }

    override fun onSessionEnd() {
        panelEnterAnimator.cancel()
        gestureAnimationCoordinator.hide()
        adjustPanelController.onSessionEnd()
        panelEnterAnimator.resetToComplete()
        layoutCoordinator.syncZoneLayout()
        panelGridSession.reset()
        taskSwitcherController.onSessionEnd()
        quickLauncherController.onSessionEnd()
        shellCoordinator.onSessionEnd()
        layoutCoordinator.notifyOverlayLayoutIfNeeded()
        notifyPresentationTouchRequirementChanged()
    }

    override fun onOpenShellCommandPanel(continuousPick: Boolean) {
        shellCoordinator.onOpenShellCommandPanel(continuousPick)
    }

    override fun onShellCommandPanelContinuousRelease() {
        shellCoordinator.onShellCommandPanelContinuousRelease()
    }

    override fun onShowAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        deferWindowLayout: Boolean,
    ) {
        onAdjustPanelLayoutCallback(anchorRawY)
        adjustPanelController.showAdjustPanel(mode, fraction, anchorRawY)
    }

    override fun onRequestInvalidate() = requestInvalidate()

    override fun hapticGestureStart() = HapticHelper.gestureStart(view, settingsProvider())

    override fun hapticLongThreshold() = HapticHelper.longThreshold(view, settingsProvider())

    override fun hapticConfirmLaunch() = HapticHelper.confirmLaunch(view, settingsProvider())

    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) {
        view.postDelayed(runnable, delayMs)
    }

    override fun cancelDelayed(runnable: Runnable) {
        view.removeCallbacks(runnable)
    }

    fun hapticLetterTick() = HapticHelper.letterTick(view, settingsProvider())

    fun hapticAppTick() = HapticHelper.appTick(view, settingsProvider())

    fun requestInvalidateThrottled() {
        if (gestureSession.isAdjustMode()) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastAdjustInvalidateMs < 16L) return
            lastAdjustInvalidateMs = now
        }
        requestInvalidate()
    }
}
