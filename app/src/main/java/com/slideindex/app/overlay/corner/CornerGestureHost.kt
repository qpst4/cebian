package com.slideindex.app.overlay.corner

import android.content.Context
import com.slideindex.app.data.AppRepository
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CornerGestureHost(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deps: AppDependencies,
) {
    private var controller: CornerGestureController? = null

    fun start() {
        if (controller != null) return
        controller = CornerGestureController(
            context = context,
            appRepository = deps.appRepository,
            scope = scope,
            onShellCommandsPersist = { commands ->
                scope.launch { deps.settingsRepository.setShellCommands(commands) }
            },
        )
        scope.launch {
            combine(
                deps.settingsRepository.gestureSettings,
                deps.settingsRepository.overlaySettings,
            ) { _, _ ->
                deps.settingsRepository.readSnapshot()
            }.collectLatest { settings ->
                controller?.applySettings(settings)
            }
        }
    }

    fun stop() {
        controller?.destroy()
        controller = null
    }

    fun onConfigurationChanged() {
        controller?.onConfigurationChanged()
    }

    fun setZonePreviewActive(active: Boolean) {
        controller?.setZonePreviewActive(active)
    }

    fun applyZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) {
        controller?.applyZonePreviewDimensions(
            verticalEdgeWidthDp,
            verticalEdgeHeightDp,
            horizontalEdgeWidthDp,
            horizontalEdgeHeightDp,
        )
    }

    fun applySettings(settings: AppSettings) {
        controller?.applySettings(settings)
    }
}
