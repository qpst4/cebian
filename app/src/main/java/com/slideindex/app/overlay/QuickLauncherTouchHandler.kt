package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedLaunchPolicy

internal class QuickLauncherTouchHandler(
    private val ctrl: QuickLauncherOverlayController,
) {
    private val host get() = ctrl.host

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (ctrl.quickLauncherExiting) return true
        val panelRect = ctrl.quickLauncherPanelRect()
        val contentRect = ctrl.quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = host.panelEnterAdjustedX(localX, contentRect)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            ctrl.quickLauncherPageSwipeStartX = touchX
            ctrl.quickLauncherPageSwipeStartY = localY
            val toolbarDown = ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)
            ctrl.quickLauncherToolbarTouchActive = toolbarDown
            beginQuickLauncherGesture(toolbarDown)
        }
        val toolbarTouchThisGesture = when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val armed = ctrl.quickLauncherToolbarTouchActive
                ctrl.quickLauncherToolbarTouchActive = false
                armed
            }
            else -> ctrl.quickLauncherToolbarTouchActive
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
        if (ctrl.quickLauncherPanelController.editMode && !continuousPick) {
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
                ctrl.quickLauncherPageChangedThisGesture = false
                ctrl.quickLauncherPageDragOffset = 0f
                ctrl.quickLauncherPageSwipeLocked = false
                ctrl.quickLauncherEdgePageZone = 0
                ctrl.quickLauncherEdgeAutoPageSeeded = false
                if (!host.gestureSession().isMoveTimeActionLocked()) {
                    ctrl.quickLauncherOpeningGestureActive = false
                }
                ctrl.quickLauncherPageSwipeTracking = ctrl.quickLauncherPageCount > 1 &&
                    host.panelContentRect().contains(touchX, localY) &&
                    !ctrl.quickLauncherPanelController.editMode
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
                    ctrl.quickLauncherOpeningGestureActive = false
                }
                if (host.gestureSession().isMoveTimeActionLocked()) {
                    ctrl.quickLauncherPageSwipeStartX = touchX
                    ctrl.quickLauncherPageSwipeStartY = localY
                    ctrl.quickLauncherPageSwipeLocked = false
                }
                if (consumeQuickLauncherPageSwipeMove(touchX, localY)) {
                    host.invalidate()
                    return true
                }
                if (host.gestureSession().isMoveTimeActionLocked() && !continuousPick) {
                    if (updateQuickLauncherEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (ctrl.quickLauncherOpeningGestureActive &&
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
                    ctrl.quickLauncherOpeningGestureActive = false
                    host.invalidate()
                    return true
                }
                ctrl.quickLauncherOpeningGestureActive = false
                if (continuousPick) {
                    if (ctrl.quickLauncherPageSwipeLocked) {
                        finishQuickLauncherPageDrag()
                        ctrl.quickLauncherPageSwipeLocked = false
                        ctrl.quickLauncherPageSwipeTracking = false
                        ctrl.quickLauncherPageChangedThisGesture = false
                        host.invalidate()
                        return true
                    }
                    if (ctrl.quickLauncherPageSnapAnimator?.isRunning == true) {
                        host.invalidate()
                        return true
                    }
                    if (tryCommitQuickLauncherToolbarOnContinuousPickUp(touchX, localY, panelRect)) {
                        host.gestureSession().clearQuickLauncherContinuousPick()
                        ctrl.quickLauncherPageSwipeLocked = false
                        ctrl.quickLauncherPageChangedThisGesture = false
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
                            endQuickLauncherAfterLaunch(ctrl.quickLauncherLaunchEndDeferMs)
                            ctrl.quickLauncherLaunchEndDeferMs = 0L
                        } else if (!ctrl.quickLauncherPanelController.editMode &&
                            !ctrl.quickLauncherOverlayDialogHost.isShowing &&
                            host.panelGridSession().highlightedIndex < 0 &&
                            !toolbarTouchThisGesture &&
                            host.gestureSession().panelMode() == panelModeBeforeAction
                        ) {
                            endQuickLauncherSessionAnimated()
                        }
                    } else if (!ctrl.quickLauncherPanelController.editMode &&
                        !ctrl.quickLauncherOverlayDialogHost.isShowing &&
                        !toolbarTouchThisGesture &&
                        ctrl.quickLauncherPageSnapAnimator?.isRunning != true
                    ) {
                        endQuickLauncherSessionAnimated()
                    }
                    ctrl.quickLauncherPageSwipeLocked = false
                    ctrl.quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (ctrl.quickLauncherPageSwipeLocked) {
                    finishQuickLauncherPageDrag()
                    ctrl.quickLauncherPageSwipeLocked = false
                    ctrl.quickLauncherPageSwipeTracking = false
                    ctrl.quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (ctrl.quickLauncherPageSnapAnimator?.isRunning == true) {
                    host.invalidate()
                    return true
                }
                if (isQuickLauncherSelectableTouch(localX, localY, panelRect)) {
                    host.panelGridSession().updateHighlight(touchX, localY)
                } else {
                    clearQuickLauncherHighlight()
                }
                val panelModeBeforeAction = host.gestureSession().panelMode()
                if (!ctrl.quickLauncherPageChangedThisGesture &&
                    performQuickLauncherUpAction(event, touchX, localX, localY)
                ) {
                    endQuickLauncherAfterLaunch(ctrl.quickLauncherLaunchEndDeferMs)
                    ctrl.quickLauncherLaunchEndDeferMs = 0L
                } else if (!ctrl.quickLauncherPanelController.editMode &&
                    !ctrl.quickLauncherOverlayDialogHost.isShowing &&
                    host.panelGridSession().highlightedIndex < 0 &&
                    !ctrl.quickLauncherPageChangedThisGesture &&
                    !toolbarTouchThisGesture &&
                    host.gestureSession().panelMode() == panelModeBeforeAction
                ) {
                    endQuickLauncherSessionAnimated()
                }
                ctrl.quickLauncherPageSwipeTracking = false
                ctrl.quickLauncherPageSwipeLocked = false
                ctrl.quickLauncherPageChangedThisGesture = false
                return true
            }
        }
        return false
    }

    fun applyEditDragAutoPage(touchX: Float, panelRect: RectF): Boolean {
        if (!ctrl.quickLauncherPanelController.isDragging()) return false
        if (!ctrl.quickLauncherPanelController.editMode) return false
        if (ctrl.quickLauncherPageCount <= 1) return false
        if (ctrl.quickLauncherPageSnapAnimator?.isRunning == true) return false
        if (panelRect.isEmpty) return false
        return applyQuickLauncherEdgeAutoPageInternal(touchX, panelRect)
    }

    private fun handleQuickLauncherManagementTouch(
        event: MotionEvent,
        x: Float,
        y: Float,
        panelRect: RectF,
        tapGesture: Boolean,
        toolbarCommitAllowed: Boolean,
    ): Boolean = ctrl.quickLauncherPanelController.handleManagementTouch(
        event = event,
        localX = x,
        localY = y,
        panelRect = panelRect,
        cellBounds = host.panelGridSession().cellBounds,
        tapGesture = tapGesture,
        toolbarCommitAllowed = toolbarCommitAllowed,
    )

    private fun isQuickLauncherTapGesture(touchX: Float, localY: Float): Boolean {
        val dx = touchX - ctrl.quickLauncherPageSwipeStartX
        val dy = localY - ctrl.quickLauncherPageSwipeStartY
        val slop = host.dp(24f)
        return dx * dx + dy * dy <= slop * slop
    }

    private fun quickLauncherToolbarCommitAllowed(): Boolean {
        if (ctrl.quickLauncherPageSwipeLocked) return false
        if (ctrl.quickLauncherPageSnapAnimator?.isRunning == true) return false
        if (kotlin.math.abs(ctrl.quickLauncherPageDragOffset) > host.dp(6f)) return false
        return true
    }

    private fun beginQuickLauncherGesture(toolbarDown: Boolean) {
        ctrl.quickLauncherPageChangedThisGesture = false
        ctrl.quickLauncherPageSwipeLocked = false
        if (toolbarDown) {
            cancelQuickLauncherPageSnapAnimation()
            ctrl.quickLauncherPageDragOffset = 0f
        }
    }

    private fun consumeQuickLauncherPageRelease(): Boolean {
        if (ctrl.quickLauncherPanelController.editMode || ctrl.quickLauncherPageCount <= 1) return false
        if (ctrl.quickLauncherPageSwipeLocked ||
            kotlin.math.abs(ctrl.quickLauncherPageDragOffset) > host.dp(6f)
        ) {
            finishQuickLauncherPageDrag()
            ctrl.quickLauncherPageSwipeLocked = false
            ctrl.quickLauncherPageSwipeTracking = false
            host.invalidate()
            return true
        }
        if (ctrl.quickLauncherPageSnapAnimator?.isRunning == true) {
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
        return ctrl.quickLauncherPanelController.commitToolbarAtRelease(
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
            if (index != ctrl.quickLauncherPressIndex) {
                scheduleQuickLauncherLongPress(index)
            }
            ctrl.quickLauncherPressIndex = index
            ctrl.quickLauncherPressDownTime = eventTime
        } else {
            cancelQuickLauncherLongPress()
            ctrl.quickLauncherPressIndex = -1
            ctrl.quickLauncherPressDownTime = 0L
        }
    }

    private fun scheduleQuickLauncherLongPress(index: Int) {
        cancelQuickLauncherLongPress()
        if (!quickLauncherLongPressEligible()) return
        ctrl.quickLauncherLongPressIndex = index
        val runnable = Runnable {
            if (host.panelGridSession().highlightedIndex == ctrl.quickLauncherLongPressIndex &&
                ctrl.quickLauncherLongPressIndex >= 0
            ) {
                ctrl.quickLauncherLongPressArmed = true
                host.hapticLongThreshold()
                host.invalidate()
            }
        }
        ctrl.quickLauncherLongPressRunnable = runnable
        host.postDelayed(runnable, host.settings().effectiveLongPressDurationMs().toLong())
    }

    private fun cancelQuickLauncherLongPress() {
        ctrl.quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
        ctrl.quickLauncherLongPressRunnable = null
        ctrl.quickLauncherLongPressIndex = -1
        ctrl.quickLauncherLongPressArmed = false
    }

    private fun performQuickLauncherUpAction(
        event: MotionEvent,
        touchX: Float,
        localX: Float,
        localY: Float,
    ): Boolean {
        if (ctrl.quickLauncherPanelController.editMode) return false
        val panelRect = ctrl.quickLauncherPanelRect()
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
                ctrl.quickLauncherLaunchEndDeferMs =
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
        if (ctrl.quickLauncherLongPressArmed) return true
        if (!quickLauncherLongPressEligible()) {
            return false
        }
        if (ctrl.quickLauncherPressIndex < 0 ||
            ctrl.quickLauncherPressIndex != host.panelGridSession().highlightedIndex
        ) {
            return false
        }
        return event.eventTime - ctrl.quickLauncherPressDownTime >= host.settings().effectiveLongPressDurationMs()
    }

    private fun quickLauncherContinuousPickReady(): Boolean = host.panelEnterProgress() >= 1f

    private fun quickLauncherPageInteractionActive(): Boolean =
        ctrl.quickLauncherPageSwipeLocked || ctrl.quickLauncherPageSnapAnimator?.isRunning == true

    private fun consumeQuickLauncherPageSwipeMove(touchX: Float, localY: Float): Boolean {
        if (host.gestureSession().isMoveTimeActionLocked()) return false
        if (host.gestureSession().quickLauncherContinuousPickActive()) return false
        if (ctrl.quickLauncherPanelController.editMode || ctrl.quickLauncherPageCount <= 1) return false
        val deltaX = touchX - ctrl.quickLauncherPageSwipeStartX
        val deltaY = localY - ctrl.quickLauncherPageSwipeStartY
        val absX = kotlin.math.abs(deltaX)
        val absY = kotlin.math.abs(deltaY)
        val directionLock = host.dp(QUICK_LAUNCHER_PAGE_SWIPE_DIRECTION_LOCK_DP)
        if (!ctrl.quickLauncherPageSwipeLocked) {
            if (absX > directionLock && absX > absY * 1.25f) {
                ctrl.quickLauncherPageSwipeLocked = true
                ctrl.quickLauncherPageSwipeTracking = true
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
        val panelWidth = ctrl.quickLauncherPanelWidthForPaging()
        var offset = deltaX
        if (ctrl.quickLauncherPageIndex <= 0 && offset > 0f) {
            offset *= QUICK_LAUNCHER_PAGE_EDGE_RESISTANCE
        } else if (ctrl.quickLauncherPageIndex >= ctrl.quickLauncherPageCount - 1 && offset < 0f) {
            offset *= QUICK_LAUNCHER_PAGE_EDGE_RESISTANCE
        }
        ctrl.quickLauncherPageDragOffset = offset.coerceIn(-panelWidth, panelWidth)
        ctrl.invalidateQuickLauncherPanel()
    }

    private fun finishQuickLauncherPageDrag() {
        val panelWidth = ctrl.quickLauncherPanelWidthForPaging()
        val threshold = panelWidth * QUICK_LAUNCHER_PAGE_COMMIT_FRACTION
        val offset = ctrl.quickLauncherPageDragOffset
        val delta = when {
            offset <= -threshold && ctrl.quickLauncherPageIndex < ctrl.quickLauncherPageCount - 1 -> 1
            offset >= threshold && ctrl.quickLauncherPageIndex > 0 -> -1
            else -> 0
        }
        if (delta != 0) {
            ctrl.quickLauncherPageIndex += delta
            ctrl.quickLauncherPageChangedThisGesture = true
            ctrl.quickLauncherPageDragOffset += if (delta > 0) panelWidth else -panelWidth
            syncQuickLauncherPageOffsetForDrag()
        }
        animateQuickLauncherPageSnapTo(0f)
    }

    private fun animateQuickLauncherPageSnapTo(targetOffset: Float) {
        ctrl.quickLauncherPageSnapAnimator?.cancel()
        val start = ctrl.quickLauncherPageDragOffset
        if (kotlin.math.abs(start - targetOffset) < host.dp(0.5f)) {
            ctrl.quickLauncherPageDragOffset = targetOffset
            ctrl.invalidateQuickLauncherPanel()
            return
        }
        ctrl.quickLauncherPageSnapAnimator = ValueAnimator.ofFloat(start, targetOffset).apply {
            duration = QUICK_LAUNCHER_PAGE_SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                ctrl.quickLauncherPageDragOffset = animator.animatedValue as Float
                ctrl.invalidateQuickLauncherPanel()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    ctrl.quickLauncherPageDragOffset = targetOffset
                    ctrl.invalidateQuickLauncherPanel()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    ctrl.quickLauncherPageDragOffset = targetOffset
                }
            })
            start()
        }
    }

    private fun cancelQuickLauncherPageSnapAnimation() {
        ctrl.quickLauncherPageSnapAnimator?.cancel()
        ctrl.quickLauncherPageSnapAnimator = null
    }

    private fun clearQuickLauncherHighlight() {
        host.panelGridSession().clearHighlight()
        ctrl.quickLauncherContinuousHapticIndex = -1
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
        val panelRect = ctrl.quickLauncherPanelRect()
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
            if (host.panelGridSession().highlightedIndex != ctrl.quickLauncherContinuousHapticIndex) {
                host.hapticTick()
                ctrl.quickLauncherContinuousHapticIndex = host.panelGridSession().highlightedIndex
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
        if (ctrl.quickLauncherPageCount <= 1) return false
        if (ctrl.quickLauncherPanelController.editMode) return false
        if (quickLauncherPageInteractionActive()) return false
        if (ctrl.quickLauncherPageSnapAnimator?.isRunning == true) return false
        val panelRect = ctrl.quickLauncherPanelRect()
        if (panelRect.isEmpty) return false
        return applyQuickLauncherEdgeAutoPageInternal(touchX, panelRect)
    }

    private fun applyQuickLauncherEdgeAutoPageInternal(touchX: Float, panelRect: RectF): Boolean {
        val zone = quickLauncherEdgePageZoneFor(touchX, panelRect)
        if (!ctrl.quickLauncherEdgeAutoPageSeeded) {
            ctrl.quickLauncherEdgeAutoPageSeeded = true
            ctrl.quickLauncherEdgePageZone = zone
            return false
        }
        val prevZone = ctrl.quickLauncherEdgePageZone
        ctrl.quickLauncherEdgePageZone = zone
        if (zone == 0 || zone == prevZone) return false

        val delta = when (zone) {
            -1 -> if (ctrl.quickLauncherPageIndex > 0) -1 else 0
            1 -> if (ctrl.quickLauncherPageIndex < ctrl.quickLauncherPageCount - 1) 1 else 0
            else -> 0
        }
        if (delta == 0) return false

        animateQuickLauncherPageTurn(delta)
        return true
    }

    private fun animateQuickLauncherPageTurn(delta: Int) {
        if (delta == 0) return
        if (ctrl.quickLauncherPageSnapAnimator?.isRunning == true) return
        cancelQuickLauncherPageSnapAnimation()
        val panelWidth = ctrl.quickLauncherPanelWidthForPaging()
        ctrl.quickLauncherPageIndex = (ctrl.quickLauncherPageIndex + delta)
            .coerceIn(0, ctrl.quickLauncherPageCount - 1)
        syncQuickLauncherPageOffsetForDrag()
        ctrl.quickLauncherPageChangedThisGesture = true
        ctrl.quickLauncherPageDragOffset += if (delta > 0) panelWidth else -panelWidth
        clearQuickLauncherHighlight()
        host.hapticTick()
        animateQuickLauncherPageSnapTo(0f)
    }

    private fun syncQuickLauncherPageOffsetForDrag() {
        val pageStart = ctrl.quickLauncherPageIndex * ctrl.quickLauncherPageSize()
        ctrl.quickLauncherPanelController.setItemPageOffset(pageStart)
        if (ctrl.quickLauncherPanelController.isDragging()) {
            ctrl.quickLauncherPanelController.syncPageLocalDragTarget()
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
        val contentRect = ctrl.quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = host.panelEnterAdjustedX(localX, contentRect)
        if (ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)) return true
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
            ctrl.quickLauncherAnchorRawY = rawY
            ctrl.quickLauncherFrozenAnchorLocalY = null
        }
        if (continuousPick && quickLauncherContinuousPickReady()) {
            val panelRect = ctrl.quickLauncherPanelRect()
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

    private fun endQuickLauncherSessionAnimated() {
        if (ctrl.quickLauncherExiting) return
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.gestureSession().endSession()
            return
        }
        ctrl.quickLauncherExiting = true
        host.notifyPresentationTouchRequirementChanged()
        host.startPanelExitAnimation {
            ctrl.quickLauncherExiting = false
            host.gestureSession().endSession()
        }
    }

    companion object {
        private const val QUICK_LAUNCHER_PAGE_SWIPE_DIRECTION_LOCK_DP = 8f
        private const val QUICK_LAUNCHER_PAGE_COMMIT_FRACTION = 0.22f
        private const val QUICK_LAUNCHER_PAGE_EDGE_RESISTANCE = 0.35f
        private const val QUICK_LAUNCHER_PAGE_SNAP_DURATION_MS = 180L
        private const val QUICK_LAUNCHER_EDGE_AUTO_PAGE_THRESHOLD_DP = 14f
    }
}
