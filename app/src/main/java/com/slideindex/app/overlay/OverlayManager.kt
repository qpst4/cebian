package com.slideindex.app.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.overlay.compositor.OverlayCompositor
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TriggerVisibility
import kotlinx.coroutines.CoroutineScope

class OverlayManager(
    private val context: Context,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val onShellCommandsPersist: (List<com.slideindex.app.shell.ShellCommand>) -> Unit = {},
    private val onQuickLauncherPanelItemsPersist: (String, List<QuickLauncherItem>) -> Unit = { _, _ -> },
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var leftController: SideOverlayController? = null
    private var rightController: SideOverlayController? = null
    private var bottomController: SideOverlayController? = null
    private var topController: SideOverlayController? = null
    private var currentSettings: AppSettings = AppSettings()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null
    private var foregroundPackage: String? = null
    private var triggersSuppressed = false
    private var triggersShown = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshVisibilityPending = false

    fun applySettings(settings: AppSettings) {
        currentSettings = settings
        if (!settings.serviceEnabled) {
            clearAllOverlayBrightness()
            leftController?.destroy()
            rightController?.destroy()
            bottomController?.destroy()
            topController?.destroy()
            leftController = null
            rightController = null
            bottomController = null
            topController = null
            triggersShown = false
            triggersSuppressed = false
            return
        }

        syncControllers(settings)
        if (settings.serviceEnabled) {
            clearAllOverlayBrightness()
        }
        val suppressRuntimeVisuals = previewMode && previewFocus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        bottomController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        topController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        recoverOverlaysIfIdle()
        ensureSideEdgesForHandles(settings)
        refreshTriggerVisibility()
    }

    private fun ensureSideEdgesForHandles(settings: AppSettings) {
        if (!settings.serviceEnabled || triggersSuppressed) return
        ensureSideEdge(PanelSide.LEFT, leftController, settings)
        ensureSideEdge(PanelSide.RIGHT, rightController, settings)
        ensureSideEdge(PanelSide.BOTTOM, bottomController, settings)
        ensureSideEdge(PanelSide.TOP, topController, settings)
    }

    private fun ensureSideEdge(side: PanelSide, controller: SideOverlayController?, settings: AppSettings) {
        if (controller == null) return
        val hasHandles = settings.triggerHandles(side).isNotEmpty()
        if (!hasHandles) {
            controller.hideEdge()
            return
        }
        if (triggersShown && !controller.isEdgeInitialized()) {
            controller.showEdge()
        }
    }

    fun recoverOverlaysIfIdle() {
        if (!currentSettings.serviceEnabled) return
        leftController?.forceCollapseIfIdle()
        rightController?.forceCollapseIfIdle()
        bottomController?.forceCollapseIfIdle()
        topController?.forceCollapseIfIdle()
    }

    fun updateForegroundPackage(packageName: String?) {
        if (foregroundPackage == packageName) return
        foregroundPackage = packageName
        scheduleRefreshTriggerVisibility()
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
        bottomController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        topController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
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
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
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
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged,
            )
        }
        rightController?.updateSettings(settings, screenWidth)

        if (bottomController == null) {
            bottomController = SideOverlayController(
                context = context,
                side = PanelSide.BOTTOM,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged,
            )
        }
        bottomController?.updateSettings(settings, screenWidth)

        if (topController == null) {
            topController = SideOverlayController(
                context = context,
                side = PanelSide.TOP,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged,
            )
        }
        topController?.updateSettings(settings, screenWidth)

        applyPreviewToControllers()
    }

    private fun refreshTriggerVisibility() {
        refreshTriggerVisibilityNow()
    }

    private fun scheduleRefreshTriggerVisibility() {
        if (refreshVisibilityPending) return
        refreshVisibilityPending = true
        mainHandler.postDelayed({
            refreshVisibilityPending = false
            refreshTriggerVisibilityNow()
        }, REFRESH_VISIBILITY_DEBOUNCE_MS)
    }

    private fun refreshTriggerVisibilityNow() {
        if (!currentSettings.serviceEnabled) return
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) return

        val suppress = shouldSuppressTrigger()
        if (suppress) {
            if (!triggersSuppressed) {
                leftController?.hideEdge()
                rightController?.hideEdge()
                bottomController?.hideEdge()
                topController?.hideEdge()
                triggersSuppressed = true
                triggersShown = false
            }
            return
        }

        triggersSuppressed = false
        if (!triggersShown) {
            clearAllOverlayBrightness()
            TaskManagerUtil.ensureServiceBound()
            triggersShown = true
            ensureSideEdgesForHandles(currentSettings)
        }
    }

    fun onEnvironmentChanged() {
        scheduleRefreshTriggerVisibility()
    }

    fun refreshTriggerVisuals() {
        if (!currentSettings.serviceEnabled) return
        val suppressRuntimeVisuals = previewMode && previewFocus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        bottomController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        topController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        leftController?.refreshTriggerVisualWindows()
        rightController?.refreshTriggerVisualWindows()
        bottomController?.refreshTriggerVisualWindows()
        topController?.refreshTriggerVisualWindows()
    }

    fun bringEdgeChromeAbovePanels(forceReAdd: Boolean = true, sides: Set<PanelSide>? = null) {
        if (!currentSettings.serviceEnabled) return
        val target = sides ?: PanelSide.entries.toSet()
        if (PanelSide.LEFT in target) leftController?.bringEdgeWindowsAbovePanels(forceReAdd)
        if (PanelSide.RIGHT in target) rightController?.bringEdgeWindowsAbovePanels(forceReAdd)
        if (PanelSide.BOTTOM in target) bottomController?.bringEdgeWindowsAbovePanels(forceReAdd)
        if (PanelSide.TOP in target) topController?.bringEdgeWindowsAbovePanels(forceReAdd)
    }

    fun edgePresentationNeedsChromeRaise(): Boolean {
        if (!currentSettings.serviceEnabled) return false
        return sequenceOf(leftController, rightController, bottomController, topController)
            .filterNotNull()
            .any { it.edgePresentationNeedsChromeRaise() }
    }

    fun notifyEdgeChromeBelowPanel() {
        if (!currentSettings.serviceEnabled) return
        leftController?.markChromeBelowPanel()
        rightController?.markChromeBelowPanel()
        bottomController?.markChromeBelowPanel()
        topController?.markChromeBelowPanel()
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
        if (focus?.showPairedGroup == true && focus.side.isHorizontalEdge) {
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
        bottomController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.BOTTOM),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.BOTTOM },
        )
        topController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.TOP),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.TOP },
        )
    }

    fun reloadApps() {
        leftController?.reloadApps()
        rightController?.reloadApps()
        bottomController?.reloadApps()
        topController?.reloadApps()
    }

    fun suspendAllEdgeOverlays() {
        leftController?.suspendEdgeOverlay()
        rightController?.suspendEdgeOverlay()
        bottomController?.suspendEdgeOverlay()
        topController?.suspendEdgeOverlay()
    }

    fun resumeAllEdgeOverlays() {
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        leftController?.resumeEdgeOverlay()
        rightController?.resumeEdgeOverlay()
        bottomController?.resumeEdgeOverlay()
        topController?.resumeEdgeOverlay()
    }

    fun suspendEdgeCapturesForPassthrough() {
        leftController?.suspendCaptureForPassthrough()
        rightController?.suspendCaptureForPassthrough()
        bottomController?.suspendCaptureForPassthrough()
        topController?.suspendCaptureForPassthrough()
    }

    fun resumeEdgeCapturesAfterPassthrough() {
        leftController?.resumeCaptureAfterPassthrough()
        rightController?.resumeCaptureAfterPassthrough()
        bottomController?.resumeCaptureAfterPassthrough()
        topController?.resumeCaptureAfterPassthrough()
    }

    fun dispatchExternalGestureAction(
        action: com.slideindex.app.gesture.GestureAction,
        anchorRawY: Float,
        panelSide: PanelSide? = null,
    ): Boolean {
        if (!currentSettings.serviceEnabled) return false
        refreshTriggerVisibility()
        val controller = controllerForPanelSide(panelSide)
            ?.takeIf { it.overlayPresentation != null }
            ?: return false
        if (!controller.prepareExternalGestureDispatch()) return false
        val view = controller.overlayPresentation ?: return false
        return view.dispatchExternalAction(action, anchorRawY)
    }

    private fun controllerForPanelSide(panelSide: PanelSide?): SideOverlayController? {
        if (panelSide != null) {
            return when (panelSide) {
                PanelSide.LEFT -> leftController
                PanelSide.RIGHT -> rightController
                PanelSide.BOTTOM -> bottomController
                PanelSide.TOP -> topController
            }
        }
        return leftController?.takeIf { it.overlayPresentation != null }
            ?: rightController?.takeIf { it.overlayPresentation != null }
            ?: bottomController?.takeIf { it.overlayPresentation != null }
            ?: topController?.takeIf { it.overlayPresentation != null }
    }

    private fun onComposeOverlayDialogStateChanged() {
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) {
            suspendAllEdgeOverlays()
            return
        }
        val dialogOpen =
            leftController?.overlayPresentation?.presentationShouldPassthroughTouches() == true ||
                rightController?.overlayPresentation?.presentationShouldPassthroughTouches() == true ||
                bottomController?.overlayPresentation?.presentationShouldPassthroughTouches() == true ||
                topController?.overlayPresentation?.presentationShouldPassthroughTouches() == true
        if (!dialogOpen) return
        leftController?.suspendCapturesForComposeDialog()
        rightController?.suspendCapturesForComposeDialog()
        bottomController?.suspendCapturesForComposeDialog()
        topController?.suspendCapturesForComposeDialog()
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        refreshVisibilityPending = false
        leftController?.destroy()
        rightController?.destroy()
        bottomController?.destroy()
        topController?.destroy()
        leftController = null
        rightController = null
        bottomController = null
        topController = null
        triggersShown = false
        triggersSuppressed = false
    }

    private fun clearAllOverlayBrightness() {
        OverlayCompositor.clearBrightnessPreview()
        OverlayCompositor.detach()
        leftController?.clearOverlayWindowBrightness()
        rightController?.clearOverlayWindowBrightness()
        bottomController?.clearOverlayWindowBrightness()
        topController?.clearOverlayWindowBrightness()
    }

    private companion object {
        private const val REFRESH_VISIBILITY_DEBOUNCE_MS = 150L
    }

    private fun performClickPassthrough(rawX: Float, rawY: Float, onComplete: () -> Unit) {
        OverlayPassthrough.run(
            hideTriggers = ::suspendEdgeCapturesForPassthrough,
            showTriggers = ::resumeEdgeCapturesAfterPassthrough,
            rawX = rawX,
            rawY = rawY,
            onComplete = onComplete,
        )
    }
}
