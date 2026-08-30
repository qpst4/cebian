package com.slideindex.app.overlay.carousel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.settings.AppSettings
import java.lang.ref.WeakReference

/**
 * 全新独立应用切换器悬浮窗控制器：
 * 管理 AppCarouselSwitcherView 悬浮窗添加、移除与手势生命周期。
 */
object AppCarouselSwitcherOverlay {
    private var activeViewRef: WeakReference<AppCarouselSwitcherView>? = null
    private var windowManager: WindowManager? = null

    val isShowing: Boolean
        get() = activeViewRef?.get() != null

    @SuppressLint("RtlHardcoded")
    fun show(context: Context, settings: AppSettings, anchorX: Float, anchorY: Float) {
        dismiss()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = AppCarouselSwitcherView(context) {
            dismiss()
        }
        view.configure(settings, anchorX, anchorY)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        runCatching {
            wm.addView(view, params)
            activeViewRef = WeakReference(view)
        }
    }

    fun onExternalMove(rawX: Float, rawY: Float) {
        activeViewRef?.get()?.onExternalMove(rawX, rawY)
    }

    fun onExternalUp(rawX: Float, rawY: Float, cancelled: Boolean) {
        activeViewRef?.get()?.onExternalUp(rawX, rawY, cancelled)
    }

    fun onExternalCancel() {
        activeViewRef?.get()?.onExternalCancel()
    }

    fun dismiss() {
        val view = activeViewRef?.get()
        if (view != null && windowManager != null) {
            runCatching {
                windowManager?.removeViewImmediate(view)
            }
        }
        activeViewRef = null
        windowManager = null
    }
}
