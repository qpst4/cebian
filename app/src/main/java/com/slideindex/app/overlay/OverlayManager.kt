package com.slideindex.app.overlay

import android.content.Context
import android.view.WindowManager
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.TaskManagerUtil
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
    ) {
        previewMode = enabled
        previewContent = content
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

        leftController?.showEdge()
        rightController?.showEdge()
    }

    private fun shouldSuppressTrigger(): Boolean {
        if (previewMode) return false
        val pkg = foregroundPackage ?: return false
        return pkg in currentSettings.excludedTriggerAppPackages
    }

    private fun applyPreviewToControllers() {
        if (!currentSettings.serviceEnabled) return
        val content = previewContent
        leftController?.setPreviewMode(previewMode, content)
        rightController?.setPreviewMode(previewMode, content)
    }

    fun reloadApps() {
        leftController?.reloadApps()
        rightController?.reloadApps()
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
