package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.slideindex.app.di.OverlayDependencies
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelLayoutMetrics
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.widget.WidgetPopupCardLayout
import com.slideindex.app.widget.WidgetPopupHost
import com.slideindex.app.widget.WidgetPopupRootLayout
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Samsung OHO+ style full-screen transparent overlay window hosting pure native widget popup card.
 */
@SuppressLint("StaticFieldLeak") // Overlay singleton; views/handlers cleared in cleanup()
object WidgetPopupOverlayWindow {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var windowManager: WindowManager? = null
  private var rootLayout: WidgetPopupRootLayout? = null
  private var cardLayout: WidgetPopupCardLayout? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  private var isVisible = false
  private var blockingTouches = true
  private var currentSettings: AppSettings? = null
  private var currentSide: PanelSide? = null
  private var currentAnchorRawY: Float? = null
  private var isWidgetAddFlowActive = false

  @Volatile
  private var pendingPagesToSave: List<WidgetPanelPage>? = null
  private var screenOffReceiver: BroadcastReceiver? = null
  private var appContext: Context? = null
  private var overlayDeps: OverlayDependencies? = null
  private var settingsCollectJob: Job? = null
  private var suspendedForPicker = false
  private var savedFlagsBeforePickerSuspend: Int? = null
  private var backHandler: OverlayViewBackHandler? = null
  private var chromeRaiseToken = 0

  val isShowing: Boolean
    get() = rootLayout != null && isVisible && !suspendedForPicker

  val isAddFlowActive: Boolean
    get() = isWidgetAddFlowActive

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
      if (isVisible) return true
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

    val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
      ?: return false
    val density = hostContext.resources.displayMetrics.density
    val screenWidthPx = hostContext.resources.displayMetrics.widthPixels
    val effectivePages = WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
    val initialPage = effectivePages.firstOrNull() ?: WidgetPanelPage()
    val initialMetrics = WidgetPanelLayoutMetrics.compute(
      screenWidthPx = screenWidthPx,
      page = initialPage,
      density = density,
    )
    val panelWidthPx = initialMetrics.panelWidthPx
    val panelPaddingPx = (12f * density).roundToInt() * 2
    val indicatorHeightPx = if (effectivePages.size > 1) (14f * density).roundToInt() else 0
    val hintHeightPx = if (initialPage.items.isEmpty()) (20f * density).roundToInt() else 0
    val panelHeightPx = panelPaddingPx + initialMetrics.viewportHeightPx + indicatorHeightPx + hintHeightPx
    val marginTopPx = (initialPage.marginTopDp * density).roundToInt()

    val root = WidgetPopupRootLayout(hostContext, onDismissOutside = { dismiss() })
    val card = WidgetPopupCardLayout(
      context = hostContext,
      hostContext = hostContext,
      deps = deps,
      settings = settings,
      onDismiss = { dismiss() },
      onSavePages = { pages -> savePages(pages) },
    )
    root.cardView = card

    val cardLp = FrameLayout.LayoutParams(panelWidthPx, panelHeightPx).apply {
      gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
      topMargin = marginTopPx
    }
    root.addView(card, cardLp)

    val params = buildLayoutParams(hostContext)
    val added = runCatching { wm.addView(root, params) }
      .onFailure { Log.e(TAG, "addView failed", it) }
      .isSuccess
    if (!added) {
      return false
    }

    windowManager = wm
    rootLayout = root
    cardLayout = card
    layoutParams = params
    isVisible = true
    blockingTouches = true
    currentSettings = settings
    currentSide = side
    currentAnchorRawY = anchorRawY
    isWidgetAddFlowActive = false
    appContext = hostContext
    overlayDeps = deps

    OverlayPerformanceMonitorBinding.onOverlayShown(settings, hostContext)
    startSettingsSync(deps)
    registerScreenOffReceiver(hostContext)
    OverlayPanelSystemGestureExclusion.attach(root, excludeLeftBackEdge = false)
    if (FloatBallOverlay.isShowing) {
      FloatBallOverlay.notifyPanelAttachedAboveChrome()
    }
    OverlaySceneController.onContentPanelShown()

    WidgetPopupHost.startListening(hostContext)
    SlideIndexAccessibilityService.refreshTriggerVisuals()

