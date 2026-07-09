package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.GestureActionIconBitmap
import com.slideindex.app.util.QuickLauncherIconResolver
import com.slideindex.app.util.coerceSafe

internal class QuickLauncherOverlayController(
    private val host: Host,
) {
    interface Host {
        val context: Context
        fun settings(): AppSettings
        fun side(): PanelSide
        fun apps(): List<AppInfo>
        fun gestureSession(): GestureSession
        fun zoneLayout(): GestureZoneLayout
        fun pathRecognizer(): SwipePathRecognizer
        fun actionExecutor(): ActionExecutor
        fun panelGridSession(): PanelGridSession
        fun panelEnterProgress(): Float
        fun panelEnterAdjustedX(localX: Float, panel: RectF): Float
        fun panelEnterOffsetX(panel: RectF): Float
        fun panelContentRect(): RectF
        fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit)
        fun activeTriggerZoneRect(): RectF
        fun viewWidth(): Int
        fun viewHeight(): Int
        fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun viewLocationOnScreen(): IntArray
        fun invalidate()
        fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int)
        fun post(action: () -> Unit)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
        fun hapticTick()
        fun hapticLongThreshold()
        fun hapticConfirmLaunch()
        fun startPanelExitAnimation(onEnd: () -> Unit)
        fun notifyPresentationTouchRequirementChanged()
        fun onQuickLauncherItemsPersist(items: List<QuickLauncherItem>)
        fun onOverlayWindowSuspend()
        fun onOverlayWindowResume()
    }

    private val quickLauncherOverlayDialogHost = OverlayComposeDialogHost(
        context = host.context,
        themeSeedArgb = { host.settings().themeColorArgb },
        dynamicColor = { host.settings().dynamicColorEnabled },
    )
    private val quickLauncherPanelController = QuickLauncherPanelController(
        object : QuickLauncherPanelController.Host {
            override val context: Context get() = host.context
            override fun settings(): AppSettings = host.settings()
            override fun side(): PanelSide = host.side()
            override fun apps(): List<AppInfo> = host.apps()
            override fun isPanelReady(): Boolean = host.panelEnterProgress() >= 1f
            override fun isAddDialogShowing(): Boolean =
                QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing
            override fun dp(value: Float): Float = host.dp(value)
            override fun sp(value: Float): Float = host.sp(value)
            override fun invalidate() = host.invalidate()
            override fun hapticTick() = host.hapticTick()
            override fun showAddDialog(
                configuredAppPackages: Set<String>,
                configuredShortcutKeys: Set<String>,
                configuredActionKeys: Set<String>,
                onAdd: (QuickLauncherItem) -> Unit,
                onRemove: (QuickLauncherItem) -> Unit,
            ) {
                QuickLauncherAddTrampoline.launch(
                    context = host.context,
                    panelSide = host.side(),
                    configuredAppPackages = configuredAppPackages,
                    configuredShortcutKeys = configuredShortcutKeys,
                    configuredActionKeys = configuredActionKeys,
                    onPrepare = { host.onOverlayWindowSuspend() },
                    onDismiss = {
                        CreateShortcutTrampoline.cancelPending()
                        host.onOverlayWindowResume()
                        host.invalidate()
                        host.notifyPresentationTouchRequirementChanged()
                    },
                    onAdd = onAdd,
                    onRemove = onRemove,
                )
            }
            override fun onPersist(items: List<QuickLauncherItem>) {
                invalidateQuickLauncherDerivedCaches()
                host.onQuickLauncherItemsPersist(items)
            }
            override fun quickLauncherPageSize(): Int = quickLauncherPageSize()
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
    private var quickLauncherAppsByPackage: Map<String, AppInfo> = emptyMap()
    private val quickLauncherIconCache = mutableMapOf<String, Bitmap>()
    private val quickLauncherLabelCache = mutableMapOf<String, String>()
    private var quickLauncherCachedPages: List<List<QuickLauncherItem>>? = null
    private var quickLauncherCachedPagesKey: Int = 0
    private var quickLauncherLayoutPanelWidth: Float = 0f

    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(230, 255, 255, 255)
    }
    private val cellHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellLongPressHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cellInitialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val tmpRect = RectF()

    private val quickLauncherCellHeight get() = host.dp(64f)
    private val quickLauncherCellWidth get() = host.dp(56f)
    private val quickLauncherGridIconSize get() = host.dp(38f)
    private val quickLauncherGridPadding get() = host.dp(8f)
    private val quickLauncherHeaderHeight get() = host.dp(24f)
    private val quickLauncherGridIconTopInset get() = host.dp(4f)
    private val quickLauncherGridIconLabelGap get() = host.dp(4f)
    private val gridCellInset get() = host.dp(4f)
    private val panelCorner get() = host.dp(18f)

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (quickLauncherExiting) return true
        val panelRect = quickLauncherPanelRect()
        val contentRect = quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = host.panelEnterAdjustedX(localX, contentRect)
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
        val continuousPick = host.gestureSession().quickLauncherContinuousPickActive()
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
                host.invalidate()
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
                if (!host.gestureSession().isMoveTimeActionLocked()) {
                    quickLauncherOpeningGestureActive = false
                }
                quickLauncherPageSwipeTracking = quickLauncherPageCount > 1 &&
                    host.panelContentRect().contains(touchX, localY) &&
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
                    host.invalidate()
                    return true
                }
                if (!isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                    endQuickLauncherSessionAnimated()
                    host.invalidate()
                    return true
                }
                host.panelGridSession().updateHighlight(touchX, localY)
                syncQuickLauncherPressTracking(event.eventTime)
                if (host.panelGridSession().highlightedIndex >= 0) {
                    host.hapticTick()
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (continuousPick && quickLauncherContinuousPickReady()) {
                    quickLauncherOpeningGestureActive = false
                }
                if (host.gestureSession().isMoveTimeActionLocked()) {
                    quickLauncherPageSwipeStartX = touchX
                    quickLauncherPageSwipeStartY = localY
                    quickLauncherPageSwipeLocked = false
                }
                if (consumeQuickLauncherPageSwipeMove(touchX, localY)) {
                    host.invalidate()
                    return true
                }
                if (host.gestureSession().isMoveTimeActionLocked() && !continuousPick) {
                    if (updateQuickLauncherEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (quickLauncherOpeningGestureActive &&
                    host.gestureSession().isMoveTimeActionLocked() &&
                    !continuousPick
                ) {
                    host.invalidate()
                    return true
                }
                if (updateQuickLauncherEdgeTracking(event.rawY, localX, localY)) return true
                if (continuousPick) {
                    if (!quickLauncherContinuousPickReady()) {
                        host.invalidate()
                        return true
                    }
                    if (quickLauncherPageInteractionActive()) {
                        host.invalidate()
                        return true
                    }
                    if (!isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                        clearQuickLauncherHighlight()
                        host.invalidate()
                        return true
                    }
                    applyQuickLauncherEdgeAutoPage(touchX)
                    updateQuickLauncherHighlight(localX, localY, touchX, event.eventTime, haptic = true)
                    host.invalidate()
                    return true
                }
                val prev = host.panelGridSession().highlightedIndex
                if (isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                    host.panelGridSession().updateHighlight(touchX, localY)
                    if (host.panelGridSession().highlightedIndex != prev) {
                        syncQuickLauncherPressTracking(event.eventTime)
                    }
                    if (host.panelGridSession().highlightedIndex != prev &&
                        host.panelGridSession().highlightedIndex >= 0
                    ) {
                        host.hapticTick()
                    }
                } else {
                    clearQuickLauncherHighlight()
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (host.gestureSession().releaseImmediateGestureLock()) {
                    quickLauncherOpeningGestureActive = false
                    host.invalidate()
                    return true
                }
                quickLauncherOpeningGestureActive = false
                if (continuousPick) {
                    if (quickLauncherPageSwipeLocked) {
                        finishQuickLauncherPageDrag()
                        quickLauncherPageSwipeLocked = false
                        quickLauncherPageSwipeTracking = false
                        quickLauncherPageChangedThisGesture = false
                        host.invalidate()
                        return true
                    }
                    if (quickLauncherPageSnapAnimator?.isRunning == true) {
                        host.invalidate()
                        return true
                    }
                    if (tryCommitQuickLauncherToolbarOnContinuousPickUp(touchX, localY, panelRect)) {
                        host.gestureSession().clearQuickLauncherContinuousPick()
                        quickLauncherPageSwipeLocked = false
                        quickLauncherPageChangedThisGesture = false
                        host.invalidate()
                        return true
                    }
                    if (quickLauncherContinuousPickReady() &&
                        isQuickLauncherSelectableTouch(localX, localY, panelRect)
                    ) {
                        updateQuickLauncherHighlight(localX, localY, touchX, event.eventTime, haptic = false)
                        val panelModeBeforeAction = host.gestureSession().panelMode()
                        if (host.panelGridSession().highlightedIndex >= 0 &&
                            performQuickLauncherUpAction(event, touchX, localX, localY)
                        ) {
                            endQuickLauncherAfterLaunch(quickLauncherLaunchEndDeferMs)
                            quickLauncherLaunchEndDeferMs = 0L
                        } else if (!quickLauncherPanelController.editMode &&
                            !quickLauncherOverlayDialogHost.isShowing &&
                            host.panelGridSession().highlightedIndex < 0 &&
                            !toolbarTouchThisGesture &&
                            host.gestureSession().panelMode() == panelModeBeforeAction
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
                    host.invalidate()
                    return true
                }
                if (quickLauncherPageSwipeLocked) {
                    finishQuickLauncherPageDrag()
                    quickLauncherPageSwipeLocked = false
                    quickLauncherPageSwipeTracking = false
                    quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (quickLauncherPageSnapAnimator?.isRunning == true) {
                    host.invalidate()
                    return true
                }
                if (isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                    host.panelGridSession().updateHighlight(touchX, localY)
                } else {
                    clearQuickLauncherHighlight()
                }
                val panelModeBeforeAction = host.gestureSession().panelMode()
                if (!quickLauncherPageChangedThisGesture &&
                    performQuickLauncherUpAction(event, touchX, localX, localY)
                ) {
                    endQuickLauncherAfterLaunch(quickLauncherLaunchEndDeferMs)
                    quickLauncherLaunchEndDeferMs = 0L
                } else if (!quickLauncherPanelController.editMode &&
                    !quickLauncherOverlayDialogHost.isShowing &&
                    host.panelGridSession().highlightedIndex < 0 &&
                    !quickLauncherPageChangedThisGesture &&
                    !toolbarTouchThisGesture &&
                    host.gestureSession().panelMode() == panelModeBeforeAction
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

    fun draw(canvas: Canvas, drawToolbar: Boolean = true) {
        drawQuickLauncherPanel(canvas, drawToolbar)
    }

    fun panelRect(): RectF = quickLauncherPanelRect()

    fun enterContentRect(): RectF {
        val panelRect = quickLauncherPanelRect()
        return quickLauncherPanelController.combinedContentRect(panelRect)
    }

    fun isExiting(): Boolean = quickLauncherExiting

    fun isOverlayDialogShowing(): Boolean =
        QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing

    fun syncOverlayDialogZOrder() {
        if (quickLauncherOverlayDialogHost.isShowing) {
            quickLauncherOverlayDialogHost.bringToFront()
        }
    }

    fun syncSettings(settings: AppSettings) {
        quickLauncherPanelController.syncSettings(settings)
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        appLabelPaint.textSize = host.sp(11f)
    }

    fun setApps(apps: List<AppInfo>) {
        rebuildQuickLauncherAppsByPackage(apps)
        invalidateQuickLauncherDerivedCaches()
    }

    fun invalidateDerivedCaches() {
        invalidateQuickLauncherDerivedCaches()
    }

    fun onSizeChanged() {
        quickLauncherLayoutPanelWidth = 0f
    }

    fun setAnchorRawY(rawY: Float?) {
        quickLauncherAnchorRawY = rawY
    }

    fun onSessionStart() {
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
        quickLauncherPanelController.ensureDefaultsPersisted(host.settings())
        warmQuickLauncherShortcutCache()
        warmQuickLauncherActionIconCache()
        warmQuickLauncherIconCache()
        quickLauncherAnchorRawY = quickLauncherAnchorRawY
            ?.takeIf { it > 0f }
            ?: host.pathRecognizer().lastRawY().takeIf { it > 0f }
            ?: host.pathRecognizer().gestureStartRawY().takeIf { it > 0f }
    }

    fun onLayoutReady() {
        if (!host.gestureSession().quickLauncherContinuousPickActive()) {
            quickLauncherFrozenAnchorLocalY = resolveQuickLauncherAnchorLocalY()
        }
    }

    fun onSessionEnd() {
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
        quickLauncherPanelController.reset()
        quickLauncherOverlayDialogHost.dismiss()
        invalidateQuickLauncherDerivedCaches()
    }

    fun onPanelEnterAnimationEnded() {
        host.notifyPresentationTouchRequirementChanged()
        if (!host.gestureSession().isMoveTimeActionLocked() ||
            host.gestureSession().quickLauncherContinuousPickActive()
        ) {
            quickLauncherOpeningGestureActive = false
        }
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
        cellBounds = host.panelGridSession().cellBounds,
        tapGesture = tapGesture,
        toolbarCommitAllowed = toolbarCommitAllowed,
    )

    private fun isQuickLauncherTapGesture(touchX: Float, localY: Float): Boolean {
        val dx = touchX - quickLauncherPageSwipeStartX
        val dy = localY - quickLauncherPageSwipeStartY
        val slop = host.dp(24f)
        return dx * dx + dy * dy <= slop * slop
    }

    private fun quickLauncherToolbarCommitAllowed(): Boolean {
        if (quickLauncherPageSwipeLocked) return false
        if (quickLauncherPageSnapAnimator?.isRunning == true) return false
        if (kotlin.math.abs(quickLauncherPageDragOffset) > host.dp(6f)) return false
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

    private fun consumeQuickLauncherPageRelease(): Boolean {
        if (quickLauncherPanelController.editMode || quickLauncherPageCount <= 1) return false
        if (quickLauncherPageSwipeLocked ||
            kotlin.math.abs(quickLauncherPageDragOffset) > host.dp(6f)
        ) {
            finishQuickLauncherPageDrag()
            quickLauncherPageSwipeLocked = false
            quickLauncherPageSwipeTracking = false
            host.invalidate()
            return true
        }
        if (quickLauncherPageSnapAnimator?.isRunning == true) {
            host.invalidate()
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

    private fun quickLauncherLongPressEligible(): Boolean =
        host.settings().freeWindowEnabled && host.settings().resolvedLaunchPolicy().usesLongPress()

    private fun syncQuickLauncherPressTracking(eventTime: Long) {
        val index = host.panelGridSession().highlightedIndex
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
        if (!quickLauncherLongPressEligible()) return
        quickLauncherLongPressIndex = index
        val runnable = Runnable {
            if (host.panelGridSession().highlightedIndex == quickLauncherLongPressIndex &&
                quickLauncherLongPressIndex >= 0
            ) {
                quickLauncherLongPressArmed = true
                host.hapticLongThreshold()
                host.invalidate()
            }
        }
        quickLauncherLongPressRunnable = runnable
        host.postDelayed(runnable, host.settings().effectiveLongPressDurationMs().toLong())
    }

    private fun cancelQuickLauncherLongPress() {
        quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
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
        val item = host.panelGridSession().highlightedQuickItem() ?: return false
        val longPress = quickLauncherLongPressTriggered(event)
        cancelQuickLauncherLongPress()
        return when (item.type) {
            QuickLauncherItemType.ACTION -> {
                val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return false
                host.gestureSession().performQuickLauncherAction(
                    action,
                    localX,
                    localY,
                    event.rawY,
                    confirmHaptic = longPress,
                )
            }
            else -> {
                if (longPress) {
                    host.hapticConfirmLaunch()
                }
                quickLauncherLaunchEndDeferMs =
                    if (host.actionExecutor().launchQuickItem(
                            item,
                            host.settings(),
                            longPressArmed = longPress,
                            anchorRawY = event.rawY,
                        )
                    ) {
                        280L
                    } else {
                        0L
                    }
                true
            }
        }
    }

    private fun endQuickLauncherAfterLaunch(deferMs: Long) {
        if (deferMs > 0L) {
            host.postDelayed({ host.gestureSession().endSession() }, deferMs)
        } else {
            host.post { host.gestureSession().endSession() }
        }
    }

    private fun quickLauncherLongPressTriggered(event: MotionEvent): Boolean {
        if (quickLauncherLongPressArmed) return true
        if (!quickLauncherLongPressEligible()) {
            return false
        }
        if (quickLauncherPressIndex < 0 ||
            quickLauncherPressIndex != host.panelGridSession().highlightedIndex
        ) {
            return false
        }
        return event.eventTime - quickLauncherPressDownTime >= host.settings().effectiveLongPressDurationMs()
    }

    private fun quickLauncherContinuousPickReady(): Boolean = host.panelEnterProgress() >= 1f

    private fun quickLauncherPageInteractionActive(): Boolean =
        quickLauncherPageSwipeLocked || quickLauncherPageSnapAnimator?.isRunning == true

    private fun consumeQuickLauncherPageSwipeMove(touchX: Float, localY: Float): Boolean {
        if (host.gestureSession().isMoveTimeActionLocked()) return false
        if (host.gestureSession().quickLauncherContinuousPickActive()) return false
        if (quickLauncherPanelController.editMode || quickLauncherPageCount <= 1) return false
        val deltaX = touchX - quickLauncherPageSwipeStartX
        val deltaY = localY - quickLauncherPageSwipeStartY
        val absX = kotlin.math.abs(deltaX)
        val absY = kotlin.math.abs(deltaY)
        val directionLock = host.dp(QUICK_LAUNCHER_PAGE_SWIPE_DIRECTION_LOCK_DP)
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
        if (kotlin.math.abs(start - targetOffset) < host.dp(0.5f)) {
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
        host.panelGridSession().clearHighlight()
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
        val effectiveX = if (host.panelContentRect().contains(touchX, localY)) {
            touchX
        } else {
            quickLauncherEdgeProjectedX(localY)
        }
        val prev = host.panelGridSession().highlightedIndex
        host.panelGridSession().updateHighlight(effectiveX, localY)
        if (host.panelGridSession().highlightedIndex != prev) {
            syncQuickLauncherPressTracking(eventTime)
        }
        if (haptic && host.panelGridSession().highlightedIndex != prev &&
            host.panelGridSession().highlightedIndex >= 0
        ) {
            if (host.panelGridSession().highlightedIndex != quickLauncherContinuousHapticIndex) {
                host.hapticTick()
                quickLauncherContinuousHapticIndex = host.panelGridSession().highlightedIndex
            }
        }
    }

    private fun quickLauncherEdgeProjectedX(localY: Float): Float {
        val rowCells = host.panelGridSession().cellBounds.filter { (_, rect) ->
            localY >= rect.top && localY <= rect.bottom
        }
        if (rowCells.isEmpty()) return host.panelContentRect().centerX()
        return when (host.side()) {
            PanelSide.LEFT -> rowCells.minByOrNull { it.second.left }?.second?.centerX()
                ?: host.panelContentRect().centerX()
            PanelSide.RIGHT -> rowCells.maxByOrNull { it.second.right }?.second?.centerX()
                ?: host.panelContentRect().centerX()
        }
    }

    private fun applyQuickLauncherEdgeAutoPage(touchX: Float): Boolean {
        if (!host.gestureSession().quickLauncherContinuousPickActive()) return false
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
        host.hapticTick()
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
        val edge = host.dp(QUICK_LAUNCHER_EDGE_AUTO_PAGE_THRESHOLD_DP)
        val innerThreshold = when (host.side()) {
            PanelSide.LEFT -> panelRect.right - edge
            PanelSide.RIGHT -> panelRect.left + edge
        }
        val outerThreshold = when (host.side()) {
            PanelSide.LEFT -> panelRect.left + edge
            PanelSide.RIGHT -> panelRect.right - edge
        }
        return when (host.side()) {
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

    private fun isQuickLauncherSelectableTouch(
        localX: Float,
        localY: Float,
        panelRect: RectF,
    ): Boolean {
        if (panelRect.isEmpty) return false
        val contentRect = quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = host.panelEnterAdjustedX(localX, contentRect)
        if (quickLauncherPanelController.toolbarContains(touchX, localY)) return true
        if (localY < contentRect.top || localY > contentRect.bottom) return false
        if (panelRect.contains(touchX, localY)) return true
        return isInQuickLauncherApproachZone(localX, panelRect)
    }

    private fun isInQuickLauncherApproachZone(localX: Float, panelRect: RectF): Boolean {
        val trigger = host.activeTriggerZoneRect()
        return when (host.side()) {
            PanelSide.LEFT -> localX >= trigger.right && localX <= panelRect.left
            PanelSide.RIGHT -> localX <= trigger.left && localX >= panelRect.right
        }
    }

    private fun shouldFreezeQuickLauncherAnchor(): Boolean =
        host.gestureSession().panelMode() == OverlayPanelMode.QUICK_LAUNCHER

    private fun updateQuickLauncherEdgeTracking(rawY: Float, localX: Float, localY: Float): Boolean {
        if (!host.zoneLayout().containsTrigger(localX, localY)) return false
        val continuousPick = host.gestureSession().quickLauncherContinuousPickActive()
        if (!shouldFreezeQuickLauncherAnchor()) {
            quickLauncherAnchorRawY = rawY
            quickLauncherFrozenAnchorLocalY = null
        }
        if (continuousPick && quickLauncherContinuousPickReady()) {
            val panelRect = quickLauncherPanelRect()
            if (isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                val touchX = host.panelEnterAdjustedX(localX, panelRect)
                if (!quickLauncherPageInteractionActive()) {
                    applyQuickLauncherEdgeAutoPage(touchX)
                    updateQuickLauncherHighlight(
                        localX,
                        localY,
                        touchX,
                        android.os.SystemClock.uptimeMillis(),
                        haptic = true,
                    )
                } else {
                    clearQuickLauncherHighlight()
                }
            } else {
                clearQuickLauncherHighlight()
            }
        } else if (!continuousPick) {
            clearQuickLauncherHighlight()
        }
        host.invalidate()
        return true
    }

    private fun resolveQuickLauncherAnchorLocalY(): Float {
        val rawY = quickLauncherAnchorRawY ?: host.pathRecognizer().gestureStartRawY()
        val loc = host.viewLocationOnScreen()
        val anchorY = rawY - loc[1]
        val trigger = host.activeTriggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun quickLauncherAnchorLocalY(): Float =
        quickLauncherFrozenAnchorLocalY ?: resolveQuickLauncherAnchorLocalY()

    private fun quickLauncherColumnsPerPage(): Int =
        host.settings().quickLauncherColumnsPerPage.coerceIn(2, 5)

    private fun quickLauncherRowsPerPage(): Int =
        host.settings().quickLauncherRowsPerPage.coerceIn(2, QUICK_LAUNCHER_MAX_ROWS)

    private fun quickLauncherGridLayoutInfo(): GridLayoutInfo {
        val m = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val panelWidth = m * quickLauncherCellWidth + quickLauncherGridPadding * 2
        return GridLayoutInfo(m, m, rows, panelWidth)
    }

    private fun quickLauncherPanelContentHeight(rows: Int): Float =
        rows * quickLauncherCellHeight + quickLauncherGridPadding * 2 + quickLauncherHeaderHeight

    private fun quickLauncherRootItems(): List<QuickLauncherItem> =
        quickLauncherPanelController.displayItems(host.settings())

    private fun quickLauncherItemCacheKey(item: QuickLauncherItem): String =
        "${item.type.id}\u0000${item.payload}"

    private fun rebuildQuickLauncherAppsByPackage(apps: List<AppInfo> = host.apps()) {
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
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.invalidate()
            return
        }
        val panelRect = quickLauncherPanelRect()
        if (panelRect.isEmpty) {
            host.invalidate()
            return
        }
        val dirty = quickLauncherPanelController.combinedContentRect(panelRect)
        val offsetX = host.panelEnterOffsetX(dirty)
        val pad = host.dp(2f).toInt()
        host.invalidatePartial(
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
                tintArgb = Color.WHITE,
            )?.also { quickLauncherIconCache[key] = it }
        }
        return QuickLauncherIconResolver.iconBitmap(
            item = item,
            appsByPackage = quickLauncherAppsByPackage,
            size = size,
            context = host.context,
        )?.also { quickLauncherIconCache[key] = it }
    }

    private fun warmQuickLauncherShortcutCache() {
        val items = quickLauncherRootItems()
        if (items.none { it.type == QuickLauncherItemType.SHORTCUT }) return
        Thread {
            AppShortcutLoader.warmQuickLauncherShortcuts(host.context, items)
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
        host.panelGridSession().cellBounds.clear()
        host.panelContentRect().set(panelRect)
        drawQuickLauncherPanelChrome(canvas, panelRect)

        val dragOffset = quickLauncherPageDragOffset
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val pagingActive = quickLauncherPageSwipeLocked ||
            quickLauncherPageSnapAnimator?.isRunning == true ||
            kotlin.math.abs(dragOffset) > host.dp(0.5f)
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
        if (pagingActive && kotlin.math.abs(dragOffset) > host.dp(0.5f)) {
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
        quickLauncherPanelController.layoutDeleteBadges(host.panelGridSession().cellBounds.map { it.second })
        quickLauncherPanelController.drawDeleteBadges(canvas)
    }

    private fun drawQuickLauncherPanelChrome(canvas: Canvas, grid: RectF) {
        panelBgPaint.color = Color.argb(
            (225 * host.settings().panelOpacity).toInt().coerceIn(150, 225),
            48,
            48,
            52,
        )
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        letterPaint.textAlign = Paint.Align.LEFT
        letterPaint.color = Color.WHITE
        letterPaint.textSize = host.sp(14f)
        letterPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("快速启动器", grid.left + quickLauncherGridPadding, grid.top + host.dp(16f), letterPaint)
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
            val visualCol = visualColumn(index, m, slotCount, host.side())
            val left = panelRect.left + quickLauncherGridPadding + visualCol * quickLauncherCellWidth
            val top = panelRect.top + quickLauncherHeaderHeight + quickLauncherGridPadding + row * quickLauncherCellHeight
            val cell = RectF(left, top, left + quickLauncherCellWidth, top + quickLauncherCellHeight)
            val (offsetX, offsetY) = 0f to 0f
            if (offsetX != 0f || offsetY != 0f) {
                canvas.save()
                canvas.translate(offsetX, offsetY)
            }
            if (recordCells) {
                host.panelGridSession().cellBounds.add(item to cell)
            }
            drawGridCell(
                canvas,
                cell,
                itemGlobalIndex,
                quickLauncherItemLabel(item),
                iconProvider = { quickLauncherItemIcon(item) },
                longPressArmed = recordCells &&
                    itemGlobalIndex == host.panelGridSession().highlightedIndex &&
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
            kotlin.math.abs(quickLauncherPageDragOffset) > host.dp(0.5f)

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
        val dotRadius = host.dp(2.5f)
        val dotGap = host.dp(6f)
        val totalWidth = quickLauncherPageCount * dotRadius * 2f +
            (quickLauncherPageCount - 1) * dotGap
        var cx = grid.centerX() - totalWidth / 2f + dotRadius
        val cy = grid.bottom - host.dp(10f)
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

    private fun drawGridCell(
        canvas: Canvas,
        cell: RectF,
        index: Int,
        label: String,
        iconProvider: () -> Bitmap?,
        longPressArmed: Boolean = false,
        iconSize: Float = quickLauncherGridIconSize,
        iconTopInset: Float = quickLauncherGridIconTopInset,
        iconLabelGap: Float = quickLauncherGridIconLabelGap,
        labelMaxWidth: Float = quickLauncherCellWidth - gridCellInset * 2,
    ) {
        if (index == host.panelGridSession().highlightedIndex) {
            tmpRect.set(cell.left + host.dp(3f), cell.top + host.dp(2f), cell.right - host.dp(3f), cell.bottom - host.dp(2f))
            val paint = if (longPressArmed) cellLongPressHighlightPaint else cellHighlightPaint
            canvas.drawRoundRect(tmpRect, host.dp(10f), host.dp(10f), paint)
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
            canvas.drawRoundRect(tmpRect, host.dp(10f), host.dp(10f), cellHighlightPaint)
            val initial = displayLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
            cellInitialPaint.textSize = host.sp(14f)
            canvas.drawText(
                initial,
                iconCenterX,
                iconTop + iconSize / 2f - (cellInitialPaint.descent() + cellInitialPaint.ascent()) / 2f,
                cellInitialPaint,
            )
        }
        canvas.drawText(displayLabel, iconCenterX, labelBaseline, appLabelPaint)
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "\u2026") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "\u2026"
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
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.gestureSession().endSession()
            return
        }
        quickLauncherExiting = true
        host.notifyPresentationTouchRequirementChanged()
        host.startPanelExitAnimation {
            quickLauncherExiting = false
            host.gestureSession().endSession()
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
        val trigger = host.activeTriggerZoneRect()
        val anchorY = quickLauncherAnchorLocalY().coerceIn(trigger.top, trigger.bottom)
        var top = anchorY - gh / 2f
        top = top.coerceSafe(host.dp(16f), host.viewHeight() - gh - host.dp(16f))
        val gap = host.dp(8f)
        val left = when (host.side()) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    private fun offsetQuickLauncherPanelForToolbar(panelRect: RectF): RectF {
        if (panelRect.isEmpty) return panelRect
        val margin = host.dp(16f)
        var left = panelRect.left
        var right = panelRect.right
        val reserve = quickLauncherPanelController.contentReserveWidth(host.settings())
        return when (host.side()) {
            PanelSide.LEFT -> {
                if (left < margin) {
                    val delta = margin - left
                    left += delta
                    right += delta
                }
                if (right > host.viewWidth() - margin) {
                    val delta = right - (host.viewWidth() - margin)
                    left -= delta
                    right = host.viewWidth() - margin
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
                if (right > host.viewWidth() - margin) {
                    val delta = right - (host.viewWidth() - margin)
                    left -= delta
                    right = host.viewWidth() - margin
                    left = left.coerceAtLeast(margin)
                }
                RectF(left, panelRect.top, right, panelRect.bottom)
            }
        }
    }

    companion object {
        private const val QUICK_LAUNCHER_MAX_ROWS = 6
        private const val QUICK_LAUNCHER_PAGE_SWIPE_DIRECTION_LOCK_DP = 8f
        private const val QUICK_LAUNCHER_PAGE_COMMIT_FRACTION = 0.22f
        private const val QUICK_LAUNCHER_PAGE_EDGE_RESISTANCE = 0.35f
        private const val QUICK_LAUNCHER_PAGE_SNAP_DURATION_MS = 180L
        private const val QUICK_LAUNCHER_EDGE_AUTO_PAGE_THRESHOLD_DP = 14f
    }
}
