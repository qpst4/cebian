package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.IndexSessionHost
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.gesture.primaryTriggerHandle
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.gesture.triggerHandle
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.ShellCommandEditorOverlaySheet
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.OverlayService
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.BrightnessControlHelper
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.VolumeControlHelper
import com.slideindex.app.util.OverlayBrightnessControl
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore
import com.slideindex.app.util.TaskSwitcherMenuActions
import com.slideindex.app.util.GestureActionIconBitmap
import com.slideindex.app.util.QuickLauncherIconResolver
import com.slideindex.app.util.coerceSafe
import kotlin.math.ceil
import kotlin.math.min

/**
 * 边缘手势 Overlay：识别层（GestureSession）+ 索引 UI（Canvas 绘制）。
 */
class EdgeGestureOverlayView(
    context: Context,
    private val side: PanelSide,
    private val appRepository: AppRepository,
    private val onSessionStartCallback: () -> Unit,
    private val onSessionEndCallback: () -> Unit,
    private val onGestureTrackingStartCallback: () -> Unit = {},
    private val onAdjustPanelLayoutCallback: (Float) -> Unit = {},
    private val onAdjustPanelDismissCallback: () -> Unit = {},
    private val onClickPassthroughCallback: (Float, Float, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit = {},
    private val onQuickLauncherItemsPersist: (List<QuickLauncherItem>) -> Unit = {},
    private val onShellPanelFocusChange: (Boolean) -> Unit = {},
    private val onOverlayWindowSuspend: () -> Unit = {},
    private val onOverlayWindowResume: () -> Unit = {},
    overlayBrightness: OverlayBrightnessControl? = null,
) : View(context), IndexSessionHost, GestureSession.Callbacks {

    init {
        isClickable = true
        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }
            handleShellPanelBackPress()
        }
    }

    private var settings = AppSettings()
    private var apps: List<AppInfo> = emptyList()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY

    private sealed class OverlayTouchLayout {
        data class TriggerCollapsed(val bounds: CollapsedWindowBounds) : OverlayTouchLayout()
        data class GestureTracking(val bounds: CollapsedWindowBounds) : OverlayTouchLayout()
        data object FullScreen : OverlayTouchLayout()
        data object AdjustPanel : OverlayTouchLayout()
    }

    private var overlayTouchLayout: OverlayTouchLayout = OverlayTouchLayout.FullScreen

    private val zoneLayout = GestureZoneLayout(side)
    private val indexSession = SlideAlongRailSession(side, zoneLayout, this)
    private val panelGridSession = PanelGridSession()
    private val actionExecutor = ActionExecutor(
        context = context,
        appRepository = appRepository,
        clickPassthroughHandler = onClickPassthroughCallback,
        overlayBrightness = overlayBrightness,
        side = side,
    )
    private val pathRecognizer = SwipePathRecognizer(side, resources.displayMetrics.density)
    private val gestureSession = GestureSession(
        side = side,
        zoneLayout = zoneLayout,
        indexSession = indexSession,
        pathRecognizer = pathRecognizer,
        actionExecutor = actionExecutor,
        callbacks = this,
    )
    private val shellOverlayDialogHost = OverlayComposeDialogHost(
        context = context,
        themeSeedArgb = { settings.themeColorArgb },
        dynamicColor = { settings.dynamicColorEnabled },
    )
    private val shellPanelController = ShellCommandPanelController(
        object : ShellCommandPanelController.Host {
            override val context: Context get() = this@EdgeGestureOverlayView.context
            override fun settings(): AppSettings = settings
            override fun isDialogShowing(): Boolean = shellOverlayDialogHost.isShowing
            override fun dismissDialogs() = shellOverlayDialogHost.dismiss()
            override fun requestEndSession() = endShellPanelSessionAnimated()
            override fun showEditorDialog(
                existing: ShellCommand?,
                shizukuGranted: Boolean,
                onDismissComplete: () -> Unit,
                onSave: (ShellCommand) -> Unit,
                onDelete: (() -> Unit)?,
                onTest: (ShellCommand, (Int, String) -> Unit) -> Unit,
            ) {
                var animatedDismiss: (() -> Unit)? = null
                shellOverlayDialogHost.show(onBackPressed = {
                    animatedDismiss?.invoke()
                    true
                }) {
                    ShellCommandEditorOverlaySheet(
                        onDismissComplete = {
                            shellOverlayDialogHost.dismiss()
                            onDismissComplete()
                            syncShellPanelInputFocus()
                        },
                        initial = existing,
                        shizukuGranted = shizukuGranted,
                        onSave = onSave,
                        onDelete = onDelete,
                        onTest = onTest,
                        registerBackHandler = { animatedDismiss = it },
                    )
                }
                syncShellPanelInputFocus()
                invalidate()
            }
            override fun viewWidth(): Int = width
            override fun viewHeight(): Int = height
            override fun dp(value: Float): Float = this@EdgeGestureOverlayView.dp(value)
            override fun sp(value: Float): Float = this@EdgeGestureOverlayView.sp(value)
            override fun invalidate() {
                this@EdgeGestureOverlayView.invalidate()
                if (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS) {
                    post { syncShellPanelInputFocus() }
                }
            }
            override fun post(action: () -> Unit) {
                this@EdgeGestureOverlayView.post(action)
            }
            override fun postDelayed(delayMs: Long, action: () -> Unit) {
                this@EdgeGestureOverlayView.postDelayed(action, delayMs)
            }
            override fun postDelayedRunnable(delayMs: Long, runnable: Runnable) {
                this@EdgeGestureOverlayView.postDelayed(runnable, delayMs)
            }
            override fun removeCallbacks(runnable: Runnable) {
                this@EdgeGestureOverlayView.removeCallbacks(runnable)
            }
            override fun hapticTick() = HapticHelper.appTick(this@EdgeGestureOverlayView, settings)
            override fun hapticConfirm() = HapticHelper.confirmLaunch(this@EdgeGestureOverlayView, settings)
            override fun endSession() = gestureSession.endSession()
            override fun onPersist(commands: List<ShellCommand>) {
                onShellCommandsPersist(commands)
            }
        },
    )
    private val quickLauncherOverlayDialogHost = OverlayComposeDialogHost(
        context = context,
        themeSeedArgb = { settings.themeColorArgb },
        dynamicColor = { settings.dynamicColorEnabled },
    )
    private val quickLauncherPanelController = QuickLauncherPanelController(
        object : QuickLauncherPanelController.Host {
            override val context: Context get() = this@EdgeGestureOverlayView.context
            override fun settings(): AppSettings = settings
            override fun side(): PanelSide = side
            override fun apps(): List<AppInfo> = apps
            override fun isPanelReady(): Boolean = panelEnterProgress >= 1f
            override fun isAddDialogShowing(): Boolean =
                QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing
            override fun dp(value: Float): Float = this@EdgeGestureOverlayView.dp(value)
            override fun sp(value: Float): Float = this@EdgeGestureOverlayView.sp(value)
            override fun invalidate() = this@EdgeGestureOverlayView.invalidate()
            override fun hapticTick() = HapticHelper.appTick(this@EdgeGestureOverlayView, settings)
            override fun showAddDialog(
                configuredAppPackages: Set<String>,
                configuredShortcutKeys: Set<String>,
                configuredActionKeys: Set<String>,
                onAdd: (QuickLauncherItem) -> Unit,
                onRemove: (QuickLauncherItem) -> Unit,
            ) {
                QuickLauncherAddTrampoline.launch(
                    context = context,
                    panelSide = side,
                    configuredAppPackages = configuredAppPackages,
                    configuredShortcutKeys = configuredShortcutKeys,
                    configuredActionKeys = configuredActionKeys,
                    onPrepare = { onOverlayWindowSuspend() },
                    onDismiss = {
                        CreateShortcutTrampoline.cancelPending()
                        onOverlayWindowResume()
                        invalidate()
                        notifyPresentationTouchRequirementChanged()
                    },
                    onAdd = onAdd,
                    onRemove = onRemove,
                )
            }
            override fun onPersist(items: List<QuickLauncherItem>) {
                invalidateQuickLauncherDerivedCaches()
                onQuickLauncherItemsPersist(items)
            }
            override fun quickLauncherPageSize(): Int = this@EdgeGestureOverlayView.quickLauncherPageSize()
            override fun onEditDragMove(touchX: Float, localY: Float, panelRect: RectF) {
                applyEditDragAutoPage(touchX, panelRect)
            }
            override fun onEditDragBegan() {
                quickLauncherEdgeAutoPageSeeded = false
                quickLauncherEdgePageZone = 0
            }
            override fun resolveEditDragTargetGlobal(
                touchX: Float,
                localY: Float,
                panelRect: RectF,
            ): Int = quickLauncherGlobalIndexAt(touchX, localY, panelRect)
        },
    )

    private var recentApps = mutableListOf<RecentAppEntry>()
    private var panelContentRect = RectF()
    private var taskSwitcherLayout: TaskSwitcherPanelLayout? = null
    private var taskSwitcherRowHighlight = -1
    private var taskSwitcherCloseHighlight = -1
    private var taskSwitcherFreeWindowHighlight = -1
    private var taskSwitcherCloseAllHighlight = false
    private var taskSwitcherClosePressIndex = -1
    private var taskSwitcherClosePressDownTime = 0L
    private var taskSwitcherCloseLongPressTriggered = false
    private var taskSwitcherCloseHapticIndex = -1
    private var taskSwitcherContinuousHapticKey = -1
    private var taskSwitcherCloseLongPressRunnable: Runnable? = null
    private var taskSwitcherRowPressIndex = -1
    private var taskSwitcherRowPressDownTime = 0L
    private var taskSwitcherRowLongPressTriggered = false
    private var taskSwitcherRowLongPressRunnable: Runnable? = null
    private var taskSwitcherContextMenu: TaskSwitcherContextMenuLayout? = null
    private var taskSwitcherMenuHighlight = -1
    private var taskSwitcherMenuAwaitingRelease = false
    private var taskSwitcherMenuEnterProgress = 1f
    private var taskSwitcherMenuEnterAnimator: ValueAnimator? = null
    private var taskSwitcherMenuDismissing = false
    private var lastTaskSwitcherTouchX = 0f
    private var lastTaskSwitcherTouchY = 0f
    private var taskSwitcherLoadGeneration = 0
    private var taskSwitcherAnchorRawY: Float? = null
    private var taskSwitcherFrozenAnchorLocalY: Float? = null
    private var quickLauncherAnchorRawY: Float? = null
    private var quickLauncherFrozenAnchorLocalY: Float? = null
    private var quickLauncherContinuousHapticIndex = -1
    private var quickLauncherPressIndex = -1
    private var quickLauncherPressDownTime = 0L
    private var quickLauncherLongPressArmed = false
    private var quickLauncherLongPressIndex = -1
    private var quickLauncherLongPressRunnable: Runnable? = null
    private var quickLauncherPageIndex = 0
    private var quickLauncherPageCount = 1
    private var quickLauncherPageSwipeStartX = 0f
    private var quickLauncherPageSwipeStartY = 0f
    private var quickLauncherPageSwipeTracking = false
    private var quickLauncherPageSwipeLocked = false
    private var quickLauncherPageChangedThisGesture = false
    private var quickLauncherPageDragOffset = 0f
    private var quickLauncherPageSnapAnimator: ValueAnimator? = null
    private var quickLauncherLaunchEndDeferMs = 0L
    private var quickLauncherExiting = false
    private var quickLauncherOpeningGestureActive = false
    private var quickLauncherToolbarTouchActive = false
    /** -1 = outer edge, 0 = middle, 1 = inner edge; used for continuous edge auto-page. */
    private var quickLauncherEdgePageZone = 0
    private var quickLauncherEdgeAutoPageSeeded = false
    private var taskSwitcherScrollOffset = 0f
    private var taskSwitcherScrollDragging = false
    private var taskSwitcherScrollDragStartY = 0f
    private var taskSwitcherScrollDragStartOffset = 0f
    private var taskSwitcherOverscrollOffset = 0f
    private var taskSwitcherOverscrollAnimator: ValueAnimator? = null
    private var taskSwitcherGestureScrolled = false
    private var taskSwitcherExiting = false
    private var taskSwitcherLoading = false
    private var panelEnterProgress = 1f
    private var shellPanelExiting = false
    private var lastAdjustInvalidateMs = 0L
    private var panelEnterAnimator: ValueAnimator? = null
    private var adjustIndicatorProgress = 0f
    private var adjustIndicatorAnimator: ValueAnimator? = null
    private var adjustIndicatorLayout: AdjustLevelIndicator.Layout? = null
    private var adjustIndicatorHoldVisual: AdjustIndicatorVisual? = null
    private var adjustIndicatorFrozenLayout: AdjustLevelIndicator.Layout? = null
    private var adjustPanelDismissing = false
    private var wasAdjustMode = false
    private var adjustIndicatorReceding = false
    private var adjustPanelExpandedForGesture = false
    private var adjustPanelEntering = false
    private var adjustPanelState: AdjustPanelState? = null
    private var volumeDragAnchorRawY = 0f
    private var volumeDragBaseline = 0f
    private var volumeChangeReceiver: BroadcastReceiver? = null
    private var brightnessSettingsObserver: ContentObserver? = null
    private val brightnessSettingsHandler = Handler(Looper.getMainLooper())
    /** True after this capture stream consumed ACTION_DOWN; keeps UP/MOVE consistent for IMMEDIATE actions. */
    private var edgeCaptureTouchActive = false
    private val gestureAnimationOverlay
        get() = GestureAnimationOverlayRegistry.controller(side)

    private val railLetters: List<Char> = ('A'..'Z').toList() + '#'
    private val iconCache = mutableMapOf<String, Bitmap>()
    private var quickLauncherAppsByPackage: Map<String, AppInfo> = emptyMap()
    private val quickLauncherIconCache = mutableMapOf<String, Bitmap>()
    private val quickLauncherLabelCache = mutableMapOf<String, String>()
    private var quickLauncherCachedPages: List<List<QuickLauncherItem>>? = null
    private var quickLauncherCachedPagesKey: Int = 0
    private var quickLauncherLayoutPanelWidth: Float = 0f

    private val railBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val bubbleLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
        color = Color.argb(230, 255, 255, 255)
    }
    private val cellHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellLongPressHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triggerPreviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triggerPreviewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cellInitialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val elevatedCardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val elevatedShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tmpRect = RectF()
    private val highlightPath = Path()

    private val cellHeight get() = dp(72f)
    private val cellWidth get() = dp(68f)
    private val gridIconSize get() = dp(44f)
    private val gridPadding get() = dp(10f)
    private val quickLauncherCellHeight get() = dp(64f)
    private val quickLauncherCellWidth get() = dp(56f)
    private val quickLauncherGridIconSize get() = dp(38f)
    private val quickLauncherGridPadding get() = dp(8f)
    private val quickLauncherHeaderHeight get() = dp(24f)
    private val quickLauncherGridIconTopInset get() = dp(4f)
    private val quickLauncherGridIconLabelGap get() = dp(4f)
    private val gridIconTopInset get() = dp(6f)
    private val gridIconLabelGap get() = dp(3f)
    private val gridCellInset get() = dp(4f)
    private val bubbleRadius get() = dp(24f)
    private val bubblePanelGap get() = dp(10f)
    private val railCorner get() = dp(14f)
    private val panelCorner get() = dp(18f)

    private data class GridLayoutInfo(
        val appsPerRow: Int,
        val panelColumns: Int,
        val rows: Int,
        val panelWidth: Float,
    )

    private data class AdjustIndicatorVisual(
        val mode: ContinuousAdjustController.Mode,
        val fraction: Float,
        val anchorRawY: Float,
    )

    fun applySettings(newSettings: AppSettings, screenWidth: Int) {
        settings = newSettings
        shellPanelController.syncSettings(newSettings)
        quickLauncherPanelController.syncSettings(newSettings)
        gestureSession.applySettings(newSettings)
        invalidateQuickLauncherDerivedCaches()
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        gestureAnimationOverlay.applySettings(newSettings)
        syncZoneLayout()
        invalidate()
    }

    fun isSessionActive(): Boolean = gestureSession.isActive()

    fun applyCollapsedTriggerLayout(bounds: CollapsedWindowBounds) {
        applyExpandedOverlayLayout()
    }

    fun applyExpandedOverlayLayout() {
        overlayTouchLayout = OverlayTouchLayout.FullScreen
        syncZoneLayout()
    }

    /** Presentation layer accepts direct touches when panels / adjust UI need hit-testing. */
    fun needsPresentationDirectTouch(): Boolean {
        if (previewMode) return false
        if (adjustPanelState != null) return true
        if (gestureSession.panelMode() != OverlayPanelMode.NONE) return true
        if (quickLauncherOverlayDialogHost.isShowing || shellOverlayDialogHost.isShowing) return true
        return false
    }

    /** Keep the presentation layer touch-passthrough while a compose overlay dialog is on top. */
    fun presentationShouldPassthroughTouches(): Boolean =
        quickLauncherOverlayDialogHost.isShowing || shellOverlayDialogHost.isShowing

    private fun composeOverlayDialogShowing(): Boolean =
        quickLauncherOverlayDialogHost.isShowing || shellOverlayDialogHost.isShowing

    fun syncOverlayDialogZOrder() {
        if (quickLauncherOverlayDialogHost.isShowing) {
            quickLauncherOverlayDialogHost.bringToFront()
        }
        if (shellOverlayDialogHost.isShowing) {
            shellOverlayDialogHost.bringToFront()
        }
    }

    var onPresentationTouchRequirementChanged: (() -> Unit)? = null

    private fun notifyPresentationTouchRequirementChanged() {
        onPresentationTouchRequirementChanged?.invoke()
    }

    /** Entry for edge capture window; also used by presentation [onTouchEvent] when panels are open. */
    fun handleOverlayTouch(event: MotionEvent): Boolean {
        if (previewMode) return false
        if (composeOverlayDialogShowing()) return false
        val (localX, localY) = rawToLocal(event.rawX, event.rawY)
        if (adjustPanelState != null && !gestureSession.isActive()) {
            if (handleAdjustPanelTouch(event, localX, localY)) return true
        }
        when (gestureSession.panelMode()) {
            OverlayPanelMode.QUICK_LAUNCHER ->
                return handleQuickLauncherTouch(event, localX, localY)
            OverlayPanelMode.SHELL_COMMANDS ->
                return handleShellCommandsTouch(event, localX, localY)
            OverlayPanelMode.TASK_SWITCHER ->
                return handleTaskSwitcherTouch(event, localX, localY)
            OverlayPanelMode.INDEX -> return handleIndexTouch(event, localX, localY)
            OverlayPanelMode.NONE -> Unit
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    adjustPanelState != null && !gestureSession.isActive() && !adjustPanelDismissing ->
                        forceRecoverInteractionState()
                    gestureSession.panelMode() != OverlayPanelMode.NONE && !gestureSession.isActive() ->
                        gestureSession.forceReset(notifySessionEnd = true)
                    gestureSession.isActive() ->
                        gestureSession.forceReset(notifySessionEnd = false)
                }
                if (gestureSession.onTouchDown(event.rawX, event.rawY, localX, localY)) {
                    edgeCaptureTouchActive = true
                    syncZoneLayout()
                    onGestureTrackingStartCallback()
                    post {
                        startGestureAnimationIfNeeded(event.rawX, event.rawY)
                    }
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!edgeCaptureTouchActive) return false
                if (!gestureSession.isActive()) return true
                forEachGesturePoint(event, localX, localY, includeHistory = true) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    updateGestureAnimationIfNeeded(rawX, rawY)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureSession.isActive()) {
                    val consumed = edgeCaptureTouchActive
                    if (consumed) {
                        edgeCaptureTouchActive = false
                        finishGestureAnimationIfNeeded()
                    }
                    return consumed || event.actionMasked == MotionEvent.ACTION_CANCEL
                }
                edgeCaptureTouchActive = false
                val canceled = event.actionMasked == MotionEvent.ACTION_CANCEL
                forEachGesturePoint(event, localX, localY, includeHistory = true) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    updateGestureAnimationIfNeeded(rawX, rawY)
                }
                finishGestureAnimationIfNeeded()
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                if (canceled) {
                    gestureAnimationOverlay.hide()
                }
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (composeOverlayDialogShowing()) return false
        if (!needsPresentationDirectTouch()) return false
        return handleOverlayTouch(event)
    }

    fun applyGestureTrackingLayout(bounds: CollapsedWindowBounds) {
        overlayTouchLayout = OverlayTouchLayout.GestureTracking(bounds)
        syncZoneLayout()
    }

    fun applyAdjustPanelOverlayLayout() {
        overlayTouchLayout = OverlayTouchLayout.AdjustPanel
        syncZoneLayout()
    }

    fun hasAdjustPanel(): Boolean = adjustPanelState != null

    fun keepsOverlayExpanded(): Boolean =
        gestureSession.isActive() ||
            gestureSession.panelMode() != OverlayPanelMode.NONE ||
            adjustPanelState != null ||
            (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS &&
                shellPanelController.hasActiveUi())

    /** Narrow capture windows expand to the open panel (incl. toolbar) so off-edge controls receive touches. */
    fun panelInteractionCaptureBounds(): List<CollapsedWindowBounds>? {
        if (previewMode || quickLauncherExiting || composeOverlayDialogShowing()) return null
        return when (gestureSession.panelMode()) {
            OverlayPanelMode.QUICK_LAUNCHER -> {
                if (panelEnterProgress < 1f) return null
                val panelRect = quickLauncherPanelRect()
                if (panelRect.isEmpty) return null
                val content = quickLauncherPanelController.combinedContentRect(panelRect)
                if (content.isEmpty) return null
                listOf(captureBoundsForContent(content))
            }
            else -> null
        }
    }

    private fun captureBoundsForContent(content: RectF): CollapsedWindowBounds {
        val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val pad = dp(12f).toInt()
        val top = (content.top - pad).toInt().coerceAtLeast(0)
        val height = (content.height() + pad * 2f).toInt().coerceAtLeast(1)
        return when (side) {
            PanelSide.LEFT -> CollapsedWindowBounds(
                widthPx = (content.right + pad).toInt().coerceIn(1, screenW),
                heightPx = height,
                yPx = top,
            )
            PanelSide.RIGHT -> CollapsedWindowBounds(
                widthPx = (screenW - content.left + pad).toInt().coerceIn(1, screenW),
                heightPx = height,
                yPx = top,
            )
        }
    }

    /** Clears stuck gesture/panel state without re-entering session callbacks. */
    fun forceRecoverInteractionState() {
        if (adjustPanelDismissing) return
        gestureAnimationOverlay.hide()
        edgeCaptureTouchActive = false
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorProgress = 0f
        adjustPanelDismissing = false
        adjustPanelExpandedForGesture = false
        adjustPanelEntering = false
        clearAdjustIndicatorExitState()
        if (adjustPanelState != null) {
            stopAdjustPanelLevelSync()
            adjustPanelState = null
            onAdjustPanelDismissCallback()
        }
        gestureSession.forceReset(notifySessionEnd = false)
        syncZoneLayout()
        invalidate()
    }

    fun isPreviewMode(): Boolean = previewMode

    fun setPreviewMode(enabled: Boolean, content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY) {
        val changed = previewMode != enabled || previewContent != content
        if (!changed) return
        previewMode = enabled
        previewContent = content
        syncZoneLayout()
        invalidate()
    }

    fun setApps(newApps: List<AppInfo>) {
        apps = newApps
        indexSession.setApps(newApps)
        iconCache.clear()
        rebuildQuickLauncherAppsByPackage()
        invalidateQuickLauncherDerivedCaches()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        quickLauncherLayoutPanelWidth = 0f
        syncZoneLayout()
        adjustPanelState?.let { state ->
            if (!adjustPanelEntering) {
                updateAdjustIndicatorLayout(state.anchorRawY)
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        gestureAnimationOverlay.hide()
        stopAdjustPanelLevelSync()
        GestureActionIconBitmap.clear()
        super.onDetachedFromWindow()
    }

    private fun handleAdjustPanelTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (adjustPanelDismissing) return false
        val state = adjustPanelState ?: return false
        val density = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val layout = adjustIndicatorLayout ?: run {
                    dismissAdjustPanel()
                    return true
                }
                when (state.mode) {
                    ContinuousAdjustController.Mode.VOLUME -> when (
                        AdjustLevelIndicator.hitVolumeTarget(layout, side, localX, localY, density)
                    ) {
                        VolumeHitTarget.DND -> {
                            if (!VolumeControlHelper.hasAccess(context)) {
                                PermissionHelper.requestNotificationPolicyAccess(context)
                            } else {
                                actionExecutor.toggleDnd()?.let { state.interruptionFilter = it }
                                hapticConfirmLaunch()
                            }
                            invalidate()
                            return true
                        }
                        VolumeHitTarget.RINGER -> {
                            if (!VolumeControlHelper.hasAccess(context)) {
                                PermissionHelper.requestNotificationPolicyAccess(context)
                            } else {
                                actionExecutor.cycleRingerMode()?.let { state.ringerMode = it }
                                hapticConfirmLaunch()
                            }
                            invalidate()
                            return true
                        }
                        VolumeHitTarget.EXPAND -> {
                            state.volumeExpanded = !state.volumeExpanded
                            if (state.volumeExpanded) {
                                refreshVolumePanelLevels(state)
                            }
                            updateAdjustIndicatorLayout(state.anchorRawY)
                            hapticConfirmLaunch()
                            invalidate()
                            return true
                        }
                        VolumeHitTarget.MEDIA -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        VolumeHitTarget.RING -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.RING, event.rawY)
                            invalidate()
                            return true
                        }
                        VolumeHitTarget.NOTIFICATION -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.NOTIFICATION, event.rawY)
                            invalidate()
                            return true
                        }
                        VolumeHitTarget.NONE -> {
                            dismissAdjustPanel()
                            return true
                        }
                    }
                    ContinuousAdjustController.Mode.BRIGHTNESS -> when (
                        AdjustLevelIndicator.hitBrightnessTarget(layout, side, localX, localY, density)
                    ) {
                        BrightnessHitTarget.AUTO_BRIGHTNESS -> {
                            if (!BrightnessControlHelper.hasAccess(context)) {
                                PermissionHelper.requestWriteSettingsAccess(context)
                            } else {
                                actionExecutor.toggleAutoBrightness()?.let {
                                    state.autoBrightnessEnabled = it
                                    hapticConfirmLaunch()
                                }
                            }
                            invalidate()
                            return true
                        }
                        BrightnessHitTarget.DARK_MODE -> {
                            if (!BrightnessControlHelper.hasDarkModeAccess(context)) {
                                PermissionHelper.requestWriteSettingsAccess(context)
                            } else {
                                actionExecutor.toggleDarkMode()?.let {
                                    state.darkModeEnabled = it
                                    hapticConfirmLaunch()
                                }
                            }
                            invalidate()
                            return true
                        }
                        BrightnessHitTarget.BRIGHTNESS -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        BrightnessHitTarget.NONE -> {
                            dismissAdjustPanel()
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        actionExecutor.updateContinuousAdjust(state.mode, event.rawY)
                        state.fraction = actionExecutor.adjustFraction()
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> {
                        updateVolumeStreamDrag(state, event.rawY)
                    }
                    null -> return false
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        actionExecutor.endContinuousAdjust()
                        state.fraction = actionExecutor.readCurrentAdjustFraction(state.mode)
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> Unit
                    null -> Unit
                }
                state.dragTarget = null
                state.dragging = false
                invalidate()
                return true
            }
        }
        return false
    }

    private fun beginMainAdjustDrag(state: AdjustPanelState, rawY: Float) {
        state.dragTarget = VolumeDragTarget.MEDIA
        state.dragging = true
        actionExecutor.beginContinuousAdjust(state.mode, rawY)
        actionExecutor.updateContinuousAdjust(state.mode, rawY)
        invalidate()
    }

    private fun beginVolumeStreamDrag(state: AdjustPanelState, target: VolumeDragTarget, rawY: Float) {
        state.dragTarget = target
        state.dragging = true
        volumeDragAnchorRawY = rawY
        volumeDragBaseline = when (target) {
            VolumeDragTarget.RING -> state.ringFraction
            VolumeDragTarget.NOTIFICATION -> state.notificationFraction
            VolumeDragTarget.MEDIA -> state.fraction
        }
    }

    private fun updateVolumeStreamDrag(state: AdjustPanelState, rawY: Float) {
        val span = resources.displayMetrics.heightPixels.coerceAtLeast(1) * VOLUME_DRAG_SPAN_SCREEN_FRACTION
        val fraction = ((volumeDragBaseline + (volumeDragAnchorRawY - rawY) / span)).coerceIn(0f, 1f)
        when (state.dragTarget) {
            VolumeDragTarget.RING -> {
                state.ringFraction = fraction
                actionExecutor.setVolumeFraction(VolumeControlHelper.Stream.RING, fraction)
            }
            VolumeDragTarget.NOTIFICATION -> {
                state.notificationFraction = fraction
                actionExecutor.setVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION, fraction)
            }
            VolumeDragTarget.MEDIA, null -> Unit
        }
    }

    private fun refreshVolumePanelLevels(state: AdjustPanelState) {
        state.fraction = actionExecutor.readCurrentAdjustFraction(ContinuousAdjustController.Mode.VOLUME)
        state.ringFraction = actionExecutor.readVolumeFraction(VolumeControlHelper.Stream.RING)
        state.notificationFraction =
            actionExecutor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION)
        state.ringerMode = actionExecutor.readRingerMode()
        state.interruptionFilter = actionExecutor.readInterruptionFilter()
    }

    private fun dismissAdjustPanel(animated: Boolean = true) {
        if (adjustPanelState == null || adjustPanelDismissing) return
        stopAdjustPanelLevelSync()
        adjustIndicatorHoldVisual = captureAdjustIndicatorVisual()
        freezeAdjustIndicatorLayout(
            adjustIndicatorHoldVisual?.anchorRawY,
            adjustIndicatorHoldVisual?.mode,
        )
        if (!animated || adjustIndicatorProgress <= 0f) {
            finishDismissAdjustPanel()
            return
        }
        adjustPanelDismissing = true
        animateAdjustIndicatorTo(
            target = 0f,
            durationMs = ADJUST_INDICATOR_EXIT_MS,
            interpolator = AccelerateInterpolator(),
        ) {
            finishDismissAdjustPanel()
        }
    }

    private fun finishDismissAdjustPanel() {
        adjustPanelDismissing = false
        adjustPanelState = null
        adjustPanelExpandedForGesture = false
        adjustPanelEntering = false
        adjustIndicatorLayout = null
        adjustIndicatorFrozenLayout = null
        adjustIndicatorHoldVisual = null
        adjustIndicatorReceding = false
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorProgress = 0f
        wasAdjustMode = false
        onAdjustPanelDismissCallback()
        invalidate()
    }

    private fun notifyOverlayLayoutIfNeeded() {
        if (!keepsOverlayExpanded() && !gestureSession.isActive()) {
            onSessionEndCallback()
        }
    }

    private fun freezeAdjustIndicatorLayout(anchorRawY: Float?, mode: ContinuousAdjustController.Mode? = null) {
        if (adjustIndicatorFrozenLayout != null) return
        anchorRawY?.let { updateAdjustIndicatorLayout(it, mode = mode) }
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
    }

    private fun clearAdjustIndicatorExitState() {
        adjustIndicatorHoldVisual = null
        adjustIndicatorLayout = null
        adjustIndicatorFrozenLayout = null
        adjustIndicatorReceding = false
    }

    fun showAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        @Suppress("UNUSED_PARAMETER") deferWindowLayout: Boolean = false,
    ) {
        if (mode == ContinuousAdjustController.Mode.BRIGHTNESS) {
            actionExecutor.clearBrightnessPreview()
        }
        adjustPanelState = if (mode == ContinuousAdjustController.Mode.VOLUME) {
            AdjustPanelState(
                mode = mode,
                fraction = fraction,
                anchorRawY = anchorRawY,
                ringFraction = actionExecutor.readVolumeFraction(VolumeControlHelper.Stream.RING),
                notificationFraction = actionExecutor.readVolumeFraction(
                    VolumeControlHelper.Stream.NOTIFICATION,
                ),
                ringerMode = actionExecutor.readRingerMode(),
                interruptionFilter = actionExecutor.readInterruptionFilter(),
            )
        } else {
            AdjustPanelState(
                mode = mode,
                fraction = fraction,
                anchorRawY = anchorRawY,
                autoBrightnessEnabled = actionExecutor.readAutoBrightnessEnabled(),
                darkModeEnabled = actionExecutor.readDarkModeEnabled(),
            )
        }
        adjustPanelDismissing = false
        adjustIndicatorAnimator?.cancel()
        val fromContinuousGesture = gestureSession.isAdjustMode()
        adjustPanelExpandedForGesture = true
        onSessionStartCallback()
        notifyPresentationTouchRequirementChanged()
        if (fromContinuousGesture && adjustIndicatorProgress >= 1f) {
            // Preview enter animation already finished during continuous tracking.
            wasAdjustMode = true
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, forceFullScreenAnchor = true, mode = mode)
            invalidate()
        } else {
            post {
                if (fromContinuousGesture && adjustIndicatorProgress > 0f) {
                    continueAdjustPanelEnterAnimation(anchorRawY)
                } else {
                    beginAdjustPanelEnterAnimation(anchorRawY)
                }
            }
        }
        startAdjustPanelLevelSync(mode)
        notifyPresentationTouchRequirementChanged()
    }

    private fun continueAdjustPanelEnterAnimation(anchorRawY: Float) {
        adjustPanelEntering = true
        wasAdjustMode = true
        adjustIndicatorReceding = false
        updateAdjustIndicatorLayout(
            anchorRawY,
            forceFullScreenAnchor = true,
            mode = adjustPanelState?.mode,
        )
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
        val remaining = (1f - adjustIndicatorProgress).coerceIn(0f, 1f)
        val durationMs = (ADJUST_INDICATOR_ENTER_MS * remaining).toLong().coerceAtLeast(1L)
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = durationMs,
            interpolator = DecelerateInterpolator(),
        ) {
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, mode = adjustPanelState?.mode)
            invalidate()
        }
    }

    private fun beginAdjustPanelEnterAnimation(anchorRawY: Float) {
        adjustPanelEntering = true
        wasAdjustMode = true
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorReceding = false
        adjustIndicatorProgress = 0f
        adjustIndicatorFrozenLayout = null
        updateAdjustIndicatorLayout(
            anchorRawY,
            forceFullScreenAnchor = true,
            mode = adjustPanelState?.mode,
        )
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = ADJUST_INDICATOR_ENTER_MS,
            interpolator = DecelerateInterpolator(),
        ) {
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, mode = adjustPanelState?.mode)
            invalidate()
        }
    }

    private fun startAdjustPanelLevelSync(mode: ContinuousAdjustController.Mode) {
        when (mode) {
            ContinuousAdjustController.Mode.VOLUME -> startVolumeLevelSync()
            ContinuousAdjustController.Mode.BRIGHTNESS -> startBrightnessSettingsSync()
        }
    }

    private fun stopAdjustPanelLevelSync() {
        stopVolumeLevelSync()
        stopBrightnessSettingsSync()
    }

    private fun startVolumeLevelSync() {
        if (volumeChangeReceiver != null) return
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    VOLUME_CHANGED_ACTION -> {
                        val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                        if (
                            streamType == AudioManager.STREAM_MUSIC ||
                            streamType == AudioManager.STREAM_RING ||
                            streamType == AudioManager.STREAM_NOTIFICATION
                        ) {
                            syncAdjustPanelVolumeFromSystem()
                        }
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> syncAdjustPanelVolumeFromSystem()
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ->
                        syncAdjustPanelVolumeFromSystem()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(volumeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(volumeChangeReceiver, filter)
        }
    }

    private fun stopVolumeLevelSync() {
        volumeChangeReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
        }
        volumeChangeReceiver = null
    }

    private fun startBrightnessSettingsSync() {
        if (brightnessSettingsObserver != null) return
        val observer = object : ContentObserver(brightnessSettingsHandler) {
            override fun onChange(selfChange: Boolean) {
                syncAdjustPanelLevelFromSystem(ContinuousAdjustController.Mode.BRIGHTNESS)
            }
        }
        brightnessSettingsObserver = observer
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(BrightnessControlHelper.UI_NIGHT_MODE_KEY),
            false,
            observer,
        )
    }

    private fun stopBrightnessSettingsSync() {
        brightnessSettingsObserver?.let { observer ->
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
        brightnessSettingsObserver = null
    }

    private fun syncAdjustPanelVolumeFromSystem() {
        syncAdjustPanelLevelFromSystem(ContinuousAdjustController.Mode.VOLUME)
    }

    private fun syncAdjustPanelLevelFromSystem(mode: ContinuousAdjustController.Mode) {
        val state = adjustPanelState ?: return
        if (state.mode != mode) return
        if (mode == ContinuousAdjustController.Mode.VOLUME) {
            if (state.dragTarget != VolumeDragTarget.MEDIA) {
                val mediaFraction = actionExecutor.readCurrentAdjustFraction(mode)
                if (kotlin.math.abs(state.fraction - mediaFraction) >= LEVEL_SYNC_EPSILON) {
                    state.fraction = mediaFraction
                }
            }
            if (state.dragTarget != VolumeDragTarget.RING) {
                val ringFraction = actionExecutor.readVolumeFraction(VolumeControlHelper.Stream.RING)
                if (kotlin.math.abs(state.ringFraction - ringFraction) >= LEVEL_SYNC_EPSILON) {
                    state.ringFraction = ringFraction
                }
            }
            if (state.dragTarget != VolumeDragTarget.NOTIFICATION) {
                val notificationFraction =
                    actionExecutor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION)
                if (kotlin.math.abs(state.notificationFraction - notificationFraction) >= LEVEL_SYNC_EPSILON) {
                    state.notificationFraction = notificationFraction
                }
            }
            state.ringerMode = actionExecutor.readRingerMode()
            val interruptionFilter = actionExecutor.readInterruptionFilter()
            if (state.interruptionFilter != interruptionFilter) {
                state.interruptionFilter = interruptionFilter
            }
            invalidate()
            return
        }
        if (state.dragTarget != null) return
        val fraction = actionExecutor.readCurrentAdjustFraction(mode)
        if (kotlin.math.abs(state.fraction - fraction) < LEVEL_SYNC_EPSILON) {
            syncAdjustPanelBrightnessFlags(state)
            return
        }
        state.fraction = fraction
        syncAdjustPanelBrightnessFlags(state)
        invalidate()
    }

    private fun syncAdjustPanelBrightnessFlags(state: AdjustPanelState) {
        val autoBrightnessEnabled = actionExecutor.readAutoBrightnessEnabled()
        val darkModeEnabled = actionExecutor.readDarkModeEnabled()
        var changed = false
        if (state.autoBrightnessEnabled != autoBrightnessEnabled) {
            state.autoBrightnessEnabled = autoBrightnessEnabled
            changed = true
        }
        if (state.darkModeEnabled != darkModeEnabled) {
            state.darkModeEnabled = darkModeEnabled
            changed = true
        }
        if (changed) invalidate()
    }

    private fun updateAdjustIndicatorLayout(
        anchorRawY: Float,
        forceFullScreenAnchor: Boolean = false,
        mode: ContinuousAdjustController.Mode? = null,
    ): AdjustLevelIndicator.Layout? {
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val (_, anchorLocalY) = rawToLocal(0f, anchorRawY)
        val adjustMode = mode
            ?: adjustPanelState?.mode
            ?: gestureSession.adjustModeOrNull()
        adjustIndicatorLayout = AdjustLevelIndicator.layout(
            viewWidth = if (forceFullScreenAnchor) screenWidthPx else width.coerceAtLeast(1),
            viewHeight = height.coerceAtLeast(resources.displayMetrics.heightPixels),
            side = side,
            anchorY = anchorLocalY,
            density = density,
            viewScreenX = if (forceFullScreenAnchor) 0 else loc[0],
            screenWidthPx = screenWidthPx,
            chrome = when (adjustMode) {
                ContinuousAdjustController.Mode.VOLUME -> AdjustPanelChrome.VOLUME
                ContinuousAdjustController.Mode.BRIGHTNESS -> AdjustPanelChrome.BRIGHTNESS
                null -> AdjustPanelChrome.NONE
            },
            volumeExpanded = adjustPanelState?.volumeExpanded == true &&
                adjustMode == ContinuousAdjustController.Mode.VOLUME,
        )
        return adjustIndicatorLayout
    }

    private fun handleIndexTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val touchX = panelEnterAdjustedX(localX, indexPanelContentRect())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInsideIndexInteractiveArea(touchX, localY)) {
                    gestureSession.endSession()
                    return false
                }
                indexSession.updateSelection(touchX, localY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureSession.isMoveTimeActionLocked()) {
                    indexSession.updateSelection(touchX, localY)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                return true
            }
        }
        return false
    }

    private fun isInsideIndexInteractiveArea(localX: Float, localY: Float): Boolean {
        if (zoneLayout.isInRailZone(localX)) return true
        if (indexSession.selectedLetter != null) {
            indexSession.gridCellBounds.forEach { (_, rect) ->
                if (rect.contains(localX, localY)) return true
            }
            val grid = gridPopupRect()
            if (grid.contains(localX, localY)) return true
        }
        return false
    }

    private fun handleQuickLauncherManagementTouch(
        event: MotionEvent,
        x: Float,
        y: Float,
        panelRect: RectF,
        tapGesture: Boolean,
        toolbarCommitAllowed: Boolean,
    ): Boolean = quickLauncherPanelController.handleManagementTouch(
        event = event,
        localX = x,
        localY = y,
        panelRect = panelRect,
        cellBounds = panelGridSession.cellBounds,
        tapGesture = tapGesture,
        toolbarCommitAllowed = toolbarCommitAllowed,
    )

    private fun isQuickLauncherTapGesture(touchX: Float, localY: Float): Boolean {
        val dx = touchX - quickLauncherPageSwipeStartX
        val dy = localY - quickLauncherPageSwipeStartY
        val slop = dp(24f)
        return dx * dx + dy * dy <= slop * slop
    }

    private fun quickLauncherToolbarCommitAllowed(): Boolean {
        if (quickLauncherPageSwipeLocked) return false
        if (quickLauncherPageSnapAnimator?.isRunning == true) return false
        if (kotlin.math.abs(quickLauncherPageDragOffset) > dp(6f)) return false
        return true
    }

    private fun beginQuickLauncherGesture(toolbarDown: Boolean) {
        quickLauncherPageChangedThisGesture = false
        quickLauncherPageSwipeLocked = false
        if (toolbarDown) {
            cancelQuickLauncherPageSnapAnimation()
            quickLauncherPageDragOffset = 0f
        }
    }

    /** Finish an in-progress page drag before toolbar / panel UP handling. */
    private fun consumeQuickLauncherPageRelease(): Boolean {
        if (quickLauncherPanelController.editMode || quickLauncherPageCount <= 1) return false
        if (quickLauncherPageSwipeLocked ||
            kotlin.math.abs(quickLauncherPageDragOffset) > dp(6f)
        ) {
            finishQuickLauncherPageDrag()
            quickLauncherPageSwipeLocked = false
            quickLauncherPageSwipeTracking = false
            invalidate()
            return true
        }
        if (quickLauncherPageSnapAnimator?.isRunning == true) {
            invalidate()
            return true
        }
        return false
    }

    private fun tryCommitQuickLauncherToolbarOnContinuousPickUp(
        touchX: Float,
        localY: Float,
        panelRect: RectF,
    ): Boolean {
        if (!quickLauncherToolbarCommitAllowed()) return false
        return quickLauncherPanelController.commitToolbarAtRelease(
            localX = touchX,
            localY = localY,
            panelRect = panelRect,
            tapGesture = false,
            toolbarCommitAllowed = true,
            allowSlideRelease = true,
        )
    }

    private fun handleQuickLauncherTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (quickLauncherExiting) return true
        val panelRect = quickLauncherPanelRect()
        val contentRect = quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = panelEnterAdjustedX(localX, contentRect)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            quickLauncherPageSwipeStartX = touchX
            quickLauncherPageSwipeStartY = localY
            val toolbarDown = quickLauncherPanelController.toolbarContains(touchX, localY)
            quickLauncherToolbarTouchActive = toolbarDown
            beginQuickLauncherGesture(toolbarDown)
        }
        val toolbarTouchThisGesture = when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val armed = quickLauncherToolbarTouchActive
                quickLauncherToolbarTouchActive = false
                armed
            }
            else -> quickLauncherToolbarTouchActive
        }
        val continuousPick = gestureSession.quickLauncherContinuousPickActive()
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            if (!continuousPick && consumeQuickLauncherPageRelease()) {
                return true
            }
        }
        val tapGesture = (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL) &&
            isQuickLauncherTapGesture(touchX, localY)
        val toolbarCommitAllowed = quickLauncherToolbarCommitAllowed()
        if (!continuousPick && handleQuickLauncherManagementTouch(
                event,
                touchX,
                localY,
                panelRect,
                tapGesture = tapGesture,
                toolbarCommitAllowed = toolbarCommitAllowed,
            )
        ) {
            return true
        }
        if (quickLauncherPanelController.editMode && !continuousPick) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                invalidate()
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelQuickLauncherPageSnapAnimation()
                quickLauncherPageChangedThisGesture = false
                quickLauncherPageDragOffset = 0f
                quickLauncherPageSwipeLocked = false
                quickLauncherEdgePageZone = 0
                quickLauncherEdgeAutoPageSeeded = false
                if (!gestureSession.isMoveTimeActionLocked()) {
                    quickLauncherOpeningGestureActive = false
                }
                quickLauncherPageSwipeTracking = quickLauncherPageCount > 1 &&
                    panelContentRect.contains(touchX, localY) &&
                    !quickLauncherPanelController.editMode
                if (continuousPick) {
                    if (quickLauncherContinuousPickReady() &&
                        isQuickLauncherSelectableTouch(localX, localY, panelRect)
                    ) {
                        updateQuickLauncherHighlight(
                            localX,
                            localY,
                            touchX,
                            event.eventTime,
                            haptic = true,
                        )
                    }
                    invalidate()
                    return true
                }
                if (!isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                    endQuickLauncherSessionAnimated()
                    invalidate()
                    return true
                }
                panelGridSession.updateHighlight(touchX, localY)
                syncQuickLauncherPressTracking(event.eventTime)
                if (panelGridSession.highlightedIndex >= 0) {
                    HapticHelper.appTick(this, settings)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (continuousPick && quickLauncherContinuousPickReady()) {
                    quickLauncherOpeningGestureActive = false
                }
                if (gestureSession.isMoveTimeActionLocked()) {
                    quickLauncherPageSwipeStartX = touchX
                    quickLauncherPageSwipeStartY = localY
                    quickLauncherPageSwipeLocked = false
                }
                if (consumeQuickLauncherPageSwipeMove(touchX, localY)) {
                    invalidate()
                    return true
                }
                if (gestureSession.isMoveTimeActionLocked() && !continuousPick) {
                    if (updateQuickLauncherEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (quickLauncherOpeningGestureActive &&
                    gestureSession.isMoveTimeActionLocked() &&
                    !continuousPick
                ) {
                    invalidate()
                    return true
                }
                if (updateQuickLauncherEdgeTracking(event.rawY, localX, localY)) return true
                if (continuousPick) {
                    if (!quickLauncherContinuousPickReady()) {
                        invalidate()
                        return true
                    }
                    if (quickLauncherPageInteractionActive()) {
                        invalidate()
                        return true
                    }
                    if (!isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                        clearQuickLauncherHighlight()
                        invalidate()
                        return true
                    }
                    applyQuickLauncherEdgeAutoPage(touchX)
                    updateQuickLauncherHighlight(localX, localY, touchX, event.eventTime, haptic = true)
                    invalidate()
                    return true
                }
                val prev = panelGridSession.highlightedIndex
                if (isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                    panelGridSession.updateHighlight(touchX, localY)
                    if (panelGridSession.highlightedIndex != prev) {
                        syncQuickLauncherPressTracking(event.eventTime)
                    }
                    if (panelGridSession.highlightedIndex != prev && panelGridSession.highlightedIndex >= 0) {
                        HapticHelper.appTick(this, settings)
                    }
                } else {
                    clearQuickLauncherHighlight()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureSession.releaseImmediateGestureLock()) {
                    quickLauncherOpeningGestureActive = false
                    invalidate()
                    return true
                }
                quickLauncherOpeningGestureActive = false
                if (continuousPick) {
                    if (quickLauncherPageSwipeLocked) {
                        finishQuickLauncherPageDrag()
                        quickLauncherPageSwipeLocked = false
                        quickLauncherPageSwipeTracking = false
                        quickLauncherPageChangedThisGesture = false
                        invalidate()
                        return true
                    }
                    if (quickLauncherPageSnapAnimator?.isRunning == true) {
                        invalidate()
                        return true
                    }
                    if (tryCommitQuickLauncherToolbarOnContinuousPickUp(touchX, localY, panelRect)) {
                        gestureSession.clearQuickLauncherContinuousPick()
                        quickLauncherPageSwipeLocked = false
                        quickLauncherPageChangedThisGesture = false
                        invalidate()
                        return true
                    }
                    if (quickLauncherContinuousPickReady() &&
                        isQuickLauncherSelectableTouch(localX, localY, panelRect)
                    ) {
                        updateQuickLauncherHighlight(localX, localY, touchX, event.eventTime, haptic = false)
                        val panelModeBeforeAction = gestureSession.panelMode()
                        if (panelGridSession.highlightedIndex >= 0 &&
                            performQuickLauncherUpAction(event, touchX, localX, localY)
                        ) {
                            endQuickLauncherAfterLaunch(quickLauncherLaunchEndDeferMs)
                            quickLauncherLaunchEndDeferMs = 0L
                        } else if (!quickLauncherPanelController.editMode &&
                            !quickLauncherOverlayDialogHost.isShowing &&
                            panelGridSession.highlightedIndex < 0 &&
                            !toolbarTouchThisGesture &&
                            gestureSession.panelMode() == panelModeBeforeAction
                        ) {
                            endQuickLauncherSessionAnimated()
                        }
                    } else if (!quickLauncherPanelController.editMode &&
                        !quickLauncherOverlayDialogHost.isShowing &&
                        !toolbarTouchThisGesture &&
                        quickLauncherPageSnapAnimator?.isRunning != true
                    ) {
                        endQuickLauncherSessionAnimated()
                    }
                    quickLauncherPageSwipeLocked = false
                    quickLauncherPageChangedThisGesture = false
                    invalidate()
                    return true
                }
                if (quickLauncherPageSwipeLocked) {
                    finishQuickLauncherPageDrag()
                    quickLauncherPageSwipeLocked = false
                    quickLauncherPageSwipeTracking = false
                    quickLauncherPageChangedThisGesture = false
                    invalidate()
                    return true
                }
                if (quickLauncherPageSnapAnimator?.isRunning == true) {
                    invalidate()
                    return true
                }
                if (isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                    panelGridSession.updateHighlight(touchX, localY)
                } else {
                    clearQuickLauncherHighlight()
                }
                val panelModeBeforeAction = gestureSession.panelMode()
                if (!quickLauncherPageChangedThisGesture &&
                    performQuickLauncherUpAction(event, touchX, localX, localY)
                ) {
                    endQuickLauncherAfterLaunch(quickLauncherLaunchEndDeferMs)
                    quickLauncherLaunchEndDeferMs = 0L
                } else if (!quickLauncherPanelController.editMode &&
                    !quickLauncherOverlayDialogHost.isShowing &&
                    panelGridSession.highlightedIndex < 0 &&
                    !quickLauncherPageChangedThisGesture &&
                    !toolbarTouchThisGesture &&
                    gestureSession.panelMode() == panelModeBeforeAction
                ) {
                    endQuickLauncherSessionAnimated()
                }
                quickLauncherPageSwipeTracking = false
                quickLauncherPageSwipeLocked = false
                quickLauncherPageChangedThisGesture = false
                return true
            }
        }
        return false
    }

    private fun syncQuickLauncherPressTracking(eventTime: Long) {
        val index = panelGridSession.highlightedIndex
        if (index >= 0) {
            if (index != quickLauncherPressIndex) {
                scheduleQuickLauncherLongPress(index)
            }
            quickLauncherPressIndex = index
            quickLauncherPressDownTime = eventTime
        } else {
            cancelQuickLauncherLongPress()
            quickLauncherPressIndex = -1
            quickLauncherPressDownTime = 0L
        }
    }

    private fun scheduleQuickLauncherLongPress(index: Int) {
        cancelQuickLauncherLongPress()
        if (!settings.freeWindowEnabled || !settings.resolvedLaunchPolicy().usesLongPress()) return
        quickLauncherLongPressIndex = index
        val runnable = Runnable {
            if (panelGridSession.highlightedIndex == quickLauncherLongPressIndex && quickLauncherLongPressIndex >= 0) {
                quickLauncherLongPressArmed = true
                HapticHelper.confirmLaunch(this, settings)
                invalidate()
            }
        }
        quickLauncherLongPressRunnable = runnable
        postDelayed(runnable, settings.effectiveLongPressDurationMs().toLong())
    }

    private fun cancelQuickLauncherLongPress() {
        quickLauncherLongPressRunnable?.let { removeCallbacks(it) }
        quickLauncherLongPressRunnable = null
        quickLauncherLongPressIndex = -1
        quickLauncherLongPressArmed = false
    }

    private fun performQuickLauncherUpAction(
        event: MotionEvent,
        touchX: Float,
        localX: Float,
        localY: Float,
    ): Boolean {
        if (quickLauncherPanelController.editMode) return false
        val panelRect = quickLauncherPanelRect()
        if (!isQuickLauncherSelectableTouch(localX, localY, panelRect)) return false
        val item = panelGridSession.highlightedQuickItem() ?: return false
        val longPress = quickLauncherLongPressTriggered(event)
        cancelQuickLauncherLongPress()
        return when (item.type) {
            QuickLauncherItemType.ACTION -> {
                val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return false
                gestureSession.performQuickLauncherAction(action, localX, localY, event.rawY)
            }
            else -> {
                HapticHelper.confirmLaunch(this, settings)
                quickLauncherLaunchEndDeferMs =
                    if (actionExecutor.launchQuickItem(item, settings, longPressArmed = longPress, anchorRawY = event.rawY)) 280L else 0L
                true
            }
        }
    }

    private fun endQuickLauncherAfterLaunch(deferMs: Long) {
        if (deferMs > 0L) {
            postDelayed({ gestureSession.endSession() }, deferMs)
        } else {
            post { gestureSession.endSession() }
        }
    }

    private fun quickLauncherLongPressTriggered(event: MotionEvent): Boolean {
        if (quickLauncherLongPressArmed) return true
        if (!settings.freeWindowEnabled || !settings.resolvedLaunchPolicy().usesLongPress()) {
            return false
        }
        if (quickLauncherPressIndex < 0 ||
            quickLauncherPressIndex != panelGridSession.highlightedIndex
        ) {
            return false
        }
        return event.eventTime - quickLauncherPressDownTime >= settings.effectiveLongPressDurationMs()
    }

    private fun quickLauncherContinuousPickReady(): Boolean = panelEnterProgress >= 1f

    private fun quickLauncherPageInteractionActive(): Boolean =
        quickLauncherPageSwipeLocked || quickLauncherPageSnapAnimator?.isRunning == true

    private fun consumeQuickLauncherPageSwipeMove(touchX: Float, localY: Float): Boolean {
        if (gestureSession.isMoveTimeActionLocked()) return false
        if (gestureSession.quickLauncherContinuousPickActive()) return false
        if (quickLauncherPanelController.editMode || quickLauncherPageCount <= 1) return false
        val deltaX = touchX - quickLauncherPageSwipeStartX
        val deltaY = localY - quickLauncherPageSwipeStartY
        val absX = kotlin.math.abs(deltaX)
        val absY = kotlin.math.abs(deltaY)
        val directionLock = dp(QUICK_LAUNCHER_PAGE_SWIPE_DIRECTION_LOCK_DP)
        if (!quickLauncherPageSwipeLocked) {
            if (absX > directionLock && absX > absY * 1.25f) {
                quickLauncherPageSwipeLocked = true
                quickLauncherPageSwipeTracking = true
                clearQuickLauncherHighlight()
            } else {
                return false
            }
        }
        updateQuickLauncherPageDragOffset(deltaX)
        return true
    }

    private fun updateQuickLauncherPageDragOffset(deltaX: Float) {
        cancelQuickLauncherPageSnapAnimation()
        val panelWidth = quickLauncherPanelWidthForPaging()
        var offset = deltaX
        if (quickLauncherPageIndex <= 0 && offset > 0f) {
            offset *= QUICK_LAUNCHER_PAGE_EDGE_RESISTANCE
        } else if (quickLauncherPageIndex >= quickLauncherPageCount - 1 && offset < 0f) {
            offset *= QUICK_LAUNCHER_PAGE_EDGE_RESISTANCE
        }
        quickLauncherPageDragOffset = offset.coerceIn(-panelWidth, panelWidth)
        invalidateQuickLauncherPanel()
    }

    private fun finishQuickLauncherPageDrag() {
        val panelWidth = quickLauncherPanelWidthForPaging()
        val threshold = panelWidth * QUICK_LAUNCHER_PAGE_COMMIT_FRACTION
        val offset = quickLauncherPageDragOffset
        val delta = when {
            offset <= -threshold && quickLauncherPageIndex < quickLauncherPageCount - 1 -> 1
            offset >= threshold && quickLauncherPageIndex > 0 -> -1
            else -> 0
        }
        if (delta != 0) {
            quickLauncherPageIndex += delta
            quickLauncherPageChangedThisGesture = true
            quickLauncherPageDragOffset += if (delta > 0) panelWidth else -panelWidth
            syncQuickLauncherPageOffsetForDrag()
        }
        animateQuickLauncherPageSnapTo(0f)
    }

    private fun animateQuickLauncherPageSnapTo(targetOffset: Float) {
        quickLauncherPageSnapAnimator?.cancel()
        val start = quickLauncherPageDragOffset
        if (kotlin.math.abs(start - targetOffset) < dp(0.5f)) {
            quickLauncherPageDragOffset = targetOffset
            invalidateQuickLauncherPanel()
            return
        }
        quickLauncherPageSnapAnimator = ValueAnimator.ofFloat(start, targetOffset).apply {
            duration = QUICK_LAUNCHER_PAGE_SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                quickLauncherPageDragOffset = animator.animatedValue as Float
                invalidateQuickLauncherPanel()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    quickLauncherPageDragOffset = targetOffset
                    invalidateQuickLauncherPanel()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    quickLauncherPageDragOffset = targetOffset
                }
            })
            start()
        }
    }

    private fun cancelQuickLauncherPageSnapAnimation() {
        quickLauncherPageSnapAnimator?.cancel()
        quickLauncherPageSnapAnimator = null
    }

    private fun clearQuickLauncherHighlight() {
        panelGridSession.clearHighlight()
        quickLauncherContinuousHapticIndex = -1
        cancelQuickLauncherLongPress()
    }

    private fun updateQuickLauncherHighlight(
        localX: Float,
        localY: Float,
        touchX: Float,
        eventTime: Long,
        haptic: Boolean,
    ) {
        if (quickLauncherPageInteractionActive()) return
        val panelRect = quickLauncherPanelRect()
        if (!isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
            clearQuickLauncherHighlight()
            return
        }
        val effectiveX = if (panelContentRect.contains(touchX, localY)) {
            touchX
        } else {
            quickLauncherEdgeProjectedX(localY)
        }
        val prev = panelGridSession.highlightedIndex
        panelGridSession.updateHighlight(effectiveX, localY)
        if (panelGridSession.highlightedIndex != prev) {
            syncQuickLauncherPressTracking(eventTime)
        }
        if (haptic && panelGridSession.highlightedIndex != prev && panelGridSession.highlightedIndex >= 0) {
            if (panelGridSession.highlightedIndex != quickLauncherContinuousHapticIndex) {
                HapticHelper.appTick(this, settings)
                quickLauncherContinuousHapticIndex = panelGridSession.highlightedIndex
            }
        }
    }

    private fun quickLauncherEdgeProjectedX(localY: Float): Float {
        val rowCells = panelGridSession.cellBounds.filter { (_, rect) ->
            localY >= rect.top && localY <= rect.bottom
        }
        if (rowCells.isEmpty()) return panelContentRect.centerX()
        return when (side) {
            PanelSide.LEFT -> rowCells.minByOrNull { it.second.left }?.second?.centerX()
                ?: panelContentRect.centerX()
            PanelSide.RIGHT -> rowCells.maxByOrNull { it.second.right }?.second?.centerX()
                ?: panelContentRect.centerX()
        }
    }

    /**
     * In continuous pick mode, advance pages when the finger crosses the panel's inner/outer edge.
     */
    private fun applyQuickLauncherEdgeAutoPage(touchX: Float): Boolean {
        if (!gestureSession.quickLauncherContinuousPickActive()) return false
        if (!quickLauncherContinuousPickReady()) return false
        if (quickLauncherPageCount <= 1) return false
        if (quickLauncherPanelController.editMode) return false
        if (quickLauncherPageInteractionActive()) return false
        if (quickLauncherPageSnapAnimator?.isRunning == true) return false
        val panelRect = quickLauncherPanelRect()
        if (panelRect.isEmpty) return false
        return applyQuickLauncherEdgeAutoPageInternal(touchX, panelRect)
    }

    private fun applyEditDragAutoPage(touchX: Float, panelRect: RectF): Boolean {
        if (!quickLauncherPanelController.isDragging()) return false
        if (!quickLauncherPanelController.editMode) return false
        if (quickLauncherPageCount <= 1) return false
        if (quickLauncherPageSnapAnimator?.isRunning == true) return false
        if (panelRect.isEmpty) return false
        return applyQuickLauncherEdgeAutoPageInternal(touchX, panelRect)
    }

    private fun applyQuickLauncherEdgeAutoPageInternal(touchX: Float, panelRect: RectF): Boolean {
        val zone = quickLauncherEdgePageZoneFor(touchX, panelRect)
        if (!quickLauncherEdgeAutoPageSeeded) {
            quickLauncherEdgeAutoPageSeeded = true
            quickLauncherEdgePageZone = zone
            return false
        }
        val prevZone = quickLauncherEdgePageZone
        quickLauncherEdgePageZone = zone
        if (zone == 0 || zone == prevZone) return false

        val delta = when (zone) {
            -1 -> if (quickLauncherPageIndex > 0) -1 else 0
            1 -> if (quickLauncherPageIndex < quickLauncherPageCount - 1) 1 else 0
            else -> 0
        }
        if (delta == 0) return false

        animateQuickLauncherPageTurn(delta)
        return true
    }

    private fun animateQuickLauncherPageTurn(delta: Int) {
        if (delta == 0) return
        if (quickLauncherPageSnapAnimator?.isRunning == true) return
        cancelQuickLauncherPageSnapAnimation()
        val panelWidth = quickLauncherPanelWidthForPaging()
        quickLauncherPageIndex = (quickLauncherPageIndex + delta)
            .coerceIn(0, quickLauncherPageCount - 1)
        syncQuickLauncherPageOffsetForDrag()
        quickLauncherPageChangedThisGesture = true
        quickLauncherPageDragOffset += if (delta > 0) panelWidth else -panelWidth
        clearQuickLauncherHighlight()
        HapticHelper.appTick(this, settings)
        animateQuickLauncherPageSnapTo(0f)
    }

    private fun syncQuickLauncherPageOffsetForDrag() {
        val pageStart = quickLauncherPageIndex * quickLauncherPageSize()
        quickLauncherPanelController.setItemPageOffset(pageStart)
        if (quickLauncherPanelController.isDragging()) {
            quickLauncherPanelController.syncPageLocalDragTarget()
        }
    }

    private fun quickLauncherEdgePageZoneFor(touchX: Float, panelRect: RectF): Int {
        val edge = dp(QUICK_LAUNCHER_EDGE_AUTO_PAGE_THRESHOLD_DP)
        val innerThreshold = when (side) {
            PanelSide.LEFT -> panelRect.right - edge
            PanelSide.RIGHT -> panelRect.left + edge
        }
        val outerThreshold = when (side) {
            PanelSide.LEFT -> panelRect.left + edge
            PanelSide.RIGHT -> panelRect.right - edge
        }
        return when (side) {
            PanelSide.LEFT -> when {
                touchX <= outerThreshold -> -1
                touchX >= innerThreshold -> 1
                else -> 0
            }
            PanelSide.RIGHT -> when {
                touchX >= outerThreshold -> -1
                touchX <= innerThreshold -> 1
                else -> 0
            }
        }
    }

    /** Touch may select or launch a grid item (panel, toolbar, or trigger-to-panel approach lane). */
    private fun isQuickLauncherSelectableTouch(
        localX: Float,
        localY: Float,
        panelRect: RectF,
    ): Boolean {
        if (panelRect.isEmpty) return false
        val contentRect = quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = panelEnterAdjustedX(localX, contentRect)
        if (quickLauncherPanelController.toolbarContains(touchX, localY)) return true
        if (localY < contentRect.top || localY > contentRect.bottom) return false
        if (panelRect.contains(touchX, localY)) return true
        return isInQuickLauncherApproachZone(localX, panelRect)
    }

    private fun activeTriggerZoneRect(): RectF =
        if (gestureSession.isActive()) {
            zoneLayout.triggerZoneRect(gestureSession.activeHandleId())
        } else {
            zoneLayout.triggerZoneUnionRect()
        }

    private fun isInQuickLauncherApproachZone(localX: Float, panelRect: RectF): Boolean {
        val trigger = activeTriggerZoneRect()
        return when (side) {
            PanelSide.LEFT -> localX >= trigger.right && localX <= panelRect.left
            PanelSide.RIGHT -> localX <= trigger.left && localX >= panelRect.right
        }
    }

    private fun shouldFreezeQuickLauncherAnchor(): Boolean =
        gestureSession.panelMode() == OverlayPanelMode.QUICK_LAUNCHER

    private fun updateQuickLauncherEdgeTracking(rawY: Float, localX: Float, localY: Float): Boolean {
        if (!zoneLayout.containsTrigger(localX, localY)) return false
        val continuousPick = gestureSession.quickLauncherContinuousPickActive()
        if (!shouldFreezeQuickLauncherAnchor()) {
            quickLauncherAnchorRawY = rawY
            quickLauncherFrozenAnchorLocalY = null
        }
        if (continuousPick && quickLauncherContinuousPickReady()) {
            val panelRect = quickLauncherPanelRect()
            if (isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                val touchX = panelEnterAdjustedX(localX, panelRect)
                if (!quickLauncherPageInteractionActive()) {
                    applyQuickLauncherEdgeAutoPage(touchX)
                    updateQuickLauncherHighlight(localX, localY, touchX, android.os.SystemClock.uptimeMillis(), haptic = true)
                } else {
                    clearQuickLauncherHighlight()
                }
            } else {
                clearQuickLauncherHighlight()
            }
        } else if (!continuousPick) {
            clearQuickLauncherHighlight()
        }
        invalidate()
        return true
    }

    private fun resolveQuickLauncherAnchorLocalY(): Float {
        val rawY = quickLauncherAnchorRawY ?: pathRecognizer.gestureStartRawY()
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val anchorY = rawY - loc[1]
        val trigger = activeTriggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun quickLauncherAnchorLocalY(): Float =
        quickLauncherFrozenAnchorLocalY ?: resolveQuickLauncherAnchorLocalY()

    private fun syncShellPanelInputFocus() {
        val shellPanelActive = gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS &&
            panelEnterProgress > 0.01f
        val needsFocus = shellOverlayDialogHost.isShowing ||
            (shellPanelActive && shellPanelController.hasActiveUi()) ||
            (shellPanelActive && panelEnterProgress >= 1f)
        onShellPanelFocusChange(needsFocus)
        if (needsFocus) requestFocus()
    }

    private fun handleShellPanelBackPress(): Boolean {
        if (shellOverlayDialogHost.isShowing) return false
        if (gestureSession.panelMode() != OverlayPanelMode.SHELL_COMMANDS) return false
        if (panelEnterProgress <= 0.01f && !shellPanelController.hasActiveUi()) return false
        val handled = shellPanelController.handleBackPress()
        if (handled) syncShellPanelInputFocus()
        return handled
    }

    private fun endShellPanelSessionAnimated() {
        if (gestureSession.panelMode() != OverlayPanelMode.SHELL_COMMANDS) {
            gestureSession.endSession()
            return
        }
        if (shellPanelExiting) return
        shellPanelController.prepareForPanelExit()
        if (panelEnterProgress > 0.01f) {
            shellPanelExiting = true
            startPanelExitAnimation {
                shellPanelExiting = false
                gestureSession.endSession()
            }
        } else {
            gestureSession.endSession()
        }
    }

    private fun handleShellCommandsTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val continuousPick = gestureSession.shellCommandContinuousPickActive()
        if (continuousPick && event.actionMasked == MotionEvent.ACTION_DOWN) {
            return true
        }
        val consumed = shellPanelController.handleTouch(
            event = event,
            localX = localX,
            localY = localY,
            releaseImmediateLock = { gestureSession.releaseImmediateGestureLock() },
        )
        if (continuousPick && event.actionMasked == MotionEvent.ACTION_UP &&
            !shellPanelController.hasActiveUi()
        ) {
            if (panelEnterProgress > 0.01f) {
                startPanelExitAnimation { gestureSession.endSession() }
            } else {
                gestureSession.endSession()
            }
        }
        return consumed
    }

    private data class TaskSwitcherPick(
        val row: Int = -1,
        val close: Int = -1,
        val freeWindow: Int = -1,
        val closeAll: Boolean = false,
    )

    private fun handleTaskSwitcherTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        lastTaskSwitcherTouchX = localX
        lastTaskSwitcherTouchY = localY
        if (taskSwitcherExiting) return true
        val continuousPick = gestureSession.taskSwitcherContinuousPickActive()
        if (taskSwitcherContextMenuActive() && !continuousPick) {
            if (handleTaskSwitcherContextMenuTouch(event, localX, localY)) {
                return true
            }
        }
        val layout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        val touchX = panelEnterAdjustedX(localX, layout.panelRect)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearTaskSwitcherPickHighlights()
                taskSwitcherClosePressIndex = -1
                taskSwitcherRowPressIndex = -1
                taskSwitcherGestureScrolled = false
                if (!layout.panelRect.contains(touchX, localY)) {
                    endTaskSwitcherSession()
                    return true
                }
                beginTaskSwitcherScrollDrag(localY)
                if (continuousPick && taskSwitcherContinuousPickReady()) {
                    val pick = resolveTaskSwitcherPick(layout, localX, localY)
                    updateContinuousTaskSwitcherPick(layout, pick, event.eventTime, haptic = true)
                } else if (!continuousPick) {
                    val pick = resolveTaskSwitcherPick(layout, localX, localY)
                    applyTaskSwitcherPick(pick, haptic = false)
                    invalidate()
                    if (pick.close >= 0) {
                        taskSwitcherClosePressIndex = pick.close
                        taskSwitcherClosePressDownTime = event.eventTime
                        taskSwitcherCloseLongPressTriggered = false
                        layout.rows.getOrNull(pick.close)?.entry?.app?.packageName?.let { packageName ->
                            scheduleTaskSwitcherCloseLongPress(pick.close, packageName)
                        }
                    }
                    if (pick.row >= 0) {
                        taskSwitcherRowPressIndex = pick.row
                        taskSwitcherRowPressDownTime = event.eventTime
                        taskSwitcherRowLongPressTriggered = false
                        scheduleTaskSwitcherRowLongPress(pick.row)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (continuousPick && handleContinuousTaskSwitcherMenuMove(localX, touchX, localY)) {
                    return true
                }
                if (gestureSession.isMoveTimeActionLocked()) {
                    if (updateTaskSwitcherEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (updateTaskSwitcherEdgeTracking(event.rawY, localX, localY)) return true
                if (continuousPick) {
                    if (!taskSwitcherContinuousPickReady()) {
                        invalidate()
                        return true
                    }
                    if (!isTaskSwitcherInteractiveTouch(localX, localY, layout)) {
                        clearTaskSwitcherContinuousLongPressTracking()
                        clearTaskSwitcherPickHighlights()
                        invalidate()
                        return true
                    }
                    applyTaskSwitcherEdgeAutoScroll(layout, localY)
                    val current = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
                    val pick = resolveTaskSwitcherPick(current, localX, localY)
                    val menu = taskSwitcherContextMenu.takeIf { taskSwitcherContextMenuActive() }
                    if (menu != null &&
                        !shouldDismissTaskSwitcherMenuForContinuousSlide(current, localX, localY, menu)
                    ) {
                        invalidate()
                        return true
                    }
                    updateContinuousTaskSwitcherPick(current, pick, event.eventTime, haptic = true)
                    invalidate()
                    return true
                }
                if (taskSwitcherClosePressIndex >= 0) {
                    val current = taskSwitcherLayout ?: layout
                    if (isTaskSwitcherDownPickHeld(localX, localY, current)) {
                        return true
                    }
                    cancelTaskSwitcherCloseLongPress()
                    taskSwitcherClosePressIndex = -1
                    taskSwitcherClosePressDownTime = 0L
                    taskSwitcherCloseLongPressTriggered = false
                }
                if (handleTaskSwitcherScrollMove(touchX, localY)) return true
                if (taskSwitcherScrollDragging) return true
                val current = taskSwitcherLayout ?: layout
                if (!isTaskSwitcherDownPickHeld(localX, localY, current)) {
                    cancelTaskSwitcherRowLongPress()
                    cancelTaskSwitcherCloseLongPress()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (continuousPick && handleContinuousTaskSwitcherMenuUp(touchX, localY)) {
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                if (gestureSession.releaseImmediateGestureLock()) {
                    if (!continuousPick && isTaskSwitcherDownPickHeld(localX, localY, layout)) {
                        performTaskSwitcherUpAction(layout, event)
                    }
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                finishTaskSwitcherScrollDrag()
                if (!continuousPick && taskSwitcherGestureScrolled) {
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                if (continuousPick) {
                    val current = taskSwitcherLayout ?: layout
                    if (taskSwitcherContinuousPickReady() &&
                        isTaskSwitcherInteractiveTouch(localX, localY, layout)
                    ) {
                        val pick = resolveTaskSwitcherPick(current, localX, localY)
                        updateContinuousTaskSwitcherPick(current, pick, event.eventTime, haptic = false)
                    } else if (!isTaskSwitcherInteractiveTouch(localX, localY, layout)) {
                        cancelContinuousTaskSwitcherOnLeavePanel()
                        return true
                    } else {
                        resetTaskSwitcherTouchHighlights()
                        return true
                    }
                } else if (!isTaskSwitcherDownPickHeld(localX, localY, layout)) {
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                performTaskSwitcherUpAction(layout, event)
                resetTaskSwitcherTouchHighlights()
                return true
            }
        }
        return false
    }

    private fun performTaskSwitcherUpAction(layout: TaskSwitcherPanelLayout, event: MotionEvent) {
        when {
            taskSwitcherCloseHighlight >= 0 -> {
                val closeIndex = taskSwitcherCloseHighlight
                val row = layout.rows.getOrNull(closeIndex)
                val packageName = row?.entry?.app?.packageName
                val isLocked = row?.entry?.isLocked == true
                val continuousPick = gestureSession.taskSwitcherContinuousPickActive()
                val longPress = if (continuousPick) {
                    taskSwitcherCloseLongPressTriggered
                } else {
                    taskSwitcherCloseLongPressTriggered ||
                        (taskSwitcherClosePressIndex == closeIndex &&
                            event.eventTime - taskSwitcherClosePressDownTime >= taskSwitcherCloseDwellMs())
                }
                cancelTaskSwitcherCloseLongPress()
                if (packageName != null) {
                    when {
                        taskSwitcherCloseLongPressTriggered -> Unit
                        longPress -> {
                            taskSwitcherCloseLongPressTriggered = true
                            toggleTaskSwitcherLock(closeIndex, packageName, !isLocked)
                        }
                        !isLocked -> {
                            HapticHelper.confirmLaunch(this, settings)
                            val entry = row.entry
                            if (entry.taskId > 0) {
                                recentApps.removeAll { it.taskId == entry.taskId }
                                RecentTasksLoader.removeTaskIds(listOf(entry.taskId))
                            } else {
                                recentApps.removeAll { it.app.packageName == packageName }
                                RecentTasksLoader.removePackages(listOf(packageName))
                            }
                            TaskSwitcherLockStore.setLocked(context, packageName, locked = false)
                            taskSwitcherLayout = null
                            if (recentApps.isEmpty()) {
                                endTaskSwitcherSession()
                            } else {
                                invalidate()
                            }
                            dismissTaskCards(listOf(entry))
                        }
                    }
                }
            }
            taskSwitcherCloseAllHighlight -> {
                HapticHelper.confirmLaunch(this, settings)
                val entries = recentApps.filterNot { it.isLocked }
                val packages = entries.map { it.app.packageName }
                val dismissed = packages.toSet()
                recentApps.removeAll { it.app.packageName in dismissed }
                RecentTasksLoader.removePackages(packages)
                RecentTasksLoader.removeTaskIds(entries.map { it.taskId })
                taskSwitcherLayout = null
                if (recentApps.isEmpty()) {
                    endTaskSwitcherSession()
                } else {
                    invalidate()
                }
                dismissTaskCards(entries)
            }
            taskSwitcherFreeWindowHighlight >= 0 -> {
                val app = layout.rows
                    .getOrNull(taskSwitcherFreeWindowHighlight)
                    ?.entry
                    ?.app
                if (app != null) {
                    HapticHelper.confirmLaunch(this, settings)
                    endTaskSwitcherSession(runBeforeExit = true) {
                        TaskSwitcherMenuActions.launchFreeWindow(
                            context,
                            app.packageName,
                            settings,
                            appRepository,
                            app = app,
                        )
                    }
                } else {
                    endTaskSwitcherSession()
                }
            }
            taskSwitcherRowHighlight >= 0 && !taskSwitcherRowLongPressTriggered -> {
                val entry = layout.rows.getOrNull(taskSwitcherRowHighlight)?.entry
                endTaskSwitcherSession(runBeforeExit = true) {
                    entry?.let {
                        HapticHelper.confirmLaunch(this@EdgeGestureOverlayView, settings)
                        actionExecutor.switchToRecentTask(
                            taskId = it.taskId,
                            rawIdentifier = it.rawIdentifier,
                            topComponent = it.topComponent,
                            packageName = it.app.packageName,
                            settings = settings,
                        )
                    }
                }
            }
        }
    }

    private fun clearTaskSwitcherPickHighlights() {
        taskSwitcherRowHighlight = -1
        taskSwitcherCloseHighlight = -1
        taskSwitcherFreeWindowHighlight = -1
        taskSwitcherCloseAllHighlight = false
        taskSwitcherCloseHapticIndex = -1
        taskSwitcherContinuousHapticKey = -1
    }

    private fun clearTaskSwitcherContinuousLongPressTracking() {
        cancelTaskSwitcherRowLongPress()
        taskSwitcherRowPressIndex = -1
        taskSwitcherRowPressDownTime = 0L
        taskSwitcherRowLongPressTriggered = false
        resetTaskSwitcherCloseLongPressTracking()
    }

    private fun resolveTaskSwitcherPick(
        layout: TaskSwitcherPanelLayout,
        localX: Float,
        localY: Float,
    ): TaskSwitcherPick {
        val touchX = panelEnterAdjustedX(localX, layout.panelRect)
        if (layout.closeAllRect.contains(touchX, localY)) {
            return TaskSwitcherPick(closeAll = true)
        }
        layout.rows.forEachIndexed { index, row ->
            if (taskSwitcherClosePickMatches(localX, localY, row, layout)) {
                return TaskSwitcherPick(close = index)
            }
            val handleX = if (layout.panelRect.contains(touchX, localY)) touchX else localX
            if (row.freeWindowRect.contains(handleX, localY) &&
                taskSwitcherHitVisible(row.freeWindowRect, layout.listRect)
            ) {
                return TaskSwitcherPick(freeWindow = index)
            }
            if (row.rowRect.contains(touchX, localY) && taskSwitcherHitVisible(row.rowRect, layout.listRect)) {
                return TaskSwitcherPick(row = index)
            }
        }
        return TaskSwitcherPick()
    }

    private fun taskSwitcherCloseApproachXRange(layout: TaskSwitcherPanelLayout): Pair<Float, Float>? {
        var minLeft = Float.MAX_VALUE
        var maxRight = Float.MIN_VALUE
        var hasVisibleRow = false
        layout.rows.forEach { row ->
            if (!RectF.intersects(layout.listRect, row.rowRect)) return@forEach
            hasVisibleRow = true
            val hit = taskSwitcherCloseHitRect(row.rowRect)
            minLeft = minOf(minLeft, hit.left)
            maxRight = maxOf(maxRight, hit.right)
        }
        if (!hasVisibleRow) return null
        return minLeft to maxRight
    }

    private fun isInTaskSwitcherCloseApproachZone(localX: Float, layout: TaskSwitcherPanelLayout): Boolean {
        val (left, right) = taskSwitcherCloseApproachXRange(layout) ?: return false
        if (localX < left || localX > right) return false
        val sampleRow = layout.rows.firstOrNull { RectF.intersects(layout.listRect, it.rowRect) } ?: return false
        val column = taskSwitcherCloseColumnRect(sampleRow.rowRect)
        val panelInteriorStart = when (side) {
            PanelSide.LEFT -> column.right + dp(2f)
            PanelSide.RIGHT -> layout.panelRect.left + dp(2f)
        }
        val panelInteriorEnd = when (side) {
            PanelSide.LEFT -> layout.panelRect.right - dp(2f)
            PanelSide.RIGHT -> column.left - dp(2f)
        }
        if (localX in panelInteriorStart..panelInteriorEnd) return false
        return true
    }

    private fun taskSwitcherClosePickMatches(
        localX: Float,
        localY: Float,
        row: TaskSwitcherRowLayout,
        layout: TaskSwitcherPanelLayout,
    ): Boolean {
        if (!taskSwitcherHitVisible(row.closeRect, layout.listRect)) return false
        if (row.closeRect.contains(localX, localY)) return true
        if (!isInTaskSwitcherCloseApproachZone(localX, layout)) return false
        return localY >= row.rowRect.top && localY <= row.rowRect.bottom
    }

    private fun updateContinuousTaskSwitcherPick(
        layout: TaskSwitcherPanelLayout,
        pick: TaskSwitcherPick,
        eventTime: Long,
        haptic: Boolean,
    ) {
        if (gestureSession.taskSwitcherContinuousPickActive() && !taskSwitcherContinuousPickReady()) {
            return
        }
        syncTaskSwitcherRowLongPress(pick, eventTime)
        syncTaskSwitcherCloseLongPress(pick, layout, eventTime)
        applyTaskSwitcherPick(pick, haptic = haptic)
    }

    private fun taskSwitcherContinuousPickReady(): Boolean = panelEnterProgress >= 1f

    private fun syncTaskSwitcherRowLongPress(pick: TaskSwitcherPick, eventTime: Long) {
        if (!gestureSession.taskSwitcherContinuousPickActive()) return
        if (taskSwitcherContextMenuActive()) return
        if (taskSwitcherRowLongPressTriggered) return
        if (pick.row >= 0) {
            if (pick.row == taskSwitcherClosePressIndex &&
                (taskSwitcherCloseLongPressRunnable != null || taskSwitcherCloseLongPressTriggered)
            ) {
                return
            }
            if (taskSwitcherRowPressIndex != pick.row) {
                taskSwitcherRowPressIndex = pick.row
                taskSwitcherRowPressDownTime = eventTime
                scheduleTaskSwitcherRowLongPress(pick.row)
            }
        } else {
            cancelTaskSwitcherRowLongPress()
            taskSwitcherRowPressIndex = -1
            taskSwitcherRowPressDownTime = 0L
        }
    }

    private fun applyTaskSwitcherPick(pick: TaskSwitcherPick, haptic: Boolean): Boolean {
        val changed = pick.row != taskSwitcherRowHighlight ||
            pick.close != taskSwitcherCloseHighlight ||
            pick.freeWindow != taskSwitcherFreeWindowHighlight ||
            pick.closeAll != taskSwitcherCloseAllHighlight
        taskSwitcherRowHighlight = pick.row
        taskSwitcherCloseHighlight = pick.close
        taskSwitcherFreeWindowHighlight = pick.freeWindow
        taskSwitcherCloseAllHighlight = pick.closeAll
        if (changed && haptic && (pick.row >= 0 || pick.close >= 0 || pick.freeWindow >= 0 || pick.closeAll)) {
            val skipCloseRetick = pick.close >= 0 &&
                !gestureSession.taskSwitcherContinuousPickActive() &&
                pick.close == taskSwitcherCloseHapticIndex
            val hapticKey = continuousPickHapticKey(pick)
            val skipContinuousRetick = hapticKey >= 0 &&
                gestureSession.taskSwitcherContinuousPickActive() &&
                hapticKey == taskSwitcherContinuousHapticKey
            if (!skipCloseRetick && !skipContinuousRetick) {
                HapticHelper.appTick(this, settings)
                if (pick.close >= 0 && !gestureSession.taskSwitcherContinuousPickActive()) {
                    taskSwitcherCloseHapticIndex = pick.close
                }
                if (hapticKey >= 0 && gestureSession.taskSwitcherContinuousPickActive()) {
                    taskSwitcherContinuousHapticKey = hapticKey
                }
            }
        }
        return changed
    }

    private fun syncTaskSwitcherCloseLongPress(
        pick: TaskSwitcherPick,
        layout: TaskSwitcherPanelLayout,
        eventTime: Long,
    ) {
        if (!gestureSession.taskSwitcherContinuousPickActive()) return
        if (taskSwitcherContextMenuActive()) return
        if (pick.close >= 0) {
            if (taskSwitcherRowLongPressRunnable != null && pick.close == taskSwitcherRowPressIndex) {
                return
            }
            val packageName = layout.rows.getOrNull(pick.close)?.entry?.app?.packageName ?: return
            if (taskSwitcherCloseLongPressTriggered && taskSwitcherClosePressIndex == pick.close) {
                return
            }
            if (taskSwitcherClosePressIndex != pick.close) {
                taskSwitcherClosePressIndex = pick.close
                taskSwitcherClosePressDownTime = eventTime
                taskSwitcherCloseLongPressTriggered = false
                scheduleTaskSwitcherCloseLongPress(pick.close, packageName)
            }
        } else if (taskSwitcherClosePressIndex >= 0) {
            when {
                pick.row >= 0 && pick.row == taskSwitcherClosePressIndex -> {
                    resetTaskSwitcherCloseLongPressTracking()
                }
                pick.freeWindow >= 0 && pick.freeWindow == taskSwitcherClosePressIndex -> {
                    resetTaskSwitcherCloseLongPressTracking()
                }
                continuousPickTargetIndex(pick) == -1 -> {
                    if (taskSwitcherCloseLongPressRunnable != null) return
                    resetTaskSwitcherCloseLongPressTracking()
                }
                else -> resetTaskSwitcherCloseLongPressTracking()
            }
        } else {
            resetTaskSwitcherCloseLongPressTracking()
        }
    }

    private fun resetTaskSwitcherCloseLongPressTracking() {
        cancelTaskSwitcherCloseLongPress()
        taskSwitcherClosePressIndex = -1
        taskSwitcherClosePressDownTime = 0L
        taskSwitcherCloseLongPressTriggered = false
    }

    private fun scrollTaskSwitcherToFollowFinger(localY: Float) {
        if (recentApps.isEmpty()) return
        val layout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        val rowHeight = dp(42f)
        val fingerInList = (localY - layout.listRect.top).coerceIn(0f, layout.listRect.height())
        val contentY = fingerInList + layout.scrollOffset
        val index = (contentY / rowHeight).toInt().coerceIn(0, recentApps.lastIndex)
        val desiredOffset = index * rowHeight + rowHeight / 2f - fingerInList
        val clamped = desiredOffset.coerceIn(0f, layout.maxScrollOffset)
        if (kotlin.math.abs(clamped - taskSwitcherScrollOffset) < 0.5f) return
        taskSwitcherScrollOffset = clamped
        taskSwitcherLayout = null
        markTaskSwitcherGestureScrolledIfNeeded()
    }

    private fun markTaskSwitcherGestureScrolledIfNeeded() {
        if (!gestureSession.taskSwitcherContinuousPickActive()) {
            taskSwitcherGestureScrolled = true
        }
    }

    private fun applyTaskSwitcherEdgeAutoScroll(layout: TaskSwitcherPanelLayout, localY: Float): Boolean {
        val edge = dp(20f)
        val step = dp(10f)
        when {
            localY < layout.listRect.top + edge && taskSwitcherScrollOffset > 0f -> {
                val next = (taskSwitcherScrollOffset - step).coerceAtLeast(0f)
                if (next == taskSwitcherScrollOffset) return false
                taskSwitcherScrollOffset = next
                taskSwitcherLayout = null
                invalidate()
                return true
            }
            localY > layout.listRect.bottom - edge &&
                taskSwitcherScrollOffset < layout.maxScrollOffset -> {
                val next = (taskSwitcherScrollOffset + step).coerceAtMost(layout.maxScrollOffset)
                if (next == taskSwitcherScrollOffset) return false
                taskSwitcherScrollOffset = next
                taskSwitcherLayout = null
                invalidate()
                return true
            }
            else -> return false
        }
    }

    private fun resetTaskSwitcherTouchHighlights() {
        cancelTaskSwitcherCloseLongPress()
        cancelTaskSwitcherRowLongPress()
        clearTaskSwitcherPickHighlights()
        taskSwitcherClosePressIndex = -1
        taskSwitcherClosePressDownTime = 0L
        taskSwitcherCloseLongPressTriggered = false
        taskSwitcherRowPressIndex = -1
        taskSwitcherRowPressDownTime = 0L
        taskSwitcherRowLongPressTriggered = false
        taskSwitcherScrollDragging = false
        invalidate()
    }

    private fun taskSwitcherOverscrollEnabled(): Boolean =
        !gestureSession.taskSwitcherContinuousPickActive()

    private fun taskSwitcherRubberBand(rawExcess: Float): Float {
        val sign = if (rawExcess >= 0f) 1f else -1f
        val resisted = kotlin.math.abs(rawExcess) * TASK_SWITCHER_OVERSCROLL_RESISTANCE
        return sign * resisted.coerceAtMost(dp(TASK_SWITCHER_OVERSCROLL_MAX_DP))
    }

    private fun cancelTaskSwitcherOverscrollAnimation() {
        taskSwitcherOverscrollAnimator?.cancel()
        taskSwitcherOverscrollAnimator = null
    }

    private fun releaseTaskSwitcherOverscroll() {
        if (kotlin.math.abs(taskSwitcherOverscrollOffset) < 0.5f) {
            taskSwitcherOverscrollOffset = 0f
            return
        }
        cancelTaskSwitcherOverscrollAnimation()
        val start = taskSwitcherOverscrollOffset
        taskSwitcherOverscrollAnimator = ValueAnimator.ofFloat(start, 0f).apply {
            duration = TASK_SWITCHER_OVERSCROLL_RELEASE_MS
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { animator ->
                taskSwitcherOverscrollOffset = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    taskSwitcherOverscrollOffset = 0f
                    taskSwitcherOverscrollAnimator = null
                    invalidate()
                }
            })
            start()
        }
    }

    private fun beginTaskSwitcherScrollDrag(localY: Float) {
        taskSwitcherScrollDragStartY = localY
        taskSwitcherScrollDragStartOffset = taskSwitcherScrollOffset
        taskSwitcherScrollDragging = false
    }

    private fun handleTaskSwitcherScrollMove(touchX: Float, localY: Float): Boolean {
        val layout = taskSwitcherLayout ?: return false
        if (!layout.listRect.contains(touchX, localY)) return false
        val canScroll = layout.maxScrollOffset > 0f
        val canOverscroll = taskSwitcherOverscrollEnabled()
        if (!canScroll && !canOverscroll) return false
        val dy = localY - taskSwitcherScrollDragStartY
        if (!taskSwitcherScrollDragging && kotlin.math.abs(dy) <= dp(TASK_SWITCHER_SCROLL_SLOP_DP)) return false
        if (!taskSwitcherScrollDragging) {
            taskSwitcherScrollDragging = true
            cancelTaskSwitcherOverscrollAnimation()
            cancelTaskSwitcherCloseLongPress()
            cancelTaskSwitcherRowLongPress()
            taskSwitcherClosePressIndex = -1
            taskSwitcherRowPressIndex = -1
            clearTaskSwitcherPickHighlights()
        }
        val rawOffset = taskSwitcherScrollDragStartOffset + taskSwitcherScrollDragStartY - localY
        if (!canScroll) {
            taskSwitcherScrollOffset = 0f
            taskSwitcherOverscrollOffset = -taskSwitcherRubberBand(rawOffset)
            invalidate()
            return true
        }
        when {
            rawOffset < 0f -> {
                taskSwitcherScrollOffset = 0f
                taskSwitcherOverscrollOffset = if (canOverscroll) {
                    -taskSwitcherRubberBand(rawOffset)
                } else {
                    0f
                }
            }
            rawOffset > layout.maxScrollOffset -> {
                taskSwitcherScrollOffset = layout.maxScrollOffset
                val excess = rawOffset - layout.maxScrollOffset
                taskSwitcherOverscrollOffset = if (canOverscroll) {
                    -taskSwitcherRubberBand(excess)
                } else {
                    0f
                }
            }
            else -> {
                taskSwitcherScrollOffset = rawOffset
                taskSwitcherOverscrollOffset = 0f
            }
        }
        taskSwitcherLayout = null
        markTaskSwitcherGestureScrolledIfNeeded()
        invalidate()
        return true
    }

    private fun finishTaskSwitcherScrollDrag(): Boolean {
        val wasDragging = taskSwitcherScrollDragging
        taskSwitcherScrollDragging = false
        if (wasDragging && taskSwitcherOverscrollEnabled()) {
            releaseTaskSwitcherOverscroll()
        }
        return wasDragging
    }

    private fun taskSwitcherHitVisible(rect: RectF, listRect: RectF): Boolean =
        RectF.intersects(listRect, rect)

    private fun isTaskSwitcherPanelTouch(localX: Float, localY: Float, panel: RectF): Boolean =
        panel.contains(localX, localY)

    private fun isTaskSwitcherDownPickHeld(
        localX: Float,
        localY: Float,
        layout: TaskSwitcherPanelLayout,
    ): Boolean {
        val touchX = panelEnterAdjustedX(localX, layout.panelRect)
        when {
            taskSwitcherRowPressIndex >= 0 -> {
                val row = layout.rows.getOrNull(taskSwitcherRowPressIndex) ?: return false
                return row.rowRect.contains(touchX, localY) &&
                    taskSwitcherHitVisible(row.rowRect, layout.listRect)
            }
            taskSwitcherClosePressIndex >= 0 -> {
                val row = layout.rows.getOrNull(taskSwitcherClosePressIndex) ?: return false
                return taskSwitcherClosePickMatches(localX, localY, row, layout)
            }
            taskSwitcherFreeWindowHighlight >= 0 -> {
                val row = layout.rows.getOrNull(taskSwitcherFreeWindowHighlight) ?: return false
                val handleX = if (layout.panelRect.contains(touchX, localY)) touchX else localX
                return row.freeWindowRect.contains(handleX, localY) &&
                    taskSwitcherHitVisible(row.freeWindowRect, layout.listRect)
            }
            taskSwitcherCloseAllHighlight -> return layout.closeAllRect.contains(touchX, localY)
            else -> return false
        }
    }

    private fun cancelContinuousTaskSwitcherOnLeavePanel() {
        if (!gestureSession.taskSwitcherContinuousPickActive()) return
        dismissTaskSwitcherContextMenu(immediate = true)
        clearTaskSwitcherContinuousLongPressTracking()
        clearTaskSwitcherPickHighlights()
        endTaskSwitcherSession()
    }

    private fun isTaskSwitcherInteractiveTouch(
        localX: Float,
        localY: Float,
        layout: TaskSwitcherPanelLayout,
    ): Boolean {
        if (isTaskSwitcherPanelTouch(localX, localY, layout.panelRect)) return true
        if (isInTaskSwitcherCloseApproachZone(localX, layout)) {
            return layout.rows.any { row ->
                RectF.intersects(layout.listRect, row.rowRect) &&
                    localY >= row.rowRect.top &&
                    localY <= row.rowRect.bottom
            }
        }
        layout.rows.forEach { row ->
            if (!RectF.intersects(layout.listRect, row.rowRect)) return@forEach
            if (row.freeWindowRect.contains(localX, localY)) {
                return true
            }
        }
        return false
    }

    private fun shouldFreezeTaskSwitcherAnchor(): Boolean =
        gestureSession.panelMode() == OverlayPanelMode.TASK_SWITCHER

    /** Edge-strip tracking while the finger stays on the trigger zone. */
    private fun updateTaskSwitcherEdgeTracking(rawY: Float, localX: Float, localY: Float): Boolean {
        if (!zoneLayout.containsTrigger(localX, localY)) return false
        val continuousPick = gestureSession.taskSwitcherContinuousPickActive()
        if (!shouldFreezeTaskSwitcherAnchor()) {
            taskSwitcherAnchorRawY = rawY
            taskSwitcherFrozenAnchorLocalY = null
            taskSwitcherLayout = null
            scrollTaskSwitcherToFollowFinger(localY)
        }
        val layout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        if (continuousPick) {
            if (isTaskSwitcherInteractiveTouch(localX, localY, layout)) {
                val pick = resolveTaskSwitcherPick(
                    layout,
                    localX,
                    localY,
                )
                val menu = taskSwitcherContextMenu.takeIf { taskSwitcherContextMenuActive() }
                if (menu == null ||
                    shouldDismissTaskSwitcherMenuForContinuousSlide(layout, localX, localY, menu)
                ) {
                    if (taskSwitcherContinuousPickReady()) {
                        applyTaskSwitcherEdgeAutoScroll(layout, localY)
                        updateContinuousTaskSwitcherPick(layout, pick, System.currentTimeMillis(), haptic = true)
                    }
                }
            } else {
                clearTaskSwitcherContinuousLongPressTracking()
                clearTaskSwitcherPickHighlights()
            }
        } else {
            clearTaskSwitcherPickHighlights()
        }
        invalidate()
        return true
    }

    private fun taskSwitcherContextMenuActive(): Boolean =
        taskSwitcherContextMenu != null && !taskSwitcherMenuDismissing

    private fun clearTaskSwitcherMenuRowHighlight() {
        if (gestureSession.taskSwitcherContinuousPickActive()) return
        val menuRowIndex = taskSwitcherContextMenu?.rowIndex ?: -1
        if (menuRowIndex >= 0 && taskSwitcherRowHighlight == menuRowIndex) {
            taskSwitcherRowHighlight = -1
        }
        taskSwitcherRowLongPressTriggered = false
    }

    private fun dismissTaskSwitcherContextMenu(immediate: Boolean = false) {
        if (taskSwitcherContextMenu == null) return
        if (taskSwitcherMenuDismissing && !immediate) return
        clearTaskSwitcherMenuRowHighlight()
        cancelTaskSwitcherMenuAnimation()
        taskSwitcherMenuHighlight = -1
        taskSwitcherMenuAwaitingRelease = false
        taskSwitcherScrollDragging = false
        cancelTaskSwitcherOverscrollAnimation()
        taskSwitcherOverscrollOffset = 0f
        if (immediate || taskSwitcherMenuEnterProgress <= 0f) {
            finishTaskSwitcherMenuDismiss()
            invalidate()
            return
        }
        startTaskSwitcherMenuExitAnimation()
    }

    private fun finishTaskSwitcherMenuDismiss() {
        taskSwitcherContextMenu = null
        taskSwitcherMenuDismissing = false
        taskSwitcherMenuEnterProgress = 1f
    }

    private fun continuousPickHapticKey(pick: TaskSwitcherPick): Int = when {
        pick.closeAll -> Int.MIN_VALUE
        pick.close >= 0 -> (pick.close shl 2) or 1
        pick.row >= 0 -> pick.row shl 2
        pick.freeWindow >= 0 -> (pick.freeWindow shl 2) or 2
        else -> -1
    }

    private fun continuousPickTargetIndex(pick: TaskSwitcherPick): Int = when {
        pick.close >= 0 -> pick.close
        pick.row >= 0 -> pick.row
        pick.freeWindow >= 0 -> pick.freeWindow
        else -> -1
    }

    private fun shouldDismissTaskSwitcherMenuForContinuousSlide(
        layout: TaskSwitcherPanelLayout,
        localX: Float,
        localY: Float,
        menu: TaskSwitcherContextMenuLayout,
    ): Boolean {
        val touchX = panelEnterAdjustedX(localX, layout.panelRect)
        if (menu.menuRect.contains(touchX, localY)) return false
        val pick = resolveTaskSwitcherPick(layout, localX, localY)
        if (pick.row == menu.rowIndex) return false
        if (pick.freeWindow == menu.rowIndex) return false
        if (pick.close == menu.rowIndex) return true
        if (pick.row >= 0 || pick.close >= 0 || pick.freeWindow >= 0 || pick.closeAll) return true
        return false
    }

    private fun dismissTaskSwitcherContextMenuForSlide() {
        if (!taskSwitcherContextMenuActive()) return
        dismissTaskSwitcherContextMenu()
        taskSwitcherRowLongPressTriggered = false
        taskSwitcherRowPressIndex = -1
        taskSwitcherRowPressDownTime = 0L
        cancelTaskSwitcherRowLongPress()
        resetTaskSwitcherCloseLongPressTracking()
    }

    /** True when the finger stays on the menu in continuous mode (panel sliding is deferred). */
    private fun handleContinuousTaskSwitcherMenuMove(
        localX: Float,
        touchX: Float,
        localY: Float,
    ): Boolean {
        val menu = taskSwitcherContextMenu.takeIf { taskSwitcherContextMenuActive() } ?: return false
        if (menu.menuRect.contains(touchX, localY)) {
            updateTaskSwitcherMenuHighlight(touchX, localY, menu, haptic = true)
            invalidate()
            return true
        }
        val layout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        if (!shouldDismissTaskSwitcherMenuForContinuousSlide(layout, localX, localY, menu)) {
            return false
        }
        dismissTaskSwitcherContextMenuForSlide()
        return false
    }

    /** True when a menu item was activated; false to fall through to normal row pick / release. */
    private fun handleContinuousTaskSwitcherMenuUp(touchX: Float, localY: Float): Boolean {
        val menu = taskSwitcherContextMenu.takeIf { taskSwitcherContextMenuActive() } ?: return false
        if (!menu.menuRect.contains(touchX, localY)) {
            dismissTaskSwitcherContextMenuForSlide()
            return false
        }
        if (activateTaskSwitcherMenuSelection(menu, touchX, localY)) {
            return true
        }
        dismissTaskSwitcherContextMenuForSlide()
        return false
    }

    private fun cancelTaskSwitcherMenuAnimation() {
        taskSwitcherMenuEnterAnimator?.removeAllListeners()
        taskSwitcherMenuEnterAnimator?.cancel()
        taskSwitcherMenuEnterAnimator = null
    }

    private fun startTaskSwitcherMenuEnterAnimation() {
        cancelTaskSwitcherMenuAnimation()
        taskSwitcherMenuDismissing = false
        taskSwitcherMenuEnterProgress = 0f
        taskSwitcherMenuEnterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TASK_SWITCHER_MENU_ENTER_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { animator ->
                taskSwitcherMenuEnterProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    private fun startTaskSwitcherMenuExitAnimation() {
        cancelTaskSwitcherMenuAnimation()
        taskSwitcherMenuDismissing = true
        val startProgress = taskSwitcherMenuEnterProgress.coerceIn(0f, 1f)
        taskSwitcherMenuEnterAnimator = ValueAnimator.ofFloat(startProgress, 0f).apply {
            duration = (TASK_SWITCHER_MENU_EXIT_MS * startProgress).toLong().coerceAtLeast(1L)
            interpolator = AccelerateInterpolator(1.6f)
            addUpdateListener { animator ->
                taskSwitcherMenuEnterProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    taskSwitcherMenuEnterAnimator = null
                    finishTaskSwitcherMenuDismiss()
                    invalidate()
                }
            })
            start()
        }
        invalidate()
    }

    private fun scheduleTaskSwitcherRowLongPress(index: Int) {
        cancelTaskSwitcherRowLongPress()
        taskSwitcherRowLongPressRunnable = Runnable {
            if (taskSwitcherRowPressIndex != index) return@Runnable
            if (taskSwitcherCloseHighlight == index) return@Runnable
            showTaskSwitcherContextMenu(index)
        }
        postDelayed(taskSwitcherRowLongPressRunnable!!, TASK_SWITCHER_LONG_PRESS_MS)
    }

    private fun cancelTaskSwitcherRowLongPress() {
        taskSwitcherRowLongPressRunnable?.let { removeCallbacks(it) }
        taskSwitcherRowLongPressRunnable = null
    }

    private fun showTaskSwitcherContextMenu(index: Int) {
        val layout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        val row = layout.rows.getOrNull(index) ?: return
        taskSwitcherRowLongPressTriggered = true
        taskSwitcherRowPressIndex = -1
        taskSwitcherRowPressDownTime = 0L
        taskSwitcherContinuousHapticKey = continuousPickHapticKey(TaskSwitcherPick(row = index))
        cancelTaskSwitcherRowLongPress()
        HapticHelper.confirmLaunch(this, settings)
        cancelTaskSwitcherMenuAnimation()
        taskSwitcherMenuDismissing = false
        val inlineInPanel = gestureSession.taskSwitcherContinuousPickActive()
        val anchorX = panelEnterAdjustedX(lastTaskSwitcherTouchX, layout.panelRect)
        val anchorY = lastTaskSwitcherTouchY
        val menu = TaskSwitcherContextMenuLayoutFactory.build(
            side = side,
            panelRect = layout.panelRect,
            listRect = layout.listRect,
            rowIndex = index,
            packageName = row.entry.app.packageName,
            items = TaskSwitcherMenuActions.buildMenuItems(context.applicationContext),
            viewWidth = width,
            viewHeight = height,
            density = resources.displayMetrics.density,
            anchorX = anchorX,
            anchorY = anchorY,
            inlineInPanel = inlineInPanel,
        )
        taskSwitcherContextMenu = menu
        startTaskSwitcherMenuEnterAnimation()
        if (gestureSession.taskSwitcherContinuousPickActive()) {
            taskSwitcherMenuAwaitingRelease = false
            taskSwitcherMenuHighlight = menu.itemRects.indexOfFirst { it.contains(anchorX, anchorY) }
        } else {
            taskSwitcherMenuAwaitingRelease = true
            taskSwitcherMenuHighlight = -1
        }
        invalidate()
    }

    private fun updateTaskSwitcherMenuHighlight(
        touchX: Float,
        localY: Float,
        menu: TaskSwitcherContextMenuLayout,
        haptic: Boolean,
    ) {
        val prev = taskSwitcherMenuHighlight
        taskSwitcherMenuHighlight = menu.itemRects.indexOfFirst { it.contains(touchX, localY) }
        if (haptic && taskSwitcherMenuHighlight != prev && taskSwitcherMenuHighlight >= 0) {
            HapticHelper.appTick(this, settings)
        }
    }

    private fun beginTaskSwitcherPanelDismissTap(localY: Float) {
        beginTaskSwitcherScrollDrag(localY)
        clearTaskSwitcherPickHighlights()
        taskSwitcherRowPressIndex = -1
        taskSwitcherClosePressIndex = -1
        cancelTaskSwitcherRowLongPress()
        cancelTaskSwitcherCloseLongPress()
    }

    private fun handleTaskSwitcherContextMenuTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val menu = taskSwitcherContextMenu ?: return false
        if (taskSwitcherMenuAwaitingRelease) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                taskSwitcherMenuAwaitingRelease = false
                taskSwitcherMenuHighlight = -1
                invalidate()
            }
            return true
        }
        val panelLayout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        val panel = panelLayout.panelRect
        val touchX = panelEnterAdjustedX(localX, panel)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateTaskSwitcherMenuHighlight(touchX, localY, menu, haptic = true)
                if (taskSwitcherMenuHighlight < 0 && !menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                    if (panel.contains(touchX, localY)) {
                        beginTaskSwitcherPanelDismissTap(localY)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                    return false
                }
                updateTaskSwitcherMenuHighlight(touchX, localY, menu, haptic = true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activateTaskSwitcherMenuSelection(menu, touchX, localY)) {
                    invalidate()
                    return true
                }
                if (!menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                    if (panel.contains(touchX, localY)) {
                        beginTaskSwitcherPanelDismissTap(localY)
                    }
                    return false
                }
                invalidate()
                return true
            }
        }
        return false
    }

    private fun activateTaskSwitcherMenuSelection(
        menu: TaskSwitcherContextMenuLayout,
        touchX: Float,
        localY: Float,
    ): Boolean {
        val selected = menu.itemRects.indexOfFirst { it.contains(touchX, localY) }
        taskSwitcherMenuHighlight = -1
        if (selected < 0) return false
        val item = menu.items.getOrNull(selected) ?: return true
        HapticHelper.confirmLaunch(this, settings)
        executeTaskSwitcherMenuItem(menu.packageName, item)
        return true
    }

    private fun executeTaskSwitcherMenuItem(packageName: String, item: TaskSwitcherMenuItem) {
        dismissTaskSwitcherContextMenu()
        val endSessionOnFreeWindow = item.type == TaskSwitcherMenuItemType.FREE_WINDOW
        TaskSwitcherMenuActions.execute(
            context = context,
            item = item,
            packageName = packageName,
            settings = settings,
            appRepository = appRepository,
            onSessionEnd = if (endSessionOnFreeWindow) {
                { endTaskSwitcherSession() }
            } else {
                null
            },
        )
        if (item.type == TaskSwitcherMenuItemType.APP_INFO) {
            endTaskSwitcherSession()
        }
    }

    private fun taskSwitcherCloseDwellMs(): Long =
        if (gestureSession.taskSwitcherContinuousPickActive()) {
            TASK_SWITCHER_CLOSE_CONTINUOUS_DWELL_MS
        } else {
            TASK_SWITCHER_CLOSE_DWELL_MS
        }

    private fun scheduleTaskSwitcherCloseLongPress(index: Int, packageName: String) {
        cancelTaskSwitcherCloseLongPress()
        taskSwitcherCloseLongPressRunnable = Runnable {
            if (gestureSession.panelMode() != OverlayPanelMode.TASK_SWITCHER) return@Runnable
            if (taskSwitcherClosePressIndex != index) return@Runnable
            if (taskSwitcherCloseLongPressTriggered) return@Runnable
            taskSwitcherCloseLongPressTriggered = true
            val locked = recentApps.getOrNull(index)?.isLocked != true
            toggleTaskSwitcherLock(index, packageName, locked)
        }
        postDelayed(taskSwitcherCloseLongPressRunnable!!, taskSwitcherCloseDwellMs())
    }

    private fun cancelTaskSwitcherCloseLongPress() {
        taskSwitcherCloseLongPressRunnable?.let { removeCallbacks(it) }
        taskSwitcherCloseLongPressRunnable = null
    }

    private fun toggleTaskSwitcherLock(index: Int, packageName: String, locked: Boolean) {
        if (recentApps.getOrNull(index)?.app?.packageName != packageName) return
        TaskSwitcherLockStore.setLocked(context, packageName, locked)
        recentApps[index] = recentApps[index].copy(isLocked = locked)
        taskSwitcherLayout = null
        HapticHelper.confirmLaunch(this, settings)
        invalidate()
    }

    private fun dismissTaskCards(entries: List<RecentAppEntry>) {
        if (entries.isEmpty() || !TaskManagerUtil.hasPermission()) return
        Thread {
            TaskManagerUtil.runOnTaskWorker {
                entries.filterNot { it.isLocked }.forEach { entry ->
                    val removed = if (entry.taskId > 0) {
                        TaskManagerUtil.removeTaskById(entry.taskId)
                    } else {
                        TaskManagerUtil.removeTaskByPackage(entry.app.packageName)
                    }
                    if (!removed) {
                        Log.w(
                            "EdgeGestureOverlay",
                            "dismissTaskCards failed package=${entry.app.packageName} taskId=${entry.taskId}",
                        )
                    }
                }
                RecentTasksLoader.syncFromSystem(appRepository)
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        if (width > 0 && height > 0 && gestureSession.isActive()) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        syncZoneLayout()
        if (previewMode) {
            when (previewContent) {
                LayoutPreviewContent.TRIGGER_ONLY -> drawTriggerZonePreview(canvas)
                LayoutPreviewContent.INDEX_ONLY -> drawLetterRail(canvas)
            }
            return
        }
        drawVisibleAdjustIndicator(canvas)
        if (!gestureSession.isActive()) return
        when (gestureSession.panelMode()) {
            OverlayPanelMode.INDEX -> {
                drawWithPanelEnterAnimation(canvas, indexPanelContentRect()) {
                    drawLetterRail(canvas)
                    if (indexSession.selectedLetter != null) {
                        drawAppGrid(canvas)
                        drawLetterBubble(canvas)
                    }
                }
            }
            OverlayPanelMode.QUICK_LAUNCHER -> {
                val panelRect = quickLauncherPanelRect()
                val contentRect = quickLauncherPanelController.combinedContentRect(panelRect)
                drawWithPanelEnterAnimation(canvas, contentRect) {
                    drawQuickLauncherPanel(canvas, drawToolbar = true)
                }
            }
            OverlayPanelMode.TASK_SWITCHER -> drawTaskSwitcherPanel(canvas)
            OverlayPanelMode.SHELL_COMMANDS -> {
                panelContentRect.set(shellPanelController.panelContentRect)
                shellPanelController.draw(canvas, panelEnterProgress)
            }
            OverlayPanelMode.NONE -> syncAdjustIndicatorAnimation()
        }
    }

    private fun drawVisibleAdjustIndicator(canvas: Canvas) {
        if (adjustIndicatorProgress <= 0f) return
        val visual = captureAdjustIndicatorVisual() ?: return
        adjustIndicatorHoldVisual = visual
        drawAdjustIndicator(
            canvas = canvas,
            mode = visual.mode,
            fraction = visual.fraction,
            anchorRawY = visual.anchorRawY,
        )
    }

    private fun captureAdjustIndicatorVisual(): AdjustIndicatorVisual? {
        adjustPanelState?.let { state ->
            val fraction = when (state.dragTarget) {
                VolumeDragTarget.MEDIA -> actionExecutor.adjustFraction()
                else -> state.fraction
            }
            return AdjustIndicatorVisual(state.mode, fraction, state.anchorRawY)
        }
        gestureSession.adjustModeOrNull()?.let { mode ->
            return AdjustIndicatorVisual(
                mode = mode,
                fraction = actionExecutor.adjustFraction(),
                anchorRawY = gestureSession.adjustAnchorRawY(),
            )
        }
        return adjustIndicatorHoldVisual
    }

    private fun animateAdjustIndicatorTo(
        target: Float,
        durationMs: Long,
        interpolator: Interpolator = DecelerateInterpolator(),
        onEnd: (() -> Unit)? = null,
    ) {
        adjustIndicatorAnimator?.cancel()
        val receding = target == 0f && adjustIndicatorProgress > 0f
        if (receding) {
            adjustIndicatorReceding = true
            freezeAdjustIndicatorLayout(
                adjustIndicatorHoldVisual?.anchorRawY ?: adjustPanelState?.anchorRawY
                    ?: gestureSession.adjustAnchorRawY(),
                adjustIndicatorHoldVisual?.mode
                    ?: adjustPanelState?.mode
                    ?: gestureSession.adjustModeOrNull(),
            )
        } else if (target >= 1f) {
            adjustIndicatorReceding = false
            if (!adjustPanelEntering) {
                adjustIndicatorFrozenLayout = null
            }
        }
        if (durationMs <= 0L || adjustIndicatorProgress == target) {
            adjustIndicatorProgress = target
            if (target >= 1f) {
                adjustIndicatorReceding = false
                if (!adjustPanelEntering) {
                    adjustIndicatorFrozenLayout = null
                }
            }
            invalidate()
            onEnd?.invoke()
            return
        }
        adjustIndicatorAnimator = ValueAnimator.ofFloat(adjustIndicatorProgress, target).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { animator ->
                adjustIndicatorProgress = animator.animatedValue as Float
                invalidate()
            }
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onEnd()
                    }
                })
            }
            start()
        }
    }

    private fun resolveBrightnessPanelVisual(): BrightnessPanelVisual? {
        adjustPanelState?.takeIf { it.mode == ContinuousAdjustController.Mode.BRIGHTNESS }?.let { state ->
            return BrightnessPanelVisual(
                autoBrightnessEnabled = state.autoBrightnessEnabled,
                darkModeEnabled = state.darkModeEnabled,
            )
        }
        if (gestureSession.adjustModeOrNull() != ContinuousAdjustController.Mode.BRIGHTNESS) return null
        return BrightnessPanelVisual(
            autoBrightnessEnabled = actionExecutor.readAutoBrightnessEnabled(),
            darkModeEnabled = actionExecutor.readDarkModeEnabled(),
        )
    }

    private fun resolveVolumePanelVisual(): VolumePanelVisual? {
        adjustPanelState?.takeIf { it.mode == ContinuousAdjustController.Mode.VOLUME }?.let { state ->
            return VolumePanelVisual(
                expanded = state.volumeExpanded,
                ringFraction = state.ringFraction,
                notificationFraction = state.notificationFraction,
                ringerMode = state.ringerMode,
                interruptionFilter = state.interruptionFilter,
            )
        }
        if (gestureSession.adjustModeOrNull() != ContinuousAdjustController.Mode.VOLUME) return null
        return VolumePanelVisual(
            expanded = false,
            ringFraction = actionExecutor.readVolumeFraction(VolumeControlHelper.Stream.RING),
            notificationFraction = actionExecutor.readVolumeFraction(
                VolumeControlHelper.Stream.NOTIFICATION,
            ),
            ringerMode = actionExecutor.readRingerMode(),
            interruptionFilter = actionExecutor.readInterruptionFilter(),
        )
    }

    private fun drawAdjustIndicator(
        canvas: Canvas,
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
    ) {
        val layout = if (adjustIndicatorReceding || adjustPanelEntering) {
            adjustIndicatorFrozenLayout ?: run {
                freezeAdjustIndicatorLayout(anchorRawY, mode)
                adjustIndicatorFrozenLayout
            }
        } else {
            adjustIndicatorFrozenLayout = null
            val panelVisible = adjustPanelState != null && !adjustPanelDismissing
            if (panelVisible && adjustIndicatorLayout != null) {
                adjustIndicatorLayout
            } else {
                updateAdjustIndicatorLayout(anchorRawY, mode = mode)
                adjustIndicatorLayout
            }
        } ?: return
        val volumePanel = resolveVolumePanelVisual()
        val brightnessPanel = resolveBrightnessPanelVisual()
        AdjustLevelIndicator.draw(
            canvas = canvas,
            layout = layout,
            mode = mode,
            fraction = fraction,
            enterProgress = adjustIndicatorProgress,
            density = resources.displayMetrics.density,
            side = side,
            recede = adjustIndicatorReceding,
            volumePanel = volumePanel,
            brightnessPanel = brightnessPanel,
            context = context,
        )
    }

    private fun startAdjustIndicatorEnterAnimationIfNeeded() {
        if (adjustPanelState != null) return
        if (adjustIndicatorProgress >= 1f) return
        if (adjustIndicatorAnimator?.isRunning == true) return
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = ADJUST_INDICATOR_ENTER_MS,
            interpolator = DecelerateInterpolator(),
        )
    }

    private fun syncAdjustIndicatorAnimation() {
        val active = gestureSession.isAdjustMode() || adjustPanelState != null
        if (active == wasAdjustMode) return
        wasAdjustMode = active
        if (active) {
            // Floating panel enter/exit is owned by showAdjustPanel / dismissAdjustPanel.
            if (adjustPanelState != null) {
                wasAdjustMode = true
                return
            }
            startAdjustIndicatorEnterAnimationIfNeeded()
        } else if (adjustPanelState == null && adjustIndicatorProgress > 0f) {
            adjustIndicatorHoldVisual = captureAdjustIndicatorVisual() ?: adjustIndicatorHoldVisual
            animateAdjustIndicatorTo(
                target = 0f,
                durationMs = ADJUST_INDICATOR_EXIT_MS,
                interpolator = AccelerateInterpolator(),
            ) {
                clearAdjustIndicatorExitState()
                adjustIndicatorProgress = 0f
                invalidate()
                notifyOverlayLayoutIfNeeded()
            }
        }
    }

    override fun hapticLetterTick() {
        HapticHelper.letterTick(this, settings)
    }

    override fun hapticAppTick() {
        HapticHelper.appTick(this, settings)
    }

    override fun hapticGestureStart() {
        HapticHelper.gestureStart(this, settings)
    }

    override fun hapticLongThreshold() {
        HapticHelper.longThreshold(this, settings)
    }

    override fun hapticConfirmLaunch() {
        HapticHelper.confirmLaunch(this, settings)
    }

    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) {
        postDelayed(runnable, delayMs)
    }

    override fun cancelDelayed(runnable: Runnable) {
        removeCallbacks(runnable)
    }

    override fun requestInvalidate() {
        if (gestureSession.isAdjustMode()) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastAdjustInvalidateMs < 16L) return
            lastAdjustInvalidateMs = now
        }
        invalidate()
    }

    override fun onShowAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        deferWindowLayout: Boolean,
    ) {
        showAdjustPanel(mode, fraction, anchorRawY, deferWindowLayout)
    }

    override fun onSessionStart(mode: OverlayPanelMode) {
        syncZoneLayout()
        cancelPanelEnterAnimation()
        when (mode) {
            OverlayPanelMode.TASK_SWITCHER -> {
                panelEnterProgress = 0f
                taskSwitcherFrozenAnchorLocalY = null
                taskSwitcherAnchorRawY = pathRecognizer.gestureStartRawY()
                taskSwitcherLayout = null
                loadTaskSwitcherApps(deferInvalidate = false)
            }
            OverlayPanelMode.INDEX, OverlayPanelMode.QUICK_LAUNCHER,
            OverlayPanelMode.SHELL_COMMANDS -> {
                panelEnterProgress = 0f
                if (mode == OverlayPanelMode.SHELL_COMMANDS) {
                    shellPanelController.syncSettings(settings)
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherPageIndex = 0
                    quickLauncherPageCount = 1
                    quickLauncherPageSwipeTracking = false
                    quickLauncherPageSwipeLocked = false
                    quickLauncherPageChangedThisGesture = false
                    cancelQuickLauncherPageSnapAnimation()
                    quickLauncherPageDragOffset = 0f
                    quickLauncherExiting = false
                    quickLauncherOpeningGestureActive = true
                    quickLauncherEdgePageZone = 0
                    quickLauncherEdgeAutoPageSeeded = false
                    quickLauncherPanelController.ensureDefaultsPersisted(settings)
                    warmQuickLauncherShortcutCache()
                    warmQuickLauncherActionIconCache()
                    warmQuickLauncherIconCache()
                    quickLauncherAnchorRawY = pathRecognizer.lastRawY()
                        .takeIf { it > 0f }
                        ?: pathRecognizer.gestureStartRawY()
                }
            }
            OverlayPanelMode.NONE -> {
                panelEnterProgress = 1f
                if (gestureSession.isAdjustMode()) {
                    wasAdjustMode = true
                    startAdjustIndicatorEnterAnimationIfNeeded()
                }
            }
        }
        panelGridSession.reset()
        onSessionStartCallback()
        notifyPresentationTouchRequirementChanged()
        if (mode != OverlayPanelMode.NONE) {
            post {
                if (gestureSession.panelMode() != mode) return@post
                syncZoneLayout()
                if (mode == OverlayPanelMode.TASK_SWITCHER) {
                    taskSwitcherLayout = null
                    taskSwitcherFrozenAnchorLocalY = resolveTaskSwitcherAnchorLocalY()
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER &&
                    !gestureSession.quickLauncherContinuousPickActive()
                ) {
                    quickLauncherFrozenAnchorLocalY = resolveQuickLauncherAnchorLocalY()
                }
                startPanelEnterAnimation()
            }
        }
    }

    private fun loadTaskSwitcherApps(deferInvalidate: Boolean = false) {
        taskSwitcherLayout = null
        clearTaskSwitcherPickHighlights()

        recentApps = mutableListOf()
        taskSwitcherLoading = TaskManagerUtil.hasPermission()
        if (!deferInvalidate) {
            invalidateTaskSwitcherPanel()
        }

        if (!TaskManagerUtil.hasPermission()) {
            if (!deferInvalidate) {
                invalidateTaskSwitcherPanel()
            }
            return
        }

        val generation = ++taskSwitcherLoadGeneration
        RecentTasksLoader.refreshAsync(appRepository) { fresh ->
            if (generation != taskSwitcherLoadGeneration) return@refreshAsync
            if (gestureSession.panelMode() != OverlayPanelMode.TASK_SWITCHER) return@refreshAsync
            if (taskSwitcherContextMenuActive()) {
                dismissTaskSwitcherContextMenu(immediate = true)
            }
            taskSwitcherLoading = false
            recentApps = fresh.toMutableList()
            taskSwitcherLayout = null
            invalidateTaskSwitcherPanel()
        }
    }

    override fun onSessionEnd() {
        cancelPanelEnterAnimation()
        gestureAnimationOverlay.hide()
        adjustPanelState?.let {
            adjustIndicatorReceding = false
            // Enter/exit animation is owned by showAdjustPanel / dismissAdjustPanel.
            // Snapping to 1f here cancels an in-flight enter (common on fast left swipes).
        }
        if (adjustPanelState == null) {
            adjustIndicatorAnimator?.cancel()
            adjustIndicatorProgress = 0f
            wasAdjustMode = false
            clearAdjustIndicatorExitState()
        }
        panelEnterProgress = 1f
        taskSwitcherLoadGeneration++
        syncZoneLayout()
        panelGridSession.reset()
        taskSwitcherLayout = null
        clearTaskSwitcherPickHighlights()
        taskSwitcherAnchorRawY = null
        taskSwitcherFrozenAnchorLocalY = null
        quickLauncherAnchorRawY = null
        quickLauncherFrozenAnchorLocalY = null
        quickLauncherContinuousHapticIndex = -1
        quickLauncherPressIndex = -1
        quickLauncherPressDownTime = 0L
        quickLauncherPageIndex = 0
        quickLauncherPageCount = 1
        quickLauncherPageSwipeTracking = false
        quickLauncherPageSwipeLocked = false
        quickLauncherPageChangedThisGesture = false
        cancelQuickLauncherPageSnapAnimation()
        quickLauncherPageDragOffset = 0f
        quickLauncherLaunchEndDeferMs = 0L
        quickLauncherOpeningGestureActive = false
        quickLauncherEdgePageZone = 0
        quickLauncherEdgeAutoPageSeeded = false
        quickLauncherExiting = false
        cancelQuickLauncherLongPress()
        shellPanelExiting = false
        shellPanelController.reset()
        shellOverlayDialogHost.dismiss()
        quickLauncherPanelController.reset()
        quickLauncherOverlayDialogHost.dismiss()
        invalidateQuickLauncherDerivedCaches()
        syncShellPanelInputFocus()
        taskSwitcherScrollOffset = 0f
        taskSwitcherScrollDragging = false
        taskSwitcherOverscrollOffset = 0f
        cancelTaskSwitcherOverscrollAnimation()
        taskSwitcherGestureScrolled = false
        taskSwitcherExiting = false
        dismissTaskSwitcherContextMenu(immediate = true)
        cancelTaskSwitcherCloseLongPress()
        cancelTaskSwitcherRowLongPress()
        notifyOverlayLayoutIfNeeded()
        notifyPresentationTouchRequirementChanged()
    }

    override fun onRequestInvalidate() {
        invalidate()
    }

    private fun syncZoneLayout() {
        val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        zoneLayout.update(
            settings = settings,
            viewWidth = screenW,
            viewHeight = screenH,
            density = resources.displayMetrics.density,
            sessionActive = gestureSession.isActive(),
            previewMode = previewMode,
            layoutHeight = screenH,
            windowOffsetY = 0f,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
        )
    }

    private fun forEachGesturePoint(
        event: MotionEvent,
        localX: Float,
        localY: Float,
        includeHistory: Boolean,
        block: (rawX: Float, rawY: Float, localX: Float, localY: Float) -> Unit,
    ) {
        if (includeHistory) {
            val rawOffsetX = event.rawX - event.x
            val rawOffsetY = event.rawY - event.y
            for (i in 0 until event.historySize) {
                val rawX = event.getHistoricalX(i) + rawOffsetX
                val rawY = event.getHistoricalY(i) + rawOffsetY
                val (lx, ly) = rawToLocal(rawX, rawY)
                block(rawX, rawY, lx, ly)
            }
        }
        block(event.rawX, event.rawY, localX, localY)
    }

    private fun rawToLocal(rawX: Float, rawY: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return rawX - loc[0] to rawY - loc[1]
    }

    private fun appsPerRow(): Int = settings.appsPerRow.coerceIn(2, 5)

    private fun quickLauncherColumnsPerPage(): Int =
        settings.quickLauncherColumnsPerPage.coerceIn(2, 5)

    private fun quickLauncherRowsPerPage(): Int =
        settings.quickLauncherRowsPerPage.coerceIn(2, QUICK_LAUNCHER_MAX_ROWS)

    private fun quickLauncherGridLayoutInfo(): GridLayoutInfo {
        val m = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val panelWidth = m * quickLauncherCellWidth + quickLauncherGridPadding * 2
        return GridLayoutInfo(m, m, rows, panelWidth)
    }

    private fun quickLauncherPanelContentHeight(rows: Int): Float =
        rows * quickLauncherCellHeight + quickLauncherGridPadding * 2 + quickLauncherHeaderHeight

    private fun gridLayoutInfo(appCount: Int): GridLayoutInfo {
        val m = appsPerRow()
        val panelColumns = if (appCount in 1 until m) appCount else m
        val rows = if (appCount == 0) 1 else ceil(appCount / m.toFloat()).toInt()
        val panelWidth = panelColumns * cellWidth + gridPadding * 2
        return GridLayoutInfo(m, panelColumns, rows, panelWidth)
    }

    private fun visualColumn(index: Int, m: Int, appCount: Int): Int {
        val colInRow = index % m
        val row = index / m
        val appsInRow = minOf(m, appCount - row * m)
        return when (side) {
            PanelSide.RIGHT -> when {
                appCount < m -> appCount - 1 - colInRow
                appsInRow == m -> m - 1 - colInRow
                else -> m - appsInRow + colInRow
            }
            PanelSide.LEFT -> colInRow
        }
    }

    private fun drawTriggerZonePreview(canvas: Canvas) {
        val corner = dp(6f)
        if (settings.interceptSystemBackGesture) {
            val intercept = zoneLayout.interceptZoneRect()
            triggerPreviewFillPaint.color = Color.argb(36, 33, 150, 243)
            canvas.drawRoundRect(intercept, corner, corner, triggerPreviewFillPaint)
            triggerPreviewStrokePaint.color = Color.argb(120, 66, 165, 245)
            triggerPreviewStrokePaint.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(intercept, corner, corner, triggerPreviewStrokePaint)
        }
        zoneLayout.triggerZoneRects().forEach { (handleId, zone) ->
            triggerPreviewFillPaint.color = Color.argb(72, 255, 152, 0)
            canvas.drawRoundRect(zone, corner, corner, triggerPreviewFillPaint)
            triggerPreviewStrokePaint.color = Color.argb(210, 255, 167, 38)
            triggerPreviewStrokePaint.strokeWidth = dp(2f)
            canvas.drawRoundRect(zone, corner, corner, triggerPreviewStrokePaint)
            drawSwipeDistancePreview(canvas, zone, handleId)
        }
    }

    private fun drawSwipeDistancePreview(canvas: Canvas, zone: RectF, handleId: String) {
        val handle = settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
        val shortR = dp(handle.shortSwipeDistanceDp)
        val longR = dp(handle.longSwipeDistanceDp)
        if (longR <= shortR) return
        val cx = when (side) {
            PanelSide.LEFT -> zone.right
            PanelSide.RIGHT -> zone.left
        }
        val cy = zone.centerY()
        val startAngle = when (side) {
            PanelSide.LEFT -> -90f
            PanelSide.RIGHT -> 90f
        }
        val sweep = 180f
        triggerPreviewStrokePaint.style = android.graphics.Paint.Style.STROKE
        triggerPreviewFillPaint.style = android.graphics.Paint.Style.FILL

        triggerPreviewFillPaint.color = Color.argb(28, 186, 104, 200)
        canvas.drawArc(cx - longR, cy - longR, cx + longR, cy + longR, startAngle, sweep, true, triggerPreviewFillPaint)
        triggerPreviewStrokePaint.color = Color.argb(170, 171, 71, 188)
        triggerPreviewStrokePaint.strokeWidth = dp(2f)
        canvas.drawArc(cx - longR, cy - longR, cx + longR, cy + longR, startAngle, sweep, false, triggerPreviewStrokePaint)

        triggerPreviewFillPaint.color = Color.argb(40, 255, 183, 77)
        canvas.drawArc(cx - shortR, cy - shortR, cx + shortR, cy + shortR, startAngle, sweep, true, triggerPreviewFillPaint)
        triggerPreviewStrokePaint.color = Color.argb(220, 255, 152, 0)
        triggerPreviewStrokePaint.strokeWidth = dp(2.5f)
        canvas.drawArc(cx - shortR, cy - shortR, cx + shortR, cy + shortR, startAngle, sweep, false, triggerPreviewStrokePaint)
    }

    private fun startGestureAnimationIfNeeded(rawX: Float, rawY: Float) {
        if (!settings.gestureHintEnabled) return
        val overlay = gestureAnimationOverlay
        overlay.applySettings(settings, gestureSession.activeHandleId())
        overlay.show()
        val state = overlay.animationState
        if (state == null) {
            post { startGestureAnimationIfNeeded(rawX, rawY) }
            return
        }
        state.onDragStart(rawX, rawY)
    }

    private fun updateGestureAnimationIfNeeded(rawX: Float, rawY: Float) {
        val state = gestureAnimationOverlay.animationState
        if (!settings.gestureHintEnabled || state == null || !state.isActive) return
        state.onDrag(
            rawX = rawX,
            rawY = rawY,
            swipeDirection = pathRecognizer.currentSwipeDirection(),
            inwardPx = pathRecognizer.currentInwardPx(),
        )
    }

    private fun finishGestureAnimationIfNeeded() {
        if (!settings.gestureHintEnabled) return
        gestureAnimationOverlay.animationState?.onDragEnd()
        gestureAnimationOverlay.hideAfterGesture()
    }

    private fun drawLetterRail(canvas: Canvas) {
        val rail = zoneLayout.indexRailRect()
        val alphaScale = settings.panelOpacity.coerceIn(0.6f, 1f)
        railBgPaint.color = Color.argb((200 * alphaScale).toInt(), 38, 38, 42)
        canvas.drawRoundRect(rail, railCorner, railCorner, railBgPaint)

        val slotHeight = rail.height() / railLetters.size
        val letterSize = slotHeight.coerceAtMost(dp(11.5f))
        val selectedLetter = indexSession.selectedLetter

        railLetters.forEachIndexed { index, letter ->
            val centerY = rail.top + slotHeight * index + slotHeight * 0.65f
            val centerX = rail.centerX()
            val selected = letter == selectedLetter
            if (selected) {
                letterCirclePaint.color = Color.argb(90, 255, 255, 255)
                canvas.drawCircle(centerX, centerY - letterSize * 0.15f, letterSize * 0.85f, letterCirclePaint)
                letterPaint.color = Color.WHITE
                letterPaint.textSize = letterSize * 1.05f
                letterPaint.typeface = Typeface.DEFAULT_BOLD
            } else {
                letterPaint.color = Color.argb(200, 220, 220, 220)
                letterPaint.textSize = letterSize
                letterPaint.typeface = Typeface.DEFAULT
            }
            canvas.drawText(letter.toString(), centerX, centerY, letterPaint)
        }
    }

    private fun drawLetterBubble(canvas: Canvas) {
        val letter = indexSession.selectedLetter ?: return
        val center = bubbleCenter()
        bubblePaint.color = Color.argb((240 * settings.panelOpacity).toInt().coerceIn(150, 240), 52, 52, 56)
        canvas.drawCircle(center.x, center.y, bubbleRadius, bubblePaint)
        bubbleLetterPaint.textSize = sp(22f)
        canvas.drawText(
            letter.toString(),
            center.x,
            center.y + sp(22f) * 0.35f,
            bubbleLetterPaint,
        )
    }

    private fun drawAppGrid(canvas: Canvas) {
        val filteredApps = indexSession.filteredApps
        if (filteredApps.isEmpty()) return
        val appCount = filteredApps.size
        val layout = gridLayoutInfo(appCount)
        val m = layout.appsPerRow
        val grid = gridPopupRect()
        panelBgPaint.color = Color.argb((215 * settings.panelOpacity).toInt().coerceIn(140, 215), 48, 48, 52)
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)

        indexSession.gridCellBounds.clear()
        filteredApps.forEachIndexed { index, app ->
            val row = index / m
            val visualCol = visualColumn(index, m, appCount)
            val left = grid.left + gridPadding + visualCol * cellWidth
            val top = grid.top + gridPadding + row * cellHeight
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            indexSession.gridCellBounds += app to cell

            if (app == indexSession.highlightedApp) {
                tmpRect.set(cell.left + dp(3f), cell.top + dp(2f), cell.right - dp(3f), cell.bottom - dp(2f))
                val paint = if (indexSession.longPressArmed) {
                    cellLongPressHighlightPaint
                } else {
                    cellHighlightPaint
                }
                canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), paint)
            }

            val icon = iconFor(app)
            val iconTop = cell.top + gridIconTopInset
            val label = ellipsize(app.label, cellWidth - gridCellInset * 2)
            val labelBaseline = iconTop + gridIconSize + gridIconLabelGap - appLabelPaint.fontMetrics.ascent
            val iconCenterX = cell.centerX()
            canvas.drawBitmap(icon, iconCenterX - gridIconSize / 2f, iconTop, null)
            canvas.drawText(label, iconCenterX, labelBaseline, appLabelPaint)
        }
    }

    private fun anchorColumnIndex(layout: GridLayoutInfo): Int {
        return when (side) {
            PanelSide.LEFT -> 0
            PanelSide.RIGHT -> layout.panelColumns - 1
        }
    }

    private fun columnCenterX(grid: RectF, columnIndex: Int): Float {
        return grid.left + gridPadding + columnIndex * cellWidth + cellWidth / 2f
    }

    private fun bubbleCenter(): PointF {
        val filteredApps = indexSession.filteredApps
        if (filteredApps.isNotEmpty()) {
            val grid = gridPopupRect()
            val layout = gridLayoutInfo(filteredApps.size)
            val cx = columnCenterX(grid, anchorColumnIndex(layout))
            val cy = grid.top - bubbleRadius - bubblePanelGap
            return PointF(cx, cy)
        }
        val rail = zoneLayout.indexRailRect()
        val cy = indexSession.selectedLetterCenterY() ?: rail.centerY()
        val cx = when (side) {
            PanelSide.LEFT -> rail.right + bubbleRadius + dp(4f)
            PanelSide.RIGHT -> rail.left - bubbleRadius - dp(4f)
        }
        return PointF(cx, cy)
    }

    private fun gridPopupRect(): RectF {
        val filteredApps = indexSession.filteredApps
        val layout = gridLayoutInfo(filteredApps.size)
        val gh = layout.rows * cellHeight + gridPadding * 2
        val gw = layout.panelWidth
        val rail = zoneLayout.indexRailRect()
        val letterY = indexSession.selectedLetterCenterY() ?: rail.centerY()
        val bubbleReserve = bubbleRadius * 2 + bubblePanelGap + dp(8f)
        var top = letterY - gh / 2f
        top = top.coerceSafe(bubbleReserve + dp(8f), height - gh - dp(16f))
        val gap = dp(8f)
        val left = when (side) {
            PanelSide.LEFT -> rail.right + gap
            PanelSide.RIGHT -> rail.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun drawScaledIcon(canvas: Canvas, app: AppInfo, left: Float, top: Float, size: Float) {
        tmpRect.set(left, top, left + size, top + size)
        canvas.drawBitmap(iconFor(app), null, tmpRect, iconBitmapPaint)
    }

    private fun iconFor(app: AppInfo): Bitmap {
        return iconCache.getOrPut(app.packageName) {
            val size = gridIconSize.toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val drawable = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon.mutate()
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        }
    }

    private fun quickLauncherRootItems(): List<QuickLauncherItem> =
        quickLauncherPanelController.displayItems(settings)

    private fun quickLauncherItemCacheKey(item: QuickLauncherItem): String =
        "${item.type.id}\u0000${item.payload}"

    private fun rebuildQuickLauncherAppsByPackage() {
        quickLauncherAppsByPackage = apps.associateBy { it.packageName }
    }

    private fun invalidateQuickLauncherDerivedCaches() {
        quickLauncherIconCache.clear()
        quickLauncherLabelCache.clear()
        quickLauncherCachedPages = null
        quickLauncherCachedPagesKey = 0
        quickLauncherLayoutPanelWidth = 0f
    }

    private fun quickLauncherPages(): List<List<QuickLauncherItem>> {
        val root = quickLauncherRootItems()
        val pageSize = quickLauncherPageSize()
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val key = QuickLauncherGridLogic.pagesCacheKey(root.size, root.hashCode(), pageSize, columns, rows)
        quickLauncherCachedPages?.let { cached ->
            if (key == quickLauncherCachedPagesKey) return cached
        }
        val pages = if (root.isEmpty()) {
            listOf(emptyList())
        } else {
            root.chunked(pageSize)
        }
        quickLauncherPageCount = pages.size
        quickLauncherPageIndex = quickLauncherPageIndex.coerceIn(0, pages.size - 1)
        quickLauncherCachedPages = pages
        quickLauncherCachedPagesKey = key
        return pages
    }

    private fun quickLauncherPanelWidthForPaging(): Float {
        if (quickLauncherLayoutPanelWidth > 0f) return quickLauncherLayoutPanelWidth
        return quickLauncherPanelRect().width().coerceAtLeast(1f).also {
            quickLauncherLayoutPanelWidth = it
        }
    }

    private fun invalidateQuickLauncherPanel() {
        if (gestureSession.panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            invalidate()
            return
        }
        val panelRect = quickLauncherPanelRect()
        if (panelRect.isEmpty) {
            invalidate()
            return
        }
        val dirty = quickLauncherPanelController.combinedContentRect(panelRect)
        val offsetX = panelEnterOffsetX(dirty)
        val pad = dp(2f).toInt()
        invalidate(
            (dirty.left + offsetX).toInt() - pad,
            dirty.top.toInt() - pad,
            (dirty.right + offsetX).toInt() + pad,
            dirty.bottom.toInt() + pad,
        )
    }

    private fun warmQuickLauncherIconCache() {
        rebuildQuickLauncherAppsByPackage()
        val size = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        quickLauncherRootItems().forEach { item ->
            resolveQuickLauncherItemIcon(item, size)
        }
    }

    private fun resolveQuickLauncherItemIcon(item: QuickLauncherItem, size: Int): Bitmap? {
        val key = "${quickLauncherItemCacheKey(item)}\u0000$size"
        quickLauncherIconCache[key]?.let { return it }
        if (item.type == QuickLauncherItemType.ACTION &&
            QuickLauncherIconResolver.shouldUseGestureVectorIcon(item)
        ) {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
            return GestureActionIconBitmap.get(
                action = action,
                sizePx = size,
                tintArgb = android.graphics.Color.WHITE,
            )?.also { quickLauncherIconCache[key] = it }
        }
        return QuickLauncherIconResolver.iconBitmap(
            item = item,
            appsByPackage = quickLauncherAppsByPackage,
            size = size,
            context = context,
        )?.also { quickLauncherIconCache[key] = it }
    }

    private fun warmQuickLauncherShortcutCache() {
        val items = quickLauncherRootItems()
        if (items.none { it.type == QuickLauncherItemType.SHORTCUT }) return
        Thread {
            AppShortcutLoader.warmQuickLauncherShortcuts(context, items)
        }.start()
    }

    private fun warmQuickLauncherActionIconCache() {
        val sizePx = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        quickLauncherRootItems().forEach { item ->
            if (item.type != QuickLauncherItemType.ACTION) return@forEach
            QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                GestureActionIconBitmap.preload(action, sizePx)
            }
        }
    }

    private fun quickLauncherItemLabel(item: QuickLauncherItem): String {
        val cacheKey = quickLauncherItemCacheKey(item)
        quickLauncherLabelCache[cacheKey]?.let { return it }
        if (quickLauncherAppsByPackage.isEmpty()) {
            rebuildQuickLauncherAppsByPackage()
        }
        val label = when (item.type) {
            QuickLauncherItemType.APP -> quickLauncherAppsByPackage[item.payload]?.label ?: item.label
            QuickLauncherItemType.SHORTCUT -> item.label.ifBlank { "快捷方式" }
            QuickLauncherItemType.ACTION -> item.label.ifBlank { "动作" }
            QuickLauncherItemType.WIDGET -> item.label.ifBlank { "小组件" }
        }
        quickLauncherLabelCache[cacheKey] = label
        return label
    }

    private fun quickLauncherItemIcon(item: QuickLauncherItem): Bitmap? {
        val size = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        if (quickLauncherAppsByPackage.isEmpty()) {
            rebuildQuickLauncherAppsByPackage()
        }
        return resolveQuickLauncherItemIcon(item, size)
    }

    private fun drawQuickLauncherPanel(canvas: Canvas, drawToolbar: Boolean = true) {
        val panelRect = quickLauncherPanelRect()
        if (panelRect.isEmpty) return
        quickLauncherPagination()
        panelGridSession.cellBounds.clear()
        panelContentRect.set(panelRect)
        drawQuickLauncherPanelChrome(canvas, panelRect)

        val dragOffset = quickLauncherPageDragOffset
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val pagingActive = quickLauncherPageSwipeLocked ||
            quickLauncherPageSnapAnimator?.isRunning == true ||
            kotlin.math.abs(dragOffset) > dp(0.5f)
        quickLauncherLayoutPanelWidth = panelWidth
        val recordCells = !pagingActive
        val clipLayer = canvas.save()
        canvas.clipRect(panelRect)
        drawQuickLauncherPageCells(
            canvas = canvas,
            panelRect = panelRect,
            pageIndex = quickLauncherPageIndex,
            translateX = if (pagingActive) dragOffset else 0f,
            recordCells = recordCells,
        )
        if (pagingActive && kotlin.math.abs(dragOffset) > dp(0.5f)) {
            if (dragOffset < 0f && quickLauncherPageIndex < quickLauncherPageCount - 1) {
                drawQuickLauncherPageCells(
                    canvas = canvas,
                    panelRect = panelRect,
                    pageIndex = quickLauncherPageIndex + 1,
                    translateX = dragOffset + panelWidth,
                    recordCells = false,
                )
            }
            if (dragOffset > 0f && quickLauncherPageIndex > 0) {
                drawQuickLauncherPageCells(
                    canvas = canvas,
                    panelRect = panelRect,
                    pageIndex = quickLauncherPageIndex - 1,
                    translateX = dragOffset - panelWidth,
                    recordCells = false,
                )
            }
        }
        canvas.restoreToCount(clipLayer)

        if (quickLauncherPanelController.editMode && quickLauncherPanelController.isDragging()) {
            drawQuickLauncherEditDragFloater(canvas, panelRect)
        }

        drawQuickLauncherPageIndicator(canvas, panelRect)
        if (drawToolbar) {
            quickLauncherPanelController.drawToolbar(canvas, panelRect)
        }
        quickLauncherPanelController.layoutDeleteBadges(panelGridSession.cellBounds.map { it.second })
        quickLauncherPanelController.drawDeleteBadges(canvas)
    }

    private fun drawQuickLauncherPanelChrome(canvas: Canvas, grid: RectF) {
        panelBgPaint.color = Color.argb((225 * settings.panelOpacity).toInt().coerceIn(150, 225), 48, 48, 52)
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        letterPaint.textAlign = Paint.Align.LEFT
        letterPaint.color = Color.WHITE
        letterPaint.textSize = sp(14f)
        letterPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("快速启动器", grid.left + quickLauncherGridPadding, grid.top + dp(16f), letterPaint)
        letterPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawQuickLauncherPageCells(
        canvas: Canvas,
        panelRect: RectF,
        pageIndex: Int,
        translateX: Float,
        recordCells: Boolean,
    ) {
        val entries = quickLauncherItemsForPage(pageIndex)
        val rootItems = quickLauncherRootItems()
        val layer = if (translateX != 0f) canvas.save() else -1
        if (layer >= 0) {
            canvas.translate(translateX, 0f)
        }
        val m = quickLauncherColumnsPerPage()
        val appCount = entries.size
        val pageSize = quickLauncherPageSize().coerceAtLeast(1)
        val pageStart = pageIndex * pageSize
        val fromGlobal = if (recordCells) quickLauncherPanelController.dragSourceGlobal() else -1
        val toGlobal = if (recordCells) quickLauncherPanelController.dragDestinationGlobal() else -1
        val itemCount = rootItems.size
        val mappingSize = pageStart + pageSize
        val editDragActive = recordCells &&
            quickLauncherPanelController.editMode &&
            fromGlobal >= 0 &&
            toGlobal >= 0
        val dragMapping = if (editDragActive) {
            QuickLauncherGridLogic.displayMapping(
                itemCount = itemCount,
                dragFrom = fromGlobal,
                dragSlotGlobal = toGlobal,
                mappingSize = mappingSize,
            )
        } else {
            null
        }
        if (entries.isEmpty() && dragMapping == null) {
            if (layer >= 0) canvas.restoreToCount(layer)
            return
        }
        val dragSourceIndex = if (recordCells) {
            quickLauncherDragLocalIndexOnPage(
                globalIndex = fromGlobal,
                pageStart = pageStart,
                pageItemCount = pageSize,
            )
        } else {
            -1
        }
        val slotCount = when {
            dragMapping != null -> pageSize
            recordCells && quickLauncherPanelController.editMode -> pageSize
            else -> appCount.coerceAtMost(pageSize)
        }
        fun drawCellAt(index: Int) {
            if (index !in 0 until slotCount) return
            val globalHere = pageStart + index
            val item: QuickLauncherItem
            val itemGlobalIndex: Int
            if (dragMapping != null) {
                val showOrig = dragMapping.getOrNull(globalHere) ?: return
                if (showOrig == fromGlobal) return
                item = rootItems.getOrNull(showOrig) ?: return
                itemGlobalIndex = showOrig
            } else {
                if (index !in entries.indices) return
                item = entries[index]
                itemGlobalIndex = globalHere
            }
            val row = index / m
            val visualCol = visualColumn(index, m, slotCount)
            val left = panelRect.left + quickLauncherGridPadding + visualCol * quickLauncherCellWidth
            val top = panelRect.top + quickLauncherHeaderHeight + quickLauncherGridPadding + row * quickLauncherCellHeight
            val cell = RectF(left, top, left + quickLauncherCellWidth, top + quickLauncherCellHeight)
            val (offsetX, offsetY) = 0f to 0f
            if (offsetX != 0f || offsetY != 0f) {
                canvas.save()
                canvas.translate(offsetX, offsetY)
            }
            if (recordCells) {
                panelGridSession.cellBounds.add(item to cell)
            }
            drawGridCell(
                canvas,
                cell,
                itemGlobalIndex,
                quickLauncherItemLabel(item),
                iconProvider = { quickLauncherItemIcon(item) },
                longPressArmed = recordCells &&
                    itemGlobalIndex == panelGridSession.highlightedIndex &&
                    quickLauncherLongPressArmed,
                iconSize = quickLauncherGridIconSize,
                iconTopInset = quickLauncherGridIconTopInset,
                iconLabelGap = quickLauncherGridIconLabelGap,
                labelMaxWidth = quickLauncherCellWidth - gridCellInset * 2,
            )
            if (offsetX != 0f || offsetY != 0f) {
                canvas.restore()
            }
        }
        for (index in 0 until slotCount) {
            if (index != dragSourceIndex) {
                drawCellAt(index)
            }
        }
        if (dragSourceIndex in 0 until slotCount) {
            drawCellAt(dragSourceIndex)
        }
        if (layer >= 0) {
            canvas.restoreToCount(layer)
        }
    }

    private fun quickLauncherPagingActiveForHitTest(): Boolean =
        quickLauncherPageSwipeLocked ||
            quickLauncherPageSnapAnimator?.isRunning == true ||
            kotlin.math.abs(quickLauncherPageDragOffset) > dp(0.5f)

    private fun quickLauncherGlobalIndexAt(touchX: Float, localY: Float, panelRect: RectF): Int {
        val pageSize = quickLauncherPageSize().coerceAtLeast(1)
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val offset = quickLauncherPageDragOffset
        val pagingActive = quickLauncherPagingActiveForHitTest()

        val pageIdx: Int
        val xInPage: Float
        if (pagingActive && quickLauncherPageCount > 1) {
            val relativeX = touchX - panelRect.left - offset
            pageIdx = (relativeX / panelWidth).toInt().coerceIn(0, quickLauncherPageCount - 1)
            xInPage = panelRect.left + relativeX - pageIdx * panelWidth
        } else {
            pageIdx = quickLauncherPageIndex.coerceIn(0, quickLauncherPageCount - 1)
            xInPage = touchX
        }

        val pageStart = pageIdx * pageSize
        val pageItems = quickLauncherItemsForPage(pageIdx)
        val localIndex = quickLauncherLocalCellIndexAt(
            xInPage = xInPage,
            localY = localY,
            panelRect = panelRect,
            maxSlotIndex = pageSize - 1,
        )
        return QuickLauncherGridLogic.dragSlotGlobal(pageStart, localIndex, pageSize)
    }

    private fun quickLauncherLocalCellIndexAt(
        xInPage: Float,
        localY: Float,
        panelRect: RectF,
        maxSlotIndex: Int,
    ): Int {
        if (maxSlotIndex < 0) return 0
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val col = ((xInPage - panelRect.left - quickLauncherGridPadding) / quickLauncherCellWidth + 0.5f)
            .toInt()
            .coerceIn(0, columns - 1)
        val row = ((localY - panelRect.top - quickLauncherHeaderHeight - quickLauncherGridPadding) /
            quickLauncherCellHeight + 0.5f)
            .toInt()
            .coerceIn(0, rows - 1)
        return (row * columns + col).coerceIn(0, maxSlotIndex)
    }

    private fun quickLauncherDragLocalIndexOnPage(
        globalIndex: Int,
        pageStart: Int,
        pageItemCount: Int,
    ): Int {
        if (globalIndex < 0 || pageItemCount <= 0) return -1
        if (globalIndex !in pageStart until pageStart + pageItemCount) return -1
        return globalIndex - pageStart
    }

    private fun drawQuickLauncherEditDragFloater(canvas: Canvas, panelRect: RectF) {
        val globalFrom = quickLauncherPanelController.dragSourceGlobal()
        val item = quickLauncherRootItems().getOrNull(globalFrom) ?: return
        val cx = quickLauncherPanelController.dragPointerX()
        val cy = quickLauncherPanelController.dragPointerY()
        val halfW = quickLauncherCellWidth / 2f
        val halfH = quickLauncherCellHeight / 2f
        val cell = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        drawGridCell(
            canvas = canvas,
            cell = cell,
            index = -1,
            label = quickLauncherItemLabel(item),
            iconProvider = { quickLauncherItemIcon(item) },
            iconSize = quickLauncherGridIconSize,
            iconTopInset = quickLauncherGridIconTopInset,
            iconLabelGap = quickLauncherGridIconLabelGap,
            labelMaxWidth = quickLauncherCellWidth - gridCellInset * 2,
        )
    }

    private fun drawQuickLauncherPageIndicator(canvas: Canvas, grid: RectF) {
        if (quickLauncherPageCount <= 1 || grid.isEmpty) return
        val dotRadius = dp(2.5f)
        val dotGap = dp(6f)
        val totalWidth = quickLauncherPageCount * dotRadius * 2f +
            (quickLauncherPageCount - 1) * dotGap
        var cx = grid.centerX() - totalWidth / 2f + dotRadius
        val cy = grid.bottom - dp(10f)
        for (page in 0 until quickLauncherPageCount) {
            pageIndicatorPaint.color = if (page == quickLauncherPageIndex) {
                Color.argb(230, 255, 255, 255)
            } else {
                Color.argb(90, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy, dotRadius, pageIndicatorPaint)
            cx += dotRadius * 2f + dotGap
        }
    }

    private fun drawTaskSwitcherPanel(canvas: Canvas) {
        val layout = computeTaskSwitcherLayout()
        taskSwitcherLayout = layout
        panelContentRect.set(layout.panelRect)
        drawWithPanelEnterAnimation(canvas, layout.panelRect) {
            drawTaskSwitcherPanelContent(canvas, layout)
        }
    }

    private fun drawTaskSwitcherPanelContent(canvas: Canvas, layout: TaskSwitcherPanelLayout) {
        val theme = OverlayPanelTheme.colors(context)
        val rowHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.rowHighlight }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.divider }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textPrimary
            textSize = sp(13.5f)
        }
        val closeAllPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            textSize = sp(13f)
            textAlign = Paint.Align.CENTER
        }
        val closeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.iconMuted
            strokeWidth = dp(1.55f)
            strokeCap = Paint.Cap.ROUND
        }
        val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.grip }

        val panel = layout.panelRect
        val panelCorner = dp(13f)
        drawElevatedRoundRect(canvas, panel, panelCorner, theme.cardBackground)

        if (layout.rows.isEmpty()) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textMuted
                textSize = sp(13f)
                textAlign = Paint.Align.CENTER
            }
            val hint = when {
                !TaskManagerUtil.hasPermission() ->
                    context.getString(R.string.task_switcher_no_shizuku)
                taskSwitcherLoading ->
                    context.getString(R.string.task_switcher_loading)
                else ->
                    context.getString(R.string.task_switcher_empty)
            }
            canvas.drawText(
                hint,
                panel.centerX(),
                panel.top + (panel.height() - layout.closeAllRect.height()) / 2f -
                    (hintPaint.descent() + hintPaint.ascent()) / 2f,
                hintPaint,
            )
        }

        canvas.save()
        canvas.clipRect(layout.listRect)
        val overscroll = taskSwitcherOverscrollOffset
        if (overscroll != 0f && taskSwitcherOverscrollEnabled()) {
            val pull = kotlin.math.abs(overscroll)
            val stretch = 1f + (pull / layout.listRect.height().coerceAtLeast(1f)) * TASK_SWITCHER_OVERSCROLL_STRETCH
            val pivotY = if (overscroll > 0f) layout.listRect.top else layout.listRect.bottom
            canvas.scale(1f, stretch, layout.listRect.centerX(), pivotY)
            canvas.translate(0f, overscroll)
        }
        layout.rows.forEachIndexed { index, row ->
            if (!RectF.intersects(layout.listRect, row.rowRect)) return@forEachIndexed
            if (index == taskSwitcherRowHighlight ||
                (taskSwitcherContextMenuActive() && index == taskSwitcherContextMenu?.rowIndex)
            ) {
                drawTaskSwitcherListHighlight(
                    canvas,
                    row.rowRect,
                    rowHighlightPaint,
                    layout,
                    panelCorner,
                    roundTopLeading = true,
                    roundTopTrailing = true,
                )
            }
            if (index == taskSwitcherCloseHighlight) {
                drawTaskSwitcherListHighlight(
                    canvas,
                    taskSwitcherCloseColumnRect(row.rowRect),
                    rowHighlightPaint,
                    layout,
                    panelCorner,
                    roundTopLeading = side == PanelSide.LEFT,
                    roundTopTrailing = side == PanelSide.RIGHT,
                )
            }
            if (index == taskSwitcherFreeWindowHighlight) {
                drawTaskSwitcherListHighlight(
                    canvas,
                    taskSwitcherHandleColumnRect(row.rowRect),
                    rowHighlightPaint,
                    layout,
                    panelCorner,
                )
            }
            val iconSize = dp(30f)
            val iconLeft = taskSwitcherIconLeft(row)
            val iconTop = row.rowRect.centerY() - iconSize / 2f
            drawScaledIcon(canvas, row.entry.app, iconLeft, iconTop, iconSize)
            val labelX = iconLeft + iconSize + dp(9f)
            val labelMaxWidth = taskSwitcherLabelMaxWidth(row, labelX)
            val label = ellipsize(row.entry.app.label, labelMaxWidth, labelPaint)
            val labelBaseline = row.rowRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(label, labelX, labelBaseline, labelPaint)
            val gripX = taskSwitcherGripX(row.rowRect)
            drawGripDots(canvas, gripX, row.rowRect.centerY(), gripPaint)
            drawCloseOrLockIcon(canvas, taskSwitcherCloseIconRect(row.rowRect), row.entry.isLocked, closeIconPaint)
            if (index < layout.rows.lastIndex) {
                val dividerBottom = row.rowRect.bottom
                if (dividerBottom <= layout.listRect.bottom && dividerBottom >= layout.listRect.top) {
                    canvas.drawLine(
                        row.rowRect.left + dp(10f),
                        dividerBottom,
                        row.rowRect.right - dp(10f),
                        dividerBottom,
                        dividerPaint,
                    )
                }
            }
        }
        canvas.restore()

        if (taskSwitcherCloseAllHighlight) {
            drawTaskSwitcherFooterHighlight(canvas, layout.closeAllRect, rowHighlightPaint, panelCorner)
        }
        val closeAllText = context.getString(R.string.task_switcher_close_all)
        canvas.drawText(
            closeAllText,
            layout.closeAllRect.centerX(),
            layout.closeAllRect.centerY() - (closeAllPaint.descent() + closeAllPaint.ascent()) / 2f,
            closeAllPaint,
        )
        drawTaskSwitcherContextMenu(canvas)
    }

    private fun drawTaskSwitcherContextMenu(canvas: Canvas) {
        val menu = taskSwitcherContextMenu ?: return
        val theme = OverlayPanelTheme.colors(context)
        val progress = taskSwitcherMenuEnterProgress
        if (progress <= 0f) return
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.rowHighlight }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textPrimary
            textSize = sp(14f)
        }
        val corner = dp(10f)
        val scale = if (menu.inlineInPanel) 0.94f + 0.06f * progress else 0.88f + 0.12f * progress
        val slideOffset = if (menu.inlineInPanel) {
            0f
        } else {
            when (side) {
                PanelSide.LEFT -> -dp(10f) * (1f - progress)
                PanelSide.RIGHT -> dp(10f) * (1f - progress)
            }
        }
        val pivotX = if (menu.inlineInPanel) {
            menu.menuRect.centerX()
        } else {
            when (side) {
                PanelSide.LEFT -> menu.menuRect.left
                PanelSide.RIGHT -> menu.menuRect.right
            }
        }
        val pivotY = menu.menuRect.centerY()
        val alpha = (255 * progress).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(slideOffset, 0f)
        canvas.scale(scale, scale, pivotX, pivotY)
        val layer = canvas.saveLayerAlpha(null, alpha)
        drawElevatedRoundRect(canvas, menu.menuRect, corner, theme.cardBackground)
        menu.items.forEachIndexed { index, item ->
            val rect = menu.itemRects[index]
            if (index == taskSwitcherMenuHighlight) {
                canvas.drawRect(rect, highlightPaint)
            }
            val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            val label = ellipsize(item.label, rect.width() - dp(24f), textPaint)
            canvas.drawText(label, rect.left + dp(16f), baseline, textPaint)
        }
        canvas.restoreToCount(layer)
        canvas.restore()
    }

    private fun computeTaskSwitcherLayout(): TaskSwitcherPanelLayout {
        val rowHeight = dp(42f)
        val footerHeight = dp(36f)
        val panelWidth = dp(226f)
        val verticalMargin = dp(32f)
        val isEmpty = recentApps.isEmpty()
        val contentHeight = if (isEmpty) {
            (dp(52f) - footerHeight).coerceAtLeast(0f)
        } else {
            recentApps.size * rowHeight
        }
        val maxListHeight = (height - verticalMargin - footerHeight).coerceAtLeast(rowHeight * 2f)
        val visibleListHeight = if (isEmpty) contentHeight else min(contentHeight, maxListHeight)
        val maxScrollOffset = (contentHeight - visibleListHeight).coerceAtLeast(0f)
        taskSwitcherScrollOffset = taskSwitcherScrollOffset.coerceIn(0f, maxScrollOffset)
        val panelHeight = visibleListHeight + footerHeight
        val trigger = activeTriggerZoneRect()
        val anchorY = taskSwitcherAnchorLocalY().coerceIn(trigger.top, trigger.bottom)
        var top = anchorY - min(rowHeight, visibleListHeight.coerceAtLeast(rowHeight)) / 2f
        top = top.coerceSafe(dp(16f), (height - panelHeight - dp(16f)).coerceAtLeast(dp(16f)))
        val gap = dp(10f)
        val left = when (side) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
        }
        val panelRect = RectF(left, top, left + panelWidth, top + panelHeight)
        val listRect = RectF(panelRect.left, panelRect.top, panelRect.right, panelRect.top + visibleListHeight)
        val closeAllRect = RectF(
            panelRect.left,
            panelRect.bottom - footerHeight,
            panelRect.right,
            panelRect.bottom,
        )
        val scrollOffset = taskSwitcherScrollOffset
        val rows = recentApps.mapIndexed { index, entry ->
            val rowTop = listRect.top + index * rowHeight - scrollOffset
            val rowRect = RectF(panelRect.left, rowTop, panelRect.right, rowTop + rowHeight)
            val closeRect = taskSwitcherCloseHitRect(rowRect)
            val freeWindowRect = taskSwitcherHandleColumnRect(rowRect)
            TaskSwitcherRowLayout(entry, rowRect, closeRect, freeWindowRect)
        }
        return TaskSwitcherPanelLayout(
            panelRect = panelRect,
            listRect = listRect,
            rows = rows,
            closeAllRect = closeAllRect,
            scrollOffset = scrollOffset,
            maxScrollOffset = maxScrollOffset,
        )
    }

    private fun taskSwitcherActionIconInset(): Float = dp(9.5f)

    private fun taskSwitcherActionSize(): Float = dp(30f)

    private fun taskSwitcherCloseEdgePadding(): Float = dp(5.5f)

    private fun taskSwitcherHitSlop(): Float = dp(2f)

    private fun taskSwitcherCloseColumnRect(rowRect: RectF): RectF {
        val size = taskSwitcherActionSize()
        val pad = taskSwitcherCloseEdgePadding()
        return when (side) {
            PanelSide.LEFT -> RectF(rowRect.left + pad, rowRect.top, rowRect.left + pad + size, rowRect.bottom)
            PanelSide.RIGHT -> RectF(rowRect.right - pad - size, rowRect.top, rowRect.right - pad, rowRect.bottom)
        }
    }

    private fun taskSwitcherHandleColumnRect(rowRect: RectF): RectF {
        val size = taskSwitcherActionSize()
        val centerX = taskSwitcherGripCenterX(rowRect)
        return RectF(centerX - size / 2f, rowRect.top, centerX + size / 2f, rowRect.bottom)
    }

    private fun taskSwitcherCloseIconRect(rowRect: RectF): RectF {
        val size = taskSwitcherActionSize()
        val column = taskSwitcherCloseColumnRect(rowRect)
        val cy = rowRect.centerY()
        return RectF(column.left, cy - size / 2f, column.right, cy + size / 2f)
    }

    private fun taskSwitcherCloseHitRect(rowRect: RectF): RectF {
        val column = taskSwitcherCloseColumnRect(rowRect)
        val slop = taskSwitcherHitSlop()
        val gapReach = dp(10f)
        return when (side) {
            PanelSide.LEFT -> RectF(column.left - slop - gapReach, column.top, column.right + slop, column.bottom)
            PanelSide.RIGHT -> RectF(column.left - slop, column.top, column.right + slop + gapReach, column.bottom)
        }
    }

    private fun taskSwitcherGripDotRadius(): Float = dp(1.65f)

    private fun taskSwitcherGripGapX(): Float = dp(3f)

    private fun taskSwitcherGripGapY(): Float = dp(3.6f)

    private fun taskSwitcherGripX(rowRect: RectF): Float {
        val inset = taskSwitcherActionIconInset()
        val radius = taskSwitcherGripDotRadius()
        val gapX = taskSwitcherGripGapX()
        return when (side) {
            PanelSide.LEFT -> rowRect.right - inset - gapX - radius
            PanelSide.RIGHT -> rowRect.left + inset + radius
        }
    }

    private fun taskSwitcherGripCenterX(rowRect: RectF): Float =
        taskSwitcherGripX(rowRect) + taskSwitcherGripGapX() / 2f

    private fun taskSwitcherIconLeft(row: TaskSwitcherRowLayout): Float {
        return when (side) {
            PanelSide.LEFT -> taskSwitcherCloseColumnRect(row.rowRect).right + dp(4f)
            PanelSide.RIGHT -> taskSwitcherHandleColumnRect(row.rowRect).right + dp(4f)
        }
    }

    private fun taskSwitcherLabelMaxWidth(row: TaskSwitcherRowLayout, labelX: Float): Float {
        return when (side) {
            PanelSide.LEFT -> taskSwitcherHandleColumnRect(row.rowRect).left - labelX - dp(8f)
            PanelSide.RIGHT -> taskSwitcherCloseColumnRect(row.rowRect).left - labelX - dp(6f)
        }.coerceAtLeast(dp(24f))
    }

    private fun drawGripDots(canvas: Canvas, x: Float, centerY: Float, paint: Paint) {
        val radius = taskSwitcherGripDotRadius()
        val gapY = taskSwitcherGripGapY()
        val gapX = taskSwitcherGripGapX()
        for (col in 0..1) {
            for (row in -1..1) {
                canvas.drawCircle(
                    x + col * gapX,
                    centerY + row * gapY,
                    radius,
                    paint,
                )
            }
        }
    }

    private fun drawCloseOrLockIcon(canvas: Canvas, rect: RectF, locked: Boolean, paint: Paint) {
        if (locked) drawLockIcon(canvas, rect, paint) else drawCloseIcon(canvas, rect, paint)
    }

    private fun drawCloseIcon(canvas: Canvas, rect: RectF, paint: Paint) {
        val inset = taskSwitcherActionIconInset()
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, paint)
        canvas.drawLine(rect.right - inset, rect.top + inset, rect.left + inset, rect.bottom - inset, paint)
    }

    private fun drawLockIcon(canvas: Canvas, rect: RectF, paint: Paint) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val bodyHalfWidth = dp(5f)
        val bodyHeight = dp(6.5f)
        val bodyTop = cy + dp(0.5f)
        val bodyBottom = bodyTop + bodyHeight
        val shackleTop = cy - dp(5.5f)
        val shackleBottom = bodyTop + dp(1f)
        paint.style = Paint.Style.STROKE
        canvas.drawArc(
            cx - bodyHalfWidth,
            shackleTop,
            cx + bodyHalfWidth,
            shackleBottom,
            180f,
            180f,
            false,
            paint,
        )
        canvas.drawRoundRect(
            cx - bodyHalfWidth,
            bodyTop,
            cx + bodyHalfWidth,
            bodyBottom,
            dp(1.2f),
            dp(1.2f),
            paint,
        )
    }

    private fun drawTaskSwitcherListHighlight(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
        layout: TaskSwitcherPanelLayout,
        panelCorner: Float,
        roundTopLeading: Boolean = false,
        roundTopTrailing: Boolean = false,
    ) {
        val bounds = RectF()
        if (!bounds.setIntersect(rect, layout.listRect)) return
        val atListTop = layout.scrollOffset <= 0.5f && bounds.top <= layout.listRect.top + 0.5f
        val topLeading = if (atListTop && roundTopLeading) panelCorner else 0f
        val topTrailing = if (atListTop && roundTopTrailing) panelCorner else 0f
        drawTaskSwitcherRoundedHighlight(canvas, bounds, paint, topLeading, topTrailing, 0f, 0f)
    }

    private fun drawTaskSwitcherFooterHighlight(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
        panelCorner: Float,
    ) {
        drawTaskSwitcherRoundedHighlight(
            canvas,
            rect,
            paint,
            topLeading = 0f,
            topTrailing = 0f,
            bottomTrailing = panelCorner,
            bottomLeading = panelCorner,
        )
    }

    private fun drawTaskSwitcherRoundedHighlight(
        canvas: Canvas,
        bounds: RectF,
        paint: Paint,
        topLeading: Float,
        topTrailing: Float,
        bottomTrailing: Float,
        bottomLeading: Float,
    ) {
        if (topLeading <= 0f && topTrailing <= 0f && bottomLeading <= 0f && bottomTrailing <= 0f) {
            canvas.drawRect(bounds, paint)
            return
        }
        highlightPath.rewind()
        highlightPath.addRoundRect(
            bounds,
            floatArrayOf(
                topLeading, topLeading,
                topTrailing, topTrailing,
                bottomTrailing, bottomTrailing,
                bottomLeading, bottomLeading,
            ),
            Path.Direction.CW,
        )
        canvas.drawPath(highlightPath, paint)
    }

    private fun drawElevatedRoundRect(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        fillColor: Int,
    ) {
        val shadowBlur = dp(3f)
        val shadowLayers = 3
        val shadowAlpha = 34
        for (layer in shadowLayers downTo 1) {
            val fraction = layer / shadowLayers.toFloat()
            val spread = shadowBlur * fraction * 0.8f
            val alpha = (shadowAlpha * fraction * fraction / shadowLayers).toInt().coerceIn(1, 255)
            elevatedShadowPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawRoundRect(
                rect.left - spread,
                rect.top - spread,
                rect.right + spread,
                rect.bottom + spread,
                cornerRadius + spread * 0.2f,
                cornerRadius + spread * 0.2f,
                elevatedShadowPaint,
            )
        }
        elevatedCardPaint.color = fillColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, elevatedCardPaint)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun <T> drawUtilityGrid(
        canvas: Canvas,
        title: String,
        entries: List<T>,
        gridRect: RectF? = null,
        clearCellBounds: Boolean = true,
        drawOnTopIndex: Int = -1,
        cellOffset: (Int) -> Pair<Float, Float> = { 0f to 0f },
        drawCell: (T, RectF, Int) -> Unit,
    ) {
        if (entries.isEmpty()) return
        val m = appsPerRow()
        val appCount = entries.size
        val layout = gridLayoutInfo(appCount)
        val grid = gridRect ?: utilityPanelRect(layout.panelWidth, layout.rows)
        panelContentRect.set(grid)
        if (clearCellBounds) {
            panelGridSession.cellBounds.clear()
        }
        panelBgPaint.color = Color.argb((225 * settings.panelOpacity).toInt().coerceIn(150, 225), 48, 48, 52)
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        letterPaint.textAlign = Paint.Align.LEFT
        letterPaint.color = Color.WHITE
        letterPaint.textSize = sp(14f)
        letterPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, grid.left + gridPadding, grid.top + dp(18f), letterPaint)
        fun drawEntryAt(index: Int) {
            val entry = entries[index]
            val row = index / m
            val visualCol = visualColumn(index, m, appCount)
            val left = grid.left + gridPadding + visualCol * cellWidth
            val top = grid.top + dp(28f) + gridPadding + row * cellHeight
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            val (offsetX, offsetY) = cellOffset(index)
            if (offsetX != 0f || offsetY != 0f) {
                canvas.save()
                canvas.translate(offsetX, offsetY)
            }
            drawCell(entry, cell, index)
            if (offsetX != 0f || offsetY != 0f) {
                canvas.restore()
            }
        }
        entries.indices.forEach { index ->
            if (index == drawOnTopIndex) return@forEach
            drawEntryAt(index)
        }
        if (drawOnTopIndex in entries.indices) {
            drawEntryAt(drawOnTopIndex)
        }
        letterPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawGridCell(
        canvas: Canvas,
        cell: RectF,
        index: Int,
        label: String,
        iconProvider: () -> Bitmap?,
        longPressArmed: Boolean = false,
        iconSize: Float = gridIconSize,
        iconTopInset: Float = gridIconTopInset,
        iconLabelGap: Float = gridIconLabelGap,
        labelMaxWidth: Float = cellWidth - gridCellInset * 2,
    ) {
        if (index == panelGridSession.highlightedIndex) {
            tmpRect.set(cell.left + dp(3f), cell.top + dp(2f), cell.right - dp(3f), cell.bottom - dp(2f))
            val paint = if (longPressArmed) cellLongPressHighlightPaint else cellHighlightPaint
            canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), paint)
        }
        val icon = iconProvider()
        val iconTop = cell.top + iconTopInset
        val displayLabel = ellipsize(label, labelMaxWidth)
        val labelBaseline = iconTop + iconSize + iconLabelGap - appLabelPaint.fontMetrics.ascent
        val iconCenterX = cell.centerX()
        if (icon != null) {
            tmpRect.set(
                iconCenterX - iconSize / 2f,
                iconTop,
                iconCenterX + iconSize / 2f,
                iconTop + iconSize,
            )
            canvas.drawBitmap(icon, null, tmpRect, iconBitmapPaint)
        } else {
            tmpRect.set(
                iconCenterX - iconSize / 2f,
                iconTop,
                iconCenterX + iconSize / 2f,
                iconTop + iconSize,
            )
            canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), cellHighlightPaint)
            val initial = displayLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
            cellInitialPaint.textSize = sp(14f)
            canvas.drawText(
                initial,
                iconCenterX,
                iconTop + iconSize / 2f - (cellInitialPaint.descent() + cellInitialPaint.ascent()) / 2f,
                cellInitialPaint,
            )
        }
        canvas.drawText(displayLabel, iconCenterX, labelBaseline, appLabelPaint)
    }

    private fun utilityPanelRect(panelWidth: Float, rows: Int): RectF {
        val gh = rows * cellHeight + gridPadding * 2 + dp(28f)
        val gw = panelWidth
        val rail = zoneLayout.indexRailRect()
        var top = rail.centerY() - gh / 2f
        top = top.coerceSafe(dp(16f), height - gh - dp(16f))
        val gap = dp(8f)
        val left = when (side) {
            PanelSide.LEFT -> rail.right + gap
            PanelSide.RIGHT -> rail.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    private fun resolveTaskSwitcherAnchorLocalY(): Float {
        val rawY = taskSwitcherAnchorRawY ?: pathRecognizer.gestureStartRawY()
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val anchorY = rawY - loc[1]
        val trigger = activeTriggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun taskSwitcherAnchorLocalY(): Float =
        taskSwitcherFrozenAnchorLocalY ?: resolveTaskSwitcherAnchorLocalY()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun invalidateTaskSwitcherPanel() {
        if (gestureSession.panelMode() == OverlayPanelMode.TASK_SWITCHER) {
            invalidate()
        }
    }

    private fun indexPanelContentRect(): RectF {
        val rail = zoneLayout.indexRailRect()
        if (indexSession.selectedLetter == null) {
            return when (side) {
                PanelSide.LEFT -> RectF(rail.left, rail.top, rail.right + dp(240f), rail.bottom)
                PanelSide.RIGHT -> RectF(rail.left - dp(240f), rail.top, rail.right, rail.bottom)
            }
        }
        val grid = gridPopupRect()
        val bubble = bubbleCenter()
        return RectF(
            minOf(rail.left, grid.left, bubble.x - bubbleRadius),
            minOf(rail.top, grid.top, bubble.y - bubbleRadius),
            maxOf(rail.right, grid.right, bubble.x + bubbleRadius),
            maxOf(rail.bottom, grid.bottom, bubble.y + bubbleRadius),
        )
    }

    private fun quickLauncherPageSize(): Int =
        quickLauncherColumnsPerPage() * quickLauncherRowsPerPage()

    private fun quickLauncherPagination(): Triple<Int, Int, Int> {
        val pages = quickLauncherPages()
        val pageSize = quickLauncherPageSize()
        val pageCount = pages.size
        val pageStart = quickLauncherPageIndex * pageSize
        return Triple(pageStart, pageSize, pageCount)
    }

    private fun quickLauncherPagedItems(): List<QuickLauncherItem> {
        val (pageStart, pageSize, _) = quickLauncherPagination()
        quickLauncherPanelController.setItemPageOffset(pageStart)
        return quickLauncherRootItems().drop(pageStart).take(pageSize)
    }

    private fun quickLauncherItemsForPage(pageIndex: Int): List<QuickLauncherItem> {
        val pages = quickLauncherPages()
        val clampedPage = pageIndex.coerceIn(0, pages.size - 1)
        val pageStart = clampedPage * quickLauncherPageSize()
        if (clampedPage == quickLauncherPageIndex) {
            quickLauncherPanelController.setItemPageOffset(pageStart)
        }
        return pages.getOrElse(clampedPage) { emptyList() }
    }

    private fun endQuickLauncherSessionAnimated() {
        if (quickLauncherExiting) return
        if (gestureSession.panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            gestureSession.endSession()
            return
        }
        quickLauncherExiting = true
        startPanelExitAnimation {
            quickLauncherExiting = false
            gestureSession.endSession()
        }
    }

    private fun quickLauncherPanelRect(): RectF {
        val layout = quickLauncherGridLayoutInfo()
        quickLauncherPagination()
        val base = anchoredQuickLauncherPanelRect(layout.panelWidth, layout.rows)
        return offsetQuickLauncherPanelForToolbar(base)
    }

    private fun anchoredQuickLauncherPanelRect(panelWidth: Float, rows: Int): RectF {
        val gh = quickLauncherPanelContentHeight(rows)
        val gw = panelWidth
        val trigger = activeTriggerZoneRect()
        val anchorY = quickLauncherAnchorLocalY().coerceIn(trigger.top, trigger.bottom)
        var top = anchorY - gh / 2f
        top = top.coerceSafe(dp(16f), height - gh - dp(16f))
        val gap = dp(8f)
        val left = when (side) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    private fun offsetQuickLauncherPanelForToolbar(panelRect: RectF): RectF {
        if (panelRect.isEmpty) return panelRect
        val margin = dp(16f)
        var left = panelRect.left
        var right = panelRect.right
        val reserve = quickLauncherPanelController.contentReserveWidth(settings)
        return when (side) {
            PanelSide.LEFT -> {
                // Keep the grid on-screen; do not shift it left to make room for the toolbar.
                if (left < margin) {
                    val delta = margin - left
                    left += delta
                    right += delta
                }
                if (right > width - margin) {
                    val delta = right - (width - margin)
                    left -= delta
                    right = width - margin
                    left = left.coerceAtLeast(margin)
                }
                RectF(left, panelRect.top, right, panelRect.bottom)
            }
            PanelSide.RIGHT -> {
                val combinedLeft = left - reserve
                if (combinedLeft < margin) {
                    val delta = margin - combinedLeft
                    left += delta
                    right += delta
                }
                if (right > width - margin) {
                    val delta = right - (width - margin)
                    left -= delta
                    right = width - margin
                    left = left.coerceAtLeast(margin)
                }
                RectF(left, panelRect.top, right, panelRect.bottom)
            }
        }
    }

    private fun anchoredUtilityPanelRect(panelWidth: Float, rows: Int): RectF {
        val gh = rows * cellHeight + gridPadding * 2 + dp(28f)
        val gw = panelWidth
        val trigger = activeTriggerZoneRect()
        val anchorY = quickLauncherAnchorLocalY().coerceIn(trigger.top, trigger.bottom)
        var top = anchorY - gh / 2f
        top = top.coerceSafe(dp(16f), height - gh - dp(16f))
        val gap = dp(8f)
        val left = when (side) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    private fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit) {
        if (panelEnterProgress >= 1f || contentRect.isEmpty) {
            drawContent()
            return
        }
        val offsetX = panelEnterOffsetX(contentRect)
        val alpha = (255 * panelEnterProgress).toInt().coerceIn(0, 255)
        val layer = canvas.saveLayerAlpha(null, alpha)
        canvas.translate(offsetX, 0f)
        drawContent()
        canvas.restoreToCount(layer)
    }

    private fun panelEnterOffsetX(panel: RectF): Float {
        val delta = 1f - panelEnterProgress
        val slide = panel.width() + dp(PANEL_ENTER_OFFSCREEN_MARGIN_DP)
        return when (side) {
            PanelSide.LEFT -> -slide * delta
            PanelSide.RIGHT -> slide * delta
        }
    }

    private fun panelEnterAdjustedX(localX: Float, panel: RectF): Float =
        if (panelEnterProgress >= 1f || panel.isEmpty) localX else localX - panelEnterOffsetX(panel)

    private fun cancelPanelEnterAnimation() {
        panelEnterAnimator?.cancel()
        panelEnterAnimator = null
    }

    private fun startPanelEnterAnimation() {
        cancelPanelEnterAnimation()
        panelEnterProgress = 0f
        val duration = when (gestureSession.panelMode()) {
            OverlayPanelMode.SHELL_COMMANDS -> SHELL_PANEL_ENTER_DURATION_MS
            else -> PANEL_ENTER_DURATION_MS
        }
        panelEnterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                panelEnterProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS) {
                        syncShellPanelInputFocus()
                    }
                    if (gestureSession.panelMode() == OverlayPanelMode.QUICK_LAUNCHER) {
                        notifyPresentationTouchRequirementChanged()
                        if (!gestureSession.isMoveTimeActionLocked() ||
                            gestureSession.quickLauncherContinuousPickActive()
                        ) {
                            quickLauncherOpeningGestureActive = false
                        }
                    }
                }
            })
            start()
        }
        if (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS) {
            syncShellPanelInputFocus()
        }
        invalidate()
    }

    private fun startPanelExitAnimation(onEnd: () -> Unit) {
        cancelPanelEnterAnimation()
        if (panelEnterProgress <= 0.01f) {
            panelEnterProgress = 0f
            onEnd()
            return
        }
        panelEnterAnimator = ValueAnimator.ofFloat(panelEnterProgress, 0f).apply {
            duration = when (gestureSession.panelMode()) {
                OverlayPanelMode.SHELL_COMMANDS -> SHELL_PANEL_ENTER_DURATION_MS
                else -> PANEL_ENTER_DURATION_MS
            }
            interpolator = AccelerateInterpolator()
            addUpdateListener { animator ->
                panelEnterProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    panelEnterProgress = 0f
                    onEnd()
                }
            })
            start()
        }
        invalidate()
    }

    private fun endTaskSwitcherSession(
        runBeforeExit: Boolean = false,
        runAfter: (() -> Unit)? = null,
    ) {
        if (taskSwitcherExiting) return
        dismissTaskSwitcherContextMenu(immediate = true)
        taskSwitcherExiting = true
        runAfter?.invoke()
        if (runBeforeExit) {
            taskSwitcherExiting = false
            gestureSession.endSession()
            return
        }
        startPanelExitAnimation {
            taskSwitcherExiting = false
            gestureSession.endSession()
        }
    }

    companion object {
        private const val TASK_SWITCHER_LONG_PRESS_MS = 650L
        private const val TASK_SWITCHER_MENU_ENTER_MS = 200L
        private const val TASK_SWITCHER_MENU_EXIT_MS = 160L
        private const val TASK_SWITCHER_CLOSE_DWELL_MS = 750L
        private const val TASK_SWITCHER_CLOSE_CONTINUOUS_DWELL_MS = 1_100L
        private const val TASK_SWITCHER_SCROLL_SLOP_DP = 8f
        private const val TASK_SWITCHER_OVERSCROLL_MAX_DP = 52f
        private const val TASK_SWITCHER_OVERSCROLL_RESISTANCE = 0.36f
        private const val TASK_SWITCHER_OVERSCROLL_STRETCH = 0.22f
        private const val TASK_SWITCHER_OVERSCROLL_RELEASE_MS = 280L
        private const val PANEL_ENTER_DURATION_MS = 180L
        private const val SHELL_PANEL_ENTER_DURATION_MS = 260L
        private const val PANEL_ENTER_OFFSCREEN_MARGIN_DP = 16f
        private const val QUICK_LAUNCHER_MAX_ROWS = 6
        private const val QUICK_LAUNCHER_PAGE_SWIPE_DIRECTION_LOCK_DP = 8f
        private const val QUICK_LAUNCHER_PAGE_COMMIT_FRACTION = 0.22f
        private const val QUICK_LAUNCHER_PAGE_EDGE_RESISTANCE = 0.35f
        private const val QUICK_LAUNCHER_PAGE_SNAP_DURATION_MS = 180L
        private const val QUICK_LAUNCHER_EDGE_AUTO_PAGE_THRESHOLD_DP = 14f
        private const val ADJUST_INDICATOR_ENTER_MS = 220L
        private const val ADJUST_INDICATOR_EXIT_MS = 160L
        private const val LEVEL_SYNC_EPSILON = 0.002f
        private const val VOLUME_DRAG_SPAN_SCREEN_FRACTION = 0.5f
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
