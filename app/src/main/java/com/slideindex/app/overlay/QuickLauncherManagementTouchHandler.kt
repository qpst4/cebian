package com.slideindex.app.overlay

import android.graphics.RectF
import android.view.MotionEvent

internal class QuickLauncherManagementTouchHandler(
    private val touch: QuickLauncherTouchHandler,
) {
    private val ctrl get() = touch.ctrl
    private val scrollHandler get() = touch.scrollHandler

    fun handleManagementTouch(
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
        cellBounds = touch.host.panelGridSession().cellBounds,
        tapGesture = tapGesture,
        toolbarCommitAllowed = toolbarCommitAllowed,
    )

    fun isTapGesture(touchX: Float, localY: Float): Boolean {
        val dx = touchX - ctrl.quickLauncherPageSwipeStartX
        val dy = localY - ctrl.quickLauncherPageSwipeStartY
        return isWithinTapSlop(dx, dy, touch.host.dp(24f))
    }

    fun toolbarCommitAllowed(): Boolean {
        if (ctrl.quickLauncherPageSwipeLocked) return false
        if (ctrl.quickLauncherPageSnapMotion.isRunning) return false
        if (kotlin.math.abs(ctrl.quickLauncherPageDragOffset) > touch.host.dp(6f)) return false
        return true
    }

    fun beginGesture(toolbarDown: Boolean) {
        ctrl.quickLauncherPageChangedThisGesture = false
        ctrl.quickLauncherPageSwipeLocked = false
        if (toolbarDown) {
            scrollHandler.cancelPageSnapAnimation()
            ctrl.quickLauncherPageDragOffset = 0f
        }
    }

    fun tryCommitToolbarOnContinuousPickUp(
        touchX: Float,
        localY: Float,
        panelRect: RectF,
    ): Boolean {
        if (!toolbarCommitAllowed()) return false
        return ctrl.quickLauncherPanelController.commitToolbarAtRelease(
            localX = touchX,
            localY = localY,
            panelRect = panelRect,
            tapGesture = false,
            toolbarCommitAllowed = true,
            allowSlideRelease = true,
        )
    }

    companion object {
        internal fun isWithinTapSlop(dx: Float, dy: Float, slopPx: Float): Boolean =
            dx * dx + dy * dy <= slopPx * slopPx
    }
}
