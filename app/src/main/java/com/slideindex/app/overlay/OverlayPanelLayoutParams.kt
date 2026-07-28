package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * Shared [WindowManager.LayoutParams] builders for full-screen overlay panels
 * (stash/clipboard side panel, translate, pick-result).
 */
object OverlayPanelLayoutParams {

    fun fullScreenOverlay(
        context: Context,
        focusable: Boolean = false,
        touchable: Boolean = true,
        softInputMode: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
    ): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            flags or if (focusable) {
                0
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } or if (touchable) {
                0
            } else {
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            },
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            this.softInputMode = softInputMode
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun pickResultPanel(context: Context): WindowManager.LayoutParams =
        fullScreenOverlay(
            context = context,
            focusable = false,
            touchable = false,
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
        )
}
