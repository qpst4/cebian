package com.slideindex.app.overlay

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Dual-window edge overlay per side:
 * - [EdgeTouchCaptureView]: fixed edge strip, always attached (touch capture)
 * - [EdgeGestureOverlayView]: full-screen presentation, attached only while drawing / panels
 *
 * Idle state keeps **capture strips only** so the screen center is never blocked.
 */
class SideOverlayController(
    internal val context: android.content.Context,
    val side: PanelSide,
    windowManager: WindowManager,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit = {},
    private val onQuickLauncherItemsPersist: (List<QuickLauncherItem>) -> Unit = {},
    private val onComposeOverlayDialogStateChanged: () -> Unit = {},
) {
    internal val androidWindowManager = windowManager
    internal var settings: AppSettings = AppSettings()
    internal var screenWidthPx: Int = 0
    internal var screenHeightPx: Int = 0
    internal var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null

    internal val overlayContext = OverlayCompose.themedContext(context)
    internal val windowManager = SideOverlayWindowManager(this)
    internal val renderer = SideOverlayRenderer(this)

    internal val overlayPresentation: EdgeGestureOverlayView?
        get() = windowManager.presentationView

    private var loadJob: Job? = null

    internal val density get() = context.resources.displayMetrics.density

    internal fun shouldShowRuntimeVisuals(): Boolean = !runtimeVisualsSuppressed && !previewMode

    internal fun syncRuntimeVisuals() {
        if (shouldShowRuntimeVisuals()) {
            renderer.syncTriggerVisualWindows()
        } else {
            renderer.detachAllTriggerVisualWindows()
        }
    }

    fun updateSettings(newSettings: AppSettings, screenWidth: Int) {
        val hiddenChanged = newSettings.hiddenAppPackages != settings.hiddenAppPackages
        settings = newSettings
        screenWidthPx = screenWidth
        screenHeightPx = context.resources.displayMetrics.heightPixels
        windowManager.presentationView?.applySettings(newSettings, screenWidth)
        if (windowManager.presentationView != null) {
            preloadApps(force = hiddenChanged)
        }
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        windowManager.syncCaptureWindowLayout()
        syncRuntimeVisuals()
        if (previewMode) {
            windowManager.ensurePresentationAttached()
            windowManager.presentationView?.setPreviewMode(true, previewContent, previewFocus)
            renderer.applyPreviewPresentationWindow()
            windowManager.presentationView?.invalidate()
        }
    }

    fun forceCollapseIfIdle() {
        val view = windowManager.presentationView ?: return
        if (view.isSessionActive() || previewMode) return
        view.forceRecoverInteractionState()
        windowManager.detachPresentationIfIdle()
    }

    internal var runtimeVisualsSuppressed = false
        private set

    fun setRuntimeVisualsSuppressed(suppressed: Boolean) {
        runtimeVisualsSuppressed = suppressed
        if (suppressed) {
            renderer.detachAllTriggerVisualWindows()
        } else if (!previewMode) {
            syncRuntimeVisuals()
        }
    }

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        previewMode = enabled
        previewContent = content
        previewFocus = focus
        syncRuntimeVisuals()
        if (enabled && windowManager.presentationView == null && windowManager.touchCaptureWindows.isNotEmpty()) {
            windowManager.ensurePresentationAttached()
        }
        val view = windowManager.presentationView
        if (view != null) {
            view.setPreviewMode(enabled, content, focus)
            if (enabled) {
                windowManager.ensurePresentationAttached()
                renderer.applyPreviewPresentationWindow()
            } else if (!runtimeVisualsSuppressed) {
                windowManager.detachPresentationIfIdle()
            }
        } else if (enabled) {
            windowManager.ensurePresentationAttached()
            windowManager.presentationView?.let { presentation ->
                presentation.setPreviewMode(enabled, content, focus)
                renderer.applyPreviewPresentationWindow()
            }
        }
    }

    fun showEdge() {
        screenWidthPx = context.resources.displayMetrics.widthPixels
        screenHeightPx = context.resources.displayMetrics.heightPixels
        if (windowManager.overlayLayoutSuspended()) return
        val existingPresentation = windowManager.presentationView
        if (existingPresentation != null) {
            if (windowManager.touchCaptureWindows.isEmpty()) {
                windowManager.reattachCaptureWindows()
            }
            existingPresentation.applySettings(settings, screenWidthPx)
            windowManager.syncCaptureWindows(existingPresentation)
            syncRuntimeVisuals()
            if (previewMode) {
                existingPresentation.setPreviewMode(true, previewContent, previewFocus)
                windowManager.ensurePresentationAttached()
                renderer.applyPreviewPresentationWindow()
                existingPresentation.invalidate()
            }
            return
        }
        if (windowManager.touchCaptureWindows.isNotEmpty()) {
            if (previewMode) {
                windowManager.ensurePresentationAttached()
                windowManager.presentationView?.setPreviewMode(true, previewContent, previewFocus)
                renderer.applyPreviewPresentationWindow()
            }
            windowManager.presentationView?.let { presentation ->
                presentation.applySettings(settings, screenWidthPx)
                windowManager.syncCaptureWindows(presentation)
            }
            syncRuntimeVisuals()
            if (previewMode) {
                windowManager.presentationView?.invalidate()
            }
            return
        }

        val container = FrameLayout(overlayContext)
        val presentation = EdgeGestureOverlayView(
            context = overlayContext,
            side = side,
            appRepository = appRepository,
            onSessionStartCallback = {
                windowManager.presentationView?.setPreviewMode(false)
                windowManager.ensurePresentationAttached()
                windowManager.syncPresentationTouchState()
                windowManager.syncCaptureWindowLayout()
            },
            onSessionEndCallback = {
                if (windowManager.presentationView?.keepsOverlayExpanded() != true &&
                    windowManager.presentationView?.isSessionActive() != true
                ) {
                    if (previewMode) {
                        windowManager.presentationView?.setPreviewMode(true, previewContent, previewFocus)
                        windowManager.ensurePresentationAttached()
                        renderer.applyPreviewPresentationWindow()
                    } else {
                        windowManager.presentationView?.forceRecoverInteractionState()
                        windowManager.detachPresentationIfIdle()
                    }
                } else {
                    windowManager.syncPresentationTouchState()
                }
                windowManager.syncCaptureWindowLayout()
            },
            onGestureTrackingStartCallback = {
                if (previewMode) {
                    windowManager.presentationView?.setPreviewMode(false)
                }
                windowManager.ensurePresentationAttached()
                windowManager.syncPresentationTouchState()
                TaskManagerUtil.ensureServiceBound()
            },
            onAdjustPanelLayoutCallback = { _ ->
                windowManager.presentationView?.setPreviewMode(false)
                windowManager.presentationView?.applyAdjustPanelOverlayLayout()
                windowManager.ensurePresentationAttached()
                windowManager.syncPresentationTouchState()
            },
            onAdjustPanelDismissCallback = {
                windowManager.syncPresentationTouchState()
                windowManager.detachPresentationIfIdle()
            },
            onClickPassthroughCallback = { rawX, rawY, onComplete ->
                val handler = clickPassthroughHandler
                if (handler != null) {
                    handler(rawX, rawY, onComplete)
                } else {
                    onComplete()
                }
            },
            onShellCommandsPersist = onShellCommandsPersist,
            onQuickLauncherItemsPersist = onQuickLauncherItemsPersist,
            onShellPanelFocusChange = { focusable -> windowManager.setPresentationFocusable(focusable) },
            onOverlayWindowSuspend = { suspendEdgeOverlay() },
            onOverlayWindowResume = { resumeEdgeOverlay() },
            onOverlayPresentationSuspend = { suspendPresentationForShellPanelActivity() },
            onOverlayPresentationResume = { resumePresentationIfNeeded() },
            onShellPanelAuxiliaryPrepare = { suspendEdgeOverlay() },
            onShellPanelAuxiliaryDismiss = { resumeEdgeOverlay() },
            overlayBrightness = windowManager.overlayBrightness,
        ).also { view ->
            view.onPresentationTouchRequirementChanged = {
                if (!windowManager.edgeOverlayDetached && !windowManager.overlayLayoutSuspended()) {
                    if (view.needsPresentationDirectTouch() && !view.presentationShouldPassthroughTouches()) {
                        windowManager.ensurePresentationAttached()
                    }
                    windowManager.syncPresentationTouchState()
                    windowManager.syncCaptureWindowLayout()
                    if (view.presentationShouldPassthroughTouches()) {
                        view.syncOverlayDialogZOrder()
                    }
                    windowManager.detachPresentationIfIdle()
                }
                onComposeOverlayDialogStateChanged()
            }
        }

        container.addView(
            presentation,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        GestureAnimationOverlayRegistry.controller(side).attach(container, overlayContext)

        val params = windowManager.createPresentationLayoutParams()
        OverlayWindowTypes.applyFullScreen(params)
        presentation.applySettings(settings, screenWidthPx)
        presentation.applyExpandedOverlayLayout()

        runCatching {
            windowManager.attachCaptureWindows(presentation)
            windowManager.presentationView = presentation
            windowManager.presentationContainer = container
            windowManager.presentationParams = params
            windowManager.presentationAttached = false
            TaskManagerUtil.ensureServiceBound()
            preloadApps()
            if (previewMode) {
                presentation.setPreviewMode(true, previewContent, previewFocus)
                windowManager.ensurePresentationAttached()
                renderer.applyPreviewPresentationWindow()
            }
        }.onFailure {
            Log.e(TAG, "Failed to show overlay", it)
            GestureAnimationOverlayRegistry.controller(side).detach()
            windowManager.detachAllCaptureWindows()
            windowManager.presentationView = null
            windowManager.presentationContainer = null
            windowManager.presentationParams = null
            windowManager.presentationAttached = false
        }
    }

    fun hideEdge() {
        windowManager.detachPresentationWindow()
        windowManager.detachAllCaptureWindows()
        GestureAnimationOverlayRegistry.controller(side).detach()
        windowManager.presentationView = null
        windowManager.presentationContainer = null
        windowManager.presentationParams = null
        windowManager.presentationAttached = false
        windowManager.edgeOverlayDetached = false
    }

    fun suspendEdgeOverlay() {
        setRuntimeVisualsSuppressed(true)
        windowManager.suspendEdgeOverlay()
    }

    /** Detach presentation only so a shell-panel Activity stays visible while edge capture remains. */
    fun suspendPresentationForShellPanelActivity() {
        windowManager.detachPresentationWindow()
    }

    fun resumePresentationIfNeeded() {
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        val view = windowManager.presentationView ?: return
        if (previewMode || view.isSessionActive() || view.keepsOverlayExpanded()) {
            windowManager.ensurePresentationAttached()
        }
        windowManager.syncPresentationTouchState()
    }

    fun resumeEdgeOverlay() {
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        windowManager.resumeEdgeOverlay()
        setRuntimeVisualsSuppressed(false)
    }

    fun suspendCapturesForComposeDialog() {
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        windowManager.detachTouchCaptureViewsOnly()
        windowManager.presentationView?.syncOverlayDialogZOrder()
    }

    fun reloadApps() {
        preloadApps(force = true)
    }

    fun refreshTriggerVisualWindows() {
        if (windowManager.edgeOverlayDetached ||
            windowManager.overlayLayoutSuspended() ||
            windowManager.presentationView == null
        ) return
        syncRuntimeVisuals()
    }

    /**
     * Attaches the full-screen presentation window before shake / other external panel triggers.
     * Idle edge overlays keep only capture strips; without this, panel commands run on an
     * in-memory [EdgeGestureOverlayView] that is not on screen.
     */
    fun prepareExternalGestureDispatch(): Boolean {
        if (OverlayTrampolineGuard.blocksOverlayResume()) return false
        if (windowManager.edgeOverlayDetached) {
            resumeEdgeOverlay()
        }
        val view = windowManager.presentationView ?: return false
        view.setPreviewMode(false)
        view.applyExpandedOverlayLayout()
        windowManager.ensurePresentationAttached()
        windowManager.syncPresentationTouchState()
        windowManager.syncCaptureWindowLayout()
        return windowManager.presentationAttached
    }

    fun destroy() {
        loadJob?.cancel()
        hideEdge()
    }

    private fun preloadApps(force: Boolean = false) {
        if (!force) {
            val cached = appRepository.getCachedApps()
                .filter { it.packageName !in settings.hiddenAppPackages }
            if (cached.isNotEmpty()) {
                windowManager.presentationView?.setApps(cached)
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val apps = appRepository.loadApps(force = force)
                .filter { it.packageName !in settings.hiddenAppPackages }
            if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return@launch
            windowManager.presentationView?.setApps(apps)
        }
    }

    companion object {
        private const val TAG = "SideOverlayController"
    }
}
