package com.slideindex.app.overlay

import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout
import com.slideindex.app.util.TaskSwitcherLockStore

internal class TaskSwitcherLongPressHandler(
    private val touch: TaskSwitcherTouchHandler
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host
    private val pickResolver get() = touch.pickResolver

    fun cancelCloseLongPress() {
        ctrl.taskSwitcherCloseLongPressRunnable?.let { host.removeCallbacks(it) }
        ctrl.taskSwitcherCloseLongPressRunnable = null
    }

    fun cancelRowLongPress() {
        ctrl.taskSwitcherRowLongPressRunnable?.let { host.removeCallbacks(it) }
        ctrl.taskSwitcherRowLongPressRunnable = null
    }

    fun scheduleCloseLongPress(index: Int, packageName: String) {
        cancelCloseLongPress()
        ctrl.taskSwitcherCloseLongPressRunnable = Runnable {
            if (host.gestureSession().panelMode() != OverlayPanelMode.TASK_SWITCHER) return@Runnable
            if (ctrl.taskSwitcherClosePressIndex != index) return@Runnable
            if (ctrl.taskSwitcherCloseLongPressTriggered) return@Runnable
            ctrl.taskSwitcherCloseLongPressTriggered = true
            val locked = ctrl.recentApps.getOrNull(index)?.isLocked != true
            toggleLock(index, packageName, locked)
        }
        host.postDelayed(ctrl.taskSwitcherCloseLongPressRunnable!!, closeDwellMs())
    }

    fun scheduleRowLongPress(index: Int) {
        cancelRowLongPress()
        ctrl.taskSwitcherRowLongPressRunnable = Runnable {
            if (ctrl.taskSwitcherRowPressIndex != index) return@Runnable
            if (ctrl.taskSwitcherCloseHighlight == index) return@Runnable
            touch.contextMenuHandler.showContextMenu(index)
        }
        host.postDelayed(
            ctrl.taskSwitcherRowLongPressRunnable!!,
            TaskSwitcherOverlayController.TASK_SWITCHER_LONG_PRESS_MS
        )
    }

    fun syncRowLongPress(pick: TaskSwitcherPick, eventTime: Long) {
        if (shouldSkipRowLongPressSync(
                host.gestureSession().taskSwitcherContinuousPickActive(),
                ctrl.taskSwitcherContextMenuActive(),
                ctrl.taskSwitcherRowLongPressTriggered
            )
        ) {
            return
        }
        if (pick.row >= 0) {
            if (!shouldArmRowLongPress(
                    pickRow = pick.row,
                    closePressIndex = ctrl.taskSwitcherClosePressIndex,
                    closeLongPressScheduled = ctrl.taskSwitcherCloseLongPressRunnable != null,
                    closeLongPressTriggered = ctrl.taskSwitcherCloseLongPressTriggered,
                    currentRowPressIndex = ctrl.taskSwitcherRowPressIndex
                )
            ) {
                return
            }
            ctrl.taskSwitcherRowPressIndex = pick.row
            ctrl.taskSwitcherRowPressDownTime = eventTime
            scheduleRowLongPress(pick.row)
        } else {
            cancelRowLongPress()
            ctrl.taskSwitcherRowPressIndex = -1
            ctrl.taskSwitcherRowPressDownTime = 0L
        }
    }

    fun syncCloseLongPress(
        pick: TaskSwitcherPick,
        layout: TaskSwitcherPanelLayout,
        eventTime: Long
    ) {
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) return
        if (ctrl.taskSwitcherContextMenuActive()) return
        if (pick.close >= 0) {
            if (ctrl.taskSwitcherRowLongPressRunnable != null && pick.close == ctrl.taskSwitcherRowPressIndex) {
                return
            }
            val packageName = layout.rows.getOrNull(pick.close)?.entry?.packageName ?: return
            if (ctrl.taskSwitcherCloseLongPressTriggered && ctrl.taskSwitcherClosePressIndex == pick.close) {
                return
            }
            if (ctrl.taskSwitcherClosePressIndex != pick.close) {
                ctrl.taskSwitcherClosePressIndex = pick.close
                ctrl.taskSwitcherClosePressDownTime = eventTime
                ctrl.taskSwitcherCloseLongPressTriggered = false
                scheduleCloseLongPress(pick.close, packageName)
            }
        } else if (ctrl.taskSwitcherClosePressIndex >= 0) {
            when {
                pick.row >= 0 && pick.row == ctrl.taskSwitcherClosePressIndex -> {
                    resetCloseLongPressTracking()
                }
                pick.freeWindow >= 0 && pick.freeWindow == ctrl.taskSwitcherClosePressIndex -> {
                    resetCloseLongPressTracking()
                }
                pickResolver.continuousPickTargetIndex(pick) == -1 -> {
                    if (ctrl.taskSwitcherCloseLongPressRunnable != null) return
                    resetCloseLongPressTracking()
                }
                else -> resetCloseLongPressTracking()
            }
        } else {
            resetCloseLongPressTracking()
        }
    }

    fun resetCloseLongPressTracking() {
        cancelCloseLongPress()
        ctrl.taskSwitcherClosePressIndex = -1
        ctrl.taskSwitcherClosePressDownTime = 0L
        ctrl.taskSwitcherCloseLongPressTriggered = false
    }

    fun clearContinuousLongPressTracking() {
        cancelRowLongPress()
        ctrl.taskSwitcherRowPressIndex = -1
        ctrl.taskSwitcherRowPressDownTime = 0L
        ctrl.taskSwitcherRowLongPressTriggered = false
        resetCloseLongPressTracking()
    }

    fun toggleLock(index: Int, packageName: String, locked: Boolean) {
        if (ctrl.recentApps.getOrNull(index)?.app?.packageName != packageName) return
        TaskSwitcherLockStore.setLocked(host.context, packageName, locked)
        ctrl.recentApps[index] = ctrl.recentApps[index].copy(isLocked = locked)
        ctrl.taskSwitcherLayout = null
        host.hapticConfirmLaunch()
        host.invalidate()
    }

    private fun closeDwellMs(): Long =
        closeLongPressDwellMs(host.gestureSession().taskSwitcherContinuousPickActive())

    companion object {
        internal fun closeLongPressDwellMs(continuousPickActive: Boolean): Long =
            if (continuousPickActive) {
                TaskSwitcherOverlayController.TASK_SWITCHER_CLOSE_CONTINUOUS_DWELL_MS
            } else {
                TaskSwitcherOverlayController.TASK_SWITCHER_CLOSE_DWELL_MS
            }

        internal fun shouldSkipRowLongPressSync(
            continuousPickActive: Boolean,
            contextMenuActive: Boolean,
            rowLongPressTriggered: Boolean
        ): Boolean = !continuousPickActive || contextMenuActive || rowLongPressTriggered

        internal fun shouldArmRowLongPress(
            pickRow: Int,
            closePressIndex: Int,
            closeLongPressScheduled: Boolean,
            closeLongPressTriggered: Boolean,
            currentRowPressIndex: Int
        ): Boolean {
            if (pickRow < 0) return false
            if (pickRow == closePressIndex && (closeLongPressScheduled || closeLongPressTriggered)) {
                return false
            }
            return pickRow != currentRowPressIndex
        }
    }
}
