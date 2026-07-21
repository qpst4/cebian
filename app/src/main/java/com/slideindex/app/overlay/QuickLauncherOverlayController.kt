package com.slideindex.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.overlay.layout.GridLayoutInfo
import com.slideindex.app.overlay.layout.OverlayPanelLayoutHost
import com.slideindex.app.overlay.layout.QuickLauncherPanelLayoutEngine
import com.slideindex.app.overlay.layout.visualColumn
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings

internal class QuickLauncherOverlayController(
    internal val host: Host,
) {
    interface Host : OverlayPanelLayoutHost {
        val context: Context
        fun settings(): AppSettings
        override fun side(): PanelSide
        fun apps(): List<AppInfo>
        fun gestureSession(): GestureSession
        fun zoneLayout(): GestureZoneLayout
        fun pathRecognizer(): SwipePathRecognizer
        fun actionExecutor(): ActionExecutor
        fun panelGridSession(): PanelGridSession
        fun panelEnterProgress(): Float
        fun panelEnterAdjustedX(localX: Float, panel: RectF): Float
        fun panelEnterOffsetX(panel: RectF): Float
        fun panelContentRect(): RectF
        fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit)
        override fun activeTriggerZoneRect(): RectF
        override fun viewWidth(): Int
        override fun viewHeight(): Int
        override fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun viewLocationOnScreen(): IntArray
        fun invalidate()
        fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int)
        fun post(action: () -> Unit)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
        fun hapticTick()
        fun hapticLongThreshold()
        fun hapticConfirmLaunch()
        fun startPanelExitAnimation(onEnd: () -> Unit)
        fun notifyPresentationTouchRequirementChanged()
        fun onQuickLauncherItemsPersist(items: List<QuickLauncherItem>)
        fun onOverlayWindowSuspend()
        fun onOverlayWindowResume()
    }

    private val renderer = QuickLauncherRenderer(this)
    private val touchHandler = QuickLauncherTouchHandler(this)

    internal val quickLauncherOverlayDialogHost = OverlayComposeDialogHost(
        context = host.context,
        themeSeedArgb = { host.settings().themeColorArgb },
        dynamicColor = { host.settings().dynamicColorEnabled },
    )
    internal val quickLauncherPanelController = QuickLauncherPanelController(
        object : QuickLauncherPanelController.Host {
            override val context: Context get() = host.context
            override fun settings(): AppSettings = host.settings()
            override fun side(): PanelSide = host.side()
            override fun apps(): List<AppInfo> = host.apps()
            override fun isPanelReady(): Boolean = host.panelEnterProgress() >= 1f
            override fun isAddDialogShowing(): Boolean =
                QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing
            override fun dp(value: Float): Float = host.dp(value)
            override fun sp(value: Float): Float = host.sp(value)
            override fun invalidate() = host.invalidate()
            override fun hapticTick() = host.hapticTick()
            override fun showAddDialog(
                configuredAppPackages: Set<String>,
                configuredShortcutKeys: Set<String>,
                configuredActionKeys: Set<String>,
                onAdd: (QuickLauncherItem) -> Unit,
                onRemove: (QuickLauncherItem) -> Unit,
            ) {
                QuickLauncherAddTrampoline.launch(
                    context = host.context,
                    panelSide = host.side(),
                    configuredAppPackages = configuredAppPackages,
                    configuredShortcutKeys = configuredShortcutKeys,
                    configuredActionKeys = configuredActionKeys,
                    onPrepare = { SlideIndexAccessibilityService.suspendAllEdgeOverlays() },
                    onDismiss = {
                        CreateShortcutTrampoline.cancelPending()
                        SlideIndexAccessibilityService.resumeAllEdgeOverlays()
                        host.invalidate()
                        host.notifyPresentationTouchRequirementChanged()
                    },
                    onAdd = onAdd,
                    onRemove = onRemove,
                )
            }
            override fun onPersist(items: List<QuickLauncherItem>) {
                invalidateQuickLauncherDerivedCaches()
                host.onQuickLauncherItemsPersist(items)
            }
            override fun quickLauncherPageSize(): Int = quickLauncherPageSize()
            override fun onEditDragMove(touchX: Float, localY: Float, panelRect: RectF) {
                touchHandler.applyEditDragAutoPage(touchX, panelRect)
            }
            override fun onEditDragBegan() {
                quickLauncherEdgeAutoPageSeeded = false
                quickLauncherEdgePageZone = 0
            }
            override fun resolveEditDragTargetGlobal(
                touchX: Float,
                localY: Float,
                panelRect: RectF,
            ): Int = quickLauncherGlobalIndexAt(touchX, localY, panelRect)
        },
    )

    internal var quickLauncherAnchorRawY: Float? = null
    internal var quickLauncherFrozenAnchorLocalY: Float? = null
    internal var quickLauncherContinuousHapticIndex = -1
    internal var quickLauncherPressIndex = -1
    internal var quickLauncherPressDownTime = 0L
    internal var quickLauncherLongPressArmed = false
    internal var quickLauncherLongPressIndex = -1
    internal var quickLauncherLongPressRunnable: Runnable? = null
    internal var quickLauncherPageIndex = 0
    internal var quickLauncherPageCount = 1
    internal var quickLauncherPageSwipeStartX = 0f
    internal var quickLauncherPageSwipeStartY = 0f
    internal var quickLauncherPageSwipeTracking = false
    internal var quickLauncherPageSwipeLocked = false
    internal var quickLauncherPageChangedThisGesture = false
    internal var quickLauncherPageDragOffset = 0f
    internal var quickLauncherPageSnapAnimator: ValueAnimator? = null
    internal var quickLauncherLaunchEndDeferMs = 0L
    internal var quickLauncherExiting = false
    internal var quickLauncherOpeningGestureActive = false
    internal var quickLauncherToolbarTouchActive = false
    /** -1 = outer edge, 0 = middle, 1 = inner edge; used for continuous edge auto-page. */
    internal var quickLauncherEdgePageZone = 0
    internal var quickLauncherEdgeAutoPageSeeded = false
    internal var quickLauncherAppsByPackage: Map<String, AppInfo> = emptyMap()
    internal val quickLauncherIconCache = mutableMapOf<String, Bitmap>()
    internal val quickLauncherLabelCache = mutableMapOf<String, String>()
    internal var quickLauncherCachedPages: List<List<QuickLauncherItem>>? = null
    internal var quickLauncherCachedPagesKey: Int = 0
    internal var quickLauncherLayoutPanelWidth: Float = 0f

    internal val quickLauncherCellHeight get() = host.dp(64f)
    internal val quickLauncherCellWidth get() = host.dp(56f)
    internal val quickLauncherGridPadding get() = host.dp(8f)
    internal val quickLauncherHeaderHeight get() = host.dp(24f)

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean =
        touchHandler.handleTouch(event, localX, localY)

    fun draw(canvas: Canvas, drawToolbar: Boolean = true) =
        renderer.draw(canvas, drawToolbar)

    fun panelRect(): RectF = quickLauncherPanelRect()

    fun enterContentRect(): RectF {
        val panelRect = quickLauncherPanelRect()
        return quickLauncherPanelController.combinedContentRect(panelRect)
    }

    fun isExiting(): Boolean = quickLauncherExiting

    fun isOverlayDialogShowing(): Boolean =
        QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing

    fun isComposeOverlayDialogShowing(): Boolean = quickLauncherOverlayDialogHost.isShowing

    fun syncOverlayDialogZOrder() {
        if (quickLauncherOverlayDialogHost.isShowing) {
            quickLauncherOverlayDialogHost.bringToFront()
        }
    }

    fun syncSettings(settings: AppSettings) {
        quickLauncherPanelController.syncSettings(settings)
        renderer.syncSettings(settings)
    }

    fun setApps(apps: List<AppInfo>) {
        rebuildQuickLauncherAppsByPackage(apps)
        invalidateQuickLauncherDerivedCaches()
    }

    fun invalidateDerivedCaches() {
        invalidateQuickLauncherDerivedCaches()
    }

    fun onSizeChanged() {
        quickLauncherLayoutPanelWidth = 0f
    }

    fun setAnchorRawY(rawY: Float?) {
        quickLauncherAnchorRawY = rawY
    }

    fun onSessionStart() {
        quickLauncherPageIndex = 0
        quickLauncherPageCount = 1
        quickLauncherPageSwipeTracking = false
        quickLauncherPageSwipeLocked = false
        quickLauncherPageChangedThisGesture = false
        quickLauncherPageSnapAnimator?.cancel()
        quickLauncherPageSnapAnimator = null
        quickLauncherPageDragOffset = 0f
        quickLauncherExiting = false
        quickLauncherOpeningGestureActive = true
        quickLauncherEdgePageZone = 0
        quickLauncherEdgeAutoPageSeeded = false
        quickLauncherPanelController.ensureDefaultsPersisted(host.settings())
        renderer.warmCaches()
        quickLauncherAnchorRawY = quickLauncherAnchorRawY
            ?.takeIf { it > 0f }
            ?: host.pathRecognizer().lastRawY().takeIf { it > 0f }
            ?: host.pathRecognizer().gestureStartRawY().takeIf { it > 0f }
    }

    fun onLayoutReady() {
        if (!host.gestureSession().quickLauncherContinuousPickActive()) {
            quickLauncherFrozenAnchorLocalY = resolveQuickLauncherAnchorLocalY()
        }
    }

    fun onSessionEnd() {
        quickLauncherAnchorRawY = null
        quickLauncherFrozenAnchorLocalY = null
        quickLauncherContinuousHapticIndex = -1
        quickLauncherPressIndex = -1
        quickLauncherPressDownTime = 0L
        quickLauncherPageIndex = 0
        quickLauncherPageCount = 1
        quickLauncherPageSwipeTracking = false
        quickLauncherPageSwipeLocked = false
        quickLauncherPageChangedThisGesture = false
        quickLauncherPageSnapAnimator?.cancel()
        quickLauncherPageSnapAnimator = null
        quickLauncherPageDragOffset = 0f
        quickLauncherLaunchEndDeferMs = 0L
        quickLauncherOpeningGestureActive = false
        quickLauncherEdgePageZone = 0
        quickLauncherEdgeAutoPageSeeded = false
        quickLauncherExiting = false
        quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
        quickLauncherLongPressRunnable = null
        quickLauncherLongPressIndex = -1
        quickLauncherLongPressArmed = false
        quickLauncherPanelController.reset()
        quickLauncherOverlayDialogHost.dismiss()
        invalidateQuickLauncherDerivedCaches()
    }

    fun onPanelEnterAnimationEnded() {
        host.notifyPresentationTouchRequirementChanged()
        if (!host.gestureSession().isMoveTimeActionLocked() ||
            host.gestureSession().quickLauncherContinuousPickActive()
        ) {
            quickLauncherOpeningGestureActive = false
        }
    }

    internal fun quickLauncherColumnsPerPage(): Int =
        host.settings().quickLauncherColumnsPerPage.coerceIn(2, 5)

    internal fun quickLauncherRowsPerPage(): Int =
        host.settings().quickLauncherRowsPerPage.coerceIn(2, QuickLauncherPanelLayoutEngine.MAX_ROWS)

    internal fun quickLauncherPageSize(): Int =
        quickLauncherColumnsPerPage() * quickLauncherRowsPerPage()

    internal fun quickLauncherRootItems(): List<QuickLauncherItem> =
        quickLauncherPanelController.displayItems(host.settings())

    internal fun quickLauncherItemCacheKey(item: QuickLauncherItem): String =
        "${item.type.id}\u0000${item.payload}"

    internal fun rebuildQuickLauncherAppsByPackage(apps: List<AppInfo> = host.apps()) {
        quickLauncherAppsByPackage = apps.associateBy { it.packageName }
    }

    internal fun invalidateQuickLauncherDerivedCaches() {
        quickLauncherIconCache.clear()
        quickLauncherLabelCache.clear()
        quickLauncherCachedPages = null
        quickLauncherCachedPagesKey = 0
        quickLauncherLayoutPanelWidth = 0f
    }

    internal fun quickLauncherPages(): List<List<QuickLauncherItem>> {
        val root = quickLauncherRootItems()
        val pageSize = quickLauncherPageSize()
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val key = QuickLauncherGridLogic.pagesCacheKey(root.size, root.hashCode(), pageSize, columns, rows)
        quickLauncherCachedPages?.let { cached ->
            if (key == quickLauncherCachedPagesKey) return cached
        }
        val pages = if (root.isEmpty()) {
            listOf(emptyList())
        } else {
            root.chunked(pageSize)
        }
        quickLauncherPageCount = pages.size
        quickLauncherPageIndex = quickLauncherPageIndex.coerceIn(0, pages.size - 1)
        quickLauncherCachedPages = pages
        quickLauncherCachedPagesKey = key
        return pages
    }

    internal fun quickLauncherPagination(): Triple<Int, Int, Int> {
        val pages = quickLauncherPages()
        val pageSize = quickLauncherPageSize()
        val pageCount = pages.size
        val pageStart = quickLauncherPageIndex * pageSize
        return Triple(pageStart, pageSize, pageCount)
    }

    internal fun quickLauncherItemsForPage(pageIndex: Int): List<QuickLauncherItem> {
        val pages = quickLauncherPages()
        val clampedPage = pageIndex.coerceIn(0, pages.size - 1)
        val pageStart = clampedPage * quickLauncherPageSize()
        if (clampedPage == quickLauncherPageIndex) {
            quickLauncherPanelController.setItemPageOffset(pageStart)
        }
        return pages.getOrElse(clampedPage) { emptyList() }
    }

    internal fun quickLauncherPanelWidthForPaging(): Float {
        if (quickLauncherLayoutPanelWidth > 0f) return quickLauncherLayoutPanelWidth
        return quickLauncherPanelRect().width().coerceAtLeast(1f).also {
            quickLauncherLayoutPanelWidth = it
        }
    }

    internal fun invalidateQuickLauncherPanel() {
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.invalidate()
            return
        }
        val panelRect = quickLauncherPanelRect()
        if (panelRect.isEmpty) {
            host.invalidate()
            return
        }
        val dirty = quickLauncherPanelController.combinedContentRect(panelRect)
        val offsetX = host.panelEnterOffsetX(dirty)
        val pad = host.dp(2f).toInt()
        host.invalidatePartial(
            (dirty.left + offsetX).toInt() - pad,
            dirty.top.toInt() - pad,
            (dirty.right + offsetX).toInt() + pad,
            dirty.bottom.toInt() + pad,
        )
    }

    internal fun quickLauncherPagingActiveForHitTest(): Boolean =
        quickLauncherPageSwipeLocked ||
            quickLauncherPageSnapAnimator?.isRunning == true ||
            kotlin.math.abs(quickLauncherPageDragOffset) > host.dp(0.5f)

    internal fun quickLauncherGlobalIndexAt(touchX: Float, localY: Float, panelRect: RectF): Int {
        val pageSize = quickLauncherPageSize().coerceAtLeast(1)
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val offset = quickLauncherPageDragOffset
        val pagingActive = quickLauncherPagingActiveForHitTest()

        val pageIdx: Int
        val xInPage: Float
        if (pagingActive && quickLauncherPageCount > 1) {
            val relativeX = touchX - panelRect.left - offset
            pageIdx = (relativeX / panelWidth).toInt().coerceIn(0, quickLauncherPageCount - 1)
            xInPage = panelRect.left + relativeX - pageIdx * panelWidth
        } else {
            pageIdx = quickLauncherPageIndex.coerceIn(0, quickLauncherPageCount - 1)
            xInPage = touchX
        }

        val pageStart = pageIdx * pageSize
        val localIndex = quickLauncherLocalCellIndexAt(
            xInPage = xInPage,
            localY = localY,
            panelRect = panelRect,
            maxSlotIndex = pageSize - 1,
        )
        return QuickLauncherGridLogic.dragSlotGlobal(pageStart, localIndex, pageSize)
    }

    internal fun quickLauncherLocalCellIndexAt(
        xInPage: Float,
        localY: Float,
        panelRect: RectF,
        maxSlotIndex: Int,
    ): Int {
        if (maxSlotIndex < 0) return 0
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val col = ((xInPage - panelRect.left - quickLauncherGridPadding) / quickLauncherCellWidth + 0.5f)
            .toInt()
            .coerceIn(0, columns - 1)
        val row = ((localY - panelRect.top - quickLauncherHeaderHeight - quickLauncherGridPadding) /
            quickLauncherCellHeight + 0.5f)
            .toInt()
            .coerceIn(0, rows - 1)
        return (row * columns + col).coerceIn(0, maxSlotIndex)
    }

    internal fun quickLauncherPanelRect(): RectF {
        quickLauncherPagination()
        return QuickLauncherPanelLayoutEngine.panelRect(
            host = host,
            columnsPerPage = host.settings().quickLauncherColumnsPerPage,
            rowsPerPage = host.settings().quickLauncherRowsPerPage,
            cellWidth = quickLauncherCellWidth,
            cellHeight = quickLauncherCellHeight,
            gridPadding = quickLauncherGridPadding,
            headerHeight = quickLauncherHeaderHeight,
            anchorLocalY = quickLauncherAnchorLocalY(),
            toolbarReserveWidth = quickLauncherPanelController.contentReserveWidth(host.settings()),
        )
    }

    private fun resolveQuickLauncherAnchorLocalY(): Float {
        val rawY = quickLauncherAnchorRawY ?: host.pathRecognizer().gestureStartRawY()
        val loc = host.viewLocationOnScreen()
        val anchorY = rawY - loc[1]
        val trigger = host.activeTriggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun quickLauncherAnchorLocalY(): Float =
        quickLauncherFrozenAnchorLocalY ?: resolveQuickLauncherAnchorLocalY()

    private fun quickLauncherGridLayoutInfo(): GridLayoutInfo =
        QuickLauncherPanelLayoutEngine.gridLayoutInfo(
            columnsPerPage = host.settings().quickLauncherColumnsPerPage,
            rowsPerPage = host.settings().quickLauncherRowsPerPage,
            cellWidth = quickLauncherCellWidth,
            gridPadding = quickLauncherGridPadding,
        )

    private fun quickLauncherPanelContentHeight(rows: Int): Float =
        QuickLauncherPanelLayoutEngine.contentHeight(
            rows,
            quickLauncherCellHeight,
            quickLauncherGridPadding,
            quickLauncherHeaderHeight,
        )

    private fun anchoredQuickLauncherPanelRect(panelWidth: Float, rows: Int): RectF =
        QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = panelWidth,
            contentHeight = quickLauncherPanelContentHeight(rows),
            anchorLocalY = quickLauncherAnchorLocalY(),
        )

    private fun offsetQuickLauncherPanelForToolbar(panelRect: RectF): RectF =
        QuickLauncherPanelLayoutEngine.offsetForToolbar(
            host = host,
            panelRect = panelRect,
            reserveWidth = quickLauncherPanelController.contentReserveWidth(host.settings()),
        )

    internal fun collectToolbarAccessibilityNodes(
        context: android.content.Context,
        panelRect: RectF,
    ): List<OverlayVirtualNode> =
        quickLauncherPanelController.collectAccessibilityNodes(context, panelRect)
}
