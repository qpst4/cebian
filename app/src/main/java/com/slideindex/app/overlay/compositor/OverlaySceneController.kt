package com.slideindex.app.overlay.compositor

import android.os.Handler
import android.os.Looper
import com.slideindex.app.overlay.FloatBallOverlay

/**
 * Single source of truth for overlay scene transitions.
 */
object OverlaySceneController {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var scene: OverlayScene = OverlayScene.Idle
        private set

    fun isEdgeGestureActive(): Boolean = scene is OverlayScene.EdgeGestureActive

    fun onEdgeGestureStarted() {
        runOnMain {
            if (scene is OverlayScene.EdgeGestureActive) {
                OverlayCompositor.bringCompositorToFront()
                return@runOnMain
            }
            scene = OverlayScene.EdgeGestureActive
            OverlayZOrderCoordinator.sync()
        }
    }

    fun onEdgeGestureEnded() {
        runOnMain {
            scene = when (scene) {
                is OverlayScene.EdgeGestureActive -> OverlayScene.Idle
                is OverlayScene.ContentPanelVisible -> OverlayScene.ContentPanelVisible
                else -> OverlayScene.Idle
            }
            OverlayCompositor.bringCompositorToFront()
            FloatBallOverlay.scheduleChromeAbovePanels()
        }
    }

    fun onContentPanelShown() {
        runOnMain {
            scene = OverlayScene.ContentPanelVisible
            OverlayCompositor.bringAboveContentPanels()
        }
    }

    fun onContentPanelHidden() {
        runOnMain {
            if (scene is OverlayScene.ContentPanelVisible) {
                scene = OverlayScene.Idle
            }
            OverlayCompositor.bringCompositorToFront()
            FloatBallOverlay.scheduleChromeAbovePanels()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
