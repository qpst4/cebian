package com.slideindex.app.overlay

import com.slideindex.app.BuildConfig
import com.slideindex.app.di.AppDependencies
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
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.R
import com.slideindex.app.monitoring.PerformanceMonitor
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetCanvasLayout
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelGridLogic
import com.slideindex.app.widget.WidgetPanelLayoutMetrics
import com.slideindex.app.widget.WidgetPanelMutator
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.widget.WidgetPopupHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

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
  private var overlayDeps: AppDependencies? = null
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

    val hostContext = SlideIndexAccessibilityService.overlayHostContext()
      ?: run {
        Log.w(TAG, "show: accessibility service not connected")
        return false
      }
    val deps = SlideIndexAccessibilityService.overlayDependencies()
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
        WidgetPopupOverlayRoot(
          settings = settingsHolder.value,
          visible = visible.value,
          blockingTouches = blockingTouches.value,
          widgetAddFlowActive = widgetAddFlowActive.value,
          side = panelSide.value,
          anchorRawY = anchorY.value,
          onDismissOutside = { dismiss() },
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
    acquirePerformanceMonitor(settings)
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
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
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
    releasePerformanceMonitor()
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

  private fun startSettingsSync(deps: AppDependencies, settingsHolder: MutableState<AppSettings>) {
    settingsCollectJob?.cancel()
    settingsCollectJob = overlayScope.launch {
      deps.settingsRepository.settings.collectLatest { latest ->
        settingsHolder.value = latest
        syncPerformanceMonitor(latest)
      }
    }
  }

  private fun syncPerformanceMonitor(settings: AppSettings) {
    if (!BuildConfig.DEBUG) return
    PerformanceMonitor.setUserPreference(settings.debugPerformanceMonitorEnabled)
  }

  private fun acquirePerformanceMonitor(settings: AppSettings) {
    if (!BuildConfig.DEBUG) return
    PerformanceMonitor.setUserPreference(settings.debugPerformanceMonitorEnabled)
    PerformanceMonitor.acquireOverlay()
  }

  private fun releasePerformanceMonitor() {
    if (!BuildConfig.DEBUG) return
    PerformanceMonitor.releaseOverlay()
  }

  private const val EDGE_MARGIN_DP = 24f
  private const val BLUR_RADIUS_PX = 40
  private const val TAG = "WidgetPopupOverlay"

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun WidgetPopupOverlayRoot(
    settings: AppSettings,
    visible: Boolean,
    blockingTouches: Boolean,
    widgetAddFlowActive: Boolean,
    side: PanelSide?,
    anchorRawY: Float?,
    onDismissOutside: () -> Unit,
  ) {
    SlideIndexTheme(
      seedColor = Color(settings.themeColorArgb),
      dynamicColor = settings.dynamicColorEnabled,
    ) {
      val context = LocalContext.current
      val hostContext = appContext ?: context
      val deps = overlayDeps ?: return@SlideIndexTheme
      val dm = context.resources.displayMetrics
      val density = LocalDensity.current

      var pages by remember {
        mutableStateOf(
          WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
            .map { WidgetPanelGridLogic.fitPageToGrid(it) },
        )
      }
      val latestPages by rememberUpdatedState(pages)
      var editMode by remember { mutableStateOf(false) }
      var gridInteractionActive by remember { mutableStateOf(false) }
      val pagerState = rememberPagerState(pageCount = { pages.size })

      LaunchedEffect(visible) {
        if (!visible) {
          editMode = false
          gridInteractionActive = false
          return@LaunchedEffect
        }
        val stored = deps.settingsRepository.settings.first().widgetPanelPages
        pages = WidgetPanelDefaults.effectivePages(stored)
          .map { WidgetPanelGridLogic.fitPageToGrid(it) }
      }

      LaunchedEffect(editMode) {
        if (!editMode) {
          gridInteractionActive = false
        }
      }

      fun persist(updated: List<WidgetPanelPage>) {
        pages = updated
        savePages(updated)
      }

      fun launchWidgetPicker(pageIndex: Int) {
        WidgetPickerTrampoline.launch(
          context = hostContext,
          onAdded = { appWidgetId ->
            val updated = WidgetPanelMutator.addWidgetToPage(
              hostContext,
              latestPages,
              pageIndex,
              appWidgetId,
            )
            if (updated != null) {
              persist(updated)
            }
          },
        )
      }

      val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) OverlayPanelEnterAnimation.enterSpec else OverlayPanelEnterAnimation.exitSpec,
        label = "widgetPopupProgress",
      )

      val page = pages.getOrElse(pagerState.currentPage) { pages.first() }
      val panelAlpha = page.overlayAlpha.coerceIn(0.2f, 0.95f)
      val panelSurfaceColor = Color(0xFF1C1C1E).copy(
        alpha = if (editMode) {
          (panelAlpha + 0.2f).coerceAtMost(0.88f)
        } else if (settings.widgetPanelBlurEnabled) {
          (panelAlpha * 0.55f + 0.18f).coerceAtMost(0.72f)
        } else {
          (panelAlpha + 0.1f).coerceAtMost(0.82f)
        },
      )
      val layoutMetrics = WidgetPanelLayoutMetrics.compute(
        screenWidthPx = dm.widthPixels,
        page = page,
        density = density.density,
        panelPaddingDp = PANEL_PADDING_DP,
        panelInnerPaddingDp = PANEL_INNER_PADDING_DP,
      )
      val panelWidthDp = with(density) { layoutMetrics.panelWidthPx.toDp() }
      val viewportHeightDp = with(density) { layoutMetrics.viewportHeightPx.toDp() }
      val marginTopDp = page.marginTopDp.dp

      Box(Modifier.fillMaxSize()) {
        if (widgetAddFlowActive) {
          return@Box
        }
        if (blockingTouches) {
          Box(
            Modifier
              .fillMaxSize()
              .alpha(OverlayPanelEnterAnimation.alpha(progress) * 0.35f)
              .background(Color.Black)
              .pointerInput(blockingTouches, editMode) {
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
                    onClick = {
                      if (editMode) editMode = false else onDismissOutside()
                    },
                  )
                } else {
                  Modifier
                },
              ),
          )
        }

        Box(
          Modifier
            .align(Alignment.TopCenter)
            .padding(top = marginTopDp)
            .width(panelWidthDp)
            .graphicsLayer {
              alpha = OverlayPanelEnterAnimation.alpha(progress)
              scaleX = 0.92f + 0.08f * progress
              scaleY = 0.92f + 0.08f * progress
            },
        ) {
          Box(
            modifier = Modifier.fillMaxWidth(),
          ) {
            Box(
              modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(panelSurfaceColor),
            )
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(PANEL_PADDING_DP.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            if (editMode) {
              Text(
                text = stringResource(R.string.widget_panel_edit_mode),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 6.dp),
              )
            } else if (page.items.isEmpty()) {
              Text(
                text = stringResource(R.string.widget_panel_edit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 4.dp),
              )
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeightDp)
                .clip(RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState(), enabled = !gridInteractionActive)
            ) {
              HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = !editMode,
              ) { pageIndex ->
                val currentPage = pages[pageIndex]
                AndroidView(
                  factory = { ctx ->
                    WidgetCanvasLayout(ctx).apply {
                      val pad = (PANEL_INNER_PADDING_DP * dm.density).roundToInt()
                      setPadding(pad, pad, pad, pad)
                      onLongPressBlank = { editMode = true }
                      onTapBlank = { if (!editMode) onDismissOutside() }
                      onPageCommitted = { committedPage ->
                        persist(WidgetPanelMutator.replacePage(latestPages, pageIndex, committedPage))
                      }
                      onItemRemoved = { widgetId ->
                        persist(WidgetPanelMutator.removeWidgetFromPage(hostContext, latestPages, pageIndex, widgetId))
                      }
                      onConfigureWidget = { widgetId ->
                        val intent = com.slideindex.app.service.WidgetConfigureTrampolineActivity.createIntent(hostContext, widgetId)
                        runCatching { hostContext.startActivity(intent) }
                        dismiss() // dismiss panel when configuring
                      }
                      onAddWidgetRequested = { launchWidgetPicker(pageIndex) }
                      onInteractionActiveChange = { gridInteractionActive = it }
                      bindIfNeeded(currentPage, hostContext)
                      this.editMode = editMode
                    }
                  },
                  update = { grid ->
                    grid.onLongPressBlank = { editMode = true }
                    grid.onTapBlank = { if (!editMode) onDismissOutside() }
                    grid.onPageCommitted = { committedPage ->
                      persist(WidgetPanelMutator.replacePage(latestPages, pageIndex, committedPage))
                    }
                    grid.onItemRemoved = { widgetId ->
                      persist(WidgetPanelMutator.removeWidgetFromPage(hostContext, latestPages, pageIndex, widgetId))
                    }
                    grid.onConfigureWidget = { widgetId ->
                      val intent = com.slideindex.app.service.WidgetConfigureTrampolineActivity.createIntent(hostContext, widgetId)
                      runCatching { hostContext.startActivity(intent) }
                      dismiss()
                    }
                    grid.onAddWidgetRequested = { launchWidgetPicker(pageIndex) }
                    grid.onInteractionActiveChange = { gridInteractionActive = it }
                    grid.editMode = editMode
                    grid.bindIfNeeded(currentPage, hostContext)
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .nestedScroll(rememberNestedScrollInteropConnection()),
                  onRelease = { /* host views cleaned on rebind */ },
                )
              }
            }

            if (pages.size > 1) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
              ) {
                pages.forEachIndexed { index, _ ->
                  Box(
                    Modifier
                      .padding(horizontal = 3.dp)
                      .height(6.dp)
                      .width(if (pagerState.currentPage == index) 12.dp else 6.dp)
                      .clip(CircleShape)
                      .background(
                        if (pagerState.currentPage == index) {
                          Color.White.copy(alpha = 0.7f)
                        } else {
                          Color.White.copy(alpha = 0.25f)
                        },
                      ),
                  )
                }
              }
            }
            }
          }

          if (visible && editMode) {
            Column(
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              val fabSize = 48.dp
              FloatingActionButton(
                onClick = { launchWidgetPicker(pagerState.currentPage) },
                modifier = Modifier.size(fabSize),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.widget_panel_add_widget))
              }
              FloatingActionButton(
                onClick = { editMode = false },
                modifier = Modifier.size(fabSize),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
              ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.widget_panel_exit_edit))
              }
            }
          }
        }

      }
    }
  }

  private const val PANEL_PADDING_DP = 12f
  private const val PANEL_INNER_PADDING_DP = 4f
}
