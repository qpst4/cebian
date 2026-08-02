package com.slideindex.app.overlay

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.util.PermissionHelper

/**
 * WindowManager overlay types and flags aligned with SideGesture (gulugulu):
 * prefer [TYPE_ACCESSIBILITY_OVERLAY] when the accessibility service is active.
 */
object OverlayWindowTypes {

    /** Prevent overlay windows from overriding system brightness (OEM bugs if unset or re-added). */
    fun ensureNoBrightnessOverride(params: WindowManager.LayoutParams) {
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }

    fun overlayWindowType(context: Context): Int =
        if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

    /**
     * 边缘捕获/触钮视觉等小块 overlay：统一使用 [overlayWindowType]，
     * 避免因 Window Type 差异导致 TYPE_ACCESSIBILITY_OVERLAY 面板强行压在 TYPE_APPLICATION_OVERLAY 悬浮球上方。
     */
    fun captureOverlayWindowType(context: Context): Int = overlayWindowType(context)

    fun createCaptureParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            captureOverlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).also {
            ensureNoBrightnessOverride(it)
            applyCaptureTouchFlags(it)
        }
    }

    fun createPresentationParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).also {
            ensureNoBrightnessOverride(it)
            applyPresentationPassthroughFlags(it)
        }
    }

    fun applyFullScreen(params: WindowManager.LayoutParams) {
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    fun applyCaptureTouchFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyPresentationPassthroughFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyPresentationInteractiveFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyPreviewPresentationFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyExclusionPassthroughFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }
}
