package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.IndexSessionHost
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.OverlayBrightnessControl
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore
import com.slideindex.app.util.TaskSwitcherMenuActions
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
    private val onAdjustPanelLayoutCallback: (Float) -> Unit = {},
    private val onAdjustPanelDismissCallback: () -> Unit = {},
    private val onClickPassthroughCallback: (Float, Float, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    overlayBrightness: OverlayBrightnessControl? = null,
) : View(context), IndexSessionHost, GestureSession.Callbacks {

    private var settings = AppSettings()
    private var apps: List<AppInfo> = emptyList()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY

    private val zoneLayout = GestureZoneLayout(side)
    private val indexSession = SlideAlongRailSession(side, zoneLayout, this)
    private val panelGridSession = PanelGridSession()
    private val actionExecutor = ActionExecutor(
        context = context,
        appRepository = appRepository,
        clickPassthroughHandler = onClickPassthroughCallback,
        overlayBrightness = overlayBrightness,
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
    private var taskSwitcherScrollOffset = 0f
    private var taskSwitcherScrollDragging = false
    private var taskSwitcherScrollDragStartY = 0f
    private var taskSwitcherScrollDragStartOffset = 0f
    private var taskSwitcherOverscrollOffset = 0f
    private var taskSwitcherOverscrollAnimator: ValueAnimator? = null
    private var taskSwitcherGestureScrolled = false
    private var taskSwitcherExiting = false
    private var panelEnterProgress = 1f
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
    private val adjustPanelIdleDismissRunnable = Runnable { dismissAdjustPanel() }
    private var volumeChangeReceiver: BroadcastReceiver? = null
    private var interceptTouchActive = false

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
    private val triggerPreviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triggerPreviewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
        gestureSession.applySettings(newSettings)
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        syncZoneLayout()
        invalidate()
    }

    fun isSessionActive(): Boolean = gestureSession.isActive()

    fun hasAdjustPanel(): Boolean = adjustPanelState != null

    fun keepsOverlayExpanded(): Boolean =
        adjustPanelState != null || adjustIndicatorProgress > 0f || adjustPanelDismissing

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
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        syncZoneLayout()
        adjustPanelState?.let { state ->
            if (!adjustPanelEntering) {
                updateAdjustIndicatorLayout(state.anchorRawY)
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        stopAdjustPanelLevelSync()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (previewMode) return false
        val (localX, localY) = rawToLocal(event.rawX, event.rawY)
        if (adjustPanelState != null && !gestureSession.isActive()) {
            if (handleAdjustPanelTouch(event, localX, localY)) return true
        }
        when (gestureSession.panelMode()) {
            OverlayPanelMode.QUICK_LAUNCHER ->
                return handleQuickLauncherTouch(event, localX, localY)
            OverlayPanelMode.TASK_SWITCHER ->
                return handleTaskSwitcherTouch(event, localX, localY)
            OverlayPanelMode.INDEX -> return handleIndexTouch(event, localX, localY)
            OverlayPanelMode.NONE -> Unit
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (gestureSession.isActive()) return true
                if (gestureSession.onTouchDown(event.rawX, event.rawY, localX, localY)) return true
                if (settings.interceptSystemBackGesture &&
                    zoneLayout.containsInterceptZone(localX, localY)
                ) {
                    interceptTouchActive = true
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (interceptTouchActive) return true
                if (!gestureSession.isActive()) return false
                gestureSession.onTouchMove(event.rawX, event.rawY, localX, localY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (interceptTouchActive) {
                    interceptTouchActive = false
                    return true
                }
                if (!gestureSession.isActive()) return false
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                return true
            }
        }
        return false
    }

    private fun handleAdjustPanelTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (adjustPanelDismissing) return false
        val state = adjustPanelState ?: return false
        val density = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val layout = adjustIndicatorLayout ?: run {
                    dismissAdjustPanel()
                    return false
                }
                if (AdjustLevelIndicator.containsTouch(layout, side, localX, localY, density)) {
                    removeCallbacks(adjustPanelIdleDismissRunnable)
                    state.dragging = true
                    actionExecutor.beginContinuousAdjust(state.mode, event.rawY)
                    actionExecutor.updateContinuousAdjust(state.mode, event.rawY)
                    invalidate()
                    return true
                }
                dismissAdjustPanel()
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!state.dragging) return false
                actionExecutor.updateContinuousAdjust(state.mode, event.rawY)
                state.fraction = actionExecutor.adjustFraction()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!state.dragging) return false
                state.dragging = false
                actionExecutor.endContinuousAdjust()
                state.fraction = actionExecutor.readCurrentAdjustFraction(state.mode)
                scheduleAdjustPanelIdleDismiss()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun dismissAdjustPanel(animated: Boolean = true) {
        if (adjustPanelState == null || adjustPanelDismissing) return
        removeCallbacks(adjustPanelIdleDismissRunnable)
        stopAdjustPanelLevelSync()
        adjustIndicatorHoldVisual = captureAdjustIndicatorVisual()
        freezeAdjustIndicatorLayout(adjustIndicatorHoldVisual?.anchorRawY)
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
            post { onSessionEndCallback() }
        }
    }

    private fun freezeAdjustIndicatorLayout(anchorRawY: Float?) {
        if (adjustIndicatorFrozenLayout != null) return
        anchorRawY?.let { updateAdjustIndicatorLayout(it) }
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
    }

    private fun clearAdjustIndicatorExitState() {
        adjustIndicatorHoldVisual = null
        adjustIndicatorLayout = null
        adjustIndicatorFrozenLayout = null
        adjustIndicatorReceding = false
    }

    private fun scheduleAdjustPanelIdleDismiss() {
        removeCallbacks(adjustPanelIdleDismissRunnable)
        postDelayed(adjustPanelIdleDismissRunnable, ADJUST_PANEL_IDLE_DISMISS_MS)
    }

    fun showAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        deferWindowLayout: Boolean = false,
    ) {
        if (mode == ContinuousAdjustController.Mode.BRIGHTNESS) {
            actionExecutor.clearBrightnessPreview()
        }
        adjustPanelState = AdjustPanelState(mode = mode, fraction = fraction, anchorRawY = anchorRawY)
        adjustPanelDismissing = false
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorProgress = 0f
        if (deferWindowLayout) {
            adjustPanelExpandedForGesture = true
            onSessionStartCallback()
            post { beginAdjustPanelEnterAnimation(anchorRawY) }
        } else {
            adjustPanelExpandedForGesture = false
            onAdjustPanelLayoutCallback(anchorRawY)
            post { beginAdjustPanelEnterAnimation(anchorRawY) }
        }
        scheduleAdjustPanelIdleDismiss()
        startAdjustPanelLevelSync(mode)
    }

    private fun beginAdjustPanelEnterAnimation(anchorRawY: Float) {
        adjustPanelEntering = true
        wasAdjustMode = true
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorReceding = false
        adjustIndicatorProgress = 0f
        adjustIndicatorFrozenLayout = null
        updateAdjustIndicatorLayout(anchorRawY, forceFullScreenAnchor = true)
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = ADJUST_INDICATOR_ENTER_MS,
            interpolator = DecelerateInterpolator(),
        ) {
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY)
            invalidate()
        }
    }

    private fun startAdjustPanelLevelSync(mode: ContinuousAdjustController.Mode) {
        if (mode == ContinuousAdjustController.Mode.VOLUME) {
            startVolumeLevelSync()
        }
    }

    private fun stopAdjustPanelLevelSync() {
        stopVolumeLevelSync()
    }

    private fun startVolumeLevelSync() {
        if (volumeChangeReceiver != null) return
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != VOLUME_CHANGED_ACTION) return
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (streamType != AudioManager.STREAM_MUSIC) return
                syncAdjustPanelVolumeFromSystem()
            }
        }
        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
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

    private fun syncAdjustPanelVolumeFromSystem() {
        syncAdjustPanelLevelFromSystem(ContinuousAdjustController.Mode.VOLUME)
    }

    private fun syncAdjustPanelLevelFromSystem(mode: ContinuousAdjustController.Mode) {
        val state = adjustPanelState ?: return
        if (state.mode != mode || state.dragging) return
        val fraction = actionExecutor.readCurrentAdjustFraction(mode)
        if (kotlin.math.abs(state.fraction - fraction) < LEVEL_SYNC_EPSILON) return
        state.fraction = fraction
        scheduleAdjustPanelIdleDismiss()
        invalidate()
    }

    private fun updateAdjustIndicatorLayout(
        anchorRawY: Float,
        forceFullScreenAnchor: Boolean = false,
    ): AdjustLevelIndicator.Layout? {
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val (_, anchorLocalY) = rawToLocal(0f, anchorRawY)
        adjustIndicatorLayout = AdjustLevelIndicator.layout(
            viewWidth = if (forceFullScreenAnchor) screenWidthPx else width.coerceAtLeast(1),
            viewHeight = height.coerceAtLeast(resources.displayMetrics.heightPixels),
            side = side,
            anchorY = anchorLocalY,
            density = density,
            viewScreenX = if (forceFullScreenAnchor) 0 else loc[0],
            screenWidthPx = screenWidthPx,
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

    private fun handleQuickLauncherTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val panelRect = quickLauncherPanelRect()
        val touchX = panelEnterAdjustedX(localX, panelRect)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panelGridSession.updateHighlight(touchX, localY)
                if (panelGridSession.highlightedIndex < 0 && !panelContentRect.contains(touchX, localY)) {
                    gestureSession.endSession()
                } else if (panelGridSession.highlightedIndex >= 0) {
                    HapticHelper.appTick(this, settings)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureSession.isMoveTimeActionLocked()) return true
                val prev = panelGridSession.highlightedIndex
                panelGridSession.updateHighlight(touchX, localY)
                if (panelGridSession.highlightedIndex != prev && panelGridSession.highlightedIndex >= 0) {
                    HapticHelper.appTick(this, settings)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureSession.releaseImmediateGestureLock()) {
                    invalidate()
                    return true
                }
                panelGridSession.highlightedQuickItem()?.let { item ->
                    HapticHelper.confirmLaunch(this, settings)
                    actionExecutor.launchQuickItem(item, settings)
                }
                gestureSession.endSession()
                return true
            }
        }
        return false
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
                                TaskManagerUtil.removePackageFromCache(packageName)
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
                dismissed.forEach { TaskManagerUtil.removePackageFromCache(it) }
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
                entry?.let { RecentTasksLoader.promoteEntry(it) }
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
                drawWithPanelEnterAnimation(canvas, panelRect) {
                    drawQuickLauncherPanel(canvas)
                }
            }
            OverlayPanelMode.TASK_SWITCHER -> drawTaskSwitcherPanel(canvas)
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
            val fraction = if (state.dragging) {
                actionExecutor.adjustFraction()
            } else {
                state.fraction
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

    private fun drawAdjustIndicator(
        canvas: Canvas,
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
    ) {
        val layout = if (adjustIndicatorReceding || adjustPanelEntering) {
            adjustIndicatorFrozenLayout ?: run {
                freezeAdjustIndicatorLayout(anchorRawY)
                adjustIndicatorFrozenLayout
            }
        } else {
            adjustIndicatorFrozenLayout = null
            val panelVisible = adjustPanelState != null && !adjustPanelDismissing
            if (panelVisible && adjustIndicatorLayout != null) {
                adjustIndicatorLayout
            } else {
                updateAdjustIndicatorLayout(anchorRawY)
                adjustIndicatorLayout
            }
        } ?: return
        AdjustLevelIndicator.draw(
            canvas = canvas,
            layout = layout,
            mode = mode,
            fraction = fraction,
            enterProgress = adjustIndicatorProgress,
            density = resources.displayMetrics.density,
            side = side,
            recede = adjustIndicatorReceding,
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
            animateAdjustIndicatorTo(
                target = 1f,
                durationMs = ADJUST_INDICATOR_ENTER_MS,
                interpolator = DecelerateInterpolator(),
            )
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
                loadTaskSwitcherApps(deferInvalidate = true)
            }
            OverlayPanelMode.INDEX, OverlayPanelMode.QUICK_LAUNCHER -> {
                panelEnterProgress = 0f
            }
            OverlayPanelMode.NONE -> {
                panelEnterProgress = 1f
            }
        }
        panelGridSession.reset()
        onSessionStartCallback()
        if (mode != OverlayPanelMode.NONE) {
            post {
                if (gestureSession.panelMode() != mode) return@post
                syncZoneLayout()
                if (mode == OverlayPanelMode.TASK_SWITCHER) {
                    taskSwitcherLayout = null
                    taskSwitcherFrozenAnchorLocalY = resolveTaskSwitcherAnchorLocalY()
                }
                startPanelEnterAnimation()
            }
        }
    }

    private fun loadTaskSwitcherApps(deferInvalidate: Boolean = false) {
        taskSwitcherLayout = null
        clearTaskSwitcherPickHighlights()

        val placeholder = RecentTasksLoader.peekCached()
        recentApps = placeholder.toMutableList()
        if (!deferInvalidate) {
            invalidateTaskSwitcherPanel()
        }

        if (!TaskManagerUtil.hasPermission()) {
            recentApps = mutableListOf()
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
            recentApps = fresh.toMutableList()
            taskSwitcherLayout = null
            invalidateTaskSwitcherPanel()
        }
    }

    override fun onSessionEnd() {
        cancelPanelEnterAnimation()
        adjustPanelState?.let {
            adjustIndicatorReceding = false
            // Enter/exit animation is owned by showAdjustPanel / dismissAdjustPanel.
            // Snapping to 1f here cancels an in-flight enter (common on fast left swipes).
        }
        var deferOverlayCollapse = false
        if (adjustPanelState == null) {
            if (adjustIndicatorProgress > 0f) {
                adjustIndicatorHoldVisual = captureAdjustIndicatorVisual() ?: adjustIndicatorHoldVisual
                wasAdjustMode = false
                deferOverlayCollapse = true
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
            } else {
                adjustIndicatorAnimator?.cancel()
                adjustIndicatorProgress = 0f
                wasAdjustMode = false
                clearAdjustIndicatorExitState()
            }
        }
        panelEnterProgress = 1f
        taskSwitcherLoadGeneration++
        syncZoneLayout()
        panelGridSession.reset()
        taskSwitcherLayout = null
        clearTaskSwitcherPickHighlights()
        taskSwitcherAnchorRawY = null
        taskSwitcherFrozenAnchorLocalY = null
        taskSwitcherScrollOffset = 0f
        taskSwitcherScrollDragging = false
        taskSwitcherOverscrollOffset = 0f
        cancelTaskSwitcherOverscrollAnimation()
        taskSwitcherGestureScrolled = false
        taskSwitcherExiting = false
        dismissTaskSwitcherContextMenu(immediate = true)
        cancelTaskSwitcherCloseLongPress()
        cancelTaskSwitcherRowLongPress()
        if (!deferOverlayCollapse) {
            post { notifyOverlayLayoutIfNeeded() }
        }
    }

    override fun onRequestInvalidate() {
        invalidate()
    }

    private fun syncZoneLayout() {
        zoneLayout.update(
            settings = settings,
            viewWidth = width,
            viewHeight = height,
            density = resources.displayMetrics.density,
            sessionActive = gestureSession.isActive(),
            previewMode = previewMode,
        )
    }

    private fun rawToLocal(rawX: Float, rawY: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return rawX - loc[0] to rawY - loc[1]
    }

    private fun appsPerRow(): Int = settings.appsPerRow.coerceIn(2, 5)

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
        val zone = zoneLayout.triggerZoneRect()
        triggerPreviewFillPaint.color = Color.argb(72, 255, 152, 0)
        canvas.drawRoundRect(zone, corner, corner, triggerPreviewFillPaint)
        triggerPreviewStrokePaint.color = Color.argb(210, 255, 167, 38)
        triggerPreviewStrokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(zone, corner, corner, triggerPreviewStrokePaint)
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

    private fun quickLauncherItems(): List<QuickLauncherItem> {
        val configured = when (side) {
            PanelSide.LEFT -> settings.quickLauncherLeft
            PanelSide.RIGHT -> settings.quickLauncherRight
        }
        if (configured.isNotEmpty()) return configured
        return apps.take(9).map { QuickLauncherItem.app(it.packageName, it.label) }
    }

    private fun drawQuickLauncherPanel(canvas: Canvas) {
        drawUtilityGrid(
            canvas = canvas,
            title = "快速启动器",
            entries = quickLauncherItems(),
        ) { item, cell, index ->
            panelGridSession.cellBounds.add(item to cell)
            val label = when (item.type) {
                QuickLauncherItemType.APP -> apps.find { it.packageName == item.payload }?.label ?: item.label
                QuickLauncherItemType.SHORTCUT -> item.label.ifBlank { "快捷方式" }
                QuickLauncherItemType.WIDGET -> item.label.ifBlank { "小组件" }
            }
            drawGridCell(canvas, cell, index, label) {
                when (item.type) {
                    QuickLauncherItemType.APP -> apps.find { it.packageName == item.payload }?.let { iconFor(it) }
                    else -> null
                }
            }
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
        val rowHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0, 0, 0) }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 30, 30, 30)
            textSize = sp(13.5f)
        }
        val closeAllPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 0, 122, 255)
            textSize = sp(13f)
            textAlign = Paint.Align.CENTER
        }
        val closeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 60, 60, 60)
            strokeWidth = dp(1.55f)
            strokeCap = Paint.Cap.ROUND
        }
        val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 120, 120, 120) }

        val panel = layout.panelRect
        val panelCorner = dp(13f)
        drawElevatedRoundRect(canvas, panel, panelCorner, Color.WHITE)

        if (layout.rows.isEmpty()) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 80, 80, 80)
                textSize = sp(13f)
                textAlign = Paint.Align.CENTER
            }
            val hint = if (TaskManagerUtil.hasPermission()) {
                context.getString(R.string.task_switcher_empty)
            } else {
                context.getString(R.string.task_switcher_no_shizuku)
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
        val progress = taskSwitcherMenuEnterProgress
        if (progress <= 0f) return
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 30, 30, 30)
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
        drawElevatedRoundRect(canvas, menu.menuRect, corner, Color.WHITE)
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
        val trigger = zoneLayout.triggerZoneRect()
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
        drawCell: (T, RectF, Int) -> Unit,
    ) {
        if (entries.isEmpty()) return
        val m = appsPerRow()
        val appCount = entries.size
        val layout = gridLayoutInfo(appCount)
        val grid = utilityPanelRect(layout.panelWidth, layout.rows)
        panelContentRect.set(grid)
        panelGridSession.cellBounds.clear()
        panelBgPaint.color = Color.argb((225 * settings.panelOpacity).toInt().coerceIn(150, 225), 48, 48, 52)
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        letterPaint.textAlign = Paint.Align.LEFT
        letterPaint.color = Color.WHITE
        letterPaint.textSize = sp(14f)
        letterPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, grid.left + gridPadding, grid.top + dp(18f), letterPaint)
        entries.forEachIndexed { index, entry ->
            val row = index / m
            val visualCol = visualColumn(index, m, appCount)
            val left = grid.left + gridPadding + visualCol * cellWidth
            val top = grid.top + dp(28f) + gridPadding + row * cellHeight
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            drawCell(entry, cell, index)
        }
        letterPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawGridCell(
        canvas: Canvas,
        cell: RectF,
        index: Int,
        label: String,
        iconProvider: () -> Bitmap?,
    ) {
        if (index == panelGridSession.highlightedIndex) {
            tmpRect.set(cell.left + dp(3f), cell.top + dp(2f), cell.right - dp(3f), cell.bottom - dp(2f))
            canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), cellHighlightPaint)
        }
        val icon = iconProvider()
        val iconTop = cell.top + gridIconTopInset
        val displayLabel = ellipsize(label, cellWidth - gridCellInset * 2)
        val labelBaseline = iconTop + gridIconSize + gridIconLabelGap - appLabelPaint.fontMetrics.ascent
        val iconCenterX = cell.centerX()
        icon?.let { canvas.drawBitmap(it, iconCenterX - gridIconSize / 2f, iconTop, null) }
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
        val trigger = zoneLayout.triggerZoneRect()
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

    private fun quickLauncherPanelRect(): RectF {
        val items = quickLauncherItems()
        if (items.isEmpty()) return RectF()
        val layout = gridLayoutInfo(items.size)
        return utilityPanelRect(layout.panelWidth, layout.rows)
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
        panelEnterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PANEL_ENTER_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                panelEnterProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
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
            duration = PANEL_ENTER_DURATION_MS
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
        if (runBeforeExit) {
            runAfter?.invoke()
        }
        startPanelExitAnimation {
            taskSwitcherExiting = false
            if (!runBeforeExit) {
                runAfter?.invoke()
            }
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
        private const val PANEL_ENTER_OFFSCREEN_MARGIN_DP = 16f
        private const val ADJUST_PANEL_IDLE_DISMISS_MS = 4_000L
        private const val ADJUST_INDICATOR_ENTER_MS = 220L
        private const val ADJUST_INDICATOR_EXIT_MS = 160L
        private const val LEVEL_SYNC_EPSILON = 0.002f
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
