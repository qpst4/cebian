package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import com.slideindex.app.gesture.TriggerHandleDesign

/**
 * SideGesture-style edge strip: one WindowManager view for touch + chrome.
 * System back-gesture opt-out uses [ViewCompat.setSystemGestureExclusionRects] on this view
 * (no separate exclusion overlay window).
 */
@SuppressLint("ViewConstructor") // Programmatically created overlay strip
class EdgeTouchCaptureView(
    context: Context,
    private val side: PanelSide,
    private val triggerIndex: Int,
    private val touchHandler: (MotionEvent) -> Boolean,
) : View(context) {
    private var handleDesign: TriggerHandleDesign = TriggerHandleDesign()
    private var showVisual: Boolean = true
    private var excludeSystemGestures: Boolean = false
    private val exclusionRects: MutableList<Rect> = mutableListOf()

    init {
        OverlayTriggerAccessibility.applyTouchCapture(this, side, triggerIndex)
        setWillNotDraw(false)
    }

    fun applyVisual(design: TriggerHandleDesign, visible: Boolean) {
        val changed = handleDesign != design || showVisual != visible
        handleDesign = design
        showVisual = visible
        if (changed) invalidate()
    }

    fun setExcludeSystemGestures(enabled: Boolean) {
        if (excludeSystemGestures == enabled) {
            if (enabled) updateSystemGestureExclusion()
            return
        }
        excludeSystemGestures = enabled
        updateSystemGestureExclusion()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) updateSystemGestureExclusion()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showVisual && width > 0 && height > 0) {
            TriggerHandleRenderer.draw(
                canvas = canvas,
                side = side,
                design = handleDesign,
                density = resources.displayMetrics.density,
                widthPx = width,
                heightPx = height,
            )
        }
        updateSystemGestureExclusion()
    }

    private fun updateSystemGestureExclusion() {
        if (!excludeSystemGestures || width <= 0 || height <= 0) {
            exclusionRects.clear()
            ViewCompat.setSystemGestureExclusionRects(this, emptyList())
            return
        }
        if (exclusionRects.isEmpty()) {
            exclusionRects += Rect(0, 0, width, height)
        } else {
            exclusionRects[0].set(0, 0, width, height)
        }
        ViewCompat.setSystemGestureExclusionRects(this, exclusionRects)
    }

    @SuppressLint("ClickableViewAccessibility") // Gesture capture strip; not a clickable control
    override fun onTouchEvent(event: MotionEvent): Boolean = touchHandler(event)
}
