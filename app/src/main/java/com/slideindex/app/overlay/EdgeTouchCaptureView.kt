package com.slideindex.app.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View

/**
 * Fixed-size edge strip that receives touch and forwards it to [EdgeGestureOverlayView].
 * Window geometry never changes during a gesture session, avoiding resize-driven ACTION_CANCEL.
 */
class EdgeTouchCaptureView(
    context: Context,
    private val touchHandler: (MotionEvent) -> Boolean,
) : View(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean = touchHandler(event)
}
