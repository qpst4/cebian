package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.service.ShellCommandEditorTrampoline
import com.slideindex.app.service.ShellCommandPanelTrampoline
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.ShellCommandResultTrampoline
import com.slideindex.app.shell.ShellCommand

internal class ShellPanelOverlayController(
    private val host: Host,
) {
    interface Host {
        val context: Context
        fun settings(): AppSettings
        fun gestureSession(): GestureSession
        fun panelEnterProgress(): Float
        fun viewWidth(): Int
        fun viewHeight(): Int
        fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun invalidate()
        fun post(action: () -> Unit)
        fun requestFocus()
        fun hapticTick()
        fun hapticConfirm()
        fun startPanelExitAnimation(onEnd: () -> Unit)
        fun notifyPresentationTouchRequirementChanged()
        fun onShellCommandsPersist(commands: List<ShellCommand>)
        fun onShellPanelFocusChange(needsFocus: Boolean)
        fun onOverlayWindowSuspend()
        fun onOverlayWindowResume()
        fun onShellPanelAuxiliaryPrepare()
        fun onShellPanelAuxiliaryDismiss()
        fun clearEdgeCaptureTouchActive()
    }

    private val panelController = ShellCommandPanelController(
        object : ShellCommandPanelController.Host {
            override val context: Context get() = host.context
            override fun settings(): AppSettings = host.settings()
            override fun isDialogShowing(): Boolean = isAuxiliaryDialogShowing()
            override fun dismissDialogs() {
                ShellCommandEditorTrampoline.closeIfActive()
                ShellCommandResultTrampoline.closeIfActive()
            }
            override fun requestEndSession() = endSessionAnimated()
            override fun showEditorDialog(
                existing: ShellCommand?,
                shizukuGranted: Boolean,
                onDismissComplete: () -> Unit,
                onSave: (ShellCommand) -> Unit,
                onDelete: (() -> Unit)?,
                onTest: (ShellCommand, (Int, String) -> Unit) -> Unit,
            ) {
                ShellCommandEditorTrampoline.launch(
                    context = host.context,
                    existing = existing,
                    shizukuGranted = shizukuGranted,
                    onPrepare = { prepareAuxiliaryUi() },
                    onDismiss = {
                        host.onShellPanelAuxiliaryDismiss()
                        onDismissComplete()
                        host.notifyPresentationTouchRequirementChanged()
                        syncInputFocus()
                    },
                    onSave = onSave,
                    onDelete = onDelete,
                )
                host.invalidate()
            }
            override fun showResultDialog(
                label: String,
                command: String,
                exitCode: Int,
                output: String,
                onDismissComplete: () -> Unit,
            ) {
                ShellCommandResultTrampoline.launch(
                    context = host.context,
                    result = ShellCommandResultTrampoline.Payload(
                        label = label,
                        command = command,
                        exitCode = exitCode,
                        output = output,
                    ),
                    onPrepare = { prepareAuxiliaryUi() },
                    onDismiss = {
                        host.onShellPanelAuxiliaryDismiss()
                        onDismissComplete()
                        host.notifyPresentationTouchRequirementChanged()
                        syncInputFocus()
                    },
                )
                host.invalidate()
            }
            override fun viewWidth(): Int = host.viewWidth()
            override fun viewHeight(): Int = host.viewHeight()
            override fun dp(value: Float): Float = host.dp(value)
            override fun sp(value: Float): Float = host.sp(value)
            override fun invalidate() {
                host.invalidate()
                if (host.gestureSession().panelMode() == OverlayPanelMode.SHELL_COMMANDS) {
                    host.post { syncInputFocus() }
                }
            }
            override fun post(action: () -> Unit) {
                host.post(action)
            }
            override fun hapticTick() = host.hapticTick()
            override fun hapticConfirm() = host.hapticConfirm()
            override fun onPersist(commands: List<ShellCommand>) {
                host.onShellCommandsPersist(commands)
            }
        },
    )

    private var exiting = false

    internal fun shellCommandPanelController(): ShellCommandPanelController = panelController

    fun syncSettings(settings: AppSettings) {
        panelController.syncSettings(settings)
    }

    fun hasActiveUi(): Boolean = panelController.hasActiveUi()

    fun isAuxiliaryDialogShowing(): Boolean =
        ShellCommandEditorTrampoline.isActive() || ShellCommandResultTrampoline.isActive()

    fun isPanelTrampolineBlockingPassthrough(): Boolean =
        ShellCommandPanelTrampoline.isActive() &&
            !host.gestureSession().shellCommandContinuousPickActive()

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val gestureSession = host.gestureSession()
        val continuousPick = gestureSession.shellCommandContinuousPickActive()
        if (continuousPick && event.actionMasked == MotionEvent.ACTION_DOWN) {
            return true
        }
        val consumed = panelController.handleTouch(
            event = event,
            localX = localX,
            localY = localY,
            releaseImmediateLock = { gestureSession.releaseImmediateGestureLock() },
        )
        if (continuousPick && event.actionMasked == MotionEvent.ACTION_UP &&
            !panelController.hasActiveUi()
        ) {
            if (host.panelEnterProgress() > 0.01f) {
                host.startPanelExitAnimation { gestureSession.endSession() }
            } else {
                gestureSession.endSession()
            }
        }
        return consumed
    }

    fun handleBackPress(): Boolean {
        if (isAuxiliaryDialogShowing()) return false
        val gestureSession = host.gestureSession()
        if (gestureSession.panelMode() != OverlayPanelMode.SHELL_COMMANDS) return false
        if (host.panelEnterProgress() <= 0.01f && !panelController.hasActiveUi()) return false
        val handled = panelController.handleBackPress()
        if (handled) syncInputFocus()
        return handled
    }

    fun draw(canvas: Canvas, panelEnterProgress: Float, panelContentRect: RectF) {
        panelContentRect.set(panelController.panelContentRect)
        panelController.draw(canvas, panelEnterProgress)
    }

    fun onOpenShellCommandPanel(continuousPick: Boolean) {
        val gestureSession = host.gestureSession()
        if (continuousPick) {
            if (gestureSession.panelMode() != OverlayPanelMode.SHELL_COMMANDS) {
                gestureSession.openPanel(OverlayPanelMode.SHELL_COMMANDS)
            }
            host.invalidate()
            return
        }
        if (ShellCommandPanelTrampoline.isActive()) return
        ShellCommandPanelTrampoline.launch(
            context = host.context,
            continuousPick = false,
            onPrepare = { SlideIndexAccessibilityService.suspendAllEdgeOverlays() },
            onDismiss = {
                SlideIndexAccessibilityService.resumeAllEdgeOverlays()
                host.notifyPresentationTouchRequirementChanged()
                syncInputFocus()
            },
            onPersist = host::onShellCommandsPersist,
        )
        host.invalidate()
    }

    fun onShellCommandPanelContinuousRelease() {
        if (ShellCommandPanelTrampoline.isActive()) {
            ShellCommandPanelTrampoline.closeIfActive()
        }
        host.gestureSession().clearShellContinuousPick()
        host.notifyPresentationTouchRequirementChanged()
    }

    fun onSessionStart() {
        panelController.syncSettings(host.settings())
    }

    fun onSessionEnd() {
        if (ShellCommandPanelTrampoline.isActive()) {
            ShellCommandPanelTrampoline.closeIfActive()
        }
        exiting = false
        panelController.reset()
        ShellCommandEditorTrampoline.closeIfActive()
        ShellCommandResultTrampoline.closeIfActive()
        syncInputFocus()
    }

    fun onPanelEnterAnimationEnded() {
        syncInputFocus()
    }

    fun closePanelTrampolineIfActive() {
        if (ShellCommandPanelTrampoline.isActive()) {
            ShellCommandPanelTrampoline.closeIfActive()
        }
    }

    fun closePanelTrampolineIfContinuous() {
        if (ShellCommandPanelTrampoline.isActive()) {
            ShellCommandPanelTrampoline.closeIfContinuous()
        }
    }

    fun clearShellContinuousPick() {
        host.gestureSession().clearShellContinuousPick()
    }

    private fun endSessionAnimated() {
        val gestureSession = host.gestureSession()
        if (gestureSession.panelMode() != OverlayPanelMode.SHELL_COMMANDS) {
            gestureSession.endSession()
            return
        }
        if (exiting) return
        panelController.prepareForPanelExit()
        if (host.panelEnterProgress() > 0.01f) {
            exiting = true
            host.startPanelExitAnimation {
                exiting = false
                gestureSession.endSession()
            }
        } else {
            gestureSession.endSession()
        }
    }

    private fun syncInputFocus() {
        val shellAuxiliaryUiActive = isAuxiliaryDialogShowing()
        val shellOverlayPanelActive = host.gestureSession().panelMode() == OverlayPanelMode.SHELL_COMMANDS &&
            host.panelEnterProgress() > 0.01f
        val needsPresentationFocus = !shellAuxiliaryUiActive && shellOverlayPanelActive && (
            panelController.hasActiveUi() || host.panelEnterProgress() >= 1f
        )
        host.onShellPanelFocusChange(needsPresentationFocus)
        if (!shellAuxiliaryUiActive && needsPresentationFocus) {
            host.requestFocus()
        }
    }

    private fun prepareAuxiliaryUi() {
        host.gestureSession().clearShellContinuousPick()
        host.clearEdgeCaptureTouchActive()
        host.onShellPanelAuxiliaryPrepare()
    }
}
