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

    @Suppress("DEPRECATION")
    private val defaultSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

    fun fullScreenOverlay(
        context: Context,
        focusable: Boolean = false,
        touchable: Boolean = true,
        softInputMode: Int = defaultSoftInputMode,
        windowType: Int = OverlayWindowTypes.overlayWindowType(context),
    ): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
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

    /** 暂存/剪贴板侧栏：固定 [TYPE_APPLICATION_OVERLAY]，与悬浮球/触钮 z-order 一致。 */
    fun stashClipboardSidePanel(
        context: Context,
        focusable: Boolean = false,
        touchable: Boolean = true,
        softInputMode: Int = defaultSoftInputMode,
    ): WindowManager.LayoutParams = fullScreenOverlay(
        context = context,
        focusable = focusable,
        touchable = touchable,
        softInputMode = softInputMode,
        windowType = OverlayWindowTypes.contentPanelWindowType(context),
    ).apply {
        // 侧栏浏览时不抢焦点，尽量保持底层 App 输入法不收起（对齐 ClipShare 单窗 NOT_FOCUSABLE 策略）。
        flags = flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
    }

    fun pickResultPanel(context: Context): WindowManager.LayoutParams =
        fullScreenOverlay(
            context = context,
            focusable = false,
            touchable = false,
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            windowType = OverlayWindowTypes.contentPanelWindowType(context),
        )
}
