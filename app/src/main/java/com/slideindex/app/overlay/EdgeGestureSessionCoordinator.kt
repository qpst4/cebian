package com.slideindex.app.overlay

import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.overlay.appswitcher.AppSwitcherOverlayWindow
import com.slideindex.app.util.HapticHelper

/**
 * Bridges [GestureSession] to [EdgeGestureSessionCoordinator] without a circular construction dependency.
 */
internal class GestureSessionCallbackBridge : GestureSession.Callbacks {
    lateinit var delegate: GestureSession.Callbacks

    override fun onSessionStart(mode: OverlayPanelMode) = delegate.onSessionStart(mode)
    override fun onLeaveOpenFingerTrackingFinished() = delegate.onLeaveOpenFingerTrackingFinished()
    override fun onSessionEnd() = delegate.onSessionEnd()
    override fun onOpenShellCommandPanel(continuousPick: Boolean) =
        delegate.onOpenShellCommandPanel(continuousPick)
    override fun onShellCommandPanelContinuousRelease() =
        delegate.onShellCommandPanelContinuousRelease()
    override fun onShowHoneycombLauncher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float,
        forceBrowseMode: Boolean,
    ): Boolean = delegate.onShowHoneycombLauncher(continuousPick, rawX, rawY, forceBrowseMode)
    override fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float) =
        delegate.onHoneycombLauncherPointerMove(rawX, rawY)
    override fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float) =
        delegate.onHoneycombLauncherContinuousRelease(rawX, rawY)
    override fun onShowAppSwitcher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float,
    ): Boolean = delegate.onShowAppSwitcher(continuousPick, rawX, rawY)
    override fun onAppSwitcherPointerMove(rawX: Float, rawY: Float) =
        delegate.onAppSwitcherPointerMove(rawX, rawY)
    override fun onAppSwitcherContinuousRelease(rawX: Float, rawY: Float) =
        delegate.onAppSwitcherContinuousRelease(rawX, rawY)
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
    private val actionExecutor: ActionExecutor,
    private val settingsProvider: () -> AppSettings,
    private val runAfterLayout: (() -> Unit) -> Unit,
    private val onSessionStartCallback: () -> Unit,
    private val onAdjustPanelLayoutCallback: (Float) -> Unit,
    private val notifyPresentationTouchRequirementChanged: () -> Unit,
    private val requestInvalidate: () -> Unit,
    private val indexPanelContentRect: () -> android.graphics.RectF,
    private val onIndexSessionStart: () -> Unit = {},
    private val notifyAccessibilityStructure: () -> Unit = {},
) : GestureSession.Callbacks {
    private var lastAdjustInvalidateMs = 0L

    @Suppress("DEPRECATION")
    private fun invalidateIndexPanel() {
        val rect = indexPanelContentRect()
        if (rect.isEmpty) {
            requestInvalidate()
            return
        }
        val pad = view.resources.displayMetrics.density * 4f
        view.invalidate(
            (rect.left - pad).toInt().coerceAtLeast(0),
            (rect.top - pad).toInt().coerceAtLeast(0),
            (rect.right + pad).toInt().coerceAtMost(view.width.coerceAtLeast(1)),
            (rect.bottom + pad).toInt().coerceAtMost(view.height.coerceAtLeast(1)),
        )
    }

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
                if (mode == OverlayPanelMode.INDEX) {
                    onIndexSessionStart()
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
        notifyAccessibilityStructure()
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
        adjustPanelController.onSessionEnd()
        panelEnterAnimator.resetToComplete()
        layoutCoordinator.syncZoneLayout()
        panelGridSession.reset()
        taskSwitcherController.onSessionEnd()
        quickLauncherController.onSessionEnd()
        shellCoordinator.onSessionEnd()
        HoneycombAppPickerOverlayWindow.onGestureSessionEnd()
        AppSwitcherOverlayWindow.onGestureSessionEnd()
        com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.onGestureSessionEnd()
        layoutCoordinator.notifyOverlayLayoutIfNeeded()
        notifyPresentationTouchRequirementChanged()
        notifyAccessibilityStructure()
    }

    override fun onLeaveOpenFingerTrackingFinished() {
        notifyPresentationTouchRequirementChanged()
    }

    override fun onOpenShellCommandPanel(continuousPick: Boolean) {
        shellCoordinator.onOpenShellCommandPanel(continuousPick)
    }

    override fun onShellCommandPanelContinuousRelease() {
        shellCoordinator.onShellCommandPanelContinuousRelease()
    }

    override fun onShowHoneycombLauncher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float,
        forceBrowseMode: Boolean,
    ): Boolean {
        val settings = settingsProvider()
        return HoneycombAppPickerOverlayWindow.show(
            context = view.context,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
            externalTracking = continuousPick,
            forceBrowseMode = forceBrowseMode,
            onLaunch = { item, longPressArmed ->
                actionExecutor.launchQuickItem(item, settings, longPressArmed = longPressArmed)
            },
        )
    }

    override fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float) {
        HoneycombAppPickerOverlayWindow.updatePointer(rawX, rawY)
    }

    override fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float) {
        HoneycombAppPickerOverlayWindow.confirmSelection(
            rawX = rawX,
            rawY = rawY,
            actionExecutor = actionExecutor,
            settings = settingsProvider(),
        )
        gestureSession.clearHoneycombContinuousPick()
    }

    override fun onShowAppSwitcher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float,
    ): Boolean {
        if (continuousPick) {
            gestureAnimationCoordinator.hide()
        }
        val settings = settingsProvider()
        return AppSwitcherOverlayWindow.show(
            context = view.context,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
            externalTracking = continuousPick,
            onLaunch = { item, longPressArmed ->
                actionExecutor.launchQuickItem(item, settings, longPressArmed = longPressArmed)
            },
            edgePanelSide = gestureSession.sessionSide,
        )
    }

    override fun onAppSwitcherPointerMove(rawX: Float, rawY: Float) {
        AppSwitcherOverlayWindow.updatePointer(rawX, rawY)
    }

    override fun onAppSwitcherContinuousRelease(rawX: Float, rawY: Float) {
        AppSwitcherOverlayWindow.confirmSelection(
            rawX = rawX,
            rawY = rawY,
            actionExecutor = actionExecutor,
            settings = settingsProvider(),
        )
        gestureSession.clearAppSwitcherContinuousPick()
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

    override fun onRequestInvalidate() {
        if (gestureSession.panelMode() == OverlayPanelMode.INDEX) {
            invalidateIndexPanel()
        } else {
            requestInvalidate()
        }
    }

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
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAdjustInvalidateMs < 16L) return
        lastAdjustInvalidateMs = now
        requestInvalidate()
    }
}
