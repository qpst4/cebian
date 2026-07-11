package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.slideindex.app.gesture.TriggerHandleDesign

/** Fixed edge strip that only renders the trigger handle chrome; touch is handled elsewhere. */
class TriggerVisualOverlayView(context: Context) : View(context) {
    private var panelSide: PanelSide = PanelSide.LEFT
    private var triggerIndex: Int = 0
    private var handleDesign: TriggerHandleDesign = TriggerHandleDesign()

    fun applyVisual(side: PanelSide, design: TriggerHandleDesign, triggerIndex: Int = 0) {
        val changed = panelSide != side || handleDesign != design || this.triggerIndex != triggerIndex
        panelSide = side
        this.triggerIndex = triggerIndex
        handleDesign = design
        OverlayTriggerAccessibility.applyTriggerVisual(this, side, triggerIndex)
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

    @SuppressLint("ClickableViewAccessibility") // Visual-only strip; touches handled by capture view
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
