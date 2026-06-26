package com.slideindex.app.overlay

import android.content.Context
import android.view.WindowManager
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope

class OverlayManager(
    private val context: Context,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var leftController: SideOverlayController? = null
    private var rightController: SideOverlayController? = null
    private var currentSettings: AppSettings = AppSettings()
    private var previewMode = false
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
        refreshTriggerVisibility()
    }

    fun updateForegroundPackage(packageName: String?) {
        if (foregroundPackage == packageName) return
        foregroundPackage = packageName
        refreshTriggerVisibility()
    }

    fun setPreviewMode(enabled: Boolean) {
        previewMode = enabled
        applyPreviewToControllers()
        refreshTriggerVisibility()
    }

    private fun syncControllers(settings: AppSettings) {
        val screenWidth = context.resources.displayMetrics.widthPixels

        if (settings.leftEdgeEnabled) {
            if (leftController == null) {
                leftController = SideOverlayController(
                    context = context,
                    side = PanelSide.LEFT,
                    windowManager = windowManager,
                    appRepository = appRepository,
                    scope = scope,
                )
            }
            leftController?.updateSettings(settings, screenWidth)
        } else {
            leftController?.destroy()
            leftController = null
        }

        if (settings.rightEdgeEnabled) {
            if (rightController == null) {
                rightController = SideOverlayController(
                    context = context,
                    side = PanelSide.RIGHT,
                    windowManager = windowManager,
                    appRepository = appRepository,
                    scope = scope,
                )
            }
            rightController?.updateSettings(settings, screenWidth)
        } else {
            rightController?.destroy()
            rightController = null
        }

        applyPreviewToControllers()
    }

    private fun refreshTriggerVisibility() {
        if (!currentSettings.serviceEnabled) return

        if (shouldSuppressTrigger()) {
            leftController?.hideEdge()
            rightController?.hideEdge()
            return
        }

        if (currentSettings.leftEdgeEnabled) {
            leftController?.showEdge()
        }
        if (currentSettings.rightEdgeEnabled) {
            rightController?.showEdge()
        }
    }

    private fun shouldSuppressTrigger(): Boolean {
        if (previewMode) return false
        val pkg = foregroundPackage ?: return false
        return pkg in currentSettings.excludedTriggerAppPackages
    }

    private fun applyPreviewToControllers() {
        if (!currentSettings.serviceEnabled) return
        leftController?.setPreviewMode(previewMode && currentSettings.leftEdgeEnabled)
        rightController?.setPreviewMode(previewMode && currentSettings.rightEdgeEnabled)
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
}
