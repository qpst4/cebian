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
import android.view.ViewTreeObserver
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
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.OverlayService
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
    private val onOverlayPresentationSuspend: () -> Unit = {},
    private val onOverlayPresentationResume: () -> Unit = {},
    private val onShellPanelAuxiliaryPrepare: () -> Unit = {},
    private val onShellPanelAuxiliaryDismiss: () -> Unit = {},
    overlayBrightness: OverlayBrightnessControl? = null,
) : View(context), IndexSessionHost, GestureSession.Callbacks {

    init {
        isClickable = true
        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }
            shellCoordinator.handleBackPress()
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
    private val shellCoordinator = ShellPanelOverlayController(
        object : ShellPanelOverlayController.Host {
            override val context: Context get() = this@EdgeGestureOverlayView.context
            override fun settings(): AppSettings = settings
            override fun gestureSession(): GestureSession = gestureSession
            override fun panelEnterProgress(): Float = panelEnterProgress
            override fun viewWidth(): Int = width
            override fun viewHeight(): Int = height
            override fun dp(value: Float): Float = this@EdgeGestureOverlayView.dp(value)
            override fun sp(value: Float): Float = this@EdgeGestureOverlayView.sp(value)
            override fun invalidate() = this@EdgeGestureOverlayView.invalidate()
            override fun post(action: () -> Unit) {
                this@EdgeGestureOverlayView.post(action)
            }
            override fun requestFocus() {
                this@EdgeGestureOverlayView.requestFocus()
            }
            override fun hapticTick() = HapticHelper.appTick(this@EdgeGestureOverlayView, settings)
            override fun hapticConfirm() = HapticHelper.confirmLaunch(this@EdgeGestureOverlayView, settings)
            override fun startPanelExitAnimation(onEnd: () -> Unit) =
                this@EdgeGestureOverlayView.startPanelExitAnimation(onEnd)
            override fun notifyPresentationTouchRequirementChanged() =
                this@EdgeGestureOverlayView.notifyPresentationTouchRequirementChanged()
            override fun onShellCommandsPersist(commands: List<ShellCommand>) {
                this@EdgeGestureOverlayView.onShellCommandsPersist(commands)
            }
            override fun onShellPanelFocusChange(needsFocus: Boolean) {
                this@EdgeGestureOverlayView.onShellPanelFocusChange(needsFocus)
            }
            override fun onOverlayWindowSuspend() {
                this@EdgeGestureOverlayView.onOverlayWindowSuspend()
            }
            override fun onOverlayWindowResume() {
                this@EdgeGestureOverlayView.onOverlayWindowResume()
            }
            override fun onShellPanelAuxiliaryPrepare() {
                this@EdgeGestureOverlayView.onShellPanelAuxiliaryPrepare()
            }
            override fun onShellPanelAuxiliaryDismiss() {
                this@EdgeGestureOverlayView.onShellPanelAuxiliaryDismiss()
            }
            override fun clearEdgeCaptureTouchActive() {
                edgeCaptureTouchActive = false
            }
        },
    )
    private val quickLauncherController = QuickLauncherOverlayController(
        object : QuickLauncherOverlayController.Host {
            override val context: Context get() = this@EdgeGestureOverlayView.context
            override fun settings(): AppSettings = settings
            override fun side(): PanelSide = side
            override fun apps(): List<AppInfo> = apps
            override fun gestureSession(): GestureSession = gestureSession
            override fun zoneLayout(): GestureZoneLayout = zoneLayout
            override fun pathRecognizer(): SwipePathRecognizer = pathRecognizer
            override fun actionExecutor(): ActionExecutor = actionExecutor
            override fun panelGridSession(): PanelGridSession = panelGridSession
            override fun panelEnterProgress(): Float = panelEnterProgress
            override fun panelEnterAdjustedX(localX: Float, panel: RectF): Float =
                this@EdgeGestureOverlayView.panelEnterAdjustedX(localX, panel)
            override fun panelEnterOffsetX(panel: RectF): Float =
                this@EdgeGestureOverlayView.panelEnterOffsetX(panel)
            override fun panelContentRect(): RectF = panelContentRect
            override fun drawWithPanelEnterAnimation(
                canvas: Canvas,
                contentRect: RectF,
                drawContent: () -> Unit,
            ) = this@EdgeGestureOverlayView.drawWithPanelEnterAnimation(canvas, contentRect, drawContent)
            override fun activeTriggerZoneRect(): RectF = this@EdgeGestureOverlayView.activeTriggerZoneRect()
            override fun viewWidth(): Int = width
            override fun viewHeight(): Int = height
            override fun dp(value: Float): Float = this@EdgeGestureOverlayView.dp(value)
            override fun sp(value: Float): Float = this@EdgeGestureOverlayView.sp(value)
            override fun viewLocationOnScreen(): IntArray = IntArray(2).also { getLocationOnScreen(it) }
            override fun invalidate() = this@EdgeGestureOverlayView.invalidate()
            override fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int) =
                this@EdgeGestureOverlayView.invalidate(left, top, right, bottom)
            override fun post(action: () -> Unit) {
                this@EdgeGestureOverlayView.post(action)
            }
            override fun postDelayed(runnable: Runnable, delayMs: Long) {
                this@EdgeGestureOverlayView.postDelayed(runnable, delayMs)
            }
            override fun removeCallbacks(runnable: Runnable) {
                this@EdgeGestureOverlayView.removeCallbacks(runnable)
            }
            override fun hapticTick() = HapticHelper.appTick(this@EdgeGestureOverlayView, settings)
            override fun hapticLongThreshold() = HapticHelper.longThreshold(this@EdgeGestureOverlayView, settings)
            override fun hapticConfirmLaunch() = HapticHelper.confirmLaunch(this@EdgeGestureOverlayView, settings)
            override fun startPanelExitAnimation(onEnd: () -> Unit) =
                this@EdgeGestureOverlayView.startPanelExitAnimation(onEnd)
            override fun notifyPresentationTouchRequirementChanged() =
                this@EdgeGestureOverlayView.notifyPresentationTouchRequirementChanged()
            override fun onQuickLauncherItemsPersist(items: List<QuickLauncherItem>) {
                this@EdgeGestureOverlayView.onQuickLauncherItemsPersist(items)
            }
            override fun onOverlayWindowSuspend() {
                this@EdgeGestureOverlayView.onOverlayWindowSuspend()
            }
            override fun onOverlayWindowResume() {
                this@EdgeGestureOverlayView.onOverlayWindowResume()
            }
        },
    )
    private val indexPanelRenderer = IndexPanelRenderer(
        object : IndexPanelRenderer.Host {
            override val context: Context get() = this@EdgeGestureOverlayView.context
            override fun settings(): AppSettings = settings
            override fun side(): PanelSide = side
            override fun zoneLayout(): GestureZoneLayout = zoneLayout
            override fun indexSession(): SlideAlongRailSession = indexSession
            override fun gestureSession(): GestureSession = gestureSession
            override fun dp(value: Float): Float = this@EdgeGestureOverlayView.dp(value)
            override fun sp(value: Float): Float = this@EdgeGestureOverlayView.sp(value)
            override fun viewHeight(): Int = height
            override fun panelEnterAdjustedX(localX: Float, panel: RectF): Float =
                this@EdgeGestureOverlayView.panelEnterAdjustedX(localX, panel)
            override fun invalidate() = this@EdgeGestureOverlayView.invalidate()
            override fun iconFor(app: AppInfo): Bitmap = this@EdgeGestureOverlayView.iconFor(app)
        },
    )
    private val adjustPanelController = AdjustPanelOverlayController(
        object : AdjustPanelOverlayController.Host {
            override val context: Context get() = this@EdgeGestureOverlayView.context
            override fun side(): PanelSide = side
            override fun settings(): AppSettings = settings
            override fun actionExecutor(): ActionExecutor = actionExecutor
            override fun gestureSession(): GestureSession = gestureSession
            override fun viewWidth(): Int = width
            override fun viewHeight(): Int = height
            override fun density(): Float = resources.displayMetrics.density
            override fun screenWidthPx(): Int = resources.displayMetrics.widthPixels
            override fun screenHeightPx(): Int = resources.displayMetrics.heightPixels
            override fun viewLocationOnScreen(): IntArray = IntArray(2).also { getLocationOnScreen(it) }
            override fun anchorLocalY(rawY: Float): Float = rawToLocal(0f, rawY).second
            override fun dp(value: Float): Float = this@EdgeGestureOverlayView.dp(value)
            override fun invalidate() = this@EdgeGestureOverlayView.invalidate()
            override fun post(action: () -> Unit) {
                this@EdgeGestureOverlayView.post(action)
            }
            override fun runAfterLayout(block: () -> Unit) = this@EdgeGestureOverlayView.runAfterLayout(block)
            override fun hapticConfirmLaunch() = HapticHelper.confirmLaunch(this@EdgeGestureOverlayView, settings)
            override fun onAdjustPanelDismiss() = onAdjustPanelDismissCallback()
            override fun onSessionStart() = onSessionStartCallback()
            override fun notifyOverlayLayoutIfNeeded() = this@EdgeGestureOverlayView.notifyOverlayLayoutIfNeeded()
            override fun notifyPresentationTouchRequirementChanged() =
                this@EdgeGestureOverlayView.notifyPresentationTouchRequirementChanged()
        },
    )
    private val taskSwitcherController = TaskSwitcherOverlayController(
        object : TaskSwitcherOverlayController.Host {
            override val context: Context get() = this@EdgeGestureOverlayView.context
            override fun settings(): AppSettings = settings
            override fun side(): PanelSide = side
            override fun appRepository(): AppRepository = appRepository
            override fun gestureSession(): GestureSession = gestureSession
            override fun zoneLayout(): GestureZoneLayout = zoneLayout
            override fun pathRecognizer(): SwipePathRecognizer = pathRecognizer
            override fun actionExecutor(): ActionExecutor = actionExecutor
            override fun panelEnterProgress(): Float = panelEnterProgress
            override fun panelEnterAdjustedX(localX: Float, panel: RectF): Float =
                this@EdgeGestureOverlayView.panelEnterAdjustedX(localX, panel)
            override fun drawWithPanelEnterAnimation(
                canvas: Canvas,
                contentRect: RectF,
                drawContent: () -> Unit,
            ) = this@EdgeGestureOverlayView.drawWithPanelEnterAnimation(canvas, contentRect, drawContent)
            override fun panelContentRect(): RectF = panelContentRect
            override fun activeTriggerZoneRect(): RectF = this@EdgeGestureOverlayView.activeTriggerZoneRect()
            override fun viewWidth(): Int = width
            override fun viewHeight(): Int = height
            override fun dp(value: Float): Float = this@EdgeGestureOverlayView.dp(value)
            override fun sp(value: Float): Float = this@EdgeGestureOverlayView.sp(value)
            override fun density(): Float = resources.displayMetrics.density
            override fun viewLocationOnScreen(): IntArray = IntArray(2).also { getLocationOnScreen(it) }
            override fun invalidate() = this@EdgeGestureOverlayView.invalidate()
            override fun post(action: () -> Unit) {
                this@EdgeGestureOverlayView.post(action)
            }
            override fun postDelayed(runnable: Runnable, delayMs: Long) {
                this@EdgeGestureOverlayView.postDelayed(runnable, delayMs)
            }
            override fun removeCallbacks(runnable: Runnable) {
                this@EdgeGestureOverlayView.removeCallbacks(runnable)
            }
            override fun hapticTick() = HapticHelper.appTick(this@EdgeGestureOverlayView, settings)
            override fun hapticConfirmLaunch() = HapticHelper.confirmLaunch(this@EdgeGestureOverlayView, settings)
            override fun iconFor(app: AppInfo): Bitmap = this@EdgeGestureOverlayView.iconFor(app)
            override fun startPanelExitAnimation(onEnd: () -> Unit) =
                this@EdgeGestureOverlayView.startPanelExitAnimation(onEnd)
        },
    )

    private var panelContentRect = RectF()
    private var panelEnterProgress = 1f
    private var lastAdjustInvalidateMs = 0L
    private var panelEnterAnimator: ValueAnimator? = null
    /** True after this capture stream consumed ACTION_DOWN; keeps UP/MOVE consistent for IMMEDIATE actions. */
    private var edgeCaptureTouchActive = false
    private val gestureAnimationOverlay
        get() = GestureAnimationOverlayRegistry.controller(side)

    private val railLetters: List<Char> = ('A'..'Z').toList() + '#'
    private val iconCache = mutableMapOf<String, Bitmap>()

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
    private val gridIconTopInset get() = dp(6f)
    private val gridIconLabelGap get() = dp(3f)
    private val gridCellInset get() = dp(4f)
    private val bubbleRadius get() = dp(24f)
    private val bubblePanelGap get() = dp(10f)
    private val railCorner get() = dp(14f)
    private val panelCorner get() = dp(18f)



    fun applySettings(newSettings: AppSettings, screenWidth: Int) {
        settings = newSettings
        shellCoordinator.syncSettings(newSettings)
        quickLauncherController.syncSettings(newSettings)
        indexPanelRenderer.syncSettings(newSettings)
        gestureSession.applySettings(newSettings)
        quickLauncherController.invalidateDerivedCaches()
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        gestureAnimationOverlay.applySettings(newSettings)
        syncZoneLayout()
        invalidate()
    }

    fun dispatchExternalAction(action: GestureAction, anchorRawY: Float): Boolean {
        applyExpandedOverlayLayout()
        runAfterLayout {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
            val viewHeight = if (height > 0) height.toFloat() else screenHeight
            val localY = (anchorRawY - loc[1]).coerceIn(0f, viewHeight)
            val screenWidth = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val localX = if (width > 0) width / 2f else screenWidth / 2f
            val anchorRawX = if (width > 0) loc[0] + localX else screenWidth / 2f
            when (action) {
                is GestureAction.TaskSwitcher -> taskSwitcherController.setExternalAnchor(anchorRawY)
                is GestureAction.QuickLauncher -> quickLauncherController.setAnchorRawY(anchorRawY)
                else -> Unit
            }
            gestureSession.openDiscretePanel(action, localX, localY, anchorRawX, anchorRawY)
            notifyPresentationTouchRequirementChanged()
            invalidate()
        }
        return true
    }

    private fun runAfterLayout(block: () -> Unit) {
        if (isAttachedToWindow && width > 0 && height > 0) {
            block()
            return
        }
        val observer = viewTreeObserver
        if (!observer.isAlive) {
            post { runAfterLayout(block) }
            return
        }
        observer.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (width <= 0 || height <= 0) return
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    block()
                }
            },
        )
        requestLayout()
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
        if (adjustPanelController.hasAdjustPanel()) return true
        if (gestureSession.panelMode() != OverlayPanelMode.NONE) return true
        if (quickLauncherController.isOverlayDialogShowing() ||
            shellCoordinator.isAuxiliaryDialogShowing()
        ) return true
        return false
    }

    /** Keep the presentation layer touch-passthrough while a compose overlay dialog is on top. */
    fun presentationShouldPassthroughTouches(): Boolean =
        QuickLauncherAddTrampoline.isActive() ||
            shellCoordinator.isAuxiliaryDialogShowing() ||
            quickLauncherController.isOverlayDialogShowing()

    private fun composeOverlayDialogShowing(): Boolean =
        QuickLauncherAddTrampoline.isActive() ||
            shellCoordinator.isAuxiliaryDialogShowing() ||
            shellCoordinator.isPanelTrampolineBlockingPassthrough() ||
            quickLauncherController.isOverlayDialogShowing()

    fun syncOverlayDialogZOrder() {
        quickLauncherController.syncOverlayDialogZOrder()
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
        if (adjustPanelController.hasAdjustPanel() && !gestureSession.isActive()) {
            if (adjustPanelController.handleTouch(event, localX, localY)) return true
        }
        when (gestureSession.panelMode()) {
            OverlayPanelMode.QUICK_LAUNCHER ->
                return quickLauncherController.handleTouch(event, localX, localY)
            OverlayPanelMode.SHELL_COMMANDS ->
                return shellCoordinator.handleTouch(event, localX, localY)
            OverlayPanelMode.TASK_SWITCHER ->
                return taskSwitcherController.handleTouch(event, localX, localY)
            OverlayPanelMode.INDEX -> return indexPanelRenderer.handleTouch(event, localX, localY)
            OverlayPanelMode.NONE -> Unit
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    adjustPanelController.hasAdjustPanel() && !gestureSession.isActive() && !adjustPanelController.isDismissing() ->
                        forceRecoverInteractionState()
                    gestureSession.panelMode() != OverlayPanelMode.NONE && !gestureSession.isActive() ->
                        gestureSession.forceReset(notifySessionEnd = true)
                    gestureSession.isActive() -> {
                        shellCoordinator.closePanelTrampolineIfContinuous()
                        gestureSession.forceReset(notifySessionEnd = false)
                    }
                }
                if (gestureSession.onTouchDown(event.rawX, event.rawY, localX, localY)) {
                    FloatingPointerAreaPreviewOverlay.onEdgeTriggerTouch(event.rawX, event.rawY)
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
                if (!gestureSession.isActive()) {
                    dismissGestureAnimationForFloatingPointerHandoff()
                    FloatingPointerOverlayWindow.forwardContinuedTouch(event)
                    return true
                }
                forEachGesturePoint(event, localX, localY, includeHistory = true) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    if (FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch()) {
                        dismissGestureAnimationForFloatingPointerHandoff()
                    } else {
                        updateOrDismissGestureAnimation(rawX, rawY)
                    }
                }
                if (FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch()) {
                    dismissGestureAnimationForFloatingPointerHandoff()
                    FloatingPointerOverlayWindow.forwardContinuedTouch(event)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureSession.isActive()) {
                    val consumed = edgeCaptureTouchActive
                    val handoff = FloatingPointerOverlayWindow.forwardContinuedTouch(event)
                    if (consumed) {
                        edgeCaptureTouchActive = false
                        finishGestureAnimationIfNeeded()
                    }
                    return consumed || handoff || event.actionMasked == MotionEvent.ACTION_CANCEL
                }
                edgeCaptureTouchActive = false
                val canceled = event.actionMasked == MotionEvent.ACTION_CANCEL
                forEachGesturePoint(event, localX, localY, includeHistory = true) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    updateGestureAnimationIfNeeded(rawX, rawY)
                }
                finishGestureAnimationIfNeeded()
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                FloatingPointerOverlayWindow.forwardContinuedTouch(event)
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

    fun hasAdjustPanel(): Boolean = adjustPanelController.hasAdjustPanel()

    fun keepsOverlayExpanded(): Boolean =
        gestureSession.isActive() ||
            gestureSession.panelMode() != OverlayPanelMode.NONE ||
            adjustPanelController.hasAdjustPanel() ||
            (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS &&
                shellCoordinator.hasActiveUi())

    /** Clears stuck gesture/panel state without re-entering session callbacks. */
    fun forceRecoverInteractionState() {
        if (adjustPanelController.isDismissing()) return
        shellCoordinator.closePanelTrampolineIfActive()
        shellCoordinator.clearShellContinuousPick()
        gestureAnimationOverlay.hide()
        edgeCaptureTouchActive = false
        adjustPanelController.forceRecover()
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
        quickLauncherController.setApps(newApps)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        quickLauncherController.onSizeChanged()
        syncZoneLayout()
        adjustPanelController.onSizeChanged()
    }

    override fun onDetachedFromWindow() {
        gestureAnimationOverlay.hide()
        adjustPanelController.onDetachedFromWindow()
        GestureActionIconBitmap.clear()
        super.onDetachedFromWindow()
    }



    private fun notifyOverlayLayoutIfNeeded() {
        if (!keepsOverlayExpanded() && !gestureSession.isActive()) {
            onSessionEndCallback()
        }
    }

    fun showAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        @Suppress("UNUSED_PARAMETER") deferWindowLayout: Boolean = false,
    ) {
        adjustPanelController.showAdjustPanel(mode, fraction, anchorRawY)
    }

    private fun activeTriggerZoneRect(): RectF =
        if (gestureSession.isActive()) {
            zoneLayout.triggerZoneRect(gestureSession.activeHandleId())
        } else {
            zoneLayout.triggerZoneUnionRect()
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
                LayoutPreviewContent.INDEX_ONLY -> indexPanelRenderer.drawLetterRail(canvas)
            }
            return
        }
        adjustPanelController.drawVisibleIndicator(canvas)
        if (!gestureSession.isActive() && !adjustPanelController.hasAdjustPanel()) return
        when (gestureSession.panelMode()) {
            OverlayPanelMode.INDEX -> {
                drawWithPanelEnterAnimation(canvas, indexPanelRenderer.indexPanelContentRect()) {
                    indexPanelRenderer.drawLetterRail(canvas)
                    if (indexSession.selectedLetter != null) {
                        indexPanelRenderer.drawAppGrid(canvas)
                        indexPanelRenderer.drawLetterBubble(canvas)
                    }
                }
            }
            OverlayPanelMode.QUICK_LAUNCHER -> {
                val contentRect = quickLauncherController.enterContentRect()
                drawWithPanelEnterAnimation(canvas, contentRect) {
                    quickLauncherController.draw(canvas)
                }
            }
            OverlayPanelMode.TASK_SWITCHER -> taskSwitcherController.draw(canvas)
            OverlayPanelMode.SHELL_COMMANDS -> shellCoordinator.draw(canvas, panelEnterProgress, panelContentRect)
            OverlayPanelMode.NONE -> adjustPanelController.syncAdjustIndicatorAnimation()
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

    override fun onOpenShellCommandPanel(continuousPick: Boolean) {
        shellCoordinator.onOpenShellCommandPanel(continuousPick)
    }

    override fun onShellCommandPanelContinuousRelease() {
        shellCoordinator.onShellCommandPanelContinuousRelease()
    }

    override fun onSessionStart(mode: OverlayPanelMode) {
        syncZoneLayout()
        cancelPanelEnterAnimation()
        when (mode) {
                        OverlayPanelMode.TASK_SWITCHER -> {
                panelEnterProgress = 0f
                taskSwitcherController.onSessionStart()
            }
            OverlayPanelMode.INDEX, OverlayPanelMode.QUICK_LAUNCHER,
            OverlayPanelMode.SHELL_COMMANDS -> {
                panelEnterProgress = 0f
                if (mode == OverlayPanelMode.SHELL_COMMANDS) {
                    shellCoordinator.onSessionStart()
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherController.onSessionStart()
                }
            }
            OverlayPanelMode.NONE -> {
                panelEnterProgress = 1f
                if (gestureSession.isAdjustMode()) {
                    adjustPanelController.onSessionStartAdjustMode()
                }
            }
        }
        panelGridSession.reset()
        onSessionStartCallback()
        notifyPresentationTouchRequirementChanged()
        if (mode != OverlayPanelMode.NONE || gestureSession.isAdjustMode()) {
            if (gestureAnimationOverlay.animationState?.isActive == true) {
                finishGestureAnimationIfNeeded()
            }
        }
        if (mode != OverlayPanelMode.NONE) {
            runAfterLayout {
                if (gestureSession.panelMode() != mode) return@runAfterLayout
                syncZoneLayout()
                if (mode == OverlayPanelMode.TASK_SWITCHER) {
                    taskSwitcherController.onLayoutReady()
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherController.onLayoutReady()
                }
                startPanelEnterAnimation()
            }
        }
    }


    override fun onSessionEnd() {
        cancelPanelEnterAnimation()
        gestureAnimationOverlay.hide()
        adjustPanelController.onSessionEnd()
        panelEnterProgress = 1f
        syncZoneLayout()
        panelGridSession.reset()
        taskSwitcherController.onSessionEnd()
        quickLauncherController.onSessionEnd()
        shellCoordinator.onSessionEnd()
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

    private fun gridLayoutInfo(appCount: Int): GridLayoutInfo =
        com.slideindex.app.overlay.gridLayoutInfo(appCount, appsPerRow(), cellWidth, gridPadding)

    private fun visualColumn(index: Int, m: Int, appCount: Int): Int =
        com.slideindex.app.overlay.visualColumn(index, m, appCount, side)

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
            val handle = settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
            if (handle.design.isVisible) {
                val glowWidth = zoneLayout.glowAwareEdgeWidthPx()
                canvas.save()
                val drawLeft = when (side) {
                    PanelSide.LEFT -> 0f
                    PanelSide.RIGHT -> zone.right - glowWidth
                }
                canvas.translate(drawLeft, zone.top)
                TriggerHandleRenderer.draw(
                    canvas = canvas,
                    side = side,
                    design = handle.design,
                    density = resources.displayMetrics.density,
                    widthPx = glowWidth,
                    heightPx = zone.height().toInt().coerceAtLeast(1),
                )
                canvas.restore()
            } else {
                triggerPreviewFillPaint.color = Color.argb(72, 255, 152, 0)
                canvas.drawRoundRect(zone, corner, corner, triggerPreviewFillPaint)
                triggerPreviewStrokePaint.color = Color.argb(210, 255, 167, 38)
                triggerPreviewStrokePaint.strokeWidth = dp(2f)
                canvas.drawRoundRect(zone, corner, corner, triggerPreviewStrokePaint)
            }
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

    private fun shouldDismissGestureAnimationDuringSession(): Boolean {
        if (!gestureSession.isActive()) return false
        return gestureSession.isMoveTimeActionLocked() ||
            gestureSession.isAdjustMode() ||
            gestureSession.panelMode() != OverlayPanelMode.NONE
    }

    private fun updateOrDismissGestureAnimation(rawX: Float, rawY: Float) {
        if (!settings.gestureHintEnabled) return
        if (shouldDismissGestureAnimationDuringSession()) {
            if (gestureAnimationOverlay.animationState?.isActive == true) {
                finishGestureAnimationIfNeeded()
            }
            return
        }
        updateGestureAnimationIfNeeded(rawX, rawY)
    }

    private fun finishGestureAnimationIfNeeded() {
        if (!settings.gestureHintEnabled) return
        gestureAnimationOverlay.animationState?.onDragEnd()
        gestureAnimationOverlay.hideAfterGesture()
    }

    /** Hides bubble/wave hint once floating pointer owns the ongoing touch stream. */
    private fun dismissGestureAnimationForFloatingPointerHandoff() {
        if (!FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch()) return
        finishGestureAnimationIfNeeded()
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

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "\u2026") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "\u2026"
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



    private fun anchoredUtilityPanelRect(panelWidth: Float, rows: Int): RectF {
        val gh = rows * cellHeight + gridPadding * 2 + dp(28f)
        val gw = panelWidth
        val trigger = activeTriggerZoneRect()
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val anchorY = (pathRecognizer.gestureStartRawY() - loc[1]).coerceIn(trigger.top, trigger.bottom)
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
                        shellCoordinator.onPanelEnterAnimationEnded()
                    }
                    if (gestureSession.panelMode() == OverlayPanelMode.QUICK_LAUNCHER) {
                        quickLauncherController.onPanelEnterAnimationEnded()
                    }
                }
            })
            start()
        }
        if (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS) {
            shellCoordinator.onPanelEnterAnimationEnded()
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

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        private const val PANEL_ENTER_DURATION_MS = 180L
        private const val SHELL_PANEL_ENTER_DURATION_MS = 260L
        private const val PANEL_ENTER_OFFSCREEN_MARGIN_DP = 16f
        }
}
