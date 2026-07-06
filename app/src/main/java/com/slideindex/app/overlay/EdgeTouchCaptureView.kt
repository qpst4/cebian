package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.slideindex.app.gesture.TriggerHandleDesign

/**
 * Fixed-size edge strip that receives touch and forwards it to [EdgeGestureOverlayView].
 * Window geometry never changes during a gesture session, avoiding resize-driven ACTION_CANCEL.
 */
class EdgeTouchCaptureView(
    context: Context,
    private val touchHandler: (MotionEvent) -> Boolean,
) : View(context) {
    private var panelSide: PanelSide = PanelSide.LEFT
    private var handleDesign: TriggerHandleDesign = TriggerHandleDesign()

    fun applyVisual(side: PanelSide, design: TriggerHandleDesign) {
        val changed = panelSide != side || handleDesign != design
        panelSide = side
        handleDesign = design
        if (changed) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        TriggerHandleRenderer.draw(
            canvas = canvas,
            side = panelSide,
            design = handleDesign,
            density = resources.displayMetrics.density,
            widthPx = width,
            heightPx = height,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = touchHandler(event)
}
