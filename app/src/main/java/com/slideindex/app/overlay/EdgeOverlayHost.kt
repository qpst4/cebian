package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.service.OverlayService
import com.slideindex.app.util.ForegroundAppTracker
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
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
    private var settingsJob: Job? = null
    private var previewActive = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY

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
        settingsJob = scope.launch {
            deps.settingsRepository.settings.collectLatest { settings ->
                if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                    overlayManager?.destroy()
                    return@collectLatest
                }
                updatePerformanceMonitor(settings.debugPerformanceMonitorEnabled)
                overlayManager?.applySettings(settings)
                if (previewActive) {
                    overlayManager?.setPreviewMode(true, previewContent)
                }
            }
        }
    }

    fun stop() {
        OverlayPerformanceMonitorBinding.onOverlayHidden(context)
        settingsJob?.cancel()
        settingsJob = null
        foregroundTracker?.stop()
        foregroundTracker = null
        overlayManager?.destroy()
        overlayManager = null
        OverlayService.foregroundPackage = null
        previewActive = false
    }

    fun reloadApps() {
        overlayManager?.reloadApps()
    }

    fun setPreviewMode(enabled: Boolean, content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY) {
        previewActive = enabled
        previewContent = content
        overlayManager?.setPreviewMode(enabled, content)
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

    fun dispatchExternalGestureAction(action: com.slideindex.app.gesture.GestureAction, anchorRawY: Float): Boolean =
        overlayManager?.dispatchExternalGestureAction(action, anchorRawY) == true

    private fun updatePerformanceMonitor(enabled: Boolean) {
        OverlayPerformanceMonitorBinding.syncUserPreference(enabled, context)
    }
}
