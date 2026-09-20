package com.slideindex.app.clipboardoverlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.LockScreenState
import com.slideindex.app.util.PermissionHelper

/**
 * Third-party host for AOSP Clipboard Overlay: WindowManager + public overlay types.
 */
@SuppressLint("StaticFieldLeak")
object ClipboardOverlayWindow {
    private const val TAG = "ClipboardOverlayWindow"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: ClipboardOverlayView? = null
    private var controller: ClipboardOverlayController? = null
    private var hostContext: Context? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var componentCallbacks: ComponentCallbacks? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    val isShowing: Boolean
        get() = overlayView?.isAttachedToWindow == true

    fun show(context: Context, payload: ClipboardPayload) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, payload) }
            return
        }
        if (LockScreenState.isActive(context)) return
        val host = MessageOverlayHost.resolveHostContext(context) ?: return
        if (!PermissionHelper.canDrawOverlays(host) &&
            host !is android.accessibilityservice.AccessibilityService
        ) {
            Log.w(TAG, "overlay permission missing")
            return
        }
        val existing = controller
        if (existing != null && overlayView?.isAttachedToWindow == true) {
            existing.setClipData(payload)
            return
        }
        attach(host, payload)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        controller?.animateOut() ?: removeImmediate()
    }

    private fun attach(host: Context, payload: ClipboardPayload) {
        val existing = controller
        if (existing != null) {
            existing.hideImmediate()
        } else {
            removeImmediate()
        }
        runCatching {
            attachUnchecked(host, payload)
        }.onFailure {
            Log.e(TAG, "attach failed", it)
            removeImmediate()
        }
    }

    private fun attachUnchecked(host: Context, payload: ClipboardPayload) {
        val settings = OverlayDependencyAccess.overlayDependencies(host)
            ?.settingsRepository?.readSnapshot()
            ?: AppSettings()
        val (themed, scheme) = host.clipboardOverlayThemedContext(settings)
        val view = LayoutInflater.from(themed)
            .inflate(R.layout.clipboard_overlay, null, false) as ClipboardOverlayView
        view.applyAppColorScheme(scheme)
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(host),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            title = "ClipboardOverlay"
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        val ctrl = ClipboardOverlayController(themed, view) { removeImmediate() }
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) return
        windowManager = wm
        overlayView = view
        controller = ctrl
        hostContext = host
        layoutParams = params
        view.post { ctrl.setClipData(payload) }
        registerConfigCallbacks(host, view, ctrl)
        registerScreenOff(host)
    }

    private fun registerConfigCallbacks(
        host: Context,
        view: ClipboardOverlayView,
        ctrl: ClipboardOverlayController,
    ) {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val insets = host.getSystemService(WindowManager::class.java)
                    .currentWindowMetrics.windowInsets
                ctrl.onWindowInsetsChanged(insets)
            }
            override fun onLowMemory() {}
        }
        host.registerComponentCallbacks(callbacks)
        componentCallbacks = callbacks
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insetsCompat ->
            val platform = insetsCompat.toWindowInsets()
            if (platform != null) ctrl.onWindowInsetsChanged(platform)
            insetsCompat
        }
    }

    private fun registerScreenOff(host: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Intent.ACTION_SCREEN_OFF == intent.action) {
                    dismiss()
                }
            }
        }
        ContextCompat.registerReceiver(
            host,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiver = receiver
    }

    private fun removeImmediate() {
        val view = overlayView
        val wm = windowManager
        if (view != null && wm != null && view.isAttachedToWindow) {
            runCatching { wm.removeViewImmediate(view) }
        }
        hostContext?.let { host ->
            componentCallbacks?.let { runCatching { host.unregisterComponentCallbacks(it) } }
            screenOffReceiver?.let { runCatching { host.unregisterReceiver(it) } }
        }
        overlayView = null
        controller = null
        windowManager = null
        hostContext = null
        layoutParams = null
        componentCallbacks = null
        screenOffReceiver = null
    }
}
