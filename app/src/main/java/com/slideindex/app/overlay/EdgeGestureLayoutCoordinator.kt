package com.slideindex.app.overlay

import android.content.res.Resources
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.service.QuickLauncherAddTrampoline

internal sealed class OverlayTouchLayout {
    data class TriggerCollapsed(val bounds: CollapsedWindowBounds) : OverlayTouchLayout()
    data class GestureTracking(val bounds: CollapsedWindowBounds) : OverlayTouchLayout()
    data object FullScreen : OverlayTouchLayout()
    data object AdjustPanel : OverlayTouchLayout()
}

internal class EdgeGestureLayoutCoordinator(
    private val context: android.content.Context,
    private val resources: Resources,
    private val zoneLayout: GestureZoneLayout,
    private val gestureSession: GestureSession,
    private val adjustPanelController: AdjustPanelOverlayController,
    private val quickLauncherController: QuickLauncherOverlayController,
    private val shellCoordinator: ShellPanelOverlayController,
    private val settingsProvider: () -> AppSettings,
    private val previewModeProvider: () -> Boolean,
    private val viewSizeProvider: () -> Pair<Int, Int>,
    private val onSessionEnd: () -> Unit,
) {
    var overlayTouchLayout: OverlayTouchLayout = OverlayTouchLayout.FullScreen
        private set

    fun applyExpandedOverlayLayout() {
        overlayTouchLayout = OverlayTouchLayout.FullScreen
        syncZoneLayout()
    }

    fun applyGestureTrackingLayout(bounds: CollapsedWindowBounds) {
        overlayTouchLayout = OverlayTouchLayout.GestureTracking(bounds)
        syncZoneLayout()
    }

    fun applyAdjustPanelOverlayLayout() {
        overlayTouchLayout = OverlayTouchLayout.AdjustPanel
        syncZoneLayout()
    }

    fun syncZoneLayout() = syncZoneLayout(settingsProvider())

    fun syncZoneLayout(settings: AppSettings) {
        val (screenW, screenH) = OverlayScreenMetrics.sizePx(context)
        val (viewW, viewH) = viewSizeProvider()
        val layoutW = maxOf(screenW, viewW)
        val layoutH = maxOf(screenH, viewH)
        zoneLayout.update(
            settings = settings,
            viewWidth = layoutW,
            viewHeight = layoutH,
            density = resources.displayMetrics.density,
            sessionActive = gestureSession.isActive(),
            previewMode = previewModeProvider(),
            layoutHeight = layoutH,
            windowOffsetY = 0f,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
        )
    }

    fun activeTriggerZoneRect(): android.graphics.RectF =
        if (gestureSession.isActive()) {
            zoneLayout.triggerZoneRect(gestureSession.activeHandleId())
        } else {
            zoneLayout.triggerZoneUnionRect()
        }

    fun needsPresentationDirectTouch(): Boolean {
        if (gestureSession.panelMode() != OverlayPanelMode.NONE) return true
        if (previewModeProvider()) return false
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) return false
        if (adjustPanelController.hasAdjustPanel()) return true
        if (quickLauncherController.isComposeOverlayDialogShowing() ||
            shellCoordinator.isAuxiliaryDialogShowing()
        ) {
            return true
        }
        return false
    }

    fun presentationShouldPassthroughTouches(): Boolean =
        QuickLauncherAddTrampoline.isActive() ||
            shellCoordinator.isAuxiliaryDialogShowing() ||
            quickLauncherController.isOverlayDialogShowing()

    fun composeOverlayDialogShowing(): Boolean =
        QuickLauncherAddTrampoline.isActive() ||
            shellCoordinator.isAuxiliaryDialogShowing() ||
            shellCoordinator.isPanelTrampolineBlockingPassthrough() ||
            quickLauncherController.isOverlayDialogShowing()

    fun keepsOverlayExpanded(): Boolean =
        OverlayTrampolineGuard.blocksOverlayPresentationTouch() ||
            gestureSession.isActive() ||
            gestureSession.panelMode() != OverlayPanelMode.NONE ||
            adjustPanelController.hasAdjustPanel() ||
            (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS &&
                shellCoordinator.hasActiveUi())

    fun notifyOverlayLayoutIfNeeded() {
        if (!keepsOverlayExpanded() && !gestureSession.isActive()) {
            onSessionEnd()
        }
    }
}
