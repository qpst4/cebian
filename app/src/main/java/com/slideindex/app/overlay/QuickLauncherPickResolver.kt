package com.slideindex.app.overlay

import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedLaunchPolicy

internal class QuickLauncherPickResolver(
    private val touch: QuickLauncherTouchHandler,
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host
    private val scrollHandler get() = touch.scrollHandler

    fun continuousPickReady(): Boolean = host.panelEnterProgress() >= 1f

    fun clearHighlight() {
        host.panelGridSession().clearHighlight()
        ctrl.quickLauncherContinuousHapticIndex = -1
        cancelLongPress()
    }

    fun updateHighlight(
        localX: Float,
        localY: Float,
        touchX: Float,
        eventTime: Long,
        haptic: Boolean,
    ) {
        if (scrollHandler.pageInteractionActive()) return
        val panelRect = ctrl.quickLauncherPanelRect()
        if (!isSelectableTouch(localX, localY, panelRect)) {
            clearHighlight()
            return
        }
        val effectiveX = if (host.panelContentRect().contains(touchX, localY)) {
            touchX
        } else {
            edgeProjectedX(localY)
        }
        val prev = host.panelGridSession().highlightedIndex
        host.panelGridSession().updateHighlight(effectiveX, localY)
        if (host.panelGridSession().highlightedIndex != prev) {
            syncPressTracking(eventTime)
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

    fun isSelectableTouch(
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
        return isInApproachZone(localX, panelRect)
    }

    fun updateEdgeTracking(rawY: Float, localX: Float, localY: Float): Boolean {
        if (!host.zoneLayout().containsTrigger(localX, localY)) return false
        val continuousPick = host.gestureSession().quickLauncherContinuousPickActive()
        if (!shouldFreezeAnchor()) {
            ctrl.quickLauncherAnchorRawY = rawY
            ctrl.quickLauncherFrozenAnchorLocalY = null
        }
        if (continuousPick && continuousPickReady()) {
            val panelRect = ctrl.quickLauncherPanelRect()
            if (isSelectableTouch(localX, localY, panelRect)) {
                val touchX = host.panelEnterAdjustedX(localX, panelRect)
                if (!scrollHandler.pageInteractionActive()) {
                    scrollHandler.applyEdgeAutoPage(touchX)
                    updateHighlight(
                        localX,
                        localY,
                        touchX,
                        android.os.SystemClock.uptimeMillis(),
                        haptic = true,
                    )
                } else {
                    clearHighlight()
                }
            } else {
                clearHighlight()
            }
        } else if (!continuousPick) {
            clearHighlight()
        }
        host.invalidate()
        return true
    }

    fun performUpAction(
        event: MotionEvent,
        touchX: Float,
        localX: Float,
        localY: Float,
    ): Boolean {
        if (ctrl.quickLauncherPanelController.editMode) return false
        val panelRect = ctrl.quickLauncherPanelRect()
        if (!isSelectableTouch(localX, localY, panelRect)) return false
        val item = host.panelGridSession().highlightedQuickItem() ?: return false
        val longPress = longPressTriggered(event)
        cancelLongPress()
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

    fun syncPressTracking(eventTime: Long) {
        val index = host.panelGridSession().highlightedIndex
        if (index >= 0) {
            if (index != ctrl.quickLauncherPressIndex) {
                scheduleLongPress(index)
            }
            ctrl.quickLauncherPressIndex = index
            ctrl.quickLauncherPressDownTime = eventTime
        } else {
            cancelLongPress()
            ctrl.quickLauncherPressIndex = -1
            ctrl.quickLauncherPressDownTime = 0L
        }
    }

    fun cancelLongPress() {
        ctrl.quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
        ctrl.quickLauncherLongPressRunnable = null
        ctrl.quickLauncherLongPressIndex = -1
        ctrl.quickLauncherLongPressArmed = false
    }

    private fun scheduleLongPress(index: Int) {
        cancelLongPress()
        if (!longPressEligible()) return
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

    private fun longPressEligible(): Boolean {
        val item = host.panelGridSession().highlightedQuickItem() ?: return false
        if (item.type == QuickLauncherItemType.ACTION) return false
        return host.settings().freeWindowEnabled && host.settings().resolvedLaunchPolicy().usesLongPress()
    }

    private fun longPressTriggered(event: MotionEvent): Boolean {
        if (ctrl.quickLauncherLongPressArmed) return true
        if (!longPressEligible()) {
            return false
        }
        if (ctrl.quickLauncherPressIndex < 0 ||
            ctrl.quickLauncherPressIndex != host.panelGridSession().highlightedIndex
        ) {
            return false
        }
        return event.eventTime - ctrl.quickLauncherPressDownTime >= host.settings().effectiveLongPressDurationMs()
    }

    private fun edgeProjectedX(localY: Float): Float {
        val rowCells = host.panelGridSession().cellBounds.filter { (_, rect) ->
            localY >= rect.top && localY <= rect.bottom
        }
        if (rowCells.isEmpty()) return host.panelContentRect().centerX()
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> rowCells.minByOrNull { it.second.left }?.second?.centerX()
                ?: host.panelContentRect().centerX()
            PanelSide.RIGHT -> rowCells.maxByOrNull { it.second.right }?.second?.centerX()
                ?: host.panelContentRect().centerX()
        }
    }

    private fun isInApproachZone(localX: Float, panelRect: RectF): Boolean {
        val trigger = host.activeTriggerZoneRect()
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> localX >= trigger.right && localX <= panelRect.left
            PanelSide.RIGHT -> localX <= trigger.left && localX >= panelRect.right
        }
    }

    private fun shouldFreezeAnchor(): Boolean =
        host.gestureSession().panelMode() == OverlayPanelMode.QUICK_LAUNCHER
}
