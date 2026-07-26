package com.slideindex.app.overlay

import android.graphics.RectF
import com.slideindex.app.overlay.layout.TaskSwitcherLayoutEngine
import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout
import com.slideindex.app.overlay.layout.TaskSwitcherRowLayout

internal data class TaskSwitcherPick(
    val row: Int = -1,
    val close: Int = -1,
    val freeWindow: Int = -1,
    val closeAll: Boolean = false,
)

internal class TaskSwitcherPickResolver(
    private val touch: TaskSwitcherTouchHandler,
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host

    fun resolve(
        layout: TaskSwitcherPanelLayout,
        localX: Float,
        localY: Float,
    ): TaskSwitcherPick {
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
        if (layout.closeAllRect.contains(touchX, localY)) {
            return TaskSwitcherPick(closeAll = true)
        }
        layout.rows.forEachIndexed { index, row ->
            if (closePickMatches(localX, localY, row, layout)) {
                return TaskSwitcherPick(close = index)
            }
            val handleX = if (layout.panelRect.contains(touchX, localY)) touchX else localX
            if (row.freeWindowRect.contains(handleX, localY) &&
                hitVisible(row.freeWindowRect, layout.listRect)
            ) {
                return TaskSwitcherPick(freeWindow = index)
            }
            if (row.rowRect.contains(touchX, localY) && hitVisible(row.rowRect, layout.listRect)) {
                return TaskSwitcherPick(row = index)
            }
        }
        return TaskSwitcherPick()
    }

    fun apply(pick: TaskSwitcherPick, haptic: Boolean): Boolean {
        val changed = pick.row != ctrl.taskSwitcherRowHighlight ||
            pick.close != ctrl.taskSwitcherCloseHighlight ||
            pick.freeWindow != ctrl.taskSwitcherFreeWindowHighlight ||
            pick.closeAll != ctrl.taskSwitcherCloseAllHighlight
        ctrl.taskSwitcherRowHighlight = pick.row
        ctrl.taskSwitcherCloseHighlight = pick.close
        ctrl.taskSwitcherFreeWindowHighlight = pick.freeWindow
        ctrl.taskSwitcherCloseAllHighlight = pick.closeAll
        if (changed && haptic && (pick.row >= 0 || pick.close >= 0 || pick.freeWindow >= 0 || pick.closeAll)) {
            val skipCloseRetick = pick.close >= 0 &&
                !host.gestureSession().taskSwitcherContinuousPickActive() &&
                pick.close == ctrl.taskSwitcherCloseHapticIndex
            val hapticKey = continuousPickHapticKey(pick)
            val skipContinuousRetick = hapticKey >= 0 &&
                host.gestureSession().taskSwitcherContinuousPickActive() &&
                hapticKey == ctrl.taskSwitcherContinuousHapticKey
            if (!skipCloseRetick && !skipContinuousRetick) {
                host.hapticTick()
                if (pick.close >= 0 && !host.gestureSession().taskSwitcherContinuousPickActive()) {
                    ctrl.taskSwitcherCloseHapticIndex = pick.close
                }
                if (hapticKey >= 0 && host.gestureSession().taskSwitcherContinuousPickActive()) {
                    ctrl.taskSwitcherContinuousHapticKey = hapticKey
                }
            }
        }
        return changed
    }

    fun updateContinuous(
        layout: TaskSwitcherPanelLayout,
        pick: TaskSwitcherPick,
        eventTime: Long,
        haptic: Boolean,
    ) {
        if (host.gestureSession().taskSwitcherContinuousPickActive() && !touch.continuousPickReady()) {
            return
        }
        touch.longPressHandler.syncRowLongPress(pick, eventTime)
        touch.longPressHandler.syncCloseLongPress(pick, layout, eventTime)
        apply(pick, haptic = haptic)
    }

    fun isDownPickHeld(
        localX: Float,
        localY: Float,
        layout: TaskSwitcherPanelLayout,
    ): Boolean {
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
        when {
            ctrl.taskSwitcherRowPressIndex >= 0 -> {
                val row = layout.rows.getOrNull(ctrl.taskSwitcherRowPressIndex) ?: return false
                return row.rowRect.contains(touchX, localY) &&
                    hitVisible(row.rowRect, layout.listRect)
            }
            ctrl.taskSwitcherClosePressIndex >= 0 -> {
                val row = layout.rows.getOrNull(ctrl.taskSwitcherClosePressIndex) ?: return false
                return closePickMatches(localX, localY, row, layout)
            }
            ctrl.taskSwitcherFreeWindowHighlight >= 0 -> {
                val row = layout.rows.getOrNull(ctrl.taskSwitcherFreeWindowHighlight) ?: return false
                val handleX = if (layout.panelRect.contains(touchX, localY)) touchX else localX
                return row.freeWindowRect.contains(handleX, localY) &&
                    hitVisible(row.freeWindowRect, layout.listRect)
            }
            ctrl.taskSwitcherCloseAllHighlight -> return layout.closeAllRect.contains(touchX, localY)
            else -> return false
        }
    }

    fun isInteractiveTouch(
        localX: Float,
        localY: Float,
        layout: TaskSwitcherPanelLayout,
    ): Boolean {
        if (isPanelTouch(localX, localY, layout.panelRect)) return true
        if (isInCloseApproachZone(localX, layout)) {
            return layout.rows.any { row ->
                hitVisible(row.rowRect, layout.listRect) &&
                    localY >= row.rowRect.top &&
                    localY <= row.rowRect.bottom
            }
        }
        layout.rows.forEach { row ->
            if (!hitVisible(row.rowRect, layout.listRect)) return@forEach
            if (row.freeWindowRect.contains(localX, localY)) {
                return true
            }
        }
        return false
    }

    fun continuousPickHapticKey(pick: TaskSwitcherPick): Int = when {
        pick.closeAll -> Int.MIN_VALUE
        pick.close >= 0 -> (pick.close shl 2) or 1
        pick.row >= 0 -> pick.row shl 2
        pick.freeWindow >= 0 -> (pick.freeWindow shl 2) or 2
        else -> -1
    }

    fun continuousPickTargetIndex(pick: TaskSwitcherPick): Int = when {
        pick.close >= 0 -> pick.close
        pick.row >= 0 -> pick.row
        pick.freeWindow >= 0 -> pick.freeWindow
        else -> -1
    }

    private fun closeApproachXRange(layout: TaskSwitcherPanelLayout): Pair<Float, Float>? {
        var minLeft = Float.MAX_VALUE
        var maxRight = Float.MIN_VALUE
        var hasVisibleRow = false
        layout.rows.forEach { row ->
            if (!RectF.intersects(layout.listRect, row.rowRect)) return@forEach
            hasVisibleRow = true
            val hit = TaskSwitcherLayoutEngine.closeHitRect(host, row.rowRect)
            minLeft = minOf(minLeft, hit.left)
            maxRight = maxOf(maxRight, hit.right)
        }
        if (!hasVisibleRow) return null
        return minLeft to maxRight
    }

    private fun isInCloseApproachZone(localX: Float, layout: TaskSwitcherPanelLayout): Boolean {
        val (left, right) = closeApproachXRange(layout) ?: return false
        if (localX < left || localX > right) return false
        val sampleRow = layout.rows.firstOrNull { RectF.intersects(layout.listRect, it.rowRect) } ?: return false
        val column = TaskSwitcherLayoutEngine.closeColumnRect(host, sampleRow.rowRect)
        val panelInteriorStart = when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM -> column.right + host.dp(2f)
            PanelSide.RIGHT -> layout.panelRect.left + host.dp(2f)
        }
        val panelInteriorEnd = when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM -> layout.panelRect.right - host.dp(2f)
            PanelSide.RIGHT -> column.left - host.dp(2f)
        }
        if (localX in panelInteriorStart..panelInteriorEnd) return false
        return true
    }

    private fun closePickMatches(
        localX: Float,
        localY: Float,
        row: TaskSwitcherRowLayout,
        layout: TaskSwitcherPanelLayout,
    ): Boolean {
        if (!hitVisible(row.closeRect, layout.listRect)) return false
        if (row.closeRect.contains(localX, localY)) return true
        if (!isInCloseApproachZone(localX, layout)) return false
        return localY >= row.rowRect.top && localY <= row.rowRect.bottom
    }

    private fun hitVisible(rect: RectF, listRect: RectF): Boolean =
        RectF.intersects(listRect, rect)

    private fun isPanelTouch(localX: Float, localY: Float, panel: RectF): Boolean =
        panel.contains(localX, localY)
}
