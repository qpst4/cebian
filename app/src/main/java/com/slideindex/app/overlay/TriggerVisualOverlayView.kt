package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.slideindex.app.gesture.TriggerHandleDesign

/** Fixed edge strip that only renders the trigger handle chrome; touch is handled elsewhere. */
class TriggerVisualOverlayView(context: Context) : View(context) {
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
        if (width <= 0 || height <= 0) return
        TriggerHandleRenderer.draw(
            canvas = canvas,
            side = panelSide,
            design = handleDesign,
            density = resources.displayMetrics.density,
            widthPx = width,
            heightPx = height,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
