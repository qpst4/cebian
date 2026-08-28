package com.slideindex.app.overlay

import android.graphics.RectF

internal class QuickLauncherScrollHandler(
    private val touch: QuickLauncherTouchHandler,
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host
    private val pickResolver get() = touch.pickResolver
    private val pageSnapMotion get() = ctrl.quickLauncherPageSnapMotion

    fun applyEditDragAutoPage(touchX: Float, panelRect: RectF): Boolean {
        if (!ctrl.quickLauncherPanelController.isDragging()) return false
        if (!ctrl.quickLauncherPanelController.editMode) return false
        if (ctrl.quickLauncherPageCount <= 1) return false
        if (pageSnapMotion.isRunning) return false
        if (panelRect.isEmpty) return false
        return applyEdgeAutoPageInternal(touchX, panelRect)
    }

    fun consumePageRelease(): Boolean {
        if (ctrl.quickLauncherPanelController.isDragging() || ctrl.quickLauncherPageCount <= 1) return false
        if (ctrl.quickLauncherPageSwipeLocked ||
            kotlin.math.abs(ctrl.quickLauncherPageDragOffset) > host.dp(6f)
        ) {
            finishPageDrag()
            ctrl.quickLauncherPageSwipeLocked = false
            ctrl.quickLauncherPageSwipeTracking = false
            host.invalidate()
            return true
        }
        if (pageSnapMotion.isRunning) {
            host.invalidate()
            return true
        }
        return false
    }

    fun consumePageSwipeMove(touchX: Float, localY: Float): Boolean {
        if (host.gestureSession().isMoveTimeActionLocked()) return false
        if (host.gestureSession().quickLauncherContinuousPickActive()) return false
        if (ctrl.quickLauncherPanelController.isDragging() || ctrl.quickLauncherPageCount <= 1) return false
        val deltaX = touchX - ctrl.quickLauncherPageSwipeStartX
        val deltaY = localY - ctrl.quickLauncherPageSwipeStartY
        val absX = kotlin.math.abs(deltaX)
        val absY = kotlin.math.abs(deltaY)
        val directionLock = host.dp(PAGE_SWIPE_DIRECTION_LOCK_DP)
        if (!ctrl.quickLauncherPageSwipeLocked) {
            if (absX > directionLock && absX > absY * 1.25f) {
                ctrl.quickLauncherPageSwipeLocked = true
                ctrl.quickLauncherPageSwipeTracking = true
                ctrl.quickLauncherPanelController.cancelPendingDrag()
                pickResolver.clearHighlight()
            } else {
                return false
            }
        }
        updatePageDragOffset(deltaX)
        return true
    }

    fun applyEdgeAutoPage(touchX: Float): Boolean {
        if (!host.gestureSession().quickLauncherContinuousPickActive()) return false
        if (!pickResolver.continuousPickReady()) return false
        if (ctrl.quickLauncherPageCount <= 1) return false
        if (ctrl.quickLauncherPanelController.editMode) return false
        if (pageInteractionActive()) return false
        if (pageSnapMotion.isRunning) return false
        val panelRect = ctrl.quickLauncherPanelRect()
        if (panelRect.isEmpty) return false
        return applyEdgeAutoPageInternal(touchX, panelRect)
    }

    fun pageInteractionActive(): Boolean =
        ctrl.quickLauncherPageSwipeLocked || pageSnapMotion.isRunning

    fun finishPageDrag() {
        val panelWidth = ctrl.quickLauncherPanelWidthForPaging()
        val delta = computePageCommitDelta(
            offset = ctrl.quickLauncherPageDragOffset,
            panelWidth = panelWidth,
            pageIndex = ctrl.quickLauncherPageIndex,
            pageCount = ctrl.quickLauncherPageCount,
            side = host.side(),
        )
        if (delta != 0) {
            ctrl.quickLauncherPageIndex += delta
            ctrl.quickLauncherPageChangedThisGesture = true
            ctrl.quickLauncherPageDragOffset += pageCommitOffsetCompensation(
                delta = delta,
                pageWidth = panelWidth,
                side = host.side(),
            )
            syncPageOffsetForDrag()
        }
        animatePageSnapTo(0f)
    }

    fun cancelPageSnapAnimation() {
        pageSnapMotion.cancel()
    }

    private fun updatePageDragOffset(deltaX: Float) {
        cancelPageSnapAnimation()
        val panelWidth = ctrl.quickLauncherPanelWidthForPaging()
        val offset = applyPageDragResistance(
            offset = deltaX,
            pageIndex = ctrl.quickLauncherPageIndex,
            pageCount = ctrl.quickLauncherPageCount,
            side = host.side(),
            resistance = PAGE_EDGE_RESISTANCE,
        )
        ctrl.quickLauncherPageDragOffset = offset.coerceIn(-panelWidth, panelWidth)
        ctrl.invalidateQuickLauncherPanel()
    }

    private fun animatePageSnapTo(targetOffset: Float) {
        pageSnapMotion.cancel()
        val start = ctrl.quickLauncherPageDragOffset
        pageSnapMotion.animateTo(
            start = start,
            target = targetOffset,
            epsilon = host.dp(0.5f),
            onValue = { value ->
                ctrl.quickLauncherPageDragOffset = value
                ctrl.invalidateQuickLauncherPanel()
            },
            onComplete = {
                ctrl.quickLauncherPageDragOffset = targetOffset
                ctrl.invalidateQuickLauncherPanel()
            },
        )
    }

    private fun applyEdgeAutoPageInternal(touchX: Float, panelRect: RectF): Boolean {
        val zone = edgePageZoneFor(touchX, panelRect)
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

        animatePageTurn(delta)
        return true
    }

    private fun animatePageTurn(delta: Int) {
        if (delta == 0) return
        if (pageSnapMotion.isRunning) return
        cancelPageSnapAnimation()
        val panelWidth = ctrl.quickLauncherPanelWidthForPaging()
        ctrl.quickLauncherPageIndex = (ctrl.quickLauncherPageIndex + delta)
            .coerceIn(0, ctrl.quickLauncherPageCount - 1)
        syncPageOffsetForDrag()
        ctrl.quickLauncherPageChangedThisGesture = true
        ctrl.quickLauncherPageDragOffset += pageCommitOffsetCompensation(
            delta = delta,
            pageWidth = panelWidth,
            side = host.side(),
        )
        pickResolver.clearHighlight()
        host.hapticTick()
        animatePageSnapTo(0f)
    }

    private fun syncPageOffsetForDrag() {
        val pageStart = ctrl.quickLauncherPageIndex * ctrl.quickLauncherPageSize()
        ctrl.quickLauncherPanelController.setItemPageOffset(pageStart)
        if (ctrl.quickLauncherPanelController.isDragging()) {
            ctrl.quickLauncherPanelController.syncPageLocalDragTarget()
        }
    }

    private fun edgePageZoneFor(touchX: Float, panelRect: RectF): Int =
        computeEdgePageZone(
            touchX = touchX,
            panelRect = panelRect,
            side = host.side(),
            edgePx = host.dp(EDGE_AUTO_PAGE_THRESHOLD_DP),
        )

    companion object {
        private const val PAGE_SWIPE_DIRECTION_LOCK_DP = 8f
        private const val PAGE_COMMIT_FRACTION = 0.22f
        private const val PAGE_EDGE_RESISTANCE = 0.35f
        private const val EDGE_AUTO_PAGE_THRESHOLD_DP = 14f

        internal data class AdjacentPageLayer(
            val pageIndex: Int,
            val translateX: Float,
        )

        internal fun pageCommitOffsetCompensation(
            delta: Int,
            pageWidth: Float,
            side: PanelSide = PanelSide.LEFT,
        ): Float {
            if (delta == 0) return 0f
            return when (side) {
                PanelSide.RIGHT -> if (delta > 0) -pageWidth else pageWidth
                else -> if (delta > 0) pageWidth else -pageWidth
            }
        }

        internal fun computePageCommitDelta(
            offset: Float,
            panelWidth: Float,
            pageIndex: Int,
            pageCount: Int,
            side: PanelSide = PanelSide.LEFT,
        ): Int {
            val threshold = panelWidth * PAGE_COMMIT_FRACTION
            return when (side) {
                PanelSide.RIGHT -> when {
                    offset >= threshold && pageIndex < pageCount - 1 -> 1
                    offset <= -threshold && pageIndex > 0 -> -1
                    else -> 0
                }
                else -> when {
                    offset <= -threshold && pageIndex < pageCount - 1 -> 1
                    offset >= threshold && pageIndex > 0 -> -1
                    else -> 0
                }
            }
        }

        internal fun applyPageDragResistance(
            offset: Float,
            pageIndex: Int,
            pageCount: Int,
            side: PanelSide,
            resistance: Float,
        ): Float {
            var adjusted = offset
            val atStart = pageIndex <= 0
            val atEnd = pageIndex >= pageCount - 1
            when (side) {
                PanelSide.RIGHT -> {
                    if (atStart && adjusted < 0f) adjusted *= resistance
                    if (atEnd && adjusted > 0f) adjusted *= resistance
                }
                else -> {
                    if (atStart && adjusted > 0f) adjusted *= resistance
                    if (atEnd && adjusted < 0f) adjusted *= resistance
                }
            }
            return adjusted
        }

        internal fun adjacentPagesForDrag(
            dragOffset: Float,
            currentPageIndex: Int,
            pageCount: Int,
            pageWidth: Float,
            side: PanelSide,
        ): List<AdjacentPageLayer> {
            if (pageCount <= 1) return emptyList()
            return when (side) {
                PanelSide.RIGHT -> buildList {
                    if (dragOffset > 0f && currentPageIndex < pageCount - 1) {
                        add(AdjacentPageLayer(currentPageIndex + 1, dragOffset - pageWidth))
                    }
                    if (dragOffset < 0f && currentPageIndex > 0) {
                        add(AdjacentPageLayer(currentPageIndex - 1, dragOffset + pageWidth))
                    }
                }
                else -> buildList {
                    if (dragOffset < 0f && currentPageIndex < pageCount - 1) {
                        add(AdjacentPageLayer(currentPageIndex + 1, dragOffset + pageWidth))
                    }
                    if (dragOffset > 0f && currentPageIndex > 0) {
                        add(AdjacentPageLayer(currentPageIndex - 1, dragOffset - pageWidth))
                    }
                }
            }
        }

        internal fun computeEdgePageZone(
            touchX: Float,
            panelRect: RectF,
            side: PanelSide,
            edgePx: Float,
        ): Int {
            val leftThreshold = panelRect.left + edgePx
            val rightThreshold = panelRect.right - edgePx
            val atLeft = touchX <= leftThreshold
            val atRight = touchX >= rightThreshold
            return when (side) {
                PanelSide.RIGHT -> when {
                    atLeft -> 1
                    atRight -> -1
                    else -> 0
                }
                else -> when {
                    atLeft -> -1
                    atRight -> 1
                    else -> 0
                }
            }
        }
    }
}
