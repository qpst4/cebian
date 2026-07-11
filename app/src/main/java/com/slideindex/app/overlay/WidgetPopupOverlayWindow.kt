package com.slideindex.app.overlay

import com.slideindex.app.di.OverlayDependencies
import com.slideindex.app.di.OverlayDependencyAccess
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
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.widget.WidgetPopupHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Samsung OHO+ / Floatwidget style widget popup with edit mode, resize handles, and in-panel add.
 */
object WidgetPopupOverlayWindow {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var windowManager: WindowManager? = null
  private var composeView: ComposeView? = null
  private var owner: OverlayComposeOwner? = null
  private var visibleState: MutableState<Boolean>? = null
  private var blockingTouchesState: MutableState<Boolean>? = null
  private var settingsState: MutableState<AppSettings>? = null
  private var panelSideState: MutableState<PanelSide?>? = null
  private var anchorRawYState: MutableState<Float?>? = null
  private var widgetAddFlowActiveState: MutableState<Boolean>? = null
  @Volatile
  private var pendingPagesToSave: List<WidgetPanelPage>? = null
  private var screenOffReceiver: BroadcastReceiver? = null
  private var appContext: Context? = null
  private var overlayDeps: OverlayDependencies? = null
  private var settingsCollectJob: Job? = null

  val isShowing: Boolean get() = composeView != null

