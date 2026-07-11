package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View

/**
 * Fixed-size edge strip that receives touch and forwards it to [EdgeGestureOverlayView].
 * Window geometry never changes during a gesture session, avoiding resize-driven ACTION_CANCEL.
 */
@SuppressLint("ViewConstructor") // Programmatically created overlay strip
class EdgeTouchCaptureView(
    context: Context,
    private val side: PanelSide,
    private val triggerIndex: Int,
    private val touchHandler: (MotionEvent) -> Boolean,
) : View(context) {
    init {
        OverlayTriggerAccessibility.applyTouchCapture(this, side, triggerIndex)
    }

    @SuppressLint("ClickableViewAccessibility") // Gesture capture strip; not a clickable control
    override fun onTouchEvent(event: MotionEvent): Boolean = touchHandler(event)
}
