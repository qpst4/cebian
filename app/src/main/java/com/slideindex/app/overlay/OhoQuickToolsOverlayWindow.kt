package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SystemGestureActions

/**
 * Hosts [OhoQuickToolsPanel] as a standalone, top-level [WindowManager] popup — independent from
 * the edge-gesture overlay session. Managed as a singleton so a repeated trigger (e.g. tapping a
 * bound gesture twice) never stacks duplicate windows.
 *
 * Architecture (see class-level requirements this satisfies):
 *  - Window type: [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] on API 26+, falling back
 *    to the legacy `TYPE_SYSTEM_ALERT` below that.
 *  - Permission: no-ops (never crashes) when `SYSTEM_ALERT_WINDOW` hasn't been granted; callers
 *    that want to *guide* the user to the settings screen should use [PermissionHelper] directly
 *    (already wired into the gesture-action picker UI).
 *  - Dismissal: outside taps ([MotionEvent.ACTION_OUTSIDE], enabled by combining
 *    [WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL] with
 *    [WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH]), the in-panel close tile, the system
 *    back key, and the screen turning off (proxy for the power key, which apps cannot intercept
 *    directly) all animate out before the window is removed.
 */
object OhoQuickToolsOverlayWindow {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var panelState: OhoQuickToolsPanelState? = null
    private var visibleState: MutableState<Boolean>? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    val isShowing: Boolean get() = composeView != null

    fun show(
        context: Context,
        settings: AppSettings,
        side: PanelSide? = null,
        anchorRawY: Float? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, settings, side, anchorRawY) }
            return
        }
        if (isShowing) return
        if (!PermissionHelper.canDrawOverlays(context)) return

        val app = context.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val state = OhoQuickToolsPanelState(app).also { it.refresh() }
        val dialogOwner = OverlayComposeOwner()
        val visible = mutableStateOf(false)
        val view = OverlayCompose.createComposeView(app, dialogOwner).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
            setContent {
                SlideIndexTheme(seedColor = Color(settings.themeColorArgb)) {
                    OhoQuickToolsPanel(
                        state = state,
                        visible = visible.value,
                        onEvent = { event -> handleEvent(settings, event) },
                    )
                }
            }
        }

        val params = buildLayoutParams(app, side, anchorRawY)
        val added = runCatching { wm.addView(view, params) }.isSuccess
        if (!added) {
            dialogOwner.destroy()
            return
        }

        windowManager = wm
        composeView = view
        owner = dialogOwner
        panelState = state
        visibleState = visible
        appContext = app
        state.startLiveSync()
        registerScreenOffReceiver(app)

        view.requestFocus()
        view.post {
            applyFingerAnchor(app, wm, view, params, side, anchorRawY)
            visible.value = true
            view.post { applyFingerAnchor(app, wm, view, params, side, anchorRawY) }
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) {
            cleanup()
            return
        }
        visible.value = false
        panelState?.commitBrightness()
        panelState?.stopLiveSync()
        mainHandler.postDelayed({ cleanup() }, EXIT_ANIM_MS)
    }

    private fun handleEvent(settings: AppSettings, event: OhoPanelEvent) {
        composeView?.let { HapticHelper.appTick(it, settings) }
        val context = appContext ?: return
        when (event) {
            is OhoPanelEvent.Tile -> {
                if (panelState?.onTileTap(event.tile) == true) dismiss()
            }
            OhoPanelEvent.ToggleAutoBrightness -> panelState?.toggleAutoBrightness()
            OhoPanelEvent.OpenMediaApp -> {
                if (panelState?.openMediaTarget() == true) dismiss()
            }
            OhoPanelEvent.MediaPrevious -> {
                SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                panelState?.refreshMediaFromSystem()
            }
            OhoPanelEvent.MediaPlayPause -> {
                SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                panelState?.refreshMediaFromSystem()
                mainHandler.postDelayed({ panelState?.refreshMediaFromSystem() }, 400L)
            }
            OhoPanelEvent.MediaNext -> {
                SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                panelState?.refreshMediaFromSystem()
            }
            OhoPanelEvent.ChevronUp -> panelState?.let { it.updateVolume(it.volumeFraction + 0.08f) }
            OhoPanelEvent.ChevronDown -> panelState?.let { it.updateVolume(it.volumeFraction - 0.08f) }
        }
    }

    private fun buildLayoutParams(
        context: Context,
        side: PanelSide?,
        anchorRawY: Float?,
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val edgeMarginPx = (EDGE_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            when (side) {
                PanelSide.LEFT -> {
                    gravity = Gravity.START or Gravity.TOP
                    x = edgeMarginPx
                    y = initialAnchorY(context, anchorRawY, 0)
                }
                PanelSide.RIGHT -> {
                    gravity = Gravity.END or Gravity.TOP
                    x = edgeMarginPx
                    y = initialAnchorY(context, anchorRawY, 0)
                }
                null -> gravity = Gravity.CENTER
            }
        }
    }

    private fun applyFingerAnchor(
        context: Context,
        wm: WindowManager,
        view: android.view.View,
        params: WindowManager.LayoutParams,
        side: PanelSide?,
        anchorRawY: Float?,
    ) {
        if (side == null || anchorRawY == null) return
        val panelHeight = view.height.takeIf { it > 0 } ?: view.measuredHeight
        if (panelHeight <= 0) return
        val nextY = initialAnchorY(context, anchorRawY, panelHeight)
        if (params.y == nextY) return
        params.y = nextY
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun initialAnchorY(context: Context, anchorRawY: Float?, panelHeight: Int): Int {
        val dm = context.resources.displayMetrics
        val margin = (EDGE_MARGIN_DP * dm.density).toInt()
        val screenH = dm.heightPixels
        val anchor = anchorRawY ?: (screenH / 2f)
        val centered = (anchor - panelHeight / 2f).toInt()
        val maxY = (screenH - panelHeight - margin).coerceAtLeast(margin)
        return centered.coerceIn(margin, maxY)
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun cleanup() {
        val view = composeView
        val wm = windowManager
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        panelState?.stopLiveSync()
        owner?.destroy()
        owner = null
        composeView = null
        windowManager = null
        panelState = null
        visibleState = null
        screenOffReceiver = null
        appContext = null
    }

    private const val EXIT_ANIM_MS = 220L
    private const val EDGE_MARGIN_DP = 30f
}