  fun show(
    context: Context,
    settings: AppSettings,
    side: PanelSide? = null,
    anchorRawY: Float? = null,
  ): Boolean {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      var result = false
      val latch = java.util.concurrent.CountDownLatch(1)
      mainHandler.post {
        result = show(context, settings, side, anchorRawY)
        latch.countDown()
      }
      runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
      return result
    }
    if (isShowing) {
      if (visibleState?.value == true) return true
      cleanup()
    }
    if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
      Log.w(TAG, "show: accessibility service not enabled")
      return false
    }

    val hostContext = OverlayDependencyAccess.overlayHostContext()
      ?: run {
        Log.w(TAG, "show: accessibility service not connected")
        return false
      }
    val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
      ?: run {
        Log.w(TAG, "show: accessibility service deps unavailable")
        return false
      }

    val overlayContext = OverlayCompose.themedContext(hostContext)
    val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
      ?: return false
    val visible = mutableStateOf(false)
    val blockingTouches = mutableStateOf(true)
    val panelSide = mutableStateOf(side)
    val anchorY = mutableStateOf(anchorRawY)
    val settingsHolder = mutableStateOf(settings)
    val widgetAddFlowActive = mutableStateOf(false)
    val dialogOwner = OverlayComposeOwner()
    val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
      setContent {
        WidgetPopupContentRenderer(
          settings = settingsHolder.value,
          visible = visible.value,
          blockingTouches = blockingTouches.value,
          widgetAddFlowActive = widgetAddFlowActive.value,
          side = panelSide.value,
          anchorRawY = anchorY.value,
          hostContext = hostContext,
          deps = deps,
          onDismissOutside = { dismiss() },
          onDismiss = { dismiss() },
          onSavePages = { pages -> savePages(pages) },
        )
      }
    }

    val params = buildLayoutParams(hostContext, blurEnabled = settings.widgetPanelBlurEnabled)
    val added = runCatching { wm.addView(view, params) }
      .onFailure { Log.e(TAG, "addView failed", it) }
      .isSuccess
    if (!added) {
      dialogOwner.destroy()
      return false
    }

    windowManager = wm
    composeView = view
    owner = dialogOwner
    visibleState = visible
    blockingTouchesState = blockingTouches
    settingsState = settingsHolder
    panelSideState = panelSide
    anchorRawYState = anchorY
    widgetAddFlowActiveState = widgetAddFlowActive
    appContext = hostContext
    overlayDeps = deps
    OverlayPerformanceMonitorBinding.onOverlayShown(settings, hostContext)
    startSettingsSync(deps, settingsHolder)
    registerScreenOffReceiver(hostContext)

    WidgetPopupHost.startListening(hostContext)
    SlideIndexAccessibilityService.refreshTriggerVisuals()
    view.post { visible.value = true }
    return true
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
    mainHandler.postDelayed({ cleanup() }, OverlayPanelEnterAnimation.DURATION_MS.toLong())
  }

  fun setWidgetAddFlowActive(active: Boolean) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { setWidgetAddFlowActive(active) }
      return
    }
    widgetAddFlowActiveState?.value = active
    if (active) {
      blockingTouchesState?.value = false
      updateOverlayTouchable(false)
    } else if (isShowing && visibleState?.value == true) {
      blockingTouchesState?.value = true
      updateOverlayTouchable(true)
    }
  }

  private fun updateOverlayTouchable(touchable: Boolean) {
    val view = composeView ?: return
    val wm = windowManager ?: return
    val params = view.layoutParams as? WindowManager.LayoutParams ?: return
    if (touchable) {
      params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    } else {
      params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
    runCatching { wm.updateViewLayout(view, params) }
      .onFailure { Log.w(TAG, "updateOverlayTouchable failed", it) }
  }

  private fun buildLayoutParams(context: Context, blurEnabled: Boolean): WindowManager.LayoutParams {
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
      layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setBlurBehindRadius(BLUR_RADIUS_PX)
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

  private fun flushPendingPages() {
    val pending = pendingPagesToSave ?: return
    val deps = overlayDeps ?: return
    runCatching {
      runBlocking {
        deps.widgetPanelPersistence.persistNow(pending)
      }
    }.onFailure { Log.e(TAG, "flushPendingPages failed", it) }
    pendingPagesToSave = null
  }

  private fun savePages(pages: List<WidgetPanelPage>) {
    val deps = overlayDeps ?: return
    pendingPagesToSave = pages
    deps.widgetPanelPersistence.schedulePersist(pages)
  }

  private fun cleanup() {
    OverlayPerformanceMonitorBinding.onOverlayHidden(appContext)
    settingsCollectJob?.cancel()
    settingsCollectJob = null
    flushPendingPages()
    val view = composeView
    val wm = windowManager
    if (view != null && wm != null) {
      runCatching { wm.removeView(view) }
    }
    appContext?.let { ctx ->
      if (!WidgetPickerOverlayWindow.isShowing &&
        !WidgetBindTrampolineActivity.isActive()
      ) {
        WidgetPopupHost.stopListening(ctx)
      }
    }
    screenOffReceiver?.let { receiver ->
      appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
    }
    owner?.destroy()
    owner = null
    composeView = null
    windowManager = null
    SlideIndexAccessibilityService.refreshTriggerVisuals()
    visibleState = null
    blockingTouchesState = null
    settingsState = null
    panelSideState = null
    anchorRawYState = null
    widgetAddFlowActiveState = null
    pendingPagesToSave = null
    screenOffReceiver = null
    appContext = null
    overlayDeps = null
  }

  private fun initialAnchorY(dm: DisplayMetrics, anchorRawY: Float?, panelHeight: Int): Int {
    val margin = (EDGE_MARGIN_DP * dm.density).toInt()
    val screenH = dm.heightPixels
    val anchor = anchorRawY ?: (screenH / 2f)
    val centered = (anchor - panelHeight / 2f).toInt()
    val maxY = (screenH - panelHeight - margin).coerceAtLeast(margin)
    return centered.coerceIn(margin, maxY)
  }

  private fun startSettingsSync(deps: OverlayDependencies, settingsHolder: MutableState<AppSettings>) {
    settingsCollectJob?.cancel()
    settingsCollectJob = overlayScope.launch {
      deps.settingsRepository.settings.collectLatest { latest ->
        settingsHolder.value = latest
        OverlayPerformanceMonitorBinding.syncUserPreference(latest, appContext)
      }
    }
  }

  private const val EDGE_MARGIN_DP = 24f
  private const val BLUR_RADIUS_PX = 40
  private const val TAG = "WidgetPopupOverlay"
}
