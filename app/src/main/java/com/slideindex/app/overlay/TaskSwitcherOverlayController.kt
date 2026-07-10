package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore
import com.slideindex.app.util.TaskSwitcherMenuActions
import com.slideindex.app.util.coerceSafe
import kotlin.math.min

internal class TaskSwitcherOverlayController(
    private val host: Host,
) {
    interface Host {
        val context: Context
        fun settings(): AppSettings
        fun side(): PanelSide
        fun appRepository(): AppRepository
        fun gestureSession(): GestureSession
        fun zoneLayout(): GestureZoneLayout
        fun pathRecognizer(): SwipePathRecognizer
        fun actionExecutor(): ActionExecutor
        fun panelEnterProgress(): Float
        fun panelEnterAdjustedX(localX: Float, panel: RectF): Float
        fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit)
        fun panelContentRect(): RectF
        fun activeTriggerZoneRect(): RectF
        fun viewWidth(): Int
        fun viewHeight(): Int
        fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun density(): Float
        fun viewLocationOnScreen(): IntArray
        fun invalidate()
        fun post(action: () -> Unit)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
        fun hapticTick()
        fun hapticConfirmLaunch()
        fun iconFor(app: AppInfo): Bitmap
        fun startPanelExitAnimation(onEnd: () -> Unit)
    }

    private var recentApps = mutableListOf<RecentAppEntry>()
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
    private var taskSwitcherExternalAnchor = false
    private var taskSwitcherFrozenAnchorLocalY: Float? = null
    private var taskSwitcherScrollOffset = 0f
    private var taskSwitcherScrollDragging = false
    private var taskSwitcherScrollDragStartY = 0f
    private var taskSwitcherScrollDragStartOffset = 0f
    private var taskSwitcherOverscrollOffset = 0f
    private var taskSwitcherOverscrollAnimator: ValueAnimator? = null
    private var taskSwitcherGestureScrolled = false
    private var taskSwitcherExiting = false
    private var taskSwitcherLoading = false
    private data class TaskSwitcherPick(
        val row: Int = -1,
        val close: Int = -1,
        val freeWindow: Int = -1,
        val closeAll: Boolean = false,
    )

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        lastTaskSwitcherTouchX = localX
        lastTaskSwitcherTouchY = localY
        if (taskSwitcherExiting) return true
        val continuousPick = host.gestureSession().taskSwitcherContinuousPickActive()
        if (taskSwitcherContextMenuActive() && !continuousPick) {
            if (handleTaskSwitcherContextMenuTouch(event, localX, localY)) {
                return true
            }
        }
        val layout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
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
                    host.invalidate()
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
                if (host.gestureSession().isMoveTimeActionLocked()) {
                    if (updateTaskSwitcherEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (updateTaskSwitcherEdgeTracking(event.rawY, localX, localY)) return true
                if (continuousPick) {
                    if (!taskSwitcherContinuousPickReady()) {
                        host.invalidate()
                        return true
                    }
                    if (!isTaskSwitcherInteractiveTouch(localX, localY, layout)) {
                        clearTaskSwitcherContinuousLongPressTracking()
                        clearTaskSwitcherPickHighlights()
                        host.invalidate()
                        return true
                    }
                    applyTaskSwitcherEdgeAutoScroll(layout, localY)
                    val current = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
                    val pick = resolveTaskSwitcherPick(current, localX, localY)
                    val menu = taskSwitcherContextMenu.takeIf { taskSwitcherContextMenuActive() }
                    if (menu != null &&
                        !shouldDismissTaskSwitcherMenuForContinuousSlide(current, localX, localY, menu)
                    ) {
                        host.invalidate()
                        return true
                    }
                    updateContinuousTaskSwitcherPick(current, pick, event.eventTime, haptic = true)
                    host.invalidate()
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
                if (host.gestureSession().releaseImmediateGestureLock()) {
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
                val continuousPick = host.gestureSession().taskSwitcherContinuousPickActive()
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
                            host.hapticConfirmLaunch()
                            val entry = row.entry
                            if (entry.taskId > 0) {
                                recentApps.removeAll { it.taskId == entry.taskId }
                                RecentTasksLoader.removeTaskIds(listOf(entry.taskId))
                            } else {
                                recentApps.removeAll { it.app.packageName == packageName }
                                RecentTasksLoader.removePackages(listOf(packageName))
                            }
                            TaskSwitcherLockStore.setLocked(host.context, packageName, locked = false)
                            taskSwitcherLayout = null
                            if (recentApps.isEmpty()) {
                                endTaskSwitcherSession()
                            } else {
                                host.invalidate()
                            }
                            dismissTaskCards(listOf(entry))
                        }
                    }
                }
            }
            taskSwitcherCloseAllHighlight -> {
                host.hapticConfirmLaunch()
                val entries = recentApps.filterNot { it.isLocked }
                val packages = entries.map { it.app.packageName }
                val dismissed = packages.toSet()
                recentApps.removeAll { it.app.packageName in dismissed }
                RecentTasksLoader.removePackages(packages)
                RecentTasksLoader.removeTaskIds(entries.map { it.taskId })
                taskSwitcherLayout = null
                if (recentApps.isEmpty()) {
                    endTaskSwitcherSession()
                } else {
                    host.invalidate()
                }
                dismissTaskCards(entries)
            }
            taskSwitcherFreeWindowHighlight >= 0 -> {
                val app = layout.rows
                    .getOrNull(taskSwitcherFreeWindowHighlight)
                    ?.entry
                    ?.app
                if (app != null) {
                    host.hapticConfirmLaunch()
                    endTaskSwitcherSession(runBeforeExit = true) {
                        TaskSwitcherMenuActions.launchFreeWindow(
                            host.context,
                            app.packageName,
                            host.settings(),
                            host.appRepository(),
                            app = app,
                        )
                    }
                } else {
                    endTaskSwitcherSession()
                }
            }
            taskSwitcherRowHighlight >= 0 && !taskSwitcherRowLongPressTriggered -> {
                val entry = layout.rows.getOrNull(taskSwitcherRowHighlight)?.entry
                endTaskSwitcherSession(runBeforeExit = true) {
                    entry?.let {
                        host.hapticConfirmLaunch()
                        host.actionExecutor().switchToRecentTask(
                            taskId = it.taskId,
                            rawIdentifier = it.rawIdentifier,
                            topComponent = it.topComponent,
                            packageName = it.app.packageName,
                            settings = host.settings(),
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
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
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
            val hit = TaskSwitcherLayoutEngine.closeHitRect(host, row.rowRect)
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
        val column = TaskSwitcherLayoutEngine.closeColumnRect(host, sampleRow.rowRect)
        val panelInteriorStart = when (host.side()) {
            PanelSide.LEFT -> column.right + host.dp(2f)
            PanelSide.RIGHT -> layout.panelRect.left + host.dp(2f)
        }
        val panelInteriorEnd = when (host.side()) {
            PanelSide.LEFT -> layout.panelRect.right - host.dp(2f)
            PanelSide.RIGHT -> column.left - host.dp(2f)
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
        if (host.gestureSession().taskSwitcherContinuousPickActive() && !taskSwitcherContinuousPickReady()) {
            return
        }
        syncTaskSwitcherRowLongPress(pick, eventTime)
        syncTaskSwitcherCloseLongPress(pick, layout, eventTime)
        applyTaskSwitcherPick(pick, haptic = haptic)
    }

    private fun taskSwitcherContinuousPickReady(): Boolean = host.panelEnterProgress() >= 1f

    private fun syncTaskSwitcherRowLongPress(pick: TaskSwitcherPick, eventTime: Long) {
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) return
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
                !host.gestureSession().taskSwitcherContinuousPickActive() &&
                pick.close == taskSwitcherCloseHapticIndex
            val hapticKey = continuousPickHapticKey(pick)
            val skipContinuousRetick = hapticKey >= 0 &&
                host.gestureSession().taskSwitcherContinuousPickActive() &&
                hapticKey == taskSwitcherContinuousHapticKey
            if (!skipCloseRetick && !skipContinuousRetick) {
                host.hapticTick()
                if (pick.close >= 0 && !host.gestureSession().taskSwitcherContinuousPickActive()) {
                    taskSwitcherCloseHapticIndex = pick.close
                }
                if (hapticKey >= 0 && host.gestureSession().taskSwitcherContinuousPickActive()) {
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
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) return
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
        val rowHeight = host.dp(42f)
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
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) {
            taskSwitcherGestureScrolled = true
        }
    }

    private fun applyTaskSwitcherEdgeAutoScroll(layout: TaskSwitcherPanelLayout, localY: Float): Boolean {
        val edge = host.dp(20f)
        val step = host.dp(10f)
        when {
            localY < layout.listRect.top + edge && taskSwitcherScrollOffset > 0f -> {
                val next = (taskSwitcherScrollOffset - step).coerceAtLeast(0f)
                if (next == taskSwitcherScrollOffset) return false
                taskSwitcherScrollOffset = next
                taskSwitcherLayout = null
                host.invalidate()
                return true
            }
            localY > layout.listRect.bottom - edge &&
                taskSwitcherScrollOffset < layout.maxScrollOffset -> {
                val next = (taskSwitcherScrollOffset + step).coerceAtMost(layout.maxScrollOffset)
                if (next == taskSwitcherScrollOffset) return false
                taskSwitcherScrollOffset = next
                taskSwitcherLayout = null
                host.invalidate()
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
        host.invalidate()
    }

    private fun taskSwitcherOverscrollEnabled(): Boolean =
        !host.gestureSession().taskSwitcherContinuousPickActive()

    private fun taskSwitcherRubberBand(rawExcess: Float): Float {
        val sign = if (rawExcess >= 0f) 1f else -1f
        val resisted = kotlin.math.abs(rawExcess) * TASK_SWITCHER_OVERSCROLL_RESISTANCE
        return sign * resisted.coerceAtMost(host.dp(TASK_SWITCHER_OVERSCROLL_MAX_DP))
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
                host.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    taskSwitcherOverscrollOffset = 0f
                    taskSwitcherOverscrollAnimator = null
                    host.invalidate()
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
        if (!taskSwitcherScrollDragging && kotlin.math.abs(dy) <= host.dp(TASK_SWITCHER_SCROLL_SLOP_DP)) return false
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
            host.invalidate()
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
        host.invalidate()
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
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
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
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) return
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
        host.gestureSession().panelMode() == OverlayPanelMode.TASK_SWITCHER

    /** Edge-strip tracking while the finger stays on the trigger zone. */
    private fun updateTaskSwitcherEdgeTracking(rawY: Float, localX: Float, localY: Float): Boolean {
        if (!host.zoneLayout().containsTrigger(localX, localY)) return false
        val continuousPick = host.gestureSession().taskSwitcherContinuousPickActive()
        if (!shouldFreezeTaskSwitcherAnchor()) {
            taskSwitcherExternalAnchor = false
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
        host.invalidate()
        return true
    }

    private fun taskSwitcherContextMenuActive(): Boolean =
        taskSwitcherContextMenu != null && !taskSwitcherMenuDismissing

    private fun clearTaskSwitcherMenuRowHighlight() {
        if (host.gestureSession().taskSwitcherContinuousPickActive()) return
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
            host.invalidate()
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
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
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
            host.invalidate()
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
                host.invalidate()
            }
            start()
        }
        host.invalidate()
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
                host.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    taskSwitcherMenuEnterAnimator = null
                    finishTaskSwitcherMenuDismiss()
                    host.invalidate()
                }
            })
            start()
        }
        host.invalidate()
    }

    private fun scheduleTaskSwitcherRowLongPress(index: Int) {
        cancelTaskSwitcherRowLongPress()
        taskSwitcherRowLongPressRunnable = Runnable {
            if (taskSwitcherRowPressIndex != index) return@Runnable
            if (taskSwitcherCloseHighlight == index) return@Runnable
            showTaskSwitcherContextMenu(index)
        }
        host.postDelayed(taskSwitcherRowLongPressRunnable!!, TASK_SWITCHER_LONG_PRESS_MS)
    }

    private fun cancelTaskSwitcherRowLongPress() {
        taskSwitcherRowLongPressRunnable?.let { host.removeCallbacks(it) }
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
        host.hapticConfirmLaunch()
        cancelTaskSwitcherMenuAnimation()
        taskSwitcherMenuDismissing = false
        val inlineInPanel = host.gestureSession().taskSwitcherContinuousPickActive()
        val anchorX = host.panelEnterAdjustedX(lastTaskSwitcherTouchX, layout.panelRect)
        val anchorY = lastTaskSwitcherTouchY
        val menu = TaskSwitcherContextMenuLayoutFactory.build(
            side = host.side(),
            panelRect = layout.panelRect,
            listRect = layout.listRect,
            rowIndex = index,
            packageName = row.entry.app.packageName,
            items = TaskSwitcherMenuActions.buildMenuItems(host.context.applicationContext),
            viewWidth = host.viewWidth(),
            viewHeight = host.viewHeight(),
            density = host.density(),
            anchorX = anchorX,
            anchorY = anchorY,
            inlineInPanel = inlineInPanel,
        )
        taskSwitcherContextMenu = menu
        startTaskSwitcherMenuEnterAnimation()
        if (host.gestureSession().taskSwitcherContinuousPickActive()) {
            taskSwitcherMenuAwaitingRelease = false
            taskSwitcherMenuHighlight = menu.itemRects.indexOfFirst { it.contains(anchorX, anchorY) }
        } else {
            taskSwitcherMenuAwaitingRelease = true
            taskSwitcherMenuHighlight = -1
        }
        host.invalidate()
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
            host.hapticTick()
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
                host.invalidate()
            }
            return true
        }
        val panelLayout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        val panel = panelLayout.panelRect
        val touchX = host.panelEnterAdjustedX(localX, panel)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateTaskSwitcherMenuHighlight(touchX, localY, menu, haptic = true)
                if (taskSwitcherMenuHighlight < 0 && !menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                    if (panel.contains(touchX, localY)) {
                        beginTaskSwitcherPanelDismissTap(localY)
                    }
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                    return false
                }
                updateTaskSwitcherMenuHighlight(touchX, localY, menu, haptic = true)
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activateTaskSwitcherMenuSelection(menu, touchX, localY)) {
                    host.invalidate()
                    return true
                }
                if (!menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                    if (panel.contains(touchX, localY)) {
                        beginTaskSwitcherPanelDismissTap(localY)
                    }
                    return false
                }
                host.invalidate()
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
        host.hapticConfirmLaunch()
        executeTaskSwitcherMenuItem(menu.packageName, item)
        return true
    }

    private fun executeTaskSwitcherMenuItem(packageName: String, item: TaskSwitcherMenuItem) {
        dismissTaskSwitcherContextMenu()
        val endSessionOnFreeWindow = item.type == TaskSwitcherMenuItemType.FREE_WINDOW
        TaskSwitcherMenuActions.execute(
            context = host.context,
            item = item,
            packageName = packageName,
            settings = host.settings(),
            appRepository = host.appRepository(),
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
        if (host.gestureSession().taskSwitcherContinuousPickActive()) {
            TASK_SWITCHER_CLOSE_CONTINUOUS_DWELL_MS
        } else {
            TASK_SWITCHER_CLOSE_DWELL_MS
        }

    private fun scheduleTaskSwitcherCloseLongPress(index: Int, packageName: String) {
        cancelTaskSwitcherCloseLongPress()
        taskSwitcherCloseLongPressRunnable = Runnable {
            if (host.gestureSession().panelMode() != OverlayPanelMode.TASK_SWITCHER) return@Runnable
            if (taskSwitcherClosePressIndex != index) return@Runnable
            if (taskSwitcherCloseLongPressTriggered) return@Runnable
            taskSwitcherCloseLongPressTriggered = true
            val locked = recentApps.getOrNull(index)?.isLocked != true
            toggleTaskSwitcherLock(index, packageName, locked)
        }
        host.postDelayed(taskSwitcherCloseLongPressRunnable!!, taskSwitcherCloseDwellMs())
    }

    private fun cancelTaskSwitcherCloseLongPress() {
        taskSwitcherCloseLongPressRunnable?.let { host.removeCallbacks(it) }
        taskSwitcherCloseLongPressRunnable = null
    }

    private fun toggleTaskSwitcherLock(index: Int, packageName: String, locked: Boolean) {
        if (recentApps.getOrNull(index)?.app?.packageName != packageName) return
        TaskSwitcherLockStore.setLocked(host.context, packageName, locked)
        recentApps[index] = recentApps[index].copy(isLocked = locked)
        taskSwitcherLayout = null
        host.hapticConfirmLaunch()
        host.invalidate()
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
                RecentTasksLoader.syncFromSystem(host.appRepository())
            }
        }.start()
    }
    private fun loadTaskSwitcherApps(deferInvalidate: Boolean = false) {
        taskSwitcherLayout = null
        clearTaskSwitcherPickHighlights()

        recentApps = mutableListOf()
        taskSwitcherLoading = TaskManagerUtil.hasPermission()
        if (!deferInvalidate) {
            invalidateTaskSwitcherPanel()
        }

        if (!TaskManagerUtil.hasPermission()) {
            if (!deferInvalidate) {
                invalidateTaskSwitcherPanel()
            }
            return
        }

        val generation = ++taskSwitcherLoadGeneration
        RecentTasksLoader.refreshAsync(host.appRepository()) { fresh ->
            if (generation != taskSwitcherLoadGeneration) return@refreshAsync
            if (host.gestureSession().panelMode() != OverlayPanelMode.TASK_SWITCHER) return@refreshAsync
            if (taskSwitcherContextMenuActive()) {
                dismissTaskSwitcherContextMenu(immediate = true)
            }
            taskSwitcherLoading = false
            recentApps = fresh.toMutableList()
            taskSwitcherLayout = null
            invalidateTaskSwitcherPanel()
        }
    }

    fun draw(canvas: Canvas) {
        val layout = computeTaskSwitcherLayout()
        taskSwitcherLayout = layout
        host.panelContentRect().set(layout.panelRect)
        host.drawWithPanelEnterAnimation(canvas, layout.panelRect) {
            taskSwitcherRenderer.drawPanelContent(canvas, layout, taskSwitcherRenderState())
        }
    }

    private val taskSwitcherRenderer = TaskSwitcherRenderer(host)

    private fun taskSwitcherRenderState() = TaskSwitcherRenderState(
        loading = taskSwitcherLoading,
        rowHighlight = taskSwitcherRowHighlight,
        closeHighlight = taskSwitcherCloseHighlight,
        freeWindowHighlight = taskSwitcherFreeWindowHighlight,
        closeAllHighlight = taskSwitcherCloseAllHighlight,
        overscrollOffset = taskSwitcherOverscrollOffset,
        overscrollEnabled = taskSwitcherOverscrollEnabled(),
        contextMenuActive = taskSwitcherContextMenuActive(),
        contextMenu = taskSwitcherContextMenu,
        menuHighlight = taskSwitcherMenuHighlight,
        menuEnterProgress = taskSwitcherMenuEnterProgress,
    )

    private fun computeTaskSwitcherLayout(): TaskSwitcherPanelLayout {
        val (layout, offset) = TaskSwitcherLayoutEngine.compute(
            host = host,
            recentApps = recentApps,
            scrollOffset = taskSwitcherScrollOffset,
            anchorLocalY = taskSwitcherAnchorLocalY(),
        )
        taskSwitcherScrollOffset = offset
        return layout
    }

    private fun resolveTaskSwitcherAnchorLocalY(): Float {
        val rawY = taskSwitcherAnchorRawY ?: host.pathRecognizer().gestureStartRawY()
        val loc = host.viewLocationOnScreen()
        val anchorY = rawY - loc[1]
        if (taskSwitcherExternalAnchor) {
            val minY = host.dp(16f)
            val maxY = (host.viewHeight() - host.dp(16f)).coerceAtLeast(minY)
            return anchorY.coerceIn(minY, maxY)
        }
        val trigger = host.activeTriggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun taskSwitcherAnchorLocalY(): Float =
        taskSwitcherFrozenAnchorLocalY ?: resolveTaskSwitcherAnchorLocalY()

    private fun invalidateTaskSwitcherPanel() {
        if (host.gestureSession().panelMode() == OverlayPanelMode.TASK_SWITCHER) {
            host.invalidate()
        }
    }
    private fun endTaskSwitcherSession(
        runBeforeExit: Boolean = false,
        runAfter: (() -> Unit)? = null,
    ) {
        if (taskSwitcherExiting) return
        dismissTaskSwitcherContextMenu(immediate = true)
        taskSwitcherExiting = true
        runAfter?.invoke()
        if (runBeforeExit) {
            taskSwitcherExiting = false
            host.gestureSession().endSession()
            return
        }
        host.startPanelExitAnimation {
            taskSwitcherExiting = false
            host.gestureSession().endSession()
        }
    }

    fun setExternalAnchor(rawY: Float) {
        taskSwitcherExternalAnchor = true
        taskSwitcherAnchorRawY = rawY
    }

    fun onSessionStart() {
        taskSwitcherFrozenAnchorLocalY = null
        if (taskSwitcherAnchorRawY == null || taskSwitcherAnchorRawY == 0f) {
            taskSwitcherAnchorRawY = host.pathRecognizer().gestureStartRawY().takeIf { it > 0f }
        }
        taskSwitcherLayout = null
        loadTaskSwitcherApps(deferInvalidate = false)
    }

    fun onLayoutReady() {
        taskSwitcherLayout = null
        taskSwitcherFrozenAnchorLocalY = resolveTaskSwitcherAnchorLocalY()
    }

    fun onSessionEnd() {
        taskSwitcherLoadGeneration++
        taskSwitcherLayout = null
        clearTaskSwitcherPickHighlights()
        taskSwitcherAnchorRawY = null
        taskSwitcherExternalAnchor = false
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
    }
}

