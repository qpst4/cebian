package com.slideindex.app.monitoring

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.slideindex.app.BuildConfig

/** Debug-only floating panel that mirrors [PerformanceMonitor] FPS / jank stats. */
@SuppressLint("StaticFieldLeak") // Debug overlay; label cleared in hide()
object DebugPerformanceOverlay {
    private const val REFRESH_MS = 500L

    private var windowManager: WindowManager? = null
    private var label: TextView? = null
    private var visible = false
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!visible) return
            val stats = PerformanceMonitor.instance.latestStats
            label?.text = if (stats != null) {
                "FPS ${stats.fps}  jank ${stats.jankFrames}  (${stats.windowMs}ms)"
            } else {
                "FPS --  jank --"
            }
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    fun sync(context: Context, userPreferenceEnabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        val shouldShow = userPreferenceEnabled && PerformanceMonitor.instance.enabled
        if (shouldShow) {
            show(context.applicationContext)
        } else {
            hide()
        }
    }

    private fun show(context: Context) {
        if (visible) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val tv = TextView(context).apply {
            textSize = 12f
            setPadding(16, 10, 16, 10)
            setBackgroundColor(0xCC000000.toInt())
            setTextColor(0xFFE0E0E0.toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }
        wm.addView(tv, params)
        windowManager = wm
        label = tv
        visible = true
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    private fun hide() {
        if (!visible) return
        handler.removeCallbacks(refreshRunnable)
        val wm = windowManager
        val tv = label
        if (wm != null && tv != null) {
            runCatching { wm.removeView(tv) }
        }
        windowManager = null
        label = null
        visible = false
    }
}