    root.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
      override fun onPreDraw(): Boolean {
        root.viewTreeObserver.removeOnPreDrawListener(this)
        card.ensureBackgroundBlurAttached()
        return true
      }
    })

    root.post {
      activateBackHandling()
      scheduleBringChromeAbovePanels()
    }
    return true
  }

  /** Hides the popup while the full-screen widget picker overlay is on top. */
  fun suspendForPickerOverlay() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { suspendForPickerOverlay() }
      return
    }
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    if (suspendedForPicker) return
    deactivateBackHandling()
    suspendedForPicker = true
    savedFlagsBeforePickerSuspend = params.flags
    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    root.visibility = View.GONE
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "suspendForPickerOverlay updateViewLayout failed", it) }
  }

  fun resumeAfterPickerOverlay() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { resumeAfterPickerOverlay() }
      return
    }
    if (!suspendedForPicker) return
    suspendedForPicker = false
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    savedFlagsBeforePickerSuspend?.let { params.flags = it }
    savedFlagsBeforePickerSuspend = null
    if (!isWidgetAddFlowActive) {
      params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
      blockingTouches = true
    }
    if (isVisible && !isWidgetAddFlowActive) {
      root.visibility = View.VISIBLE
    }
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "resumeAfterPickerOverlay updateViewLayout failed", it) }
    if (isVisible && !isWidgetAddFlowActive) {
      activateBackHandling()
      scheduleBringChromeAbovePanels()
    }
  }

  fun dismiss() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { dismiss() }
      return
    }
    if (!isVisible) {
      cleanup()
      return
    }
    deactivateBackHandling()
    isVisible = false
    ++chromeRaiseToken
    cleanup()
  }

  fun setWidgetAddFlowActive(active: Boolean) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { setWidgetAddFlowActive(active) }
      return
    }
    isWidgetAddFlowActive = active
    if (active) {
      deactivateBackHandling()
      blockingTouches = false
      hideForWidgetAddFlow()
    } else {
      resumeAfterWidgetAddFlow()
    }
  }

  private fun hideForWidgetAddFlow() {
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    updateOverlayTouchable(false)
    root.visibility = View.GONE
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "hideForWidgetAddFlow updateViewLayout failed", it) }
  }

  private fun resumeAfterWidgetAddFlow() {
    if (rootLayout == null || !isVisible || suspendedForPicker) return
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    root.visibility = View.VISIBLE
    blockingTouches = true
    updateOverlayTouchable(true)
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "resumeAfterWidgetAddFlow updateViewLayout failed", it) }
    activateBackHandling()
    scheduleBringChromeAbovePanels()
  }

  private fun scheduleBringChromeAbovePanels() {
    if (!FloatBallOverlay.isShowing) return
    var token = ++chromeRaiseToken
    fun attempt() {
      if (token != chromeRaiseToken) return
      FloatBallOverlay.scheduleChromeAbovePanels(delayMs = 0L)
    }
    attempt()
    rootLayout?.post {
      attempt()
      rootLayout?.postOnAnimation { attempt() }
    }
    mainHandler.postDelayed({ attempt() }, CHROME_RAISE_RETRY_MS)
  }

  /** Clear NOT_FOCUSABLE + OverlayViewBackHandler so system back reaches us. */
  private fun activateBackHandling() {
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    if (suspendedForPicker || root.parent == null) return
    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
    runCatching { wm.updateViewLayout(root, params) }
    root.isFocusable = true
    root.isFocusableInTouchMode = true
    root.requestFocus()
    backHandler?.detach()
    backHandler = OverlayViewBackHandler(root, ::dismiss).also { it.attach() }
  }

  private fun deactivateBackHandling() {
    backHandler?.detach()
    backHandler = null
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    if (root.parent == null) return
    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    runCatching { wm.updateViewLayout(root, params) }
    root.clearFocus()
  }

  private fun updateOverlayTouchable(touchable: Boolean) {
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = root.layoutParams as? WindowManager.LayoutParams ?: return
    if (touchable) {
      params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    } else {
      params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "updateOverlayTouchable failed", it) }
  }

  private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
    val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
      WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    return WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      OverlayWindowTypes.contentPanelWindowType(context),
      flags,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      windowAnimations = android.R.style.Animation_Dialog
      layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      OverlayWindowTypes.ensureNoBrightnessOverride(this)
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
    deactivateBackHandling()
    val root = rootLayout
    val wm = windowManager
    if (root != null) {
      if (wm != null) {
        runCatching { wm.removeView(root) }
      }
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
    rootLayout = null
    cardLayout = null
    layoutParams = null
    windowManager = null
    suspendedForPicker = false
    savedFlagsBeforePickerSuspend = null
    OverlaySceneController.onContentPanelHidden()
    SlideIndexAccessibilityService.refreshTriggerVisuals()
    isVisible = false
    blockingTouches = false
    currentSettings = null
    currentSide = null
    currentAnchorRawY = null
    isWidgetAddFlowActive = false
    pendingPagesToSave = null
    screenOffReceiver = null
    appContext = null
    overlayDeps = null
  }

  private fun startSettingsSync(deps: OverlayDependencies) {
    settingsCollectJob?.cancel()
    settingsCollectJob = overlayScope.launch {
      deps.settingsRepository.settings.collectLatest { latest ->
        currentSettings = latest
        cardLayout?.updateSettings(latest)
        OverlayPerformanceMonitorBinding.syncUserPreference(latest, appContext)
      }
    }
  }

  private const val CHROME_RAISE_RETRY_MS = 200L
  private const val TAG = "WidgetPopupOverlay"
}
