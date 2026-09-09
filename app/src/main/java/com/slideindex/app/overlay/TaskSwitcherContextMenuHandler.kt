package com.slideindex.app.overlay

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout
import com.slideindex.app.util.TaskSwitcherMenuActions

internal class TaskSwitcherContextMenuHandler(
    private val touch: TaskSwitcherTouchHandler
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host
    private val pickResolver get() = touch.pickResolver

    fun handleContextMenuTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val menu = ctrl.taskSwitcherContextMenu ?: return false
        if (ctrl.taskSwitcherMenuAwaitingRelease) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                ctrl.taskSwitcherMenuAwaitingRelease = false
                ctrl.taskSwitcherMenuHighlight = -1
                host.invalidate()
            }
            return true
        }
        val panelLayout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        val panel = panelLayout.panelRect
        val touchX = host.panelEnterAdjustedX(localX, panel)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateMenuHighlight(touchX, localY, menu, haptic = true)
                if (ctrl.taskSwitcherMenuHighlight < 0 && !menu.menuRect.contains(touchX, localY)) {
                    ctrl.dismissTaskSwitcherContextMenu()
                    if (panel.contains(touchX, localY)) {
                        beginPanelDismissTap(localY)
                    }
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!menu.menuRect.contains(touchX, localY)) {
                    ctrl.dismissTaskSwitcherContextMenu()
                    return false
                }
                updateMenuHighlight(touchX, localY, menu, haptic = true)
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activateMenuSelection(menu, touchX, localY)) {
                    host.invalidate()
                    return true
                }
                if (!menu.menuRect.contains(touchX, localY)) {
                    ctrl.dismissTaskSwitcherContextMenu()
                    if (panel.contains(touchX, localY)) {
                        beginPanelDismissTap(localY)
                    }
                    return false
                }
                host.invalidate()
                return true
            }
        }
        return false
    }

    fun handleContinuousMenuMove(
        localX: Float,
        touchX: Float,
        localY: Float
    ): Boolean {
        val menu = ctrl.taskSwitcherContextMenu.takeIf { ctrl.taskSwitcherContextMenuActive() } ?: return false
        if (menu.menuRect.contains(touchX, localY)) {
            updateMenuHighlight(touchX, localY, menu, haptic = true)
            host.invalidate()
            return true
        }
        val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        if (!shouldDismissMenuForContinuousSlide(layout, localX, localY, menu)) {
            return false
        }
        dismissContextMenuForSlide()
        return false
    }

    fun handleContinuousMenuUp(touchX: Float, localY: Float): Boolean {
        val menu = ctrl.taskSwitcherContextMenu.takeIf { ctrl.taskSwitcherContextMenuActive() } ?: return false
        if (!menu.menuRect.contains(touchX, localY)) {
            dismissContextMenuForSlide()
            return false
        }
        if (activateMenuSelection(menu, touchX, localY)) {
            return true
        }
        dismissContextMenuForSlide()
        return false
    }

    fun showContextMenu(index: Int) {
        val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        val row = layout.rows.getOrNull(index) ?: return
        ctrl.taskSwitcherRowLongPressTriggered = true
        ctrl.taskSwitcherRowPressIndex = -1
        ctrl.taskSwitcherRowPressDownTime = 0L
        ctrl.taskSwitcherContinuousHapticKey = pickResolver.continuousPickHapticKey(TaskSwitcherPick(row = index))
        touch.longPressHandler.cancelRowLongPress()
        host.hapticConfirmLaunch()
        ctrl.taskSwitcherMenuEnterAnimator?.removeAllListeners()
        ctrl.taskSwitcherMenuEnterAnimator?.cancel()
        ctrl.taskSwitcherMenuEnterAnimator = null
        ctrl.taskSwitcherMenuDismissing = false
        val inlineInPanel = host.gestureSession().taskSwitcherContinuousPickActive()
        val anchorX = host.panelEnterAdjustedX(ctrl.lastTaskSwitcherTouchX, layout.panelRect)
        val anchorY = ctrl.lastTaskSwitcherTouchY
        val menu = TaskSwitcherContextMenuLayoutFactory.build(
            side = host.side(),
            panelRect = layout.panelRect,
            listRect = layout.listRect,
            rowIndex = index,
            packageName = row.entry.packageName,
            items = TaskSwitcherMenuActions.buildMenuItems(host.context.applicationContext),
            viewWidth = host.viewWidth(),
            viewHeight = host.viewHeight(),
            density = host.density(),
            anchorX = anchorX,
            anchorY = anchorY,
            inlineInPanel = inlineInPanel
        )
        ctrl.taskSwitcherContextMenu = menu
        startMenuEnterAnimation()
        if (host.gestureSession().taskSwitcherContinuousPickActive()) {
            ctrl.taskSwitcherMenuAwaitingRelease = false
            ctrl.taskSwitcherMenuHighlight = menu.itemRects.indexOfFirst { it.contains(anchorX, anchorY) }
        } else {
            ctrl.taskSwitcherMenuAwaitingRelease = true
            ctrl.taskSwitcherMenuHighlight = -1
        }
        host.invalidate()
    }

    fun shouldDismissMenuForContinuousSlide(
        layout: TaskSwitcherPanelLayout,
        localX: Float,
        localY: Float,
        menu: TaskSwitcherContextMenuLayout
    ): Boolean {
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
        if (menu.menuRect.contains(touchX, localY)) return false
        val pick = pickResolver.resolve(layout, localX, localY)
        if (pick.row == menu.rowIndex) return false
        if (pick.freeWindow == menu.rowIndex) return false
        if (pick.close == menu.rowIndex) return true
        if (pick.row >= 0 || pick.close >= 0 || pick.freeWindow >= 0 || pick.closeAll) return true
        return false
    }

    fun dismissContextMenuForSlide() {
        if (!ctrl.taskSwitcherContextMenuActive()) return
        ctrl.dismissTaskSwitcherContextMenu()
        ctrl.taskSwitcherRowLongPressTriggered = false
        ctrl.taskSwitcherRowPressIndex = -1
        ctrl.taskSwitcherRowPressDownTime = 0L
        touch.longPressHandler.cancelRowLongPress()
        touch.longPressHandler.resetCloseLongPressTracking()
    }

    private fun startMenuEnterAnimation() {
        ctrl.taskSwitcherMenuEnterAnimator?.removeAllListeners()
        ctrl.taskSwitcherMenuEnterAnimator?.cancel()
        ctrl.taskSwitcherMenuEnterAnimator = null
        ctrl.taskSwitcherMenuDismissing = false
        ctrl.taskSwitcherMenuEnterProgress = 0f
        ctrl.taskSwitcherMenuEnterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TaskSwitcherOverlayController.TASK_SWITCHER_MENU_ENTER_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { animator ->
                ctrl.taskSwitcherMenuEnterProgress = animator.animatedValue as Float
                host.invalidate()
            }
            start()
        }
        host.invalidate()
    }

    private fun updateMenuHighlight(
        touchX: Float,
        localY: Float,
        menu: TaskSwitcherContextMenuLayout,
        haptic: Boolean
    ) {
        val prev = ctrl.taskSwitcherMenuHighlight
        ctrl.taskSwitcherMenuHighlight = menu.itemRects.indexOfFirst { it.contains(touchX, localY) }
        if (haptic && ctrl.taskSwitcherMenuHighlight != prev && ctrl.taskSwitcherMenuHighlight >= 0) {
            host.hapticTick()
        }
    }

    private fun activateMenuSelection(
        menu: TaskSwitcherContextMenuLayout,
        touchX: Float,
        localY: Float
    ): Boolean {
        val selected = menu.itemRects.indexOfFirst { it.contains(touchX, localY) }
        ctrl.taskSwitcherMenuHighlight = -1
        if (selected < 0) return false
        val item = menu.items.getOrNull(selected) ?: return true
        host.hapticConfirmLaunch()
        executeMenuItem(menu.packageName, item)
        return true
    }

    private fun executeMenuItem(packageName: String, item: TaskSwitcherMenuItem) {
        ctrl.dismissTaskSwitcherContextMenu()
        val endSessionOnFreeWindow = item.type == TaskSwitcherMenuItemType.FREE_WINDOW
        TaskSwitcherMenuActions.execute(
            context = host.context,
            item = item,
            packageName = packageName,
            settings = host.settings(),
            appRepository = host.appRepository(),
            onSessionEnd = if (endSessionOnFreeWindow) {
                { ctrl.endTaskSwitcherSession() }
            } else {
                null
            }
        )
        if (item.type == TaskSwitcherMenuItemType.APP_INFO) {
            ctrl.endTaskSwitcherSession()
        }
    }

    private fun beginPanelDismissTap(localY: Float) {
        touch.scrollHandler.beginScrollDrag(localY)
        touch.clearTaskSwitcherPickHighlights()
        ctrl.taskSwitcherRowPressIndex = -1
        ctrl.taskSwitcherClosePressIndex = -1
        touch.longPressHandler.cancelRowLongPress()
        touch.longPressHandler.cancelCloseLongPress()
    }
}
