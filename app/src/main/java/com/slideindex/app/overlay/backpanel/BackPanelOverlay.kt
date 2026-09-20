package com.slideindex.app.overlay.backpanel

import android.content.Context
import android.view.ViewGroup
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.gestureHintStyle
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerHandle

class BackPanelOverlayController(
    val side: PanelSide,
) {
    private var panel: BackPanel? = null
    private var controller: BackPanelController? = null
    private var parent: ViewGroup? = null
    private var appContext: Context? = null
    private var enabled = false
    var onSettled: (() -> Unit)? = null
        set(value) {
            field = value
            controller?.onSettled = value
        }

    val isShowing: Boolean
        get() = controller?.isShowing == true

    fun attach(parent: ViewGroup, context: Context) {
        appContext = context.applicationContext
        this.parent = parent
        val host = panel ?: BackPanel(context).also { panel = it }
        val ctrl = controller ?: BackPanelController(host).also { created ->
            created.onSettled = onSettled
            controller = created
        }
        ctrl.attach(parent)
        host.z = GESTURE_ANIMATION_Z_INDEX
    }

    fun applySettings(settings: AppSettings, handleId: String? = null) {
        enabled = settings.gestureHintEnabled &&
            settings.gestureHintStyle() == GestureHintStyle.ANDROID &&
            side.isHorizontalEdge
        val density = appContext?.resources?.displayMetrics?.density ?: 3f
        val handle = if (handleId != null) {
            settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
        } else {
            settings.primaryTriggerHandle(side)
        }
        controller?.extraFingerOffsetPx = settings.gestureHintFingerOffsetDp * density
        controller?.setActivationThresholdPx(handle.shortSwipeDistanceDp * density)
        if (!enabled) {
            hide()
        }
    }

    fun onDown(
        rawX: Float,
        rawY: Float,
        translationOriginX: Float = rawX,
        activationThresholdPx: Float? = null,
    ) {
        if (!enabled) return
        val ctrl = controller ?: return
        val ctx = appContext ?: return
        val metrics = OverlayScreenMetrics.snapshot(ctx)
        ctrl.setDisplaySize(metrics.widthPx, metrics.heightPx)
        ctrl.setIsLeftPanel(side == PanelSide.LEFT)
        activationThresholdPx?.let { ctrl.setActivationThresholdPx(it) }
        ctrl.onDown(rawX, rawY, translationOriginX)
    }

    fun onMove(rawX: Float, rawY: Float) {
        if (!enabled) return
        controller?.onMove(rawX, rawY)
    }

    fun onUp(rawX: Float, rawY: Float) {
        if (!enabled) {
            hide()
            return
        }
        controller?.onUp(rawX, rawY)
    }

    fun onCancel() {
        controller?.onCancel()
    }

    fun hide() {
        controller?.hideImmediately()
    }

    fun detach() {
        hide()
        controller?.detach()
        panel = null
        controller = null
        parent = null
        appContext = null
    }

    private companion object {
        const val GESTURE_ANIMATION_Z_INDEX = 40f
    }
}

object BackPanelOverlayRegistry {
    private val controllers = mutableMapOf<PanelSide, BackPanelOverlayController>()

    fun controller(side: PanelSide): BackPanelOverlayController =
        controllers.getOrPut(side) { BackPanelOverlayController(side) }

    fun isAnyShowing(): Boolean = controllers.values.any { it.isShowing }

    fun detachAll() {
        controllers.values.forEach { it.detach() }
        controllers.clear()
    }
}
