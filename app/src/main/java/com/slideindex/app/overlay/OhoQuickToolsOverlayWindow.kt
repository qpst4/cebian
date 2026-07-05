package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SystemGestureActions

/**
 * Hosts [OhoQuickToolsPanel] as a standalone, top-level [WindowManager] popup — independent from
 * the edge-gesture overlay session. Managed as a singleton so a repeated trigger (e.g. tapping a
 * bound gesture twice) never stacks duplicate windows.
 *
 * The window is full-screen so outside taps are consumed here (panel dismisses first) instead of
 * leaking through to the edge-gesture overlay below.
 */
object OhoQuickToolsOverlayWindow {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var panelState: OhoQuickToolsPanelState? = null
    private var visibleState: MutableState<Boolean>? = null
    private var blockingTouchesState: MutableState<Boolean>? = null
    private var panelSideState: MutableState<PanelSide?>? = null
    private var anchorRawYState: MutableState<Float?>? = null
    private var settingsState: MutableState<AppSettings>? = null
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
        if (isShowing) {
            if (visibleState?.value == true) return
            cleanup()
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return
        }

        val hostContext = SlideIndexAccessibilityService.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return
            }
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val app = hostContext.applicationContext
        val state = OhoQuickToolsPanelState(app)
        val dialogOwner = OverlayComposeOwner()
        val visible = mutableStateOf(false)
        val blockingTouches = mutableStateOf(true)
        val panelSide = mutableStateOf(side)
        val anchorY = mutableStateOf(anchorRawY)
        val settingsHolder = mutableStateOf(settings)
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                QuickToolsOverlayRoot(
                    settings = settingsHolder.value,
                    state = state,
                    visible = visible.value,
                    blockingTouches = blockingTouches.value,
                    side = panelSide.value,
                    anchorRawY = anchorY.value,
                    onDismissOutside = { dismiss() },
                    onEvent = { event -> handleEvent(settingsHolder.value, event) },
                )
            }
        }

        val params = buildLayoutParams(hostContext)
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            return
        }

        windowManager = wm
        composeView = view
        owner = dialogOwner
        panelState = state
        visibleState = visible
        blockingTouchesState = blockingTouches
        panelSideState = panelSide
        anchorRawYState = anchorY
        settingsState = settingsHolder
        appContext = hostContext
        registerScreenOffReceiver(hostContext)

        // Attach the window first, then refresh state and animate in so the trigger gesture
        // doesn't block on I/O or first Compose composition.
        view.post {
            state.startLiveSync()
            state.refresh()
            visible.value = true
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
        blockingTouchesState?.value = false
        panelState?.commitBrightness()
        panelState?.stopLiveSync()
        mainHandler.postDelayed({ cleanup() }, OverlayPanelEnterAnimation.DURATION_MS.toLong())
    }

    private fun handleEvent(settings: AppSettings, event: OhoPanelEvent) {
        composeView?.let { HapticHelper.appTick(it, settings) }
        val context = appContext ?: return
        when (event) {
            is OhoPanelEvent.Tile -> {
                if (panelState?.onTileTap(event.tile) == true) dismiss()
            }
            is OhoPanelEvent.TileLongPress -> {
                if (panelState?.onTileLongPress(event.tile) == true) dismiss()
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

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
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
        blockingTouchesState = null
        panelSideState = null
        anchorRawYState = null
        settingsState = null
        screenOffReceiver = null
        appContext = null
    }

    private fun initialAnchorY(dm: DisplayMetrics, anchorRawY: Float?, panelHeight: Int): Int {
        val margin = (EDGE_MARGIN_DP * dm.density).toInt()
        val screenH = dm.heightPixels
        val anchor = anchorRawY ?: (screenH / 2f)
        val centered = (anchor - panelHeight / 2f).toInt()
        val maxY = (screenH - panelHeight - margin).coerceAtLeast(margin)
        return centered.coerceIn(margin, maxY)
    }

    private const val EDGE_MARGIN_DP = 30f
    private const val ESTIMATED_PANEL_HEIGHT_DP = 420f
    private const val TAG = "OhoQuickToolsOverlay"

    @Composable
    private fun QuickToolsOverlayRoot(
        settings: AppSettings,
        state: OhoQuickToolsPanelState,
        visible: Boolean,
        blockingTouches: Boolean,
        side: PanelSide?,
        anchorRawY: Float?,
        onDismissOutside: () -> Unit,
        onEvent: (OhoPanelEvent) -> Unit,
    ) {
        SlideIndexTheme(
            seedColor = Color(settings.themeColorArgb),
            dynamicColor = settings.dynamicColorEnabled,
        ) {
            val context = LocalContext.current
            val dm = context.resources.displayMetrics
            var panelHeightPx by remember { mutableIntStateOf(0) }
            val estimatedPanelHeightPx = remember(dm) {
                (ESTIMATED_PANEL_HEIGHT_DP * dm.density).toInt()
            }
            val topOffsetPx = remember(side, anchorRawY, panelHeightPx, dm.heightPixels) {
                if (side == null || anchorRawY == null) {
                    0
                } else {
                    val height = panelHeightPx.takeIf { it > 0 } ?: estimatedPanelHeightPx
                    initialAnchorY(dm, anchorRawY, height)
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (blockingTouches) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(blockingTouches) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .then(
                                if (visible) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onDismissOutside,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }

                val horizontalPadding = EDGE_MARGIN_DP.dp
                val align = when (side) {
                    PanelSide.LEFT -> Alignment.TopStart
                    PanelSide.RIGHT -> Alignment.TopEnd
                    null -> Alignment.Center
                }
                Box(
                    Modifier
                        .align(align)
                        .then(
                            when (side) {
                                PanelSide.LEFT -> Modifier.padding(start = horizontalPadding)
                                PanelSide.RIGHT -> Modifier.padding(end = horizontalPadding)
                                null -> Modifier
                            },
                        )
                        .then(
                            if (side != null && anchorRawY != null) {
                                Modifier.offset { IntOffset(0, topOffsetPx) }
                            } else {
                                Modifier
                            },
                        )
                        .onSizeChanged { panelHeightPx = it.height },
                ) {
                    OhoQuickToolsPanel(
                        state = state,
                        visible = visible,
                        side = side,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}
