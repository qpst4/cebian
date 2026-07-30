package com.slideindex.app.overlay

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.content.Context
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.gesture.GestureAnglesPreviewStore
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.overlay.corner.CornerGestureHost
import com.slideindex.app.service.OverlayService
import com.slideindex.app.util.ForegroundAppTracker
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Hosts edge gesture overlays on the accessibility service process (SideGesture-style).
 */
class EdgeOverlayHost(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deps: AppDependencies,
) {
    private var overlayManager: OverlayManager? = null
    private var foregroundTracker: ForegroundAppTracker? = null
    private var floatBallController: FloatBallController? = null
    private var cornerGestureHost: CornerGestureHost? = null
    private var settingsJob: Job? = null
    private var previewActive = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null

    fun start() {
        if (overlayManager != null) return
        OverlayPerformanceMonitorBinding.onOverlayShown(
            deps.settingsRepository.readSnapshot(),
            context,
        )
        overlayManager = OverlayManager(
            context = context,
            appRepository = deps.appRepository,
            scope = scope,
            onShellCommandsPersist = { commands ->
                scope.launch { deps.settingsRepository.setShellCommands(commands) }
            },
            onQuickLauncherItemsPersist = { items ->
                scope.launch { deps.settingsRepository.setQuickLauncherItems(items) }
            },
        )
        if (PermissionHelper.hasUsageAccess(context)) {
            foregroundTracker = ForegroundAppTracker(context, scope).also { tracker ->
                scope.launch {
                    tracker.foregroundPackage.collectLatest { packageName ->
                        OverlayService.foregroundPackage = packageName
                        overlayManager?.updateForegroundPackage(packageName)
                    }
                }
            }
        }
        scope.launch(Dispatchers.Default) {
            deps.appRepository.loadApps()
        }
        if (TaskManagerUtil.hasPermission()) {
            TaskManagerUtil.warmUp()
        }
        floatBallController = FloatBallController(context, scope, deps.settingsRepository)
        cornerGestureHost = CornerGestureHost(context, scope, deps).also { it.start() }
        settingsJob = scope.launch {
            combine(
                deps.settingsRepository.gestureSettings,
                deps.settingsRepository.overlaySettings,
            ) { _, _ ->
                deps.settingsRepository.readSnapshot()
            }.collectLatest { settings ->
                if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                    overlayManager?.destroy()
                    floatBallController?.stop()
                    cornerGestureHost?.stop()
                    return@collectLatest
                }
                val effectiveSettings = settings.withGestureAnglesPreview()
                floatBallController?.apply(effectiveSettings)
                updatePerformanceMonitor(effectiveSettings.debugPerformanceMonitorEnabled)
                overlayManager?.applySettings(effectiveSettings)
                if (previewActive) {
                    overlayManager?.setPreviewMode(true, previewContent, previewFocus)
                }
            }
        }
    }

    fun stop() {
        OverlayPerformanceMonitorBinding.onOverlayHidden(context)
        settingsJob?.cancel()
        settingsJob = null
        floatBallController?.stop()
        floatBallController = null
        cornerGestureHost?.stop()
        cornerGestureHost = null
        foregroundTracker?.stop()
        foregroundTracker = null
        overlayManager?.destroy()
        overlayManager = null
        OverlayService.foregroundPackage = null
        previewActive = false
    }

    fun onConfigurationChanged() {
        floatBallController?.onConfigurationChanged()
        cornerGestureHost?.onConfigurationChanged()
    }

    fun reloadApps() {
        overlayManager?.reloadApps()
    }

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        previewActive = enabled
        previewContent = content
        previewFocus = if (enabled) focus else null
        overlayManager?.setPreviewMode(enabled, content, previewFocus)
    }

    fun setGestureAnglesPreview(angles: com.slideindex.app.gesture.GestureAngles?) {
        GestureAnglesPreviewStore.current = angles
        val settings = deps.settingsRepository.readSnapshot().withGestureAnglesPreview()
        overlayManager?.applySettings(settings)
    }

    fun setCornerZonePreviewActive(active: Boolean) {
        cornerGestureHost?.setZonePreviewActive(active)
    }

    fun applyCornerZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) {
        cornerGestureHost?.applyZonePreviewDimensions(
            verticalEdgeWidthDp,
            verticalEdgeHeightDp,
            horizontalEdgeWidthDp,
            horizontalEdgeHeightDp,
        )
    }

    fun updateForegroundPackage(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        OverlayService.foregroundPackage = packageName
        overlayManager?.updateForegroundPackage(packageName)
    }

    fun recoverOverlaysIfIdle() {
        overlayManager?.recoverOverlaysIfIdle()
    }

    fun refreshTriggerVisibility() {
        overlayManager?.onEnvironmentChanged()
    }

    fun refreshTriggerVisuals() {
        overlayManager?.refreshTriggerVisuals()
    }

    fun suspendAllEdgeOverlays() {
        overlayManager?.suspendAllEdgeOverlays()
    }

    fun resumeAllEdgeOverlays() {
        overlayManager?.resumeAllEdgeOverlays()
    }

    fun dispatchExternalGestureAction(
        action: com.slideindex.app.gesture.GestureAction,
        anchorRawY: Float,
        panelSide: com.slideindex.app.overlay.PanelSide? = null,
    ): Boolean =
        overlayManager?.dispatchExternalGestureAction(action, anchorRawY, panelSide) == true

    private fun updatePerformanceMonitor(enabled: Boolean) {
        OverlayPerformanceMonitorBinding.syncUserPreference(enabled, context)
    }

    private fun AppSettings.withGestureAnglesPreview(): AppSettings {
        val preview = GestureAnglesPreviewStore.current ?: return this
        return copy(gestureAngles = preview)
    }
}
