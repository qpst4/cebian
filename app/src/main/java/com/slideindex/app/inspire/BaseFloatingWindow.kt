package com.slideindex.app.inspire

import android.content.Context
import android.util.Log
import android.view.View
import android.view.WindowManager
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * GestureEVO BaseFloatingWindow — safe WindowManager attach/detach helpers.
 */
abstract class BaseFloatingWindow(
    private val context: Context,
    protected val coroutineScope: CoroutineScope,
    private val logTag: String,
) {
    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private val attachRequests = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
    private val attachRetryJobs = IdentityHashMap<View, Job>()

    protected fun configureAccessibilityOverlay(
        params: WindowManager.LayoutParams,
        flags: Int = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        width: Int = WindowManager.LayoutParams.MATCH_PARENT,
        height: Int = WindowManager.LayoutParams.MATCH_PARENT,
        gravity: Int = android.view.Gravity.START or android.view.Gravity.TOP,
        format: Int = android.graphics.PixelFormat.TRANSLUCENT,
        x: Int = 0,
        y: Int = 0,
        allowDisplayCutout: Boolean = true,
        title: String? = null,
    ) {
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        params.format = format
        params.flags = flags
        params.width = width
        params.height = height
        params.gravity = gravity
        params.x = x
        params.y = y
        title?.let(params::setTitle)
        if (allowDisplayCutout) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    protected fun addViewSafely(
        view: View,
        layoutParams: WindowManager.LayoutParams,
        onAttached: () -> Unit = {},
    ): Boolean {
        attachRequests.add(view)
        attachRetryJobs.remove(view)?.cancel(CancellationException("replaced"))
        if (view.isAttachedToWindow) {
            onAttached()
            return true
        }
        return try {
            windowManager.addView(view, layoutParams)
            onAttached()
            true
        } catch (error: Throwable) {
            Log.d(logTag, "addView failed: $error")
            false
        }
    }

    protected fun detachViewSafely(
        view: View,
        onDetached: () -> Unit = {},
    ): Boolean {
        attachRequests.remove(view)
        attachRetryJobs.remove(view)?.cancel(CancellationException("detached"))
        if (!view.isAttachedToWindow) {
            onDetached()
            return true
        }
        return try {
            windowManager.removeView(view)
            onDetached()
            true
        } catch (error: Throwable) {
            Log.d(logTag, "removeView failed: $error")
            false
        }
    }
}
