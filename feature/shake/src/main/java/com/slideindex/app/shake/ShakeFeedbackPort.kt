package com.slideindex.app.shake

import android.content.Context
import com.slideindex.app.gesture.GestureAction

/** Haptic and visual feedback after a shake gesture is recognized. */
interface ShakeFeedbackPort {
    fun vibrate(context: Context)
    fun showGestureFeedback(
        context: Context,
        gestureType: ShakeGestureType,
        action: GestureAction,
        colorArgb: Int,
    )
    fun detachFeedbackOverlay()
}
