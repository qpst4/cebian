package com.slideindex.app.overlay

import android.content.Context
import android.view.WindowManager
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TriggerVisibility
import kotlinx.coroutines.CoroutineScope

class OverlayManager(
    private val context: Context,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val onShellCommandsPersist: (List<com.slideindex.app.shell.ShellCommand>) -> Unit = {},
    private val onQuickLauncherItemsPersist: (List<com.slideindex.app.launcher.QuickLauncherItem>) -> Unit = {},
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var leftController: SideOverlayController? = null
    private var rightController: SideOverlayController? = null
    private var currentSettings: AppSettings = AppSettings()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null
    private var foregroundPackage: String? = null

    fun applySettings(settings: AppSettings) {
        currentSettings = settings
        if (!settings.serviceEnabled) {
            leftController?.destroy()
            rightController?.destroy()
            leftController = null
            rightController = null
            return
        }

        syncControllers(settings)
        val suppressRuntimeVisuals = previewMode && previewFocus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        recoverOverlaysIfIdle()
        refreshTriggerVisibility()
    }

    fun recoverOverlaysIfIdle() {
        if (!currentSettings.serviceEnabled) return
        leftController?.forceCollapseIfIdle()
        rightController?.forceCollapseIfIdle()
    }

    fun updateForegroundPackage(packageName: String?) {
        if (foregroundPackage == packageName) return
        foregroundPackage = packageName
        TaskManagerUtil.ensureServiceBound()
        refreshTriggerVisibility()
    }

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        previewMode = enabled
        previewContent = content
        previewFocus = if (enabled) focus else null
        val suppressRuntimeVisuals = enabled && focus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        applyPreviewToControllers()
        refreshTriggerVisibility()
    }

    private fun syncControllers(settings: AppSettings) {
        val screenWidth = context.resources.displayMetrics.widthPixels

        if (leftController == null) {
            leftController = SideOverlayController(
                context = context,
                side = PanelSide.LEFT,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherItemsPersist = onQuickLauncherItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged,
            )
        }
        leftController?.updateSettings(settings, screenWidth)

        if (rightController == null) {
            rightController = SideOverlayController(
                context = context,
                side = PanelSide.RIGHT,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherItemsPersist = onQuickLauncherItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged,
            )
        }
        rightController?.updateSettings(settings, screenWidth)

        applyPreviewToControllers()
    }

    private fun refreshTriggerVisibility() {
        if (!currentSettings.serviceEnabled) return

        if (shouldSuppressTrigger()) {
            leftController?.hideEdge()
            rightController?.hideEdge()
            return
        }
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) return

        val screenWidth = context.resources.displayMetrics.widthPixels
        leftController?.updateSettings(currentSettings, screenWidth)
        rightController?.updateSettings(currentSettings, screenWidth)

        leftController?.showEdge()
        rightController?.showEdge()
    }

    fun onEnvironmentChanged() {
        refreshTriggerVisibility()
    }

    fun refreshTriggerVisuals() {
        if (!currentSettings.serviceEnabled) return
        val suppressRuntimeVisuals = previewMode && previewFocus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        leftController?.refreshTriggerVisualWindows()
        rightController?.refreshTriggerVisualWindows()
    }

    private fun shouldSuppressTrigger(): Boolean {
        if (previewMode) return false
        return TriggerVisibility.shouldSuppress(
            settings = currentSettings,
            context = context,
            foregroundPackage = foregroundPackage,
        )
    }

    private fun applyPreviewToControllers() {
        if (!currentSettings.serviceEnabled) return
        val content = previewContent
        val focus = previewFocus
        if (focus?.showPairedGroup == true) {
            leftController?.setPreviewMode(
                enabled = previewMode,
                content = content,
                focus = focus.copy(side = PanelSide.LEFT),
            )
            rightController?.setPreviewMode(
                enabled = previewMode,
                content = content,
                focus = focus.copy(side = PanelSide.RIGHT),
            )
            return
        }
        leftController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.LEFT),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.LEFT },
        )
        rightController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.RIGHT),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.RIGHT },
        )
    }

    fun reloadApps() {
        leftController?.reloadApps()
        rightController?.reloadApps()
    }

    fun suspendAllEdgeOverlays() {
        leftController?.suspendEdgeOverlay()
        rightController?.suspendEdgeOverlay()
    }

    fun resumeAllEdgeOverlays() {
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        leftController?.resumeEdgeOverlay()
        rightController?.resumeEdgeOverlay()
    }

    fun dispatchExternalGestureAction(
        action: com.slideindex.app.gesture.GestureAction,
        anchorRawY: Float,
    ): Boolean {
        if (!currentSettings.serviceEnabled) return false
        refreshTriggerVisibility()
        val controller = leftController?.takeIf { it.overlayPresentation != null }
            ?: rightController?.takeIf { it.overlayPresentation != null }
            ?: return false
        if (!controller.prepareExternalGestureDispatch()) return false
        val view = controller.overlayPresentation ?: return false
        return view.dispatchExternalAction(action, anchorRawY)
    }

    private fun onComposeOverlayDialogStateChanged() {
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) {
            suspendAllEdgeOverlays()
            return
        }
        val dialogOpen =
            leftController?.overlayPresentation?.presentationShouldPassthroughTouches() == true ||
                rightController?.overlayPresentation?.presentationShouldPassthroughTouches() == true
        if (!dialogOpen) return
        leftController?.suspendCapturesForComposeDialog()
        rightController?.suspendCapturesForComposeDialog()
    }

    fun destroy() {
        leftController?.destroy()
        rightController?.destroy()
        leftController = null
        rightController = null
    }

    private fun performClickPassthrough(rawX: Float, rawY: Float, onComplete: () -> Unit) {
        OverlayPassthrough.run(
            hideTriggers = {
                leftController?.hideEdge()
                rightController?.hideEdge()
            },
            showTriggers = {
                refreshTriggerVisibility()
            },
            rawX = rawX,
            rawY = rawY,
            onComplete = onComplete,
        )
    }
}
