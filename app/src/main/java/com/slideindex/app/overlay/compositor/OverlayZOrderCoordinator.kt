package com.slideindex.app.overlay.compositor

/**
 * Applies [OverlayScene] to WM window ordering across compositor, panels, and chrome.
 */
internal object OverlayZOrderCoordinator {
    fun sync() {
        when (OverlaySceneController.scene) {
            OverlayScene.EdgeGestureActive -> {
                OverlayCompositor.bringCompositorToFront()
            }
            OverlayScene.ContentPanelVisible -> {
                OverlayCompositor.bringAboveContentPanels()
            }
            OverlayScene.Idle -> Unit
        }
    }
}
