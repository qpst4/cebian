package com.slideindex.app.overlay.compositor

import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatingPointerOverlayWindow

/**
 * Applies [OverlayScene] to WM window ordering across compositor, panels, and chrome.
 */
internal object OverlayZOrderCoordinator {
    fun sync() {
        when (OverlaySceneController.scene) {
            OverlayScene.EdgeGestureActive -> {
                OverlayCompositor.bringCompositorToFront()
                FloatBallOverlay.scheduleChromeAbovePanels()
                FloatingPointerOverlayWindow.bringToFront(forceReAdd = false)
            }
            OverlayScene.ContentPanelVisible -> {
                OverlayCompositor.bringAboveContentPanels()
                FloatBallOverlay.scheduleChromeAbovePanels()
                FloatingPointerOverlayWindow.bringToFront(forceReAdd = false)
            }
            OverlayScene.Idle -> Unit
        }
    }
}
