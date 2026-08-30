package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.slideindex.app.R
import com.slideindex.app.util.HapticHelper

/**
 * 伪息屏（SCREEN_OFF_KEEP_AWAKE）：屏幕熄灭且保持唤醒。
 * 全屏全黑悬浮窗（TYPE_APPLICATION_OVERLAY），设置最低亮度与 FLAG_KEEP_SCREEN_ON。
 * 拦截屏幕所有普通点击防误触，支持双击屏幕或按音量键解除黑屏。
 */
@SuppressLint("StaticFieldLeak")
object PseudoScreenOffOverlayWindow {
    private const val TAG = "PseudoScreenOffOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: PseudoScreenOffView? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun toggle(context: Context) {
        if (isShowing) {
            dismiss()
        } else {
            show(context)
        }
    }

    @Suppress("DEPRECATION")
    fun show(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context) }
            return true
        }

        if (isShowing) return true

        val applicationContext = context.applicationContext
        appContext = applicationContext
        val wm = applicationContext.getSystemService(WindowManager::class.java) ?: return false
        windowManager = wm

        val view = PseudoScreenOffView(applicationContext) {
            dismiss()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenBrightness = 0.0f
            buttonBrightness = 0.0f
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }

        return try {
            wm.addView(view, params)
            overlayView = view
            registerScreenOffReceiver(applicationContext)
            true
        } catch (e: Exception) {
            overlayView = null
            false
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }

        val view = overlayView ?: return
        val wm = windowManager
        overlayView = null

        unregisterScreenOffReceiver()

        try {
            wm?.removeViewImmediate(view)
        } catch (_: Exception) {
            try {
                wm?.removeView(view)
            } catch (_: Exception) {}
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    dismiss()
                }
            }
        }
        screenOffReceiver = receiver
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (_: Exception) {
            try {
                context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            } catch (_: Exception) {}
        }
    }

    private fun unregisterScreenOffReceiver() {
        val receiver = screenOffReceiver ?: return
        screenOffReceiver = null
        val ctx = appContext ?: return
        try {
            ctx.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}

@SuppressLint("ViewConstructor")
private class PseudoScreenOffView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val onExitRequest: () -> Unit,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val hintTextView: TextView
    private val gestureDetector: GestureDetector
    private val hideHintRunnable = Runnable {
        hintTextView.animate().alpha(0f).setDuration(400L).start()
    }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        hintTextView = TextView(context).apply {
            text = context.getString(R.string.pseudo_screen_off_hint)
            setTextColor(Color.argb(120, 255, 255, 255))
            textSize = 14f
            gravity = Gravity.CENTER
            alpha = 0f
            val pad = (16f * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        addView(hintTextView, lp)

        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    showWakeHint()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onExitRequest()
                    return true
                }
            },
        )
    }

    private fun showWakeHint() {
        removeCallbacks(hideHintRunnable)
        hintTextView.animate().cancel()
        hintTextView.alpha = 0.85f
        postDelayed(hideHintRunnable, 1500L)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP || event.action == KeyEvent.ACTION_DOWN) {
                    onExitRequest()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }
}
