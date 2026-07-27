package com.slideindex.app.overlay

import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout

internal class TaskSwitcherScrollHandler(
    private val touch: TaskSwitcherTouchHandler,
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host
    private val overscrollMotion get() = ctrl.taskSwitcherOverscrollMotion
    fun beginScrollDrag(localY: Float) {
        ctrl.taskSwitcherScrollDragStartY = localY
        ctrl.taskSwitcherScrollDragStartOffset = ctrl.taskSwitcherScrollOffset
        ctrl.taskSwitcherScrollDragging = false
    }

    fun handleScrollMove(touchX: Float, localY: Float): Boolean {
        val layout = ctrl.taskSwitcherLayout ?: return false
        if (!layout.listRect.contains(touchX, localY)) return false
        val canScroll = layout.maxScrollOffset > 0f
        val canOverscroll = ctrl.taskSwitcherOverscrollEnabled()
        if (!canScroll && !canOverscroll) return false
        val dy = localY - ctrl.taskSwitcherScrollDragStartY
        if (!ctrl.taskSwitcherScrollDragging &&
            kotlin.math.abs(dy) <= host.dp(TaskSwitcherOverlayController.TASK_SWITCHER_SCROLL_SLOP_DP)
        ) {
            return false
        }
        if (!ctrl.taskSwitcherScrollDragging) {
            ctrl.taskSwitcherScrollDragging = true
            cancelOverscrollAnimation()
            touch.longPressHandler.cancelCloseLongPress()
            touch.longPressHandler.cancelRowLongPress()
            ctrl.taskSwitcherClosePressIndex = -1
            ctrl.taskSwitcherRowPressIndex = -1
            touch.clearTaskSwitcherPickHighlights()
        }
        val rawOffset = ctrl.taskSwitcherScrollDragStartOffset + ctrl.taskSwitcherScrollDragStartY - localY
        if (!canScroll) {
            ctrl.taskSwitcherScrollOffset = 0f
            ctrl.taskSwitcherOverscrollOffset = -rubberBand(rawOffset)
            host.invalidate()
            return true
        }
        when {
            rawOffset < 0f -> {
                ctrl.taskSwitcherScrollOffset = 0f
                ctrl.taskSwitcherOverscrollOffset = if (canOverscroll) {
                    -rubberBand(rawOffset)
                } else {
                    0f
                }
            }
            rawOffset > layout.maxScrollOffset -> {
                ctrl.taskSwitcherScrollOffset = layout.maxScrollOffset
                val excess = rawOffset - layout.maxScrollOffset
                ctrl.taskSwitcherOverscrollOffset = if (canOverscroll) {
                    -rubberBand(excess)
                } else {
                    0f
                }
            }
            else -> {
                ctrl.taskSwitcherScrollOffset = rawOffset
                ctrl.taskSwitcherOverscrollOffset = 0f
            }
        }
        ctrl.taskSwitcherLayout = null
        markGestureScrolledIfNeeded()
        host.invalidate()
        return true
    }

    fun finishScrollDrag(): Boolean {
        val wasDragging = ctrl.taskSwitcherScrollDragging
        ctrl.taskSwitcherScrollDragging = false
        if (wasDragging && ctrl.taskSwitcherOverscrollEnabled()) {
            releaseOverscroll()
        }
        return wasDragging
    }

    fun applyEdgeAutoScroll(layout: TaskSwitcherPanelLayout, localY: Float): Boolean {
        val edge = host.dp(20f)
        val step = host.dp(10f)
        when {
            localY < layout.listRect.top + edge && ctrl.taskSwitcherScrollOffset > 0f -> {
                val next = (ctrl.taskSwitcherScrollOffset - step).coerceAtLeast(0f)
                if (next == ctrl.taskSwitcherScrollOffset) return false
                ctrl.taskSwitcherScrollOffset = next
                ctrl.taskSwitcherLayout = null
                host.invalidate()
                return true
            }
            localY > layout.listRect.bottom - edge &&
                ctrl.taskSwitcherScrollOffset < layout.maxScrollOffset -> {
                val next = (ctrl.taskSwitcherScrollOffset + step).coerceAtMost(layout.maxScrollOffset)
                if (next == ctrl.taskSwitcherScrollOffset) return false
                ctrl.taskSwitcherScrollOffset = next
                ctrl.taskSwitcherLayout = null
                host.invalidate()
                return true
            }
            else -> return false
        }
    }

    fun scrollToFollowFinger(localY: Float) {
        if (ctrl.recentApps.isEmpty()) return
        val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        val rowHeight = host.dp(42f)
        val fingerInList = (localY - layout.listRect.top).coerceIn(0f, layout.listRect.height())
        val contentY = fingerInList + layout.scrollOffset
        val index = (contentY / rowHeight).toInt().coerceIn(0, ctrl.recentApps.lastIndex)
        val desiredOffset = index * rowHeight + rowHeight / 2f - fingerInList
        val clamped = desiredOffset.coerceIn(0f, layout.maxScrollOffset)
        if (kotlin.math.abs(clamped - ctrl.taskSwitcherScrollOffset) < 0.5f) return
        ctrl.taskSwitcherScrollOffset = clamped
        ctrl.taskSwitcherLayout = null
        markGestureScrolledIfNeeded()
    }

    fun markGestureScrolledIfNeeded() {
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) {
            ctrl.taskSwitcherGestureScrolled = true
        }
    }

    fun cancelOverscrollAnimation() {
        overscrollMotion.cancel()
    }
    private fun rubberBand(rawExcess: Float): Float {
        val sign = if (rawExcess >= 0f) 1f else -1f
        val resisted = kotlin.math.abs(rawExcess) * TaskSwitcherOverlayController.TASK_SWITCHER_OVERSCROLL_RESISTANCE
        return sign * resisted.coerceAtMost(host.dp(TaskSwitcherOverlayController.TASK_SWITCHER_OVERSCROLL_MAX_DP))
    }

    private fun releaseOverscroll() {
        if (kotlin.math.abs(ctrl.taskSwitcherOverscrollOffset) < 0.5f) {
            ctrl.taskSwitcherOverscrollOffset = 0f
            return
        }
        cancelOverscrollAnimation()
        val start = ctrl.taskSwitcherOverscrollOffset
        overscrollMotion.animateTo(
            start = start,
            target = 0f,
            epsilon = 0.5f,
            onValue = { value ->
                ctrl.taskSwitcherOverscrollOffset = value
                host.invalidate()
            },
            onComplete = {
                ctrl.taskSwitcherOverscrollOffset = 0f
                host.invalidate()
            },
        )
    }
}