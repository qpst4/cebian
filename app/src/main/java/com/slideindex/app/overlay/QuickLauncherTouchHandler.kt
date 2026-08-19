package com.slideindex.app.overlay

import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType

internal class QuickLauncherTouchHandler(
    internal val ctrl: QuickLauncherOverlayController,
) {
    internal val host get() = ctrl.host
    internal val pickResolver = QuickLauncherPickResolver(this)
    internal val scrollHandler = QuickLauncherScrollHandler(this)
    internal val managementHandler = QuickLauncherManagementTouchHandler(this)
    private var lastToolbarSwitchHoverTime: Long = 0L

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
            managementHandler.beginGesture(toolbarDown)
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
            if (!continuousPick && scrollHandler.consumePageRelease()) {
                return true
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scrollHandler.cancelPageSnapAnimation()
            ctrl.quickLauncherPageChangedThisGesture = false
            ctrl.quickLauncherPageDragOffset = 0f
            ctrl.quickLauncherPageSwipeLocked = false
            ctrl.quickLauncherEdgePageZone = 0
            ctrl.quickLauncherEdgeAutoPageSeeded = false
            if (!host.gestureSession().isMoveTimeActionLocked()) {
                ctrl.quickLauncherOpeningGestureActive = false
            }
            ctrl.quickLauncherPageSwipeStartX = touchX
            ctrl.quickLauncherPageSwipeStartY = localY
            ctrl.quickLauncherPageSwipeTracking = ctrl.quickLauncherPageCount > 1 &&
                host.panelContentRect().contains(touchX, localY)
        }
        val tapGesture = (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL) &&
            managementHandler.isTapGesture(touchX, localY)
        val toolbarCommitAllowed = managementHandler.toolbarCommitAllowed()
        if (ctrl.folderOpen || ctrl.folderGestureActive) {
            if (handleFolderTouch(event, touchX, localX, localY)) {
                return true
            }
        }
        if (!continuousPick && managementHandler.handleManagementTouch(
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
            if (ctrl.quickLauncherPanelController.isDragging()) {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    host.invalidate()
                }
                return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (continuousPick) {
                    if (pickResolver.continuousPickReady() &&
                        pickResolver.isSelectableTouch(localX, localY, panelRect)
                    ) {
                        pickResolver.updateHighlight(
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
                if (ctrl.quickLauncherPanelController.editMode) {
                    host.invalidate()
                    return true
                }
                if (!pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                    endQuickLauncherSessionAnimated()
                    host.invalidate()
                    return true
                }
                host.panelGridSession().updateHighlight(touchX, localY)
                pickResolver.syncPressTracking(event.eventTime)
                if (host.panelGridSession().highlightedIndex >= 0) {
                    host.hapticTick()
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (continuousPick && pickResolver.continuousPickReady()) {
                    ctrl.quickLauncherOpeningGestureActive = false
                }
                if (host.gestureSession().isMoveTimeActionLocked()) {
                    ctrl.quickLauncherPageSwipeStartX = touchX
                    ctrl.quickLauncherPageSwipeStartY = localY
                    ctrl.quickLauncherPageSwipeLocked = false
                }
                if (scrollHandler.consumePageSwipeMove(touchX, localY)) {
                    host.invalidate()
                    return true
                }
                if (host.gestureSession().isMoveTimeActionLocked() && !continuousPick) {
                    if (pickResolver.updateEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (ctrl.quickLauncherOpeningGestureActive &&
                    host.gestureSession().isMoveTimeActionLocked() &&
                    !continuousPick
                ) {
                    host.invalidate()
                    return true
                }
                if (pickResolver.updateEdgeTracking(event.rawY, localX, localY)) return true
                if (continuousPick) {
                    if (!pickResolver.continuousPickReady()) {
                        host.invalidate()
                        return true
                    }
                    if (ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)) {
                        pickResolver.clearHighlight()
                        val action = ctrl.quickLauncherPanelController.resolveToolbarAction(touchX, localY, panelRect)
                        if (action == QuickLauncherPanelToolbar.ToolbarAction.SWITCH) {
                            val now = event.eventTime
                            if (now - lastToolbarSwitchHoverTime > 500L) {
                                lastToolbarSwitchHoverTime = now
                                ctrl.switchToNextPanel()
                                host.invalidate()
                                return true
                            }
                        }
                        host.invalidate()
                        return true
                    }
                    if (scrollHandler.pageInteractionActive()) {
                        host.invalidate()
                        return true
                    }
                    if (!pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                        pickResolver.clearHighlight()
                        host.invalidate()
                        return true
                    }
                    scrollHandler.applyEdgeAutoPage(touchX)
                    pickResolver.updateHighlight(localX, localY, touchX, event.eventTime, haptic = true)
                    host.invalidate()
                    return true
                }
                val prev = host.panelGridSession().highlightedIndex
                if (pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                    host.panelGridSession().updateHighlight(touchX, localY)
                    if (host.panelGridSession().highlightedIndex != prev) {
                        pickResolver.syncPressTracking(event.eventTime)
                    }
                    if (host.panelGridSession().highlightedIndex != prev &&
                        host.panelGridSession().highlightedIndex >= 0
                    ) {
                        host.hapticTick()
                    }
                } else {
                    pickResolver.clearHighlight()
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (host.gestureSession().releaseImmediateGestureLock()) {
                    ctrl.quickLauncherOpeningGestureActive = false
                    host.onInitiatingEdgeGestureReleased()
                    host.setPanelPresentationFocus(true)
                    host.invalidate()
                    return true
                }
                ctrl.quickLauncherOpeningGestureActive = false
                if (continuousPick) {
                    if (ctrl.quickLauncherPageSwipeLocked) {
                        scrollHandler.finishPageDrag()
                        ctrl.quickLauncherPageSwipeLocked = false
                        ctrl.quickLauncherPageSwipeTracking = false
                        ctrl.quickLauncherPageChangedThisGesture = false
                        host.invalidate()
                        return true
                    }
                    if (ctrl.quickLauncherPageSnapMotion.isRunning) {
                        host.invalidate()
                        return true
                    }
                    if (managementHandler.tryCommitToolbarOnContinuousPickUp(touchX, localY, panelRect)) {
                        host.gestureSession().clearQuickLauncherContinuousPick()
                        host.gestureSession().finishLeaveOpenFingerTracking()
                        host.notifyPresentationTouchRequirementChanged()
                        host.onInitiatingEdgeGestureReleased()
                        host.setPanelPresentationFocus(true)
                        ctrl.quickLauncherPageSwipeLocked = false
                        ctrl.quickLauncherPageChangedThisGesture = false
                        host.invalidate()
                        return true
                    }
                    if (pickResolver.continuousPickReady() &&
                        pickResolver.isSelectableTouch(localX, localY, panelRect)
                    ) {
                        pickResolver.updateHighlight(localX, localY, touchX, event.eventTime, haptic = false)
                        val panelModeBeforeAction = host.gestureSession().panelMode()
                        if (host.panelGridSession().highlightedIndex >= 0 &&
                            pickResolver.performUpAction(event, touchX, localX, localY)
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
                        ctrl.quickLauncherPageSnapMotion.isRunning != true
                    ) {
                        endQuickLauncherSessionAnimated()
                    }
                    ctrl.quickLauncherPageSwipeLocked = false
                    ctrl.quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (ctrl.quickLauncherPageSwipeLocked) {
                    scrollHandler.finishPageDrag()
                    ctrl.quickLauncherPageSwipeLocked = false
                    ctrl.quickLauncherPageSwipeTracking = false
                    ctrl.quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (ctrl.quickLauncherPageSnapMotion.isRunning) {
                    host.invalidate()
                    return true
                }
                if (pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                    host.panelGridSession().updateHighlight(touchX, localY)
                } else {
                    pickResolver.clearHighlight()
                }
                val panelModeBeforeAction = host.gestureSession().panelMode()
                if (!ctrl.quickLauncherPageChangedThisGesture &&
                    pickResolver.performUpAction(event, touchX, localX, localY)
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

    fun applyEditDragAutoPage(touchX: Float, panelRect: RectF): Boolean =
        scrollHandler.applyEditDragAutoPage(touchX, panelRect)

    private fun handleFolderTouch(
        event: MotionEvent,
        touchX: Float,
        localX: Float,
        localY: Float,
    ): Boolean {
        val continuousPick = host.gestureSession().quickLauncherContinuousPickActive()
        val closeHit = ctrl.folderCloseButtonBounds.contains(touchX, localY)
        val addHit = ctrl.quickLauncherPanelController.editMode && ctrl.folderAddButtonBounds.contains(touchX, localY)
        val insideFolder = ctrl.folderRect.contains(touchX, localY)
        val isToolbarHit = ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ctrl.folderGestureActive = true
                if (isToolbarHit) {
                    return false
                }
                if (closeHit) {
                    ctrl.closeFolder()
                    return true
                }
                if (addHit) {
                    ctrl.quickLauncherPanelController.openAddDialog(ctrl.folderGlobalIndex)
                    return true
                }
                if (ctrl.quickLauncherPanelController.editMode) {
                    // Check delete badges / cells on folder child cells
                    val deleteHit = ctrl.folderCellBounds.indexOfFirst { (_, rect) ->
                        rect.contains(touchX, localY) ||
                            (touchX in (rect.left - host.dp(6f))..(rect.left + host.dp(24f)) &&
                             localY in (rect.top - host.dp(6f))..(rect.top + host.dp(24f)))
                    }
                    if (deleteHit >= 0) {
                        ctrl.quickLauncherPanelController.removeFolderChildItem(ctrl.folderGlobalIndex, deleteHit)
                        ctrl.folderSubPanelItems = ctrl.quickLauncherRootItems().getOrNull(ctrl.folderGlobalIndex)?.folderItems().orEmpty()
                        host.hapticTick()
                        host.invalidate()
                        return true
                    }
                }
                if (insideFolder) {
                    val hit = ctrl.folderCellBounds.indexOfFirst { it.second.contains(touchX, localY) }
                    ctrl.folderHighlightLocalIndex = hit
                    if (hit >= 0) {
                        host.hapticTick()
                        if (!ctrl.quickLauncherPanelController.editMode) {
                            ctrl.scheduleFolderLongPress(hit, event.eventTime)
                        }
                    } else {
                        ctrl.cancelFolderLongPress()
                    }
                    host.invalidate()
                    return true
                } else {
                    ctrl.closeFolder()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isToolbarHit) {
                    return false
                }
                if (!ctrl.folderOpen) {
                    return true
                }
                if (closeHit) {
                    if (continuousPick) {
                        ctrl.closeFolder()
                        host.hapticTick()
                        return true
                    }
                }
                if (insideFolder) {
                    val hit = ctrl.folderCellBounds.indexOfFirst { it.second.contains(touchX, localY) }
                    if (hit != ctrl.folderHighlightLocalIndex) {
                        ctrl.folderHighlightLocalIndex = hit
                        if (hit >= 0) {
                            host.hapticTick()
                            ctrl.scheduleFolderLongPress(hit, event.eventTime)
                        } else {
                            ctrl.cancelFolderLongPress()
                        }
                        host.invalidate()
                    }
                } else {
                    if (ctrl.folderHighlightLocalIndex >= 0) {
                        ctrl.folderHighlightLocalIndex = -1
                        ctrl.cancelFolderLongPress()
                        host.invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ctrl.folderGestureActive = false
                if (isToolbarHit) {
                    return false
                }
                if (!ctrl.folderOpen) {
                    return true
                }
                if (closeHit) {
                    ctrl.closeFolder()
                    return true
                }
                if (addHit) {
                    ctrl.quickLauncherPanelController.openAddDialog(ctrl.folderGlobalIndex)
                    return true
                }
                val hit = ctrl.folderHighlightLocalIndex
                if (hit in ctrl.folderSubPanelItems.indices && !ctrl.quickLauncherPanelController.editMode) {
                    val childItem = ctrl.folderSubPanelItems[hit]
                    val longPress = ctrl.isFolderLongPressTriggered(event)
                    ctrl.cancelFolderLongPress()
                    if (childItem.type == QuickLauncherItemType.ACTION) {
                        val action = QuickLauncherItemCodec.parseActionPayload(childItem.payload)
                        if (action != null) {
                            host.gestureSession().performQuickLauncherAction(
                                action,
                                localX,
                                localY,
                                event.rawY,
                                confirmHaptic = longPress,
                            )
                        }
                    } else {
                        if (longPress) host.hapticConfirmLaunch()
                        ctrl.quickLauncherLaunchEndDeferMs = if (
                            host.actionExecutor().launchQuickItem(
                                childItem,
                                host.settings(),
                                longPressArmed = longPress,
                                anchorRawY = event.rawY,
                            )
                        ) 280L else 0L
                    }
                    ctrl.closeFolder()
                    endQuickLauncherAfterLaunch(ctrl.quickLauncherLaunchEndDeferMs)
                    ctrl.quickLauncherLaunchEndDeferMs = 0L
                    return true
                } else if (!insideFolder && !closeHit && !addHit) {
                    ctrl.closeFolder()
                    return true
                }
                ctrl.folderHighlightLocalIndex = -1
                ctrl.cancelFolderLongPress()
                host.invalidate()
                return true
            }
        }
        return false
    }

    private fun endQuickLauncherAfterLaunch(deferMs: Long) {
        if (deferMs > 0L) {
            host.postDelayed({ host.gestureSession().endSession() }, deferMs)
        } else {
            host.post { host.gestureSession().endSession() }
        }
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
}
